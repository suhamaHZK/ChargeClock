package com.kmmm_engineering.chargeclock

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

class MainActivity : ComponentActivity() {

    private val thresholdTracker = ThresholdFireTracker()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        val app = application as ChargeClockApp
        val repo = app.settingsRepository

        setContent {
            val settings by repo.settingsFlow.collectAsStateWithLifecycle(
                initialValue = UserSettings(),
            )
            val batteryFlow = remember { BatteryMonitor.observe(applicationContext) }
            val battery by batteryFlow.collectAsStateWithLifecycle(
                initialValue = com.kmmm_engineering.chargeclock.battery.BatteryStatus(0, false, false),
            )
            val scope = rememberCoroutineScope()

            // Apply language override
            LaunchedEffect(settings.language) {
                applyLanguage(settings.language)
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

            // Clock-screen temporary brighten (single tap)
            var brightUntil by remember { mutableStateOf(0L) }
            // Settings screen: null = readable (system); non-null = live idle-slider preview
            var settingsBrightnessPreview by remember { mutableStateOf<Float?>(null) }
            var showSettings by remember { mutableStateOf(false) }

            LaunchedEffect(
                showSettings,
                settingsBrightnessPreview,
                settings.idleBrightness,
                brightUntil,
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
                    else -> {
                        val now = System.currentTimeMillis()
                        if (brightUntil > now) {
                            applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                            val remaining = brightUntil - now
                            delay(remaining)
                            // Re-check: may have entered settings during delay
                            if (!showSettings) {
                                applyWindowBrightness(settings.idleBrightness)
                            }
                        } else {
                            applyWindowBrightness(settings.idleBrightness)
                        }
                    }
                }
            }

            // Exit on unplug
            LaunchedEffect(battery.isPlugged, settings.exitOnUnplug) {
                if (settings.exitOnUnplug && !battery.isPlugged) {
                    finish()
                }
            }

            // Discord thresholds
            LaunchedEffect(
                battery.percent,
                battery.isCharging,
                settings.discordDischargeThreshold,
                settings.discordChargeThreshold,
                settings.discordWebhookUrl,
            ) {
                val kind = thresholdTracker.check(
                    percent = battery.percent,
                    isCharging = battery.isCharging,
                    dischargeThreshold = settings.discordDischargeThreshold,
                    chargeThreshold = settings.discordChargeThreshold,
                )
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

            ChargeClockTheme {
                if (showSettings) {
                    SettingsScreen(
                        settings = settings,
                        repository = repo,
                        onBack = {
                            settingsBrightnessPreview = null
                            showSettings = false
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
                        onSingleTap = {
                            brightUntil = System.currentTimeMillis() + 5_000L
                        },
                        onOpenSettings = {
                            settingsBrightnessPreview = null
                            showSettings = true
                        },
                        onHintDismissed = {
                            scope.launch { repo.markFirstRunHintSeen() }
                        },
                    )
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
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
