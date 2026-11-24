package com.quran.alarm.alarm.data.storage

import android.content.Context
import android.os.Build
import android.os.UserManager
import java.io.File

/**
 * Shared helper for resolving Device-Protected Storage on API 24+ (Direct Boot)
 * and migrating existing preferences/databases once the user unlocks the device.
 */
object DirectBootStorageHelper {

    private const val MIGRATION_PREFS = "_direct_boot_migration_state"
    private val migratedKeys = mutableSetOf<String>()

    fun resolveStorageContext(
        context: Context,
        prefsNamesToMigrate: List<String> = emptyList(),
        dbNamesToMigrate: List<String> = emptyList()
    ): Context {
        val appContext = context.applicationContext ?: context
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return appContext
        }
        val deviceContext = if (appContext.isDeviceProtectedStorage) {
            appContext
        } else {
            appContext.createDeviceProtectedStorageContext()
        }
        if (prefsNamesToMigrate.isEmpty() && dbNamesToMigrate.isEmpty()) {
            return deviceContext
        }
        try {
            val userManager = appContext.getSystemService(Context.USER_SERVICE) as? UserManager
            if (userManager?.isUserUnlocked == true) {
                synchronized(migratedKeys) {
                    val migrationPrefs = deviceContext.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
                    val editor = migrationPrefs.edit()
                    var changed = false

                    for (prefsName in prefsNamesToMigrate) {
                        val key = "prefs_$prefsName"
                        if (key in migratedKeys || migrationPrefs.getBoolean(key, false)) {
                            migratedKeys.add(key)
                            continue
                        }
                        // Only migrate CE prefs if DE does not already have newer data
                        val dePrefsFile = File(File(deviceContext.dataDir, "shared_prefs"), "$prefsName.xml")
                        if (!dePrefsFile.exists()) {
                            runCatching { deviceContext.moveSharedPreferencesFrom(appContext, prefsName) }
                        }
                        migratedKeys.add(key)
                        editor.putBoolean(key, true)
                        changed = true
                    }

                    for (dbName in dbNamesToMigrate) {
                        val key = "db_$dbName"
                        if (key in migratedKeys || migrationPrefs.getBoolean(key, false)) {
                            migratedKeys.add(key)
                            continue
                        }
                        val deDbFile = deviceContext.getDatabasePath(dbName)
                        if (!deDbFile.exists()) {
                            runCatching { deviceContext.moveDatabaseFrom(appContext, dbName) }
                        }
                        migratedKeys.add(key)
                        editor.putBoolean(key, true)
                        changed = true
                    }

                    if (changed) {
                        editor.apply()
                    }
                }
            }
        } catch (_: Exception) {
            // Storage locked or already migrated
        }
        return deviceContext
    }
}
