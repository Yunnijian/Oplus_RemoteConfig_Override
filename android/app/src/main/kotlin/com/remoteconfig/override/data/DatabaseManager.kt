package com.remoteconfig.override.data

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.io.File
import java.util.UUID

/** Root shell adapter for the bundled Rust/rusqlite database utility. */
class DatabaseManager(context: Context) {

    data class WriteResult(val success: Boolean, val message: String)

    private val appContext = context.applicationContext

    companion object {
        private const val EXPORT_DIR = "/storage/emulated/0/cosa_json"
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

    fun checkDatabase(): Boolean = run("list").isSuccess

    fun listConfiguredPackages(): List<String> =
        run("list").let { result ->
            if (result.isSuccess) result.out.map(String::trim).filter(String::isNotEmpty) else emptyList()
        }

    fun countConfiguredPackages(): Int = listConfiguredPackages().size

    fun loadConfig(packageName: String): String? {
        if (!validPackage(packageName)) return null
        return run("read", packageName).let { result ->
            if (!result.isSuccess) null
            else result.out.joinToString("\n").trim().ifEmpty { null }
        }
    }

    /**
     * 导出配置到内部存储：原样落盘 [json]（当前编辑器文本），不读数据库。
     * 写临时文件 → root shell `mkdir -p $EXPORT_DIR && cp && chmod 644` → 删临时文件。
     */
    fun exportConfig(packageName: String, json: String): WriteResult {
        if (!validPackage(packageName)) return WriteResult(false, "包名格式无效")
        if (json.isBlank()) return WriteResult(false, "JSON 内容为空")
        val temporary = File(appContext.cacheDir, "export-${UUID.randomUUID()}.json")
        return try {
            temporary.writeText(json)
            val exportPath = "$EXPORT_DIR/$packageName.json"
            val command = listOf(
                "mkdir -p ${quote(EXPORT_DIR)}",
                "cp ${quote(temporary.absolutePath)} ${quote(exportPath)}",
                "chmod 644 ${quote(exportPath)}",
            ).joinToString(" && ")
            val result = Shell.cmd(command).exec()
            WriteResult(
                result.isSuccess,
                if (result.isSuccess) "已导出至 $exportPath"
                else result.message("导出失败"),
            )
        } catch (error: Exception) {
            WriteResult(false, "导出失败: ${error.message ?: "无法创建临时文件"}")
        } finally {
            temporary.delete()
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

    fun getCosaVersion(): String =
        Shell.cmd("dumpsys package com.oplus.cosa | grep versionName | head -1")
            .exec().out.firstOrNull()?.substringAfter('=')?.trim()?.ifEmpty { null } ?: "未知"

    fun clearGameData(): WriteResult {
        val result = Shell.cmd("pm clear com.oplus.cosa").exec()
        return if (result.isSuccess) WriteResult(true, "应用增强服务数据已清除")
        else WriteResult(false, result.message("清除失败"))
    }
}
