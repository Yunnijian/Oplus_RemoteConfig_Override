package com.remoteconfig.override.settings

import android.content.Context
import androidx.core.content.edit
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

/** 全局 application context（对齐 KernelSU 的 ksuApp），由 App.onCreate 调用 [initSettingsRepository] 初始化。 */
lateinit var settingsAppContext: Context
    private set

/** 初始化全局 context。App.onCreate 中调用，之后任意位置可无参构造 [SettingsRepositoryImpl]。 */
fun initSettingsRepository(context: Context) {
    settingsAppContext = context.applicationContext
}

/**
 * SharedPreferences 实现 — 完整对齐 KernelSU SettingsRepositoryImpl：
 * **纯 SharedPreferences 直读直写，不含 Compose 快照状态**。
 *
 * 观察模型随之对齐 KernelSU：主题写入不再经快照即时失效整树，
 * 而由 ViewModel 层显式驱动 ——
 * - [com.remoteconfig.override.viewmodel.MainActivityViewModel]：
 *   OnSharedPreferenceChangeListener → StateFlow，根主题/CompositionLocal 更新；
 * - [com.remoteconfig.override.viewmodel.SettingsViewModel]：
 *   StateFlow，取色屏/设置列表自身状态更新。
 *
 * Prefs 名与键名均与 KernelSU 一致（ui_mode / color_mode / key_color / color_style /
 * color_spec / enable_blur / enable_floating_bottom_bar / enable_floating_bottom_bar_blur /
 * miuix_monet / enable_predictive_back / page_scale / enable_navigation_badge /
 * platform_mode）。
 */
class SettingsRepositoryImpl(
    context: Context = settingsAppContext,
) : SettingsRepository {

    private val prefs = context.getSharedPreferences(SETTINGS_PREFS_FILE, Context.MODE_PRIVATE)

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

    override var enablePredictiveBack: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_PREDICTIVE_BACK, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLE_PREDICTIVE_BACK, value) }

    override var pageScale: Float
        get() = prefs.getFloat(KEY_PAGE_SCALE, 1.0f)
        set(value) = prefs.edit { putFloat(KEY_PAGE_SCALE, value) }

    override var enableNavigationBadge: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_NAVIGATION_BADGE, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLE_NAVIGATION_BADGE, value) }

    override var platformMode: String
        get() = prefs.getString(KEY_PLATFORM_MODE, "auto") ?: "auto"
        set(value) = prefs.edit { putString(KEY_PLATFORM_MODE, value) }

    override var hyperOsAppSortOption: Int
        get() = prefs.getInt(KEY_HYPEROS_APP_SORT_OPTION, 0)
        set(value) = prefs.edit { putInt(KEY_HYPEROS_APP_SORT_OPTION, value) }

    override var hyperOsAppShowInstalledOnly: Boolean
        get() = prefs.getBoolean(KEY_HYPEROS_APP_SHOW_INSTALLED_ONLY, false)
        set(value) = prefs.edit { putBoolean(KEY_HYPEROS_APP_SHOW_INSTALLED_ONLY, value) }

    override var colorosAppSortOption: Int
        get() = prefs.getInt(KEY_COLOROS_APP_SORT_OPTION, 0)
        set(value) = prefs.edit { putInt(KEY_COLOROS_APP_SORT_OPTION, value) }

    override var colorosAppShowInstalledOnly: Boolean
        get() = prefs.getBoolean(KEY_COLOROS_APP_SHOW_INSTALLED_ONLY, false)
        set(value) = prefs.edit { putBoolean(KEY_COLOROS_APP_SHOW_INSTALLED_ONLY, value) }

    companion object {
        const val SETTINGS_PREFS_FILE = "settings"
        const val KEY_UI_MODE = "ui_mode"
        const val KEY_COLOR_MODE = "color_mode"
        const val KEY_MIUIX_MONET = "miuix_monet"
        const val KEY_KEY_COLOR = "key_color"
        const val KEY_COLOR_STYLE = "color_style"
        const val KEY_COLOR_SPEC = "color_spec"
        const val KEY_ENABLE_BLUR = "enable_blur"
        const val KEY_ENABLE_FLOATING_BOTTOM_BAR = "enable_floating_bottom_bar"
        const val KEY_ENABLE_FLOATING_BOTTOM_BAR_BLUR = "enable_floating_bottom_bar_blur"
        const val KEY_ENABLE_PREDICTIVE_BACK = "enable_predictive_back"
        const val KEY_PAGE_SCALE = "page_scale"
        const val KEY_ENABLE_NAVIGATION_BADGE = "enable_navigation_badge"
        const val KEY_PLATFORM_MODE = "platform_mode"
        const val KEY_HYPEROS_APP_SORT_OPTION = "hyperos_app_sort_option"
        const val KEY_HYPEROS_APP_SHOW_INSTALLED_ONLY = "hyperos_app_show_installed_only"
        const val KEY_COLOROS_APP_SORT_OPTION = "coloros_app_sort_option"
        const val KEY_COLOROS_APP_SHOW_INSTALLED_ONLY = "coloros_app_show_installed_only"
    }
}
