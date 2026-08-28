package com.remoteconfig.override.ui.screens

/**
 * 包名校验 — 与 DatabaseManager.validPackage 同规则（数据层不动，此处为共享副本）：
 * 以字母开头，仅含字母/数字/下划线/点，长度 ≤255。
 * 新建配置对话框在"创建"时校验，非法时不关闭对话框、不进入编辑器。
 */
private val PACKAGE_NAME_REGEX = Regex("[A-Za-z][A-Za-z0-9_.]*")

fun isValidPackageName(packageName: String): Boolean =
    packageName.length <= 255 && PACKAGE_NAME_REGEX.matches(packageName)
