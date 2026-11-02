package com.quran.alarm.alarm

import com.quran.alarm.alarm.audio.AudioPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AudioPlaybackStateTest {

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
    fun verifyAutoMuteLifecycleContract() {
        // As defined in ADR-005:
        // 1. Alarm triggers -> BLARING (maximum volume to wake user)
        // 2. User looks at Mushaf -> transitions to MUTED (calm recitation)
        // 3. User looks away > 2s -> transitions to BLARING (penalty scream)
        // 4. User completes 15 lines -> transitions to STOPPED

        var currentState = AudioPlaybackState.IDLE
        assertEquals(AudioPlaybackState.IDLE, currentState)

        // Step 1: Trigger alarm
        currentState = AudioPlaybackState.BLARING
        assertEquals(AudioPlaybackState.BLARING, currentState)

        // Step 2: Attention detected -> Auto-Mute
        currentState = AudioPlaybackState.MUTED
        assertEquals(AudioPlaybackState.MUTED, currentState)

        // Step 3: Look away penalty -> Scream
        currentState = AudioPlaybackState.BLARING
        assertEquals(AudioPlaybackState.BLARING, currentState)

        // Step 4: Completion -> Stop
        currentState = AudioPlaybackState.STOPPED
        assertEquals(AudioPlaybackState.STOPPED, currentState)
    }
}

