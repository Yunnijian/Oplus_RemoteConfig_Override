package com.remoteconfig.override.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

/** 全局 application context（对齐 KernelSU 的 ksuApp），由 App.onCreate 调用 [initSettingsRepository] 初始化。 */
private lateinit var settingsAppContext: Context

/**
 * 初始化全局 context。App.onCreate 中调用，之后任意位置可无参构造 [SettingsRepositoryImpl]。
 * 同时从 SharedPreferences 装载 [SettingsStates] 初值（早于任何组合期读取）。
 */
fun initSettingsRepository(context: Context) {
    settingsAppContext = context.applicationContext
    SettingsStates.initFrom(
        settingsAppContext.getSharedPreferences(SettingsStates.SETTINGS_PREFS_FILE, Context.MODE_PRIVATE)
    )
}

/**
 * Compose 快照状态单例 — 设置字段的进程内唯一数据源，所有 [SettingsRepositoryImpl] 实例共享。
 *
 * 读：组合期读取即订阅，任一字段变更即时通知重组（修复取色页下拉/开关「实际已切换、
 * 显示停留旧值」的滞后 — 此前仅 MainActivity 经 prefs 监听 + settingsVersion 手动驱动，
 * NavDisplay 内的页面收不到重组）；
 * 写：先更状态再落盘，组合内不会写入（全部发生在点击回调等事件路径）。
 */
private object SettingsStates {
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

    var uiMode by mutableStateOf(UiMode.DEFAULT_VALUE)
    var themeMode by mutableIntStateOf(0)
    var miuixMonet by mutableStateOf(false)
    var keyColor by mutableIntStateOf(0)
    var colorStyle by mutableStateOf(PaletteStyle.TonalSpot.name)
    var colorSpec by mutableStateOf(ColorSpec.SpecVersion.SPEC_2025.name)
    var enableBlur by mutableStateOf(false)
    var enableFloatingBottomBar by mutableStateOf(false)
    var enableFloatingBottomBarBlur by mutableStateOf(false)
    var enablePredictiveBack by mutableStateOf(false)
    var pageScale by mutableStateOf(1.0f)
    var enableNavigationBadge by mutableStateOf(true)

    @Volatile
    private var initialized = false

    /** 从 prefs 装载初值（幂等；App.onCreate 或首个实例构造时执行）。 */
    fun initFrom(prefs: SharedPreferences) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            uiMode = prefs.getString(KEY_UI_MODE, UiMode.DEFAULT_VALUE) ?: UiMode.DEFAULT_VALUE
            themeMode = prefs.getInt(KEY_COLOR_MODE, 0)
            miuixMonet = prefs.getBoolean(KEY_MIUIX_MONET, false)
            keyColor = prefs.getInt(KEY_KEY_COLOR, 0)
            colorStyle = prefs.getString(KEY_COLOR_STYLE, PaletteStyle.TonalSpot.name)
                ?: PaletteStyle.TonalSpot.name
            colorSpec = prefs.getString(KEY_COLOR_SPEC, ColorSpec.SpecVersion.SPEC_2025.name)
                ?: ColorSpec.SpecVersion.SPEC_2025.name
            enableBlur = prefs.getBoolean(KEY_ENABLE_BLUR, false)
            enableFloatingBottomBar = prefs.getBoolean(KEY_ENABLE_FLOATING_BOTTOM_BAR, false)
            enableFloatingBottomBarBlur = prefs.getBoolean(KEY_ENABLE_FLOATING_BOTTOM_BAR_BLUR, false)
            enablePredictiveBack = prefs.getBoolean(KEY_ENABLE_PREDICTIVE_BACK, false)
            pageScale = prefs.getFloat(KEY_PAGE_SCALE, 1.0f)
            enableNavigationBadge = prefs.getBoolean(KEY_ENABLE_NAVIGATION_BADGE, true)
            initialized = true
        }
    }
}

/**
 * SharedPreferences 实现 — 对齐 KernelSU SettingsRepositoryImpl，只保留主题相关字段。
 *
 * 读取经 [SettingsStates] 快照状态（组合期可观察），写入先更状态再落盘 SharedPreferences。
 * Prefs 名与键名均与 KernelSU 一致（ui_mode / color_mode / key_color / color_style / color_spec /
 * enable_blur / enable_floating_bottom_bar / enable_floating_bottom_bar_blur / miuix_monet /
 * enable_predictive_back / page_scale / enable_navigation_badge）。
 */
class SettingsRepositoryImpl(
    context: Context = settingsAppContext,
) : SettingsRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(SettingsStates.SETTINGS_PREFS_FILE, Context.MODE_PRIVATE)

    init {
        // 防御：正常路径 App.onCreate 已装载；直接构造实例（测试/默认参数）时兜底。
        SettingsStates.initFrom(prefs)
    }

    override var uiMode: String
        get() = SettingsStates.uiMode
        set(value) {
            SettingsStates.uiMode = value
            prefs.edit { putString(SettingsStates.KEY_UI_MODE, value) }
        }

    override var themeMode: Int
        get() = SettingsStates.themeMode
        set(value) {
            SettingsStates.themeMode = value
            prefs.edit { putInt(SettingsStates.KEY_COLOR_MODE, value) }
        }

    override var miuixMonet: Boolean
        get() = SettingsStates.miuixMonet
        set(value) {
            SettingsStates.miuixMonet = value
            prefs.edit { putBoolean(SettingsStates.KEY_MIUIX_MONET, value) }
        }

    override var keyColor: Int
        get() = SettingsStates.keyColor
        set(value) {
            SettingsStates.keyColor = value
            prefs.edit { putInt(SettingsStates.KEY_KEY_COLOR, value) }
        }

    override var colorStyle: String
        get() = SettingsStates.colorStyle
        set(value) {
            SettingsStates.colorStyle = value
            prefs.edit { putString(SettingsStates.KEY_COLOR_STYLE, value) }
        }

    override var colorSpec: String
        get() = SettingsStates.colorSpec
        set(value) {
            SettingsStates.colorSpec = value
            prefs.edit { putString(SettingsStates.KEY_COLOR_SPEC, value) }
        }

    override var enableBlur: Boolean
        get() = SettingsStates.enableBlur
        set(value) {
            SettingsStates.enableBlur = value
            prefs.edit { putBoolean(SettingsStates.KEY_ENABLE_BLUR, value) }
        }

    override var enableFloatingBottomBar: Boolean
        get() = SettingsStates.enableFloatingBottomBar
        set(value) {
            SettingsStates.enableFloatingBottomBar = value
            prefs.edit { putBoolean(SettingsStates.KEY_ENABLE_FLOATING_BOTTOM_BAR, value) }
        }

    override var enableFloatingBottomBarBlur: Boolean
        get() = SettingsStates.enableFloatingBottomBarBlur
        set(value) {
            SettingsStates.enableFloatingBottomBarBlur = value
            prefs.edit { putBoolean(SettingsStates.KEY_ENABLE_FLOATING_BOTTOM_BAR_BLUR, value) }
        }

    override var enablePredictiveBack: Boolean
        get() = SettingsStates.enablePredictiveBack
        set(value) {
            SettingsStates.enablePredictiveBack = value
            prefs.edit { putBoolean(SettingsStates.KEY_ENABLE_PREDICTIVE_BACK, value) }
        }

    override var pageScale: Float
        get() = SettingsStates.pageScale
        set(value) {
            SettingsStates.pageScale = value
            prefs.edit { putFloat(SettingsStates.KEY_PAGE_SCALE, value) }
        }

    override var enableNavigationBadge: Boolean
        get() = SettingsStates.enableNavigationBadge
        set(value) {
            SettingsStates.enableNavigationBadge = value
            prefs.edit { putBoolean(SettingsStates.KEY_ENABLE_NAVIGATION_BADGE, value) }
        }
}
