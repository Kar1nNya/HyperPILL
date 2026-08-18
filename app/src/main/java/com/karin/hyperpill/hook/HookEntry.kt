package com.karin.hyperpill.hook

import android.content.SharedPreferences
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * HyperPILL LSPosed entry.
 *
 * Step 1: load hook framework into Xiaomi system packages and verify class visibility.
 * Actual behavior hooks will be added in later steps.
 */
class HookEntry : XposedModule() {

    private val tag = "HyperPILL-HookEntry"
    private val configListeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return

        when (param.packageName) {
            "com.android.systemui" -> {
                loadHook(SystemUiPluginHook, param.defaultClassLoader, param.packageName)
                loadHook(DeviceCardHook, param.defaultClassLoader, param.packageName)
            }
            "com.android.bluetooth" -> {
                loadHook(HeadsetStateDispatcher, param.defaultClassLoader, param.packageName)
                loadHook(BluetoothUpstreamHeadsetHook, param.defaultClassLoader, param.packageName)
            }
            "com.milink.service" -> {
                loadHook(MiLinkServiceHook, param.defaultClassLoader, param.packageName)
                loadHook(MoreSettingsRedirectHook, param.defaultClassLoader, param.packageName)
                loadHook(HeadSetsDetailHook, param.defaultClassLoader, param.packageName)
            }
            "com.xiaomi.bluetooth" -> {
                loadHook(MiBluetoothToastHook, param.defaultClassLoader, param.packageName)
                loadHook(BluetoothUpstreamHeadsetHook, param.defaultClassLoader, param.packageName)
                loadHook(MoreSettingsRedirectHook, param.defaultClassLoader, param.packageName)
            }
            "com.android.settings" -> {
                loadHook(SettingsHeadsetHook, param.defaultClassLoader, param.packageName)
            }
        }
    }

    private fun loadHook(hook: HookContext, classLoader: ClassLoader, packageName: String) {
        Log.module = this
        Log.prefs = getRemotePreferences(ConfigManager.PREFS_NAME)
        hook.module = this
        hook.appClassLoader = classLoader
        hook.packageName = packageName
        hook.prefs = getRemotePreferences(ConfigManager.PREFS_NAME)
        Log.d(tag, "loadHook package=$packageName hook=${hook.javaClass.simpleName}")
        ConfigManager.init(hook.prefs)
        val configListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ConfigManager.PREF_KEY_FAKE_DEVICE_ID ||
                key == ConfigManager.PREF_KEY_LOG_LEVEL ||
                key == ConfigManager.PREF_KEY_ISLAND_MODE
            ) {
                ConfigManager.refreshFromPrefs(hook.prefs)
            }
        }
        configListeners.add(configListener)
        hook.prefs.registerOnSharedPreferenceChangeListener(configListener)
        hook.onHook()
    }
}
