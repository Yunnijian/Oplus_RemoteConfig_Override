package com.remoteconfig.override.ui.util

import android.icu.text.Transliterator

/**
 * 汉字转拼音 —— 逐字移植自 KernelSU `ui/util/PinyinUtil.kt`。
 *
 * 搜索用：列表展示的是中文应用名，用户按拼音（如 "huoxian"）输入时必须能命中，
 * 否则会出现"看着有、搜了没"。行级拼音在 ViewModel 里预计算一次，避免每次击键
 * 都走一遍 Transliterator。
 */
object PinyinUtil {

    private val transliterator: Transliterator by lazy {
        Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower")
    }

    @Synchronized
    fun toPinyin(input: String): String =
        transliterator.transliterate(input).replace(" ", "")
}
