package com.quran.alarm.alarm.data.repository

import android.content.Context
import com.quran.alarm.alarm.domain.model.AlarmItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: AlarmRepositoryImpl

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        repository = AlarmRepositoryImpl(context)
    }

    @Test
    fun saveAlarm_persistsSoundUriAndSoundTitle() {
        val alarm = AlarmItem(
            id = 101,
            hour = 5,
            minute = 30,
            repeatDays = setOf(java.util.Calendar.FRIDAY),
            isEnabled = true,
            label = "صلاة الجمعة والكهف",
            targetPage = 293,
            startPage = 293,
            pageCount = 11,
            volumeLevel = 90,
            soundUri = "builtin://adhan_fajr",
            soundTitle = "أذان الفجر"
        )

        repository.saveAlarm(alarm)

        val retrieved = repository.getAlarmById(101)
        assertNotNull(retrieved)
        assertEquals("builtin://adhan_fajr", retrieved!!.soundUri)
        assertEquals("أذان الفجر", retrieved.soundTitle)
        assertEquals(90, retrieved.volumeLevel)
        assertEquals("صلاة الجمعة والكهف", retrieved.label)
    }

    @Test
    fun saveAlarm_updatesSoundUriAndSoundTitleCorrectly() {
        val original = AlarmItem(
            id = 202,
            hour = 6,
            minute = 0,
            soundUri = "builtin://calm_tone",
            soundTitle = "نغمة هادئة"
        )
        repository.saveAlarm(original)

        val updated = original.copy(
            soundUri = "content://media/external/audio/123",
            soundTitle = "نغمة مخصصة"
        )
        repository.saveAlarm(updated)

        val retrieved = repository.getAlarmById(202)
        assertNotNull(retrieved)
        assertEquals("content://media/external/audio/123", retrieved!!.soundUri)
        assertEquals("نغمة مخصصة", retrieved.soundTitle)
    }

    @Test
    fun deleteAlarm_removesSoundProperties() {
        val alarm = AlarmItem(
            id = 303,
            hour = 4,
            minute = 45,
            soundUri = "builtin://eid_takbeer",
            soundTitle = "تكبيرات العيد"
        )
        repository.saveAlarm(alarm)
        assertNotNull(repository.getAlarmById(303))

        repository.deleteAlarm(303)
        assertNull(repository.getAlarmById(303))
    }

    @Test
    fun saveAlarm_withNullSoundProperties_handlesNullsSafely() {
        val alarm = AlarmItem(
            id = 404,
            hour = 5,
            minute = 0,
            soundUri = null,
            soundTitle = null
        )
        repository.saveAlarm(alarm)

        val retrieved = repository.getAlarmById(404)
        assertNotNull(retrieved)
        assertNull(retrieved!!.soundUri)
        assertNull(retrieved.soundTitle)
    }

    @Test
    fun saveAlarms_persistsBatchInSingleTransaction_withAllFields() {
        val a1 = AlarmItem(
            id = 501,
            hour = 4,
            minute = 30,
            repeatDays = setOf(java.util.Calendar.MONDAY, java.util.Calendar.THURSDAY),
            label = "ورد الفجر",
            startPage = 10,
            pageCount = 4,
            volumeLevel = 75,
            wirdSurahNumber = 2,
            startAyah = 1,
            dailyAyahCount = 5
        )
        val a2 = AlarmItem(
            id = 502,
            hour = 21,
            minute = 15,
            isEnabled = false,
            label = "سورة الملك",
            startPage = 562,
            pageCount = 3,
            isSequential = false
        )

        repository.saveAlarms(listOf(a1, a2))

        val loaded1 = repository.getAlarmById(501)
        val loaded2 = repository.getAlarmById(502)
        assertEquals(a1, loaded1)
        assertEquals(a2, loaded2)
    }

    @Test
    fun saveAlarm_concurrentWrites_doNotLoseAnyAlarm() {
        val latch = java.util.concurrent.CountDownLatch(8)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(8)
        try {
            for (idx in 1..8) {
                executor.execute {
                    try {
                        repository.saveAlarm(
                            AlarmItem(
                                id = 600 + idx,
                                hour = 5,
                                minute = idx,
                                label = "Thread $idx"
                            )
                        )
                    } finally {
                        latch.countDown()
                    }
                }
            }
            assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }

        val allIds = repository.getAllAlarms().map { it.id }.toSet()
        for (idx in 1..8) {
            assertTrue("Concurrent save must not lose alarm ${600 + idx}", (600 + idx) in allIds)
        }
    }
}

