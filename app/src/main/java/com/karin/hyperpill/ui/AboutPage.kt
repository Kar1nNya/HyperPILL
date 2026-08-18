package com.karin.hyperpill.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karin.hyperpill.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text

private data class Developer(
    val name: String,
    val url: String,
    val avatarRes: Int
)

private data class OpenSourceProject(
    val name: String,
    val url: String
)

private val developers = listOf(
    Developer(
        name = "Kar1nNya",
        url = "https://github.com/Kar1nNya",
        avatarRes = R.drawable.developer_karin
    ),
    Developer(
        name = "DeepSeek",
        url = "https://www.deepseek.com/",
        avatarRes = R.drawable.developer_deepseek
    )
)

private val openSourceProjects = listOf(
    OpenSourceProject("HyperOriG", "https://github.com/KiriChen-Wind/HyperOriG"),
    OpenSourceProject("OppoPods (by 1812z)", "https://github.com/1812z/OppoPods"),
    OpenSourceProject("OppoPods (by Leaf-lsgtky)", "https://github.com/Leaf-lsgtky/OppoPods"),
    OpenSourceProject("HyperPods", "https://github.com/Art-Chen/HyperPods"),
    OpenSourceProject("Miuix", "https://github.com/compose-miuix-ui/miuix"),
    OpenSourceProject("LibXposed API", "https://github.com/LSPosed/LibXposed")
)

@Composable
fun AboutPage(
    modifier: Modifier = Modifier,
    onOpenDebug: () -> Unit
) {
    val context = LocalContext.current
    val packageInfo = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: "未知"
    val versionCode = packageInfo?.longVersionCode?.toString() ?: "未知"
    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastVersionTap by remember { mutableLongStateOf(0L) }
    var showDebugButton by remember { mutableStateOf(false) }

    fun onVersionClick() {
        val now = System.currentTimeMillis()
        versionTapCount = if (now - lastVersionTap < 2000) versionTapCount + 1 else 1
        lastVersionTap = now
        if (versionTapCount >= 3) {
            showDebugButton = true
            versionTapCount = 0
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("软件信息", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    InfoRow("应用名称", "HyperPILL")
                    InfoRow("版本号", versionName, onClick = { onVersionClick() })
                    InfoRow("版本代码", versionCode)
                    InfoRow("包名", context.packageName)
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = showDebugButton,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Button(
                    onClick = onOpenDebug,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("调试功能")
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "开发者",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
                    )
                    developers.forEach { developer ->
                        BasicComponent(
                            title = developer.name,
                            summary = developer.url,
                            startAction = {
                                Image(
                                    painter = painterResource(developer.avatarRes),
                                    contentDescription = "${developer.name} 头像",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                )
                            },
                            onClick = { openUrl(context, developer.url) }
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "引用",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
                    )
                    openSourceProjects.forEach { project ->
                        BasicComponent(
                            title = project.name,
                            summary = project.url,
                            onClick = { openUrl(context, project.url) }
                        )
                    }
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() }),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp)
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
