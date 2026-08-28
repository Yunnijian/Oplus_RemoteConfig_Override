package com.remoteconfig.override.settings

/** UI 设计风格。 */
enum class UiMode(val value: Int) {
    Miuix(0),
    Material(1);

    companion object {
        fun fromValue(value: Int): UiMode = entries.find { it.value == value } ?: Miuix
    }
}
