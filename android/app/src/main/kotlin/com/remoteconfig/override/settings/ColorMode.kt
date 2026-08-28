package com.remoteconfig.override.settings

/** 深浅色模式。 */
enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);

    val isDark: Boolean get() = this == DARK

    companion object {
        fun fromValue(value: Int): ColorMode = entries.find { it.value == value } ?: SYSTEM
    }
}
