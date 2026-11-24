package com.quran.alarm.alarm.data.storage

import android.content.Context
import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomSoundStorageTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun resolveSafeCustomSoundFile_blocksTraversalAndPrefixBypass() {
        val dir = CustomSoundStorage.getCustomSoundsDir(context).apply { mkdirs() }
        val validFile = File(dir, "valid_alarm.audio").apply { writeText("test") }
        val validUri = Uri.fromFile(validFile).toString()

        assertNotNull(CustomSoundStorage.resolveSafeCustomSoundFile(context, validUri))

        val traversalUri = "file://${dir.absolutePath}/../shared_prefs/secret.xml"
        assertNull(CustomSoundStorage.resolveSafeCustomSoundFile(context, traversalUri))

        val siblingDir = File(dir.parentFile, "custom_sounds_evil").apply { mkdirs() }
        val siblingFile = File(siblingDir, "evil.audio").apply { writeText("evil") }
        assertNull(CustomSoundStorage.resolveSafeCustomSoundFile(context, Uri.fromFile(siblingFile).toString()))

        assertNull(CustomSoundStorage.resolveSafeCustomSoundFile(context, "$validUri?param=1"))
        assertNull(CustomSoundStorage.resolveSafeCustomSoundFile(context, "$validUri#frag"))
        assertNull(CustomSoundStorage.resolveSafeCustomSoundFile(context, "file://remote-host${validFile.absolutePath}"))
    }

    @Test
    fun validateRingtoneUri_rejectsNetworkSchemes() {
        assertNull(CustomSoundStorage.validateRingtoneUri(context, Uri.parse("https://example.com/alarm.mp3")))
        assertNull(CustomSoundStorage.validateRingtoneUri(context, Uri.parse("http://example.com/alarm.mp3")))
        assertNull(CustomSoundStorage.validateRingtoneUri(context, Uri.parse("rtsp://example.com/live")))
    }

    @Test
    fun cleanupOrphanedSounds_deletesOnlyUnreferencedExpiredFiles() {
        val dir = CustomSoundStorage.getCustomSoundsDir(context).apply { mkdirs() }
        val referenced = File(dir, "referenced.audio").apply { writeText("a") }
        val recentOrphan = File(dir, "recent_orphan.audio").apply { writeText("b") }
        val oldOrphan = File(dir, "old_orphan.audio").apply { writeText("c") }

        val now = System.currentTimeMillis()
        referenced.setLastModified(now - 10_000_000L)
        recentOrphan.setLastModified(now - 1_000L)
        oldOrphan.setLastModified(now - 10_000_000L)

        CustomSoundStorage.cleanupOrphanedSounds(
            context = context,
            referencedUriStrings = setOf(Uri.fromFile(referenced).toString()),
            minAgeMs = 3_600_000L,
            nowMillis = now
        )

        assertTrue("Referenced file must remain", referenced.exists())
        assertTrue("Recent orphan within grace period must remain", recentOrphan.exists())
        assertFalse("Expired unreferenced orphan must be deleted", oldOrphan.exists())
    }

    @Test
    fun importAudioFromUri_whenExceedingSizeLimit_deletesPartialFileAndReturnsNull() {
        val dir = CustomSoundStorage.getCustomSoundsDir(context).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }

        val uri = Uri.parse("content://test.custom.sounds/oversize.mp3")
        val oversizedStream = object : java.io.InputStream() {
            private var remaining = 16 * 1024 * 1024
            override fun read(): Int {
                if (remaining <= 0) return -1
                remaining--
                return 0x41
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val chunk = minOf(len, remaining)
                java.util.Arrays.fill(b, off, off + chunk, 0x41.toByte())
                remaining -= chunk
                return chunk
            }
        }
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(uri, oversizedStream)

        val imported = CustomSoundStorage.importAudioFromUri(context, uri)
        assertNull("Oversized custom audio must be rejected", imported)
        val leftoverFiles = dir.listFiles()?.toList().orEmpty()
        assertTrue("Partial oversized file must be deleted on failure, found: $leftoverFiles", leftoverFiles.isEmpty())
    }
}
