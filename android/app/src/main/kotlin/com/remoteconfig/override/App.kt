package com.remoteconfig.override

import android.app.Application
import com.topjohnwu.superuser.Shell

/**
 * 应用入口 — 初始化 libsu Shell 容器。
 *
 * libsu 的 Shell 需要在 Application.onCreate 中配置容器模式。
 * 配置后，所有 Shell.cmd() 调用会自动以 Root 身份执行
 * （若设备已 Root 且授权）。
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        configureShell()
    }

    private fun configureShell() {
        Shell.enableVerboseLogging = false      // 关闭详细日志
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)   // 挂载命名空间（用于访问 /data/data）
                .setTimeout(10)
        )
    }
}
