package com.remoteconfig.override.model

/**
 * 数据库中一条现有记录的摘要（用于列表展示）
 */
data class GameConfigSummary(
    val packageName: String,
    val appName: String,
    val hasConfig: Boolean,
    val isInstalled: Boolean = false
)
