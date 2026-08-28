package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode

/**
 * 设置页分发器：按当前 UI 风格 [LocalUiMode] 选择 Miuix / Material 实现。
 * 设置页无数据加载，因此无 isCurrentPage 黏性参数（与 Task 8/9 的数据页不同）。
 *
 * [bottomInnerPadding]：底栏占位高度，透传给两侧实现（它们在 LazyColumn 最后一个 item
 * 末尾用 `Spacer` 消费，对齐 KernelSU SettingsMiuix.kt:74,475 / SettingsMaterial.kt:72,378）。
 * Pager 不加 bottom padding，内容需铺到底栏下方供悬浮玻璃采样折射。
 */
@Composable
fun SettingsContent(bottomInnerPadding: Dp = 0.dp) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingsContentMiuix(bottomInnerPadding)
        UiMode.Material -> SettingsContentMaterial(bottomInnerPadding)
    }
}
