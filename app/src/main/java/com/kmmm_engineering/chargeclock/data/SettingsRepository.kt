package com.kmmm_engineering.chargeclock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import com.kmmm_engineering.chargeclock.discord.ThresholdAlertSnapshot
import com.kmmm_engineering.chargeclock.widget.ClockWidgetUpdater
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val language = stringPreferencesKey("language")
        val idleBrightness = floatPreferencesKey("idle_brightness")
        /** Legacy preset enum name (WHITE/AMBER/…). Migrated to [textColorArgb]. */
        val textColorLegacy = stringPreferencesKey("text_color")
        val textColorArgb = longPreferencesKey("text_color_argb")
        val discordWebhookUrl = stringPreferencesKey("discord_webhook_url")
        val discordDischarge = intPreferencesKey("discord_discharge")
        val discordCharge = intPreferencesKey("discord_charge")
        val landscape = stringPreferencesKey("landscape")
        val displayScale = stringPreferencesKey("display_scale")
        val use24Hour = booleanPreferencesKey("use_24_hour")
        val showSeconds = booleanPreferencesKey("show_seconds")
        val dateFormat = stringPreferencesKey("date_format")
        val monthFormat = stringPreferencesKey("month_format")
        val weekdayFormat = stringPreferencesKey("weekday_format")
        val clockTheme = stringPreferencesKey("clock_theme")
        val exitOnUnplug = booleanPreferencesKey("exit_on_unplug")
        val allowAutoSleep = booleanPreferencesKey("allow_auto_sleep")
        /** Persisted as first_run_hint_seen; means settings opened at least once. */
        val settingsOpenedOnce = booleanPreferencesKey("first_run_hint_seen")
        // Discord threshold-cross persistence (survives process death)
        val alertLastPercent = intPreferencesKey("alert_last_percent")
        val alertLastCharging = booleanPreferencesKey("alert_last_charging")
        val alertDischargeArmed = booleanPreferencesKey("alert_discharge_armed")
        val alertChargeArmed = booleanPreferencesKey("alert_charge_armed")
        val alertLastDischargeThreshold = intPreferencesKey("alert_last_discharge_threshold")
        val alertLastChargeThreshold = intPreferencesKey("alert_last_charge_threshold")
        val alertSeeded = booleanPreferencesKey("alert_seeded")
    }

    /** Prior preset palette (v0.1.11 and earlier). */
    private val legacyTextColorArgb: Map<String, Long> = mapOf(
        "WHITE" to 0xFFFFFFFFL,
        "AMBER" to 0xFFFFC107L,
        "GREEN" to 0xFF4CAF50L,
        "CYAN" to 0xFF00BCD4L,
        "PINK" to 0xFFE91E63L,
        "ORANGE" to 0xFFFF9800L,
    )

    private fun readTextColorArgb(prefs: Preferences): Long {
        prefs[Keys.textColorArgb]?.let { return it }
        val legacy = prefs[Keys.textColorLegacy] ?: return 0xFFFFFFFFL
        legacyTextColorArgb[legacy]?.let { return it }
        // Numeric string leftover, if any
        legacy.toLongOrNull()?.let { return it }
        legacy.removePrefix("0x").toLongOrNull(16)?.let { return it }
        return 0xFFFFFFFFL
    }

    private fun prefsToSettings(prefs: Preferences): UserSettings = UserSettings(
        language = prefs[Keys.language]?.let {
            runCatching { AppLanguage.valueOf(it) }.getOrDefault(AppLanguage.SYSTEM)
        } ?: AppLanguage.SYSTEM,
        idleBrightness = prefs[Keys.idleBrightness] ?: 0.10f,
        textColorArgb = readTextColorArgb(prefs),
        discordWebhookUrl = prefs[Keys.discordWebhookUrl] ?: "",
        discordDischargeThreshold = prefs[Keys.discordDischarge] ?: -1,
        discordChargeThreshold = prefs[Keys.discordCharge] ?: -1,
        landscapeMode = prefs[Keys.landscape]?.let {
            runCatching { LandscapeMode.valueOf(it) }.getOrDefault(LandscapeMode.OFF)
        } ?: LandscapeMode.OFF,
        displayScale = prefs[Keys.displayScale]?.let {
            runCatching { DisplayScale.valueOf(it) }.getOrDefault(DisplayScale.NORMAL)
        } ?: DisplayScale.NORMAL,
        use24Hour = prefs[Keys.use24Hour] ?: true,
        showSeconds = prefs[Keys.showSeconds] ?: true,
        dateFormat = prefs[Keys.dateFormat]?.let {
            runCatching { DateFormatOption.valueOf(it) }.getOrDefault(DateFormatOption.YMD)
        } ?: DateFormatOption.YMD,
        monthFormat = prefs[Keys.monthFormat]?.let {
            runCatching { MonthFormatOption.valueOf(it) }.getOrDefault(MonthFormatOption.NUMERIC)
        } ?: MonthFormatOption.NUMERIC,
        weekdayFormat = prefs[Keys.weekdayFormat]?.let {
            runCatching { WeekdayFormatOption.valueOf(it) }.getOrDefault(WeekdayFormatOption.EN)
        } ?: WeekdayFormatOption.EN,
        clockTheme = prefs[Keys.clockTheme]?.let {
            runCatching { ClockTheme.valueOf(it) }.getOrDefault(ClockTheme.DEFAULT)
        } ?: ClockTheme.DEFAULT,
        exitOnUnplug = prefs[Keys.exitOnUnplug] ?: false,
        allowAutoSleep = prefs[Keys.allowAutoSleep] ?: false,
        settingsOpenedOnce = prefs[Keys.settingsOpenedOnce] ?: false,
    )

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        prefsToSettings(prefs)
    }

    suspend fun getSettingsOnce(): UserSettings = settingsFlow.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { prefs ->
            val current = prefsToSettings(prefs)
            val next = transform(current)
            prefs[Keys.language] = next.language.name
            prefs[Keys.idleBrightness] = next.idleBrightness.coerceIn(0.01f, 0.35f)
            prefs[Keys.textColorArgb] = next.textColorArgb
            // Drop legacy enum string once free ARGB is written
            prefs.remove(Keys.textColorLegacy)
            prefs[Keys.discordWebhookUrl] = next.discordWebhookUrl
            prefs[Keys.discordDischarge] = next.discordDischargeThreshold
            prefs[Keys.discordCharge] = next.discordChargeThreshold
            prefs[Keys.landscape] = next.landscapeMode.name
            prefs[Keys.displayScale] = next.displayScale.name
            prefs[Keys.use24Hour] = next.use24Hour
            prefs[Keys.showSeconds] = next.showSeconds
            prefs[Keys.dateFormat] = next.dateFormat.name
            prefs[Keys.monthFormat] = next.monthFormat.name
            prefs[Keys.weekdayFormat] = next.weekdayFormat.name
            prefs[Keys.clockTheme] = next.clockTheme.name
            prefs[Keys.exitOnUnplug] = next.exitOnUnplug
            prefs[Keys.allowAutoSleep] = next.allowAutoSleep
            prefs[Keys.settingsOpenedOnce] = next.settingsOpenedOnce
        }
        ClockWidgetUpdater.updateAll(context.applicationContext)
    }

    suspend fun setLanguage(v: AppLanguage) = update { it.copy(language = v) }
    suspend fun setIdleBrightness(v: Float) = update { it.copy(idleBrightness = v) }
    suspend fun setTextColorArgb(v: Long) = update { it.copy(textColorArgb = v) }
    suspend fun setDiscordWebhookUrl(v: String) = update { it.copy(discordWebhookUrl = v) }
    suspend fun setDiscordDischarge(v: Int) = update { it.copy(discordDischargeThreshold = v) }
    suspend fun setDiscordCharge(v: Int) = update { it.copy(discordChargeThreshold = v) }
    suspend fun setLandscape(v: LandscapeMode) = update { it.copy(landscapeMode = v) }
    suspend fun setDisplayScale(v: DisplayScale) = update { it.copy(displayScale = v) }
    suspend fun setUse24Hour(v: Boolean) = update { it.copy(use24Hour = v) }
    suspend fun setShowSeconds(v: Boolean) = update { it.copy(showSeconds = v) }
    suspend fun setDateFormat(v: DateFormatOption) = update { it.copy(dateFormat = v) }
    suspend fun setMonthFormat(v: MonthFormatOption) = update { it.copy(monthFormat = v) }
    suspend fun setWeekdayFormat(v: WeekdayFormatOption) = update { it.copy(weekdayFormat = v) }
    suspend fun setClockTheme(v: ClockTheme) = update { it.copy(clockTheme = v) }
    suspend fun setExitOnUnplug(v: Boolean) = update { it.copy(exitOnUnplug = v) }
    suspend fun setAllowAutoSleep(v: Boolean) = update { it.copy(allowAutoSleep = v) }
    suspend fun markSettingsOpenedOnce() = update { it.copy(settingsOpenedOnce = true) }

    suspend fun loadThresholdAlertSnapshot(): ThresholdAlertSnapshot {
        val prefs = context.dataStore.data.first()
        val seeded = prefs[Keys.alertSeeded] ?: false
        val lastPercent = prefs[Keys.alertLastPercent] ?: -1
        return ThresholdAlertSnapshot(
            lastPercent = lastPercent,
            lastCharging = prefs[Keys.alertLastCharging] ?: false,
            dischargeArmed = prefs[Keys.alertDischargeArmed] ?: true,
            chargeArmed = prefs[Keys.alertChargeArmed] ?: true,
            lastDischargeThreshold = prefs[Keys.alertLastDischargeThreshold] ?: -1,
            lastChargeThreshold = prefs[Keys.alertLastChargeThreshold] ?: -1,
            seeded = seeded && lastPercent >= 0,
        )
    }

    suspend fun saveThresholdAlertSnapshot(snapshot: ThresholdAlertSnapshot) {
        context.dataStore.edit { prefs ->
            prefs[Keys.alertLastPercent] = snapshot.lastPercent
            prefs[Keys.alertLastCharging] = snapshot.lastCharging
            prefs[Keys.alertDischargeArmed] = snapshot.dischargeArmed
            prefs[Keys.alertChargeArmed] = snapshot.chargeArmed
            prefs[Keys.alertLastDischargeThreshold] = snapshot.lastDischargeThreshold
            prefs[Keys.alertLastChargeThreshold] = snapshot.lastChargeThreshold
            prefs[Keys.alertSeeded] = snapshot.seeded
        }
    }
}
