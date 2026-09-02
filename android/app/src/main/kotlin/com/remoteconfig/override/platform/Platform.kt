package com.remoteconfig.override.platform

import com.remoteconfig.override.ui.util.getSystemProperty

/** HyperOS 版本号系统属性 — HyperOS 3.x 实测值 OS3.0（如 OS3.0.0.xxx）。 */
private const val PROP_MI_OS_VERSION = "ro.mi.os.version.name"

/** MIUI/HyperOS 地区系统属性 — HyperOS/MIUI 设备上非空。 */
private const val PROP_MIUI_REGION = "ro.miui.region"

/**
 * 目标平台 — 判定思路对齐 KernelSU OemHelper 的 isColorOS/isHyperOS。
 * 当前支持 ColorOS（原生平台）与 HyperOS（P2.0 起新增）。
 */
enum class Platform {
    ColorOS,
    HyperOS,
}

/**
 * 按系统属性自动检测当前平台：
 * - 主判据：[PROP_MI_OS_VERSION] 以 "OS" 开头 → HyperOS
 *   （HyperOS 3.x 实测值 OS3.0；旧版 MIUI 该值形如 "V14.x" 或为空，不命中）；
 * - 辅判据：[PROP_MIUI_REGION] 非空 → HyperOS（部分机型主判据属性缺失时兜底）；
 * - 其余（含反射读取失败返回空串）→ ColorOS（本项目原生支持平台，兜底）。
 */
fun detectPlatform(): Platform {
    if (getSystemProperty(PROP_MI_OS_VERSION).startsWith("OS")) return Platform.HyperOS
    if (getSystemProperty(PROP_MIUI_REGION).isNotEmpty()) return Platform.HyperOS
    return Platform.ColorOS
}

/**
 * 按用户设置解析生效平台：
 * - "coloros" / "hyperos" → 强制指定平台；
 * - "auto" 及其他未知值 → [detected]（跟随自动检测结果）。
 */
fun resolvePlatform(mode: String, detected: Platform): Platform = when (mode) {
    "coloros" -> Platform.ColorOS
    "hyperos" -> Platform.HyperOS
    else -> detected
}

/** 应用显示名：ColorOS 平台「Color云控修改」，HyperOS 平台「Hyper 云控修改」。 */
fun Platform.appDisplayName(): String = when (this) {
    Platform.ColorOS -> "Color云控修改"
    Platform.HyperOS -> "Hyper 云控修改"
}
