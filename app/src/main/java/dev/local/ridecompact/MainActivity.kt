package dev.local.ridecompact

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private val NoCardPadding = PaddingValues(0.dp)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MainMiuixScreen() }
    }

    @Composable private fun MainMiuixScreen() {
        val bike = remember { TextFieldState() }
        var connected by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("无活动订单") }
        var event by remember { mutableStateOf("准备就绪") }
        val session = remember { mutableStateOf<org.json.JSONObject?>(null) }
        LaunchedEffect(Unit) { session.value = try { SessionStore(this@MainActivity).load() } catch (_: Exception) { null } }

        MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp)
                ) {
                    Spacer(Modifier.height(20.dp))
                    Text("轻骑", modifier = Modifier.padding(horizontal = 28.dp), style = MiuixTheme.textStyles.title1)
                    Text(
                        "单车网络控制台",
                        modifier = Modifier.padding(horizontal = 28.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )

                    SmallTitle("账号")
                    Card(modifier = Modifier.padding(horizontal = 12.dp), insideMargin = NoCardPadding) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("登录状态", modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.body1)
                                Text(
                                    if (session.value == null) "未导入账号" else "账号已导入",
                                    style = MiuixTheme.textStyles.body1,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(thickness = 0.6.dp, color = MiuixTheme.colorScheme.dividerLine)
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("连接真实账号", style = MiuixTheme.textStyles.body1)
                                    Text(
                                        "每次打开默认关闭，需手动确认",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                                Switch(checked = connected, onCheckedChange = { connected = it })
                            }
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                text = "手机号验证码登录",
                                onClick = { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    SmallTitle("车辆与操作")
                    Card(modifier = Modifier.padding(horizontal = 12.dp), insideMargin = NoCardPadding) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            TextField(bike, Modifier.fillMaxWidth(), label = "车号")
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    event = "扫码入口已打开"
                                    startActivityForResult(Intent(this@MainActivity, PortraitScanActivity::class.java), 100)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) { Text("扫码") }
                            Spacer(Modifier.height(10.dp))
                            TextButton(
                                text = "预校验 / 计费规则",
                                onClick = { status = "正在预校验 ${bike.text}"; event = "预校验操作已提交" },
                                enabled = connected && bike.text.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                            TextButton(
                                text = "确认开锁",
                                onClick = { status = "等待确认开锁"; event = "开锁需要先完成预校验" },
                                enabled = connected && bike.text.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                            TextButton(
                                text = "还车",
                                onClick = { status = "还车位置校验"; event = "请确认位于运营区内" },
                                enabled = connected,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (!connected) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "开启「连接真实账号」后可执行以上操作",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }

                    SmallTitle("订单状态")
                    Card(modifier = Modifier.padding(horizontal = 12.dp), insideMargin = NoCardPadding) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("当前状态", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Spacer(Modifier.height(2.dp))
                            Text(status, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(thickness = 0.6.dp, color = MiuixTheme.colorScheme.dividerLine)
                            Spacer(Modifier.height(12.dp))
                            Text("事件记录", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Spacer(Modifier.height(2.dp))
                            Text(event, style = MiuixTheme.textStyles.body1)
                            Spacer(Modifier.height(12.dp))
                            TextButton(
                                text = "刷新订单状态",
                                onClick = { event = "订单状态刷新已请求" },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
