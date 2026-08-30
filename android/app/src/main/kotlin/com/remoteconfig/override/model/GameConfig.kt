package com.remoteconfig.override.model

/**
 * 数据库中一条现有记录的摘要（用于列表展示）
 */
data class GameConfigSummary(
    val packageName: String,
    val appName: String,
    val hasConfig: Boolean,
    val isInstalled: Boolean = false,
    /** 安装/更新时间（供"安装时间/更新时间"排序；未安装为 0） */
    val installTime: Long = 0L,
    val updateTime: Long = 0L,
    /** appName 的拼音（预计算，供按拼音搜中文名） */
    val pinyin: String = "",
)
