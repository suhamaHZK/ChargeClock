package com.kmmm_engineering.chargeclock

import android.app.Application
import com.kmmm_engineering.chargeclock.data.SettingsRepository
import com.kmmm_engineering.chargeclock.widget.WidgetTimeTickRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ChargeClockApp : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set

    /** Process-lifetime scope for widget Discord alerts and other fire-and-forget work. */
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        // TIME_TICK + Handler minute-arm while process alive; AlarmManager covers process-dead.
        WidgetTimeTickRegistrar.register(this)
    }
}
