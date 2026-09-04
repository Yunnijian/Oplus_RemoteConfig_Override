package com.remoteconfig.override

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import com.remoteconfig.override.settings.SettingsRepositoryImpl
import com.remoteconfig.override.settings.initSettingsRepository
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
        initSettingsRepository(this)
        // 任务3：预测性返回手势（Android 14+）——反射调用 hidden API
        // setEnableOnBackInvokedCallback，对齐 KernelSU KernelSUApplication.kt。
        // 注意：开关在 App 启动时读取一次，改动后需重启应用生效（KernelSU 同语义）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setEnableOnBackInvokedCallback(applicationInfo, SettingsRepositoryImpl().enablePredictiveBack)
        }
        configureShell()
    }

    /** 反射设置预测性返回回调（hidden API，Android 14+）。 */
    private fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean) {
        runCatching {
            val method = ApplicationInfo::class.java.getDeclaredMethod(
                "setEnableOnBackInvokedCallback",
                Boolean::class.javaPrimitiveType,
            )
            method.isAccessible = true
            method.invoke(appInfo, enable)
        }
    }

    private fun configureShell() {
        Shell.enableVerboseLogging = false      // 关闭详细日志
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)   // 挂载命名空间（用于访问 /data/data）
            // 不调用 setTimeout()：libsu 6.0.0 里该值只用于**创建 root shell 时的可用性检查**
            // （ShellImpl 构造器 FutureTask.get(timeout, SECONDS)），与每条命令无关 ——
            // Shell.cmd().exec() 走 JobTask.run 的无参 FutureTask.get()，没有每命令超时。
            // 原先传 10 秒等于把库默认 20 秒砍半：首次启动若用户未在 10 秒内点掉 su 授权弹窗，
            // shell 创建直接抛 "Shell check timeout"。KernelSU 同样不覆盖此值。
        )
    }
}
