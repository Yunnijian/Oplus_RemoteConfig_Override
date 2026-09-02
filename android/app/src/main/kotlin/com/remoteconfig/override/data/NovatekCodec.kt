package com.remoteconfig.override.data

/**
 * Novatek 独显配置串编解码（game_booster.novatek_game_params 数组元素，方案 1.6）。
 *
 * 串格式（`_` 分隔，4 token；3 token = 旧格式无 FISR 段）：
 * `包名_FI段_SR段_FISR段`
 * 每段 = `|` 分隔的温度等级链，每级 7 字段：
 * `dynamicFps#targetFps#params#tgTh#tgRec#mgTh#mgRec`
 * params = 逗号串，按序映射 17 键（InputFPS..EMVMode；Category 恒 0 由硬件侧补）。
 *
 * 解析失败返回 null（调用方原样只读展示，避免丢数据）；round-trip 保真。
 */
object NovatekCodec {

    /** params 值槽语义（17 键；下发命令时前缀 "Category:0," 由 t.k.c 拼装）。 */
    val PARAM_KEYS = listOf(
        "InputFPS", "TargetFPS", "MEMC", "MEMCMode", "LDSR", "LDSRMode",
        "SDR2HDR", "SDR2HDRMode", "Sharpness", "SharpnessMode", "3DLUT", "3DLUTMode",
        "LDSRV2Mode", "GEX", "GEXMode", "EMV", "EMVMode",
    )

    /** 插帧预制方案（2026-09-02 v2：只改前两组 4 数，全部取自 songyuan 现成样例）。 */
    data class FpsPreset(
        val label: String,
        val dynamicFps: String,
        val targetFps: String,
        val inputFps: String,
        val targetOut: String,
    )

    val FPS_PRESETS = listOf(
        FpsPreset("30-60", "31", "60", "30", "60"),
        FpsPreset("55-165", "55", "165", "55", "165"),
        FpsPreset("83-165", "83", "165", "83", "165"),
        FpsPreset("60-120", "61", "120", "60", "120"),
        FpsPreset("93-185", "93", "185", "93", "185"),
        FpsPreset("73-144", "73", "144", "72", "144"),
    )

    /** 温度档位（℃，相对原始基线的绝对偏移）；0 = 原始（未调整）。 */
    val TEMP_OFFSETS = listOf(0, 10, 20, 30, 40)
    fun tempLabel(v: Int): String = if (v == 0) "原始" else "+${v}℃"

    /** 一个温度等级（7 字段；params 保持原 token 列表）。 */
    data class Level(
        val dynamicFps: String,
        val targetFps: String,
        val params: List<String>,
        val tgTh: String,
        val tgRec: String,
        val mgTh: String,
        val mgRec: String,
    ) {
        /** 插帧方案应用：只改前两组 4 数（dynamicFps#targetFps 与 params 头 InputFPS,TargetFPS），
         *  其余 params（MEMC/MEMCMode/超分等）原样保留。 */
        fun withFpsPreset(p: FpsPreset): Level {
            val newParams = params.toMutableList().also { list ->
                if (list.isNotEmpty()) list[0] = p.inputFps
                if (list.size >= 2) list[1] = p.targetOut
            }
            return copy(dynamicFps = p.dynamicFps, targetFps = p.targetFps, params = newParams)
        }

        /** 温度档位（绝对）：4 个温度字段 = 基线等级同位字段 + offset（保留本级的帧率/params）。
         *  以基线为准取绝对值，幂等、不累加，且始终以文档为唯一真相。 */
        fun withTempsFrom(base: Level, offset: Int): Level = copy(
            tgTh = addTemp(base.tgTh, offset),
            tgRec = addTemp(base.tgRec, offset),
            mgTh = addTemp(base.mgTh, offset),
            mgRec = addTemp(base.mgRec, offset),
        )

        private companion object {
            fun addTemp(v: String, offset: Int): String = v.trim().toDoubleOrNull()?.let {
                val r = it + offset
                if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
            } ?: v
        }
    }

    /** 一段策略链（FI / SR / FISR）。 */
    data class Segment(val levels: List<Level>) {
        /** 相对基线的档位（首级 tgTh 差值，四舍五入到整数℃）；无基线返回 0。 */
        fun tierDiff(base: Segment?): Int {
            val a = levels.firstOrNull()?.tgTh?.toDoubleOrNull() ?: return 0
            val b = base?.levels?.firstOrNull()?.tgTh?.toDoubleOrNull() ?: return 0
            return Math.round(a - b).toInt()
        }
    }

    data class Entry(
        val pkg: String,
        val fi: Segment,
        val sr: Segment,
        /** 旧格式（3 token）无此段。 */
        val fisr: Segment?,
    )

    fun parse(raw: String): Entry? {
        val tokens = raw.split('_')
        if (tokens.size !in 3..4) return null
        val pkg = tokens[0].trim()
        if (pkg.isEmpty() || pkg.contains('#') || pkg.contains('|')) return null
        val fi = parseSegment(tokens[1]) ?: return null
        val sr = parseSegment(tokens[2]) ?: return null
        val fisr = if (tokens.size == 4) parseSegment(tokens[3]) ?: return null else null
        return Entry(pkg, fi, sr, fisr)
    }

    fun serialize(e: Entry): String = buildString {
        append(e.pkg).append('_').append(formatSegment(e.fi))
        append('_').append(formatSegment(e.sr))
        if (e.fisr != null) append('_').append(formatSegment(e.fisr))
    }

    private fun parseSegment(text: String): Segment? {
        if (text.isEmpty()) return null
        val levels = text.split('|').map { lvl ->
            val f = lvl.split('#')
            if (f.size != 7) return null
            Level(f[0], f[1], f[2].split(','), f[3], f[4], f[5], f[6])
        }
        if (levels.isEmpty()) return null
        return Segment(levels)
    }

    private fun formatSegment(s: Segment): String =
        s.levels.joinToString("|") { l ->
            listOf(l.dynamicFps, l.targetFps, l.params.joinToString(","), l.tgTh, l.tgRec, l.mgTh, l.mgRec)
                .joinToString("#")
        }

    /** params token 数 ↔ 17 键名对齐（超出 17 的 token 丢弃语义提示，值仍保真）。 */
    fun paramNames(params: List<String>): List<Pair<String, String>> =
        params.mapIndexed { i, v -> (PARAM_KEYS.getOrNull(i) ?: "param$i") to v }
}
