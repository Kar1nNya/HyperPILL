package com.karin.hyperpill.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karin.hyperpill.PillUiState
import com.karin.hyperpill.R
import com.karin.hyperpill.pods.PillProducts
import com.karin.hyperpill.pods.VoiceConf
import com.karin.hyperpill.utils.DeviceIconProvider
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.RichTooltipBox
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.rememberTooltipState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

private val bocchiTheRockUuids = setOf(
    "fb36c2bb-845d-4e0a-9b83-193b046bc6cb",
    "91e6febd-d61b-4849-9c0f-5d4e9627700d",
    "655903e7-046f-49d8-be63-bbadb3ea7881",
    "42b775b3-2781-47f2-95b1-86ef7de4f9bd"
)

@Composable
fun DeviceConfigPage(
    state: PillUiState,
    onBack: () -> Unit,
    onSetGain: (Int) -> Unit,
    onSelectEq: (Int) -> Unit,
    onSetOneBringTwo: (Boolean) -> Unit,
    onSetOneBringTwoTimeout: (Int) -> Unit,
    onSetVoiceEnabled: (Boolean) -> Unit,
    onSetVoiceVolume: (Int) -> Unit
) {
    val deviceName = state.spoofedDeviceName ?: state.selectedDevice?.name
    val product = PillProducts.fromDeviceName(deviceName)
    val showBandLogo = product?.uuid in bocchiTheRockUuids

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = deviceName ?: "设备配置",
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(
                            DeviceIconProvider.getDeviceIconResId(deviceName)
                        ),
                        contentDescription = deviceName ?: "耳机",
                        modifier = Modifier
                            .fillMaxWidth(if (showBandLogo) 0.6f else 1f)
                            .padding(vertical = 20.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            item {
                GainCard(state.gainIndex, onSetGain)
            }

            item {
                EqCard(
                    selected = state.eqSelected,
                    onSelectEq = onSelectEq
                )
            }

            item {
                VoiceCard(state.voiceConf, onSetVoiceEnabled, onSetVoiceVolume)
            }

            item {
                OneBringTwoCard(
                    enabled = state.oneBringTwoEnabled,
                    timeout = state.oneBringTwoTimeout,
                    onSetEnabled = onSetOneBringTwo,
                    onSetTimeout = onSetOneBringTwoTimeout
                )
            }

            if (showBandLogo) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.band_logo),
                            contentDescription = "Band Logo",
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .padding(vertical = 16.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GainCard(gainIndex: Int?, onSetGain: (Int) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("增益", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("当前：${gainLabel(gainIndex)}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("低" to 2, "中" to 1, "高" to 0).forEach { (label, value) ->
                    TextButton(
                        text = label,
                        onClick = { onSetGain(value) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EqCard(
    selected: Int?,
    onSelectEq: (Int) -> Unit
) {
    val eqPresets = listOf(0, 1, 2, 63)
    val eqLabels = listOf("Reference", "Bass+", "Bass-", "Custom")
    val selectedIndex = selected?.let { eqPresets.indexOf(it) } ?: -1
    val eqItems = remember { eqLabels.map { DropdownItem(text = it) } }
    val context = LocalContext.current
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "EQ设置",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp)
            )
            WindowSpinnerPreference(
                title = "预设曲线",
                items = eqItems,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { index ->
                    onSelectEq(eqPresets[index])
                }
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "自定义EQ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "该模块只提供EQ切换，不支持修改自定义EQ配置功能，如需要手动调整或下载社区配置请前往Moondrop App",
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    RichTooltipBox(
                        text = "因为本质是覆盖板载的EQ配置，修改后的EQ可以通过本模块正常切换（CustomEQ）",
                        state = tooltipState,
                        positioning = TooltipAnchorPosition.End
                    ) {
                        IconButton(onClick = { scope.launch { tooltipState.show() } }) {
                            Icon(
                                imageVector = MiuixIcons.Help,
                                contentDescription = "自定义EQ说明"
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            setClassName(
                                "com.moondroplab.moondrop.moondrop_app",
                                "com.moondroplab.moondrop.moondrop_app.MainActivity"
                            )
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("打开 Moondrop App")
                }
            }
        }
    }
}

@Composable
private fun VoiceCard(
    conf: VoiceConf?,
    onSetEnabled: (Boolean) -> Unit,
    onSetVolume: (Int) -> Unit
) {
    var localVolume by remember(conf?.volume) { mutableFloatStateOf((conf?.volume ?: 0).toFloat()) }
    LaunchedEffect(conf?.volume) {
        localVolume = (conf?.volume ?: 0).toFloat()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("提示音", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("提示音开关")
                Switch(
                    checked = conf?.enabled ?: false,
                    onCheckedChange = onSetEnabled
                )
            }
            Text("提示音音量：${localVolume.roundToInt()}%")
            Slider(
                value = localVolume,
                onValueChange = { localVolume = it },
                onValueChangeFinished = { onSetVolume(localVolume.roundToInt()) },
                valueRange = 0f..100f,
                steps = 99
            )
            if (conf == null) {
                Text("提示音状态读取中…", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun OneBringTwoCard(
    enabled: Boolean?,
    timeout: Int?,
    onSetEnabled: (Boolean) -> Unit,
    onSetTimeout: (Int) -> Unit
) {
    val timeoutOptions = listOf(5, 10, 30, 60)
    val timeoutIndex = timeout?.let { timeoutOptions.indexOf(it) } ?: -1
    val timeoutItems = remember { timeoutOptions.map { DropdownItem(text = "${it}分钟") } }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("双设备连接", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = enabled ?: false,
                    onCheckedChange = onSetEnabled
                )
            }
            WindowSpinnerPreference(
                title = "超时时间",
                summary = if (timeout != null) "自动断开等待" else "未设置",
                items = timeoutItems,
                selectedIndex = timeoutIndex,
                onSelectedIndexChange = { index -> onSetTimeout(timeoutOptions[index]) }
            )
        }
    }
}

private fun gainLabel(index: Int?): String = when (index) {
    0 -> "高"
    1 -> "中"
    2 -> "低"
    else -> "--"
}

