package com.karin.hyperpill

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karin.hyperpill.pods.GaiaFeature
import com.karin.hyperpill.pods.GaiaProtocol
import com.karin.hyperpill.pods.PillClient
import com.karin.hyperpill.pods.VoiceConf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PillUiState(
    val devices: List<BluetoothDevice> = emptyList(),
    val selectedDevice: BluetoothDevice? = null,
    val connected: Boolean = false,
    val batteryLevels: Map<Int, Int> = emptyMap(),
    val supportedBatteries: List<Int> = emptyList(),
    val features: List<GaiaFeature> = emptyList(),
    val gainIndex: Int? = null,
    val eqState: Int? = null,
    val eqPresets: List<Int> = emptyList(),
    val eqSelected: Int? = null,
    val oneBringTwoEnabled: Boolean? = null,
    val oneBringTwoTimeout: Int? = null,
    val voiceConf: VoiceConf? = null,
    val spoofedDeviceName: String? = null,
    val message: String = "",
    val busy: Boolean = false,
    val refreshingDevices: Boolean = false
)

class PillViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PillUiState())
    val uiState: StateFlow<PillUiState> = _uiState.asStateFlow()

    private var client: PillClient? = null
    private var pendingFeatures: List<GaiaFeature> = emptyList()

    fun refreshDevices() {
        _uiState.update { it.copy(refreshingDevices = true) }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            _uiState.update { it.copy(message = "设备不支持蓝牙", refreshingDevices = false) }
            return
        }
        val pills = adapter.bondedDevices
            ?.filter { isPillName(it.name) }
            ?.sortedBy { it.name }
            .orEmpty()
        _uiState.update {
            it.copy(
                devices = pills,
                message = if (pills.isEmpty()) "未找到已配对的 Pill 设备" else "找到 ${pills.size} 个 Pill 设备",
                refreshingDevices = false
            )
        }
    }

    fun spoofDevice(name: String?) {
        _uiState.update { it.copy(spoofedDeviceName = name, message = if (name != null) "已伪装连接：$name" else "已取消伪装") }
    }

    fun connect(device: BluetoothDevice) {
        if (_uiState.value.connected) return
        _uiState.update { it.copy(selectedDevice = device, busy = true, message = "正在连接 ${device.name}...") }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val c = PillClient(device, object : PillClient.Listener {
                    override fun onConnected() {
                        _uiState.update {
                            it.copy(
                                connected = true,
                                busy = false,
                                message = "已连接 ${device.name}"
                            )
                        }
                        runCatching { client?.requestSupportedFeatures() }
                        runCatching { client?.requestSupportedBatteries() }
                        runCatching { client?.requestBatteryLevels() }
                        runCatching { client?.requestGain() }
                        runCatching { client?.requestEqState() }
                        runCatching { client?.requestAvailablePreSets() }
                        runCatching { client?.requestEqSet() }
                        runCatching { client?.requestOneBringTwoState() }
                        runCatching { client?.requestOneBringTwoTimeout() }
                        runCatching { client?.requestVoiceConf() }
                    }

                    override fun onFrame(pdu: ByteArray) {
                        handleFrame(pdu)
                    }

                    override fun onError(throwable: Throwable) {
                        _uiState.update { it.copy(connected = false, busy = false, message = "错误: ${throwable.message}") }
                    }

                    override fun onDisconnected() {
                        _uiState.update { it.copy(connected = false, busy = false, message = "连接已断开") }
                    }
                })
                client = c
                c.connect()
            }.onFailure { t ->
                _uiState.update { it.copy(connected = false, busy = false, message = "连接失败: ${t.message}") }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            client?.close()
            client = null
            pendingFeatures = emptyList()
            _uiState.update {
                it.copy(
                    connected = false,
                    selectedDevice = null,
                    batteryLevels = emptyMap(),
                    supportedBatteries = emptyList(),
                    features = emptyList(),
                    gainIndex = null,
                    eqState = null,
                    eqPresets = emptyList(),
                    eqSelected = null,
                    oneBringTwoEnabled = null,
                    oneBringTwoTimeout = null,
                    voiceConf = null,
                    message = "已断开"
                )
            }
        }
    }

    fun refreshBattery() {
        val c = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { c.requestBatteryLevels() }
                .onFailure { _uiState.update { it.copy(message = "刷新电量失败: ${it.message}") } }
        }
    }

    fun setGain(index: Int) {
        val c = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { c.setGain(index) }
                .onSuccess { _uiState.update { it.copy(gainIndex = index, message = "增益已设为 $index") } }
                .onFailure { _uiState.update { it.copy(message = "设置增益失败: ${it.message}") } }
        }
    }

    fun selectEqSet(presetId: Int) {
        val c = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { c.setEqSet(presetId) }
                .onSuccess { _uiState.update { it.copy(eqSelected = presetId, message = "EQ 已切换为 $presetId") } }
                .onFailure { _uiState.update { it.copy(message = "切换 EQ 失败: ${it.message}") } }
        }
    }

    fun setOneBringTwoState(enabled: Boolean) {
        val c = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { c.setOneBringTwoState(enabled) }
                .onSuccess { _uiState.update { it.copy(oneBringTwoEnabled = enabled, message = if (enabled) "双设备连接已开启" else "双设备连接已关闭") } }
                .onFailure { _uiState.update { it.copy(message = "设置双设备连接失败: ${it.message}") } }
        }
    }

    fun setOneBringTwoTimeout(timeout: Int) {
        val c = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { c.setOneBringTwoTimeout(timeout) }
                .onSuccess { _uiState.update { it.copy(oneBringTwoTimeout = timeout, message = "双设备超时已设为 $timeout") } }
                .onFailure { _uiState.update { it.copy(message = "设置双设备超时失败: ${it.message}") } }
        }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        val c = client ?: return
        val current = _uiState.value.voiceConf
        val volume = current?.volume ?: 100
        val index = current?.index ?: 0
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { c.setVoiceConf(enabled, volume, index) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            voiceConf = VoiceConf(enabled = enabled, volume = volume, index = index),
                            message = if (enabled) "提示音已开启" else "提示音已关闭"
                        )
                    }
                }
                .onFailure { _uiState.update { it.copy(message = "设置提示音开关失败: ${it.message}") } }
        }
    }

    fun setVoiceVolume(volume: Int) {
        val c = client ?: return
        val current = _uiState.value.voiceConf
        val enabled = current?.enabled ?: true
        val index = current?.index ?: 0
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { c.setVoiceConf(enabled, volume, index) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            voiceConf = VoiceConf(enabled = enabled, volume = volume, index = index),
                            message = "提示音音量已设为 $volume%"
                        )
                    }
                }
                .onFailure { _uiState.update { it.copy(message = "设置提示音音量失败: ${it.message}") } }
        }
    }

    private fun handleFrame(pdu: ByteArray) {
        val command = GaiaProtocol.commandValueOf(pdu)
        val type = (command shr 7) and 0x03
        val feature = (command shr 9) and 0x7F
        val cmd = command and 0x7F
        if (type != GaiaProtocol.TYPE_RESPONSE) return

        val payload = GaiaProtocol.payloadOf(pdu)

        if (feature == GaiaProtocol.FEATURE_BASIC) {
            when (cmd) {
                GaiaProtocol.CMD_GET_SUPPORTED_FEATURES,
                GaiaProtocol.CMD_GET_SUPPORTED_FEATURES_NEXT -> handleFeatures(payload)
            }
            return
        }

        when (feature) {
            GaiaProtocol.FEATURE_BATTERY -> when (cmd) {
                GaiaProtocol.CMD_GET_SUPPORTED_BATTERIES -> {
                    val supported = GaiaProtocol.parseSupportedBatteries(payload)
                    _uiState.update { it.copy(supportedBatteries = supported) }
                }
                GaiaProtocol.CMD_GET_BATTERY_LEVELS -> {
                    val levels = GaiaProtocol.parseBatteryLevels(payload)
                    _uiState.update { it.copy(batteryLevels = levels, message = "电量已更新") }
                }
            }
            GaiaProtocol.FEATURE_DAC_GAIN -> if (cmd == GaiaProtocol.CMD_GET_GAIN) {
                _uiState.update { it.copy(gainIndex = GaiaProtocol.parseGainIndex(payload)) }
            }
            GaiaProtocol.FEATURE_MUSIC_PROCESSING -> when (cmd) {
                GaiaProtocol.CMD_GET_EQ_STATE -> {
                    _uiState.update { it.copy(eqState = GaiaProtocol.parseEqState(payload)) }
                }
                GaiaProtocol.CMD_GET_AVAILABLE_EQ_PRE_SETS -> {
                    _uiState.update { it.copy(eqPresets = GaiaProtocol.parseAvailablePreSets(payload)) }
                }
                GaiaProtocol.CMD_GET_EQ_SET -> {
                    _uiState.update { it.copy(eqSelected = GaiaProtocol.parseEqSet(payload)) }
                }
            }
            GaiaProtocol.FEATURE_ONEBRINGTWO -> when (cmd) {
                GaiaProtocol.CMD_GET_ONEBRINGTWO_STATE -> {
                    _uiState.update { it.copy(oneBringTwoEnabled = GaiaProtocol.parseOneBringTwoState(payload)) }
                }
                GaiaProtocol.CMD_GET_ONEBRINGTWO_TIMEOUT -> {
                    _uiState.update { it.copy(oneBringTwoTimeout = GaiaProtocol.parseOneBringTwoTimeout(payload)) }
                }
            }
            GaiaProtocol.FEATURE_VOICE -> if (cmd == GaiaProtocol.CMD_GET_VOICE_CONF) {
                GaiaProtocol.parseVoiceConf(payload)?.let { conf ->
                    _uiState.update { it.copy(voiceConf = conf, message = "提示音状态已读取") }
                }
            }
        }
    }

    private fun handleFeatures(payload: ByteArray) {
        val page = GaiaProtocol.parseSupportedFeatures(payload)
        val merged = (pendingFeatures + page.features).distinctBy { it.id }
        if (page.hasMoreData) {
            pendingFeatures = merged
            runCatching { client?.requestSupportedFeaturesNext() }
        } else {
            pendingFeatures = emptyList()
            _uiState.update {
                it.copy(
                    features = merged,
                    message = "已发现 ${merged.size} 个功能: ${merged.joinToString { GaiaProtocol.featureName(it.id) }}"
                )
            }
        }
    }

    private fun isPillName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name.lowercase()
        return n.contains("pill") || n.contains("moondrop") || n.contains("oba") || n.contains("laplace")
    }
}
