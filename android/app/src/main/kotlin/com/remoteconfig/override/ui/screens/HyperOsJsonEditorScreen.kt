package com.remoteconfig.override.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.remoteconfig.override.navigation.LocalNavigator
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.component.DiscardChangesDialog
import com.remoteconfig.override.ui.component.rememberContentReady
import com.remoteconfig.override.ui.editor.NativeJsonEditorView
import com.remoteconfig.override.ui.theme.LocalUiMode
import com.remoteconfig.override.ui.theme.isInDarkTheme
import com.remoteconfig.override.viewmodel.HyperOsViewModel
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

// 编辑器画布配色与 ColorOS 侧 ConfigEditorScreen 同一套代码编辑器色板：
// 它必须与 NativeJsonEditorView 内部自绘的正文/行号色一致，不随强调色漂移。
private val EDITOR_DARK_BG = Color(0xFF1E1E1E)
private val EDITOR_LIGHT_BG = Color(0xFFFFFFFF)
private val EDITOR_DARK_STATUS = Color(0xFF252526)
private val EDITOR_LIGHT_STATUS = Color(0xFFF5F5F5)
private val EDITOR_DARK_TEXT = Color(0xFFD4D4D4)
private val EDITOR_LIGHT_TEXT = Color(0xFF333333)

/**
 * HyperOS 高级 JSON 编辑（整份 cloud_config params）—— 兜底入口。
 *
 * 主编辑路径是作用域编辑（[HyperOsScopedEditorScreen]）；本页直接编辑整份文档，
 * 走 joyose-write 双库镜像写。数据来自 `joyose-read`，CLI 已输出格式化 JSON。
 */
@Composable
fun HyperOsJsonEditorScreen(configName: String) {
    val viewModel: HyperOsViewModel = viewModel()
    val state by viewModel.editorState.collectAsStateWithLifecycle()
    LaunchedEffect(configName) { viewModel.loadEditor(configName) }

    HyperOsEditorScreen(
        title = "高级编辑 · $configName",
        label = configName,
        loading = state.loading,
        writing = state.writing,
        base = state.json,
        edited = state.edited,
        error = state.error,
        warning = null,
        onTextChange = viewModel::updateEdited,
        onSave = viewModel::saveEditor,
        onRetry = { viewModel.loadEditor(configName) },
        onRevert = { state.json?.let(viewModel::updateEdited) },
    )
}

/**
 * HyperOS 作用域编辑 —— 只编辑指定 App 名下的云控片段。
 *
 * 片段文档（`{ "<JSON Pointer>": <片段> }`）由 `joyose-scoped` 一次调用产出：
 * 指针解析、抽取与格式化全在 CLI 内完成，App 不搬运 346KB 整份文档；保存走
 * `joyose-scoped-write`，只补丁本 App 的指针，新增/改名/删除键由 CLI 直接报错。
 */
@Composable
fun HyperOsScopedEditorScreen(packageName: String) {
    val viewModel: HyperOsViewModel = viewModel()
    val state by viewModel.scopedEditorState.collectAsStateWithLifecycle()
    LaunchedEffect(packageName) { viewModel.loadScopedEditor(packageName) }

    HyperOsEditorScreen(
        title = "高级编辑",
        label = packageName,
        loading = state.loading,
        writing = state.writing,
        base = state.base,
        edited = state.edited,
        error = state.error,
        warning = state.warning,
        onTextChange = viewModel::updateScopedEdited,
        onSave = viewModel::saveScopedEditor,
        onRetry = { viewModel.loadScopedEditor(packageName) },
        onRevert = viewModel::revertScopedEditor,
    )
}

/** 编辑器页状态快照（整文档版/作用域版共用）。 */
private data class EditorUiState(
    val loading: Boolean,
    val writing: Boolean,
    val base: String?,
    val edited: String?,
    val error: String?,
    val warning: String?,
)

/**
 * 编辑器页外壳：双皮肤 Scaffold + TopAppBar，正文与操作区共享。
 *
 * 结构逐字对齐 ColorOS 侧 [ConfigEditorScreen]：Scaffold 不消费键盘 inset，
 * 容器级 `imePadding` 复现 adjustResize 几何，`rememberContentReady` +
 * 一帧 `withFrameNanos` 推迟重型编辑器全树组装（push 转场卡顿的根因），
 * 脏检查经 [DiscardChangesDialog] 拦截返回键与顶栏返回。
 */
@Composable
private fun HyperOsEditorScreen(
    title: String,
    label: String,
    loading: Boolean,
    writing: Boolean,
    base: String?,
    edited: String?,
    error: String?,
    warning: String?,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onRevert: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val isMiuix = LocalUiMode.current == UiMode.Miuix
    val dark = isInDarkTheme()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val editorBg = if (dark) EDITOR_DARK_BG else EDITOR_LIGHT_BG
    val editorText = if (dark) EDITOR_DARK_TEXT else EDITOR_LIGHT_TEXT
    val statusBg = if (dark) EDITOR_DARK_STATUS else EDITOR_LIGHT_STATUS

    var fontSize by rememberSaveable { mutableFloatStateOf(13f) }
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val editorHolder = remember { mutableStateOf<NativeJsonEditorView?>(null) }

    val currentText = edited.orEmpty()
    val isDirty = base != null && edited != null && edited != base
    val requestClose: () -> Unit = {
        editorHolder.value?.dismissInput()
        if (isDirty) showDiscardDialog = true else navigator.pop()
    }
    BackHandler(enabled = isDirty) { showDiscardDialog = true }

    // 转场期间保持 spinner 占位，转场结束 +1 帧后才揭幕编辑器全树。
    val contentReady = rememberContentReady()
    LaunchedEffect(loading, currentText, contentReady) {
        if (loading) {
            editorVisible = false
        } else if (contentReady && base != null && !editorVisible) {
            withFrameNanos { }
            editorVisible = true
        }
    }

    // Navigation 可以在 IME 仍附着于原生 EditText 时移除 AndroidView。
    DisposableEffect(Unit) {
        onDispose { editorHolder.value?.dismissInput() }
    }

    val lineCount = remember(currentText) { currentText.count { it == '\n' } + 1 }

    // 实时语法校验（对齐 ColorOS 侧 ConfigEditorScreen）：边打字边报、修好即消失。
    // ViewModel 的 error 只承载写入失败/CLI 拒绝，两者一起进同一条状态行。
    var syntaxError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentText) {
        syntaxError = null
        if (currentText.isBlank()) return@LaunchedEffect
        val snapshot = currentText
        delay(250)
        syntaxError = withContext(Dispatchers.Default) {
            try {
                Json.parseToJsonElement(snapshot)
                null
            } catch (e: Exception) {
                e.message?.replace('\n', ' ')
            }
        }
    }
    val statusMessage = syntaxError ?: error ?: warning
    val canSave = base != null && !writing && syntaxError == null

    // ── 顶栏操作区（双皮肤共享语义）：字号 ±、保存 ──
    val editorActions: @Composable RowScope.() -> Unit = {
        var showOverflow by remember { mutableStateOf(false) }
        val actionTint = if (isMiuix) colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
        IconButton(onClick = { fontSize = (fontSize - 1f).coerceIn(8f, 32f) }) {
            Icon(Icons.Default.ZoomOut, "缩小", tint = actionTint)
        }
        IconButton(onClick = { fontSize = (fontSize + 1f).coerceIn(8f, 32f) }) {
            Icon(Icons.Default.ZoomIn, "放大", tint = actionTint)
        }
        if (writing) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = actionTint,
                )
            }
        } else {
            IconButton(onClick = { showOverflow = true }) {
                Icon(Icons.Default.MoreVert, "更多操作", tint = actionTint)
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                    DropdownMenuItem(
                        text = { Text("保存并写入") },
                        enabled = canSave,
                        onClick = {
                            showOverflow = false
                            onSave()
                        },
                    )
                }
            }
        }
    }

    // ── 正文（双皮肤共享）──
    val editorBody: @Composable (PaddingValues) -> Unit = { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(editorBg),
        ) {
            if (loading && base == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在加载…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (base == null) {
                // 读取失败：错误条 + 重试入口，不给出可编辑的空文档（避免误写）。
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = error ?: "无法读取该配置",
                            style = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.error),
                        )
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                }
            } else {
                AnimatedVisibility(
                    visible = !editorVisible,
                    exit = fadeOut(animationSpec = tween(120)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = editorVisible,
                    enter = fadeIn(animationSpec = tween(160)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(Modifier.fillMaxSize().imePadding()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(statusBg)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = editorText.copy(alpha = 0.6f),
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = when {
                                    writing -> "写入中…"
                                    isDirty -> "未保存"
                                    else -> "✓ 已同步"
                                },
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = if (isDirty && !writing) Color(0xFFB7860B)
                                    else editorText.copy(alpha = 0.6f),
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "$lineCount 行 · ${fontSize.toInt()}sp",
                                style = TextStyle(fontSize = 10.sp, color = editorText.copy(alpha = 0.4f)),
                            )
                        }

                        // 语法错误 / 写回失败 / CLI 未解析指针，都在这条状态行里显形。
                        statusMessage?.let { message ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(statusBg)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = message,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        color = if (syntaxError != null || error != null) MaterialTheme.colorScheme.error
                                        else editorText.copy(alpha = 0.6f),
                                    ),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clipToBounds()
                                .background(editorBg),
                            factory = { viewContext ->
                                NativeJsonEditorView(viewContext).apply {
                                    editorHolder.value = this
                                    onTextChanged = { value ->
                                        if (edited != value) onTextChange(value)
                                    }
                                    onFontSizeChanged = { value ->
                                        if (abs(fontSize - value) > 0.01f) fontSize = value
                                    }
                                    setDarkTheme(dark)
                                    setImeBottomInset(imeBottom)
                                    setFontSize(fontSize)
                                    setEditorTextIfChanged(currentText)
                                }
                            },
                            update = { view ->
                                editorHolder.value = view
                                view.onTextChanged = { value ->
                                    if (edited != value) onTextChange(value)
                                }
                                view.onFontSizeChanged = { value ->
                                    if (abs(fontSize - value) > 0.01f) fontSize = value
                                }
                                view.setDarkTheme(dark)
                                view.setImeBottomInset(imeBottom)
                                view.setFontSize(fontSize)
                                view.setEditorTextIfChanged(currentText)
                            },
                            onRelease = { view ->
                                view.dismissInput()
                                if (editorHolder.value === view) editorHolder.value = null
                            },
                        )
                    }
                }
            }
        }
    }

    // ── 外壳：Scaffold + TopAppBar 按 UiMode 分支 ──
    if (isMiuix) {
        MiuixScaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                MiuixTopAppBar(
                    title = title,
                    navigationIcon = {
                        MiuixIconButton(onClick = requestClose) {
                            MiuixIcon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = colorScheme.onSurface,
                            )
                        }
                    },
                    actions = {
                        MiuixIconButton(onClick = onSave, enabled = canSave) {
                            MiuixIcon(
                                imageVector = MiuixIcons.Ok,
                                contentDescription = "保存",
                                tint = if (isDirty) colorScheme.primary else colorScheme.onSurface,
                            )
                        }
                        editorActions()
                    },
                )
            },
        ) { padding -> editorBody(padding) }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = requestClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = onSave,
                            enabled = canSave,
                        ) { Text(if (writing) "保存中…" else "保存") }
                        editorActions()
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding -> editorBody(padding) }
    }

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onConfirm = {
                showDiscardDialog = false
                // 丢弃脏草稿：edited 重置回 base，否则幂等守卫会让残留修改在下次进入时复活
                onRevert()
                navigator.pop()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }
}
