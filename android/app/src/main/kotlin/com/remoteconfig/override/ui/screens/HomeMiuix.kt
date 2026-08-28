package com.remoteconfig.override.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.system.Os
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.R
import com.remoteconfig.override.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 首页 — Miuix 实现。
 *
 * 内容分区与 Material 版一致（Root 状态卡 / 设备信息 / 作者·捐赠 / 源码卡 / 捐赠弹窗），
 * 组件对齐 KernelSU HomeMiuix.kt 用法：
 * `Scaffold + TopAppBar(MiuixScrollBehavior) + Column(verticalScroll + nestedScroll) + Card +
 *  BasicComponent + Button`。
 *
 * 注意（miuix 0.9.3 实际签名）：
 * - TopAppBar 的 `title` 为 String（Task 7 同款用法）
 * - BasicComponent 的槽位名为 `startAction`/`endActions`（简报中的 leftAction/rightActions 在 0.9.3 不存在）
 * - TextButton 只接收 `text: String`（无 content 槽），故捐赠按钮用带 content 槽的 `Button`
 */
@Composable
fun HomeContentMiuix(viewModel: MainViewModel) {
    val systemStatus by viewModel.systemStatus.collectAsState()
    val cosaVersion by viewModel.cosaVersion.collectAsState()
    val context = LocalContext.current

    val kernelVersion = remember {
        try { Os.uname().release } catch (_: Exception) { "未知" }
    }

    var showDonateDialog by remember { mutableStateOf(false) }
    var donateImageId by remember { mutableIntStateOf(0) }

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "Color云控修改",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RootStatusCard(systemStatus = systemStatus)
            DeviceInfoCard(kernelVersion = kernelVersion, cosaVersion = cosaVersion)
            AuthorCard(
                onCoolapkClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coolapk.com/u/1404550")))
                    } catch (_: Exception) {}
                },
                onWechatDonate = { donateImageId = R.drawable.wechat; showDonateDialog = true },
                onAlipayDonate = { donateImageId = R.drawable.alipay; showDonateDialog = true },
            )
            SourceCard(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Yunnijian/Oplus_RemoteConfig_Override")))
                    } catch (_: Exception) {}
                },
            )
        }
    }

    // ── 捐赠弹窗（Miuix 用 WindowDialog，内容与 Material 版一致）──
    WindowDialog(
        show = showDonateDialog,
        onDismissRequest = { showDonateDialog = false },
    ) {
        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = donateImageId),
                contentDescription = null,
                modifier = Modifier
                    .width(260.dp)
                    .heightIn(max = 400.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

// ── Root 状态卡 ──
// 三态颜色映射：primaryContainer/tertiaryContainer/errorContainer 对应 正常/警告/错误（简报要求）
@Composable
private fun RootStatusCard(systemStatus: MainViewModel.SystemStatus) {
    val containerColor = when {
        systemStatus.isRooted && systemStatus.dbAvailable -> colorScheme.primaryContainer
        systemStatus.isRooted -> colorScheme.tertiaryContainer
        else -> colorScheme.errorContainer
    }
    val onContainerColor = when {
        systemStatus.isRooted && systemStatus.dbAvailable -> colorScheme.onPrimaryContainer
        systemStatus.isRooted -> colorScheme.onTertiaryContainer
        else -> colorScheme.onErrorContainer
    }
    val icon = when {
        systemStatus.isRooted && systemStatus.dbAvailable -> Icons.Filled.Verified
        systemStatus.isRooted -> Icons.Filled.Warning
        else -> Icons.Filled.GppBad
    }
    val title = when {
        systemStatus.isRooted && systemStatus.dbAvailable -> "Root 权限正常"
        systemStatus.isRooted -> "数据库连接失败"
        else -> "未授予 Root 权限"
    }
    val subtitle = when {
        systemStatus.isRooted && systemStatus.dbAvailable -> "数据库已连接，可读写配置"
        systemStatus.isRooted -> "已获取 Root 权限，但数据库文件不可访问"
        else -> "请授予 Root 权限后重启应用"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = containerColor),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onContainerColor,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = onContainerColor,
                )
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = onContainerColor.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ── 设备信息卡 ──
@Composable
private fun DeviceInfoCard(kernelVersion: String, cosaVersion: String) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            BasicComponent(title = "设备型号", summary = "${Build.MANUFACTURER} ${Build.MODEL}")
            BasicComponent(title = "安卓版本", summary = "Android ${Build.VERSION.RELEASE}")
            BasicComponent(title = "内核版本", summary = kernelVersion)
            BasicComponent(title = "应用增强服务", summary = "v$cosaVersion · com.oplus.cosa")
        }
    }
}

// ── 作者卡片（作者信息 + 版本 + 捐赠描述 + 微信/支付宝捐赠入口）──
@Composable
private fun AuthorCard(
    onCoolapkClick: () -> Unit,
    onWechatDonate: () -> Unit,
    onAlipayDonate: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            BasicComponent(
                title = "Smartisan_Apple",
                summary = "酷安 @Smartisan_Apple",
                startAction = {
                    Image(
                        painter = painterResource(id = R.drawable.author_avatar),
                        contentDescription = "作者头像",
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                },
                endActions = {
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowRight,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariantActions,
                    )
                },
                onClick = onCoolapkClick,
            )
            // 版本行（与 Material 版作者信息第三行一致）
            Text(
                text = "Color云控修改 v1.2.1",
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
            )
            // 捐赠描述
            Text(
                text = "Color云控修改始终保持免费，向开发者捐赠以表示支持。",
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            // 捐赠入口
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DonateButton(icon = R.drawable.ic_wechat, label = "微信", onClick = onWechatDonate)
                DonateButton(icon = R.drawable.ic_alipay, label = "支付宝", onClick = onAlipayDonate)
            }
        }
    }
}

@Composable
private fun RowScope.DonateButton(icon: Int, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 16.sp)
    }
}

// ── 源码卡 ──
@Composable
private fun SourceCard(onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        BasicComponent(
            title = "查看源代码",
            summary = "在 GitHub 上查看源代码",
            endActions = {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariantActions,
                )
            },
            onClick = onClick,
        )
    }
}
