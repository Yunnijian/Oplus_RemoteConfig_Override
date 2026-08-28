package com.remoteconfig.override.settings

/** UI 设计风格 — 对齐 KernelSU UiMode（字符串值 + DEFAULT_VALUE）。 */
enum class UiMode(val value: String) {
    Miuix("miuix"),
    Material("material");

    companion object {
        fun fromValue(value: String): UiMode = when (value) {
            Material.value -> Material
            else -> Miuix
        }

        val DEFAULT_VALUE = Miuix.value
    }
}
