package com.quran.alarm.alarm.data.device

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class OemFamily(
    val displayNameArabic: String,
    val guidanceArabic: String,
    val supportsAutoStartSettings: Boolean = false
) {
    XIAOMI(
        displayNameArabic = "Xiaomi / Redmi / POCO (HyperOS / MIUI)",
        guidanceArabic = "فعّل «التشغيل التلقائي» (AutoStart) و«عرض النوافذ المنبثقة في الخلفية»، واضبط موفر البطارية على «بلا قيود».",
        supportsAutoStartSettings = true
    ),
    SAMSUNG(
        displayNameArabic = "Samsung (OneUI)",
        guidanceArabic = "أضف التطبيق إلى «التطبيقات غير الموضوعة في السكون» واضبط البطارية على «غير مقيد».",
        supportsAutoStartSettings = false
    ),
    HUAWEI(
        displayNameArabic = "Huawei / Honor (EMUI / HarmonyOS)",
        guidanceArabic = "انتقل إلى تشغيل التطبيقات (App Launch) وقم بإدارته يدوياً مع تفعيل التشغيل التلقائي والتشغيل في الخلفية.",
        supportsAutoStartSettings = true
    ),
    OPPO_REALME(
        displayNameArabic = "OPPO / Realme / OnePlus (ColorOS)",
        guidanceArabic = "اسمح بالتشغيل التلقائي والنشاط في الخلفية من إعدادات البطارية.",
        supportsAutoStartSettings = true
    ),
    VIVO(
        displayNameArabic = "Vivo / iQOO (FuntouchOS)",
        guidanceArabic = "فعّل التشغيل التلقائي واستهلاك الطاقة العالي في الخلفية.",
        supportsAutoStartSettings = true
    ),
    GENERIC(
        displayNameArabic = "أندرويد القياسي",
        guidanceArabic = "استثنِ التطبيق من تحسين البطارية لضمان استيقاظ المنبه في موعده.",
        supportsAutoStartSettings = false
    )
}

data class DeviceReadinessStatus(
    val oemFamily: OemFamily,
    val manufacturer: String,
    val isNotificationsGranted: Boolean,
    val isOverlayGranted: Boolean,
    val isFullScreenIntentGranted: Boolean,
    val isExactAlarmGranted: Boolean,
    val isBatteryOptimizationIgnored: Boolean
) {
    val isCoreAlertPermissionsGranted: Boolean
        get() = isFullScreenIntentGranted && isOverlayGranted

    val needsAttention: Boolean
        get() = !isNotificationsGranted ||
            !isOverlayGranted ||
            !isFullScreenIntentGranted ||
            !isBatteryOptimizationIgnored
}

object OemDeviceHelper {

    fun detectOemFamily(manufacturer: String = Build.MANUFACTURER.orEmpty()): OemFamily {
        val m = manufacturer.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> OemFamily.XIAOMI
            m.contains("samsung") -> OemFamily.SAMSUNG
            m.contains("huawei") || m.contains("honor") -> OemFamily.HUAWEI
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> OemFamily.OPPO_REALME
            m.contains("vivo") || m.contains("iqoo") -> OemFamily.VIVO
            else -> OemFamily.GENERIC
        }
    }

    fun getReadinessStatus(context: Context): DeviceReadinessStatus {
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        val fullScreenGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.canUseFullScreenIntent() ?: true
        } else {
            true
        }

        val exactAlarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.canScheduleExactAlarms() ?: true
        } else {
            true
        }

        val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }

        return DeviceReadinessStatus(
            oemFamily = detectOemFamily(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            isNotificationsGranted = notificationsGranted,
            isOverlayGranted = overlayGranted,
            isFullScreenIntentGranted = fullScreenGranted,
            isExactAlarmGranted = exactAlarmGranted,
            isBatteryOptimizationIgnored = batteryIgnored
        )
    }

    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val directPrompt = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
            if (launchSafe(context, directPrompt)) return
            launchSafe(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } else {
            launchSafe(context, Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        if (!launchSafe(context, intent)) {
            openAppDetailsSettings(context)
        }
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            launchSafe(
                context,
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            )
        }
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            launchSafe(
                context,
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
            )
        } else {
            openAppDetailsSettings(context)
        }
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            launchSafe(
                context,
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            )
        }
    }

    fun isPhoneCallActive(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val mode = am?.mode ?: AudioManager.MODE_NORMAL
            mode == AudioManager.MODE_RINGTONE ||
                mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION
        } catch (_: Exception) {
            false
        }
    }

    fun autoStartCandidatesFor(family: OemFamily, packageName: String): List<Intent> {
        return when (family) {
            OemFamily.XIAOMI -> listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"))
                    .putExtra("extra_pkgname", packageName),
                Intent("miui.intent.action.APP_PERM_EDITOR")
                    .putExtra("extra_pkgname", packageName)
            )
            OemFamily.SAMSUNG -> emptyList()
            OemFamily.HUAWEI -> listOf(
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
            )
            OemFamily.OPPO_REALME -> listOf(
                Intent().setComponent(ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"))
            )
            OemFamily.VIVO -> listOf(
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
            )
            OemFamily.GENERIC -> emptyList()
        }
    }

    /**
     * Attempts to open OEM-specific AutoStart / Battery settings ordered for [detectOemFamily].
     * Returns `true` if an OEM-specific screen opened, or `false` if it fell back to App Details.
     */
    fun openOemAutoStartSettings(context: Context): Boolean {
        val family = detectOemFamily()
        for (intent in autoStartCandidatesFor(family, context.packageName)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launchSafe(context, intent)) return true
        }
        openAppDetailsSettings(context)
        return false
    }

    internal fun openAppDetailsSettings(context: Context) {
        launchSafe(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        )
    }

    private fun launchSafe(context: Context, intent: Intent): Boolean {
        return try {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
