package com.remoteconfig.override.data

/**
 * 场景命令（{permission, cmd} 元素）解析与序列化（方案第五章 5.2/5.5）。
 *
 * 编辑范围排除（用户裁定，硬性）：
 * - `perflock#` 类命令不进功能页：调用方直接过滤，本编码器不解析；
 * - `end` 命令组整组不展示（由调用方按容器 key 过滤）；
 * - 本编码器只处理纳入范围的命令：perfhint / setprop / glk / 裸路径透传。
 *
 * 解析失败返回 [Parsed.Raw]（原文模式兜底，绝不丢数据）。
 */
object CmdCodec {

    /** 19 槽位词表（文档 5.4，control/j.E() 匹配规则：段值不在词表 → key 永不命中）。 */
    val KEY_VOCAB: List<List<String>> = listOf(
        // 1 FPS
        listOf("20", "24", "25", "30", "40", "45", "50", "60", "90", "120", "144", "165", "185"),
        // 2 游戏类型
        listOf("TGAME", "MGAME", "CGAME"),
        // 3 性能模式
        listOf("SPEEDON", "SPEEDOFF"),
        // 4 HDR
        listOf("HDR", "HDROFF"),
        // 5 RAM
        listOf("8G", "12G", "16G"),
        // 6 GPUTunerMode
        listOf("POWERSAVE", "BALANCE", "STANDARD", "CUSTOMIZE", "HIGH_QUALITY"),
        // 7 增强模式
        listOf("ED", "FI", "SR", "SP", "FISR"),
        // 8 多任务复合（含运行时别名 video）
        listOf("leave", "GameAndWechat", "GameAndCall", "video"),
        // 9 画质 / 10 DDR / 11 密度 / 12 YS_RE / 13 VK|GL / 14 MISR / 15 YSRS / 16 FOLD —— 自由值
        emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        // 17 对局规模 / 18 P2P / 19 FAN —— 自由值
        emptyList(), emptyList(), emptyList(),
    )

    sealed class Parsed {
        /** `perfhint#<hint>_<userData>_<data1>#<data3>`（实机 hint 仅 0x1401/0x1095）。 */
        data class PerfHint(val hint: String, val userData: String, val data1: String, val data3: String) : Parsed()

        /** `setprop#<k>#<v>[#<k2>#<v2>...]`（奇数段 ≥3，两两成对）。 */
        data class SetProp(val pairs: List<Pair<String, String>>) : Parsed()

        /** `glk#<占位符>#<值>;...`（占位符段可枚举，值域自由文本）。 */
        data class Glk(val segments: List<Pair<String, String>>) : Parsed()

        /** 透传：裸 sysfs 路径命令 / raf / sw / DDR / vsf / rebind_tid / motor / scx_daemon / 未知前缀。 */
        data class Raw(val text: String) : Parsed()
    }

    /** glk 占位符 → migt/调度节点映射（control/d.java:54-82）。 */
    val GLK_PLACEHOLDERS = listOf("MM", "MA", "WA", "BI", "RE", "BE", "MF", "BP", "GD", "RM", "FR", "MS", "SCN", "FPM")

    /** perfhint hint 枚举（实机统计）。 */
    val PERF_HINTS = listOf("0x1401", "0x1095")

    fun parse(cmd: String): Parsed = when {
        cmd.startsWith("perfhint#") -> parsePerfHint(cmd)
        cmd.startsWith("setprop#") -> parseSetProp(cmd)
        cmd.startsWith("glk#") -> parseGlk(cmd)
        else -> Parsed.Raw(cmd)
    }

    fun serialize(p: Parsed): String = when (p) {
        is Parsed.PerfHint -> "perfhint#${p.hint}_${p.userData}_${p.data1}#${p.data3}"
        is Parsed.SetProp ->
            if (p.pairs.isEmpty()) "" else "setprop#" + p.pairs.joinToString("#") { "${it.first}#${it.second}" }
        is Parsed.Glk -> "glk#" + p.segments.joinToString(";") { "${it.first}#${it.second}" }
        is Parsed.Raw -> p.text
    }

    private fun parsePerfHint(cmd: String): Parsed {
        // perfhint#<hint>_<userData>_<data1>#<data3>
        val body = cmd.removePrefix("perfhint#")
        val hash = body.lastIndexOf('#')
        if (hash <= 0) return Parsed.Raw(cmd)
        val head = body.substring(0, hash)
        val data3 = body.substring(hash + 1)
        val parts = head.split('_')
        if (parts.size != 3) return Parsed.Raw(cmd)
        return Parsed.PerfHint(parts[0], parts[1], parts[2], data3)
    }

    private fun parseSetProp(cmd: String): Parsed {
        val segs = cmd.removePrefix("setprop#").split('#')
        if (segs.size < 2 || segs.size % 2 != 0) return Parsed.Raw(cmd)
        val pairs = ArrayList<Pair<String, String>>(segs.size / 2)
        var i = 0
        while (i + 1 < segs.size) {
            pairs.add(segs[i] to segs[i + 1])
            i += 2
        }
        return Parsed.SetProp(pairs)
    }

    private fun parseGlk(cmd: String): Parsed {
        // glk#MM#值;MA#值;...（段间 `;`，段内 `#`；容忍尾分号）
        val body = cmd.removePrefix("glk#").trimEnd(';')
        if (body.isEmpty()) return Parsed.Raw(cmd)
        val segments = ArrayList<Pair<String, String>>()
        body.split(';').forEach { seg ->
            val idx = seg.indexOf('#')
            if (idx <= 0 || idx == seg.length - 1) return Parsed.Raw(cmd)
            segments.add(seg.substring(0, idx) to seg.substring(idx + 1))
        }
        return Parsed.Glk(segments)
    }

    /**
     * 命令组 key 校验（V2 最长全段命中）：前缀 booster/end + 槽位段。
     * 槽位词表为空的槽位允许任意值（画质/DDR/密度等自由值槽）。
     */
    fun validateKey(key: String): String? {
        if (key.isBlank()) return "key 不能为空"
        val segs = key.split('#')
        if (segs[0] != "booster" && segs[0] != "end") return "key 必须以 booster/end 开头"
        segs.drop(1).forEachIndexed { i, seg ->
            if (seg.isBlank()) return "key 第 ${i + 2} 段为空"
            val vocab = KEY_VOCAB.getOrNull(i)
            if (vocab != null && vocab.isNotEmpty() && seg !in vocab) {
                return "key 第 ${i + 2} 段「$seg」不在词表内（该 key 永不命中）"
            }
        }
        if (segs.size - 1 > KEY_VOCAB.size) return "key 段数超过 19 槽位"
        return null
    }
}
