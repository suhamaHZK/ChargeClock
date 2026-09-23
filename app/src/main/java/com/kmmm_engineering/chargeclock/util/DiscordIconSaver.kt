package com.kmmm_engineering.chargeclock.util

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.kmmm_engineering.chargeclock.R

/**
 * Saves the bundled Discord webhook PNG into DCIM via MediaStore.
 * minSdk 29: RELATIVE_PATH + IS_PENDING (no legacy storage permission).
 */
object DiscordIconSaver {
    fun saveToGallery(context: Context): Boolean {
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "chargeclock-discord-icon.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_DCIM + "/ChargeClock",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                context.resources.openRawResource(R.raw.discord_icon_256).use { input ->
                    input.copyTo(out)
                }
            } ?: run {
                resolver.delete(uri, null, null)
                return false
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrDefault(false)
    }
}
