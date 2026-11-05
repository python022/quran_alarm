package com.quran.alarm.alarm

import android.content.Context
import com.quran.alarm.alarm.audio.AudioPlaybackState
import com.quran.alarm.alarm.audio.SmartAlarmAudioManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioPlaybackStateTest {

    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var smartAudioManager: SmartAlarmAudioManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        smartAudioManager = SmartAlarmAudioManager(context, testScope)
    }

    @Test
    fun verifyAllStatesExist() {
        val states = AudioPlaybackState.values()
        assertEquals(4, states.size)
        assertEquals(AudioPlaybackState.IDLE, AudioPlaybackState.valueOf("IDLE"))
        assertEquals(AudioPlaybackState.BLARING, AudioPlaybackState.valueOf("BLARING"))
        assertEquals(AudioPlaybackState.MUTED, AudioPlaybackState.valueOf("MUTED"))
        assertEquals(AudioPlaybackState.STOPPED, AudioPlaybackState.valueOf("STOPPED"))
    }

    @Test
    fun verifySmartAudioManagerStateTransitions() = runTest(testDispatcher) {
        // Initial state is IDLE
        assertEquals(AudioPlaybackState.IDLE, smartAudioManager.playbackState.value)

        // smoothMute guard: must not mute when IDLE
        smartAudioManager.smoothMute(durationMs = 0L)
        testScheduler.advanceUntilIdle()
        assertEquals(AudioPlaybackState.IDLE, smartAudioManager.playbackState.value)

        // 1. Alarm triggers -> BLARING
        smartAudioManager.startAlarm(soundUriString = null, volumeLevel = 80)
        assertEquals(AudioPlaybackState.BLARING, smartAudioManager.playbackState.value)

        // 2. User looks at Mushaf -> transitions to MUTED
        smartAudioManager.smoothMute(durationMs = 0L)
        testScheduler.advanceUntilIdle()
        assertEquals(AudioPlaybackState.MUTED, smartAudioManager.playbackState.value)

        // 3. User looks away -> transitions to BLARING (instant scream)
        smartAudioManager.instantScream()
        assertEquals(AudioPlaybackState.BLARING, smartAudioManager.playbackState.value)

        // 4. Wird completion -> transitions to STOPPED
        smartAudioManager.stopAlarm()
        assertEquals(AudioPlaybackState.STOPPED, smartAudioManager.playbackState.value)
    }
}
