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
}
