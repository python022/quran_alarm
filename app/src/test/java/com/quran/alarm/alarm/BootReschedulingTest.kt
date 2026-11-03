package com.quran.alarm.alarm

import com.quran.alarm.alarm.domain.model.AlarmItem
import com.quran.alarm.alarm.domain.repository.AlarmRepository
import com.quran.alarm.alarm.domain.scheduler.AlarmScheduler
import com.quran.alarm.alarm.receiver.BootReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
    fun calculateNextTriggerTime_advancesPastCurrentTime() {
        val pastTime = 1000L
        val now = 5000L
        val nextTime = BootReceiver.calculateNextTriggerTime(pastTime, now)

        assertTrue(nextTime > now)
        assertEquals(pastTime + 24 * 60 * 60 * 1000L, nextTime)
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

        receiver.rescheduleAllAlarms(android.app.Application())

        assertEquals(2, scheduler.scheduledAlarms.size)
        assertTrue(scheduler.scheduledAlarms.any { it.id == 1 })
        assertTrue(scheduler.scheduledAlarms.any { it.id == 2 })
    }

    @Test
    fun verifyBootPermissionInManifest() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            assertTrue("RECEIVE_BOOT_COMPLETED must be declared", content.contains("RECEIVE_BOOT_COMPLETED"))
            assertTrue("BootReceiver must be declared", content.contains("BootReceiver"))
        }
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

        receiver.rescheduleAllAlarms(android.app.Application())

        // Must NOT be scheduled!
        assertTrue(scheduler.scheduledAlarms.none { it.id == 99 })
        // Must be saved as isEnabled = false
        val saved = repo.getAlarmById(99)
        org.junit.Assert.assertNotNull(saved)
        org.junit.Assert.assertFalse(saved!!.isEnabled)
    }
}
