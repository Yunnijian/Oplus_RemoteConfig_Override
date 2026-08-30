package com.remoteconfig.override.viewmodel

import java.text.Collator
import java.util.Locale

/**
 * 应用列表排序模型 —— 对齐 KernelSU `SuperUserViewModel` 的 AppSortType/AppSortConfig。
 *
 * 持久化沿用 KernelSU 的打包方式：`sortType.ordinal * 2 + reversed` 存一个 Int。
 */
enum class AppSortType {
    NAME, PACKAGE_NAME, INSTALL_TIME, UPDATE_TIME;

    companion object {
        fun fromOrdinal(ordinal: Int): AppSortType = entries.getOrElse(ordinal) { NAME }
    }
}

data class AppSortConfig(
    val sortType: AppSortType = AppSortType.NAME,
    val reversed: Boolean = false,
) {
    fun toInt(): Int = sortType.ordinal * 2 + if (reversed) 1 else 0

    fun withType(type: AppSortType): AppSortConfig = copy(sortType = type)
    fun toggleReversed(): AppSortConfig = copy(reversed = !reversed)

    companion object {
        fun fromInt(value: Int): AppSortConfig = AppSortConfig(
            sortType = AppSortType.fromOrdinal(value / 2),
            reversed = value % 2 != 0,
        )
    }
}

/**
 * 一行应用及其排序/筛选依据。
 *
 * [installed] = false 表示云控里有该包的配置但设备已卸载（label 退化为包名）；
 * [pinyin] 在 ViewModel 里预计算一次，供"按拼音搜中文名"命中。
 */
data class AppRow(
    val pkg: String,
    val group: String?,
    val features: Int,
    val label: String,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L,
    val installed: Boolean = false,
    val pinyin: String = "",
)

/**
 * 排序实现同 KernelSU `sortGroups`：NAME 走本地化 Collator，其余按自然序，
 * reversed 用 `base.reversed()`。KernelSU 额外的 groupRank 前置分组是 root 授权
 * 语义（allow_su/custom profile），云控列表没有对应概念，因此不搬。
 */
fun sortApps(apps: List<AppRow>, config: AppSortConfig): List<AppRow> {
    val collator = Collator.getInstance(Locale.getDefault())
    val base: Comparator<AppRow> = when (config.sortType) {
        AppSortType.PACKAGE_NAME -> compareBy { it.pkg }
        AppSortType.INSTALL_TIME -> compareBy { it.firstInstallTime }
        AppSortType.UPDATE_TIME -> compareBy { it.lastUpdateTime }
        AppSortType.NAME -> Comparator { a, b -> collator.compare(a.label, b.label) }
    }
    return apps.sortedWith(if (config.reversed) base.reversed() else base)
}
