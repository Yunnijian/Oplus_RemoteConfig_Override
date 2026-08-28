package com.remoteconfig.override.ui.util

import android.annotation.SuppressLint

/**
 * Ported from KernelSU `OemHelper.kt` — only the parts we need.
 * `getSystemProperty` is required by [resolveDeviceName] (DeviceNameHelper.kt).
 * KernelSU additionally exposes `isMiui`/`isHyperOS`/`isColorOS` (used by module
 * shortcut permission flows), which this app does not use.
 */
@SuppressLint("PrivateApi")
internal fun getSystemProperty(key: String): String {
    return try {
        val props = Class.forName("android.os.SystemProperties")
        props.getMethod("get", String::class.java).invoke(null, key) as? String ?: ""
    } catch (_: Throwable) {
        ""
    }
}
