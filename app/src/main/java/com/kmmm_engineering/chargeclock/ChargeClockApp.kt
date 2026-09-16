package com.kmmm_engineering.chargeclock

import android.app.Application
import com.kmmm_engineering.chargeclock.data.SettingsRepository

class ChargeClockApp : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
    }
}
