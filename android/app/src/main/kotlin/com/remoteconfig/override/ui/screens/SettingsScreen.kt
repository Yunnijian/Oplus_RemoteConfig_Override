package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.ui.theme.isExpandedWidth

/**
 * 设置页分发器：按当前 UI 风格 [LocalUiMode] 选择 Miuix / Material 实现。
 * 设置页无数据加载，因此无 isCurrentPage 黏性参数（与 Task 8/9 的数据页不同）。
 *
 * 双窗（Task：设置页宽屏双窗）：宽屏 [isExpandedWidth]（≥840dp）渲染 list-detail 双窗格
 * （左设置列表 + 右 [ThemePane]），点「主题设置」不 push 路由，改选中右侧 pane；
 * 窄屏保持整页 push 路由（现状）。[SettingsContent] 是 Route.Main 的 tab 2（在 Pager 内），
 * 双窗直接在内部实现（左列表 + 右 pane 都是 Pager page 2 的内容），不移出 Pager。
 *
 * [bottomInnerPadding]：底栏占位高度，透传给两侧实现（它们在 LazyColumn 最后一个 item
 * 末尾用 `Spacer` 消费，对齐 KernelSU SettingsMiuix.kt:74,475 / SettingsMaterial.kt:72,378）。
 * Pager 不加 bottom padding，内容需铺到底栏下方供悬浮玻璃采样折射。
 */
@Composable
fun SettingsContent(bottomInnerPadding: Dp = 0.dp) {
    // 双窗选中 pane（宽屏 list-detail）："theme" = 主题设置；null = 无；窄屏不使用（恒为 null）
    var selectedPane by rememberSaveable { mutableStateOf<String?>(null) }

    if (isExpandedWidth()) {
        // 宽屏：左设置列表 + 右主题设置 pane 双窗格
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth(0.42f)) {
                SettingsListImpl(
                    bottomInnerPadding,
                    onOpenTheme = { selectedPane = "theme" },
                )
            }
            VerticalDivider()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (selectedPane) {
                    "theme" -> ThemePane()
                    else -> EmptyPaneHint(text = "选择左侧设置项查看详情")
                }
            }
        }
    } else {
        SettingsListImpl(bottomInnerPadding)
    }
}

/** 按当前 UI 风格选择设置列表实现；[onOpenTheme] 在宽屏双窗下接管「主题设置」点击。 */
@Composable
private fun SettingsListImpl(bottomInnerPadding: Dp, onOpenTheme: (() -> Unit)? = null) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingsContentMiuix(bottomInnerPadding, onOpenTheme)
        UiMode.Material -> SettingsContentMaterial(bottomInnerPadding, onOpenTheme)
    }
}

/**
 * 宽屏双窗右侧 — 主题设置 pane。
 *
 * 复用取色屏状态/动作（[rememberColorPaletteStateAndActions]）与内容
 * （[ColorPaletteContentMiuix] / [ColorPaletteContentMaterial]，showTopBar=false 无顶栏返回按钮），
 * pane 不占路由，返回由左侧设置列表选择控制。
 */
@Composable
private fun ThemePane() {
    val (uiState, actions) = rememberColorPaletteStateAndActions(onBack = {})
    when (LocalUiMode.current) {
        UiMode.Miuix -> ColorPaletteContentMiuix(uiState, actions, showTopBar = false)
        UiMode.Material -> ColorPaletteContentMaterial(uiState, actions, showTopBar = false)
    }
}
