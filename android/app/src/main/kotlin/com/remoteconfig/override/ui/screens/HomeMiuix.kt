package com.remoteconfig.override.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.system.Os
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.remoteconfig.override.ui.LocalMainPagerState
import com.remoteconfig.override.platform.appDisplayName
import com.remoteconfig.override.ui.theme.LocalPlatform
import com.remoteconfig.override.ui.theme.isInDarkTheme
import com.remoteconfig.override.ui.util.resolveDeviceName
import com.remoteconfig.override.viewmodel.HyperOsViewModel
import com.remoteconfig.override.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
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
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 首页 — Miuix 实现。
 *
 * 内容分区与 Material 版一致（Root 状态卡 / 设备信息 / 作者 / 源码卡），
 * 组件对齐 KernelSU HomeMiuix.kt 用法：
 * `Scaffold + TopAppBar(MiuixScrollBehavior) + Column(verticalScroll + nestedScroll) + Card +
 *  BasicComponent`。
 *
 * 注意（miuix 0.9.3 实际签名）：
 * - TopAppBar 的 `title` 为 String（Task 7 同款用法）
 * - BasicComponent 的槽位名为 `startAction`/`endActions`（简报中的 leftAction/rightActions 在 0.9.3 不存在）
 *
 * [bottomInnerPadding]：底栏占位高度，在滚动内容末尾用 `Spacer` 消费
 * （对齐 KernelSU HomeMiuix.kt:84,177）。Pager 不加 bottom padding，内容需铺到底栏下方
 * 供悬浮玻璃底栏采样折射。
 */
@Composable
fun HomeContentMiuix(viewModel: MainViewModel, bottomInnerPadding: Dp = 0.dp) {
    val systemStatus by viewModel.systemStatus.collectAsStateWithLifecycle()
    val installedConfigCount by viewModel.installedConfigCount.collectAsStateWithLifecycle()
    val cosaVersion by viewModel.cosaVersion.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mainPagerState = LocalMainPagerState.current

    // HyperOS 平台分支：状态卡与设备信息卡的数据源切换为 Joyose（轻量 stat，无重查询）
    val hyperOS = LocalPlatform.current == Platform.HyperOS
    val hyperOsViewModel: HyperOsViewModel = composeViewModel()
    val joyoseStat by hyperOsViewModel.statState.collectAsStateWithLifecycle()
    val listState by hyperOsViewModel.listState.collectAsStateWithLifecycle()
    val commonState by hyperOsViewModel.commonState.collectAsStateWithLifecycle()
    val joyoseVersion = remember(hyperOS) { if (hyperOS) hyperOsViewModel.joyoseVersion else "" }
    LaunchedEffect(hyperOS) {
        if (hyperOS) {
            hyperOsViewModel.refreshStat()
            hyperOsViewModel.refreshList()
            hyperOsViewModel.refreshCommon()
        }
    }

    val kernelVersion = remember {
        try { Os.uname().release } catch (_: Exception) { "未知" }
    }

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        // 不消费底部 inset：内容必须能铺到悬浮玻璃底栏下方（底部留白由末尾 Spacer 承担），
        // 与 ConfigListMiuix / SettingsMiuix 一致，对齐 KernelSU HomeMiuix.kt:100
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = LocalPlatform.current.appDisplayName(),
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
            // ── Root / Joyose 状态卡（照搬 KernelSU v3.2.5 HomeMiuix.kt StatusCard）──
            // 检测未完成前显示中性"正在检测"卡，避免冷启动闪现红色错误卡误导用户。
            StatusCardLayout(
                hyperOS = hyperOS,
                systemStatus = systemStatus,
                joyoseStat = joyoseStat,
                appConfigCount = if (hyperOS) listState.apps.size else systemStatus.configuredCount,
                commonConfigCount = commonState.switches.size,
                installedConfigCount = installedConfigCount,
                onAppConfigClick = { mainPagerState.animateToPage(1) },
                onCommonConfigClick = { mainPagerState.animateToPage(2) },
                onRefreshClick = {
                    if (hyperOS) {
                        hyperOsViewModel.refreshStat()
                        hyperOsViewModel.refreshList()
                        hyperOsViewModel.refreshCommon()
                    } else {
                        viewModel.refreshAll()
                    }
                },
            )
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
}

// ── Root / Joyose 状态卡（照搬 KernelSU v3.2.5 HomeMiuix.kt StatusCard）──
// 左侧大卡：KernelSU 激活态"绿卡 + CheckCircleOutline 大图标 + 状态文字"（带 Tilt 按压反馈）；
// 右侧小卡：KernelSU 超级用户/模块计数卡结构（应用配置 + 通用配置/已安装应用配置）。
// 数据源：ColorOS = MainViewModel.SystemStatus / installedConfigCount；
//         HyperOS = HyperOsViewModel.statState / listState / commonState。
// 卡片点击对齐 KernelSU：大卡点击重新检测，小卡点击跳转对应页面。
// 检测未完成前显示中性"正在检测"卡，避免冷启动闪现红色错误卡误导用户。
@Composable
private fun StatusCardLayout(
    hyperOS: Boolean,
    systemStatus: MainViewModel.SystemStatus,
    joyoseStat: JoyoseManager.Stat?,
    appConfigCount: Int,
    commonConfigCount: Int,
    installedConfigCount: Int,
    onAppConfigClick: () -> Unit,
    onCommonConfigClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    val checking = if (hyperOS) joyoseStat == null else !systemStatus.checked
    val connected = if (hyperOS) {
        joyoseStat != null && (joyoseStat.smartp.exists || joyoseStat.teg.exists)
    } else {
        !checking && systemStatus.isRooted && systemStatus.dbAvailable
    }
    val frozen = hyperOS && joyoseStat?.sp?.frozen == true

    // 照搬 KernelSU v3.2.5 激活态大卡配色（isDynamicColor → secondaryContainer；
    // 非 Monet 时深色 0xFF1A3825 / 浅色 0xFFDFFAE4 与 CheckCircleOutline 绿 0xFF36D167 一致）
    val containerColor = when {
        checking -> colorScheme.surfaceContainer
        connected -> when {
            isDynamicColor -> colorScheme.secondaryContainer
            isInDarkTheme() -> Color(0xFF1A3825)
            else -> Color(0xFFDFFAE4)
        }
        else -> colorScheme.errorContainer
    }
    val iconTint = when {
        checking -> colorScheme.onSurfaceVariantSummary
        connected -> if (isDynamicColor) colorScheme.primary.copy(alpha = 0.8f) else Color(0xFF36D167)
        else -> colorScheme.onErrorContainer
    }
    val iconVector = when {
        checking -> Icons.Rounded.HourglassEmpty
        connected -> Icons.Rounded.CheckCircleOutline
        else -> Icons.Rounded.ErrorOutline
    }
    val title = when {
        checking -> "正在检测..."
        connected -> if (hyperOS) "Joyose 云控正常" else "Root 权限正常"
        else -> if (hyperOS) "Joyose 云控不可用" else "请授予 Root 权限"
    }
    val subtitle = when {
        checking -> if (hyperOS) "正在读取 Joyose 数据库状态" else "正在检测 Root 权限与数据库状态"
        connected -> if (hyperOS) {
            "SmartP / teg_config 已连接 · ${if (frozen) "云控已冻结" else "云控未冻结"}"
        } else {
            "数据库已连接，可读写配置"
        }
        else -> if (hyperOS) {
            "未找到 Joyose 数据库，请确认设备为 HyperOS"
        } else {
            if (systemStatus.isRooted) "数据库连接失败" else "请授予 Root 权限后重启应用"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左大卡：照搬 KernelSU v3.2.5（Card + onClick + showIndication + Tilt + Box + offset 大图标 + 状态文字）
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.defaultColors(color = containerColor),
            onClick = onRefreshClick,
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Tilt,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(38.dp, 45.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Icon(
                        modifier = Modifier.size(170.dp),
                        imageVector = iconVector,
                        tint = iconTint,
                        contentDescription = null,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(all = 16.dp),
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = subtitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            SmallCountCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "应用配置",
                count = appConfigCount,
                onClick = onAppConfigClick,
            )
            Spacer(Modifier.height(12.dp))
            SmallCountCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = if (hyperOS) "通用配置" else "已安装应用配置",
                count = if (hyperOS) commonConfigCount else installedConfigCount,
                onClick = if (hyperOS) onCommonConfigClick else onAppConfigClick,
            )
        }
    }
}

@Composable
private fun SmallCountCard(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(16.dp),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = label,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = count.toString(),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
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
            BasicComponent(title = "系统指纹", summary = Build.FINGERPRINT.ifBlank { "未知" })
            if (hyperOS) {
                BasicComponent(
                    title = "Joyose 版本",
                    summary = "v$joyoseVersion · com.xiaomi.joyose",
                )
            } else {
                BasicComponent(title = "应用增强服务", summary = "v$cosaVersion · com.oplus.cosa")
            }
        }
    }
}

// ── 作者卡片（作者信息）──
@Composable
private fun AuthorCard(
    onCoolapkClick: () -> Unit,
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
        }
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
