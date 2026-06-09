package com.remoteconfig.override.ui.screens

import android.content.Intent
import android.net.Uri
import android.system.Os
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remoteconfig.override.R
import com.remoteconfig.override.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    viewModel: MainViewModel,
    isActive: Boolean = true,
    onNavigateConfig: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val systemStatus by viewModel.systemStatus.collectAsState()
    val cosaVersion by viewModel.cosaVersion.collectAsState()
    val context = LocalContext.current

    val kernelVersion = remember {
        try { Os.uname().release } catch (_: Exception) { "未知" }
    }

    var showDonateDialog by remember { mutableStateOf(false) }
    var donateImageId by remember { mutableIntStateOf(0) }

    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = { Text("Color云控修改", style = MaterialTheme.typography.headlineLarge) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    scrollBehavior = scrollBehavior
                )
            },
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Root 状态卡 ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            systemStatus.isRooted && systemStatus.dbAvailable -> MaterialTheme.colorScheme.primaryContainer
                            systemStatus.isRooted -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                systemStatus.isRooted && systemStatus.dbAvailable -> Icons.Filled.Verified
                                systemStatus.isRooted -> Icons.Filled.Warning
                                else -> Icons.Filled.GppBad
                            },
                            contentDescription = null,
                            tint = when {
                                systemStatus.isRooted && systemStatus.dbAvailable -> MaterialTheme.colorScheme.onPrimaryContainer
                                systemStatus.isRooted -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    systemStatus.isRooted && systemStatus.dbAvailable -> "Root 权限正常"
                                    systemStatus.isRooted -> "数据库连接失败"
                                    else -> "未授予 Root 权限"
                                },
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                color = when {
                                    systemStatus.isRooted && systemStatus.dbAvailable -> MaterialTheme.colorScheme.onPrimaryContainer
                                    systemStatus.isRooted -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                            Text(
                                text = when {
                                    systemStatus.isRooted && systemStatus.dbAvailable -> "数据库已连接，可读写配置"
                                    systemStatus.isRooted -> "已获取 Root 权限，但数据库文件不可访问"
                                    else -> "请授予 Root 权限后重启应用"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    systemStatus.isRooted && systemStatus.dbAvailable -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    systemStatus.isRooted -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    else -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                }
                            )
                        }
                    }
                }

                // ── 设备信息卡 ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(24.dp)) {
                        InfoCardItem(label = "设备型号", content = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", icon = Icons.Filled.Smartphone)
                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(label = "安卓版本", content = "Android ${android.os.Build.VERSION.RELEASE}", icon = Icons.Filled.Android)
                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(label = "内核版本", content = kernelVersion, icon = Icons.Filled.Memory)
                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(label = "应用增强服务", content = "v${cosaVersion} · com.oplus.cosa", icon = Icons.Filled.SettingsSuggest)
                    }
                }

                // ── 作者卡片 ──
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        // 作者信息
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coolapk.com/u/1404550"))) }
                                catch (_: Exception) {}
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(painter = painterResource(id = R.drawable.author_avatar),
                                contentDescription = "作者头像", modifier = Modifier.size(64.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Smartisan_Apple", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("酷安 @Smartisan_Apple", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text("Color云控修改 v1.2.1", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                            Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        // 捐赠描述
                        Text(
                            text = "Color云控修改始终保持免费，向开发者捐赠以表示支持。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        // 捐赠入口
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { donateImageId = R.drawable.wechat; showDonateDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_wechat), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("微信", style = MaterialTheme.typography.bodyMedium)
                            }
                            OutlinedButton(
                                onClick = { donateImageId = R.drawable.alipay; showDonateDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(painter = painterResource(id = R.drawable.ic_alipay), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("支付宝", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                // ── 开源仓库卡片 ──
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Yunnijian/Oplus_RemoteConfig_Override")))
                        } catch (_: Exception) {}
                    },
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painter = painterResource(id = R.drawable.author_avatar),
                            contentDescription = "GitHub 头像", modifier = Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("查看源代码", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("在 GitHub 上查看源代码", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // ── 捐赠弹窗 ──
    if (showDonateDialog) {
        AlertDialog(
            onDismissRequest = { showDonateDialog = false },
            confirmButton = { TextButton(onClick = { showDonateDialog = false }) { Text("关闭") } },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = donateImageId),
                        contentDescription = null,
                        modifier = Modifier.width(260.dp).heightIn(max = 400.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        )
    }
}

@Composable
private fun InfoCardItem(label: String, content: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 20.dp).size(24.dp))
        }
        Column {
            Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = content, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
