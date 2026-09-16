package com.kmmm_engineering.chargeclock.discord

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DiscordNotifier {
    private const val TAG = "DiscordNotifier"

    suspend fun send(webhookUrl: String, content: String): Boolean = withContext(Dispatchers.IO) {
        if (webhookUrl.isBlank() || !webhookUrl.startsWith("https://")) return@withContext false
        try {
            val url = URL(webhookUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val body = JSONObject().put("content", content).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "webhook failed", e)
            false
        }
    }
}

/**
 * Fires once when crossing a threshold; stays quiet until leaving the zone
 * (or switching charge/discharge mode) then crossing again.
 */
class ThresholdFireTracker {
    private var dischargeArmed = true
    private var chargeArmed = true
    private var lastDischargeThreshold = -1
    private var lastChargeThreshold = -1

    /** @return "discharge", "charge", or null */
    fun check(
        percent: Int,
        isCharging: Boolean,
        dischargeThreshold: Int,
        chargeThreshold: Int,
    ): String? {
        if (dischargeThreshold != lastDischargeThreshold) {
            lastDischargeThreshold = dischargeThreshold
            dischargeArmed = true
        }
        if (chargeThreshold != lastChargeThreshold) {
            lastChargeThreshold = chargeThreshold
            chargeArmed = true
        }

        if (!isCharging) {
            chargeArmed = true
            if (dischargeThreshold < 0) {
                dischargeArmed = true
                return null
            }
            if (percent <= dischargeThreshold) {
                if (dischargeArmed) {
                    dischargeArmed = false
                    return "discharge"
                }
            } else {
                dischargeArmed = true
            }
        } else {
            dischargeArmed = true
            if (chargeThreshold < 0) {
                chargeArmed = true
                return null
            }
            if (percent >= chargeThreshold) {
                if (chargeArmed) {
                    chargeArmed = false
                    return "charge"
                }
            } else {
                chargeArmed = true
            }
        }
        return null
    }
}
