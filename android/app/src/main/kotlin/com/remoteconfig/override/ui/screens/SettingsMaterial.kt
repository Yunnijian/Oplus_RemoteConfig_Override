package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.settings.ColorMode
import com.remoteconfig.override.settings.SettingsRepositoryImpl
import com.remoteconfig.override.settings.UiMode

private const val ABOUT_VERSION = "Color云控修改 v1.2.1"

/**
 * 设置页 — Material 3 实现。
 *
 * 与 [SettingsContentMiuix] 行为一致、读同一 [com.remoteconfig.override.settings.SettingsRepositoryImpl]；
 * 结构对齐 KernelSU `SettingsMaterial.kt` 的分组（外观 / 液态玻璃 / 关于），
 * 组件用 M3 标准件：分组 `ElevatedCard` + `ListItem` / `Switch` / `SegmentedButton`，
 * 每行带与 Miuix 版一致的 startAction 前导图标（`Icons.Rounded.*`）。
 * 关于弹窗使用 Material3 [AlertDialog]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContentMaterial(bottomInnerPadding: Dp = 0.dp) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val navigator = LocalNavigator.current
    var showAbout by rememberSaveable { mutableStateOf(false) }
    // 数据源：KernelSU 风格 SettingsRepository（SharedPreferences 即时读写，主题即时响应）
    val repo = remember { SettingsRepositoryImpl() }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
        ) {
            // ── 外观组 ──
            item(key = "appearance") {
                ElevatedCard(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    SegmentedSettingRow(
                        title = "设计风格",
                        summary = "Miuix / Material 设计风格",
                        icon = Icons.Rounded.Dashboard,
                        options = listOf("Miuix", "Material"),
                        selectedIndex = if (UiMode.fromValue(repo.uiMode) == UiMode.Material) 1 else 0,
                        onSelectedIndexChange = { index ->
                            repo.uiMode = if (index == 1) UiMode.Material.value else UiMode.Miuix.value
                        },
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("主题取色", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("自定义强调色与调色板风格") },
                        leadingContent = {
                            Icon(
                                Icons.Rounded.Palette,
                                contentDescription = "主题取色",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable {
                            navigator.push(Route.ColorPalette)
                        },
                    )
                    SegmentedSettingRow(
                        title = "深色模式",
                        summary = "跟随系统 / 浅色 / 深色",
                        icon = Icons.Rounded.DarkMode,
                        options = listOf("跟随系统", "浅色", "深色"),
                        // Monet 态（3-5）/AMOLED(6) 映射回对应非 Monet 三态显示
                        selectedIndex = ColorMode.fromValue(repo.themeMode).toNonMonetMode(),
                        onSelectedIndexChange = { index ->
                            repo.themeMode = index
                        },
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("动态取色 Monet", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("跟随系统壁纸取色") },
                        leadingContent = {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = "动态取色 Monet",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = repo.miuixMonet,
                                onCheckedChange = { repo.miuixMonet = it },
                            )
                        },
                    )
                }
            }

            // ── 液态玻璃组 ──
            item(key = "glass") {
                ElevatedCard(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("液态玻璃底栏", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("关闭后使用普通底栏（更省电）") },
                        leadingContent = {
                            Icon(
                                Icons.Rounded.Layers,
                                contentDescription = "液态玻璃底栏",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = repo.enableFloatingBottomBar,
                                onCheckedChange = { repo.enableFloatingBottomBar = it },
                            )
                        },
                    )
                    // 底栏实时模糊仅在底栏开启时显示
                    if (repo.enableFloatingBottomBar) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("底栏实时模糊", style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { Text("关闭后保留玻璃质感但更省电") },
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.BlurOn,
                                    contentDescription = "底栏实时模糊",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = repo.enableFloatingBottomBarBlur,
                                    onCheckedChange = { repo.enableFloatingBottomBarBlur = it },
                                )
                            },
                        )
                    }
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("全局模糊", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("液态玻璃模糊总开关") },
                        leadingContent = {
                            Icon(
                                Icons.Rounded.BlurOn,
                                contentDescription = "全局模糊",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = repo.enableBlur,
                                onCheckedChange = { repo.enableBlur = it },
                            )
                        },
                    )
                }
            }

            // ── 关于组 ──
            item(key = "about") {
                ElevatedCard(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .fillMaxWidth(),
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("关于", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text(ABOUT_VERSION) },
                        leadingContent = {
                            Icon(
                                Icons.Rounded.ContactPage,
                                contentDescription = "关于",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable { showAbout = true },
                    )
                }
                Spacer(Modifier.height(bottomInnerPadding))
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于") },
            text = { Text(ABOUT_VERSION) },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("确定") }
            },
        )
    }
}

/**
 * 单行分段选择项（对齐 KernelSU SegmentedDropdownItem 的功能，用 M3 SegmentedButton 实现）：
 * 前导图标 + 标题/摘要 + 底部单行分段按钮。用于「设计风格」与「深色模式」。
 */
@Composable
private fun SegmentedSettingRow(
    title: String,
    summary: String,
    icon: ImageVector,
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedIndex == index,
                    onClick = { onSelectedIndexChange(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(label)
                }
            }
        }
    }
}
