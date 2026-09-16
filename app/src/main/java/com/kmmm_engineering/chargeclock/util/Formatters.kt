package com.kmmm_engineering.chargeclock.util

import com.kmmm_engineering.chargeclock.data.DateFormatOption
import com.kmmm_engineering.chargeclock.data.MonthFormatOption
import com.kmmm_engineering.chargeclock.data.UserSettings
import com.kmmm_engineering.chargeclock.data.WeekdayFormatOption
import java.util.Calendar
import java.util.Locale

object Formatters {
    private val monthAbbr = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    private val weekdayEn = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val weekdayJa = arrayOf("日", "月", "火", "水", "木", "金", "土")

    fun formatDate(cal: Calendar, settings: UserSettings): String {
        val y = cal.get(Calendar.YEAR)
        val mIdx = cal.get(Calendar.MONTH)
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val monthStr = when (settings.monthFormat) {
            MonthFormatOption.NUMERIC -> "%02d".format(mIdx + 1)
            MonthFormatOption.ABBR -> monthAbbr[mIdx]
        }
        val dayStr = "%02d".format(d)
        val datePart = when (settings.dateFormat) {
            DateFormatOption.YMD -> when (settings.monthFormat) {
                MonthFormatOption.NUMERIC -> "%04d/%s/%s".format(y, monthStr, dayStr)
                MonthFormatOption.ABBR -> "%04d/%s/%s".format(y, monthStr, dayStr)
            }
            DateFormatOption.MDY -> when (settings.monthFormat) {
                MonthFormatOption.NUMERIC -> "%s/%s/%04d".format(monthStr, dayStr, y)
                MonthFormatOption.ABBR -> "%s %s, %04d".format(monthStr, dayStr, y)
            }
            DateFormatOption.DMY -> when (settings.monthFormat) {
                MonthFormatOption.NUMERIC -> "%s/%s/%04d".format(dayStr, monthStr, y)
                MonthFormatOption.ABBR -> "%s %s %04d".format(dayStr, monthStr, y)
            }
        }
        val wd = cal.get(Calendar.DAY_OF_WEEK) - 1
        val weekday = when (settings.weekdayFormat) {
            WeekdayFormatOption.EN -> weekdayEn[wd]
            WeekdayFormatOption.JA -> weekdayJa[wd]
        }
        return "$datePart  $weekday"
    }

    fun formatTime(cal: Calendar, settings: UserSettings): String {
        val h24 = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)
        val hour = if (settings.use24Hour) {
            h24
        } else {
            val h12 = cal.get(Calendar.HOUR)
            if (h12 == 0) 12 else h12
        }
        val base = if (settings.use24Hour) {
            "%02d:%02d".format(hour, min)
        } else {
            val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
            "%d:%02d %s".format(hour, min, amPm)
        }
        return if (settings.showSeconds) {
            if (settings.use24Hour) {
                "%02d:%02d:%02d".format(hour, min, sec)
            } else {
                val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
                "%d:%02d:%02d %s".format(hour, min, sec, amPm)
            }
        } else {
            base
        }
    }

    fun resolveLocale(settings: UserSettings, system: Locale): Locale = when (settings.language) {
        com.kmmm_engineering.chargeclock.data.AppLanguage.SYSTEM -> system
        com.kmmm_engineering.chargeclock.data.AppLanguage.ENGLISH -> Locale.ENGLISH
        com.kmmm_engineering.chargeclock.data.AppLanguage.JAPANESE -> Locale.JAPANESE
    }
}
