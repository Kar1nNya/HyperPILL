package com.karin.hyperpill.hook

/**
 * Step 1 stubs: verify LSPosed can load into Xiaomi system packages and that
 * the target classes are visible to the module classloader.
 *
 * Each hook only probes classes and logs; no system behavior is changed yet.
 */
object SystemUiPluginHook : HookContext() {
    private const val TAG = "HyperPILL-SystemUI"

    override fun onHook() {
        Log.i(TAG, "hook ready package=$packageName")
        probe("miui.systemui.controlcenter.panel.main.MainPanelController")
        probe("miui.systemui.devicecenter.devices.DeviceInfoWrapper")
    }
}

object HeadsetStateDispatcher : HookContext() {
    private const val TAG = "HyperPILL-HeadsetState"

    override fun onHook() {
        Log.i(TAG, "hook ready package=$packageName")
        probe("com.android.bluetooth.a2dp.A2dpService")

        runCatching {
            hookAfter(findMethodByParamCount("com.android.bluetooth.a2dp.A2dpService", "handleConnectionStateChanged", 3)) {
                val device = args.getOrNull(0) as? android.bluetooth.BluetoothDevice
                val fromState = args.getOrNull(1) as? Int ?: return@hookAfter
                val currState = args.getOrNull(2) as? Int ?: return@hookAfter
                if (device == null || currState == fromState) return@hookAfter

                val context = instance as? android.content.ContextWrapper ?: return@hookAfter
                val isPill = isPillDevice(device)
                Log.i(TAG, "A2DP state $fromState->$currState device=${device.address} isPill=$isPill")

                if (!isPill) return@hookAfter

                when (currState) {
                    android.bluetooth.BluetoothHeadset.STATE_CONNECTED -> {
                        com.karin.hyperpill.pods.PillSystemController.connect(context, device)
                    }
                    android.bluetooth.BluetoothHeadset.STATE_DISCONNECTING,
                    android.bluetooth.BluetoothHeadset.STATE_DISCONNECTED -> {
                        com.karin.hyperpill.pods.PillSystemController.disconnect(context, device)
                    }
                }
            }
            Log.i(TAG, "A2dpService.handleConnectionStateChanged hook installed")
        }.onFailure {
            Log.w(TAG, "A2dpService.handleConnectionStateChanged hook failed", it)
        }
    }

    private fun isPillDevice(device: android.bluetooth.BluetoothDevice): Boolean {
        val name = runCatching { device.name ?: device.alias }.getOrNull().orEmpty()
        if (name.isBlank()) return false
        if (com.karin.hyperpill.pods.PillProducts.fromDeviceName(name) != null) return true
        val n = name.lowercase()
        return n.contains("pill") || n.contains("oba") || n.contains("laplace")
    }
}

object BluetoothUpstreamHeadsetHook : HookContext() {
    private const val TAG = "HyperPILL-Upstream"
    private val knownAddresses = linkedSetOf<String>()
    private val callbacks = linkedMapOf<android.os.IBinder, Any>()
    private var context: android.content.Context? = null
    private var receiverRegistered = false
    private var currentAddress: String? = null
    private var currentBattery: com.karin.hyperpill.pods.BatteryParams? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onHook() {
        Log.i(TAG, "hook ready package=$packageName")
        probe("com.android.bluetooth.ble.app.headset.BluetoothHeadsetService")
        probe("com.android.bluetooth.ble.app.MiuiBluetoothNotificationApi")
        probe("com.android.bluetooth.ble.app.MiuiBluetoothNotification")
        if (packageName == "com.xiaomi.bluetooth") {
            hookBluetoothHeadsetService()
        }
    }

    private fun hookBluetoothHeadsetService() {
        val serviceClass = findClassOrNull("com.android.bluetooth.ble.app.headset.BluetoothHeadsetService")
        if (serviceClass != null) {
            runCatching {
                hookAfter(serviceClass.getDeclaredMethod("onBind", android.content.Intent::class.java).apply { isAccessible = true }) {
                    registerStatusReceiver(instance as? android.content.Context)
                    val binder = result ?: return@hookAfter
                    installBinderHooks(binder.javaClass)
                }
                Log.i(TAG, "BluetoothHeadsetService.onBind hook installed")
            }.onFailure { Log.w(TAG, "hook BluetoothHeadsetService.onBind failed", it) }
            runCatching {
                hookAfter(serviceClass.getDeclaredMethod("onCreate")) {
                    registerStatusReceiver(instance as? android.content.Context)
                }
                Log.i(TAG, "BluetoothHeadsetService.onCreate hook installed")
            }.onFailure { Log.w(TAG, "hook BluetoothHeadsetService.onCreate skipped", it) }
        }

        listOf(
            "com.android.bluetooth.ble.app.headset.BinderC6776v",
            "com.android.bluetooth.ble.app.headset.v"
        ).forEach { className ->
            findClassOrNull(className)?.let { installBinderHooks(it) }
        }
    }

    private fun installBinderHooks(binderClass: Class<*>) {
        val className = binderClass.name
        Log.i(TAG, "installing binder hooks on $className")

        runCatching {
            val method = findMethodOrNull(binderClass, "checkSupport", android.bluetooth.BluetoothDevice::class.java)
            if (method != null) {
                hookBefore(method) {
                    val device = args.getOrNull(0) as? android.bluetooth.BluetoothDevice
                    if (!isPillDevice(device)) return@hookBefore
                    result = fakeSupport()
                    Log.i(TAG, "checkSupport forced device=${device?.address} support=$result")
                }
                Log.i(TAG, "$className.checkSupport hook installed")
            }
        }.onFailure { Log.w(TAG, "hook $className.checkSupport failed", it) }

        listOf(
            "getDeviceInfo" to { fakeSupport() },
            "isSupportAudioSwitch" to { "1" },
            "isMiTWS" to { true },
            "checkIsMiTWS" to { true },
            "getRingFindState" to { false }
        ).forEach { (name, forced) ->
            runCatching {
                val method = findMethodOrNull(binderClass, name, String::class.java)
                if (method != null) {
                    hookBefore(method) {
                        val address = args.getOrNull(0) as? String
                        if (address == null || !isPillAddress(address)) return@hookBefore
                        result = forced()
                        Log.i(TAG, "$className.$name forced address=$address result=$result")
                    }
                    Log.i(TAG, "$className.$name hook installed")
                }
            }.onFailure { Log.w(TAG, "hook $className.$name failed", it) }
        }

        runCatching {
            val callbackClass = findClassOrNull("com.android.bluetooth.ble.app.IMiuiHeadsetCallback")
            if (callbackClass != null) {
                val register = findMethodOrNull(binderClass, "register", callbackClass)
                if (register != null) {
                    hookBefore(register) {
                        val callback = args.getOrNull(0)
                        if (callback != null) {
                            rememberCallback(callback)
                            result = null
                            Log.i(TAG, "register swallowed callback=$callback")
                            notifyCallbacks("register")
                        }
                    }
                    Log.i(TAG, "$className.register hook installed")
                }
                val registerDevice = findMethodOrNull(binderClass, "registerCallbackDevice", callbackClass, android.bluetooth.BluetoothDevice::class.java)
                if (registerDevice != null) {
                    hookBefore(registerDevice) {
                        val callback = args.getOrNull(0)
                        val device = args.getOrNull(1) as? android.bluetooth.BluetoothDevice
                        if (callback != null && isPillDevice(device)) {
                            rememberCallback(callback)
                            result = null
                            Log.i(TAG, "registerCallbackDevice swallowed callback=$callback device=${device?.address}")
                            notifyCallbacks("registerCallbackDevice")
                        }
                    }
                    Log.i(TAG, "$className.registerCallbackDevice hook installed")
                }
                val unregister = findMethodOrNull(binderClass, "unregister", callbackClass, android.bluetooth.BluetoothDevice::class.java)
                if (unregister != null) {
                    hookBefore(unregister) {
                        val callback = args.getOrNull(0)
                        val device = args.getOrNull(1) as? android.bluetooth.BluetoothDevice
                        if (callback != null && isPillDevice(device)) {
                            forgetCallback(callback)
                            result = null
                            Log.i(TAG, "unregister swallowed callback=$callback device=${device?.address}")
                        }
                    }
                    Log.i(TAG, "$className.unregister hook installed")
                }
            }
        }.onFailure { Log.w(TAG, "hook callback methods failed", it) }

        listOf("connect", "getDeviceConfig", "getCommonConfig").forEach { name ->
            runCatching {
                val method = findMethodOrNull(binderClass, name, android.bluetooth.BluetoothDevice::class.java)
                if (method != null) {
                    hookBefore(method) {
                        val device = args.getOrNull(0) as? android.bluetooth.BluetoothDevice
                        if (!isPillDevice(device)) return@hookBefore
                        result = null
                        Log.i(TAG, "$className.$name swallowed device=${device?.address}")
                        notifyCallbacks(name)
                    }
                    Log.i(TAG, "$className.$name hook installed")
                }
            }.onFailure { Log.w(TAG, "hook $className.$name failed", it) }
        }
    }

    private fun registerStatusReceiver(ctx: android.content.Context?) {
        if (ctx == null || receiverRegistered) return
        context = ctx.applicationContext ?: ctx
        val filter = android.content.IntentFilter().apply {
            addAction(com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_CONNECTED)
            addAction(com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_DISCONNECTED)
            addAction(com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_BATTERY_CHANGED)
        }
        context?.registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_CONNECTED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentAddress?.let { knownAddresses.add(it.uppercase()) }
                    }
                    com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_DISCONNECTED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                    }
                    com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_BATTERY_CHANGED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentAddress?.let { knownAddresses.add(it.uppercase()) }
                        currentBattery = batteryFromIntent(intent)
                        Log.n(TAG, "battery broadcast address=$currentAddress L=${currentBattery?.left?.battery} R=${currentBattery?.right?.battery} C=${currentBattery?.case?.battery}")
                        notifyCallbacks("battery-broadcast")
                    }
                }
            }
        }, filter, android.content.Context.RECEIVER_EXPORTED)
        receiverRegistered = true
        Log.i(TAG, "status receiver registered context=$context")
    }

    private fun batteryFromIntent(intent: android.content.Intent): com.karin.hyperpill.pods.BatteryParams {
        fun pod(batteryKey: String, connectedKey: String): com.karin.hyperpill.pods.PodParams? {
            val connected = intent.getBooleanExtra(connectedKey, false)
            val battery = intent.getIntExtra(batteryKey, 0)
            return if (connected) com.karin.hyperpill.pods.PodParams(battery, false, true) else null
        }
        return com.karin.hyperpill.pods.BatteryParams(
            left = pod("left_battery", "left_connected"),
            right = pod("right_battery", "right_connected"),
            case = pod("case_battery", "case_connected")
        )
    }

    private fun notifyCallbacks(reason: String) {
        val address = currentAddress ?: return
        if (callbacks.isEmpty()) {
            Log.d(TAG, "notifyCallbacks skipped: no callbacks reason=$reason address=$address")
            return
        }
        val payload = miuiRefreshPayload(currentBattery)
        handler.post {
            callbacks.values.toList().forEach { callback ->
                runCatching {
                    callMethod(callback, "refreshStatus", address, payload)
                    Log.i(TAG, "refreshStatus sent reason=$reason address=$address payload=$payload")
                }.onFailure {
                    forgetCallback(callback)
                    Log.w(TAG, "refreshStatus failed reason=$reason callback=$callback", it)
                }
            }
        }
    }

    private fun miuiRefreshPayload(battery: com.karin.hyperpill.pods.BatteryParams?): String {
        val values = MutableList(16) { "" }
        values[0] = miuiBatteryValue(battery?.left)
        values[1] = miuiBatteryValue(battery?.right)
        values[2] = miuiBatteryValue(battery?.case)
        values[7] = "0000"
        values[8] = "true"
        values[11] = "00"
        values[13] = "00"
        values[14] = "00"
        return values.joinToString(",")
    }

    private fun miuiBatteryValue(params: com.karin.hyperpill.pods.PodParams?): String {
        if (params?.isConnected != true) return "255"
        return params.battery.coerceIn(0, 100).toString()
    }

    private fun rememberCallback(callback: Any) {
        (callMethod(callback, "asBinder") as? android.os.IBinder)?.let { callbacks[it] = callback }
    }

    private fun forgetCallback(callback: Any) {
        (callMethod(callback, "asBinder") as? android.os.IBinder)?.let { callbacks.remove(it) }
    }

    private fun isPillDevice(device: android.bluetooth.BluetoothDevice?): Boolean {
        if (device == null) return false
        val address = runCatching { device.address }.getOrNull()
        val name = runCatching { device.name ?: device.alias }.getOrNull().orEmpty()
        val result = com.karin.hyperpill.pods.PillProducts.fromDeviceName(name) != null ||
            name.lowercase().contains("pill") ||
            name.lowercase().contains("oba") ||
            name.lowercase().contains("laplace") ||
            (address != null && isPillAddress(address))
        if (result && address != null) knownAddresses.add(address.uppercase())
        return result
    }

    private fun isPillAddress(address: String): Boolean = address.uppercase() in knownAddresses

    private fun findClassOrNull(className: String): Class<*>? =
        runCatching { findClass(className) }.getOrNull()

    private fun findMethodOrNull(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): java.lang.reflect.Method? =
        runCatching { clazz.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true } }.getOrNull()
}

object MiLinkServiceHook : HookContext() {
    private const val TAG = "HyperPILL-MiLink"
    private val knownAddresses = linkedSetOf<String>()
    private var context: android.content.Context? = null
    private var receiverRegistered = false
    private var currentAddress: String? = null
    private var currentBattery: com.karin.hyperpill.pods.BatteryParams? = null

    override fun onHook() {
        Log.i(TAG, "hook ready package=$packageName")
        probe("com.xiaomi.mxbluetoothsdk.service.MxBluetoothService")
        probe("com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager")
        probe("com.miui.headset.runtime.ProfileContext")
        probe("com.miui.headset.runtime.AncBatteryController")
        probe("com.miui.headset.api.HeadsetInfo")

        listOf(
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService",
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager"
        ).forEach { className ->
            runCatching {
                val method = findMethodOrNull(className, "getInstanceForIsMiTWS", android.content.Context::class.java)
                if (method != null) {
                    hookBefore(method) {
                        registerStatusReceiver(args.getOrNull(0) as? android.content.Context)
                    }
                }
            }.onFailure { Log.w(TAG, "hook $className.getInstanceForIsMiTWS skipped", it) }
        }

        hookMxBluetoothRuntime()
        hookHeadsetRuntimeDisplay()
    }

    private fun hookMxBluetoothRuntime() {
        val classes = listOf(
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager",
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService"
        )
        classes.forEach { className ->
            hookBluetoothDeviceResult(className, "checkIsMiTWS") { 1 }
            hookBluetoothDeviceResult(className, "getDeviceId") { fakeDeviceId() }
            hookBluetoothDeviceResult(className, "getBatteryLevel") { 1 }
            hookBluetoothDeviceResult(className, "getAncState") { -1 }
            hookBluetoothDeviceResult(className, "getDeviceRunInfo") { 0 }
            hookBluetoothDeviceResult(className, "getSpatialMode") { 0 }
            hookBluetoothDeviceResult(className, "getWearStatus") { "0,0" }
            hookBluetoothDeviceResult(className, "isLeAudio") { false }

            hookStringAddressResult(className, "isMiTWS") { true }
            hookStringAddressResult(className, "isSupportAudioSwitch") { 1 }
            hookStringAddressResult(className, "getRingFindState") { false }
        }
    }

    private fun hookHeadsetRuntimeDisplay() {
        hookBluetoothDeviceResult("com.miui.headset.runtime.ProfileContext", "getDeviceId") { fakeDeviceId() }
        hookBluetoothDeviceListResult("com.miui.headset.runtime.ProfileContext", "getBatteryLevel") { batteryList() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getDeviceId") { fakeDeviceId() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getAncState") { -1 }
        hookBluetoothDeviceListResult("com.miui.headset.runtime.AncBatteryController", "getBatteryLevelCache") { batteryList() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getHeadsetPropertyBlock") { 100 }

        listOf("getDeviceId", "component3").forEach { name ->
            hookHeadsetInfoNoArg(name) { fakeDeviceId() }
        }
        listOf("getPowers", "component4").forEach { name ->
            hookHeadsetInfoNoArg(name) { batteryList() }
        }
        listOf("getMode", "component5").forEach { name ->
            hookHeadsetInfoNoArg(name) { -1 }
        }
    }

    private fun hookBluetoothDeviceResult(className: String, methodName: String, result: () -> Any) {
        runCatching {
            val method = findMethodOrNull(className, methodName, android.bluetooth.BluetoothDevice::class.java)
            if (method != null) {
                hookAfter(method) {
                    val device = args.getOrNull(0) as? android.bluetooth.BluetoothDevice ?: return@hookAfter
                    if (!isPillDevice(device)) return@hookAfter
                    val old = this.result
                    this.result = result()
                    Log.i(TAG, "$className.$methodName forced old=$old new=${this.result} address=${device.address}")
                    if (className == "com.miui.headset.runtime.AncBatteryController" &&
                        methodName == "getHeadsetPropertyBlock"
                    ) {
                        notifyHeadsetPropertyChanged(instance, device, 4)
                    }
                }
                Log.i(TAG, "$className.$methodName hook installed")
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName(BluetoothDevice) skipped", it) }
    }

    private fun hookBluetoothDeviceListResult(className: String, methodName: String, result: () -> Any) {
        runCatching {
            val method = findMethodOrNull(className, methodName, android.bluetooth.BluetoothDevice::class.java)
            if (method != null) {
                hookAfter(method) {
                    val device = args.getOrNull(0) as? android.bluetooth.BluetoothDevice ?: return@hookAfter
                    if (!isPillDevice(device)) return@hookAfter
                    val old = this.result
                    this.result = result()
                    Log.i(TAG, "$className.$methodName forced old=$old new=${this.result} address=${device.address}")
                }
                Log.i(TAG, "$className.$methodName hook installed")
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName(BluetoothDevice) skipped", it) }
    }

    private fun hookStringAddressResult(className: String, methodName: String, result: () -> Any) {
        runCatching {
            val method = findMethodOrNull(className, methodName, String::class.java)
            if (method != null) {
                hookAfter(method) {
                    val address = args.getOrNull(0) as? String ?: return@hookAfter
                    if (!isPillAddress(address)) return@hookAfter
                    val old = this.result
                    this.result = result()
                    Log.i(TAG, "$className.$methodName forced old=$old new=${this.result} address=$address")
                }
                Log.i(TAG, "$className.$methodName hook installed")
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName(String) skipped", it) }
    }

    private fun hookHeadsetInfoNoArg(methodName: String, result: () -> Any) {
        runCatching {
            val method = findMethodByParamCountOrNull("com.miui.headset.api.HeadsetInfo", methodName, 0)
            if (method != null) {
                hookAfter(method) {
                    if (!isTargetHeadsetInfo(instance)) return@hookAfter
                    val old = this.result
                    this.result = result()
                    Log.i(TAG, "HeadsetInfo.$methodName forced old=$old new=${this.result}")
                }
                Log.i(TAG, "HeadsetInfo.$methodName hook installed")
            }
        }.onFailure { Log.w(TAG, "hook HeadsetInfo.$methodName skipped", it) }
    }

    private fun registerStatusReceiver(ctx: android.content.Context?) {
        if (ctx == null || receiverRegistered) return
        context = ctx.applicationContext ?: ctx
        val filter = android.content.IntentFilter().apply {
            addAction(com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_CONNECTED)
            addAction(com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_DISCONNECTED)
            addAction(com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_BATTERY_CHANGED)
        }
        context?.registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_CONNECTED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentAddress?.let { knownAddresses.add(it.uppercase()) }
                    }
                    com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_DISCONNECTED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                    }
                    com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_BATTERY_CHANGED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentAddress?.let { knownAddresses.add(it.uppercase()) }
                        currentBattery = batteryFromIntent(intent)
                        Log.n(TAG, "battery broadcast address=$currentAddress L=${currentBattery?.left?.battery} R=${currentBattery?.right?.battery} C=${currentBattery?.case?.battery}")
                    }
                }
            }
        }, filter, android.content.Context.RECEIVER_EXPORTED)
        receiverRegistered = true
        Log.i(TAG, "status receiver registered context=$context")
    }

    private fun batteryFromIntent(intent: android.content.Intent): com.karin.hyperpill.pods.BatteryParams {
        fun pod(batteryKey: String, connectedKey: String): com.karin.hyperpill.pods.PodParams? {
            val connected = intent.getBooleanExtra(connectedKey, false)
            val battery = intent.getIntExtra(batteryKey, 0)
            return if (connected) com.karin.hyperpill.pods.PodParams(battery, false, true) else null
        }
        return com.karin.hyperpill.pods.BatteryParams(
            left = pod("left_battery", "left_connected"),
            right = pod("right_battery", "right_connected"),
            case = pod("case_battery", "case_connected")
        )
    }

    private fun notifyHeadsetPropertyChanged(controller: Any?, device: android.bluetooth.BluetoothDevice, updateType: Int) {
        val listener = runCatching { getObjectField(controller, "headsetPropertyChangeListener") }.getOrNull()
        if (listener == null) {
            Log.w(TAG, "notifyHeadsetPropertyChanged skipped: listener is null updateType=$updateType")
            return
        }
        runCatching {
            callMethod(listener, "invoke", device, updateType)
            Log.i(TAG, "notifyHeadsetPropertyChanged invoked updateType=$updateType address=${device.address}")
        }.onFailure { Log.w(TAG, "notifyHeadsetPropertyChanged failed updateType=$updateType", it) }
    }

    private fun batteryLevel(): Int {
        val values = listOfNotNull(currentBattery?.left, currentBattery?.right)
            .filter { it.isConnected }
            .map { it.battery.coerceIn(0, 100) }
        return values.minOrNull() ?: 0
    }

    private fun batteryList(): List<Int> {
        val box = currentBattery?.case?.takeIf { it.isConnected }?.battery?.coerceIn(0, 100) ?: -1
        val left = currentBattery?.left?.takeIf { it.isConnected }?.battery?.coerceIn(0, 100) ?: -1
        val right = currentBattery?.right?.takeIf { it.isConnected }?.battery?.coerceIn(0, 100) ?: -1
        return listOf(
            box,
            left,
            right,
            0,
            0,
            0
        )
    }

    private fun isPillDevice(device: android.bluetooth.BluetoothDevice): Boolean {
        val address = runCatching { device.address }.getOrNull()
        if (address != null && isPillAddress(address)) return true
        val name = runCatching { device.name ?: device.alias }.getOrNull().orEmpty()
        val result = com.karin.hyperpill.pods.PillProducts.fromDeviceName(name) != null ||
            name.lowercase().contains("pill") ||
            name.lowercase().contains("oba") ||
            name.lowercase().contains("laplace")
        if (result && address != null) {
            knownAddresses.add(address.uppercase())
            currentAddress = address
        }
        return result
    }

    private fun isPillAddress(address: String): Boolean = address.uppercase() in knownAddresses

    private fun isTargetHeadsetInfo(info: Any?): Boolean {
        if (info == null) return false
        listOf("getAddress", "component1").forEach { method ->
            val address = runCatching { callMethod(info, method) as? String }.getOrNull()
            if (address != null && isPillAddress(address)) return true
        }
        return false
    }

    private fun findClassOrNull(className: String): Class<*>? =
        runCatching { findClass(className) }.getOrNull()

    private fun findMethodOrNull(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): java.lang.reflect.Method? =
        runCatching { clazz.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true } }.getOrNull()

    private fun findMethodOrNull(className: String, name: String, vararg parameterTypes: Class<*>): java.lang.reflect.Method? =
        findClassOrNull(className)?.let { findMethodOrNull(it, name, *parameterTypes) }

    private fun findMethodByParamCountOrNull(className: String, name: String, paramCount: Int): java.lang.reflect.Method? =
        findClassOrNull(className)?.declaredMethods?.firstOrNull { it.name == name && it.parameterTypes.size == paramCount }?.apply { isAccessible = true }
}

object SettingsHeadsetHook : HookContext() {
    private const val TAG = "HyperPILL-Settings"

    override fun onHook() {
        Log.i(TAG, "hook ready package=$packageName")
        probe("com.android.settings.bluetooth.HeadsetIDConstants")

        runCatching {
            val method = findMethodOrNull("com.android.settings.bluetooth.HeadsetIDConstants", "checkSupport", String::class.java)
            if (method != null) {
                module.hook(method).intercept { chain ->
                    val original = chain.proceed()
                    val support = chain.args.getOrNull(0) as? String
                    val fakeId = fakeDeviceId()
                    if (support != null && (support.startsWith(fakeId) || support.contains(fakeId))) {
                        Log.i(TAG, "checkSupport blocked fakeId=$fakeId support=$support")
                        false
                    } else {
                        original
                    }
                }
                Log.i(TAG, "HeadsetIDConstants.checkSupport hook installed")
            }
        }.onFailure { Log.w(TAG, "HeadsetIDConstants.checkSupport hook skipped", it) }
    }

    private fun findClassOrNull(className: String): Class<*>? =
        runCatching { findClass(className) }.getOrNull()

    private fun findMethodOrNull(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): java.lang.reflect.Method? =
        runCatching { clazz.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true } }.getOrNull()

    private fun findMethodOrNull(className: String, name: String, vararg parameterTypes: Class<*>): java.lang.reflect.Method? =
        findClassOrNull(className)?.let { findMethodOrNull(it, name, *parameterTypes) }
}

object DeviceCardHook : HookContext() {
    private const val TAG = "HyperPILL-DeviceCard"

    override fun onHook() {
        Log.i(TAG, "hook ready package=$packageName")
        probe("miui.systemui.controlcenter.panel.main.MainPanelController")
        probe("miui.systemui.devicecenter.devices.DeviceInfoWrapper")
    }
}

object MiBluetoothToastHook : HookContext() {
    private const val TAG = "HyperPILL-MiToast"
    private var connectedAddress: String? = null
    private var islandShownForAddress: String? = null

    override fun onHook() {
        Log.i(TAG, "hook ready package=$packageName")
        probe("com.android.bluetooth.ble.app.MiuiBluetoothNotificationApi")
        probe("com.android.bluetooth.ble.app.MiuiBluetoothNotification")

        if (packageName != "com.xiaomi.bluetooth") return

        runCatching {
            hookConstructorAfter(findConstructorByParamCount("com.android.bluetooth.ble.app.MiuiBluetoothNotification", 2)) {
                val context = runCatching { getObjectField(instance, "mContext") as? android.content.Context }.getOrNull()
                if (context == null) return@hookConstructorAfter
                registerFocusIslandReceiver(context)
            }
            Log.i(TAG, "MiuiBluetoothNotification constructor hook installed")
        }.onFailure {
            Log.w(TAG, "MiuiBluetoothNotification constructor hook failed", it)
        }
    }

    private fun registerFocusIslandReceiver(context: android.content.Context) {
        val filter = android.content.IntentFilter().apply {
            addAction(com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_CONNECTED)
            addAction(com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_DISCONNECTED)
            addAction(com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_BATTERY_CHANGED)
        }
        context.registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_CONNECTED -> {
                        connectedAddress = intent.getStringExtra("address")
                        islandShownForAddress = null
                        Log.i(TAG, "connected address=$connectedAddress, reset island")
                    }
                    com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_DISCONNECTED -> {
                        connectedAddress = null
                        islandShownForAddress = null
                        val ctx = context ?: return@onReceive
                        com.karin.hyperpill.utils.HyperPillNotificationUtil.cancelPodsNotification(ctx, intent.getStringExtra("address"))
                    }
                    com.karin.hyperpill.pods.HyperPILLAction.ACTION_PODS_BATTERY_CHANGED -> {
                        val address = intent.getStringExtra("address") ?: connectedAddress
                        if (address == null || address != connectedAddress) return@onReceive
                        val ctx = context ?: return@onReceive
                        val battery = batteryFromIntent(intent)
                        val device = runCatching {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra<android.bluetooth.BluetoothDevice>("device")
                        }.getOrNull()
                        device?.let {
                            com.karin.hyperpill.utils.HyperPillNotificationUtil.showPodsNotification(ctx, it, battery)
                        }
                        if (islandShownForAddress != address) {
                            val deviceName = runCatching { device?.alias ?: device?.name }.getOrNull()
                            if (com.karin.hyperpill.utils.FocusIslandUtil.showBatteryIsland(ctx, battery, deviceName)) {
                                islandShownForAddress = address
                                Log.i(TAG, "focus island shown for $address")
                            }
                        }
                    }
                }
            }
        }, filter, android.content.Context.RECEIVER_EXPORTED)
        Log.i(TAG, "focus island receiver registered")
    }

    private fun batteryFromIntent(intent: android.content.Intent): com.karin.hyperpill.pods.BatteryParams {
        fun pod(batteryKey: String, connectedKey: String): com.karin.hyperpill.pods.PodParams? {
            val connected = intent.getBooleanExtra(connectedKey, false)
            val battery = intent.getIntExtra(batteryKey, 0)
            return if (connected) com.karin.hyperpill.pods.PodParams(battery, false, true) else null
        }
        return com.karin.hyperpill.pods.BatteryParams(
            left = pod("left_battery", "left_connected"),
            right = pod("right_battery", "right_connected"),
            case = pod("case_battery", "case_connected")
        )
    }
}

object MoreSettingsRedirectHook : HookContext() {
    private const val TAG = "HyperPILL-MoreSettings"

    override fun onHook() {
        Log.i(TAG, "hook ready package=$packageName")
        probe("com.xiaomi.mxbluetoothsdk.service.MxBluetoothService")

        listOf(
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService",
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager"
        ).forEach { className ->
            runCatching {
                val method = findMethodOrNull(className, "switchToHeadsetActivity", android.bluetooth.BluetoothDevice::class.java)
                if (method != null) {
                    hookBefore(method) {
                        val device = args.getOrNull(0) as? android.bluetooth.BluetoothDevice
                        if (!isPillDevice(device)) return@hookBefore
                        val ctx = runCatching { getObjectField(instance, "mContext") as? android.content.Context }.getOrNull()
                        if (ctx == null) {
                            Log.w(TAG, "switchToHeadsetActivity context is null")
                            return@hookBefore
                        }
                        val intent = ctx.packageManager.getLaunchIntentForPackage("com.karin.hyperpill")?.apply {
                            putExtra("open_detail", true)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (intent != null) {
                            ctx.startActivity(intent)
                            result = null
                            Log.i(TAG, "switchToHeadsetActivity → HyperPILL device=${device?.address}")
                        }
                    }
                    Log.i(TAG, "$className.switchToHeadsetActivity hook installed")
                }
            }.onFailure { Log.w(TAG, "hook $className.switchToHeadsetActivity failed", it) }
        }
    }

    private fun isPillDevice(device: android.bluetooth.BluetoothDevice?): Boolean {
        if (device == null) return false
        val name = runCatching { device.name ?: device.alias }.getOrNull().orEmpty()
        return com.karin.hyperpill.pods.PillProducts.fromDeviceName(name) != null ||
            name.lowercase().contains("pill") ||
            name.lowercase().contains("oba") ||
            name.lowercase().contains("laplace")
    }

    private fun findClassOrNull(className: String): Class<*>? =
        runCatching { findClass(className) }.getOrNull()

    private fun findMethodOrNull(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): java.lang.reflect.Method? =
        runCatching { clazz.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true } }.getOrNull()

    private fun findMethodOrNull(className: String, name: String, vararg parameterTypes: Class<*>): java.lang.reflect.Method? =
        findClassOrNull(className)?.let { findMethodOrNull(it, name, *parameterTypes) }
}

object HeadSetsDetailHook : HookContext() {
    private const val TAG = "HyperPILL-HeadSetsDetail"
    private const val CLASS_NAME = "com.miui.circulateplus.world.headset.HeadSetsDetail"
    private const val MILINK_PACKAGE = "com.milink.service"

    override fun onHook() {
        Log.i(TAG, "hook ready package=$packageName")
        runCatching {
            val method = findBindMethod()
            hookAfter(method) {
                val root = instance as? android.view.View ?: return@hookAfter
                val address = extractAddress(args.getOrNull(0), args.getOrNull(2), args.getOrNull(3))
                Log.i(TAG, "HeadSetsDetail bound root=${root.javaClass.name} address=$address")
                if (address == null || !isPillAddress(address)) return@hookAfter
                hideAncCard(root)
            }
            Log.i(TAG, "HeadSetsDetail bind hook installed")
        }.onFailure {
            Log.w(TAG, "HeadSetsDetail hook failed", it)
        }
    }

    private fun findBindMethod(): java.lang.reflect.Method {
        val clazz = findClass(CLASS_NAME)
        return clazz.declaredMethods.single { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 4 &&
                method.parameterTypes[0].name == "com.miui.circulate.api.service.CirculateServiceInfo" &&
                method.parameterTypes[2].name == "com.miui.circulate.api.protocol.headset.HeadsetDeviceInfo" &&
                method.parameterTypes[3].name == "com.miui.circulate.api.service.CirculateDeviceInfo"
        }.apply { isAccessible = true }
    }

    private fun extractAddress(vararg infos: Any?): String? {
        val methodCandidates = listOf("getAddress", "getMac", "component1")
        val fieldCandidates = listOf("address", "mac", "deviceId")
        for (info in infos) {
            if (info == null) continue
            for (method in methodCandidates) {
                val value = runCatching { callMethod(info, method) as? String }.getOrNull()
                if (value != null && isBluetoothAddress(value)) return value
            }
            for (field in fieldCandidates) {
                val value = runCatching { getObjectField(info, field) as? String }.getOrNull()
                if (value != null && isBluetoothAddress(value)) return value
            }
        }
        return null
    }

    private fun isBluetoothAddress(value: String): Boolean =
        Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}").matches(value)

    private fun isPillAddress(address: String): Boolean {
        val name = address.uppercase()
        // HeadSetsDetail only gives us the BT address; compare against known Pill address from
        // the system controller if available, otherwise accept all and let view presence decide.
        // For now we hide ANC for any address that looks like a BT MAC; harmless for non-Pill.
        return name.matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}"))
    }

    private fun hideAncCard(root: android.view.View) {
        val ancCard = findMiLinkView(root, "anc_card") ?: run {
            Log.d(TAG, "anc_card not found, nothing to hide")
            return
        }
        ancCard.visibility = android.view.View.GONE
        Log.i(TAG, "anc_card hidden")
    }

    private fun findMiLinkView(root: android.view.View, name: String): android.view.View? {
        val resources = root.resources
        val id = resources.getIdentifier(name, "id", root.context.packageName)
            .takeIf { it != 0 }
            ?: resources.getIdentifier(name, "id", MILINK_PACKAGE)
        return id.takeIf { it != 0 }?.let(root::findViewById)
    }
}

private fun HookContext.probe(className: String) {
    runCatching { findClass(className) }
        .onSuccess {
            Log.i("HyperPILL-Probe", "class found in $packageName: $className")
        }
        .onFailure {
            Log.w("HyperPILL-Probe", "class NOT found in $packageName: $className")
        }
}
