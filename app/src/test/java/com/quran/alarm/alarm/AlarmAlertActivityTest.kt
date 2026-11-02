package com.quran.alarm.alarm

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AlarmAlertActivityTest {

    @Test
    fun verifyAlertActivityInManifest() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            assertTrue("AlarmAlertActivity must be declared in AndroidManifest", content.contains("AlarmAlertActivity"))
            assertTrue("showWhenLocked must be enabled", content.contains("showWhenLocked=\"true\""))
            assertTrue("turnScreenOn must be enabled", content.contains("turnScreenOn=\"true\""))
        }
    }
}

