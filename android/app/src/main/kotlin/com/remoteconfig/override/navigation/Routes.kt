package com.remoteconfig.override.navigation

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize

/** Navigation3 类型安全路由键。 */
sealed interface Route : NavKey, Parcelable {
    @Parcelize
    data object Main : Route

    @Parcelize
    data object ColorPalette : Route

    @Parcelize
    data class ConfigEditor(val packageName: String) : Route

    /** HyperOS 应用配置详情（P2.0b 目标屏，暂未接入 entryProvider，后续阶段实现）。 */
    @Parcelize
    data class HyperOsAppDetail(val packageName: String) : Route

    /** HyperOS 通用配置（P2.0b 目标屏，暂未接入 entryProvider，后续阶段实现）。 */
    @Parcelize
    data object HyperOsCommonConfig : Route

    /** HyperOS 高级 JSON 编辑：直接编辑指定配置（cloud_config 名）的 params 文档。 */
    @Parcelize
    data class HyperOsJsonEditor(val configName: String) : Route

    /** HyperOS 作用域编辑：仅编辑指定 App 名下的云控片段（编辑器打开快、作用域隔离）。 */
    @Parcelize
    data class HyperOsScopedEditor(val packageName: String) : Route

    // ── HyperOS 功能页 v2：按功能入口组织的子屏（共享同一份作用域草稿）──

    @Parcelize
    data class HyperOsPerfSchedule(val packageName: String) : Route

    @Parcelize
    data class HyperOsThermalFps(val packageName: String) : Route

    @Parcelize
    data class HyperOsFisr(val packageName: String) : Route

    @Parcelize
    data class HyperOsDynRes(val packageName: String) : Route

    @Parcelize
    data class HyperOsGpuTuner(val packageName: String) : Route

    @Parcelize
    data class HyperOsMigt(val packageName: String) : Route
}
