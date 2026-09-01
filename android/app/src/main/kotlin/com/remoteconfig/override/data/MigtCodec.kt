package com.remoteconfig.override.data

/**
 * migt 条目串编解码（game_booster.migt 数组元素）。
 *
 * 串格式（方案第〇章，实机 8 字段样例）：
 * `包名;migt_freq;migt_ms;fps:thresh表;boost_policy;fps_variance_ratio;super_task_max_num;migt_ceiling_freq`
 * 其中 migt_freq / migt_ceiling_freq 是 `cpu:频率` 空格分隔映射表，fps:thresh 是
 * `fps:阈值` 空格分隔映射表；段数 2..8 不等（可选段从尾部省略）。
 *
 * 解析失败返回 null（调用方原样只读展示，避免丢数据）；序列化按最末非空段截断。
 */
object MigtCodec {

    /** 一个游戏的 migt 参数包（映射表保持原样串，标量为数字）。 */
    data class Pack(
        val pkg: String,
        /** `cpu:频率` 映射表（如 `0:384000 1:384000 ...`）。 */
        val migtFreq: String,
        val migtMs: Long? = null,
        /** `fps:阈值` 映射表（如 `90:15 60:18`）。 */
        val fpsThresh: String? = null,
        val boostPolicy: Long? = null,
        val fpsVarianceRatio: Long? = null,
        val superTaskMaxNum: Long? = null,
        /** `cpu:频率` 上限映射表；全 0 = 无上限。 */
        val migtCeilingFreq: String? = null,
    )

    fun parse(raw: String): Pack? {
        val segs = raw.split(';')
        if (segs.size < 2 || segs.size > 8) return null
        val pkg = segs[0].trim()
        if (pkg.isEmpty() || pkg.any { it.isWhitespace() }) return null
        // 任何空段（含尾部）整条拒绝——与 Rust 侧 validate_entry 口径一致，
        // 避免序列化截断丢数据与两侧判定分歧
        for (i in 2 until segs.size) {
            if (segs[i].trim().isEmpty()) return null
        }
        val freq = segs[1].trim()
        if (!isMappingTable(freq)) return null
        fun long(i: Int): Long? = segs.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()
        var malformed = false
        fun table(i: Int): String? {
            val seg = segs.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            // 段存在但格式不识别 = 整条拒绝（避免静默丢段）
            if (!isMappingTable(seg)) {
                malformed = true
                return null
            }
            return seg
        }
        // 数字/映射段存在但格式不识别 = 整条返回 null（不丢数据）
        for (i in listOf(2, 4, 5, 6)) {
            segs.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                if (it.toLongOrNull() == null) {
                    malformed = true
                }
            }
        }
        val fpsThresh = table(3)
        val ceiling = table(7)
        if (malformed) return null
        return Pack(
            pkg = pkg,
            migtFreq = freq,
            migtMs = long(2),
            fpsThresh = fpsThresh,
            boostPolicy = long(4),
            fpsVarianceRatio = long(5),
            superTaskMaxNum = long(6),
            migtCeilingFreq = ceiling,
        )
    }

    fun serialize(pack: Pack): String = buildString {
        append(pack.pkg)
        append(';')
        append(pack.migtFreq)
        pack.migtMs?.let { append(';').append(it) } ?: return toString()
        pack.fpsThresh?.let { append(';').append(it) } ?: return toString()
        pack.boostPolicy?.let { append(';').append(it) } ?: return toString()
        pack.fpsVarianceRatio?.let { append(';').append(it) } ?: return toString()
        pack.superTaskMaxNum?.let { append(';').append(it) } ?: return toString()
        pack.migtCeilingFreq?.let { append(';').append(it) }
    }

    /** `N:value` 空白分隔映射表校验（与 Rust 侧 split_whitespace 口径一致：连续空白/首尾空白均宽容）。 */
    private fun isMappingTable(text: String): Boolean {
        if (text.isBlank()) return false
        text.split(' ').map { it.trim() }.filter { it.isNotEmpty() }.forEach { pair ->
            val idx = pair.indexOf(':')
            if (idx <= 0 || idx == pair.length - 1) return false
            val left = pair.substring(0, idx)
            val right = pair.substring(idx + 1)
            if (left.toLongOrNull() == null || right.toLongOrNull() == null) return false
        }
        return true
    }
}
