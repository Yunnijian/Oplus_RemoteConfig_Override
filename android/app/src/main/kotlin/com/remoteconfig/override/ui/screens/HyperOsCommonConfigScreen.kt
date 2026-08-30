package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.viewmodel.HyperOsViewModel

/** 云控冻结语义小字（云控状态卡内展示，双皮肤共用同一段文案）。 */
internal const val FREEZE_HINT =
    "冻结后 Joyose 不再被云端覆盖（约每 13 分钟一次的拉取被阻断）；副作用：所有 MIUI teg 云控模块暂停更新"

/**
 * HyperOS 通用配置页 — 分发器（P2.2，对应 [com.remoteconfig.override.navigation.Route.HyperOsCommonConfig]）。
 *
 * 数据源为 [HyperOsViewModel] 的 commonState（StateFlow 驱动）：进入页面时
 * [LaunchedEffect] 触发一次 refreshCommon()（其内部有 loading/writing 守卫，重复触发安全）。
 * 按 [LocalUiMode] 分发 Miuix / Material 实现，两者接收同一 [HyperOsViewModel.CommonState]
 * 与动作回调（toggleSwitch / freeze / unfreeze / refreshCommon）。
 *
 * 写路径（开关切换 / 冻结解冻）的乐观更新与失败回滚均在 ViewModel 内完成，
 * UI 只渲染状态：writing 时开关与按钮禁用（避免连点，ViewModel 亦有并发守护兜底）。
 */
@Composable
fun HyperOsCommonConfigScreen(bottomInnerPadding: Dp = 0.dp) {
    val viewModel: HyperOsViewModel = viewModel()
    val state by viewModel.commonState.collectAsStateWithLifecycle()
    // 进入页面触发一次首刷
    LaunchedEffect(Unit) { viewModel.refreshCommon() }
    when (LocalUiMode.current) {
        UiMode.Miuix -> HyperOsCommonConfigMiuix(
            state = state,
            bottomInnerPadding = bottomInnerPadding,
            onToggle = viewModel::toggleSwitch,
            onFreeze = viewModel::freeze,
            onUnfreeze = viewModel::unfreeze,
            onRetry = viewModel::refreshCommon,
        )
        UiMode.Material -> HyperOsCommonConfigMaterial(
            state = state,
            bottomInnerPadding = bottomInnerPadding,
            onToggle = viewModel::toggleSwitch,
            onFreeze = viewModel::freeze,
            onUnfreeze = viewModel::unfreeze,
            onRetry = viewModel::refreshCommon,
        )
    }
}
