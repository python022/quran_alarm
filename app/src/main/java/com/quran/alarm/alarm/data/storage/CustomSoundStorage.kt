package com.quran.alarm.alarm.data.storage

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Result of importing or validating a custom alarm audio selection.
 */
data class ImportedAudioResult(
    val copiedFile: File?,
    val uriString: String,
    val displayName: String
)

/**
 * Centralized storage and security-validation helper for custom alarm sound files
 * stored in Device-Protected Storage (`custom_sounds`).
 *
 * Eliminates path-traversal, filename collisions, Direct Boot inaccessible external URIs,
 * and orphaned temporary files.
 */
object CustomSoundStorage {

    const val CUSTOM_SOUNDS_DIR = "custom_sounds"
    const val MAX_CUSTOM_AUDIO_BYTES = 15L * 1024L * 1024L
    const val DEFAULT_ORPHAN_GRACE_MS = 60L * 60L * 1000L

    fun getCustomSoundsDir(context: Context): File {
        val storageContext = DirectBootStorageHelper.resolveStorageContext(context)
        return File(storageContext.filesDir, CUSTOM_SOUNDS_DIR)
    }

    /**
     * Resolves a `file://` URI string only if its canonical path resides strictly inside
     * the app's Device-Protected `custom_sounds` directory.
     */
    fun resolveSafeCustomSoundFile(context: Context, uriString: String): File? {
        return try {
            val parsed = Uri.parse(uriString)
            if (!parsed.scheme.equals("file", ignoreCase = true)) return null
            if (!parsed.authority.isNullOrEmpty() || !parsed.query.isNullOrEmpty() || !parsed.fragment.isNullOrEmpty()) {
                return null
            }
            val path = parsed.path ?: return null
            val customDir = getCustomSoundsDir(context).canonicalFile
            val targetFile = File(path).canonicalFile
            if (targetFile.path.startsWith(customDir.path + File.separator)) {
                targetFile
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Copies an audio document from [uri] into Device-Protected `custom_sounds` with a
     * collision-free UUID filename, enforcing size and MediaMetadataRetriever audio checks.
     */
    fun importAudioFromUri(
        context: Context,
        uri: Uri,
        fallbackDisplayName: String = "ملف صوتي مخصص"
    ): ImportedAudioResult? {
        val dir = getCustomSoundsDir(context).apply { mkdirs() }
        val target = File(dir, "alarm_sound_${UUID.randomUUID()}.audio")
        return try {
            val mimeType = context.contentResolver.getType(uri)?.lowercase()
            if (mimeType != null && isExplicitlyNonAudioMime(mimeType)) {
                return null
            }

            var exceededLimit = false
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_CUSTOM_AUDIO_BYTES) {
                            exceededLimit = true
                            break
                        }
                        output.write(buffer, 0, read)
                    }
                }
                !exceededLimit
            } ?: false

            if (!copied || exceededLimit || !isPlayableAudioFile(target)) {
                target.delete()
                return null
            }

            val displayName = queryDisplayName(context, uri) ?: fallbackDisplayName
            ImportedAudioResult(
                copiedFile = target,
                uriString = Uri.fromFile(target).toString(),
                displayName = displayName
            )
        } catch (_: Exception) {
            runCatching { target.delete() }
            null
        }
    }

    /**
     * Validates a URI returned from the system Ringtone Picker.
     * - Rejects non-whitelisted/network schemes (e.g. http/https/ftp).
     * - Imports local `file://` and external `content://` URIs into Device-Protected `custom_sounds`
     *   storage so Direct Boot alarms remain playable before device unlock.
     * - Allows `android.resource://` and system `content://settings/...` ringtones directly.
     */
    fun validateRingtoneUri(context: Context, uri: Uri): ImportedAudioResult? {
        return try {
            val scheme = uri.scheme?.lowercase() ?: return null
            when (scheme) {
                "file" -> {
                    val existingProtected = resolveSafeCustomSoundFile(context, uri.toString())
                    if (existingProtected != null && existingProtected.exists()) {
                        ImportedAudioResult(
                            copiedFile = null,
                            uriString = uri.toString(),
                            displayName = existingProtected.name
                        )
                    } else {
                        importAudioFromUri(context, uri)
                    }
                }
                "android.resource" -> {
                    val ringtone = RingtoneManager.getRingtone(context, uri) ?: return null
                    val title = runCatching { ringtone.getTitle(context) }.getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: "نغمة هاتف"
                    ImportedAudioResult(
                        copiedFile = null,
                        uriString = uri.toString(),
                        displayName = title
                    )
                }
                "content" -> {
                    val ringtone = RingtoneManager.getRingtone(context, uri) ?: return null
                    val title = runCatching { ringtone.getTitle(context) }.getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: "نغمة هاتف"
                    if (uri.authority == "settings" || RingtoneManager.isDefault(uri)) {
                        ImportedAudioResult(
                            copiedFile = null,
                            uriString = uri.toString(),
                            displayName = title
                        )
                    } else {
                        importAudioFromUri(context, uri, fallbackDisplayName = title)
                            ?: if (uri.authority == "media") {
                                ImportedAudioResult(
                                    copiedFile = null,
                                    uriString = uri.toString(),
                                    displayName = title
                                )
                            } else {
                                null
                            }
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun deleteIfUnused(context: Context, uriString: String, stillReferenced: Boolean) {
        if (stillReferenced) return
        try {
            val targetFile = resolveSafeCustomSoundFile(context, uriString) ?: return
            if (targetFile.exists()) {
                targetFile.delete()
            }
        } catch (_: Exception) {}
    }

    /**
     * Deletes orphaned files in `custom_sounds` that are not referenced by any saved alarm
     * and are older than [minAgeMs] (to avoid deleting files currently staged in an open dialog).
     */
    fun cleanupOrphanedSounds(
        context: Context,
        referencedUriStrings: Set<String>,
        minAgeMs: Long = DEFAULT_ORPHAN_GRACE_MS,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        try {
            val customDir = getCustomSoundsDir(context).canonicalFile
            if (!customDir.exists() || !customDir.isDirectory) return
            val referencedPaths = referencedUriStrings.mapNotNull { uriStr ->
                resolveSafeCustomSoundFile(context, uriStr)?.canonicalPath
            }.toSet()
            val files = customDir.listFiles() ?: return
            for (file in files) {
                if (!file.isFile) continue
                val canonical = file.canonicalPath
                val ageMs = nowMillis - file.lastModified()
                if (canonical !in referencedPaths && ageMs >= minAgeMs) {
                    runCatching { file.delete() }
                }
            }
        } catch (_: Exception) {}
    }

    private fun isExplicitlyNonAudioMime(mimeType: String): Boolean {
        return mimeType.startsWith("text/") ||
            mimeType.startsWith("image/") ||
            mimeType == "application/pdf" ||
            mimeType == "application/zip"
    }

    private fun isPlayableAudioFile(file: File): Boolean {
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                hasAudio == "yes" || duration > 0L
            } finally {
                retriever.release()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
