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
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.viewmodel.HyperOsSwitchCatalog
import com.remoteconfig.override.viewmodel.HyperOsViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme as miuixColorScheme

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
    ThermalParam("monitor_power", "功耗监控曲线", CurveCodec.TEMP_POWER),
)

private val THERMAL_TGAME = listOf(
    ThermalParam("dynamic_fps", "温控限帧曲线", CurveCodec.TEMP_FPS),
    ThermalParam("dynamicfps_by_battery_T", "低电量限帧", CurveCodec.FPS_SOC_FPS),
    ThermalParam("dynamic_targetfps", "帧率曲线", CurveCodec.FPS_TARGET_BAND),
    ThermalParam("dynamic_fan_targetfps", "风扇帧率曲线", CurveCodec.FPS_TARGET_BAND),
    ThermalParam("dynamic_targetfps_cpufreq", "CPU 限频曲线", CurveCodec.TEMP_FPS_FREQ),
    ThermalParam("PID_T", "PID 参数", CurveCodec.FPS_TEMP_PARAM),
)

private val THERMAL_MGAME = listOf(
    ThermalParam("dynamic_fps_M", "温控限帧曲线", CurveCodec.TEMP_FPS),
    ThermalParam("dynamicfps_by_battery_M", "低电量限帧", CurveCodec.FPS_SOC_FPS),
    ThermalParam("dynamic_targetfps_M", "帧率曲线", CurveCodec.FPS_TARGET_BAND),
    ThermalParam("dynamic_fan_targetfps_M", "风扇帧率曲线", CurveCodec.FPS_TARGET_BAND),
    ThermalParam("dynamic_targetfps_cpufreq_M", "CPU 限频曲线", CurveCodec.TEMP_FPS_FREQ),
    ThermalParam("PID_M", "PID 参数", CurveCodec.FPS_TEMP_PARAM),
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
    val thermal by viewModel.thermalState.collectAsStateWithLifecycle()
    LaunchedEffect(pkg) {
        viewModel.loadDetail(pkg)
        viewModel.loadThermal(pkg)
    }

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
                // 多档曲线：档首 = 刷新率档，用设备档位预填 key 列；限频曲线另加频率 y 列
                CurveCodec.FPS_SOC_FPS, CurveCodec.FPS_TEMP_PARAM -> targetFpsBandChips(caps)
                CurveCodec.TEMP_FPS_FREQ -> targetFpsBandChips(caps) + cpuFreqChips(caps)
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
        error = detail.switchError ?: scoped.error,
        loading = detail.loading || scoped.loading,
        onBack = navigator::pop,
    ) {
        if (fragment == null || pointer == null) {
            item(key = "empty") { HyperOsEmptyHint("该应用无 ovrride 温控条目") }
            return@HyperOsFeatureScaffold
        }

        // 参数行收集器：把一组 ThermalParam 转成分组卡的行列表（无值则跳过；
        // 多档曲线走档位入口页，不在此列）
        val bandedFormats = setOf(
            CurveCodec.FPS_TARGET_BAND,
            CurveCodec.FPS_SOC_FPS,
            CurveCodec.TEMP_FPS_FREQ,
            CurveCodec.FPS_TEMP_PARAM,
        )
        fun paramRows(params: List<ThermalParam>): List<@Composable () -> Unit> =
            params.mapNotNull { p ->
                val current = fragment[p.key] ?: return@mapNotNull null
                val text = (current as? JsonPrimitive)?.content ?: return@mapNotNull null
                if (p.format in bandedFormats || p.format === CurveCodec.TEMP_FPS) return@mapNotNull null
                // 温控曲线（dynamic_*）受总开关控制：关闭时灰置不可编辑（PID/阈值等监控项不受控）
                val editable = !p.key.startsWith("dynamic") || thermal.enabled
                val fmt = p.format
                val isNum = !((current as? JsonPrimitive)?.isString ?: false)
                @Composable {
                    HyperOsValueRow(title = p.label, value = text, enabled = editable) {
                        if (fmt != null) {
                            curveTarget = CurveTarget(p.key, p.label, fmt)
                        } else {
                            scalarTarget = EditTarget(p.label, text, isNumber = isNum) { v ->
                                viewModel.updateFragmentValue(pointer, p.key, current.sameTypePrimitive(v))
                            }
                        }
                    }
                }
            }

        // 多档曲线（帧率曲线 / 风扇帧率曲线 / 低电量限帧 / CPU 限频 / PID，含 _M）：
        // 小标题 = 曲线名，rows = 各档位功能入口行 → push 档位编辑页
        fun bandCurveItems(params: List<ThermalParam>, section: String) {
            params.filter { it.format in bandedFormats }.forEach { p ->
                val current = fragment[p.key] as? JsonPrimitive ?: return@forEach
                val text = if (current.isString) current.content else return@forEach
                val bands = parseFpsBands(text)
                item(key = "band_${p.key}") {
                    if (bands.isNotEmpty()) {
                        // 电量轴（by_battery）：低电量档；其余（温度/频率/PID 均温度轴）：温控档
                        val noun = if (p.format === CurveCodec.FPS_SOC_FPS) "低电量档" else "温控档"
                        HyperOsSectionCard(
                            title = "${p.label}（$section）",
                            rows = bands.map { (bandKey, _) ->
                                val row: @Composable () -> Unit = {
                                    // PID 是参数项不受温控开关控制（同原文编辑语义）；其余曲线关闭时灰置
                                    val editable = p.key.startsWith("PID") || thermal.enabled
                                    val click: (() -> Unit)? = if (editable) {
                                        { navigator.push(Route.HyperOsBandEditor(pkg, p.key, p.label, bandKey)) }
                                    } else {
                                        null
                                    }
                                    HyperOsActionRow(
                                        title = "$bandKey $noun",
                                        summary = "$bandKey FPS 下$noun",
                                        enabled = editable,
                                        end = {
                                            when (LocalUiMode.current) {
                                                UiMode.Miuix -> MiuixIcon(
                                                    imageVector = Icons.Filled.KeyboardArrowRight,
                                                    contentDescription = "编辑",
                                                    tint = miuixColorScheme.onSurfaceVariantActions,
                                                )
                                                UiMode.Material -> Icon(
                                                    Icons.Filled.KeyboardArrowRight,
                                                    contentDescription = "编辑",
                                                )
                                            }
                                        },
                                        onClick = click,
                                    )
                                }
                                row
                            },
                        )
                    } else {
                        // 解析失败：回退原文编辑（仍按格式校验 gate）
                        HyperOsValueRow(title = p.label, value = text, enabled = thermal.enabled) {
                            curveTarget = CurveTarget(p.key, p.label, p.format!!)
                        }
                    }
                }
            }
        }

        // 单层温控曲线（温控限帧 dynamic_fps，含 _M）：无档位，整条曲线走结构化编辑页
        fun simpleCurveItems(params: List<ThermalParam>, section: String) {
            params.filter { it.format === CurveCodec.TEMP_FPS }.forEach { p ->
                val current = fragment[p.key] as? JsonPrimitive ?: return@forEach
                val text = if (current.isString) current.content else return@forEach
                val points = parseSimpleCurve(text, CurveCodec.TEMP_FPS)
                item(key = "simple_${p.key}") {
                    if (points.isNotEmpty()) {
                        val summary = points.joinToString("，") { (x, y) ->
                            "${x}℃→${if (y == "0") "不限" else y}"
                        }
                        val click: (() -> Unit)? = if (thermal.enabled) {
                            { navigator.push(Route.HyperOsSimpleCurveEditor(pkg, p.key, p.label)) }
                        } else {
                            null
                        }
                        HyperOsSectionCard(rows = listOf<@Composable () -> Unit> {
                            HyperOsActionRow(
                                title = p.label,
                                summary = summary,
                                enabled = thermal.enabled,
                                end = {
                                    when (LocalUiMode.current) {
                                        UiMode.Miuix -> MiuixIcon(
                                            imageVector = Icons.Filled.KeyboardArrowRight,
                                            contentDescription = "编辑",
                                            tint = miuixColorScheme.onSurfaceVariantActions,
                                        )
                                        UiMode.Material -> Icon(
                                            Icons.Filled.KeyboardArrowRight,
                                            contentDescription = "编辑",
                                        )
                                    }
                                },
                                onClick = click,
                            )
                        })
                    } else {
                        // 解析失败：回退原文编辑
                        HyperOsValueRow(title = p.label, value = text, enabled = thermal.enabled) {
                            curveTarget = CurveTarget(p.key, p.label, CurveCodec.TEMP_FPS)
                        }
                    }
                }
            }
        }

        // 温控限帧总开关：关闭 = 曲线从配置删除（编辑灰置），开启 = 恢复
        item(key = "thermal_switch") {
            HyperOsSectionCard(rows = listOf<@Composable () -> Unit> {
                HyperOsSwitchRow(
                    title = "温控限帧",
                    checked = thermal.enabled,
                    enabled = !scoped.writing,
                ) { v -> viewModel.setThermalEnabled(pkg, v) }
            })
        }

        // v2.2：charge_optimize_enable 是全局开关，只在全局通用配置页展示/编辑
        val commonRows = paramRows(THERMAL_COMMON)
        if (commonRows.isNotEmpty()) {
            item(key = "common") { HyperOsSectionCard(rows = commonRows) }
        }
        val tgameRows = paramRows(THERMAL_TGAME)
        if (tgameRows.isNotEmpty()) {
            item(key = "tgame") { HyperOsSectionCard(title = "TGAME", rows = tgameRows) }
        }
        bandCurveItems(THERMAL_TGAME, "TGAME")
        simpleCurveItems(THERMAL_TGAME, "TGAME")
        val mgameRows = paramRows(THERMAL_MGAME)
        if (mgameRows.isNotEmpty()) {
            item(key = "mgame") { HyperOsSectionCard(title = "MGAME", rows = mgameRows) }
        }
        bandCurveItems(THERMAL_MGAME, "MGAME")
        simpleCurveItems(THERMAL_MGAME, "MGAME")
        // 说明：_M 档与基础档分节（方案「温控与帧率」）；songyuan 云控无
        // ovrride_config#90/#120 档（appview 亦不索引），故无档位切换 UI。
    }
}

// ── S3-1b 单档位编辑页（温控与帧率 → 多档曲线 → 某档入口）──────────────────

/**
 * 单个档位的编辑页：每个触发点一张卡（阈值行 + 触发策略行 + 删除），
 * 尾部「添加阈值」。各曲线族按轴与策略不同渲染：
 * 温度轴（帧率/风扇/CPU限频/PID）用滑条，电量轴（by_battery）用数值输入；
 * 策略列：限帧 = 刷新率档下拉，限频 = CPU 频率下拉，PID = 参数串文本编辑。
 * 改动即时序列化整条曲线写回作用域草稿。
 */
@Composable
fun HyperOsBandEditorScreen(pkg: String, curveKey: String, curveLabel: String, bandKey: String) {
    val navigator = LocalNavigator.current
    val viewModel: HyperOsViewModel = viewModel()
    val detail by viewModel.detailState.collectAsStateWithLifecycle()
    val scoped by viewModel.scopedEditorState.collectAsStateWithLifecycle()
    val caps by viewModel.deviceCapsState.collectAsStateWithLifecycle()
    LaunchedEffect(pkg) { viewModel.loadDetail(pkg) }

    // 轴与策略类型（按键名：by_battery=电量轴；cpufreq=温度轴+限频；PID=温度轴+参数串；其余=温度轴+限帧）
    val battery = curveKey.contains("battery")
    val cpufreq = curveKey.contains("cpufreq")
    val pid = curveKey.contains("PID")
    val noun = if (battery) "低电量档" else "温控档"

    val document = remember(scoped.edited) {
        scoped.edited?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    }
    val found = remember(document) { document.findFragment("/game_booster/booster_config/ovrride_config/") }
    val (pointer, fragment) = found ?: (null to null)
    val bands = remember(scoped.edited, curveKey) {
        (fragment?.get(curveKey) as? JsonPrimitive)?.takeIf { it.isString }
            ?.content?.let { parseFpsBands(it) }.orEmpty()
    }
    val band = bands.firstOrNull { it.first == bandKey }
    val currentPrimitive = fragment?.get(curveKey) as? JsonPrimitive

    var batteryTarget by remember { mutableStateOf<EditTarget?>(null) }
    var pidTarget by remember { mutableStateOf<EditTarget?>(null) }
    batteryTarget?.let { t ->
        EditValueDialog(
            title = t.title, initial = t.initial, isNumber = true,
            onCommit = { v ->
                batteryTarget = null
                t.onCommit(v)
            },
            onDismiss = { batteryTarget = null },
        )
    }
    pidTarget?.let { t ->
        EditValueDialog(
            title = t.title, initial = t.initial, isNumber = false,
            onCommit = { v ->
                pidTarget = null
                t.onCommit(v)
            },
            onDismiss = { pidTarget = null },
        )
    }

    fun commit(mutate: (MutableList<FpsBand>) -> Unit) {
        val el = currentPrimitive ?: return
        val ptr = pointer ?: return
        val bs = parseFpsBands(el.content).map { it.first to it.second.toMutableList() }.toMutableList()
        mutate(bs)
        viewModel.updateFragmentValue(ptr, curveKey, el.sameTypePrimitive(formatFpsBands(bs)))
    }

    HyperOsFeatureScaffold(
        title = "$curveLabel · ${bandKey} $noun",
        error = detail.switchError ?: scoped.error,
        loading = detail.loading || scoped.loading,
        onBack = navigator::pop,
    ) {
        if (band == null || pointer == null) {
            item(key = "empty") { HyperOsEmptyHint("档位不存在或配置已变更") }
            return@HyperOsFeatureScaffold
        }
        val options = fpsOptions(caps?.refreshRates.orEmpty(), band.second.map { it.second })
        val cpuFreqs = caps?.cpuFrequencies.orEmpty()
        band.second.forEachIndexed { i, point ->
            item(key = "pt_$i") {
                HyperOsSectionCard(
                    title = "触发点 ${i + 1}",
                    rows = buildList {
                        if (battery) {
                            add {
                                HyperOsValueRow(title = "低电量阈值", value = "${point.first}%") {
                                    batteryTarget = EditTarget(
                                        title = "低电量阈值（%）", initial = point.first, isNumber = true,
                                    ) { v ->
                                        commit { bs ->
                                            val bi = bs.indexOfFirst { it.first == bandKey }
                                            if (bi >= 0) bs[bi].second[i] = v to bs[bi].second[i].second
                                        }
                                    }
                                }
                            }
                        } else {
                            add { BandTempSliderRow(temp = point.first, enabled = true) { t ->
                                commit { bs ->
                                    val bi = bs.indexOfFirst { it.first == bandKey }
                                    if (bi >= 0) bs[bi].second[i] = t to bs[bi].second[i].second
                                }
                            } }
                        }
                        // 策略列：限帧 / 限频 / PID 参数串
                        when {
                            cpufreq -> add {
                                BandFreqDropdownRow(
                                    freq = point.second, cpuFreqs = cpuFreqs, enabled = true,
                                ) { f ->
                                    commit { bs ->
                                        val bi = bs.indexOfFirst { it.first == bandKey }
                                        if (bi >= 0) bs[bi].second[i] = bs[bi].second[i].first to f
                                    }
                                }
                            }
                            pid -> add {
                                HyperOsValueRow(title = "PID 参数", value = point.second) {
                                    pidTarget = EditTarget(
                                        title = "PID 参数串（kP kI kD …，空格分隔）", initial = point.second, isNumber = false,
                                    ) { v ->
                                        commit { bs ->
                                            val bi = bs.indexOfFirst { it.first == bandKey }
                                            if (bi >= 0) bs[bi].second[i] = bs[bi].second[i].first to v
                                        }
                                    }
                                }
                            }
                            else -> add {
                                BandFpsDropdownRow(fps = point.second, options = options, enabled = true) { f ->
                                    commit { bs ->
                                        val bi = bs.indexOfFirst { it.first == bandKey }
                                        if (bi >= 0) bs[bi].second[i] = bs[bi].second[i].first to f
                                    }
                                }
                            }
                        }
                        if (band.second.size > 1) {
                            add {
                                val yDesc = when {
                                    battery -> if (point.second == "0") "不限" else point.second
                                    cpufreq -> freqLabel(point.second)
                                    pid -> point.second
                                    else -> if (point.second == "0") "不限" else point.second
                                }
                                HyperOsActionRow(
                                    title = "删除此触发点",
                                    summary = if (battery) {
                                        "移除「${point.first}% → $yDesc」"
                                    } else {
                                        "移除「${point.first}℃ → $yDesc」"
                                    },
                                    onClick = {
                                        commit { bs ->
                                            val bi = bs.indexOfFirst { it.first == bandKey }
                                            if (bi >= 0 && bs[bi].second.size > 1) bs[bi].second.removeAt(i)
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
        item(key = "add") {
            HyperOsSectionCard(
                rows = listOf<@Composable () -> Unit> {
                    val defaultY = when {
                        cpufreq -> cpuFreqs.lastOrNull()?.toString() ?: "0"
                        pid -> "0 0 0 0 0 0"
                        else -> "60"
                    }
                    HyperOsActionRow(
                        title = if (battery) "添加低电量阈值" else "添加温度阈值",
                        summary = when {
                            battery -> "新增一个「电量 → 限帧」触发点（默认 20% → 60）"
                            cpufreq -> "新增一个「温度 → 限频」触发点（默认 45℃ → 最高频）"
                            pid -> "新增一个「温度 → PID 参数」触发点（默认 45℃ → 全 0）"
                            else -> "新增一个「温度 → 限帧」触发点（默认 45℃ → 60）"
                        },
                        onClick = {
                            commit { bs ->
                                val bi = bs.indexOfFirst { it.first == bandKey }
                                if (bi >= 0) bs[bi].second.add(if (battery) "20" to "60" else "45" to defaultY)
                            }
                        },
                    )
                },
            )
        }
    }
}

// ── S3-1c 单层温控曲线编辑页（温控限帧曲线 dynamic_fps：无档位，直接编辑触发点）──

/**
 * 单层温控曲线编辑页（dynamic_fps / _M）：无「档位」概念，一进入即触发点列表
 * （温控阈值滑条 + 触发后限帧下拉 + 删除），尾部「添加温度阈值」。改动即时序列化写回。
 */
@Composable
fun HyperOsSimpleCurveEditorScreen(pkg: String, curveKey: String, curveLabel: String) {
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
    val points = remember(scoped.edited, curveKey) {
        (fragment?.get(curveKey) as? JsonPrimitive)?.takeIf { it.isString }
            ?.content?.let { parseSimpleCurve(it, CurveCodec.TEMP_FPS) }.orEmpty()
    }
    val currentPrimitive = fragment?.get(curveKey) as? JsonPrimitive

    fun commit(mutate: (MutableList<Pair<String, String>>) -> Unit) {
        val el = currentPrimitive ?: return
        val ptr = pointer ?: return
        val pts = parseSimpleCurve(el.content, CurveCodec.TEMP_FPS).toMutableList()
        mutate(pts)
        viewModel.updateFragmentValue(ptr, curveKey, el.sameTypePrimitive(formatSimpleCurve(pts, CurveCodec.TEMP_FPS)))
    }

    HyperOsFeatureScaffold(
        title = curveLabel,
        error = detail.switchError ?: scoped.error,
        loading = detail.loading || scoped.loading,
        onBack = navigator::pop,
    ) {
        if (pointer == null) {
            item(key = "empty") { HyperOsEmptyHint("配置不存在或已变更") }
            return@HyperOsFeatureScaffold
        }
        val options = fpsOptions(caps?.refreshRates.orEmpty(), points.map { it.second })
        points.forEachIndexed { i, point ->
            item(key = "pt_$i") {
                HyperOsSectionCard(
                    title = "触发点 ${i + 1}",
                    rows = buildList {
                        add { BandTempSliderRow(temp = point.first, enabled = true) { t ->
                            commit { pts -> pts[i] = t to pts[i].second }
                        } }
                        add { BandFpsDropdownRow(fps = point.second, options = options, enabled = true) { f ->
                            commit { pts -> pts[i] = pts[i].first to f }
                        } }
                        if (points.size > 1) {
                            add {
                                HyperOsActionRow(
                                    title = "删除此触发点",
                                    summary = "移除「${point.first}℃ → ${if (point.second == "0") "不限" else point.second}」",
                                    onClick = { commit { pts -> if (pts.size > 1) pts.removeAt(i) } },
                                )
                            }
                        }
                    },
                )
            }
        }
        item(key = "add") {
            HyperOsSectionCard(
                rows = listOf<@Composable () -> Unit> {
                    HyperOsActionRow(
                        title = "添加温度阈值",
                        summary = "新增一个「温度 → 限帧」触发点（默认 45℃ → 60）",
                        onClick = { commit { pts -> pts.add("45" to "60") } },
                    )
                },
            )
        }
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
        error = detail.switchError ?: scoped.error,
        loading = detail.loading || scoped.loading,
        onBack = navigator::pop,
    ) {
        if (pointer == null || fragment == null) {
            item(key = "empty") { HyperOsEmptyHint("该应用无调度/场景配置") }
            return@HyperOsFeatureScaffold
        }

        // ── 本应用参数（ovrride 片段内，per-app）：合并进一张分组卡 ──
        val perAppRows = buildList<@Composable () -> Unit> {
            (fragment["dcs_enable"] as? JsonPrimitive)?.let { sw ->
                if (sw.booleanOrNull != null) {
                    add {
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
                add {
                    HyperOsValueRow(title = "团战场景识别阈值（ms）", value = thresh.content) {
                        intTarget = EditTarget(
                            title = "团战场景识别阈值（ms）",
                            initial = thresh.content,
                            isNumber = !thresh.isString,
                        ) { v -> viewModel.updateFragmentValue(pointer, "group_fight_thresh", thresh.sameTypePrimitive(v)) }
                    }
                }
            }
            (fragment["disable_scenes"] as? JsonPrimitive)?.let { v ->
                add {
                    HyperOsValueRow(title = "禁用场景（逗号 scene_id）", value = v.content) {
                        textTarget = EditTarget("禁用场景", v.content, isNumber = false) { nv ->
                            viewModel.updateFragmentValue(pointer, "disable_scenes", v.sameTypePrimitive(nv))
                        }
                    }
                }
            }
            (fragment["need_game_sdk"] as? JsonPrimitive)?.let { v ->
                add {
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
                add {
                    HyperOsActionRow(
                        title = "首启加速命令（fstb_cmds）",
                        summary = "共 ${((fstb as? JsonArray)?.size ?: 0)} 条 · 只读",
                    )
                }
            }
            if (fragment.containsKey("dcs_config")) {
                add {
                    HyperOsActionRow(
                        title = "DCS 命令表（dcs_config）",
                        summary = "结构复杂，请经高级编辑（JSON）修改",
                    )
                }
            }
        }
        if (perAppRows.isNotEmpty()) {
            item(key = "per_app") { HyperOsSectionCard(title = "本应用参数", rows = perAppRows) }
        }

        // ── 场景管理（scene_ovrride；v2.2 由独立场景命令页并入）──
        scenes.forEach { scene ->
            item(key = "scene_${scene.index}") {
                val sceneRows = buildList<@Composable () -> Unit> {
                    scene.flags.forEach { (flag, value) ->
                        add {
                            HyperOsSwitchRow(
                                title = flag,
                                summary = "场景结构布尔",
                                checked = value,
                                enabled = !scoped.writing,
                            ) { v -> viewModel.updateSceneValue(pointer, scene.index, flag, JsonPrimitive(v)) }
                        }
                    }
                    val rawScene = sceneArray.getOrNull(scene.index) as? JsonObject
                    rawScene?.forEach { (k, v) ->
                        if (k == "timeout" && v is JsonPrimitive) {
                            add {
                                HyperOsValueRow(title = "timeout（定时释放秒数）", value = v.content) {
                                    intTarget = EditTarget("timeout", v.content, isNumber = true) { nv ->
                                        nv.toLongOrNull()?.let { n ->
                                            viewModel.updateSceneValue(pointer, scene.index, k, JsonPrimitive(n))
                                        }
                                    }
                                }
                            }
                        } else if (k == "default_need" && v is JsonPrimitive && v.booleanOrNull != null) {
                            add {
                                HyperOsSwitchRow(
                                    title = "default_need",
                                    summary = "场景结构布尔",
                                    checked = v.boolean,
                                    enabled = !scoped.writing,
                                ) { b -> viewModel.updateSceneValue(pointer, scene.index, k, JsonPrimitive(b)) }
                            }
                        }
                    }
                    // 命令组：end 组不展示（恢复默认态）；perflock 不进功能页（原样透传）
                    scene.containers.filter { !it.key.startsWith("end") }.forEach { container ->
                        container.entries.filter { !it.cmd.startsWith("perflock#") }.forEach { entry ->
                            add {
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
                HyperOsSectionCard(title = scene.sceneNameLabel, rows = sceneRows)
            }
        }

        // 复用映射 + JSON 编辑器入口（结构性增删在作用域编辑器完成）
        item(key = "to_editor") {
            HyperOsSectionCard(rows = listOf<@Composable () -> Unit> {
                HyperOsActionRow(
                    title = "在 JSON 编辑器中打开",
                    summary = "新增/删除场景与命令组条目请走作用域编辑（形状守卫在 CLI）",
                    onClick = { navigator.push(Route.HyperOsScopedEditor(pkg)) },
                )
            })
        }
    }
}

/** 场景命令编辑目标（容器内定位 + 原始 cmd）。 */
private data class SceneCmdTarget(val title: String, val initial: String, val onCommit: (String) -> Unit)

// ── S3-3 插帧超分（Novatek 独显，2026-09-02 v2）─────────────────────────────
//
// 只保留生效路径（novatek_game_params）：FI / SR / FISR 三段，每段用原生下拉
// 直接选「插帧方案」「温度档位」，选中即写回。无自建弹窗、无只读展示行、无冗余小标题。

@Composable
fun HyperOsFisrScreen(pkg: String) {
    val navigator = LocalNavigator.current
    val viewModel: HyperOsViewModel = viewModel()
    val detail by viewModel.detailState.collectAsStateWithLifecycle()
    val scoped by viewModel.scopedEditorState.collectAsStateWithLifecycle()
    LaunchedEffect(pkg) { viewModel.loadDetail(pkg) }

    val document = remember(scoped.edited) {
        scoped.edited?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    }

    val novatekFound = remember(document) { document.findAnyFragment("/game_booster/novatek_game_params/") }
    val novatekRaw = (novatekFound?.second as? JsonPrimitive)?.content
    val novatekEntry = remember(novatekRaw) { novatekRaw?.let { NovatekCodec.parse(it) } }

    // 原始基线：首次读取捕获当前串（此后固定），温度档位一律按「基线 + 档位」取绝对值。
    var baselineEntry by remember(pkg) { mutableStateOf<NovatekCodec.Entry?>(null) }
    LaunchedEffect(novatekRaw) {
        if (novatekRaw != null) {
            val stored = viewModel.novatekBaseline(pkg) ?: novatekRaw.also {
                viewModel.captureNovatekBaseline(pkg, it)
            }
            baselineEntry = NovatekCodec.parse(stored)
        }
    }
    val gexFound = remember(document) {
        document.findAnyFragment("/game_booster/novatek_extend_config/novatek_gex_fps_limit/")
    }
    val nonPlaying = remember(document) {
        document.findAnyFragment("/game_booster/novatek_extend_config/novatek_non_playing_config/")
    }
    val blacklistFound = remember(document) { document.findAnyFragment("/game_booster/novatek_black_app") }

    var textTarget by remember { mutableStateOf<EditTarget?>(null) }
    textTarget?.let { t ->
        EditValueDialog(title = t.title, initial = t.initial, isNumber = false, onCommit = { v ->
            textTarget = null; t.onCommit(v)
        }, onDismiss = { textTarget = null })
    }

    val editable = !scoped.writing

    HyperOsFeatureScaffold(
        title = "插帧超分",
        error = detail.switchError ?: scoped.error,
        loading = detail.loading || scoped.loading,
        onBack = navigator::pop,
    ) {
        if (novatekFound == null) {
            item(key = "empty") { HyperOsEmptyHint("该应用无 Novatek 插帧/超分条目") }
            return@HyperOsFeatureScaffold
        }

        if (novatekEntry == null) {
            item(key = "nt_raw") {
                HyperOsSectionCard(
                    title = "当前条目（格式不识别，只读）",
                    rows = listOf<@Composable () -> Unit> {
                        HyperOsMonoBlockRow(title = "raw", body = novatekRaw.orEmpty())
                    },
                )
            }
        }

        novatekEntry?.let { entry ->
            val ntTarget = novatekFound.first
            fun baseSeg(which: String): NovatekCodec.Segment? = when (which) {
                "FI" -> baselineEntry?.fi
                "SR" -> baselineEntry?.sr
                else -> baselineEntry?.fisr
            }
            // 段整体变换写回（which = FI/SR/FISR）
            fun rewrite(which: String, seg: NovatekCodec.Segment, t: (Int, NovatekCodec.Level) -> NovatekCodec.Level) {
                val ns = NovatekCodec.Segment(seg.levels.mapIndexed { i, lvl -> t(i, lvl) })
                val newEntry = when (which) {
                    "FI" -> entry.copy(fi = ns)
                    "SR" -> entry.copy(sr = ns)
                    else -> entry.copy(fisr = ns)
                }
                viewModel.updateFragmentSelf(ntTarget, JsonPrimitive(NovatekCodec.serialize(newEntry)))
            }
            // 温度档位：绝对值 = 基线同位等级温度 + 档位（幂等，不累加，以文档为唯一真相）
            fun pickTemp(which: String, seg: NovatekCodec.Segment, tier: Int) {
                val base = baseSeg(which) ?: return
                rewrite(which, seg) { i, lvl -> lvl.withTempsFrom(base.levels.getOrNull(i) ?: lvl, tier) }
            }
            item(key = "nt") {
                HyperOsSectionCard(rows = buildList<@Composable () -> Unit> {
                    add {
                        NovatekPresetDropdownRow("插帧方案（FI）", entry.fi, editable) { p ->
                            rewrite("FI", entry.fi) { _, it -> it.withFpsPreset(p) }
                        }
                    }
                    add {
                        NovatekTempDropdownRow("温度档位（FI）", entry.fi.tierDiff(baseSeg("FI")), editable) {
                            pickTemp("FI", entry.fi, it)
                        }
                    }
                    add {
                        NovatekTempDropdownRow("温度档位（SR）", entry.sr.tierDiff(baseSeg("SR")), editable) {
                            pickTemp("SR", entry.sr, it)
                        }
                    }
                    entry.fisr?.let { fs ->
                        add {
                            NovatekPresetDropdownRow("插帧方案（FISR）", fs, editable) { p ->
                                rewrite("FISR", fs) { _, it -> it.withFpsPreset(p) }
                            }
                        }
                        add {
                            NovatekTempDropdownRow("温度档位（FISR）", fs.tierDiff(baseSeg("FISR")), editable) {
                                pickTemp("FISR", fs, it)
                            }
                        }
                    }
                })
            }
        }

        // GEX 帧率上限（pkg:fps）
        (gexFound?.second as? JsonPrimitive)?.let { gex ->
            item(key = "nt_gex") {
                HyperOsSectionCard(rows = listOf<@Composable () -> Unit> {
                    HyperOsValueRow(title = "GEX 帧率上限", value = gex.content) {
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
                })
            }
        }
        // 非游玩档（只读 + 跳 JSON）
        (nonPlaying?.second as? JsonPrimitive)?.let { np ->
            item(key = "nt_non_playing") {
                HyperOsSectionCard(
                    title = "非游玩降级档",
                    rows = listOf<@Composable () -> Unit>(
                        { HyperOsMonoBlockRow(title = "raw", body = np.content) },
                        {
                            HyperOsActionRow(
                                title = "在 JSON 编辑器中修改",
                                summary = "格式与 game_params 相同",
                                onClick = { navigator.push(Route.HyperOsScopedEditor(pkg)) },
                            )
                        },
                    ),
                )
            }
        }
        // 黑名单（仅在名单内时出现片段 → 可移除）
        if (blacklistFound != null) {
            val arr = blacklistFound.second as? JsonArray
            item(key = "nt_blacklist") {
                HyperOsSectionCard(rows = listOf<@Composable () -> Unit> {
                    HyperOsSwitchRow(
                        title = "独显黑名单",
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
                })
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

    // 本地表单（编辑中副本）；「默认即生效」：每次确认修改立即整条 joyose-migt-write 落库
    var form by remember { mutableStateOf<MigtCodec.Pack?>(null) }
    LaunchedEffect(migt.form) { form = migt.form }
    // form 被确认修改后自动保存（写入中由 ViewModel 排队，不丢连改）
    LaunchedEffect(form) {
        val f = form
        if (f != null && f != migt.form) viewModel.saveMigtPack(f)
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
        error = migt.error ?: scoped.error,
        // scoped 片段加载失败（base==null）时不再等 migt.loaded，避免永久转圈
        loading = detail.loading || scoped.loading || (!migt.loaded && scoped.base != null),
        onBack = navigator::pop,
    ) {
        item(key = "membership") {
            HyperOsSectionCard(
                title = "名单成员",
                rows = listOf<@Composable () -> Unit> {
                    HyperOsSwitchRow(
                        title = "启用 migt 帧感知加速",
                        summary = "进出 game_booster.migt 名单（游戏进前台生效）",
                        checked = migt.inList,
                        enabled = !migt.writing,
                    ) { v -> viewModel.toggleMigtMembership(v) }
                },
            )
        }

        if (migt.raw != null && migt.form == null) {
            // 条目存在但格式不识别：只读原始串，避免丢数据
            item(key = "raw") {
                HyperOsSectionCard(
                    title = "当前条目（格式不识别，只读）",
                    rows = listOf<@Composable () -> Unit> {
                        HyperOsMonoBlockRow(title = "raw", body = migt.raw.orEmpty())
                    },
                )
            }
        }

        if (form != null) {
            val f = form!!
            item(key = "sec_form") {
                HyperOsSectionCard(
                    title = "参数包（保存后整条写入）",
                    rows = buildList<@Composable () -> Unit> {
                        add {
                            HyperOsValueRow(title = "migt_freq（每核频率表）", value = f.migtFreq) {
                                curveTarget = CurveTarget("migt_freq", "migt_freq 每核频率", CurveCodec.CPU_FREQ)
                            }
                        }
                        add {
                            HyperOsValueRow(title = "migt_ms（毫秒）", value = f.migtMs?.toString().orEmpty()) {
                                intTarget = EditTarget("migt_ms", f.migtMs?.toString().orEmpty(), isNumber = true) { v ->
                                    v.toLongOrNull()?.let { n -> form = f.copy(migtMs = n) }
                                }
                            }
                        }
                        add {
                            HyperOsValueRow(title = "fps:thresh（帧率阈值表）", value = f.fpsThresh.orEmpty()) {
                                curveTarget = CurveTarget("fps:thresh", "fps:thresh 帧率阈值", CurveCodec.FPS_THRESH)
                            }
                        }
                        add {
                            HyperOsValueRow(title = "boost_policy", value = f.boostPolicy?.toString().orEmpty()) {
                                intTarget = EditTarget("boost_policy", f.boostPolicy?.toString().orEmpty(), isNumber = true) { v ->
                                    v.toLongOrNull()?.let { n -> form = f.copy(boostPolicy = n) }
                                }
                            }
                        }
                        add {
                            HyperOsValueRow(title = "fps_variance_ratio", value = f.fpsVarianceRatio?.toString().orEmpty()) {
                                intTarget = EditTarget("fps_variance_ratio", f.fpsVarianceRatio?.toString().orEmpty(), isNumber = true) { v ->
                                    v.toLongOrNull()?.let { n -> form = f.copy(fpsVarianceRatio = n) }
                                }
                            }
                        }
                        // 字段级显隐：本机内核无该参数则隐藏（songyuan 无 super_task_max_num）
                        if (caps?.hasMigtParam("super_task_max_num") == true) {
                            add {
                                HyperOsValueRow(title = "super_task_max_num", value = f.superTaskMaxNum?.toString().orEmpty()) {
                                    intTarget = EditTarget("super_task_max_num", f.superTaskMaxNum?.toString().orEmpty(), isNumber = true) { v ->
                                        v.toLongOrNull()?.let { n -> form = f.copy(superTaskMaxNum = n) }
                                    }
                                }
                            }
                        } else {
                            add {
                                HyperOsActionRow(title = "super_task_max_num", summary = "本机内核无此参数，不展示编辑")
                            }
                        }
                        add {
                            HyperOsValueRow(
                                title = "migt_ceiling_freq（频率上限表）",
                                value = f.migtCeilingFreq ?: "未设置",
                            ) {
                                curveTarget = CurveTarget("migt_ceiling_freq", "migt_ceiling_freq 频率上限", CurveCodec.CPU_FREQ)
                            }
                        }
                    },
                )
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
        error = detail.switchError ?: scoped.error,
        loading = detail.loading || scoped.loading,
        onBack = navigator::pop,
    ) {
        if (fragment == null) {
            item(key = "empty") { HyperOsEmptyHint("该应用无 self_gpu_tuner_config 配置") }
            return@HyperOsFeatureScaffold
        }

        // v2.2：self_gpu_tuner_enable 是全局开关，只在全局通用配置页展示/编辑
        item(key = "gate_hint") {
            HyperOsSectionCard(rows = listOf<@Composable () -> Unit> {
                HyperOsActionRow(
                    title = "解析门（self_gpu_tuner_enable）",
                    summary = "全局开关，请在「通用配置」页修改；关闭时本配置不解析",
                )
            })
        }

        // profile 逐档键值（值全字符串；生效需下次 profile 保存/游戏重启）
        fragment.keys.forEach { profile ->
            val props = fragment[profile] as? JsonObject ?: return@forEach
            item(key = "profile_$profile") {
                HyperOsSectionCard(
                    title = profile,
                    rows = props.map { (k, v) ->
                        val text = (v as? JsonPrimitive)?.content ?: v.toString()
                        val row: @Composable () -> Unit = {
                            HyperOsValueRow(title = k, value = text) {
                                textTarget = EditTarget(k, text, isNumber = false) { nv ->
                                    viewModel.updateFragmentNested(pointer, listOf(profile, k), JsonPrimitive(nv))
                                }
                            }
                        }
                        row
                    },
                )
            }
        }

        item(key = "mode_hint") {
            HyperOsSectionCard(rows = listOf<@Composable () -> Unit> {
                HyperOsActionRow(
                    title = "TunerMode 档位说明",
                    summary = "STANDARD / HIGH_QUALITY / CUSTOMIZE 等档位在游戏侧 GPU 面板运行时选择；此处编辑各档 profile 键值",
                )
            })
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
        error = detail.switchError ?: scoped.error,
        loading = detail.loading || scoped.loading,
        onBack = navigator::pop,
    ) {
        val (ovPointer, ovFragment) = ovrrideFound ?: (null to null)

        // ovrride 内曲线/映射键：合并进一张分组卡
        val curveRows = DYNRES_CURVES.mapNotNull { p ->
            val current = ovFragment?.get(p.key) ?: return@mapNotNull null
            val text = (current as? JsonPrimitive)?.content ?: return@mapNotNull null
            val fmt = p.format
            @Composable {
                HyperOsValueRow(title = p.label, value = text) {
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
        if (curveRows.isNotEmpty()) {
            item(key = "curves") { HyperOsSectionCard(rows = curveRows) }
        }

        // MIGL 条目（drr / xrender_config / drr_static）
        val (miglPointer, migl) = miglFound ?: (null to null)
        if (migl != null && miglPointer != null) {
            item(key = "sec_migl") { HyperOsSectionLabel("MIGL 条目") }
            val drr = migl["drr"] as? JsonObject
            if (drr != null && drr.isNotEmpty()) {
                item(key = "drr_obj") {
                    HyperOsSectionCard(
                        title = "drr（刷新率→缩放）",
                        rows = drr.map { (k, v) ->
                            val text = (v as? JsonPrimitive)?.content ?: v.toString()
                            val row: @Composable () -> Unit = {
                                HyperOsValueRow(title = k, value = text) {
                                    textTarget = EditTarget(k, text, isNumber = false) { nv ->
                                        viewModel.updateFragmentNested(miglPointer, listOf("drr", k), v.sameTypePrimitive(nv))
                                    }
                                }
                            }
                            row
                        },
                    )
                }
            }
            val xrender = migl["xrender_config"] as? JsonObject
            if (xrender != null && xrender.isNotEmpty()) {
                item(key = "xrender") {
                    HyperOsSectionCard(
                        title = "xrender_config",
                        rows = xrender.map { (k, v) ->
                            val text = (v as? JsonPrimitive)?.content ?: v.toString()
                            val row: @Composable () -> Unit = {
                                HyperOsValueRow(title = k, value = text) {
                                    textTarget = EditTarget(k, text, isNumber = false) { nv ->
                                        viewModel.updateFragmentNested(miglPointer, listOf("xrender_config", k), v.sameTypePrimitive(nv))
                                    }
                                }
                            }
                            row
                        },
                    )
                }
            }
        }

        if (ovFragment == null && migl == null) {
            item(key = "empty") { HyperOsEmptyHint("该应用无动态分辨率相关配置") }
        }
    }
}
