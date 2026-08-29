package com.remoteconfig.override.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remoteconfig.override.settings.UiMode
import com.remoteconfig.override.ui.theme.LocalUiMode
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 切换配置/关闭双窗前的“放弃未保存修改”确认弹窗（Miuix / Material 双风格）。
 * 编辑器内部退出走自己的 BackHandler，这里拦截列表侧的切换路径。
 */
@Composable
fun DiscardChangesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> WindowDialog(
            show = true,
            title = "未保存的修改",
            onDismissRequest = onDismiss,
        ) {
            Column(Modifier.fillMaxWidth()) {
                MiuixText("当前编辑内容尚未写入数据库，切换后将丢失，是否继续？", fontSize = 14.sp)
                Row(
                    Modifier.align(Alignment.End).padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    MiuixTextButton(text = "继续编辑", onClick = onDismiss)
                    Spacer(Modifier.width(12.dp))
                    MiuixTextButton(
                        text = "放弃修改",
                        onClick = onConfirm,
                        colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("未保存的修改", fontWeight = FontWeight.SemiBold) },
            text = { Text("当前编辑内容尚未写入数据库，切换后将丢失，是否继续？") },
            confirmButton = {
                FilledTonalButton(onClick = onConfirm) { Text("放弃修改") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("继续编辑") }
            },
        )
    }
}
