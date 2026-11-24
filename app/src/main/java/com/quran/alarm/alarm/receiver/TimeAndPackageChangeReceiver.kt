package com.quran.alarm.alarm.receiver

/**
 * Separate BroadcastReceiver (without RECEIVE_BOOT_COMPLETED permission restriction)
 * that handles app updates and clock/timezone changes using [BootReceiver.onReceive] directly
 * so `goAsync()` operates properly on the system-registered receiver instance.
 */
class TimeAndPackageChangeReceiver : BootReceiver()
