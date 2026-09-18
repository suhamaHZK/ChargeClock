package com.kmmm_engineering.chargeclock

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmmm_engineering.chargeclock.battery.BatteryMonitor
import com.kmmm_engineering.chargeclock.data.AppLanguage
import com.kmmm_engineering.chargeclock.data.LandscapeMode
import com.kmmm_engineering.chargeclock.data.UserSettings
import com.kmmm_engineering.chargeclock.discord.DiscordNotifier
import com.kmmm_engineering.chargeclock.discord.ThresholdFireTracker
import com.kmmm_engineering.chargeclock.ui.ClockScreen
import com.kmmm_engineering.chargeclock.ui.SettingsScreen
import com.kmmm_engineering.chargeclock.ui.theme.ChargeClockTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val thresholdTracker = ThresholdFireTracker()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        val app = application as ChargeClockApp
        val repo = app.settingsRepository

        setContent {
            // null until first DataStore emission — avoid applying SYSTEM default and wiping
            // a previously stored AppCompat locale before prefs load.
            val settingsState = repo.settingsFlow.collectAsStateWithLifecycle(
                initialValue = null,
            )
            val settings = settingsState.value ?: UserSettings()
            val batteryFlow = remember { BatteryMonitor.observe(applicationContext) }
            val batteryState = batteryFlow.collectAsStateWithLifecycle(
                initialValue = null,
            )
            val battery = batteryState.value
                ?: com.kmmm_engineering.chargeclock.battery.BatteryStatus(0, false, false)
            val scope = rememberCoroutineScope()

            // Restore threshold-alert latch from DataStore once; restore forces re-seed on first check.
            var alertStateReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                thresholdTracker.restore(repo.loadThresholdAlertSnapshot())
                alertStateReady = true
            }

            // Apply language override (AppCompat per-app locales). Activity recreates on change.
            LaunchedEffect(settingsState.value?.language) {
                val language = settingsState.value?.language ?: return@LaunchedEffect
                applyLanguage(language)
            }

            // Orientation
            LaunchedEffect(settings.landscapeMode) {
                requestedOrientation = when (settings.landscapeMode) {
                    LandscapeMode.OFF -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    LandscapeMode.RIGHT -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    LandscapeMode.LEFT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                }
            }

            // Keep screen on unless auto-sleep allowed
            LaunchedEffect(settings.allowAutoSleep) {
                if (settings.allowAutoSleep) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // Clock-screen temporary brighten (single tap) + idle interaction clock
            var brightUntil by remember { mutableStateOf(0L) }
            var lastInteractionAt by remember { mutableStateOf(System.currentTimeMillis()) }
            // Settings screen: null = readable (system); non-null = live idle-slider preview
            var settingsBrightnessPreview by remember { mutableStateOf<Float?>(null) }
            var showSettings by remember { mutableStateOf(false) }
            // Anti-misoperation unlock slider visible on clock → readable brightness
            var unlockSliderVisible by remember { mutableStateOf(false) }
            // Only poll while resumed so onPause system-brightness restore is not fought
            var activityResumed by remember { mutableStateOf(true) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> activityResumed = true
                        Lifecycle.Event.ON_PAUSE -> activityResumed = false
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            fun markInteraction(brightenMs: Long = 0L) {
                val now = System.currentTimeMillis()
                lastInteractionAt = now
                if (brightenMs > 0L) {
                    brightUntil = now + brightenMs
                }
            }

            LaunchedEffect(
                showSettings,
                settingsBrightnessPreview,
                settings.idleBrightness,
                brightUntil,
                unlockSliderVisible,
            ) {
                when {
                    showSettings -> {
                        val preview = settingsBrightnessPreview
                        if (preview != null) {
                            applyWindowBrightness(preview)
                        } else {
                            // Readable settings: system brightness (not ultra-dim idle)
                            applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                        }
                    }
                    unlockSliderVisible -> {
                        // Same idea as settings: readable while slide-to-settings is on screen
                        applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                    }
                    else -> {
                        val now = System.currentTimeMillis()
                        if (brightUntil > now) {
                            applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                            val remaining = brightUntil - now
                            delay(remaining)
                            // Re-check: may have entered settings / slider during delay
                            if (!showSettings && !unlockSliderVisible) {
                                applyWindowBrightness(settings.idleBrightness)
                            }
                        } else {
                            applyWindowBrightness(settings.idleBrightness)
                        }
                    }
                }
            }

            // Periodic idle-dim enforcement after resume / external brightness resets.
            // Skips while settings or unlock slider visible; respects 5s tap-to-brighten.
            LaunchedEffect(activityResumed, settings.idleBrightness) {
                if (!activityResumed) return@LaunchedEffect
                while (true) {
                    if (!showSettings && !unlockSliderVisible) {
                        val now = System.currentTimeMillis()
                        val idleDeadline = maxOf(lastInteractionAt + 5_000L, brightUntil)
                        if (now >= idleDeadline) {
                            val current = window.attributes.screenBrightness
                            val idle = settings.idleBrightness
                            val needsDim =
                                current == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE ||
                                    current < 0f ||
                                    current > idle + 0.005f
                            if (needsDim) {
                                applyWindowBrightness(idle)
                            }
                        }
                    }
                    delay(1_000L)
                }
            }

            // Exit on unplug — falling edge only (plugged → unplugged).
            // Do NOT exit merely because the app started while already unplugged.
            var previousPlugged by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(battery.isPlugged, settings.exitOnUnplug) {
                val plugged = battery.isPlugged
                val prev = previousPlugged
                previousPlugged = plugged
                if (settings.exitOnUnplug && prev == true && !plugged) {
                    finish()
                }
            }

            // Discord thresholds — notify only on crossing, never on cold start while already
            // on the alert side. Wait for DataStore settings + real battery + restored latch.
            LaunchedEffect(
                batteryState.value,
                settingsState.value,
                alertStateReady,
                battery.percent,
                battery.isCharging,
                settings.discordDischargeThreshold,
                settings.discordChargeThreshold,
                settings.discordWebhookUrl,
            ) {
                if (!alertStateReady) return@LaunchedEffect
                if (settingsState.value == null) return@LaunchedEffect
                if (batteryState.value == null) return@LaunchedEffect

                val kind = thresholdTracker.check(
                    percent = battery.percent,
                    isCharging = battery.isCharging,
                    dischargeThreshold = settings.discordDischargeThreshold,
                    chargeThreshold = settings.discordChargeThreshold,
                )
                // Persist latch / last percent so process death does not re-arm spuriously.
                repo.saveThresholdAlertSnapshot(thresholdTracker.snapshot())

                if (kind != null && settings.discordWebhookUrl.isNotBlank()) {
                    val msg = when (kind) {
                        "discharge" -> getString(R.string.discord_discharge_msg, battery.percent)
                        else -> getString(R.string.discord_charge_msg, battery.percent)
                    }
                    scope.launch {
                        DiscordNotifier.send(settings.discordWebhookUrl, msg)
                    }
                }
            }

            // Recompose strings when application locales / configuration update
            val configuration = LocalConfiguration.current
            val localeKey = configuration.locales.toLanguageTags() + "|" + settings.language.name

            key(localeKey) {
                ChargeClockTheme {
                    if (showSettings) {
                        SettingsScreen(
                            settings = settings,
                            repository = repo,
                            onBack = {
                                settingsBrightnessPreview = null
                                showSettings = false
                                markInteraction()
                                hideSystemBars()
                            },
                            onIdleBrightnessPreview = { preview ->
                                settingsBrightnessPreview = preview
                            },
                        )
                    } else {
                        ClockScreen(
                            settings = settings,
                            batteryPercent = battery.percent,
                            isCharging = battery.isCharging,
                            onSingleTap = {
                                markInteraction(brightenMs = 5_000L)
                            },
                            onOpenSettings = {
                                markInteraction()
                                unlockSliderVisible = false
                                settingsBrightnessPreview = null
                                showSettings = true
                            },
                            onHintDismissed = {
                                scope.launch { repo.markFirstRunHintSeen() }
                            },
                            onUnlockSliderVisibilityChange = { visible ->
                                if (visible) markInteraction()
                                unlockSliderVisible = visible
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onPause() {
        // Restore system brightness when leaving
        applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        super.onPause()
    }

    private fun applyWindowBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value
        window.attributes = lp
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun applyLanguage(language: AppLanguage) {
        val tags = when (language) {
            AppLanguage.SYSTEM -> ""
            AppLanguage.ENGLISH -> "en"
            AppLanguage.JAPANESE -> "ja"
        }
        val locales = if (tags.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tags)
        }
        val current = AppCompatDelegate.getApplicationLocales()
        val currentTags = current.toLanguageTags()
        val desiredTags = locales.toLanguageTags()
        if (currentTags != desiredTags) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
