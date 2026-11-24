package com.quran.alarm.alarm

import android.content.Context
import android.provider.Settings
import com.quran.alarm.alarm.data.device.DeviceReadinessStatus
import com.quran.alarm.alarm.data.device.OemDeviceHelper
import com.quran.alarm.alarm.data.device.OemFamily
import com.quran.alarm.alarm.data.repository.AlarmRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OemDeviceAndSanitizationTest {

    @Test
    fun detectOemFamily_recognizesMajorManufacturers() {
        assertEquals(OemFamily.XIAOMI, OemDeviceHelper.detectOemFamily("Xiaomi"))
        assertEquals(OemFamily.XIAOMI, OemDeviceHelper.detectOemFamily("Redmi"))
        assertEquals(OemFamily.XIAOMI, OemDeviceHelper.detectOemFamily("POCO"))
        assertEquals(OemFamily.SAMSUNG, OemDeviceHelper.detectOemFamily("samsung"))
        assertEquals(OemFamily.HUAWEI, OemDeviceHelper.detectOemFamily("HUAWEI"))
        assertEquals(OemFamily.HUAWEI, OemDeviceHelper.detectOemFamily("HONOR"))
        assertEquals(OemFamily.OPPO_REALME, OemDeviceHelper.detectOemFamily("realme"))
        assertEquals(OemFamily.VIVO, OemDeviceHelper.detectOemFamily("vivo"))
        assertEquals(OemFamily.GENERIC, OemDeviceHelper.detectOemFamily("Google"))
    }

    @Test
    fun supportsAutoStartSettings_isHiddenForSamsungAndGoogle_andShownForOtherMajorOems() {
        assertFalse(OemFamily.SAMSUNG.supportsAutoStartSettings)
        assertFalse(OemFamily.GENERIC.supportsAutoStartSettings)
        assertTrue(OemFamily.XIAOMI.supportsAutoStartSettings)
        assertTrue(OemFamily.HUAWEI.supportsAutoStartSettings)
        assertTrue(OemFamily.OPPO_REALME.supportsAutoStartSettings)
        assertTrue(OemFamily.VIVO.supportsAutoStartSettings)
        assertTrue(OemDeviceHelper.autoStartCandidatesFor(OemFamily.SAMSUNG, "com.quran.alarm").isEmpty())
    }

    @Test
    fun openBatteryOptimizationSettings_launchesDirectRequestIgnoreDialogIntent() {
        val app = RuntimeEnvironment.getApplication()
        OemDeviceHelper.openBatteryOptimizationSettings(app)

        val started = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull("Must launch direct battery optimization dialog", started)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, started.action)
        assertEquals("package:${app.packageName}", started.dataString)
    }

    @Test
    fun deviceReadinessStatus_ignoresExactAlarmForNeedsAttention_andTracksCorePermissions() {
        val coreMissingFullScreen = DeviceReadinessStatus(
            oemFamily = OemFamily.SAMSUNG,
            manufacturer = "samsung",
            isNotificationsGranted = true,
            isOverlayGranted = true,
            isFullScreenIntentGranted = false,
            isExactAlarmGranted = false,
            isBatteryOptimizationIgnored = true
        )
        assertFalse(coreMissingFullScreen.isCoreAlertPermissionsGranted)
        assertTrue(coreMissingFullScreen.needsAttention)

        val allGrantedExceptExactAlarm = coreMissingFullScreen.copy(isFullScreenIntentGranted = true)
        assertTrue(allGrantedExceptExactAlarm.isCoreAlertPermissionsGranted)
        assertFalse(allGrantedExceptExactAlarm.needsAttention)
    }

    @Test
    fun repository_sanitizesCorruptedOutOfRangePrefsWithoutCrashing() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("corrupted_test_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("key_alarm_ids", setOf("99"))
            .putString(
                "alarm_json_99",
                """
                {
                  "id": 99,
                  "hour": 99,
                  "minute": -5,
                  "startPage": 999,
                  "pageCount": 0,
                  "volumeLevel": 250,
                  "repeatDays": [1, 99, -2, 7]
                }
                """.trimIndent()
            )
            .commit()

        val repo = AlarmRepositoryImpl(context, prefs)
        val alarm = repo.getAlarmById(99)
        assertNotNull(alarm)
        assertEquals(23, alarm!!.hour)
        assertEquals(0, alarm.minute)
        assertEquals(604, alarm.startPage)
        assertEquals(1, alarm.pageCount)
        assertEquals(100, alarm.volumeLevel)
        assertEquals(setOf(1, 7), alarm.repeatDays)
    }
}
