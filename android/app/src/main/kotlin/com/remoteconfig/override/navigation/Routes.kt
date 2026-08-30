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
}
