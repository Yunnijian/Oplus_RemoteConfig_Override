package com.remoteconfig.override.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.viewmodel.MainViewModel
import kotlinx.serialization.json.Json

private val DARK_BG = Color(0xFF1E1E1E)
private val LIGHT_BG = Color(0xFFFFFFFFFF)
private val DARK_TEXT = Color(0xFFD4D4D4)
private val LIGHT_TEXT = Color(0xFF333333)
private val DARK_LINE = Color(0xFF858585)
private val LIGHT_LINE = Color(0xFF9AA0A6)
private val DARK_LINE_BG = Color(0xFF252526)
private val LIGHT_LINE_BG = Color(0xFFF5F5F5)
private val DARK_STATUS = Color(0xFF252526)
private val LIGHT_STATUS = Color(0xFFF5F5F5)
private val DARK_CURSOR = Color(0xFF569CD6)
private val LIGHT_CURSOR = Color(0xFF1A73E8)

// ── JSON 语法高亮配色（暗色 / 亮色）──
private val DARK_STR = Color(0xFFCE9178)  // 字符串
private val LIGHT_STR = Color(0xFF0451A5)
private val DARK_NUM = Color(0xFFB5CEA8)  // 数字
private val LIGHT_NUM = Color(0xFF098658)
private val DARK_KEY = Color(0xFF9CDCFE)  // 键名
private val LIGHT_KEY = Color(0xFF881280)
private val DARK_BOOL = Color(0xFF569CD6) // true/false
private val LIGHT_BOOL = Color(0xFF267F99)
private val DARK_NULL = Color(0xFFF44747) // null
private val LIGHT_NULL = Color(0xFFE51400)
private val DARK_BRACE = Color(0xFFFFD700)// {}[]
private val LIGHT_BRACE = Color(0xFF800000)
private val DARK_PUNCT = Color(0xFF808080)// :,
private val LIGHT_PUNCT = Color(0xFFA0A0A0)

// ── JSON 语法高亮解析器（轻量状态机）──
private fun highlightJson(text: String, dark: Boolean): AnnotatedString {
    val strColor = if (dark) DARK_STR else LIGHT_STR
    val numColor = if (dark) DARK_NUM else LIGHT_NUM
    val keyColor = if (dark) DARK_KEY else LIGHT_KEY
    val boolColor = if (dark) DARK_BOOL else LIGHT_BOOL
    val nullColor = if (dark) DARK_NULL else LIGHT_NULL
    val braceColor = if (dark) DARK_BRACE else LIGHT_BRACE
    val punctColor = if (dark) DARK_PUNCT else LIGHT_PUNCT
    val defColor = if (dark) DARK_TEXT else LIGHT_TEXT

    return buildAnnotatedString {
        var i = 0
        var expectKey = true

        while (i < text.length) {
            when {
                text[i] == '"' -> {
                    val start = i
                    i++
                    while (i < text.length && !(text[i] == '"' && text[i - 1] != '\\')) i++
                    if (i < text.length) i++ // skip closing "
                    val color = if (expectKey) keyColor else strColor
                    withStyle(SpanStyle(color = color)) { append(text.substring(start, i)) }
                    expectKey = false
                }
                text[i] == '-' || text[i].isDigit() -> {
                    val start = i
                    if (text[i] == '-') i++
                    while (i < text.length && (text[i].isDigit() || text[i] == '.' || text[i] == 'e' || text[i] == 'E' || text[i] == '+' || text[i] == '-')) {
                        if ((text[i] == '+' || text[i] == '-') && i > start + 1 && text[i-1] != 'e' && text[i-1] != 'E') break
                        i++
                    }
                    withStyle(SpanStyle(color = numColor)) { append(text.substring(start, i)) }
                }
                text.startsWith("true", i) -> {
                    withStyle(SpanStyle(color = boolColor)) { append("true") }; i += 4
                }
                text.startsWith("false", i) -> {
                    withStyle(SpanStyle(color = boolColor)) { append("false") }; i += 5
                }
                text.startsWith("null", i) -> {
                    withStyle(SpanStyle(color = nullColor)) { append("null") }; i += 4
                }
                text[i] == '{' || text[i] == '}' || text[i] == '[' || text[i] == ']' -> {
                    withStyle(SpanStyle(color = braceColor)) { append(text[i].toString()) }; i++
                    expectKey = i < text.length && (text[i] == '{' || text[i] == '[')
                }
                text[i] == ':' -> { withStyle(SpanStyle(color = punctColor)) { append(":") }; i++; expectKey = true }
                text[i] == ',' -> { withStyle(SpanStyle(color = punctColor)) { append(",") }; i++; expectKey = false }
                else -> { withStyle(SpanStyle(color = defColor)) { append(text[i].toString()) }; i++ }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val editingJson by viewModel.editingJson.collectAsState()
    val editingPackageName by viewModel.editingPackageName.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val systemStatus by viewModel.systemStatus.collectAsState()
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()

    val appLabel = remember(editingPackageName) {
        editingPackageName?.let { p -> try {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(p, 0)).toString()
        } catch (_: Exception) { p } } ?: "配置编辑"
    }

    var showResultDialog by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var resultSuccess by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fontSize by rememberSaveable { mutableStateOf(13f) }

    val bg = if (dark) DARK_BG else LIGHT_BG
    val textColor = if (dark) DARK_TEXT else LIGHT_TEXT
    val lineColor = if (dark) DARK_LINE else LIGHT_LINE
    val lineBg = if (dark) DARK_LINE_BG else LIGHT_LINE_BG
    val statusBg = if (dark) DARK_STATUS else LIGHT_STATUS
    val cursorColor = if (dark) DARK_CURSOR else LIGHT_CURSOR

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.readText() ?: ""
                inputStream?.close()
                if (text.isNotBlank()) {
                    viewModel.updateEditingJson(text)
                    resultSuccess = true; resultMessage = "已导入配置"
                } else { resultSuccess = false; resultMessage = "文件内容为空" }
            } catch (e: Exception) { resultSuccess = false; resultMessage = "导入失败: ${e.message}" }
            showResultDialog = true
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appLabel, style = MaterialTheme.typography.titleMedium) },
                actions = {
                    var showOverflow by remember { mutableStateOf(false) }
                    IconButton(onClick = { fontSize = (fontSize - 1).coerceIn(8f, 32f) }) { Icon(Icons.Default.ZoomOut, "缩小") }
                    IconButton(onClick = { fontSize = (fontSize + 1).coerceIn(8f, 32f) }) { Icon(Icons.Default.ZoomIn, "放大") }
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(Icons.Filled.MoreVert, "更多操作")
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(text = { Text("保存配置") }, onClick = {
                                showOverflow = false
                                viewModel.saveLocalConfig { s, msg -> resultSuccess = s; resultMessage = msg; showResultDialog = true }
                            })
                            if (systemStatus.isRooted) {
                                DropdownMenuItem(text = { Text("保存到数据库") }, onClick = {
                                    showOverflow = false
                                    viewModel.writeToDatabase { s, msg -> resultSuccess = s; resultMessage = msg; showResultDialog = true }
                                })
                            }
                            DropdownMenuItem(text = { Text("回退配置") }, onClick = {
                                showOverflow = false
                                viewModel.restoreBackupConfig { s, msg ->
                                    resultSuccess = s; resultMessage = msg; showResultDialog = true
                                }
                            })
                            DropdownMenuItem(text = { Text("导入配置") }, onClick = {
                                showOverflow = false; importLauncher.launch(arrayOf("application/json", "*/*"))
                            })
                            DropdownMenuItem(text = { Text("导出配置") }, onClick = {
                                showOverflow = false
                                viewModel.exportConfig { success, msg -> resultSuccess = success; resultMessage = msg; showResultDialog = true }
                            })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(); Spacer(Modifier.height(12.dp))
                        Text("正在加载...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val text = editingJson ?: ""
                if (text.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("数据库中无此记录，请输入 JSON 后写入", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        // 状态栏
                        Row(
                            Modifier.fillMaxWidth().background(statusBg).padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (error != null) {
                                Text("⚠ ${error!!.take(60)}", style = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.error),
                                    maxLines = 1, modifier = Modifier.weight(1f))
                            } else {
                                Text("✓ JSON", style = TextStyle(fontSize = 11.sp, color = textColor.copy(alpha = 0.6f)))
                            }
                            Spacer(Modifier.weight(1f))
                            Text("${text.count { it == '\n' } + 1} 行 · ${fontSize.toInt()}sp",
                                style = TextStyle(fontSize = 10.sp, color = textColor.copy(alpha = 0.4f)))
                        }

                        // 编辑区 — 语法高亮
                        Row(Modifier.fillMaxSize().background(bg)) {
                            val textScroll = rememberScrollState()
                            val lines = remember(text) { text.split("\n") }

                            // 行号
                            Column(
                                Modifier.width(40.dp).fillMaxHeight()
                                    .verticalScroll(textScroll)
                                    .background(lineBg)
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                lines.forEachIndexed { i, _ ->
                                    Text("${i + 1}", style = TextStyle(
                                        fontFamily = FontFamily.Monospace, fontSize = (fontSize * 0.85f).sp,
                                        lineHeight = (fontSize * 1.5f).sp, color = lineColor))
                                }
                            }

                            // 语法高亮 + 编辑区
                            val highlighted = remember(text, dark) { highlightJson(text, dark) }

                            Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(textScroll).horizontalScroll(rememberScrollState())) {
                                BasicTextField(
                                    value = text,
                                    onValueChange = { newText ->
                                        viewModel.updateEditingJson(newText)
                                        error = try { Json { ignoreUnknownKeys = true }.parseToJsonElement(newText); null }
                                        catch (e: Exception) { e.message }
                                    },
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace, fontSize = fontSize.sp,
                                        lineHeight = (fontSize * 1.5f).sp, color = Color.Transparent
                                    ),
                                    cursorBrush = SolidColor(cursorColor),
                                    decorationBox = { innerTextField ->
                                        // 层叠: 底层的语法高亮 Text + 上层的编辑区（透明文字，保留光标）
                                        Text(
                                            text = highlighted,
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace, fontSize = fontSize.sp,
                                                lineHeight = (fontSize * 1.5f).sp
                                            )
                                        )
                                        innerTextField()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showResultDialog) {
        AlertDialog(onDismissRequest = { showResultDialog = false },
            icon = { Icon(if (resultSuccess) Icons.Default.CheckCircle else Icons.Default.Info, null,
                tint = if (resultSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp)) },
            title = { Text(if (resultSuccess) "操作成功" else "操作失败", fontWeight = FontWeight.SemiBold) },
            text = { Text(resultMessage) },
            confirmButton = { FilledTonalButton(onClick = { showResultDialog = false }) { Text("确定") } }
        )
}
}
