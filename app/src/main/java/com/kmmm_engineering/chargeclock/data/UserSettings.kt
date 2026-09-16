package com.kmmm_engineering.chargeclock.data

enum class AppLanguage { SYSTEM, ENGLISH, JAPANESE }

enum class LandscapeMode { OFF, RIGHT, LEFT }

enum class DateFormatOption { YMD, MDY, DMY }

enum class MonthFormatOption { NUMERIC, ABBR }

enum class WeekdayFormatOption { EN, JA, TW, CN }

enum class TextColorOption(val argb: Long) {
    WHITE(0xFFFFFFFF),
    AMBER(0xFFFFC107),
    GREEN(0xFF4CAF50),
    CYAN(0xFF00BCD4),
    PINK(0xFFE91E63),
    ORANGE(0xFFFF9800),
}

/** Display scale multipliers for the clock block. */
enum class DisplayScale(val factor: Float) {
    SMALL(0.85f),
    NORMAL(1.0f),
    LARGE(1.2f),
    XLARGE(1.45f),
}

/**
 * Discord threshold: -1 = OFF, otherwise 5..100 step 5.
 */
data class UserSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val idleBrightness: Float = 0.10f,
    val textColor: TextColorOption = TextColorOption.WHITE,
    val discordWebhookUrl: String = "",
    val discordDischargeThreshold: Int = -1,
    val discordChargeThreshold: Int = -1,
    val landscapeMode: LandscapeMode = LandscapeMode.OFF,
    val displayScale: DisplayScale = DisplayScale.NORMAL,
    val use24Hour: Boolean = true,
    val showSeconds: Boolean = true,
    val dateFormat: DateFormatOption = DateFormatOption.YMD,
    val monthFormat: MonthFormatOption = MonthFormatOption.NUMERIC,
    val weekdayFormat: WeekdayFormatOption = WeekdayFormatOption.EN,
    val exitOnUnplug: Boolean = false,
    val allowAutoSleep: Boolean = false,
    val firstRunHintSeen: Boolean = false,
)
