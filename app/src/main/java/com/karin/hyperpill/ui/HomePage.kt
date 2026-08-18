package com.karin.hyperpill.ui

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karin.hyperpill.PillUiState
import com.karin.hyperpill.pods.GaiaProtocol
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private enum class ConnectionStatus { CONNECTED, WARNING, DISCONNECTED }

@Composable
private fun connectionStatus(state: PillUiState, connected: Boolean): ConnectionStatus = when {
    connected && (state.features.isNotEmpty() || !state.message.contains("已发现")) -> ConnectionStatus.CONNECTED
    connected -> ConnectionStatus.WARNING
    state.message.contains("错误") ||
        state.message.contains("失败") ||
        state.message.contains("异常") -> ConnectionStatus.WARNING
    else -> ConnectionStatus.DISCONNECTED
}

@Composable
private fun statusColors(status: ConnectionStatus): Pair<Color, Color> {
    val dark = isSystemInDarkTheme()
    return when (status) {
        ConnectionStatus.CONNECTED -> if (dark) Color(0xFF1E3A2A) to Color(0xFFB8F0C0)
        else Color(0xFFDCF5E1) to Color(0xFF1A5B2E)
        ConnectionStatus.WARNING -> if (dark) Color(0xFF3A321A) to Color(0xFFF5E6B8)
        else Color(0xFFFFF3D6) to Color(0xFF7A5B00)
        ConnectionStatus.DISCONNECTED -> if (dark) Color(0xFF3A1E1E) to Color(0xFFF5C0C0)
        else Color(0xFFFFE0E0) to Color(0xFF7A1A1A)
    }
}

@Composable
fun HomePage(
    modifier: Modifier = Modifier,
    state: PillUiState,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onSelectDevice: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshDevices: () -> Unit,
    onRefreshBattery: () -> Unit,
    onOpenConfig: () -> Unit
) {
    val connected = state.connected || state.spoofedDeviceName != null
    val deviceName = state.spoofedDeviceName ?: state.selectedDevice?.name
    val status = connectionStatus(state, connected)
    val retry = {
        if (connected) onRefreshBattery()
        else state.selectedDevice?.let { onSelectDevice(it) } ?: onRefreshDevices()
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StatusCard(
                state = state,
                status = status,
                connected = connected,
                deviceName = deviceName,
                onRetry = retry
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BatteryMiniCard(
                    levels = state.batteryLevels,
                    connected = connected,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                ConfigEntryCard(
                    connected = connected,
                    onClick = onOpenConfig,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        if (!permissionGranted) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("需要蓝牙权限", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("授予蓝牙连接权限后才能读取已配对的 Moondrop Pill 设备。")
                        Button(onClick = onRequestPermission) {
                            Text("授予权限")
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("选择设备", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (state.refreshingDevices) {
                        val transition = rememberInfiniteTransition(label = "refresh")
                        val angle by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 800, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "refreshAngle"
                        )
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = "刷新中",
                                modifier = Modifier.rotate(angle)
                            )
                        }
                    } else {
                        IconButton(onClick = onRefreshDevices) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = "刷新设备"
                            )
                        }
                    }
                }
            }

            if (state.devices.isEmpty()) {
                item {
                    Text("未找到名称包含 Pill / MOONDROP 的设备", fontSize = 14.sp)
                }
            } else {
                items(state.devices, key = { it.address }) { device ->
                    DeviceCard(
                        device = device,
                        selected = state.selectedDevice?.address == device.address,
                        connected = connected,
                        enabled = !state.busy && !connected,
                        onClick = { onSelectDevice(device) },
                        onDisconnect = onDisconnect
                    )
                }
            }

            if (state.busy) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfiniteProgressIndicator()
                        Text(state.message)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    state: PillUiState,
    status: ConnectionStatus,
    connected: Boolean,
    deviceName: String?,
    onRetry: () -> Unit
) {
    var showErrorDialog by remember { mutableStateOf(false) }
    val (bg, fg) = statusColors(status)
    val title = when (status) {
        ConnectionStatus.CONNECTED -> "已连接"
        ConnectionStatus.WARNING -> "连接异常"
        ConnectionStatus.DISCONNECTED -> "未连接"
    }
    val summary = when (status) {
        ConnectionStatus.CONNECTED -> "连接正常"
        ConnectionStatus.WARNING -> state.message.ifBlank { "连接异常，点击查看详情" }
        ConnectionStatus.DISCONNECTED -> "请选择下方设备进行连接"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = bg, contentColor = fg),
        onClick = if (status == ConnectionStatus.WARNING) {
            { showErrorDialog = true }
        } else {
            null
        }
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(summary, fontSize = 14.sp)
            if (status == ConnectionStatus.CONNECTED) {
                Text("设备：${deviceName ?: "--"}", fontSize = 14.sp)
            }
            if (status == ConnectionStatus.WARNING) {
                Text("提示：点击卡片可查看详细报错信息", fontSize = 12.sp)
            }
        }
    }

    if (status == ConnectionStatus.WARNING) {
        OverlayDialog(
            show = showErrorDialog,
            title = "连接信息",
            onDismissRequest = { showErrorDialog = false }
        ) {
            Text(
                text = state.message.ifBlank { "未知错误" },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )
            TextButton(
                text = "重试",
                onClick = {
                    showErrorDialog = false
                    onRetry()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
private fun BatteryMiniCard(
    levels: Map<Int, Int>,
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    val left = levels[GaiaProtocol.BATTERY_LEFT]
    val right = levels[GaiaProtocol.BATTERY_RIGHT]
    val textColor = if (connected) Color.Unspecified else Color.Gray
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("电量", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("L  ${left?.let { "$it%" } ?: "--"}", fontSize = 15.sp, color = textColor)
                Text("R  ${right?.let { "$it%" } ?: "--"}", fontSize = 15.sp, color = textColor)
            }
        }
    }
}

@Composable
private fun ConfigEntryCard(
    connected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = if (connected) Color.Unspecified else Color.Gray
    Card(
        onClick = onClick,
        modifier = modifier,
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("配置耳机", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                Text("进入设备参数设置", fontSize = 13.sp, color = textColor)
            }
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = "进入配置",
                modifier = Modifier.padding(start = 4.dp),
                tint = if (connected) LocalContentColor.current else Color.Gray
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: BluetoothDevice,
    selected: Boolean,
    connected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = device.name ?: "未知设备",
            summary = device.address,
            enabled = enabled,
            onClick = onClick,
            endActions = {
                if (selected && connected) {
                    TextButton(
                        text = "断开",
                        onClick = onDisconnect,
                        minWidth = 0.dp,
                        minHeight = 0.dp
                    )
                } else if (selected) {
                    Text("已选择", fontSize = 12.sp)
                }
            }
        )
    }
}
