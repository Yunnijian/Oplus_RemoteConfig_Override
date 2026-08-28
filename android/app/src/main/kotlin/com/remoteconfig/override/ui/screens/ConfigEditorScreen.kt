package com.remoteconfig.override.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

private val DARK_BG = Color(0xFF1E1E1E)
private val LIGHT_BG = Color(0xFFFFFFFF)
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

/**
 * 配置编辑器页分发器：按当前 UI 风格 [LocalUiMode] 选择 Miuix / Material 外壳。
 *
 * 签名 `(viewModel, onBack)` 保持不变（MainActivity / 列表页调用方不变）。
 * 编辑器自研核心（语法高亮 / 行号 / 捏合缩放 / IME 跟随 / 校验）完全不动，
 * 仅 Scaffold / TopAppBar / 结果弹窗 三处外壳按 isMiuix 分支。
 */
@Composable
fun ConfigEditorScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> ConfigEditorContent(viewModel, onBack, isMiuix = true)
        UiMode.Material -> ConfigEditorContent(viewModel, onBack, isMiuix = false)
    }
}

/**
 * 双窗右侧窗格（Task 12）：宽屏配置页右侧内嵌编辑器，不占路由。
 *
 * 进入时加载指定包名的配置（仿 ConfigEditorScreen 打开路径）；
 * 顶栏无返回箭头（showBack=false），显示关闭 X → [onClosed]。
 *
 * 注意：loadConfig 内部自己 launch 协程（非 suspend），直接调用即可。
 * 若该包名已在编辑中（含 MainScreen onNewConfig 的 createNewConfig 模板），
 * 跳过重载以保留未保存的编辑 / 新建模板。
 */
@Composable
fun ConfigEditorPane(viewModel: MainViewModel, packageName: String, onClosed: () -> Unit) {
    val editingPackageName by viewModel.editingPackageName.collectAsState()
    LaunchedEffect(packageName) {
        if (editingPackageName != packageName) {
            viewModel.loadConfig(packageName)
        }
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> ConfigEditorContent(viewModel, onClosed, isMiuix = true, packageName = packageName, showBack = false)
        UiMode.Material -> ConfigEditorContent(viewModel, onClosed, isMiuix = false, packageName = packageName, showBack = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ConfigEditorContent(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    isMiuix: Boolean,
    packageName: String? = null,
    showBack: Boolean = true,
) {
    val editingJson by viewModel.editingJson.collectAsState()
    val editingPackageName by viewModel.editingPackageName.collectAsState()
    // 编辑器/窗格加载状态：与列表刷新（isLoading）分离，双窗下点列表项不影响左列表
    val isEditorLoading by viewModel.isEditorLoading.collectAsState()
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
    var highlighted by remember(dark) { mutableStateOf(AnnotatedString("")) }
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var editorFocused by remember { mutableStateOf(false) }
    var fieldValue by remember(editingPackageName) { mutableStateOf(TextFieldValue()) }
    var cursorRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val latestTextLayout = remember { arrayOfNulls<TextLayoutResult>(1) }

    val bg = if (dark) DARK_BG else LIGHT_BG
    val textColor = if (dark) DARK_TEXT else LIGHT_TEXT
    val lineColor = if (dark) DARK_LINE else LIGHT_LINE
    val lineBg = if (dark) DARK_LINE_BG else LIGHT_LINE_BG
    val statusBg = if (dark) DARK_STATUS else LIGHT_STATUS
    val cursorColor = if (dark) DARK_CURSOR else LIGHT_CURSOR

    val currentText = editingJson.orEmpty()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(currentText) {
        if (fieldValue.text != currentText) {
            val cursor = fieldValue.selection.end.coerceIn(0, currentText.length)
            fieldValue = TextFieldValue(currentText, TextRange(cursor))
            cursorRect = null
            latestTextLayout[0] = null
        }
    }
    LaunchedEffect(fieldValue.selection.end, currentText) {
        // Selection changes do not necessarily trigger onTextLayout. Re-read the
        // caret from the latest layout when the user taps another line.
        latestTextLayout[0]?.let { layout ->
            val nextRect = layout.getCursorRect(fieldValue.selection.end)
            if (nextRect != cursorRect) cursorRect = nextRect
        }
    }
    // Bug 3：editorVisible 纳入 currentText 判定——空文档（DB 无记录/全选删除后）也可编辑；
    // 导入文件（updateEditingJson）会改变 currentText 触发本效果重新运行，保证导入后可见。
    LaunchedEffect(isEditorLoading, editingPackageName, currentText) {
        if (isEditorLoading) {
            editorVisible = false
        } else if (!editorVisible) {
            // Let the loading state commit one frame before composing the large
            // editor tree, then reveal it without a first-frame jump.
            withFrameNanos { }
            editorVisible = true
        }
    }
    LaunchedEffect(currentText, dark) {
        // Keep typing responsive: render plain text immediately, then highlight
        // the latest snapshot off the UI thread after a short idle window.
        highlighted = AnnotatedString(currentText)
        val snapshot = currentText
        // The first document waits until the entry fade has settled, so the
        // initial composition and syntax scan do not compete for the same frame.
        delay(if (editorVisible) 120 else 300)
        highlighted = withContext(Dispatchers.Default) { highlightJson(snapshot, dark) }
    }
    LaunchedEffect(currentText) {
        error = null
        if (currentText.isBlank()) return@LaunchedEffect
        val snapshot = currentText
        delay(250)
        error = withContext(Dispatchers.Default) {
            try {
                Json { ignoreUnknownKeys = true }.parseToJsonElement(snapshot)
                null
            } catch (e: Exception) {
                e.message
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                if (text.isNotBlank()) {
                    viewModel.updateEditingJson(text)
                    resultSuccess = true; resultMessage = "已导入配置"
                } else { resultSuccess = false; resultMessage = "文件内容为空" }
            } catch (e: Exception) { resultSuccess = false; resultMessage = "导入失败: ${e.message}" }
            showResultDialog = true
        }
    }


    // TopAppBar 操作区（双模式共享）：缩小 / 放大 / 更多（写入数据库 / 导入配置 / 导出配置）。
    // 菜单项文案与现有 Material 版逐字一致（"注入数据"已改名"写入数据库"，"回退配置"已删除）。
    // 注：M3 组件在 Miuix TopAppBar 内无 MaterialTheme 包裹，默认 LocalContentColor 为黑；
    // 深色 Miuix 主题下对比度差，故按当前模式显式取 onSurface 作为 tint。
    val editorActions: @Composable RowScope.() -> Unit = {
        var showOverflow by remember { mutableStateOf(false) }
        val actionTint = if (isMiuix) colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
        IconButton(onClick = { fontSize = (fontSize - 1).coerceIn(8f, 32f) }) { Icon(Icons.Default.ZoomOut, "缩小", tint = actionTint) }
        IconButton(onClick = { fontSize = (fontSize + 1).coerceIn(8f, 32f) }) { Icon(Icons.Default.ZoomIn, "放大", tint = actionTint) }
        IconButton(onClick = { showOverflow = true }) {
            Icon(Icons.Filled.MoreVert, "更多操作", tint = actionTint)
            DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                DropdownMenuItem(text = { Text("写入数据库") }, onClick = {
                    showOverflow = false
                    viewModel.injectConfig { s, msg -> resultSuccess = s; resultMessage = msg; showResultDialog = true }
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
    }

    // 编辑器主体（双模式共享）：自研核心原样保留，仅作为两个 Scaffold 分支共用的 content。
    val editorBody: @Composable (PaddingValues) -> Unit = { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isEditorLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(); Spacer(Modifier.height(12.dp))
                        Text("正在加载...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val text = currentText
                val lineCount = remember(text) { text.count { it == '\n' } + 1 }
                // Bug 3：空文档与有内容文档共用同一编辑器（含可编辑输入框）。
                // "数据库中无此记录"等引导文案由状态栏/placeholder 承担，不再替代输入框。
                val editorPane: @Composable () -> Unit = {
                    if (!editorVisible) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                    AnimatedVisibility(
                        visible = editorVisible,
                        enter = fadeIn(animationSpec = tween(160)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Row(
                                Modifier.fillMaxWidth().background(statusBg).padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (text.isEmpty()) {
                                    Text("数据库中无此记录，请输入 JSON 后写入", style = TextStyle(fontSize = 11.sp, color = textColor.copy(alpha = 0.6f)),
                                        maxLines = 1, modifier = Modifier.weight(1f))
                                } else if (error != null) {
                                    Text("⚠ ${error!!.take(60)}", style = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.error),
                                        maxLines = 1, modifier = Modifier.weight(1f))
                                } else {
                                    Text("✓ JSON", style = TextStyle(fontSize = 11.sp, color = textColor.copy(alpha = 0.6f)))
                                }
                                Spacer(Modifier.weight(1f))
                                Text("$lineCount 行 · ${fontSize.toInt()}sp",
                                    style = TextStyle(fontSize = 10.sp, color = textColor.copy(alpha = 0.4f)))
                            }

                            val textScroll = rememberScrollState()
                            val horizontalScroll = rememberScrollState()
                            LaunchedEffect(
                                fieldValue.selection.end,
                                cursorRect,
                                editorFocused,
                                imeBottom,
                                editorVisible,
                                textScroll.viewportSize
                            ) {
                                val caret = cursorRect ?: return@LaunchedEffect
                                if (!editorFocused || !editorVisible || textScroll.viewportSize <= 0) {
                                    return@LaunchedEffect
                                }
                                // windowInsetsPadding(WindowInsets.ime) makes the scroll viewport
                                // itself end above the keyboard, so use that measured boundary.
                                delay(if (imeBottom > 0) 120 else 60)
                                val margin = with(density) { 28.dp.toPx() }
                                val top = textScroll.value.toFloat()
                                val bottom = top + textScroll.viewportSize
                                val target = when {
                                    caret.bottom + margin > bottom ->
                                        textScroll.value + (caret.bottom + margin - bottom).toInt()
                                    caret.top - margin < top ->
                                        textScroll.value - (top - caret.top + margin).toInt()
                                    else -> null
                                }?.coerceIn(0, textScroll.maxValue)
                                if (target != null && target != textScroll.value) {
                                    textScroll.animateScrollTo(target, tween(180))
                                }
                            }
                            val lineNumbers = remember(lineCount) {
                                buildString {
                                    for (line in 1..lineCount) {
                                        if (line > 1) append('\n')
                                        append(line)
                                    }
                                }
                            }
                            var gestureScale by remember { mutableFloatStateOf(1f) }
                            val transformState = rememberTransformableState { zoomChange, _, _ ->
                                if (zoomChange != 1f) {
                                    // Keep the gesture on the GPU layer. Re-measuring the
                                    // complete document for every touch event causes jank.
                                    gestureScale = (gestureScale * zoomChange).coerceIn(0.65f, 2.5f)
                                }
                            }
                            LaunchedEffect(transformState.isTransformInProgress) {
                                if (!transformState.isTransformInProgress && gestureScale != 1f) {
                                    val appliedScale = gestureScale
                                    gestureScale = 1f
                                    fontSize = (fontSize * appliedScale).coerceIn(8f, 32f)
                                }
                            }
                            val gestureLayer = if (gestureScale == 1f) {
                                Modifier
                            } else {
                                Modifier.graphicsLayer {
                                    scaleX = gestureScale
                                    scaleY = gestureScale
                                    transformOrigin = TransformOrigin(0f, 0f)
                                }
                            }
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .background(bg)
                                    .verticalScroll(textScroll)
                                    .windowInsetsPadding(WindowInsets.ime)
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .then(gestureLayer)
                                ) {
                                    Text(
                                        text = lineNumbers,
                                        modifier = Modifier
                                            .width(40.dp)
                                            .background(lineBg)
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = (fontSize * 0.85f).sp,
                                            lineHeight = (fontSize * 1.5f).sp,
                                            color = lineColor,
                                            textAlign = TextAlign.End
                                        )
                                    )

                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .horizontalScroll(horizontalScroll)
                                            .transformable(
                                                state = transformState,
                                                canPan = { false },
                                                lockRotationOnZoomPan = true
                                            )
                                    ) {
                                        BasicTextField(
                                            value = fieldValue,
                                            onValueChange = { newValue ->
                                                fieldValue = newValue
                                                viewModel.updateEditingJson(newValue.text)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                                .onFocusChanged { editorFocused = it.isFocused },
                                            textStyle = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = fontSize.sp,
                                                lineHeight = (fontSize * 1.5f).sp,
                                                color = Color.Transparent
                                            ),
                                            minLines = lineCount.coerceAtLeast(1),
                                            cursorBrush = SolidColor(cursorColor),
                                            onTextLayout = { layout: TextLayoutResult ->
                                                latestTextLayout[0] = layout
                                                val nextRect = layout.getCursorRect(fieldValue.selection.end)
                                                if (nextRect != cursorRect) cursorRect = nextRect
                                            },
                                            decorationBox = { innerTextField ->
                                                Box {
                                                    Text(
                                                        text = highlighted,
                                                        style = TextStyle(
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = fontSize.sp,
                                                            lineHeight = (fontSize * 1.5f).sp
                                                        )
                                                    )
                                                    if (text.isEmpty()) {
                                                        // Bug 3：空文档占位提示（不拦截输入，仅引导）
                                                        Text(
                                                            text = "请输入 JSON",
                                                            style = TextStyle(
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = fontSize.sp,
                                                                lineHeight = (fontSize * 1.5f).sp,
                                                                color = lineColor.copy(alpha = 0.5f)
                                                            )
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                editorPane()
            }
        }
    }

    // ── 外壳（chrome）：Scaffold + TopAppBar 按 UiMode 分支，body/actions 共享 ──
    if (isMiuix) {
        // 编辑器自行对滚动视口做 IME padding，Scaffold 不消费键盘 inset。
        MiuixScaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                MiuixTopAppBar(
                    title = if (packageName != null) appLabel else "JSON 编辑",
                    navigationIcon = {
                        if (showBack) {
                            MiuixIconButton(onClick = onBack) {
                                MiuixIcon(imageVector = MiuixIcons.Back, contentDescription = "返回", tint = colorScheme.onSurface)
                            }
                        } else {
                            // 双窗模式：关闭 X → onClosed
                            MiuixIconButton(onClick = onBack) {
                                MiuixIcon(imageVector = MiuixIcons.Basic.Close, contentDescription = "关闭", tint = colorScheme.onSurface)
                            }
                        }
                    },
                    actions = editorActions,
                )
            }
        ) { padding -> editorBody(padding) }
    } else {
        Scaffold(
            // The editor applies IME padding to its scroll viewport below. Do not
            // let Scaffold consume the keyboard inset before that modifier sees it.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text(appLabel, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        if (showBack) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, "返回")
                            }
                        } else {
                            // 双窗模式：关闭 X → onClosed
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.Close, "关闭")
                            }
                        }
                    },
                    actions = editorActions,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { padding -> editorBody(padding) }
    }

    // ── 结果弹窗（Miuix 用 WindowDialog，Material 保持 AlertDialog，内容一致）──
    if (showResultDialog) {
        if (isMiuix) {
            WindowDialog(
                show = true,
                title = if (resultSuccess) "操作成功" else "操作失败",
                onDismissRequest = { showResultDialog = false },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiuixIcon(
                            imageVector = if (resultSuccess) MiuixIcons.Ok else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (resultSuccess) colorScheme.primary else colorScheme.error,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        MiuixText(resultMessage, fontSize = 14.sp)
                    }
                    MiuixTextButton(
                        text = "确定",
                        onClick = { showResultDialog = false },
                        modifier = Modifier.align(Alignment.End).padding(top = 16.dp),
                        colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        } else {
            AlertDialog(onDismissRequest = { showResultDialog = false },
                icon = { Icon(if (resultSuccess) Icons.Default.CheckCircle else Icons.Default.Info, null,
                    tint = if (resultSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp)) },
                title = { Text(if (resultSuccess) "操作成功" else "操作失败", fontWeight = FontWeight.SemiBold) },
                text = { Text(resultMessage) },
                confirmButton = { FilledTonalButton(onClick = { showResultDialog = false }) { Text("确定") } }
            )
        }
    }
}
