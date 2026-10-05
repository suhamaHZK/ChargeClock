package com.kmmm_engineering.chargeclock

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.kmmm_engineering.chargeclock.discord.ThresholdAlertEvaluator
import com.kmmm_engineering.chargeclock.ui.ClockScreen
import com.kmmm_engineering.chargeclock.ui.IdleDisplayPolicy
import com.kmmm_engineering.chargeclock.ui.SettingsScreen
import com.kmmm_engineering.chargeclock.ui.theme.ChargeClockTheme
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {

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
            // Survive Activity recreate from AppCompatDelegate.setApplicationLocales
            var showSettings by rememberSaveable { mutableStateOf(false) }
            // Anti-misoperation unlock slider visible on clock → readable brightness
            var unlockSliderVisible by remember { mutableStateOf(false) }
            // Only poll while resumed so onPause system-brightness restore is not fought
            var activityResumed by remember { mutableStateOf(true) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            activityResumed = true
                            // Come back showing the clock (not black / not seconds-hidden).
                            lastInteractionAt = System.currentTimeMillis()
                        }
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

            // Main-clock idle state: seconds hiding + OLED blank (pure helper decides).
            // Discharging = !isPlugged && !isCharging (BatteryMonitor reports isCharging
            // whenever plugged, so this matches the Discord discharge side).
            val batteryKnown = batteryState.value != null
            val discharging = batteryKnown &&
                IdleDisplayPolicy.isDischarging(battery.isCharging, battery.isPlugged)
            // Plug-in / unplug restarts the blank countdown (plug-in also exits black at once
            // because blank requires discharging). Initial value only seeds.
            var previousDischarging by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(discharging) {
                val prev = previousDischarging
                previousDischarging = discharging
                if (prev != null && prev != discharging) {
                    markInteraction()
                }
            }
            var clockIdle by remember { mutableStateOf(false) }
            var clockBlank by remember { mutableStateOf(false) }
            LaunchedEffect(
                lastInteractionAt,
                brightUntil,
                showSettings,
                unlockSliderVisible,
                activityResumed,
                settings.blankWhenIdleDischarging,
                settings.blankIdleDelaySec,
                battery.isCharging,
                battery.isPlugged,
                batteryKnown,
            ) {
                while (true) {
                    val now = System.currentTimeMillis()
                    val state = IdleDisplayPolicy.evaluate(
                        now = now,
                        lastInteractionAt = lastInteractionAt,
                        brightUntil = brightUntil,
                        blankEnabled = settings.blankWhenIdleDischarging,
                        blankDelaySec = settings.blankIdleDelaySec,
                        isCharging = battery.isCharging,
                        isPlugged = battery.isPlugged,
                        batteryKnown = batteryKnown,
                        overlayVisible = showSettings || unlockSliderVisible || !activityResumed,
                    )
                    clockIdle = state.idle
                    clockBlank = state.blank
                    val next = state.nextChangeAt ?: break
                    delay((next - now).coerceAtLeast(1L))
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

            // Discord thresholds — shared helper with widget path (DataStore latch).
            // First sample in this process only seeds (a cross while dead must not fire).
            // Later in-process samples can fire on a real cross since the last sample.
            LaunchedEffect(
                batteryState.value,
                settingsState.value,
                battery.percent,
                battery.isCharging,
                settings.discordDischargeThreshold,
                settings.discordChargeThreshold,
                settings.discordWebhookUrl,
            ) {
                if (settingsState.value == null) return@LaunchedEffect
                if (batteryState.value == null) return@LaunchedEffect
                ThresholdAlertEvaluator.evaluateAndNotify(
                    context = applicationContext,
                    percent = battery.percent,
                    isCharging = battery.isCharging,
                    settings = settings,
                )
            }

            // Recompose strings when application locales / configuration update
            val configuration = LocalConfiguration.current
            val localeKey = configuration.locales.toLanguageTags() + "|" + settings.language.name

            key(localeKey) {
                ChargeClockTheme {
                                        val closeSettings: () -> Unit = {
                        settingsBrightnessPreview = null
                        showSettings = false
                        markInteraction()
                        hideSystemBars()
                    }
                    BackHandler(enabled = showSettings) {
                        closeSettings()
                    }

                    when {
                        showSettings -> {
                            // Mark only when Settings is actually shown (not on double-tap alone).
                            LaunchedEffect(Unit) {
                                if (!settings.settingsOpenedOnce) {
                                    repo.markSettingsOpenedOnce()
                                }
                            }
                            SettingsScreen(
                                settings = settings,
                                repository = repo,
                                onBack = closeSettings,
                                onIdleBrightnessPreview = { preview ->
                                    settingsBrightnessPreview = preview
                                },
                            )
                        }
                        else -> {
                        ClockScreen(
                            settings = settings,
                            batteryPercent = battery.percent,
                            isCharging = battery.isCharging,
                            idle = clockIdle,
                            blank = clockBlank,
                            onWakeFromBlank = {
                                // Wake only + normal 5 s brighten; ClockScreen refreshes time.
                                clockBlank = false
                                markInteraction(brightenMs = 5_000L)
                            },
                            onSingleTap = {
                                markInteraction(brightenMs = 5_000L)
                            },
                            onOpenSettings = {
                                markInteraction()
                                unlockSliderVisible = false
                                settingsBrightnessPreview = null
                                showSettings = true
                            },
                            onUnlockSliderVisibilityChange = { visible ->
                                // Showing or hiding the slider both restart the idle countdown,
                                // so the clock does not go black right after the slider closes.
                                markInteraction()
                                unlockSliderVisible = visible
                            },
                        )
                        }
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
