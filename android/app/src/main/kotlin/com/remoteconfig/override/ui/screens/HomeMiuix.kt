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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.remoteconfig.override.R
import com.remoteconfig.override.data.JoyoseManager
import com.remoteconfig.override.platform.Platform
import com.remoteconfig.override.ui.theme.LocalPlatform
import com.remoteconfig.override.ui.util.resolveDeviceName
import com.remoteconfig.override.viewmodel.HyperOsViewModel
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
 *
 * [bottomInnerPadding]：底栏占位高度，在滚动内容末尾用 `Spacer` 消费
 * （对齐 KernelSU HomeMiuix.kt:84,177）。Pager 不加 bottom padding，内容需铺到底栏下方
 * 供悬浮玻璃底栏采样折射。
 */
@Composable
fun HomeContentMiuix(viewModel: MainViewModel, bottomInnerPadding: Dp = 0.dp) {
    val systemStatus by viewModel.systemStatus.collectAsState()
    val cosaVersion by viewModel.cosaVersion.collectAsState()
    val context = LocalContext.current

    // HyperOS 平台分支：状态卡与设备信息卡的数据源切换为 Joyose（轻量 stat，无重查询）
    val hyperOS = LocalPlatform.current == Platform.HyperOS
    val hyperOsViewModel: HyperOsViewModel = composeViewModel()
    val joyoseStat by hyperOsViewModel.statState.collectAsState()
    val joyoseVersion = remember(hyperOS) { if (hyperOS) hyperOsViewModel.joyoseVersion else "" }
    LaunchedEffect(hyperOS) { if (hyperOS) hyperOsViewModel.refreshStat() }

    val kernelVersion = remember {
        try { Os.uname().release } catch (_: Exception) { "未知" }
    }

    var showDonateDialog by remember { mutableStateOf(false) }
    var donateImageId by remember { mutableIntStateOf(0) }

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        // 不消费底部 inset：内容必须能铺到悬浮玻璃底栏下方（底部留白由末尾 Spacer 承担），
        // 与 ConfigListMiuix / SettingsMiuix 一致，对齐 KernelSU HomeMiuix.kt:100
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
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
                // bottom 留白由末尾 Spacer(bottomInnerPadding) 承担，不再叠加 bottom padding
                // （对齐 KernelSU #3620：原先 spacedBy 12dp + bottom 12dp 双倍留白）
                .padding(start = 12.dp, end = 12.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (hyperOS) {
                JoyoseStatusCard(stat = joyoseStat)
            } else {
                RootStatusCard(systemStatus = systemStatus)
            }
            DeviceInfoCard(
                kernelVersion = kernelVersion,
                hyperOS = hyperOS,
                joyoseVersion = joyoseVersion,
                cosaVersion = cosaVersion,
            )
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
            // 底栏留白：由页面自己消费（对齐 KernelSU HomeMiuix.kt:177）
            Spacer(Modifier.height(bottomInnerPadding))
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
// 四态颜色映射：surfaceContainer/primaryContainer/tertiaryContainer/errorContainer
// 对应 检测中/正常/警告/错误；检测未完成前不得显示红色错误卡（避免误导用户重启应用）
@Composable
private fun RootStatusCard(systemStatus: MainViewModel.SystemStatus) {
    val containerColor = when {
        !systemStatus.checked -> colorScheme.surfaceContainer
        systemStatus.isRooted && systemStatus.dbAvailable -> colorScheme.primaryContainer
        systemStatus.isRooted -> colorScheme.tertiaryContainer
        else -> colorScheme.errorContainer
    }
    val onContainerColor = when {
        !systemStatus.checked -> colorScheme.onSurfaceVariantSummary
        systemStatus.isRooted && systemStatus.dbAvailable -> colorScheme.onPrimaryContainer
        systemStatus.isRooted -> colorScheme.onTertiaryContainer
        else -> colorScheme.onErrorContainer
    }
    val icon = when {
        !systemStatus.checked -> Icons.Filled.HourglassEmpty
        systemStatus.isRooted && systemStatus.dbAvailable -> Icons.Filled.Verified
        systemStatus.isRooted -> Icons.Filled.Warning
        else -> Icons.Filled.GppBad
    }
    val title = when {
        !systemStatus.checked -> "正在检测..."
        systemStatus.isRooted && systemStatus.dbAvailable -> "Root 权限正常"
        systemStatus.isRooted -> "数据库连接失败"
        else -> "未授予 Root 权限"
    }
    val subtitle = when {
        !systemStatus.checked -> "正在检测 Root 权限与数据库状态"
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

// ── Joyose 状态卡（HyperOS）──
// 数据源 = HyperOsViewModel.statState（joyose-stat 轻量查询）。
// 双库任一存在 → primaryContainer 正常态；双库全缺 → errorContainer 不可用态；
// stat 未返回前 → surfaceContainer 检测中态（避免闪红）。
@Composable
private fun JoyoseStatusCard(stat: JoyoseManager.Stat?) {
    val connected = stat != null && (stat.smartp.exists || stat.teg.exists)
    val containerColor = when {
        stat == null -> colorScheme.surfaceContainer
        connected -> colorScheme.primaryContainer
        else -> colorScheme.errorContainer
    }
    val onContainerColor = when {
        stat == null -> colorScheme.onSurfaceVariantSummary
        connected -> colorScheme.onPrimaryContainer
        else -> colorScheme.onErrorContainer
    }
    val icon = when {
        stat == null -> Icons.Filled.HourglassEmpty
        connected -> Icons.Filled.Verified
        else -> Icons.Filled.GppBad
    }
    val title = when {
        stat == null -> "正在检测..."
        connected -> "Joyose 云控正常"
        else -> "Joyose 云控不可用"
    }
    val subtitle = when {
        stat == null -> "正在读取 Joyose 数据库状态"
        connected -> "SmartP / teg_config 已连接 · " +
            if (stat.sp.frozen) "云控已冻结" else "云控未冻结"
        else -> "未找到 Joyose 数据库，请确认设备为 HyperOS"
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
// 设备型号对齐 KernelSU：resolveDeviceName() 解析各厂商市场名（remember 缓存一次）。
// 内核版本与 KernelSU Home 一致：Os.uname().release。
// HyperOS 平台末行切 Joyose 云控服务（com.xiaomi.joyose，PackageManager 版本）。
@Composable
private fun DeviceInfoCard(
    kernelVersion: String,
    hyperOS: Boolean,
    joyoseVersion: String,
    cosaVersion: String,
) {
    val deviceModel = remember { resolveDeviceName() }
    Card(Modifier.fillMaxWidth()) {
        Column {
            BasicComponent(title = "设备型号", summary = deviceModel)
            BasicComponent(title = "安卓版本", summary = "Android ${Build.VERSION.RELEASE}")
            BasicComponent(title = "内核版本", summary = kernelVersion)
            if (hyperOS) {
                BasicComponent(
                    title = "Joyose 云控服务",
                    summary = "v$joyoseVersion · com.xiaomi.joyose",
                )
            } else {
                BasicComponent(title = "应用增强服务", summary = "v$cosaVersion · com.oplus.cosa")
            }
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
