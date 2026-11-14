package com.quran.alarm

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BuildSanityTest {

    private fun resolveManifestFile(): File {
        return listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() }
            ?: error("AndroidManifest.xml must exist in src/main or app/src/main")
    }

    @Test
    fun verifyZeroInternetPermissionInManifest() {
        val manifestFile = resolveManifestFile()
        val content = manifestFile.readText()
        assertTrue("Internet permission must NOT be requested", !content.contains("android.permission.INTERNET"))
        assertTrue("BIND_ACCESSIBILITY_SERVICE must NOT be present", !content.contains("BIND_ACCESSIBILITY_SERVICE"))
        assertTrue("BIND_DEVICE_ADMIN must NOT be present", !content.contains("BIND_DEVICE_ADMIN"))
        assertTrue("SYSTEM_ALERT_WINDOW must be present for full-screen alarm overlay", content.contains("SYSTEM_ALERT_WINDOW"))
    }

    @Test
    fun verifyZeroNetworkApisInProductionSource() {
        val sourceDir = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val forbiddenApis = listOf("HttpURLConnection", "OkHttpClient", "Retrofit", "java.net.Socket", ".openStream(")
        val kotlinFiles = sourceDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Production Kotlin files must be found", kotlinFiles.isNotEmpty())
        for (file in kotlinFiles) {
            val content = file.readText()
            for (api in forbiddenApis) {
                assertTrue("Forbidden network API $api found in ${file.name}", !content.contains(api))
            }
        }
    }
}
