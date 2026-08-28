package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode

/**
 * 设置页分发器：按当前 UI 风格 [LocalUiMode] 选择 Miuix / Material 实现。
 * 设置页无数据加载，因此无 isCurrentPage 黏性参数（与 Task 8/9 的数据页不同）。
 */
@Composable
fun SettingsContent() {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingsContentMiuix()
        UiMode.Material -> SettingsContentMaterial()
    }
}
