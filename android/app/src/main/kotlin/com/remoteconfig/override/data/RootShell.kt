package com.remoteconfig.override.data

import com.topjohnwu.superuser.Shell
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * root 命令的统一结果（P2-26）。
 *
 * 字段名与 `Shell.Result` 在 Kotlin 侧的属性名逐一对应（code / out / err /
 * isSuccess），因此把两个 Manager 的返回类型换掉不影响任何调用点。
 */
data class RootResult(
    val code: Int,
    val out: List<String>,
    val err: List<String>,
) {
    val isSuccess: Boolean get() = code == 0

    companion object {
        const val TIMEOUT_MESSAGE = "root 命令超时，本次操作结果未知，请刷新后确认"

        /** 看门狗到点：命令仍在 libsu 队列里跑，结果未知。 */
        val TIMEOUT = RootResult(code = -1, out = emptyList(), err = listOf(TIMEOUT_MESSAGE))

        internal fun of(result: Shell.Result): RootResult =
            RootResult(result.code, result.out, result.err)
    }
}

/**
 * root 命令看门狗时长。
 *
 * 必须**显著大于**正常耗时：超时只是放弃等待，libsu 无法中断已提交的命令
 * （`ResultFuture.cancel()` 连入参都忽略，只读 latch 计数），命令仍会跑完。
 * 把超时定得太短就会出现"UI 报未知、库里其实已改"的错位。
 * 当前 CLI 最重的路径也受 Rust 侧 `busy_timeout(3s)` 约束，秒级即返回。
 */
private const val ROOT_TIMEOUT_SECONDS = 30L

/**
 * 带看门狗地执行一条 root 命令（P2-26）。
 *
 * libsu 6.0.0 里**没有**"每条命令超时"这回事：`Shell.cmd().exec()` 走
 * `ShellImpl.JobTask.run()` 中**无参**的 `FutureTask.get()`，命令一挂就永久挂。
 * `Shell.Builder.setTimeout()` 只管创建 shell 时的 `shellCheck()`，与命令无关
 * （见 `App.configureShell()` 注释）。上层一律 `withContext(Dispatchers.IO)`，
 * 于是挂死会把 `_isWriting` / `_isLoading` 一起永久钉住 —— 写入按钮再也点不动、
 * 列表永久转圈，而且 `viewModelScope` 取消也打断不了那个阻塞调用。
 *
 * `Shell.Job.enqueue()` 返回 `Future<Shell.Result>`，于是能复用 libsu 自己的任务
 * 队列加超时，不必为每次调用额外占一个线程。返回 [RootResult.TIMEOUT] 而不是抛异常，
 * 让调用方原有的"失败即展示 message"路径自然接管，状态位照常复位。
 */
internal fun execRoot(command: String): RootResult =
    try {
        RootResult.of(
            Shell.cmd(command).enqueue().get(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        )
    } catch (timeout: TimeoutException) {
        RootResult.TIMEOUT
    } catch (interrupted: InterruptedException) {
        // 调用线程被中断（如作用域取消）：还原中断标记，按"结果未知"上报
        Thread.currentThread().interrupt()
        RootResult.TIMEOUT
    } catch (failed: ExecutionException) {
        RootResult(
            code = -1,
            out = emptyList(),
            err = listOf(failed.cause?.message ?: failed.message ?: "root 命令执行失败"),
        )
    }
