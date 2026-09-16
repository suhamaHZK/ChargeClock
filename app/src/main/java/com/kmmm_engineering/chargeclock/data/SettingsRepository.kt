package com.kmmm_engineering.chargeclock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val language = stringPreferencesKey("language")
        val idleBrightness = floatPreferencesKey("idle_brightness")
        val textColor = stringPreferencesKey("text_color")
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
        val exitOnUnplug = booleanPreferencesKey("exit_on_unplug")
        val allowAutoSleep = booleanPreferencesKey("allow_auto_sleep")
        val firstRunHintSeen = booleanPreferencesKey("first_run_hint_seen")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            language = prefs[Keys.language]?.let {
                runCatching { AppLanguage.valueOf(it) }.getOrDefault(AppLanguage.SYSTEM)
            } ?: AppLanguage.SYSTEM,
            idleBrightness = prefs[Keys.idleBrightness] ?: 0.02f,
            textColor = prefs[Keys.textColor]?.let {
                runCatching { TextColorOption.valueOf(it) }.getOrDefault(TextColorOption.WHITE)
            } ?: TextColorOption.WHITE,
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
            exitOnUnplug = prefs[Keys.exitOnUnplug] ?: false,
            allowAutoSleep = prefs[Keys.allowAutoSleep] ?: false,
            firstRunHintSeen = prefs[Keys.firstRunHintSeen] ?: false,
        )
    }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { prefs ->
            // Read current snapshot from prefs into a settings object, transform, write back
            val current = UserSettings(
                language = prefs[Keys.language]?.let {
                    runCatching { AppLanguage.valueOf(it) }.getOrDefault(AppLanguage.SYSTEM)
                } ?: AppLanguage.SYSTEM,
                idleBrightness = prefs[Keys.idleBrightness] ?: 0.02f,
                textColor = prefs[Keys.textColor]?.let {
                    runCatching { TextColorOption.valueOf(it) }.getOrDefault(TextColorOption.WHITE)
                } ?: TextColorOption.WHITE,
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
                exitOnUnplug = prefs[Keys.exitOnUnplug] ?: false,
                allowAutoSleep = prefs[Keys.allowAutoSleep] ?: false,
                firstRunHintSeen = prefs[Keys.firstRunHintSeen] ?: false,
            )
            val next = transform(current)
            prefs[Keys.language] = next.language.name
            prefs[Keys.idleBrightness] = next.idleBrightness.coerceIn(0.01f, 0.35f)
            prefs[Keys.textColor] = next.textColor.name
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
            prefs[Keys.exitOnUnplug] = next.exitOnUnplug
            prefs[Keys.allowAutoSleep] = next.allowAutoSleep
            prefs[Keys.firstRunHintSeen] = next.firstRunHintSeen
        }
    }

    suspend fun setLanguage(v: AppLanguage) = update { it.copy(language = v) }
    suspend fun setIdleBrightness(v: Float) = update { it.copy(idleBrightness = v) }
    suspend fun setTextColor(v: TextColorOption) = update { it.copy(textColor = v) }
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
    suspend fun setExitOnUnplug(v: Boolean) = update { it.copy(exitOnUnplug = v) }
    suspend fun setAllowAutoSleep(v: Boolean) = update { it.copy(allowAutoSleep = v) }
    suspend fun markFirstRunHintSeen() = update { it.copy(firstRunHintSeen = true) }
}
