package com.remoteconfig.override.data

/**
 * 温控/帧率曲线串 ↔ 段列表 双向编解码（功能页 v2 曲线编辑器的数据层）。
 *
 * 统一段模型：每段 = 可空 key（`key#` 前缀，如 `60#10:0 0...` 的 60）+
 * 横轴 x + 纵轴 y（y 可为多参数串，如 PID 的 `43.5 60 30 10 1 2`）。
 * 实机观察：`key#` 前缀仅首段携带、后续段省略（消费端沿用）；解析时按段独立
 * 识别，序列化时 key 非空才输出前缀 → round-trip 保真。
 *
 * 各参数族的 [Format] 见伴生常量；解析失败返回 null（编辑器 gate 拦截保存）。
 */
object CurveCodec {

    enum class AxisKind { INT, DOUBLE, TEXT }

    /** 一段曲线点。 */
    data class Segment(val key: String?, val x: String, val y: String)

    /** 曲线串格式描述（段分隔符 / 档分隔符 / 各列类型 / 列名）。 */
    data class Format(
        val separator: String,
        val xKind: AxisKind,
        val yKind: AxisKind,
        val keyKind: AxisKind?,
        val xLabel: String,
        val yLabel: String,
        val keyLabel: String?,
        val hint: String,
        /** 多档曲线：档与档之间用该分隔符（如 dynamic_targetfps 的 `;`）。null = 单层。 */
        val groupSeparator: String? = null,
    ) {
        val hasKey: Boolean get() = keyKind != null
        val isGrouped: Boolean get() = groupSeparator != null
    }

    fun parse(text: String, format: Format): List<Segment>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val segments = ArrayList<Segment>()
        for (rawSeg in trimmed.split(format.separator)) {
            val seg = rawSeg.trim()
            if (seg.isEmpty()) return null
            // 多档格式：`;` 分隔档，档分隔符之后不能拆到 x/y —— 先按档切开
            if (format.isGrouped) {
                // 段内可能粘着档分隔符（如 `47.5:60;165#10:0`）：整条先按 separator 拆会把它拆坏，
                // 因此多档解析改为逐档处理 —— 见 parseGrouped
                segments.clear()
                return parseGrouped(trimmed, format)
            }
            val parsed = parseSegment(seg, format) ?: return null
            segments.add(parsed)
        }
        return segments
    }

    /** 多档解析：先按 [Format.groupSeparator] 分档，每档内部再按 [Format.separator] 分行。 */
    private fun parseGrouped(text: String, format: Format): List<Segment>? {
        val groupSep = format.groupSeparator ?: return null
        val segments = ArrayList<Segment>()
        for (group in text.split(groupSep)) {
            val groupTrimmed = group.trim()
            if (groupTrimmed.isEmpty()) return null
            for (rawSeg in groupTrimmed.split(format.separator)) {
                val parsed = parseSegment(rawSeg.trim(), format) ?: return null
                segments.add(parsed)
            }
        }
        return segments
    }

    private fun parseSegment(seg: String, format: Format): Segment? {
        if (seg.isEmpty()) return null
        var body = seg
        var key: String? = null
        if (format.hasKey) {
            val idx = seg.indexOf('#')
            if (idx >= 0) {
                key = seg.substring(0, idx).trim()
                if (key.isEmpty()) return null
                body = seg.substring(idx + 1).trim()
            }
        }
        val colon = body.indexOf(':')
        if (colon <= 0 || colon == body.length - 1) return null
        val x = body.substring(0, colon).trim()
        val y = body.substring(colon + 1).trim()
        if (x.isEmpty() || y.isEmpty()) return null
        return Segment(key, x, y)
    }

    fun format(segments: List<Segment>, format: Format): String {
        if (!format.isGrouped) {
            return segments.joinToString(format.separator) { seg ->
                val prefix = seg.key?.takeIf { format.hasKey }?.let { "$it#" } ?: ""
                "$prefix${seg.x}:${seg.y}"
            }
        }
        // 多档：把"有 key 的段"视为档首 —— 档首前插 groupSeparator（首档除外），
        // 档内后续段仍用 separator 分隔
        val sb = StringBuilder()
        var first = true
        segments.forEach { seg ->
            if (!first) {
                sb.append(if (seg.key != null) format.groupSeparator else format.separator)
            }
            if (seg.key != null) {
                sb.append(seg.key).append('#')
            }
            sb.append(seg.x).append(':').append(seg.y)
            first = false
        }
        return sb.toString()
    }

    /** 逐段校验：null = 通过，否则为可直接展示的错误文案。 */
    fun validate(segments: List<Segment>, format: Format): String? {
        segments.forEachIndexed { i, seg ->
            val label = "第 ${i + 1} 行"
            seg.key?.let { key ->
                if (!kindOk(key, format.keyKind)) return "$label${format.keyLabel}「$key」不是有效数字"
            }
            if (!kindOk(seg.x, format.xKind)) return "$label${format.xLabel}「${seg.x}」不是有效数字"
            if (!kindOk(seg.y, format.yKind)) return "$label${format.yLabel}「${seg.y}」不是有效数字"
        }
        return null
    }

    private fun kindOk(value: String, kind: AxisKind?): Boolean = when (kind) {
        null -> true
        AxisKind.INT -> value.toLongOrNull() != null
        AxisKind.DOUBLE -> value.toDoubleOrNull() != null
        AxisKind.TEXT -> value.isNotBlank()
    }

    // ── 参数族格式常量（对照方案第一章参数总表）──────────────────

    /** `温度:fps,...`（dynamic_fps / _M / _multiWin / migl_dr_by_temp）。 */
    val TEMP_FPS = Format(
        separator = ",", xKind = AxisKind.DOUBLE, yKind = AxisKind.INT, keyKind = null,
        xLabel = "温度", yLabel = "帧率", keyLabel = null,
        hint = "温度:帧率，逗号分隔（如 43.5:60,45:40）",
    )

    /** `温度:功率,...`（monitor_power）。 */
    val TEMP_POWER = Format(
        separator = ",", xKind = AxisKind.DOUBLE, yKind = AxisKind.DOUBLE, keyKind = null,
        xLabel = "温度", yLabel = "功率", keyLabel = null,
        hint = "温度:功率，逗号分隔（如 42:4.5,45:5）",
    )

    /**
     * `fps#温度:参数串,...`（PID_*）：单档，key=fps 档，档内按温度拆行。
     * 例：`60#10:0 0 0 0 0 0,42.5:43.5 60 30 10 1 2`
     */
    val FPS_TEMP_PARAM = Format(
        separator = ",", xKind = AxisKind.DOUBLE, yKind = AxisKind.TEXT, keyKind = AxisKind.INT,
        xLabel = "温度", yLabel = "参数", keyLabel = "fps",
        hint = "fps#温度:参数，逗号分隔；key 前缀仅首段需要（如 60#10:0 0 0,42.5:43.5 60 30）",
    )

    /**
     * `fps#温度:档位fps;fps#温度:档位fps;...`（dynamic_targetfps / _M /
     * dynamic_fan_targetfps / _M）：多档，`;` 分档，每档以 targetFps 开头。
     * 例：`165#10:0,45:120,47:90;120#10:0,45:90,47:60`
     */
    val FPS_TARGET_BAND = Format(
        separator = ",", xKind = AxisKind.DOUBLE, yKind = AxisKind.INT, keyKind = AxisKind.INT,
        xLabel = "温度", yLabel = "帧率", keyLabel = "档位fps",
        groupSeparator = ";",
        hint = "每档「档位fps#温度:帧率,温度:帧率…」，档间用 ; 分隔（如 165#10:0,45:120;120#10:0,45:90）",
    )

    /** `fps#socLevel:limitFps,...`（dynamicfps_by_battery_T/_M）。 */
    val FPS_SOC_FPS = Format(
        separator = ",", xKind = AxisKind.INT, yKind = AxisKind.INT, keyKind = AxisKind.INT,
        xLabel = "电量", yLabel = "限帧", keyLabel = "fps",
        hint = "fps#电量:限帧，逗号分隔；限帧 0 = 解除（如 60#1:45,20:0）",
    )

    /**
     * `温度#fps:频率;温度#fps:频率;...`（dynamic_targetfps_cpufreq / _speedmode / _M）：
     * 多档，`;` 分档，每档以温度开头。例：`43.5#60:1804800;45#60:1555200`
     */
    val TEMP_FPS_FREQ = Format(
        separator = ",", xKind = AxisKind.INT, yKind = AxisKind.INT, keyKind = AxisKind.DOUBLE,
        xLabel = "fps", yLabel = "频率", keyLabel = "温度",
        groupSeparator = ";",
        hint = "每档「温度#fps:频率(Hz)…」，档间用 ; 分隔（如 43.5#60:1804800;45#60:1555200）",
    )

    /** `fps:thresh ...`（migt 参数包第 3 段，空格分隔）。 */
    val FPS_THRESH = Format(
        separator = " ", xKind = AxisKind.INT, yKind = AxisKind.INT, keyKind = null,
        xLabel = "fps", yLabel = "阈值", keyLabel = null,
        hint = "fps:阈值，空格分隔（如 90:15 60:18 40:30）",
    )

    /** `cpu:频率 ...`（migt_freq / migt_ceiling_freq 映射表，空格分隔）。 */
    val CPU_FREQ = Format(
        separator = " ", xKind = AxisKind.INT, yKind = AxisKind.INT, keyKind = null,
        xLabel = "CPU", yLabel = "频率", keyLabel = null,
        hint = "cpu编号:频率(Hz)，空格分隔（如 0:384000 6:1017600）",
    )

    /** `sceneId:scale,...`（dsar，分隔符未知 → TEXT 宽容）。 */
    val SCENE_SCALE = Format(
        separator = ",", xKind = AxisKind.INT, yKind = AxisKind.TEXT, keyKind = null,
        xLabel = "场景ID", yLabel = "缩放", keyLabel = null,
        hint = "场景ID:缩放，逗号分隔",
    )

    /** `refreshRate#fps:scale,...`（drr / migl_dr_by_RR）。 */
    val RR_FPS_SCALE = Format(
        separator = ",", xKind = AxisKind.INT, yKind = AxisKind.TEXT, keyKind = AxisKind.INT,
        xLabel = "fps", yLabel = "缩放", keyLabel = "刷新率",
        hint = "刷新率#fps:缩放，逗号分隔（如 120#60:0.8）",
    )
}
