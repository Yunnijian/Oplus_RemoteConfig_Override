package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.settings.ColorMode
import com.remoteconfig.override.settings.SettingsRepositoryImpl
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalEnableBlur
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import com.remoteconfig.override.ui.util.BlurredBar
import com.remoteconfig.override.ui.util.rememberBlurBackdrop

/** 关于项展示的版本文本（与简报逐字一致）。 */
private const val ABOUT_VERSION = "Color云控修改 v1.2.1"

/**
 * 设置页 — Miuix 实现。
 *
 * 布局/组件/文案风格完整对齐 KernelSU `SettingsMiuix.kt`：
 * `Scaffold` 顶栏用 [BlurredBar]（backdrop 由 [rememberBlurBackdrop] 按
 * `LocalEnableBlur` 惰性创建）+ `MiuixScrollBehavior`；内容 `LazyColumn` 用
 * `scrollEndHaptic() + overScrollVertical() + nestedScroll + overscrollEffect = null`，
 * 分组 `Card(padding(top = 12.dp).fillMaxWidth())` 组内直接排
 * `SwitchPreference / OverlayDropdownPreference / ArrowPreference`，每个
 * Preference 均带 `startAction` 图标（`Icons.Rounded.*`，tint = colorScheme.onBackground）。
 *
 * 功能项为本应用自己的 9 个设置字段（不移植 KernelSU 的 su/selinux 等特有功能）：
 * 外观组（设计风格 / 主题取色 / 深色模式 / 动态取色 Monet）+ 液态玻璃组
 * （液态玻璃底栏 / 底栏实时模糊 / 全局模糊）+ 关于组。
 */
@Composable
fun SettingsContentMiuix(bottomInnerPadding: Dp = 0.dp) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val navigator = LocalNavigator.current
    var showAbout by rememberSaveable { mutableStateOf(false) }
    // 数据源：KernelSU 风格 SettingsRepository（SharedPreferences 即时读写，主题即时响应）
    val repo = remember { SettingsRepositoryImpl() }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = "设置",
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                // ── 外观组 ──
                item {
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        OverlayDropdownPreference(
                            title = "设计风格",
                            summary = "Miuix / Material 设计风格",
                            items = listOf("Miuix", "Material"),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Dashboard,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = "设计风格",
                                    tint = colorScheme.onBackground,
                                )
                            },
                            selectedIndex = if (UiMode.fromValue(repo.uiMode) == UiMode.Material) 1 else 0,
                            onSelectedIndexChange = { index ->
                                repo.uiMode = if (index == 1) UiMode.Material.value else UiMode.Miuix.value
                            },
                        )
                        ArrowPreference(
                            title = "主题取色",
                            summary = "自定义强调色与调色板风格",
                            startAction = {
                                Icon(
                                    Icons.Rounded.Palette,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = "主题取色",
                                    tint = colorScheme.onBackground,
                                )
                            },
                            onClick = { navigator.push(Route.ColorPalette) },
                        )
                        OverlayDropdownPreference(
                            title = "深色模式",
                            summary = "跟随系统 / 浅色 / 深色",
                            items = listOf("跟随系统", "浅色", "深色"),
                            startAction = {
                                Icon(
                                    Icons.Rounded.DarkMode,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = "深色模式",
                                    tint = colorScheme.onBackground,
                                )
                            },
                            // Monet 态（3-5）/AMOLED(6) 映射回对应非 Monet 三态显示
                            selectedIndex = ColorMode.fromValue(repo.themeMode).toNonMonetMode(),
                            onSelectedIndexChange = { index ->
                                repo.themeMode = index
                            },
                        )
                        SwitchPreference(
                            title = "动态取色 Monet",
                            summary = "跟随系统壁纸取色",
                            startAction = {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = "动态取色 Monet",
                                    tint = colorScheme.onBackground,
                                )
                            },
                            checked = repo.miuixMonet,
                            onCheckedChange = { repo.miuixMonet = it },
                        )
                    }
                }

                // ── 液态玻璃组 ──
                item {
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = "液态玻璃底栏",
                            summary = "关闭后使用普通底栏（更省电）",
                            startAction = {
                                Icon(
                                    Icons.Rounded.Layers,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = "液态玻璃底栏",
                                    tint = colorScheme.onBackground,
                                )
                            },
                            checked = repo.enableFloatingBottomBar,
                            onCheckedChange = { repo.enableFloatingBottomBar = it },
                        )
                        // 底栏实时模糊仅在底栏开启时显示
                        if (repo.enableFloatingBottomBar) {
                            SwitchPreference(
                                title = "底栏实时模糊",
                                summary = "关闭后保留玻璃质感但更省电",
                                startAction = {
                                    Icon(
                                        Icons.Rounded.BlurOn,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = "底栏实时模糊",
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                checked = repo.enableFloatingBottomBarBlur,
                                onCheckedChange = { repo.enableFloatingBottomBarBlur = it },
                            )
                        }
                        SwitchPreference(
                            title = "全局模糊",
                            summary = "液态玻璃模糊总开关",
                            startAction = {
                                Icon(
                                    Icons.Rounded.BlurOn,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = "全局模糊",
                                    tint = colorScheme.onBackground,
                                )
                            },
                            checked = repo.enableBlur,
                            onCheckedChange = { repo.enableBlur = it },
                        )
                    }
                }

                // ── 关于组 ──
                item {
                    Card(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        ArrowPreference(
                            title = "关于",
                            summary = ABOUT_VERSION,
                            startAction = {
                                Icon(
                                    Icons.Rounded.ContactPage,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = "关于",
                                    tint = colorScheme.onBackground,
                                )
                            },
                            onClick = { showAbout = true },
                        )
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
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
