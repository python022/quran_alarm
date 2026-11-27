package com.quran.alarm.alarm.domain.model

import android.content.ContentResolver
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import androidx.annotation.RawRes
import com.quran.alarm.R
import com.quran.alarm.alarm.data.storage.CustomSoundStorage

/**
 * Representation of an alarm sound option (built-in preset or local device sound).
 */
data class AlarmSoundOption(
    val title: String,
    val uriString: String?,
    val isCustomFile: Boolean = false
)

/**
 * Curated list of built-in alarm sounds and sound URI constants for Quran Alarm.
 *
 * Implements 100% offline, zero-network audio presets.
 */
object AlarmSoundPresets {
    const val URI_DEFAULT = "default_alarm"
    const val URI_ADHAN_FAJR = "builtin://adhan_fajr"
    const val URI_EID_TAKBEER = "builtin://eid_takbeer"
    const val URI_CALM_TONE = "builtin://calm_tone"
    const val URI_QURAN_ALERT = "builtin://quran_alert"

    val PRESET_OPTIONS: List<AlarmSoundOption> = listOf(
        AlarmSoundOption(title = "النغمة الافتراضية", uriString = null),
        AlarmSoundOption(title = "أذان الفجر", uriString = URI_ADHAN_FAJR),
        AlarmSoundOption(title = "تكبيرات العيد", uriString = URI_EID_TAKBEER),
        AlarmSoundOption(title = "نغمة هادئة", uriString = URI_CALM_TONE),
        AlarmSoundOption(title = "تنبيه قرآني", uriString = URI_QURAN_ALERT)
    )

    fun getTitleForUri(uriString: String?, defaultTitle: String = "النغمة الافتراضية"): String {
        if (uriString.isNullOrBlank() || uriString == URI_DEFAULT) {
            return "النغمة الافتراضية"
        }
        val preset = PRESET_OPTIONS.firstOrNull { it.uriString == uriString }
        return preset?.title ?: defaultTitle
    }

    /**
     * Returns the raw resource ID for built-in sound presets as an actual integer reference
     * (preventing compile-time string folding so R8 resource shrinking retains raw assets).
     */
    @RawRes
    fun getRawResId(uriString: String?): Int? = when (uriString) {
        URI_ADHAN_FAJR -> R.raw.adhan_fajr
        URI_EID_TAKBEER -> R.raw.eid_takbeer
        URI_CALM_TONE -> R.raw.calm_tone
        URI_QURAN_ALERT -> R.raw.quran_alert
        else -> null
    }

    /**
     * Resolves a sound URI string into a playable [Uri].
     *
     * Rejects network URIs (http, https, ftp, etc.) to enforce offline-only playback.
     */
    fun resolveUri(context: Context, uriString: String?): Uri? {
        if (uriString.isNullOrBlank() || uriString == URI_DEFAULT) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }

        val rawResId = getRawResId(uriString)
        if (rawResId != null) {
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.packageName)
                .appendPath(rawResId.toString())
                .build()
        }

        return try {
            val parsed = Uri.parse(uriString)
            val scheme = parsed.scheme?.lowercase()
            when (scheme) {
                "content", "android.resource" -> parsed
                "file" -> {
                    val safeFile = CustomSoundStorage.resolveSafeCustomSoundFile(context, uriString)
                    if (safeFile != null) parsed else null
                }
                else -> {
                    // Strict whitelist rejects network/streaming schemes (http, https, ftp, rtsp, ws, smb)
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

