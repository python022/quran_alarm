package com.quran.alarm.alarm

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AlarmAlertActivityTest {

    @Test
    fun verifyAlertActivityInManifest() {
        val manifestFile = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() }
        assertNotNull("AndroidManifest.xml must exist for verification", manifestFile)
        val content = manifestFile!!.readText()
        assertTrue("AlarmAlertActivity must be declared in AndroidManifest", content.contains("AlarmAlertActivity"))
        assertTrue("showWhenLocked must be enabled", content.contains("showWhenLocked=\"true\""))
        assertTrue("turnScreenOn must be enabled", content.contains("turnScreenOn=\"true\""))
        assertTrue("singleInstance must be set", content.contains("launchMode=\"singleInstance\""))
        assertTrue("directBootAware must be set on AlarmAlertActivity", content.contains("android:directBootAware=\"true\""))
    }
}
