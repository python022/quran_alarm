package com.quran.alarm.alarm

import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
import com.quran.alarm.alarm.receiver.BootReceiver
import com.quran.alarm.alarm.receiver.TimeAndPackageChangeReceiver
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootReschedulingTest {

    private class TestAlarmRepository : AlarmRepository {
        val storage = mutableMapOf<Int, AlarmItem>()

        override fun saveAlarm(alarm: AlarmItem) {
            storage[alarm.id] = alarm
        }

        override fun getAllAlarms(): List<AlarmItem> = storage.values.toList()

        override fun getActiveAlarms(): List<AlarmItem> = storage.values.filter { it.isEnabled }

        override fun getAlarmById(id: Int): AlarmItem? = storage[id]

        override fun deleteAlarm(id: Int) {
            storage.remove(id)
        }
    }

    private class TestAlarmScheduler : AlarmScheduler {
        val scheduledAlarms = mutableListOf<AlarmItem>()

        override fun schedule(item: AlarmItem) {
            scheduledAlarms.add(item)
        }

        override fun cancel(item: AlarmItem) {
            scheduledAlarms.removeAll { it.id == item.id }
        }

        override fun cancel(alarmId: Int) {
            scheduledAlarms.removeAll { it.id == alarmId }
        }
    }

    @Test
    fun oneShotAlarm_isOneShotExpired_detectsElapsedTime() {
        val now = System.currentTimeMillis()
        val pastAlarm = AlarmItem(id = 10, timeInMillis = now - 60_000L, isEnabled = true)
        val futureAlarm = AlarmItem(id = 11, timeInMillis = now + 60_000L, isEnabled = true)

        assertTrue(pastAlarm.isOneShotExpired(now))
        assertTrue(!futureAlarm.isOneShotExpired(now))
    }

    @Test
    fun rescheduleAllAlarms_reschedulesActiveAlarmsSuccessfully() {
        val repo = TestAlarmRepository()
        val scheduler = TestAlarmScheduler()

        val futureTime = System.currentTimeMillis() + 3600_000L
        val activeAlarm1 = AlarmItem(id = 1, timeInMillis = futureTime, isEnabled = true)
        val activeAlarm2 = AlarmItem(id = 2, timeInMillis = futureTime + 1000L, isEnabled = true)
        val disabledAlarm = AlarmItem(id = 3, timeInMillis = futureTime + 2000L, isEnabled = false)

        repo.saveAlarm(activeAlarm1)
        repo.saveAlarm(activeAlarm2)
        repo.saveAlarm(disabledAlarm)

        val receiver = BootReceiver().apply {
            repositoryProvider = { repo }
            schedulerProvider = { scheduler }
        }

        receiver.rescheduleAllAlarms(RuntimeEnvironment.getApplication())

        assertEquals(2, scheduler.scheduledAlarms.size)
        assertTrue(scheduler.scheduledAlarms.any { it.id == 1 })
        assertTrue(scheduler.scheduledAlarms.any { it.id == 2 })
    }

    @Test
    fun verifyBootPermissionInManifest() {
        val manifestFile = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() }
        assertNotNull("AndroidManifest.xml must exist for verification", manifestFile)
        val content = manifestFile!!.readText()
        assertTrue("RECEIVE_BOOT_COMPLETED must be declared", content.contains("RECEIVE_BOOT_COMPLETED"))
        assertTrue("USE_EXACT_ALARM must be declared", content.contains("USE_EXACT_ALARM"))
        assertTrue("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS must be declared", content.contains("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
        assertTrue("BootReceiver must be declared", content.contains("BootReceiver"))
        assertTrue("TimeAndPackageChangeReceiver must be declared", content.contains("TimeAndPackageChangeReceiver"))
        assertTrue("HTC quickboot action must be declared", content.contains("com.htc.intent.action.QUICKBOOT_POWERON"))
        assertTrue("BootReceiver must require RECEIVE_BOOT_COMPLETED permission", content.contains("android:permission=\"android.permission.RECEIVE_BOOT_COMPLETED\""))
        assertTrue("directBootAware must be set on BootReceiver", content.contains("android:directBootAware=\"true\""))
        val normalized = content.replace("\r\n", "\n")
        assertTrue("AlarmReceiver must be directBootAware for locked ringing", normalized.contains("AlarmReceiver\"\n            android:directBootAware=\"true\""))
        assertTrue("AlarmForegroundService must be directBootAware", normalized.contains("AlarmForegroundService\"\n            android:directBootAware=\"true\""))
    }

    @Test
    fun expiredOneShotAlarm_isMarkedDisabled_andNotRescheduledAsGhostAlarm() {
        val repo = TestAlarmRepository()
        val scheduler = TestAlarmScheduler()

        val pastTime = System.currentTimeMillis() - 3600_000L
        // One-shot alarm whose trigger expired while phone was off
        val expiredOneShot = AlarmItem(
            id = 99,
            hour = 5,
            minute = 0,
            repeatDays = emptySet(),
            isEnabled = true,
            explicitTriggerMillis = pastTime
        )
        repo.saveAlarm(expiredOneShot)

        val receiver = BootReceiver().apply {
            repositoryProvider = { repo }
            schedulerProvider = { scheduler }
        }

        receiver.rescheduleAllAlarms(RuntimeEnvironment.getApplication())

        // Must NOT be scheduled!
        assertTrue(scheduler.scheduledAlarms.none { it.id == 99 })
        // Must be saved as isEnabled = false
        val saved = repo.getAlarmById(99)
        assertNotNull(saved)
        assertFalse(saved!!.isEnabled)
    }

    @Test
    fun overnightOneShotAlarm_withNullExplicitTrigger_staysEnabledAndReschedulesForTomorrow() {
        val repo = TestAlarmRepository()
        val scheduler = TestAlarmScheduler()

        // One-shot alarm without explicitTriggerMillis (legacy/cleared) scheduled for an earlier hour (i.e., tomorrow)
        val now = java.util.Calendar.getInstance()
        val earlierHour = (now.get(java.util.Calendar.HOUR_OF_DAY) + 23) % 24
        val overnightAlarm = AlarmItem(
            id = 98,
            hour = earlierHour,
            minute = 0,
            repeatDays = emptySet(),
            isEnabled = true,
            explicitTriggerMillis = null
        )
        repo.saveAlarm(overnightAlarm)

        val receiver = BootReceiver().apply {
            repositoryProvider = { repo }
            schedulerProvider = { scheduler }
        }

        receiver.rescheduleAllAlarms(RuntimeEnvironment.getApplication())

        assertTrue("Overnight one-shot alarm must stay rescheduled", scheduler.scheduledAlarms.any { it.id == 98 })
        val saved = repo.getAlarmById(98)
        assertNotNull(saved)
        assertTrue("Overnight one-shot alarm must remain enabled", saved!!.isEnabled)
        assertNotNull("BootReceiver must backfill explicitTriggerMillis", saved.explicitTriggerMillis)
        assertTrue("Backfilled trigger must be in the future", saved.explicitTriggerMillis!! > System.currentTimeMillis())
    }

    @Test
    fun repeatingAlarm_onTimeChange_forceRecalculatesExplicitTrigger() {
        val repo = TestAlarmRepository()
        val scheduler = TestAlarmScheduler()
        val repeating = AlarmItem(
            id = 105,
            hour = 5,
            minute = 0,
            repeatDays = setOf(java.util.Calendar.MONDAY, java.util.Calendar.FRIDAY),
            isEnabled = true,
            explicitTriggerMillis = System.currentTimeMillis() + 10 * 24 * 3600_000L
        )
        repo.saveAlarm(repeating)

        val receiver = BootReceiver().apply {
            repositoryProvider = { repo }
            schedulerProvider = { scheduler }
        }

        receiver.rescheduleAllAlarms(RuntimeEnvironment.getApplication(), forceRecalculateRepeating = true)

        assertTrue(scheduler.scheduledAlarms.any { it.id == 105 })
        val saved = repo.getAlarmById(105)!!
        assertEquals(repeating.copy(explicitTriggerMillis = null).getNextTriggerMillis(), saved.explicitTriggerMillis)
    }

    @Test
    fun timeAndPackageChangeReceiver_inheritsBootReceiver_andRecalculatesOnTimeChanged() {
        assertTrue(
            "TimeAndPackageChangeReceiver must inherit BootReceiver so goAsync() works on the active receiver",
            BootReceiver::class.java.isAssignableFrom(TimeAndPackageChangeReceiver::class.java)
        )

        val repo = TestAlarmRepository()
        val scheduler = TestAlarmScheduler()
        val repeating = AlarmItem(
            id = 106,
            hour = 5,
            minute = 0,
            repeatDays = setOf(java.util.Calendar.MONDAY, java.util.Calendar.FRIDAY),
            isEnabled = true,
            explicitTriggerMillis = System.currentTimeMillis() + 10 * 24 * 3600_000L
        )
        repo.saveAlarm(repeating)

        val receiver = TimeAndPackageChangeReceiver().apply {
            repositoryProvider = { repo }
            schedulerProvider = { scheduler }
        }
        receiver.onReceive(RuntimeEnvironment.getApplication(), Intent(Intent.ACTION_TIME_CHANGED))

        assertTrue(scheduler.scheduledAlarms.any { it.id == 106 })
        val saved = repo.getAlarmById(106)!!
        assertEquals(repeating.copy(explicitTriggerMillis = null).getNextTriggerMillis(), saved.explicitTriggerMillis)
    }
}
