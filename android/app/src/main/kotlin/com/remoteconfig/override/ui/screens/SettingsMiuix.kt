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
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Dashboard
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
import com.remoteconfig.override.settings.SettingsRepositoryImpl
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalEnableBlur
import com.remoteconfig.override.ui.util.BlurredBar
import com.remoteconfig.override.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

/** 关于项展示的版本文本（与简报逐字一致）。 */
private const val ABOUT_VERSION = "Color云控修改 v1.2.1"

/**
 * 设置页 — Miuix 实现（精简为对齐 KernelSU 设置页外观组）。
 *
 * 布局/组件/文案风格完整对齐 KernelSU `SettingsMiuix.kt`：
 * `Scaffold` 顶栏用 [BlurredBar]（backdrop 由 [rememberBlurBackdrop] 按
 * `LocalEnableBlur` 惰性创建）+ `MiuixScrollBehavior`；内容 `LazyColumn` 用
 * `scrollEndHaptic() + overScrollVertical() + nestedScroll + overscrollEffect = null`，
 * 分组 `Card(padding(top = 12.dp).fillMaxWidth())` 组内直接排
 * `OverlayDropdownPreference / ArrowPreference`，每个 Preference 均带
 * `startAction` 图标（`Icons.Rounded.*`，tint = colorScheme.onBackground）。
 *
 * 功能项：界面风格（Miuix/Material）+ 主题设置（→ Route.ColorPalette）+ 关于。
 * 其余主题字段（深色模式/动态取色/液态玻璃等）已移入主题设置页。
 *
 * [onOpenTheme]：主题设置项点击回调。null（窄屏）= push Route.ColorPalette（现状）；
 * 非 null（宽屏双窗）= 由 SettingsContent 注入选中右侧 pane，不 push 路由。
 */
@Composable
fun SettingsContentMiuix(
    bottomInnerPadding: Dp = 0.dp,
    onOpenTheme: (() -> Unit)? = null,
) {
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
                            title = "界面风格",
                            summary = "选择应用的界面风格",
                            items = listOf("Miuix", "Material"),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Dashboard,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = "界面风格",
                                    tint = colorScheme.onBackground,
                                )
                            },
                            selectedIndex = if (UiMode.fromValue(repo.uiMode) == UiMode.Material) 1 else 0,
                            onSelectedIndexChange = { index ->
                                repo.uiMode = if (index == 1) UiMode.Material.value else UiMode.Miuix.value
                            },
                        )
                        ArrowPreference(
                            title = "主题设置",
                            summary = "自定义更多主题选项",
                            startAction = {
                                Icon(
                                    Icons.Rounded.Palette,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = "主题设置",
                                    tint = colorScheme.onBackground,
                                )
                            },
                            onClick = {
                                if (onOpenTheme != null) onOpenTheme()
                                else navigator.push(Route.ColorPalette)
                            },
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
