package com.karin.hyperpill.hook

import android.content.SharedPreferences

/**
 * Minimal configuration manager for HyperPILL system hooks.
 * Ported from HyperOriG, simplified for the first milestone.
 */
object ConfigManager {
    const val PREFS_NAME = "hyperpill_settings"
    const val PREF_KEY_FAKE_DEVICE_ID = "fake_device_id"
    const val PREF_KEY_LOG_LEVEL = "log_level"
    const val PREF_KEY_ISLAND_MODE = "island_mode"

    const val DEFAULT_FAKE_DEVICE_ID = "01010607"
    const val LOG_LEVEL_OFF = 0
    const val LOG_LEVEL_BASIC = 1
    const val LOG_LEVEL_DEBUG = 2
    const val ISLAND_MODE_NONE = 0
    const val ISLAND_MODE_OFFICIAL = 1
    const val ISLAND_MODE_MODULE = 2

    @Volatile
    private var fakeDeviceId: String = DEFAULT_FAKE_DEVICE_ID
    @Volatile
    private var logLevel: Int = LOG_LEVEL_BASIC
    @Volatile
    private var islandMode: Int = ISLAND_MODE_OFFICIAL

    fun init(prefs: SharedPreferences) {
        fakeDeviceId = prefs.getString(PREF_KEY_FAKE_DEVICE_ID, DEFAULT_FAKE_DEVICE_ID)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_FAKE_DEVICE_ID
        logLevel = prefs.getInt(PREF_KEY_LOG_LEVEL, LOG_LEVEL_BASIC).coerceIn(LOG_LEVEL_OFF, LOG_LEVEL_DEBUG)
        islandMode = prefs.getInt(PREF_KEY_ISLAND_MODE, ISLAND_MODE_OFFICIAL).coerceIn(ISLAND_MODE_NONE, ISLAND_MODE_MODULE)
    }

    fun refreshFromPrefs(prefs: SharedPreferences) = init(prefs)

    fun fakeDeviceId(): String = fakeDeviceId

    fun fakeSupport(): String = "$fakeDeviceId,000000000000000010000000"

    fun logLevel(): Int = logLevel

    fun islandMode(): Int = islandMode
}
