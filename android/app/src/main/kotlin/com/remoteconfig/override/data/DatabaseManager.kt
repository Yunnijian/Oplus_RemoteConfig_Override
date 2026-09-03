package com.remoteconfig.override.data

import android.net.Uri

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.io.File
import java.util.UUID

/** Root shell adapter for the bundled Rust/rusqlite database utility. */
class DatabaseManager(context: Context) {

    data class WriteResult(val success: Boolean, val message: String)

    private val appContext = context.applicationContext

    companion object {
        private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_.]*")
    }

    private val binary: String
        get() = File(appContext.applicationInfo.nativeLibraryDir, "libcosa.so").absolutePath

    private fun quote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private fun run(vararg args: String): Shell.Result {
        val command = buildList {
            add(binary)
            addAll(args.asList())
        }.joinToString(" ", transform = ::quote)
        return Shell.cmd(command).exec()
    }

    /**
     * 合并 stdout/stderr 的全部非空行（而非仅取首行）：Rust CLI 的双库失败报告与
     * “已忽略未知字段”警告均为多行输出，只取首行会丢失后续库的失败详情。
     */
    private fun Shell.Result.message(fallback: String): String =
        (out.asSequence() + err.asSequence())
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("\n")
            .ifEmpty { fallback }

    private fun validPackage(packageName: String): Boolean =
        packageName.length <= 255 && PACKAGE_NAME.matches(packageName)

    fun checkRoot(): Boolean =
        Shell.cmd("id -u").exec().out.any { it.trim() == "0" }

    /**
     * 单次 `list` 同时得出数据库可用性、配置数量与包名列表（原先 checkDatabase /
     * countConfiguredPackages / listConfiguredPackages 各跑一次 shell，现合并为一次）。
     * 成功返回包名列表（可为空表）；失败返回 null（数据库不可访问）。
     */
    fun listConfiguredPackagesOrNull(): List<String>? =
        run("list").let { result ->
            if (result.isSuccess) result.out.map(String::trim).filter(String::isNotEmpty) else null
        }

    fun loadConfig(packageName: String): String? {
        if (!validPackage(packageName)) return null
        return run("read", packageName).let { result ->
            if (!result.isSuccess) null
            else result.out.joinToString("\n").trim().ifEmpty { null }
        }
    }

    /** 通过 SAF Uri 写出 JSON，不经 root、不落公共目录。 */
    fun exportConfig(uri: Uri, json: String): WriteResult {
        return try {
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: return WriteResult(false, "无法打开导出目标")
            WriteResult(true, "已导出")
        } catch (error: Exception) {
            WriteResult(false, "导出失败: ${error.message ?: "写入失败"}")
        }
    }

    fun writeConfig(packageName: String, json: String): WriteResult {
        if (!validPackage(packageName)) return WriteResult(false, "包名格式无效")
        if (json.isBlank()) return WriteResult(false, "JSON 内容为空")
        val temporary = File(appContext.cacheDir, "write-${UUID.randomUUID()}.json")
        return try {
            temporary.writeText(json)
            val result = run("write", packageName, temporary.absolutePath)
            val raw = result.message(if (result.isSuccess) "写入成功" else "写入失败")
            // 新建配置模板只有 package_name 时必命中“没有匹配到任何列”，给用户可读文案。
            val message = if (!result.isSuccess && raw.contains("没有匹配到任何列")) {
                "请至少填写一项配置字段后再写入"
            } else raw
            WriteResult(result.isSuccess, message)
        } catch (error: Exception) {
            WriteResult(false, "写入失败: ${error.message ?: "无法创建临时文件"}")
        } finally {
            temporary.delete()
        }
    }

    fun deleteConfig(packageName: String): WriteResult {
        if (!validPackage(packageName)) return WriteResult(false, "包名格式无效")
        val result = run("delete", packageName)
        return WriteResult(result.isSuccess, result.message(if (result.isSuccess) "已删除" else "删除失败"))
    }

    fun enableProtection(): WriteResult {
        val result = run("protect")
        return WriteResult(result.isSuccess, result.message(if (result.isSuccess) "已启用保护" else "启用失败"))
    }

    /** cosa 版本在应用会话内不变，只经 root shell 查询一次（dumpsys 开销大，且每次写入后刷新均会调用）。 */
    private var cachedCosaVersion: String? = null

    fun getCosaVersion(): String {
        cachedCosaVersion?.let { return it }
        val version = Shell.cmd("dumpsys package com.oplus.cosa | grep versionName | head -1")
            .exec().out.firstOrNull()?.substringAfter('=')?.trim()?.ifEmpty { null } ?: "未知"
        cachedCosaVersion = version
        return version
    }

    fun clearGameData(): WriteResult {
        val result = Shell.cmd("pm clear com.oplus.cosa").exec()
        return if (result.isSuccess) WriteResult(true, "应用增强服务数据已清除")
        else WriteResult(false, result.message("清除失败"))
    }
}
