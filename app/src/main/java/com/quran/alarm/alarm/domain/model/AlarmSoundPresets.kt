package com.quran.alarm.alarm.domain.model

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
}
