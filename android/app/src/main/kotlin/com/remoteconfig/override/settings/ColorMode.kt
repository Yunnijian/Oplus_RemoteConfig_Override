package com.remoteconfig.override.settings

/**
 * 深浅色 / 动态取色模式 — 对齐 KernelSU ColorMode 六态。
 *
 * 0-2 为非 Monet 三态（跟随系统 / 浅色 / 深色），3-5 为对应 Monet（壁纸取色）三态，
 * 6 为 AMOLED 纯黑深色。辅助属性（isSystem/isDark/isAmoled/isMonet）与
 * toNonMonetMode/toMonetMode 转换完全对齐 KernelSU Theme.kt。
 */
enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    MONET_SYSTEM(3),
    MONET_LIGHT(4),
    MONET_DARK(5),
    DARK_AMOLED(6);

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: SYSTEM
    }

    val isSystem: Boolean get() = value == 0 || value == 3
    val isDark: Boolean get() = value == 2 || value == 5 || value == 6
    val isAmoled: Boolean get() = value == 6
    val isMonet: Boolean get() = value >= 3

    fun toNonMonetMode(): Int = when (this) {
        MONET_SYSTEM -> 0
        MONET_LIGHT -> 1
        MONET_DARK, DARK_AMOLED -> 2
        else -> value
    }

    fun toMonetMode(): Int = when (this) {
        SYSTEM -> 3
        LIGHT -> 4
        DARK -> 5
        else -> value
    }
}
