package com.karin.hyperpill.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karin.hyperpill.pods.PillProducts
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
fun DebugPage(
    currentSpoofedName: String?,
    onSpoofDevice: (String?) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "调试功能",
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
                Text(
                    "伪装机型",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "选择后会模拟已连接该设备，仅用于界面测试，不会真实建立蓝牙连接。",
                    fontSize = 13.sp
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        PillProducts.all.forEach { product ->
                            BasicComponent(
                                title = product.name,
                                summary = product.model,
                                endActions = {
                                    if (currentSpoofedName == product.name) {
                                        Text("当前", fontSize = 12.sp)
                                    }
                                },
                                onClick = { onSpoofDevice(product.name) }
                            )
                        }
                        BasicComponent(
                            title = "取消伪装",
                            summary = "恢复真实连接状态",
                            onClick = { onSpoofDevice(null) }
                        )
                    }
                }
            }
        }
    }
}
