package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.settings.ColorMode
import com.remoteconfig.override.settings.SettingsRepositoryImpl
import com.remoteconfig.override.settings.UiMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowDialog

/** 关于项展示的版本文本（与简报逐字一致）。 */
private const val ABOUT_VERSION = "Color云控修改 v1.2.1"

/**
 * 设置页 — Miuix 实现。
 *
 * 组件用法对齐 KernelSU SettingsMiuix.kt：
 * `Scaffold + TopAppBar(MiuixScrollBehavior) + LazyColumn(nestedScroll) + Card +
 *  SwitchPreference / OverlayDropdownPreference / ArrowPreference`。
 *
 * 注意：简报示例中的 `SuperDropdown` 在 miuix 0.9.3 中实际名为
 * `OverlayDropdownPreference`（KernelSU 实际用法为准），此处已对齐。
 * 关于弹窗使用 miuix-ui 0.9.3 的 `WindowDialog`（`SuperDialog` 在 0.9.3 不存在）。
 */
@Composable
fun SettingsContentMiuix() {
    val scrollBehavior = MiuixScrollBehavior()
    val navigator = LocalNavigator.current
    var showAbout by remember { mutableStateOf(false) }
    // R1：数据源换为 KernelSU 风格 SettingsRepository（设置页 UI 不重写，R3 再做）
    val repo = remember { SettingsRepositoryImpl() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
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
        ) {
            // ── 外观 ──
            item(key = "appearance") {
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    SwitchPreference(
                        title = "MIUI 风格 (Miuix)",
                        summary = "切换 Miuix / Material 设计风格",
                        checked = repo.uiMode == UiMode.Miuix.value,
                        onCheckedChange = { miuix ->
                            repo.uiMode = if (miuix) UiMode.Miuix.value else UiMode.Material.value
                        },
                    )
                    OverlayDropdownPreference(
                        title = "深色模式",
                        summary = "跟随系统 / 浅色 / 深色",
                        items = listOf("跟随系统", "浅色", "深色"),
                        // Monet 态（3-5）/AMOLED(6) 映射回对应非 Monet 三态显示
                        selectedIndex = ColorMode.fromValue(repo.themeMode).toNonMonetMode(),
                        onSelectedIndexChange = { idx ->
                            repo.themeMode = idx
                        },
                    )
                    SwitchPreference(
                        title = "动态取色 (Monet)",
                        summary = "跟随系统壁纸取色",
                        checked = repo.miuixMonet,
                        onCheckedChange = { repo.miuixMonet = it },
                    )
                    ArrowPreference(
                        title = "主题取色",
                        summary = "自定义强调色与调色板风格",
                        onClick = { navigator.push(Route.ColorPalette) },
                    )
                }
            }

            // ── 液态玻璃 ──
            item(key = "glass") {
                Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    SwitchPreference(
                        title = "液态玻璃底栏",
                        summary = "关闭后使用普通底栏（更省电）",
                        checked = repo.enableBlur,
                        onCheckedChange = { repo.enableBlur = it },
                    )
                    if (repo.enableBlur) {
                        SwitchPreference(
                            title = "底栏实时模糊",
                            summary = "关闭后保留玻璃质感但更省电",
                            checked = repo.enableFloatingBottomBarBlur,
                            onCheckedChange = { repo.enableFloatingBottomBarBlur = it },
                        )
                    }
                }
            }

            // ── 关于 ──
            item(key = "about") {
                Card(modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth()) {
                    ArrowPreference(
                        title = "关于",
                        summary = ABOUT_VERSION,
                        onClick = { showAbout = true },
                    )
                }
            }
        }
    }

    // 关于：弹出版本信息（Miuix 用 WindowDialog，对齐 KernelSU DialogMiuix.kt 用法）
    WindowDialog(
        show = showAbout,
        title = "关于",
        onDismissRequest = { showAbout = false },
        content = { Text(ABOUT_VERSION) },
    )
}
