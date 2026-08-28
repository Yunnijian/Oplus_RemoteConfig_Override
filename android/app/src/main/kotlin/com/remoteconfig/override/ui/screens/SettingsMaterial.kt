package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
 * 结构对齐简报 Step 3：外观卡（Switch + 三选项 SegmentedButton + Switch）+
 * 液态玻璃卡（两个 Switch）+ 关于项（ListItem + ChevronRight）。
 * 关于弹窗使用 Material3 [AlertDialog]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContentMaterial() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val navigator = LocalNavigator.current
    var showAbout by remember { mutableStateOf(false) }
    // R1：数据源换为 KernelSU 风格 SettingsRepository（设置页 UI 不重写，R3 再做）
    val repo = remember { SettingsRepositoryImpl() }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("设置") },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 外观 ──
            item(key = "appearance") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("MIUI 风格 (Miuix)", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("切换 Miuix / Material 设计风格") },
                        trailingContent = {
                            Switch(
                                checked = repo.uiMode == UiMode.Miuix.value,
                                onCheckedChange = { miuix ->
                                    repo.uiMode = if (miuix) UiMode.Miuix.value else UiMode.Material.value
                                },
                            )
                        },
                    )
                    ColorModeSegmentedRow(repo)
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("动态取色 (Monet)", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("跟随系统壁纸取色") },
                        trailingContent = {
                            Switch(
                                checked = repo.miuixMonet,
                                onCheckedChange = { repo.miuixMonet = it },
                            )
                        },
                    )
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("主题取色", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("自定义强调色与调色板风格") },
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
                }
            }

            // ── 液态玻璃 ──
            item(key = "glass") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("液态玻璃底栏", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("关闭后使用普通底栏（更省电）") },
                        trailingContent = {
                            Switch(
                                checked = repo.enableBlur,
                                onCheckedChange = { repo.enableBlur = it },
                            )
                        },
                    )
                    if (repo.enableBlur) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("底栏实时模糊", style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { Text("关闭后保留玻璃质感但更省电") },
                            trailingContent = {
                                Switch(
                                    checked = repo.enableFloatingBottomBarBlur,
                                    onCheckedChange = { repo.enableFloatingBottomBarBlur = it },
                                )
                            },
                        )
                    }
                }
            }

            // ── 关于 ──
            item(key = "about") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("关于", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text(ABOUT_VERSION) },
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

/** 深色模式三选项 SegmentedButton（跟随系统 / 浅色 / 深色）。 */
@Composable
private fun ColorModeSegmentedRow(repo: SettingsRepositoryImpl) {
    val options = listOf(
        "跟随系统" to ColorMode.SYSTEM,
        "浅色" to ColorMode.LIGHT,
        "深色" to ColorMode.DARK,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            "深色模式",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            options.forEachIndexed { index, (label, mode) ->
                SegmentedButton(
                    selected = ColorMode.fromValue(repo.themeMode).toNonMonetMode() == mode.value,
                    onClick = { repo.themeMode = mode.value },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(label)
                }
            }
        }
    }
}
