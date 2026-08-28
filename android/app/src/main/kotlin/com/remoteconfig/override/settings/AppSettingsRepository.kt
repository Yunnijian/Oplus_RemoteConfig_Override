package com.remoteconfig.override.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 应用设置仓库 — SharedPreferences 持久化 + Compose 可观察状态。
 * 在 App.onCreate 中 init；UI 直接读取属性即可响应变更。
 */
object AppSettingsRepository {
    private const val PREFS = "ui_settings"

    private val _uiMode = mutableStateOf(UiMode.Miuix)
    private val _colorMode = mutableStateOf(ColorMode.SYSTEM)
    private val _enableMonet = mutableStateOf(false)
    private val _enableGlass = mutableStateOf(true)
    private val _enableGlassBlur = mutableStateOf(true)
    private val _keyColor = mutableStateOf(0)
    private val _paletteStyle = mutableStateOf("TonalSpot")
    private val _colorSpec = mutableStateOf("SPEC_2025")

    val uiMode: UiMode get() = _uiMode.value
    val colorMode: ColorMode get() = _colorMode.value
    val enableMonet: Boolean get() = _enableMonet.value
    val enableGlass: Boolean get() = _enableGlass.value
    val enableGlassBlur: Boolean get() = _enableGlassBlur.value
    val keyColor: Int get() = _keyColor.value
    val paletteStyle: String get() = _paletteStyle.value
    val colorSpec: String get() = _colorSpec.value

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _uiMode.value = UiMode.fromValue(p.getInt("ui_mode", UiMode.Miuix.value))
        _colorMode.value = ColorMode.fromValue(p.getInt("color_mode", ColorMode.SYSTEM.value))
        _enableMonet.value = p.getBoolean("enable_monet", false)
        _enableGlass.value = p.getBoolean("enable_glass", true)
        _enableGlassBlur.value = p.getBoolean("enable_glass_blur", true)
        _keyColor.value = p.getInt("key_color", 0)
        _paletteStyle.value = p.getString("palette_style", "TonalSpot") ?: "TonalSpot"
        _colorSpec.value = p.getString("color_spec", "SPEC_2025") ?: "SPEC_2025"
    }

    fun setUiMode(mode: UiMode) {
        _uiMode.value = mode
        prefs?.edit()?.putInt("ui_mode", mode.value)?.apply()
    }

    fun setColorMode(mode: ColorMode) {
        _colorMode.value = mode
        prefs?.edit()?.putInt("color_mode", mode.value)?.apply()
    }

    fun setEnableMonet(enabled: Boolean) {
        _enableMonet.value = enabled
        prefs?.edit()?.putBoolean("enable_monet", enabled)?.apply()
    }

    fun setEnableGlass(enabled: Boolean) {
        _enableGlass.value = enabled
        prefs?.edit()?.putBoolean("enable_glass", enabled)?.apply()
    }

    fun setEnableGlassBlur(enabled: Boolean) {
        _enableGlassBlur.value = enabled
        prefs?.edit()?.putBoolean("enable_glass_blur", enabled)?.apply()
    }

    fun setKeyColor(color: Int) {
        _keyColor.value = color
        prefs?.edit()?.putInt("key_color", color)?.apply()
    }

    fun setPaletteStyle(style: String) {
        _paletteStyle.value = style
        prefs?.edit()?.putString("palette_style", style)?.apply()
    }

    fun setColorSpec(spec: String) {
        _colorSpec.value = spec
        prefs?.edit()?.putString("color_spec", spec)?.apply()
    }
}
