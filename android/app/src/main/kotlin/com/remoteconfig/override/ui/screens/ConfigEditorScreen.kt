package com.remoteconfig.override.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.ui.theme.isInDarkTheme
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
 *
 * [bottomInnerPadding]：双窗右侧窗格处在 Pager 内，Pager 已不加 bottom padding
 * （内容需铺到底栏下方供悬浮玻璃采样折射），因此编辑器主体自行扣除底栏高度，
 * 避免最后几行文本被系统导航栏 / 底栏遮挡。
 */
/**
 * 吞掉编辑器内部滚动（horizontalScroll 抵达行首/行尾边缘）向上传递的横向剩余位移。
 * 不拦截的话，剩余位移会沿嵌套滚动链绕过 Pager 的 userScrollEnabled 直接拖动 Pager，
 * 表现为：在编辑器上左滑时整页跟随拖出小半截、松手又弹回（宽屏双窗下观感极差）。
 * 文本行到边缘后停住（标准滚动边界行为），Pager 不再被牵连。
 */
private val EditorNestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = if (available.x != 0f) Offset(available.x, 0f) else Offset.Zero
}

@Composable
fun ConfigEditorPane(
    viewModel: MainViewModel,
    packageName: String,
    onClosed: () -> Unit,
    bottomInnerPadding: Dp = 0.dp,
) {
    val editingPackageName by viewModel.editingPackageName.collectAsState()
    LaunchedEffect(packageName) {
        if (editingPackageName != packageName) {
            viewModel.loadConfig(packageName)
        }
    }
    Box(Modifier.nestedScroll(EditorNestedScrollConnection)) {
        when (LocalUiMode.current) {
            UiMode.Miuix -> ConfigEditorContent(
                viewModel, onClosed, isMiuix = true, packageName = packageName, showBack = false,
                bottomInnerPadding = bottomInnerPadding,
            )
            UiMode.Material -> ConfigEditorContent(
                viewModel, onClosed, isMiuix = false, packageName = packageName, showBack = false,
                bottomInnerPadding = bottomInnerPadding,
            )
        }
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
    bottomInnerPadding: Dp = 0.dp,
) {
    val editingJson by viewModel.editingJson.collectAsState()
    val editingPackageName by viewModel.editingPackageName.collectAsState()
    val baselineJson by viewModel.baselineJson.collectAsState()
    // 写入守护：root shell 最长 10s，期间禁用写入类菜单防止并发重复触发。
    val isWriting by viewModel.isWriting.collectAsState()
    // 脏标记：编辑内容与基准（最近一次载入的原文）不一致即为未保存修改。
    val isDirty = editingPackageName != null && editingJson != baselineJson
    // 编辑器/窗格加载状态：与列表刷新（isLoading）分离，双窗下点列表项不影响左列表
    val isEditorLoading by viewModel.isEditorLoading.collectAsState()
    val context = LocalContext.current
    // Bug 2：编辑器配色（背景/正文/行号/高亮色表/光标/状态栏）必须跟随应用主题
    // （LocalColorMode，SYSTEM/Monet-SYSTEM 回落系统），而非系统深色。
    val dark = isInDarkTheme()

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
    // rememberSaveable：旋转/重建后保留光标位置（与同层 fontSize/editorVisible 的保存策略一致）
    var fieldValue by rememberSaveable(editingPackageName, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var cursorRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val latestTextLayout = remember { arrayOfNulls<TextLayoutResult>(1) }

    // 未保存修改拦截：退出/关闭/系统返回均先弹确认，避免静默丢稿。
    var showDiscardDialog by remember { mutableStateOf(false) }
    val requestClose: () -> Unit = {
        if (isDirty) showDiscardDialog = true else onBack()
    }
    BackHandler(enabled = isDirty) { showDiscardDialog = true }

    val bg = if (dark) DARK_BG else LIGHT_BG
    val textColor = if (dark) DARK_TEXT else LIGHT_TEXT
    val lineColor = if (dark) DARK_LINE else LIGHT_LINE
    val lineBg = if (dark) DARK_LINE_BG else LIGHT_LINE_BG
    val statusBg = if (dark) DARK_STATUS else LIGHT_STATUS
    val cursorColor = if (dark) DARK_CURSOR else LIGHT_CURSOR

    val currentText = editingJson.orEmpty()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    // 全屏路由下 bottomInnerPadding 为 0：键盘收起时自补系统导航栏高度，
    // 否则最后一行文本被导航栏遮挡、光标无法定位（双窗窗格传入真实值，不受影响）。
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
    val bottomInset = if (imeBottom > 0) {
        bottomInnerPadding
    } else {
        bottomInnerPadding.coerceAtLeast(with(density) { navBarBottomPx.toDp() })
    }
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

    var pendingImportText by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                if (text.isNotBlank()) {
                    if (isDirty) {
                        // 导入会整体替换编辑缓冲区：先确认，避免覆盖未保存修改。
                        pendingImportText = text
                    } else {
                        viewModel.updateEditingJson(text)
                        resultSuccess = true; resultMessage = "已导入配置"
                        showResultDialog = true
                    }
                } else { resultSuccess = false; resultMessage = "文件内容为空"; showResultDialog = true }
            } catch (e: Exception) {
                resultSuccess = false; resultMessage = "导入失败: ${e.message}"; showResultDialog = true
            }
        }
    }

    // 写入确认：覆盖目标应用数据库的破坏性操作，须明确确认后才执行。
    var showWriteConfirm by remember { mutableStateOf(false) }


    // TopAppBar 操作区（双模式共享）：缩小 / 放大 / 更多（写入数据库 / 导入配置 / 导出配置）。
    // 菜单项文案与现有 Material 版逐字一致（"注入数据"已改名"写入数据库"，"回退配置"已删除）。
    // 注：M3 组件在 Miuix TopAppBar 内无 MaterialTheme 包裹，默认 LocalContentColor 为黑；
    // 深色 Miuix 主题下对比度差，故按当前模式显式取 onSurface 作为 tint。
    val editorActions: @Composable RowScope.() -> Unit = {
        var showOverflow by remember { mutableStateOf(false) }
        val actionTint = if (isMiuix) colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
        IconButton(onClick = { fontSize = (fontSize - 1).coerceIn(8f, 32f) }) { Icon(Icons.Default.ZoomOut, "缩小", tint = actionTint) }
        IconButton(onClick = { fontSize = (fontSize + 1).coerceIn(8f, 32f) }) { Icon(Icons.Default.ZoomIn, "放大", tint = actionTint) }
        if (isWriting) {
            // 写入进行中：以进度指示替换更多菜单，防止重复触发。
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = actionTint,
                )
            }
        } else {
            IconButton(onClick = { showOverflow = true }) {
                Icon(Icons.Filled.MoreVert, "更多操作", tint = actionTint)
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                    DropdownMenuItem(
                        text = { Text("写入数据库") },
                        // 校验门禁：JSON 无效时禁止写入，避免失败后才在弹窗里看到裸错误。
                        enabled = error == null,
                        onClick = {
                            showOverflow = false
                            showWriteConfirm = true
                        },
                    )
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
    }

    // 编辑器主体（双模式共享）：自研核心原样保留，仅作为两个 Scaffold 分支共用的 content。
    val editorBody: @Composable (PaddingValues) -> Unit = { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // 双窗窗格：Pager 不再代为扣除底栏高度，编辑器自己让出底部空间；
                // 全屏路由下自补系统导航栏避让（见 bottomInset 计算）。
                .padding(bottom = bottomInset)
        ) {
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
                                // windowInsetsPadding(WindowInsets.ime) 在 verticalScroll 内侧——滚动视口是
                                // 全屏高度，需手动扣除键盘高度，否则光标会被滚到键盘下方（API 30+）
                                delay(if (imeBottom > 0) 120 else 60)
                                val margin = with(density) { 28.dp.toPx() }
                                val top = textScroll.value.toFloat()
                                // Bug 5: 键盘开启时视口底界 = 视口底 - imeBottom，光标保持在键盘上方可视区
                                val viewportBottom = top + textScroll.viewportSize - (if (imeBottom > 0) imeBottom else 0)
                                val target = when {
                                    caret.bottom + margin > viewportBottom ->
                                        textScroll.value + (caret.bottom + margin - viewportBottom).toInt()
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
                                            // 捏合缩放：transformable 只在多指时产生 zoom 变化，
                                            // 单指点击/拖动正常透传给 BasicTextField（输入/光标）与滚动。
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
                    // 窄屏路由与双窗窗格统一显示应用名，保留“正在编辑哪个应用”上下文。
                    title = appLabel,
                    navigationIcon = {
                        if (showBack) {
                            MiuixIconButton(onClick = requestClose) {
                                MiuixIcon(imageVector = MiuixIcons.Back, contentDescription = "返回", tint = colorScheme.onSurface)
                            }
                        } else {
                            // 双窗模式：关闭 X → 脏检查后 onClosed
                            MiuixIconButton(onClick = requestClose) {
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
                            IconButton(onClick = requestClose) {
                                Icon(Icons.Default.ArrowBack, "返回")
                            }
                        } else {
                            // 双窗模式：关闭 X → 脏检查后 onClosed
                            IconButton(onClick = requestClose) {
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

    // ── 写入确认弹窗：写入会覆盖目标应用数据库中的现有配置，须显式确认 ──
    if (showWriteConfirm) {
        val pkg = editingPackageName.orEmpty()
        val confirmMessage = "将覆盖 $pkg 在 com.oplus.cosa 数据库中的现有配置（双库均写入），是否继续？"
        if (isMiuix) {
            WindowDialog(
                show = true,
                title = "确认写入",
                onDismissRequest = { showWriteConfirm = false },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    MiuixText(confirmMessage, fontSize = 14.sp)
                    Row(
                        Modifier.align(Alignment.End).padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        MiuixTextButton(
                            text = "取消",
                            onClick = { showWriteConfirm = false },
                        )
                        Spacer(Modifier.width(12.dp))
                        MiuixTextButton(
                            text = "写入",
                            onClick = {
                                showWriteConfirm = false
                                viewModel.injectConfig { s, msg ->
                                    resultSuccess = s; resultMessage = msg; showResultDialog = true
                                }
                            },
                            colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { showWriteConfirm = false },
                title = { Text("确认写入", fontWeight = FontWeight.SemiBold) },
                text = { Text(confirmMessage) },
                confirmButton = {
                    FilledTonalButton(onClick = {
                        showWriteConfirm = false
                        viewModel.injectConfig { s, msg ->
                            resultSuccess = s; resultMessage = msg; showResultDialog = true
                        }
                    }) { Text("写入") }
                },
                dismissButton = {
                    TextButton(onClick = { showWriteConfirm = false }) { Text("取消") }
                },
            )
        }
    }

    // ── 放弃未保存修改弹窗（退出/关闭/系统返回/双窗切换均经此拦截）──
    if (showDiscardDialog) {
        if (isMiuix) {
            WindowDialog(
                show = true,
                title = "未保存的修改",
                onDismissRequest = { showDiscardDialog = false },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    MiuixText("当前编辑内容尚未写入数据库，确定放弃修改？", fontSize = 14.sp)
                    Row(
                        Modifier.align(Alignment.End).padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        MiuixTextButton(
                            text = "继续编辑",
                            onClick = { showDiscardDialog = false },
                        )
                        Spacer(Modifier.width(12.dp))
                        MiuixTextButton(
                            text = "放弃修改",
                            onClick = { showDiscardDialog = false; onBack() },
                            colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text("未保存的修改", fontWeight = FontWeight.SemiBold) },
                text = { Text("当前编辑内容尚未写入数据库，确定放弃修改？") },
                confirmButton = {
                    FilledTonalButton(onClick = { showDiscardDialog = false; onBack() }) { Text("放弃修改") }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) { Text("继续编辑") }
                },
            )
        }
    }

    // ── 导入覆盖确认弹窗：脏状态下导入会整体替换编辑缓冲区 ──
    if (pendingImportText != null) {
        val pending = pendingImportText.orEmpty()
        if (isMiuix) {
            WindowDialog(
                show = true,
                title = "导入将替换当前内容",
                onDismissRequest = { pendingImportText = null },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    MiuixText("当前编辑内容尚未保存，导入将替换它且不可恢复，是否继续？", fontSize = 14.sp)
                    Row(
                        Modifier.align(Alignment.End).padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        MiuixTextButton(
                            text = "取消",
                            onClick = { pendingImportText = null },
                        )
                        Spacer(Modifier.width(12.dp))
                        MiuixTextButton(
                            text = "导入",
                            onClick = {
                                pendingImportText = null
                                viewModel.updateEditingJson(pending)
                                resultSuccess = true; resultMessage = "已导入配置"; showResultDialog = true
                            },
                            colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { pendingImportText = null },
                title = { Text("导入将替换当前内容", fontWeight = FontWeight.SemiBold) },
                text = { Text("当前编辑内容尚未保存，导入将替换它且不可恢复，是否继续？") },
                confirmButton = {
                    FilledTonalButton(onClick = {
                        pendingImportText = null
                        viewModel.updateEditingJson(pending)
                        resultSuccess = true; resultMessage = "已导入配置"; showResultDialog = true
                    }) { Text("导入") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingImportText = null }) { Text("取消") }
                },
            )
        }
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
                        // 多行展示：双库失败报告/未知字段警告为多行输出。
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
