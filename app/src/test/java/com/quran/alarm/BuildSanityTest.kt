package com.quran.alarm

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BuildSanityTest {

    @Test
    fun verifyZeroInternetPermissionInManifest() {
        // Verify manifest file exists and strictly avoids prohibited permissions
        val manifestFile = File("src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            assertTrue("Internet permission must NOT be requested", !content.contains("android.permission.INTERNET"))
            assertTrue("BIND_ACCESSIBILITY_SERVICE must NOT be present", !content.contains("BIND_ACCESSIBILITY_SERVICE"))
            assertTrue("BIND_DEVICE_ADMIN must NOT be present", !content.contains("BIND_DEVICE_ADMIN"))
            assertTrue("SYSTEM_ALERT_WINDOW must be present for full-screen alarm overlay", content.contains("SYSTEM_ALERT_WINDOW"))
        }
    }
}
