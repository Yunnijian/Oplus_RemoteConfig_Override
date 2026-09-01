package com.remoteconfig.override.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.remoteconfig.override.data.JoyoseManager
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.viewmodel.HyperOsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * HyperOS 应用功能页 v2 — 分发器 + 功能入口模型。
 *
 * 详情页 = 顶部应用卡 + **功能入口列表**（按片段实际存在的键 + 设备能力动态显隐）；
 * 参数只在各功能子屏内出现（温控曲线 / 性能调度 / FISR / migt / GPU 调参 /
 * 动态分辨率 / 场景命令 / 通用开关），子屏与作用域编辑器共享同一份草稿
 * （scopedEditorState），保存统一走 joyose-scoped-write。
 */
@Composable
fun HyperOsAppDetailScreen(packageName: String) {
    // LocalNavigator.current 是 @Composable getter，提前读出（避免非组合 lambda 内访问）。
    val navigator = LocalNavigator.current
    val viewModel: HyperOsViewModel = viewModel()
    val state by viewModel.detailState.collectAsStateWithLifecycle()
    val scoped by viewModel.scopedEditorState.collectAsStateWithLifecycle()
    val caps by viewModel.deviceCapsState.collectAsStateWithLifecycle()

    LaunchedEffect(packageName) {
        viewModel.loadDetail(packageName)
        viewModel.refreshStat()
    }
    // 应用卡信息（IO 线程取 label；stat 提供云控版本与冻结状态）
    val stat by viewModel.statState.collectAsStateWithLifecycle()
    var headerInfo by remember { mutableStateOf<DetailHeaderInfo?>(null) }
    LaunchedEffect(state.view, stat) {
        if (state.view != null) {
            headerInfo = withContext(Dispatchers.IO) {
                val frozen = stat?.sp?.frozen == true
                // 冻结哨兵（Long.MAX）/未拉取(0) 不作为版本号展示；冻结态由冻结徽标表达
                val version = stat?.sp?.prefLocalMaxVersion
                    ?.takeIf { !frozen && it != "0" && it != "9223372036854775807" }
                DetailHeaderInfo(
                    icon = viewModel.getCachedIcon(packageName),
                    label = viewModel.appLabel(packageName),
                    cloudVersion = version.orEmpty(),
                    frozen = frozen,
                )
            }
        }
    }

    // 片段文档解析按 edited 文本缓存：避免每次重组重复解析整份片段文档。
    val document = remember(scoped.edited) {
        scoped.edited?.let { text ->
            runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        }
    }
    val dirty = scopedIsDirty(scoped.base, scoped.edited)

    when (LocalUiMode.current) {
        UiMode.Miuix -> HyperOsAppDetailMiuix(
            view = state.view,
            header = headerInfo,
            loading = state.loading,
            error = state.error,
            editError = scoped.error,
            document = document,
            dirty = dirty,
            saving = scoped.writing,
            caps = caps,
            onRetry = { viewModel.loadDetail(packageName) },
            onSave = viewModel::saveScopedEditor,
            onRevert = viewModel::revertScopedEditor,
            onBack = navigator::pop,
            onOpenEditor = { navigator.push(Route.HyperOsScopedEditor(packageName)) },
            onOpenFeature = { entry -> navigator.push(entry.route(packageName)) },
        )
        UiMode.Material -> HyperOsAppDetailMaterial(
            view = state.view,
            header = headerInfo,
            loading = state.loading,
            error = state.error,
            editError = scoped.error,
            document = document,
            dirty = dirty,
            saving = scoped.writing,
            caps = caps,
            onRetry = { viewModel.loadDetail(packageName) },
            onSave = viewModel::saveScopedEditor,
            onRevert = viewModel::revertScopedEditor,
            onBack = navigator::pop,
            onOpenEditor = { navigator.push(Route.HyperOsScopedEditor(packageName)) },
            onOpenFeature = { entry -> navigator.push(entry.route(packageName)) },
        )
    }
}

// ── 功能入口模型（方案第三章信息架构）──────────────────────────────────────

/** 详情页应用卡信息（图标 + 应用名 + 云控版本 + 冻结状态）。 */
data class DetailHeaderInfo(
    val icon: android.graphics.Bitmap?,
    val label: String,
    val cloudVersion: String,
    val frozen: Boolean,
)

/** 功能入口标识（顺序 = 详情页展示顺序，v2.2：6 入口，无通用开关/独立场景命令）。 */
enum class HyperOsFeatureEntry(val title: String, val summary: String) {
    THERMAL_FPS("温控与帧率", "温控限帧曲线 / PID / 低电量限帧"),
    PERF_SCHEDULE("性能调度", "本应用参数 + 场景管理（scene_ovrride）"),
    FISR("插帧超分", "fisr NT 策略表 + Novatek 独显配置"),
    DYN_RES("动态分辨率", "MIGL 动态分辨率曲线与映射"),
    GPU_TUNER("GPU 自研调参", "self_gpu_tuner profile 键值"),
    MIGT("migt 帧感知加速", "名单进出与帧感知参数包"),
}

/** 入口 → 路由（子屏均为独立页面，携带包名）。 */
internal fun HyperOsFeatureEntry.route(pkg: String): Route = when (this) {
    HyperOsFeatureEntry.THERMAL_FPS -> Route.HyperOsThermalFps(pkg)
    HyperOsFeatureEntry.PERF_SCHEDULE -> Route.HyperOsPerfSchedule(pkg)
    HyperOsFeatureEntry.FISR -> Route.HyperOsFisr(pkg)
    HyperOsFeatureEntry.DYN_RES -> Route.HyperOsDynRes(pkg)
    HyperOsFeatureEntry.GPU_TUNER -> Route.HyperOsGpuTuner(pkg)
    HyperOsFeatureEntry.MIGT -> Route.HyperOsMigt(pkg)
}

/**
 * 可见入口计算（纯函数）：按该 App 作用域片段中实际存在的键 + 设备能力。
 * 无对应数据的入口隐藏（方案「显隐规则」）。v2.2：全局开关只留全局通用配置页，
 * 场景管理并入性能调度。
 */
internal fun visibleFeatureEntries(
    document: JsonObject?,
    caps: JoyoseManager.DeviceCaps?,
): List<HyperOsFeatureEntry> {
    val entries = mutableListOf<HyperOsFeatureEntry>()
    val ovrride = document.findFragment("/game_booster/booster_config/ovrride_config/")
    val fisr = document.findFragment("/game_booster/fisr_config/enhance_config/")
    val gpu = document?.keys?.any { it.startsWith("/game_booster/self_gpu_tuner_config/") } == true
    val migl = document.findFragment("/game_booster/migl_settings/game_params/")
    val scenes = (ovrride?.second?.get("scene_ovrride") as? JsonArray)?.isNotEmpty() == true
    // 性能调度 per-app 项（dcs/fstb/group_fight_thresh 等）
    val perfKeys = ovrride?.second?.keysMatching(
        "dcs_enable", "dcs_config", "fstb_cmds", "group_fight_thresh",
        "need_game_sdk", "disable_scenes", "start_scene", "end_scene",
    )?.isNotEmpty() == true

    // 温控与帧率：该 App 有 ovrride 条目
    if (ovrride != null) entries += HyperOsFeatureEntry.THERMAL_FPS
    // 性能调度：per-app 参数或场景条目存在
    if (perfKeys || scenes) entries += HyperOsFeatureEntry.PERF_SCHEDULE
    // 插帧超分：fisr 片段存在（direct 或 OTHER 兜底命中）
    if (fisr != null) entries += HyperOsFeatureEntry.FISR
    // 动态分辨率：migl 片段或 ovrride 内 migl_dr_* / dsar / drr 键
    val dynResKeys = ovrride?.second?.keysMatching("migl_dr_", "dsar", "drr")?.isNotEmpty() == true
    if (migl != null || dynResKeys) entries += HyperOsFeatureEntry.DYN_RES
    // GPU 自研调参：self_gpu_tuner_config 该 App profile 存在
    if (gpu) entries += HyperOsFeatureEntry.GPU_TUNER
    // migt：内核模块在载（无 migt 参数则隐藏，方案第四章）
    if (caps?.migt?.exists == true) entries += HyperOsFeatureEntry.MIGT
    return entries
}

// ── 双皮肤共享的展示辅助（JsonElement 渲染 / 徽标文案）────────────────

/** JsonPrimitive 展示文本：字符串/数字/布尔统一取 content（JsonNull 即 "null"）。 */
internal fun jsonScalarText(element: JsonElement): String? =
    (element as? kotlinx.serialization.json.JsonPrimitive)?.content

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
