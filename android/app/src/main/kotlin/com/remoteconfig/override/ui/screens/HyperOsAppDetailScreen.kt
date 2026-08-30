package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.remoteconfig.override.data.JoyoseManager
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.viewmodel.HyperOsViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * HyperOS 应用功能页 — 分发器（P2.1）。
 *
 * 签名固定为 `(packageName: String)`：该页由 NavDisplay 的 Route.HyperOsAppDetail
 * entry 挂载，按 [LocalUiMode] 分发 Miuix / Material 双皮肤实现。
 *
 * 数据源为 [HyperOsViewModel.detailState]（DetailState = loading/view/error）：
 * 进入页面及包名变化时经 [HyperOsViewModel.loadDetail] 重新加载，重试同源。
 * 返回导航对齐全屏路由惯例（取色屏/编辑器）：TopBar 返回键 → navigator.pop()。
 */
@Composable
fun HyperOsAppDetailScreen(packageName: String) {
    // LocalNavigator.current 是 @Composable getter，提前读出（避免非组合 lambda 内访问）。
    val navigator = LocalNavigator.current
    val viewModel: HyperOsViewModel = viewModel()
    val state by viewModel.detailState.collectAsStateWithLifecycle()

    LaunchedEffect(packageName) { viewModel.loadDetail(packageName) }

    when (LocalUiMode.current) {
        UiMode.Miuix -> HyperOsAppDetailMiuix(
            view = state.view,
            loading = state.loading,
            error = state.error,
            onRetry = { viewModel.loadDetail(packageName) },
            onBack = navigator::pop,
        )
        UiMode.Material -> HyperOsAppDetailMaterial(
            view = state.view,
            loading = state.loading,
            error = state.error,
            onRetry = { viewModel.loadDetail(packageName) },
            onBack = navigator::pop,
        )
    }
}

// ── 双皮肤共享的展示辅助（JsonElement 渲染 / 徽标文案）────────────────

/** JsonPrimitive 展示文本：字符串/数字/布尔统一取 content（JsonNull 即 "null"）。 */
internal fun jsonScalarText(element: JsonElement): String? =
    (element as? JsonPrimitive)?.content

/**
 * 对象/数组折叠态摘要：对象显示键数、数组显示项数；原始量返回 null（不可折叠）。
 */
internal fun jsonCollapseSummary(element: JsonElement): String? = when (element) {
    is JsonObject -> "对象 · ${element.size} 个键"
    is JsonArray -> "数组 · ${element.size} 项"
    else -> null
}

/** 对象/数组展开态文本（美化 JSON，mono 呈现）。 */
private val PrettyJson = Json { prettyPrint = true }

internal fun jsonPrettyText(element: JsonElement): String =
    PrettyJson.encodeToString(JsonElement.serializer(), element)

/** source 徽标文案：direct→直接配置 / group_alias→组别名: {key} / fallback→兜底(OTHER)。 */
internal fun featureSourceBadge(feature: JoyoseManager.FeatureHit): String = when (feature.source) {
    "direct" -> "直接配置"
    "group_alias" -> "组别名: ${feature.key}"
    "fallback" -> "兜底(OTHER)"
    else -> feature.source
}
