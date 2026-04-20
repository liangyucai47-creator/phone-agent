package com.phoneagent.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phoneagent.CommandParser
import com.phoneagent.PhoneControlForegroundService
import com.phoneagent.PhoneControlService
import com.phoneagent.PhoneControlWebSocketServer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    MainScreen()
                }
            }
        }
        // 启动前台服务
        startForegroundService(Intent(this, PhoneControlForegroundService::class.java))
    }
}

/** 获取 WiFi IP 地址 */
fun getWifiIpAddress(context: Context): String {
    return try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val ip = wifiManager.connectionInfo.ipAddress
        ((ip shr 0) and 0xFF).toString() + "." +
        ((ip shr 8) and 0xFF).toString() + "." +
        ((ip shr 16) and 0xFF).toString() + "." +
        ((ip shr 24) and 0xFF).toString()
    } catch (e: Exception) {
        "未知"
    }
}

/** 检查无障碍服务是否已开启 */
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val service = "${context.packageName}/${PhoneControlService::class.java.canonicalName}"
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return enabled?.contains(service) == true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var isServiceEnabled by remember {
        mutableStateOf(isAccessibilityServiceEnabled(context))
    }
    var screenTree by remember { mutableStateOf("") }
    var commandInput by remember { mutableStateOf("") }
    val logs = remember { mutableStateListOf<CommandParser.CommandResult>() }
    var showTreeDialog by remember { mutableStateOf(false) }

    // 每次重组时刷新状态
    LaunchedEffect(Unit) {
        while (true) {
            isServiceEnabled = isAccessibilityServiceEnabled(context)
            kotlinx.coroutines.delay(2000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone Agent") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // === 服务状态 ===
            val statusColor = if (isServiceEnabled)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.error

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isServiceEnabled) "✅ 无障碍服务已开启" else "❌ 无障碍服务未开启",
                        color = statusColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // === WebSocket 连接信息 ===
            val wsIp = remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                wsIp.value = getWifiIpAddress(context)
            }
            val wsRunning = PhoneControlWebSocketServer.instance != null
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (wsRunning) "🌐 WebSocket 服务已启动" else "⏳ WebSocket 服务启动中...",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (wsRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (wsIp.value != "未知" && wsIp.value.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = "ws://${wsIp.value}:19876",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        TextButton(onClick = {
                            clipboardManager.setText(AnnotatedString("ws://${wsIp.value}:19876"))
                            android.widget.Toast.makeText(context, "已复制 WebSocket 地址", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Text("复制地址", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // === 操作按钮 ===
            // 打开无障碍设置
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("打开无障碍设置")
            }

            // 读取 UI 树
            Button(
                onClick = {
                    val service = PhoneControlService.instance
                    if (service == null) {
                        Toast.makeText(context, "服务未连接", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    screenTree = service.readScreenTree()
                    showTreeDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("读取 UI 树")
            }

            // 系统导航按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavigationButton("返回", Icons.Default.ArrowBack) {
                    PhoneControlService.instance?.pressBack()
                        ?: Toast.makeText(context, "服务未连接", Toast.LENGTH_SHORT).show()
                }
                NavigationButton("首页", Icons.Default.Home) {
                    PhoneControlService.instance?.pressHome()
                        ?: Toast.makeText(context, "服务未连接", Toast.LENGTH_SHORT).show()
                }
                NavigationButton("最近", Icons.Default.List) {
                    PhoneControlService.instance?.pressRecent()
                        ?: Toast.makeText(context, "服务未连接", Toast.LENGTH_SHORT).show()
                }
            }

            HorizontalDivider()

            // === 指令输入 ===
            Text("指令输入", style = MaterialTheme.typography.titleSmall)

            Text(
                text = "支持: click(x,y) / swipe(x1,y1,x2,y2) / tap(\"文本\") / tapDesc(\"描述\") / input(\"文字\") / back / home / recent / tree",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    label = { Text("输入指令") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(onClick = {
                    if (commandInput.isBlank()) return@Button
                    val service = PhoneControlService.instance
                    if (service == null) {
                        logs.add(
                            CommandParser.CommandResult(
                                command = commandInput,
                                result = "错误: 服务未连接",
                                success = false
                            )
                        )
                        commandInput = ""
                        return@Button
                    }
                    val result = CommandParser.execute(commandInput, service)
                    logs.add(result)
                    commandInput = ""
                }) {
                    Text("执行")
                }
            }

            HorizontalDivider()

            // === 执行日志 ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("执行日志", style = MaterialTheme.typography.titleSmall)
                if (logs.isNotEmpty()) {
                    TextButton(onClick = { logs.clear() }) {
                        Text("清空", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (logs.isEmpty()) {
                Text(
                    text = "暂无日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 显示最近 50 条
                logs.takeLast(50).forEach { log ->
                    val color = if (log.success)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.error
                    SelectionContainer {
                        Text(
                            text = "[${log.timestamp}] ${log.command}\n  → ${log.result}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = color,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }

    // === UI 树弹窗 ===
    if (showTreeDialog && screenTree.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showTreeDialog = false },
            title = { Text("UI 树") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            clipboardManager.setText(AnnotatedString(screenTree))
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.List, contentDescription = "复制")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("复制")
                        }
                    }
                    SelectionContainer {
                        Text(
                            text = screenTree,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTreeDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun RowScope.NavigationButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
    ) {
        Icon(icon, contentDescription = text, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
