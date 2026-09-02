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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.remoteconfig.override.R
import com.remoteconfig.override.data.JoyoseManager
import com.remoteconfig.override.platform.Platform
import com.remoteconfig.override.platform.appDisplayName
import com.remoteconfig.override.ui.component.material.ExpressiveScaffold
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
                // ── Root / Joyose 状态卡（HyperOS 分支：Joyose 状态卡）──
                // 检测未完成前显示中性“正在检测”卡，避免冷启动闪现红色错误卡误导用户。
                if (hyperOS) {
                    JoyoseStatusCardMaterial(joyoseStat)
                } else {
                // 检测未完成前显示中性“正在检测”卡，避免冷启动闪现红色错误卡误导用户。
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            !systemStatus.checked -> MaterialTheme.colorScheme.surfaceVariant
                            systemStatus.isRooted && systemStatus.dbAvailable -> MaterialTheme.colorScheme.primaryContainer
                            systemStatus.isRooted -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                !systemStatus.checked -> Icons.Filled.HourglassEmpty
                                systemStatus.isRooted && systemStatus.dbAvailable -> Icons.Filled.Verified
                                systemStatus.isRooted -> Icons.Filled.Warning
                                else -> Icons.Filled.GppBad
                            },
                            contentDescription = null,
                            tint = when {
                                !systemStatus.checked -> MaterialTheme.colorScheme.onSurfaceVariant
                                systemStatus.isRooted && systemStatus.dbAvailable -> MaterialTheme.colorScheme.onPrimaryContainer
                                systemStatus.isRooted -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    !systemStatus.checked -> "正在检测..."
                                    systemStatus.isRooted && systemStatus.dbAvailable -> "Root 权限正常"
                                    systemStatus.isRooted -> "数据库连接失败"
                                    else -> "未授予 Root 权限"
                                },
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                color = when {
                                    !systemStatus.checked -> MaterialTheme.colorScheme.onSurfaceVariant
                                    systemStatus.isRooted && systemStatus.dbAvailable -> MaterialTheme.colorScheme.onPrimaryContainer
                                    systemStatus.isRooted -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                            Text(
                                text = when {
                                    !systemStatus.checked -> "正在检测 Root 权限与数据库状态"
                                    systemStatus.isRooted && systemStatus.dbAvailable -> "数据库已连接，可读写配置"
                                    systemStatus.isRooted -> "已获取 Root 权限，但数据库文件不可访问"
                                    else -> "请授予 Root 权限后重启应用"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    !systemStatus.checked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    systemStatus.isRooted && systemStatus.dbAvailable -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    systemStatus.isRooted -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    else -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                }
                            )
                        }
                    }
                }
                } // if (hyperOS) else 旧 Root 状态卡

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

// ── Joyose 状态卡（HyperOS）──
// 数据源 = HyperOsViewModel.statState（joyose-stat 轻量查询），状态视觉与 Root 状态卡一致。
@Composable
private fun JoyoseStatusCardMaterial(stat: JoyoseManager.Stat?) {
    val connected = stat != null && (stat.smartp.exists || stat.teg.exists)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                stat == null -> MaterialTheme.colorScheme.surfaceVariant
                connected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when {
                    stat == null -> Icons.Filled.HourglassEmpty
                    connected -> Icons.Filled.Verified
                    else -> Icons.Filled.GppBad
                },
                contentDescription = null,
                tint = when {
                    stat == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    connected -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onErrorContainer
                },
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        stat == null -> "正在检测..."
                        connected -> "Joyose 云控正常"
                        else -> "Joyose 云控不可用"
                    },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = when {
                        stat == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        connected -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                Text(
                    text = when {
                        stat == null -> "正在读取 Joyose 数据库状态"
                        connected -> "SmartP / teg_config 已连接 · " +
                            if (stat.sp.frozen) "云控已冻结" else "云控未冻结"
                        else -> "未找到 Joyose 数据库，请确认设备为 HyperOS"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        stat == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        connected -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    }
                )
            }
        }
    }
}
