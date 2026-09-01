package com.remoteconfig.override.ui.screens

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.remoteconfig.override.data.CurveCodec
import com.remoteconfig.override.data.MigtCodec
import com.remoteconfig.override.data.NovatekCodec
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.navigation.Route
import com.remoteconfig.override.viewmodel.HyperOsSwitchCatalog
import com.remoteconfig.override.viewmodel.HyperOsViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/**
 * HyperOS 功能页 v2 — 8 个功能子屏（方案第三章）。
 *
 * 所有子屏共用 [HyperOsFeatureScaffold] 外壳与 [HyperOsViewModel] 状态：
 * - 片段编辑类（温控 / 性能调度 / FISR / GPU / 动态分辨率 / 场景）与作用域编辑器
 *   共享同一份草稿（scopedEditorState），保存统一走 joyose-scoped-write；
 * - 全局开关（一级布尔）走 toggleGlobalSwitch（toggleBoolean 双库镜像写，
 *   path 必带 game_booster 前缀），乐观更新 + 失败回滚；
 * - migt 名单/参数包走专用命令（joyose-migt-write / -remove，数组增删超出
 *   作用域编辑能力），写后强制重载作用域草稿。
 */

// ── S3-1 温控与帧率 ─────────────────────────────────────────────────────────

/** 温控参数目录：key → 标签 + 曲线格式（null = 标量 int）。 */
private data class ThermalParam(val key: String, val label: String, val format: CurveCodec.Format?)

private val THERMAL_COMMON = listOf(
    ThermalParam("dynamic_fps_hysteresis", "温度档回切滞回（秒）", null),
    ThermalParam("badfps_thresh1", "低帧率统计阈值 1", null),
    ThermalParam("badfps_thresh2", "低帧率统计阈值 2", null),
    ThermalParam("monitor_power", "功耗监控曲线", CurveCodec.TEMP_POWER),
)

private val THERMAL_TGAME = listOf(
    ThermalParam("dynamic_fps", "温控限帧曲线", CurveCodec.TEMP_FPS),
    ThermalParam("dynamicfps_by_battery_T", "低电量限帧（电量:限帧）", CurveCodec.FPS_SOC_FPS),
    ThermalParam("dynamic_targetfps", "目标帧率专属曲线", CurveCodec.FPS_TEMP_PARAM),
    ThermalParam("dynamic_fan_targetfps", "散热风扇目标帧率", CurveCodec.FPS_TEMP_PARAM),
    ThermalParam("dynamic_targetfps_cpufreq", "温控 CPU 限频", CurveCodec.TEMP_FPS_FREQ),
    ThermalParam("PID_T", "温控限帧 PID 参数", CurveCodec.FPS_TEMP_PARAM),
)

private val THERMAL_MGAME = listOf(
    ThermalParam("dynamic_fps_M", "温控限帧曲线（MGAME）", CurveCodec.TEMP_FPS),
    ThermalParam("dynamicfps_by_battery_M", "低电量限帧（MGAME）", CurveCodec.FPS_SOC_FPS),
    ThermalParam("dynamic_targetfps_M", "目标帧率专属曲线（MGAME）", CurveCodec.FPS_TEMP_PARAM),
    ThermalParam("dynamic_fan_targetfps_M", "散热风扇目标帧率（MGAME）", CurveCodec.FPS_TEMP_PARAM),
    ThermalParam("dynamic_targetfps_cpufreq_M", "温控 CPU 限频（MGAME）", CurveCodec.TEMP_FPS_FREQ),
    ThermalParam("PID_M", "温控限帧 PID 参数（MGAME）", CurveCodec.FPS_TEMP_PARAM),
)

/** 一次曲线编辑的目标。 */
private data class CurveTarget(val key: String, val label: String, val format: CurveCodec.Format)

@Composable
fun HyperOsThermalFpsScreen(pkg: String) {
    val navigator = LocalNavigator.current
    val viewModel: HyperOsViewModel = viewModel()
    val detail by viewModel.detailState.collectAsStateWithLifecycle()
    val scoped by viewModel.scopedEditorState.collectAsStateWithLifecycle()
    val caps by viewModel.deviceCapsState.collectAsStateWithLifecycle()
    LaunchedEffect(pkg) { viewModel.loadDetail(pkg) }

    val document = remember(scoped.edited) {
        scoped.edited?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    }
    val found = remember(document) { document.findFragment("/game_booster/booster_config/ovrride_config/") }
    val (pointer, fragment) = found ?: (null to null)

    var scalarTarget by remember { mutableStateOf<EditTarget?>(null) }
    var curveTarget by remember { mutableStateOf<CurveTarget?>(null) }
    scalarTarget?.let { t ->
        EditValueDialog(title = t.title, initial = t.initial, isNumber = t.isNumber, onCommit = { v ->
            scalarTarget = null
            t.onCommit(v)
        }, onDismiss = { scalarTarget = null })
    }
    curveTarget?.let { t ->
        val current = fragment?.get(t.key)
        CurveEditorDialog(
            title = t.label,
            value = (current as? JsonPrimitive)?.content.orEmpty(),
            format = t.format,
            chips = when (t.format) {
                CurveCodec.TEMP_FPS, CurveCodec.FPS_THRESH -> fpsChips(caps)
                CurveCodec.TEMP_FPS_FREQ -> cpuFreqChips(caps)
                else -> emptyList()
            },
            onCommit = { text ->
                curveTarget = null
                if (pointer != null && current != null) {
                    viewModel.updateFragmentValue(pointer, t.key, current.sameTypePrimitive(text))
                }
            },
            onDismiss = { curveTarget = null },
        )
    }

    HyperOsFeatureScaffold(
        title = "温控与帧率",
        dirty = scoped.base != null && scoped.edited != scoped.base,
        saving = scoped.writing,
        error = detail.switchError ?: scoped.error,
        loading = detail.loading || scoped.loading,
        diffBase = scoped.base,
        diffEdited = scoped.edited,
        onBack = navigator::pop,
        onSave = viewModel::saveScopedEditor,
        onRevert = viewModel::revertScopedEditor,
    ) {
        if (fragment == null || pointer == null) {
            item(key = "empty") { HyperOsEmptyHint("该应用无 ovrride 温控条目") }
            return@HyperOsFeatureScaffold
        }
        fun LazyListScope.renderParams(params: List<ThermalParam>) {
            params.forEach { p ->
                val current = fragment[p.key] ?: return@forEach
                val text = (current as? JsonPrimitive)?.content ?: return@forEach
                item(key = p.key) {
                    HyperOsValueRow(title = p.label, value = text) {
                        val fmt = p.format
                        if (fmt != null) {
                            curveTarget = CurveTarget(p.key, p.label, fmt)
                        } else {
                            scalarTarget = EditTarget(
                                title = p.label,
                                initial = text,
                                isNumber = !((current as? JsonPrimitive)?.isString ?: false),
                            ) { v ->
                                viewModel.updateFragmentValue(pointer, p.key, current.sameTypePrimitive(v))
                            }
                        }
                    }
                }
            }
        }

        // v2.2：charge_optimize_enable 是全局开关，只在全局通用配置页展示/编辑
        renderParams(THERMAL_COMMON)

        if (THERMAL_TGAME.any { fragment.containsKey(it.key) }) {
            item(key = "sec_t") { HyperOsSectionLabel("TGAME 档（大型游戏）") }
            renderParams(THERMAL_TGAME)
        }
        if (THERMAL_MGAME.any { fragment.containsKey(it.key) }) {
            item(key = "sec_m") { HyperOsSectionLabel("MGAME 档（小游戏）") }
            renderParams(THERMAL_MGAME)
        }
        // 说明：_M 档与基础档分节（方案「温控与帧率」）；songyuan 云控无
        // ovrride_config#90/#120 档（appview 亦不索引），故无档位切换 UI。
    }
}

// ── S3-2 性能调度（v2.2：per-app 项 + 场景管理；全局开关只在全局通用配置页）──

@Composable
fun HyperOsPerfScheduleScreen(pkg: String) {
    val navigator = LocalNavigator.current
    val viewModel: HyperOsViewModel = viewModel()
    val detail by viewModel.detailState.collectAsStateWithLifecycle()
    val scoped by viewModel.scopedEditorState.collectAsStateWithLifecycle()
    LaunchedEffect(pkg) { viewModel.loadDetail(pkg) }

    val document = remember(scoped.edited) {
        scoped.edited?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    }
    val found = remember(document) { document.findFragment("/game_booster/booster_config/ovrride_config/") }
    val (pointer, fragment) = found ?: (null to null)
    val scenes = remember(fragment) { fragment?.let(viewModel::parseSceneInfo).orEmpty() }
    // 原始场景数组（结构字段编辑需要：timeout/default_need 等不在 SceneInfo 模型里）
    val sceneArray = remember(fragment) { (fragment?.get("scene_ovrride") as? JsonArray).orEmpty() }

    var textTarget by remember { mutableStateOf<EditTarget?>(null) }
    var intTarget by remember { mutableStateOf<EditTarget?>(null) }
    var cmdTarget by remember { mutableStateOf<SceneCmdTarget?>(null) }
    textTarget?.let { t ->
        EditValueDialog(title = t.title, initial = t.initial, isNumber = false, onCommit = { v ->
            textTarget = null; t.onCommit(v)
        }, onDismiss = { textTarget = null })
    }
    intTarget?.let { t ->
        EditValueDialog(title = t.title, initial = t.initial, isNumber = true, onCommit = { v ->
            intTarget = null; t.onCommit(v)
        }, onDismiss = { intTarget = null })
    }
    cmdTarget?.let { t ->
        CmdEditorDialog(
            title = t.title,
            cmd = t.initial,
            onCommit = { nv ->
                cmdTarget = null
                t.onCommit(nv)
            },
            onDismiss = { cmdTarget = null },
        )
    }

    HyperOsFeatureScaffold(
        title = "性能调度",
        dirty = scoped.base != null && scoped.edited != scoped.base,
        saving = scoped.writing,
        error = detail.switchError ?: scoped.error,
        loading = detail.loading || scoped.loading,
        diffBase = scoped.base,
        diffEdited = scoped.edited,
        onBack = navigator::pop,
        onSave = viewModel::saveScopedEditor,
        onRevert = viewModel::revertScopedEditor,
    ) {
        if (pointer == null || fragment == null) {
            item(key = "empty") { HyperOsEmptyHint("该应用无调度/场景配置") }
            return@HyperOsFeatureScaffold
        }

        // ── 本应用参数（ovrride 片段内，per-app）──
        item(key = "sec_scalar") { HyperOsSectionLabel("本应用参数") }
        (fragment["dcs_enable"] as? JsonPrimitive)?.let { sw ->
            if (sw.booleanOrNull != null) {
                item(key = "dcs_enable") {
                    HyperOsSwitchRow(
                        title = "DCS 动态 CPU 分级",
                        summary = "dcs_enable（本应用条目内）",
                        checked = sw.boolean,
                        enabled = !scoped.writing,
                    ) { v -> viewModel.updateFragmentValue(pointer, "dcs_enable", JsonPrimitive(v)) }
                }
            }
        }
        (fragment["group_fight_thresh"] as? JsonPrimitive)?.let { thresh ->
            item(key = "group_fight_thresh") {
                HyperOsValueRow(title = "团战场景识别阈值（ms）", value = thresh.content) {
                    intTarget = EditTarget(
                        title = "团战场景识别阈值（ms）",
                        initial = thresh.content,
                        isNumber = !thresh.isString,
                    ) { v -> viewModel.updateFragmentValue(pointer, "group_fight_thresh", thresh.sameTypePrimitive(v)) }
                }
            }
        }
        // 场景生效范围（逗号 scene_id）
        listOf(
            "disable_scenes" to "禁用场景（逗号 scene_id）",
            "start_scene" to "生效起始场景",
            "end_scene" to "生效结束场景",
        ).forEach { (key, label) ->
            (fragment[key] as? JsonPrimitive)?.let { v ->
                item(key = key) {
                    HyperOsValueRow(title = label, value = v.content) {
                        textTarget = EditTarget(label, v.content, isNumber = false) { nv ->
                            viewModel.updateFragmentValue(pointer, key, v.sameTypePrimitive(nv))
                        }
                    }
                }
            }
        }
        (fragment["need_game_sdk"] as? JsonPrimitive)?.let { v ->
            item(key = "need_game_sdk") {
                if (v.booleanOrNull != null) {
                    HyperOsSwitchRow(
                        title = "需要游戏 SDK（need_game_sdk）",
                        summary = "影响 booster 应用判定",
                        checked = v.boolean,
                        enabled = !scoped.writing,
                    ) { b -> viewModel.updateFragmentValue(pointer, "need_game_sdk", JsonPrimitive(b)) }
                } else {
                    HyperOsValueRow(title = "need_game_sdk", value = v.content) {}
                }
            }
        }
        fragment["fstb_cmds"]?.let { fstb ->
            item(key = "fstb") {
                HyperOsActionRow(
                    title = "首启加速命令（fstb_cmds）",
                    summary = "共 ${((fstb as? JsonArray)?.size ?: 0)} 条 · 只读",
                )
            }
        }
        if (fragment.containsKey("dcs_config")) {
            item(key = "dcs_hint") {
                HyperOsActionRow(
                    title = "DCS 命令表（dcs_config）",
                    summary = "结构复杂，请经高级编辑（JSON）修改",
                )
            }
        }

        // ── 场景管理（scene_ovrride；v2.2 由独立场景命令页并入）──
        item(key = "sec_scene") { HyperOsSectionLabel("场景管理（scene_ovrride）") }
        if (scenes.isEmpty()) {
            item(key = "scene_empty") { HyperOsEmptyHint("无场景条目") }
        }
        scenes.forEach { scene ->
            item(key = "scene_${scene.index}") {
                HyperOsSectionCard(title = scene.sceneNameLabel) {
                    // 结构布尔（已有键编辑）
                    scene.flags.forEach { (flag, value) ->
                        HyperOsSwitchRow(
                            title = flag,
                            summary = "场景结构布尔",
                            checked = value,
                            enabled = !scoped.writing,
                        ) { v -> viewModel.updateSceneValue(pointer, scene.index, flag, JsonPrimitive(v)) }
                    }
                    // 未进 SceneInfo.flags 的结构字段（timeout / default_need 等）
                    val rawScene = sceneArray.getOrNull(scene.index) as? JsonObject
                    rawScene?.forEach { (k, v) ->
                        if (k == "timeout" && v is JsonPrimitive) {
                            HyperOsValueRow(title = "timeout（定时释放秒数）", value = v.content) {
                                intTarget = EditTarget("timeout", v.content, isNumber = true) { nv ->
                                    nv.toLongOrNull()?.let { n ->
                                        viewModel.updateSceneValue(pointer, scene.index, k, JsonPrimitive(n))
                                    }
                                }
                            }
                        } else if (k == "default_need" && v is JsonPrimitive && v.booleanOrNull != null) {
                            HyperOsSwitchRow(
                                title = "default_need",
                                summary = "场景结构布尔",
                                checked = v.boolean,
                                enabled = !scoped.writing,
                            ) { b -> viewModel.updateSceneValue(pointer, scene.index, k, JsonPrimitive(b)) }
                        }
                    }
                    // 命令组：end 组不展示（恢复默认态，无修改价值）；
                    // perflock 命令不进功能页（原样透传）；其余经 CmdEditor 结构化编辑
                    val editable = scene.containers.filter { !it.key.startsWith("end") }
                    editable.forEach { container ->
                        val (shown, perflocks) = container.entries.partition { !it.cmd.startsWith("perflock#") }
                        if (perflocks.isNotEmpty()) {
                            HyperOsActionRow(
                                title = "${container.key} · perflock ×${perflocks.size}",
                                summary = "锁定命令不在此编辑（保存时原样保留）",
                            )
                        }
                        shown.forEach { entry ->
                            HyperOsActionRow(
                                title = "${container.key}[${entry.index}] ${entry.permission}",
                                summary = entry.cmd,
                                onClick = {
                                    cmdTarget = SceneCmdTarget(
                                        title = "${scene.sceneNameLabel} · ${container.key}[${entry.index}]",
                                        initial = entry.cmd,
                                    ) { nv ->
                                        viewModel.updateSceneCmd(pointer, scene.index, container.key, entry.index, nv)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // 复用映射 + JSON 编辑器入口（结构性增删在作用域编辑器完成）
        item(key = "to_editor") {
            HyperOsSectionCard {
                HyperOsActionRow(
                    title = "在 JSON 编辑器中打开",
                    summary = "新增/删除场景与命令组条目请走作用域编辑（形状守卫在 CLI）",
                    onClick = { navigator.push(Route.HyperOsScopedEditor(pkg)) },
                )
            }
        }
    }
}

/** 场景命令编辑目标（容器内定位 + 原始 cmd）。 */
private data class SceneCmdTarget(val title: String, val initial: String, val onCommit: (String) -> Unit)

/** Novatek 等级编辑目标（段标识 + 等级下标 + 当前等级）。 */
private data class NtLevelTarget(
    val label: String,
    val levelIndex: Int,
    val level: NovatekCodec.Level,
    val onCommit: (NovatekCodec.Level) -> Unit,
)

// ── S3-3 插帧超分（FISR）────────────────────────────────────────────────────

private val FISR_FEATURES = listOf("FI", "SR", "FISR", "RE", "PQ_FIRST", "FPS_FIRST")
private val FISR_STRATEGIES = listOf("FRC", "FSR", "FSR3", "XAISR", "XFI", "AFME", "NT#FI", "NT#SR", "NT#FISR")

@Composable
fun HyperOsFisrScreen(pkg: String) {
    val navigator = LocalNavigator.current
    val viewModel: HyperOsViewModel = viewModel()
    val detail by viewModel.detailState.collectAsStateWithLifecycle()
    val scoped by viewModel.scopedEditorState.collectAsStateWithLifecycle()
    val caps by viewModel.deviceCapsState.collectAsStateWithLifecycle()
    val spEcho by viewModel.spEchoState.collectAsStateWithLifecycle()
    LaunchedEffect(pkg) {
        viewModel.loadDetail(pkg)
        viewModel.loadSpEcho(listOf("fisr_switch_$pkg", "fisr_enhance_status_$pkg"))
    }

    val document = remember(scoped.edited) {
        scoped.edited?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    }
    val found = remember(document) { document.findFragment("/game_booster/fisr_config/enhance_config/") }
    val (pointer, fragment) = found ?: (null to null)
    val policies = remember(fragment) { (fragment?.get("enhance_policy_config") as? JsonArray).orEmpty() }

    // Novatek 片段定位（remember 须在组合期，不能放 LazyListScope 内）
    val novatekFound = remember(document) { document.findAnyFragment("/game_booster/novatek_game_params/") }
    val novatekRaw = (novatekFound?.second as? JsonPrimitive)?.content
    val novatekEntry = remember(novatekRaw) { novatekRaw?.let { NovatekCodec.parse(it) } }
    val gexFound = remember(document) {
        document.findAnyFragment("/game_booster/novatek_extend_config/novatek_gex_fps_limit/")
    }
    val nonPlaying = remember(document) {
        document.findAnyFragment("/game_booster/novatek_extend_config/novatek_non_playing_config/")
    }
    val blacklistFound = remember(document) { document.findAnyFragment("/game_booster/novatek_black_app") }

    var textTarget by remember { mutableStateOf<EditTarget?>(null) }
    var intTarget by remember { mutableStateOf<EditTarget?>(null) }
    var ntLevelTarget by remember { mutableStateOf<NtLevelTarget?>(null) }
    textTarget?.let { t ->
        EditValueDialog(title = t.title, initial = t.initial, isNumber = false, onCommit = { v ->
            textTarget = null; t.onCommit(v)
        }, onDismiss = { textTarget = null })
    }
    intTarget?.let { t ->
        EditValueDialog(title = t.title, initial = t.initial, isNumber = true, onCommit = { v ->
            intTarget = null; t.onCommit(v)
        }, onDismiss = { intTarget = null })
    }
    ntLevelTarget?.let { t ->
        NovatekLevelDialog(
            title = "Novatek ${t.label} 等级 ${t.levelIndex}",
            level = t.level,
            refreshRates = caps?.refreshRates.orEmpty(),
            onCommit = { lv ->
                ntLevelTarget = null
                t.onCommit(lv)
            },
            onDismiss = { ntLevelTarget = null },
        )
    }

    HyperOsFeatureScaffold(
        title = "插帧超分",
        dirty = scoped.base != null && scoped.edited != scoped.base,
        saving = scoped.writing,
        error = detail.switchError ?: scoped.error,
        loading = detail.loading || scoped.loading,
        diffBase = scoped.base,
        diffEdited = scoped.edited,
        onBack = navigator::pop,
        onSave = viewModel::saveScopedEditor,
        onRevert = viewModel::revertScopedEditor,
    ) {
        if (fragment == null || pointer == null) {
            item(key = "empty") { HyperOsEmptyHint("该应用无 FISR 策略片段") }
            return@HyperOsFeatureScaffold
        }

        // 运行态只读（Joyose SP：fisr_switch / fisr_enhance_status）
        item(key = "runtime") {
            HyperOsSectionCard(title = "运行态（只读）") {
                HyperOsActionRow(
                    title = "插帧开关（fisr_switch）",
                    summary = spEcho["fisr_switch_$pkg"]?.let { "已写入：$it" } ?: "未写入（默认关闭）",
                )
                HyperOsActionRow(
                    title = "增强状态（fisr_enhance_status）",
                    summary = spEcho["fisr_enhance_status_$pkg"]?.let { "已写入：$it" } ?: "未写入",
                )
            }
        }

        item(key = "game_list") {
            val games = (fragment["game_list"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
            HyperOsSectionCard(title = "覆盖范围") {
                HyperOsActionRow(title = "game_list", summary = games?.joinToString(", ").orEmpty())
            }
        }

        // enhance_policy_config 逐条表单（数组内增删 = 片段内部结构调整，作用域写回支持）
        policies.forEachIndexed { i, entry ->
            val obj = entry as? JsonObject ?: return@forEachIndexed
            item(key = "policy_$i") {
                HyperOsSectionCard(title = "策略 ${i + 1}") {
                    val feature = (obj["feature"] as? JsonPrimitive)?.content.orEmpty()
                    HyperOsValueRow(title = "feature（增强类型）", value = feature) {
                        textTarget = EditTarget("feature", feature, isNumber = false) { v ->
                            viewModel.updateFragmentNested(pointer, listOf("enhance_policy_config", "$i", "feature"), JsonPrimitive(v))
                        }
                    }
                    val strategy = (obj["strategy"] as? JsonPrimitive)?.content.orEmpty()
                    HyperOsValueRow(title = "strategy（实现策略）", value = strategy) {
                        textTarget = EditTarget("strategy", strategy, isNumber = false) { v ->
                            viewModel.updateFragmentNested(pointer, listOf("enhance_policy_config", "$i", "strategy"), JsonPrimitive(v))
                        }
                    }
                    val refresh = (obj["support_max_refresh"] as? JsonPrimitive)
                    if (refresh != null) {
                        HyperOsValueRow(title = "support_max_refresh（上限刷新率）", value = refresh.content) {
                            intTarget = EditTarget("support_max_refresh", refresh.content, isNumber = true) { v ->
                                v.toLongOrNull()?.let { n ->
                                    viewModel.updateFragmentNested(
                                        pointer, listOf("enhance_policy_config", "$i", "support_max_refresh"), JsonPrimitive(n),
                                    )
                                }
                            }
                        }
                    }
                    val scenes = (obj["disable_scene_list"] as? JsonArray)
                    if (scenes != null) {
                        HyperOsActionRow(
                            title = "disable_scene_list（命中即停增强）",
                            summary = scenes.joinToString(", ") { (it as? JsonPrimitive)?.content.orEmpty() },
                        )
                    }
                    val status = (obj["switch_default_status"] as? JsonPrimitive)
                    if (status != null) {
                        HyperOsValueRow(title = "switch_default_status", value = status.content) {
                            textTarget = EditTarget("switch_default_status", status.content, isNumber = false) { v ->
                                viewModel.updateFragmentNested(
                                    pointer, listOf("enhance_policy_config", "$i", "switch_default_status"), JsonPrimitive(v),
                                )
                            }
                        }
                    }
                    HyperOsActionRow(title = "删除此策略", summary = "从 enhance_policy_config 移除") {
                        viewModel.updateFragmentValue(
                            pointer, "enhance_policy_config",
                            JsonArray(policies.filterIndexed { idx, _ -> idx != i }),
                        )
                    }
                }
            }
        }

        item(key = "add_policy") {
            HyperOsSectionCard {
                HyperOsActionRow(title = "+ 添加策略", summary = "追加一条 {feature, strategy}") {
                    val newEntry = JsonObject(mapOf("feature" to JsonPrimitive("FI"), "strategy" to JsonPrimitive("FRC")))
                    viewModel.updateFragmentValue(pointer, "enhance_policy_config", JsonArray(policies + newEntry))
                }
            }
        }

        item(key = "enum_hint") {
            HyperOsSectionCard(title = "取值参考") {
                HyperOsActionRow(title = "feature", summary = FISR_FEATURES.joinToString(" / "))
                HyperOsActionRow(title = "strategy", summary = FISR_STRATEGIES.joinToString(" / ") + "（可 - 组合）")
                HyperOsActionRow(
                    title = "support_max_refresh 档位",
                    summary = "本机刷新率：" + (caps?.refreshRates?.joinToString("/ ") ?: "未知"),
                )
            }
        }

        // ── Novatek 独显配置（songyuan 实际路径，方案 1.6）──
        if (novatekFound != null) {
            item(key = "sec_nt") { HyperOsSectionLabel("Novatek 独显（novatek_game_params）") }
            if (novatekEntry == null) {
                item(key = "nt_raw") {
                    HyperOsSectionCard(title = "当前条目（格式不识别，只读）") {
                        HyperOsActionRow(title = "raw", summary = novatekRaw)
                    }
                }
            }
            novatekEntry?.let { entry ->
                val ntTarget = novatekFound.first
                val segments = listOf(
                    "FI（插帧）" to entry.fi,
                    "SR（超分）" to entry.sr,
                    "FISR（插帧+超分）" to entry.fisr,
                )
                segments.forEach { (label, seg) ->
                    if (seg == null) return@forEach
                    seg.levels.forEachIndexed { li, lvl ->
                        item(key = "nt_${label}_$li") {
                            HyperOsValueRow(
                                title = "$label 等级 $li（${lvl.dynamicFps} → ${lvl.targetFps}fps）",
                                value = lvl.params.joinToString(","),
                            ) {
                                ntLevelTarget = NtLevelTarget(label, li, lvl) { newLevel ->
                                    val newSeg = NovatekCodec.Segment(
                                        seg.levels.toMutableList().also { it[li] = newLevel },
                                    )
                                    val newEntry = when (label) {
                                        "FI（插帧）" -> entry.copy(fi = newSeg)
                                        "SR（超分）" -> entry.copy(sr = newSeg)
                                        else -> entry.copy(fisr = newSeg)
                                    }
                                    viewModel.updateFragmentSelf(
                                        ntTarget,
                                        JsonPrimitive(NovatekCodec.serialize(newEntry)),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // GEX 帧率上限（pkg:fps）
            (gexFound?.second as? JsonPrimitive)?.let { gex ->
                item(key = "nt_gex") {
                    HyperOsValueRow(title = "Novatek GEX 帧率上限", value = gex.content) {
                        val fps = gex.content.substringAfterLast(':')
                        textTarget = EditTarget("GEX 帧率上限", fps, isNumber = true) { nv ->
                            nv.toLongOrNull()?.let { n ->
                                viewModel.updateFragmentSelf(
                                    gexFound.first,
                                    JsonPrimitive(gex.content.substringBeforeLast(':') + ":" + n),
                                )
                            }
                        }
                    }
                }
            }
            // 非游玩档（只读 + 跳 JSON）
            (nonPlaying?.second as? JsonPrimitive)?.let { np ->
                item(key = "nt_non_playing") {
                    HyperOsSectionCard(title = "非游玩降级档（novatek_non_playing_config）") {
                        HyperOsActionRow(title = "raw", summary = np.content.take(200))
                        HyperOsActionRow(
                            title = "在 JSON 编辑器中修改",
                            summary = "格式与 game_params 相同",
                            onClick = { navigator.push(Route.HyperOsScopedEditor(pkg)) },
                        )
                    }
                }
            }
            // 黑名单（仅在名单内时出现片段 → 可移除；加入走 JSON 编辑）
            if (blacklistFound != null) {
                val arr = blacklistFound.second as? JsonArray
                item(key = "nt_blacklist") {
                    HyperOsSwitchRow(
                        title = "独显黑名单（novatek_black_app）",
                        summary = "云游戏/串流等已是插帧后内容的应用；关闭 = 从名单移除",
                        checked = true,
                        enabled = !scoped.writing,
                    ) { on ->
                        if (!on) {
                            val kept = (arr ?: JsonArray(emptyList())).filterNot {
                                (it as? JsonPrimitive)?.content == pkg
                            }
                            viewModel.updateFragmentSelf(blacklistFound.first, JsonArray(kept))
                        }
                    }
                }
            } else {
                item(key = "nt_blacklist_off") {
                    HyperOsActionRow(
                        title = "独显黑名单",
                        summary = "当前不在名单内（加入请走 JSON 编辑器编辑 novatek_black_app）",
                    )
                }
            }
        }
    }
}

// ── S3-4 migt 帧感知加速 ────────────────────────────────────────────────────

@Composable
fun HyperOsMigtScreen(pkg: String) {
    val navigator = LocalNavigator.current
    val viewModel: HyperOsViewModel = viewModel()
    val detail by viewModel.detailState.collectAsStateWithLifecycle()
    val scoped by viewModel.scopedEditorState.collectAsStateWithLifecycle()
    val caps by viewModel.deviceCapsState.collectAsStateWithLifecycle()
    val migt by viewModel.migtState.collectAsStateWithLifecycle()
    LaunchedEffect(pkg) { viewModel.loadDetail(pkg) }
    // 作用域片段就绪后取 migt 条目（名单成员判定 + sysfs 运行态回显）
    LaunchedEffect(scoped.base, migt.writing) {
        if (scoped.base != null && !migt.writing) viewModel.loadMigt(pkg)
    }

    // 本地表单（编辑中副本；保存 = 整条 joyose-migt-write 一次落库）
    var form by remember { mutableStateOf<MigtCodec.Pack?>(null) }
    val formDirty = form != null && form != migt.form
    // 仅在无未保存修改时同步后台重载；写入完成后（writing true→false）强制同步——写入即提交
    LaunchedEffect(migt.form) { if (!formDirty) form = migt.form }
    var wasWriting by remember { mutableStateOf(false) }
    LaunchedEffect(migt.writing) {
        if (wasWriting && !migt.writing) form = migt.form
        wasWriting = migt.writing
    }

    var curveTarget by remember { mutableStateOf<CurveTarget?>(null) }
    var intTarget by remember { mutableStateOf<EditTarget?>(null) }
    curveTarget?.let { t ->
        val current = when (t.key) {
            "migt_freq" -> form?.migtFreq.orEmpty()
            "fps:thresh" -> form?.fpsThresh.orEmpty()
            "migt_ceiling_freq" -> form?.migtCeilingFreq.orEmpty()
            else -> ""
        }
        CurveEditorDialog(
            title = t.label,
            value = current,
            format = t.format,
            chips = when (t.key) {
                "migt_freq", "migt_ceiling_freq" -> cpuFreqChips(caps)
                "fps:thresh" -> fpsChips(caps)
                else -> emptyList()
            },
            onCommit = { text ->
                curveTarget = null
                val f = form ?: return@CurveEditorDialog
                form = when (t.key) {
                    "migt_freq" -> f.copy(migtFreq = text)
                    "fps:thresh" -> f.copy(fpsThresh = text.ifBlank { null })
                    "migt_ceiling_freq" -> f.copy(migtCeilingFreq = text.ifBlank { null })
                    else -> f
                }
            },
            onDismiss = { curveTarget = null },
        )
    }
    intTarget?.let { t ->
        EditValueDialog(title = t.title, initial = t.initial, isNumber = true, onCommit = { v ->
            intTarget = null; t.onCommit(v)
        }, onDismiss = { intTarget = null })
    }

    HyperOsFeatureScaffold(
        title = "migt 帧感知加速",
        dirty = formDirty,
        saving = migt.writing,
        error = migt.error ?: scoped.error,
        // scoped 片段加载失败（base==null）时不再等 migt.loaded，避免永久转圈
        loading = detail.loading || scoped.loading || (!migt.loaded && scoped.base != null),
        diffBase = migt.raw,
        diffEdited = form?.let { MigtCodec.serialize(it) },
        onBack = navigator::pop,
        onSave = { form?.let(viewModel::saveMigtPack) },
        onRevert = { form = migt.form },
    ) {
        item(key = "membership") {
            HyperOsSectionCard(title = "名单成员") {
                HyperOsSwitchRow(
                    title = "启用 migt 帧感知加速",
                    summary = "进出 game_booster.migt 名单（游戏进前台生效）",
                    checked = migt.inList,
                    enabled = !migt.writing,
                ) { v -> viewModel.toggleMigtMembership(v) }
            }
        }

        if (migt.raw != null && migt.form == null) {
            // 条目存在但格式不识别：只读原始串，避免丢数据
            item(key = "raw") {
                HyperOsSectionCard(title = "当前条目（格式不识别，只读）") {
                    HyperOsActionRow(title = "raw", summary = migt.raw)
                }
            }
        }

        if (form != null) {
            val f = form!!
            item(key = "sec_form") { HyperOsSectionLabel("参数包（保存后整条写入）") }
            item(key = "migt_freq") {
                HyperOsValueRow(title = "migt_freq（每核频率表）", value = f.migtFreq) {
                    curveTarget = CurveTarget("migt_freq", "migt_freq 每核频率", CurveCodec.CPU_FREQ)
                }
            }
            item(key = "migt_ms") {
                HyperOsValueRow(title = "migt_ms（毫秒）", value = f.migtMs?.toString().orEmpty()) {
                    intTarget = EditTarget("migt_ms", f.migtMs?.toString().orEmpty(), isNumber = true) { v ->
                        v.toLongOrNull()?.let { n -> form = f.copy(migtMs = n) }
                    }
                }
            }
            item(key = "fps_thresh") {
                HyperOsValueRow(title = "fps:thresh（帧率阈值表）", value = f.fpsThresh.orEmpty()) {
                    curveTarget = CurveTarget("fps:thresh", "fps:thresh 帧率阈值", CurveCodec.FPS_THRESH)
                }
            }
            item(key = "boost_policy") {
                HyperOsValueRow(title = "boost_policy", value = f.boostPolicy?.toString().orEmpty()) {
                    intTarget = EditTarget("boost_policy", f.boostPolicy?.toString().orEmpty(), isNumber = true) { v ->
                        v.toLongOrNull()?.let { n -> form = f.copy(boostPolicy = n) }
                    }
                }
            }
            item(key = "fps_variance_ratio") {
                HyperOsValueRow(title = "fps_variance_ratio", value = f.fpsVarianceRatio?.toString().orEmpty()) {
                    intTarget = EditTarget("fps_variance_ratio", f.fpsVarianceRatio?.toString().orEmpty(), isNumber = true) { v ->
                        v.toLongOrNull()?.let { n -> form = f.copy(fpsVarianceRatio = n) }
                    }
                }
            }
            // 字段级显隐：本机内核无该参数则隐藏（songyuan 无 super_task_max_num）
            if (caps?.hasMigtParam("super_task_max_num") == true) {
                item(key = "super_task_max_num") {
                    HyperOsValueRow(title = "super_task_max_num", value = f.superTaskMaxNum?.toString().orEmpty()) {
                        intTarget = EditTarget("super_task_max_num", f.superTaskMaxNum?.toString().orEmpty(), isNumber = true) { v ->
                            v.toLongOrNull()?.let { n -> form = f.copy(superTaskMaxNum = n) }
                        }
                    }
                }
            } else {
                item(key = "super_task_hint") {
                    HyperOsActionRow(title = "super_task_max_num", summary = "本机内核无此参数，不展示编辑")
                }
            }
            item(key = "migt_ceiling") {
                HyperOsValueRow(
                    title = "migt_ceiling_freq（频率上限表）",
                    value = f.migtCeilingFreq ?: "未设置",
                ) {
                    curveTarget = CurveTarget("migt_ceiling_freq", "migt_ceiling_freq 频率上限", CurveCodec.CPU_FREQ)
                }
            }
        }

        // v2.2：不提供 /sys/module/migt/parameters 实时回显（用户裁定）
    }
}

// ── S3-5 GPU 自研调参 ───────────────────────────────────────────────────────

@Composable
fun HyperOsGpuTunerScreen(pkg: String) {
    val navigator = LocalNavigator.current
    val viewModel: HyperOsViewModel = viewModel()
    val detail by viewModel.detailState.collectAsStateWithLifecycle()
    val scoped by viewModel.scopedEditorState.collectAsStateWithLifecycle()
    LaunchedEffect(pkg) { viewModel.loadDetail(pkg) }

    val document = remember(scoped.edited) {
        scoped.edited?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    }
    val pointer = "/game_booster/self_gpu_tuner_config/$pkg"
    val fragment = remember(document) { document?.get(pointer) as? JsonObject }

    var textTarget by remember { mutableStateOf<EditTarget?>(null) }
    textTarget?.let { t ->
        EditValueDialog(title = t.title, initial = t.initial, isNumber = false, onCommit = { v ->
            textTarget = null; t.onCommit(v)
        }, onDismiss = { textTarget = null })
    }

    HyperOsFeatureScaffold(
        title = "GPU 自研调参",
        dirty = scoped.base != null && scoped.edited != scoped.base,
        saving = scoped.writing,
        error = detail.switchError ?: scoped.error,
        loading = detail.loading || scoped.loading,
        diffBase = scoped.base,
        diffEdited = scoped.edited,
        onBack = navigator::pop,
        onSave = viewModel::saveScopedEditor,
        onRevert = viewModel::revertScopedEditor,
    ) {
        if (fragment == null) {
            item(key = "empty") { HyperOsEmptyHint("该应用无 self_gpu_tuner_config 配置") }
            return@HyperOsFeatureScaffold
        }

        // v2.2：self_gpu_tuner_enable 是全局开关，只在全局通用配置页展示/编辑
        item(key = "gate_hint") {
            HyperOsActionRow(
                title = "解析门（self_gpu_tuner_enable）",
                summary = "全局开关，请在「通用配置」页修改；关闭时本配置不解析",
            )
        }

        // profile 逐档键值（值全字符串；生效需下次 profile 保存/游戏重启）
        fragment.keys.forEach { profile ->
            val props = fragment[profile] as? JsonObject ?: return@forEach
            item(key = "profile_$profile") {
                HyperOsSectionCard(title = profile) {
                    props.forEach { (k, v) ->
                        val text = (v as? JsonPrimitive)?.content ?: v.toString()
                        HyperOsValueRow(title = k, value = text) {
                            textTarget = EditTarget(k, text, isNumber = false) { nv ->
                                viewModel.updateFragmentNested(pointer, listOf(profile, k), JsonPrimitive(nv))
                            }
                        }
                    }
                }
            }
        }

        item(key = "mode_hint") {
            HyperOsActionRow(
                title = "TunerMode 档位说明",
                summary = "STANDARD / HIGH_QUALITY / CUSTOMIZE 等档位在游戏侧 GPU 面板运行时选择；此处编辑各档 profile 键值",
            )
        }
    }
}

// ── S3-6 动态分辨率 ─────────────────────────────────────────────────────────

/** MIGL 温度→缩放曲线（值可为小数比例）。 */
private val TEMP_SCALE = CurveCodec.Format(
    separator = ",",
    xKind = CurveCodec.AxisKind.DOUBLE,
    yKind = CurveCodec.AxisKind.DOUBLE,
    keyKind = null,
    xLabel = "温度", yLabel = "缩放", keyLabel = null,
    hint = "温度:缩放，逗号分隔（如 43.5:0.9,45:0.8）",
)

private val DYNRES_CURVES = listOf(
    ThermalParam("migl_dr_by_temp_T", "MIGL 温度缩放（TGAME）", TEMP_SCALE),
    ThermalParam("migl_dr_by_temp_M", "MIGL 温度缩放（MGAME）", TEMP_SCALE),
    ThermalParam("dsar", "场景分辨率映射（sceneId:scale）", CurveCodec.SCENE_SCALE),
    ThermalParam("drr", "刷新率缩放映射（refreshRate#fps:scale）", CurveCodec.RR_FPS_SCALE),
)

@Composable
fun HyperOsDynResScreen(pkg: String) {
    val navigator = LocalNavigator.current
    val viewModel: HyperOsViewModel = viewModel()
    val detail by viewModel.detailState.collectAsStateWithLifecycle()
    val scoped by viewModel.scopedEditorState.collectAsStateWithLifecycle()
    LaunchedEffect(pkg) { viewModel.loadDetail(pkg) }

    val document = remember(scoped.edited) {
        scoped.edited?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    }
    val ovrrideFound = remember(document) { document.findFragment("/game_booster/booster_config/ovrride_config/") }
    val miglFound = remember(document) { document.findFragment("/game_booster/migl_settings/game_params/") }

    var curveTarget by remember { mutableStateOf<CurveTarget?>(null) }
    var textTarget by remember { mutableStateOf<EditTarget?>(null) }
    curveTarget?.let { t ->
        val current = ovrrideFound?.second?.get(t.key)
        CurveEditorDialog(
            title = t.label,
            value = (current as? JsonPrimitive)?.content.orEmpty(),
            format = t.format,
            onCommit = { text ->
                curveTarget = null
                val (p, frag) = ovrrideFound ?: return@CurveEditorDialog
                if (current != null) viewModel.updateFragmentValue(p, t.key, current.sameTypePrimitive(text))
            },
            onDismiss = { curveTarget = null },
        )
    }
    textTarget?.let { t ->
        EditValueDialog(title = t.title, initial = t.initial, isNumber = false, onCommit = { v ->
            textTarget = null; t.onCommit(v)
        }, onDismiss = { textTarget = null })
    }

    HyperOsFeatureScaffold(
        title = "动态分辨率",
        dirty = scoped.base != null && scoped.edited != scoped.base,
        saving = scoped.writing,
        error = detail.switchError ?: scoped.error,
        loading = detail.loading || scoped.loading,
        diffBase = scoped.base,
        diffEdited = scoped.edited,
        onBack = navigator::pop,
        onSave = viewModel::saveScopedEditor,
        onRevert = viewModel::revertScopedEditor,
    ) {
        val (ovPointer, ovFragment) = ovrrideFound ?: (null to null)

        // ovrride 内曲线/映射键
        DYNRES_CURVES.forEach { p ->
            val current = ovFragment?.get(p.key) ?: return@forEach
            val text = (current as? JsonPrimitive)?.content ?: return@forEach
            item(key = p.key) {
                HyperOsValueRow(title = p.label, value = text) {
                    val fmt = p.format
                    if (fmt != null) {
                        curveTarget = CurveTarget(p.key, p.label, fmt)
                    } else {
                        textTarget = EditTarget(p.label, text, isNumber = false) { v ->
                            if (ovPointer != null) viewModel.updateFragmentValue(ovPointer, p.key, current.sameTypePrimitive(v))
                        }
                    }
                }
            }
        }

        // MIGL 条目（drr / xrender_config / drr_static）
        val (miglPointer, migl) = miglFound ?: (null to null)
        if (migl != null && miglPointer != null) {
            item(key = "sec_migl") { HyperOsSectionLabel("MIGL 条目") }
            val drr = migl["drr"] as? JsonObject
            if (drr != null && drr.isNotEmpty()) {
                item(key = "drr_obj") {
                    HyperOsSectionCard(title = "drr（刷新率→缩放）") {
                        drr.forEach { (k, v) ->
                            val text = (v as? JsonPrimitive)?.content ?: v.toString()
                            HyperOsValueRow(title = k, value = text) {
                                textTarget = EditTarget(k, text, isNumber = false) { nv ->
                                    viewModel.updateFragmentNested(miglPointer, listOf("drr", k), v.sameTypePrimitive(nv))
                                }
                            }
                        }
                    }
                }
            }
            val xrender = migl["xrender_config"] as? JsonObject
            if (xrender != null && xrender.isNotEmpty()) {
                item(key = "xrender") {
                    HyperOsSectionCard(title = "xrender_config") {
                        xrender.forEach { (k, v) ->
                            val text = (v as? JsonPrimitive)?.content ?: v.toString()
                            HyperOsValueRow(title = k, value = text) {
                                textTarget = EditTarget(k, text, isNumber = false) { nv ->
                                    viewModel.updateFragmentNested(miglPointer, listOf("xrender_config", k), v.sameTypePrimitive(nv))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (ovFragment == null && migl == null) {
            item(key = "empty") { HyperOsEmptyHint("该应用无动态分辨率相关配置") }
        }
    }
}
