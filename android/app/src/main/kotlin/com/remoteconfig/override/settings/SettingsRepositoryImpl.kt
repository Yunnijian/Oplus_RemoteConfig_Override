package com.remoteconfig.override.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

/** 全局 application context（对齐 KernelSU 的 ksuApp），由 App.onCreate 调用 [initSettingsRepository] 初始化。 */
private lateinit var settingsAppContext: Context

/**
 * 初始化全局 context。App.onCreate 中调用，之后任意位置可无参构造 [SettingsRepositoryImpl]。
 */
fun initSettingsRepository(context: Context) {
    settingsAppContext = context.applicationContext
}

/**
 * SharedPreferences 实现 — 对齐 KernelSU SettingsRepositoryImpl，只保留主题相关字段。
 * Prefs 名与键名均与 KernelSU 一致（ui_mode / color_mode / key_color / color_style / color_spec /
 * enable_blur / enable_floating_bottom_bar / enable_floating_bottom_bar_blur / miuix_monet）。
 */
class SettingsRepositoryImpl(
    context: Context = settingsAppContext,
) : SettingsRepository {

    /** 供外部（如 MainActivity）注册 SharedPreferences 监听以驱动主题即时响应。 */
    val prefs: SharedPreferences = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    override var uiMode: String
        get() = prefs.getString(KEY_UI_MODE, UiMode.DEFAULT_VALUE) ?: UiMode.DEFAULT_VALUE
        set(value) = prefs.edit { putString(KEY_UI_MODE, value) }

    override var themeMode: Int
        get() = prefs.getInt(KEY_COLOR_MODE, 0)
        set(value) = prefs.edit { putInt(KEY_COLOR_MODE, value) }

    override var miuixMonet: Boolean
        get() = prefs.getBoolean(KEY_MIUIX_MONET, false)
        set(value) = prefs.edit { putBoolean(KEY_MIUIX_MONET, value) }

    override var keyColor: Int
        get() = prefs.getInt(KEY_KEY_COLOR, 0)
        set(value) = prefs.edit { putInt(KEY_KEY_COLOR, value) }

    override var colorStyle: String
        get() = prefs.getString(KEY_COLOR_STYLE, PaletteStyle.TonalSpot.name) ?: PaletteStyle.TonalSpot.name
        set(value) = prefs.edit { putString(KEY_COLOR_STYLE, value) }

    override var colorSpec: String
        get() = prefs.getString(KEY_COLOR_SPEC, ColorSpec.SpecVersion.SPEC_2025.name) ?: ColorSpec.SpecVersion.SPEC_2025.name
        set(value) = prefs.edit { putString(KEY_COLOR_SPEC, value) }

    override var enableBlur: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_BLUR, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLE_BLUR, value) }

    override var enableFloatingBottomBar: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_FLOATING_BOTTOM_BAR, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLE_FLOATING_BOTTOM_BAR, value) }

    override var enableFloatingBottomBarBlur: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_FLOATING_BOTTOM_BAR_BLUR, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLE_FLOATING_BOTTOM_BAR_BLUR, value) }

    private companion object {
        const val SETTINGS_PREFS = "settings"
        const val KEY_UI_MODE = "ui_mode"
        const val KEY_COLOR_MODE = "color_mode"
        const val KEY_MIUIX_MONET = "miuix_monet"
        const val KEY_KEY_COLOR = "key_color"
        const val KEY_COLOR_STYLE = "color_style"
        const val KEY_COLOR_SPEC = "color_spec"
        const val KEY_ENABLE_BLUR = "enable_blur"
        const val KEY_ENABLE_FLOATING_BOTTOM_BAR = "enable_floating_bottom_bar"
        const val KEY_ENABLE_FLOATING_BOTTOM_BAR_BLUR = "enable_floating_bottom_bar_blur"
    }
}
