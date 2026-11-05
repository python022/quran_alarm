package com.quran.alarm.alarm.domain.model

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import com.quran.alarm.R

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
 * Implements 100% offline, zero-network audio presets complying with AGENTS.md Law 2.
 */
object AlarmSoundPresets {
    const val URI_DEFAULT = "default_alarm"
    const val URI_ADHAN_FAJR = "builtin://adhan_fajr"
    const val URI_EID_TAKBEER = "builtin://eid_takbeer"
    const val URI_CALM_TONE = "builtin://calm_tone"
    const val URI_QURAN_ALERT = "builtin://quran_alert"

    val PRESET_OPTIONS: List<AlarmSoundOption> = listOf(
        AlarmSoundOption(title = "نغمة المنبه الافتراضية", uriString = null),
        AlarmSoundOption(title = "أذان الفجر", uriString = URI_ADHAN_FAJR),
        AlarmSoundOption(title = "تكبيرات العيد", uriString = URI_EID_TAKBEER),
        AlarmSoundOption(title = "نغمة هادئة", uriString = URI_CALM_TONE),
        AlarmSoundOption(title = "تنبيه قرآني", uriString = URI_QURAN_ALERT)
    )

    fun getTitleForUri(uriString: String?, defaultTitle: String = "نغمة المنبه الافتراضية"): String {
        if (uriString.isNullOrBlank() || uriString == URI_DEFAULT) {
            return "نغمة المنبه الافتراضية"
        }
        val preset = PRESET_OPTIONS.firstOrNull { it.uriString == uriString }
        return preset?.title ?: defaultTitle
    }

    /**
     * Resolves a sound URI string into a playable [Uri].
     *
     * Stricly enforces AGENTS.md Law 2 (Zero-Network Law):
     * Rejects network URIs (http, https, ftp) to prevent unauthorized network attempts.
     */
    fun resolveUri(context: Context, uriString: String?): Uri? {
        if (uriString.isNullOrBlank() || uriString == URI_DEFAULT) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }

        return when (uriString) {
            URI_ADHAN_FAJR -> Uri.parse("android.resource://${context.packageName}/${R.raw.adhan_fajr}")
            URI_EID_TAKBEER -> Uri.parse("android.resource://${context.packageName}/${R.raw.eid_takbeer}")
            URI_CALM_TONE -> Uri.parse("android.resource://${context.packageName}/${R.raw.calm_tone}")
            URI_QURAN_ALERT -> Uri.parse("android.resource://${context.packageName}/${R.raw.quran_alert}")
            else -> {
                try {
                    val parsed = Uri.parse(uriString)
                    val scheme = parsed.scheme?.lowercase()
                    val allowedSchemes = setOf("content", "android.resource", "file")
                    if (scheme in allowedSchemes) {
                        parsed
                    } else {
                        // Strict Whitelist rejects network/streaming schemes (http, https, ftp, rtsp, ws, smb) per AGENTS.md Law 2
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}

