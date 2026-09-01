package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlinx.serialization.json.jsonObject

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
    val edit by viewModel.scopedEditorState.collectAsStateWithLifecycle()

    LaunchedEffect(packageName) { viewModel.loadDetail(packageName) }

    // 功能页参数编辑（P2.4）：与作用域编辑器共享同一份 ScopedEditorState ——
    // 这里只把"当前片段文档 + 写状态 + 修改回调"打包下传，保存走 saveScopedEditor
    // 的 CLI 补丁链路（joyose-scoped-write：双库镜像 + 回读校验）。
    // 片段文档解析按 edited 文本缓存：避免每次重组重复解析整份片段文档。
    val document = remember(edit.edited) {
        edit.edited?.let { text ->
            runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        }
    }
    val editBundle = HyperOsDetailEdit(
        document = document,
        writing = edit.writing,
        dirty = edit.base != null && edit.edited != edit.base,
        error = edit.error,
        parseScenes = viewModel::parseSceneInfo,
        onSave = viewModel::saveScopedEditor,
        onRevert = viewModel::revertScopedEditor,
        onScalar = viewModel::updateParamScalar,
        onSceneCmd = viewModel::updateSceneCmd,
        onSceneFlag = viewModel::updateSceneFlag,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> HyperOsAppDetailMiuix(
            view = state.view,
            loading = state.loading,
            error = state.error,
            edit = editBundle,
            onRetry = { viewModel.loadDetail(packageName) },
            onBack = navigator::pop,
        )
        UiMode.Material -> HyperOsAppDetailMaterial(
            view = state.view,
            loading = state.loading,
            error = state.error,
            editError = edit.error,
            onRetry = { viewModel.loadDetail(packageName) },
            onBack = navigator::pop,
        )
    }
}

/**
 * 功能页参数编辑包：片段文档（指针→片段）+ 写状态 + 修改回调。
 *
 * P2.4 范围：参数编辑 UI 仅 Miuix 皮肤实装；Material 皮肤只读展示，
 * 仅消费 [error]（错误条，与 Miuix 皮肤失败表现一致）。[onRevert] 供
 * 「放弃修改」确认后重置脏草稿（功能页与作用域编辑器两处入口共用）。
 */
data class HyperOsDetailEdit(
    /** 编辑中的片段文档：键 = JSON Pointer，值 = 片段 JSON；null = 未就绪（只读）。 */
    val document: JsonObject?,
    val writing: Boolean,
    /** edited 与 base 不一致 = 有未保存修改。 */
    val dirty: Boolean,
    val error: String?,
    val parseScenes: (JsonObject) -> List<HyperOsViewModel.SceneInfo>,
    val onSave: () -> Unit,
    val onRevert: () -> Unit,
    val onScalar: (pointer: String, name: String, value: JsonPrimitive) -> Unit,
    val onSceneCmd: (pointer: String, sceneIndex: Int, containerKey: String, boosterIndex: Int, cmd: String) -> Unit,
    val onSceneFlag: (pointer: String, sceneIndex: Int, flag: String, value: Boolean) -> Unit,
)

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
