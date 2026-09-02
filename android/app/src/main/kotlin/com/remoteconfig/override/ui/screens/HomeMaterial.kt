package com.remoteconfig.override.ui.screens

import android.content.Intent
import android.net.Uri
import android.system.Os
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.remoteconfig.override.R
import com.remoteconfig.override.data.JoyoseManager
import com.remoteconfig.override.platform.Platform
import com.remoteconfig.override.platform.appDisplayName
import com.remoteconfig.override.ui.LocalMainPagerState
import com.remoteconfig.override.ui.component.material.ExpressiveScaffold
import com.remoteconfig.override.ui.component.material.TonalCard
import com.remoteconfig.override.ui.theme.LocalPlatform
import com.remoteconfig.override.ui.util.resolveDeviceName
import com.remoteconfig.override.viewmodel.HyperOsViewModel
import com.remoteconfig.override.viewmodel.MainViewModel
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

/**
 * 首页 — Material 3 实现（原 HomeScreen.kt 的 HomeContent 迁入）。
 *
 * 视觉/行为与 Task 8 之前逐字保持（Root 状态卡 / 设备信息 / 作者·捐赠 / 源码卡 / 捐赠弹窗），
 * 仅删除了从未被 MainScreen 传递的 `isActive`/`onNavigateConfig` 参数；
 * `modifier` 在体内使用，保留默认值。
 *
 * [bottomInnerPadding]：底栏占位高度，在滚动内容末尾用 `Spacer` 消费
 * （对齐 KernelSU HomeMaterial.kt:66,140）。原先硬编码的 `bottom = 96.dp` 由它替代，
 * 因为 Pager 已不加 bottom padding（内容需铺到底栏下方供玻璃采样折射）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContentMaterial(
    viewModel: MainViewModel,
    bottomInnerPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val systemStatus by viewModel.systemStatus.collectAsState()
    val installedConfigCount by viewModel.installedConfigCount.collectAsState()
    val cosaVersion by viewModel.cosaVersion.collectAsState()
    val context = LocalContext.current
    val mainPagerState = LocalMainPagerState.current

    // HyperOS 平台分支：状态卡与设备信息卡的数据源切换为 Joyose（轻量 stat，无重查询）
    val hyperOS = LocalPlatform.current == Platform.HyperOS
    val hyperOsViewModel: HyperOsViewModel = composeViewModel()
    val joyoseStat by hyperOsViewModel.statState.collectAsState()
    val listState by hyperOsViewModel.listState.collectAsState()
    val commonState by hyperOsViewModel.commonState.collectAsState()
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
    // 设备型号对齐 KernelSU：resolveDeviceName() 解析各厂商市场名（remember 缓存一次）。
    val deviceModel = remember { resolveDeviceName() }

    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Box(modifier = modifier.fillMaxSize()) {
        ExpressiveScaffold(
            topBar = {
                LargeTopAppBar(
                    title = { Text(LocalPlatform.current.appDisplayName(), style = MaterialTheme.typography.headlineLarge) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    scrollBehavior = scrollBehavior
                )
            },
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Root / Joyose 状态卡（KernelSU v3.2.5 布局：顶部大卡 + 下方计数小卡）──
                // 检测未完成前显示中性“正在检测”卡，避免冷启动闪现红色错误卡误导用户。
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

                // ── 设备信息卡（图二 MaterialHome 写法：Card + ListItem + HorizontalDivider）──
                Card(modifier = Modifier.fillMaxWidth()) {
                    val infoItems = buildList<Pair<String, String>> {
                        add("设备型号" to deviceModel)
                        add("安卓版本" to "Android ${android.os.Build.VERSION.RELEASE}")
                        add("内核版本" to kernelVersion)
                        add("系统指纹" to android.os.Build.FINGERPRINT.ifBlank { "未知" })
                        if (hyperOS) {
                            add("Joyose 版本" to "v$joyoseVersion · com.xiaomi.joyose")
                        } else {
                            add("应用增强服务" to "v${cosaVersion} · com.oplus.cosa")
                        }
                    }
                    infoItems.forEachIndexed { index, (label, content) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            supportingContent = { Text(content) },
                        )
                        if (index != infoItems.lastIndex) HorizontalDivider()
                    }
                }

                // ── 作者卡片（图二 MaterialHome 卡片风格：Card + ListItem）──
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        leadingContent = {
                            Image(painter = painterResource(id = R.drawable.author_avatar),
                                contentDescription = "作者头像", modifier = Modifier.size(64.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        },
                        headlineContent = { Text("Smartisan_Apple") },
                        supportingContent = {
                            Text(
                                text = "酷安 @Smartisan_Apple",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.fillMaxWidth().clickable {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coolapk.com/u/1404550"))) }
                            catch (_: Exception) {}
                        },
                    )
                }
                // ── 开源仓库卡片（图二 MaterialHome 卡片风格：Card + ListItem）──
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("查看源代码") },
                        supportingContent = { Text("在 GitHub 上查看源代码") },
                        leadingContent = {
                            Image(painter = painterResource(id = R.drawable.author_avatar),
                                contentDescription = "GitHub 头像", modifier = Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        },
                        trailingContent = {
                            Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.fillMaxWidth().clickable {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Yunnijian/Oplus_RemoteConfig_Override")))
                            } catch (_: Exception) {}
                        },
                    )
                }
                // 底栏留白：由页面自己消费（对齐 KernelSU HomeMaterial.kt:140）
                Spacer(Modifier.height(bottomInnerPadding))
            }
        }
    }
}

// ── Root / Joyose 状态卡（KernelSU v3.2.5 布局）──
// 顶部大卡（Root / Joyose 状态）+ 下方计数小卡（应用配置 / 通用配置）。
// 大卡数据源：ColorOS = MainViewModel.SystemStatus；HyperOS = HyperOsViewModel.statState。
// 小卡数据源：应用配置 = listState.apps.size（HyperOS）/ configuredCount（ColorOS）；
//            第二卡 HyperOS = 通用配置（commonState.switches.size）；
//            ColorOS = 已安装应用配置（installedConfigCount）。
// 卡片点击交互对齐 KernelSU：大卡点击重新检测，小卡点击跳转对应页面。
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

    // 照搬 KernelSU v3.2.5 StatusCard 的容器色：激活 secondaryContainer / 异常 errorContainer
    val containerColor = when {
        checking -> MaterialTheme.colorScheme.surfaceVariant
        connected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val icon = when {
        checking -> Icons.Outlined.HourglassEmpty
        connected -> Icons.Outlined.CheckCircle
        else -> Icons.Outlined.Warning
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 大卡：照搬 KernelSU v3.2.5（TonalCard + onClick + Row(24dp) + 图标 + 状态文字）
        TonalCard(containerColor = containerColor, onClick = onRefreshClick) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                )
                Column(Modifier.padding(start = 20.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SmallCountCard(
                modifier = Modifier.weight(1f),
                label = "应用配置",
                count = appConfigCount,
                onClick = onAppConfigClick,
            )
            SmallCountCard(
                modifier = Modifier.weight(1f),
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
    TonalCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
