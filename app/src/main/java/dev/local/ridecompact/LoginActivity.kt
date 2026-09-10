package dev.local.ridecompact

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
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

class LoginActivity : ComponentActivity() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf("未发送验证码")
    private var confirmedPhone = ""
    private var loginId = java.util.UUID.randomUUID().toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LoginScreen() }
    }

    @Composable private fun LoginScreen() {
        val saved = remember { getSharedPreferences("region", 0) }
        val resolved = remember { mutableStateOf<RegionApi.Region?>(null) }
        var regionStatus by remember { mutableStateOf("未填写；可用当前位置自动填写，也可手动输入") }
        var locating by remember { mutableStateOf(false) }
        val phone = remember { TextFieldState() }
        val code = remember { TextFieldState() }
        val city = remember(resolved.value) { TextFieldState(resolved.value?.cityCode ?: saved.getString("cityCode", "") ?: "") }
        val adCode = remember(resolved.value) { TextFieldState(resolved.value?.adCode ?: saved.getString("adCode", "") ?: "") }
        val consent = remember { mutableStateOf(false) }
        var permissionAsked by remember { mutableStateOf(false) }
        var permissionGranted by remember { mutableStateOf<Boolean?>(null) }
        val resolveRegion: () -> Unit = {
            when {
                locating -> Unit
                !DeviceLocation.permitted(this@LoginActivity) -> {
                    regionStatus = "需要精确定位权限才能自动填写"
                    permissionAsked = true
                }
                else -> {
                    locating = true
                    regionStatus = "正在根据当前位置确定城市和行政区…"
                    scope.launch(Dispatchers.IO) {
                        try {
                            val fix = DeviceLocation.requireGcj02(this@LoginActivity)
                            val region = RegionApi(Gateway.lbs { text -> regionStatus = text }).resolve(fix[0], fix[1])
                            resolved.value = region
                            regionStatus = "已按当前位置填写：" + region.label()
                        } catch (e: Exception) {
                            regionStatus = "自动填写失败，请手动输入：" + ApiResponse.clean(e.message)
                        } finally { locating = false }
                    }
                }
            }
        }
        val locateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            permissionGranted = granted.values.any { it }
        }
        LaunchedEffect(permissionAsked) {
            if (permissionAsked) {
                permissionAsked = false
                locateLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
        LaunchedEffect(permissionGranted) {
            when (permissionGranted) {
                true -> { permissionGranted = null; resolveRegion() }
                false -> { permissionGranted = null; regionStatus = "没有定位权限，请手动填写城市编码和行政区编码" }
                null -> Unit
            }
        }
        LaunchedEffect(Unit) { if (DeviceLocation.permitted(this@LoginActivity)) resolveRegion() }
        val seconds by produceState(0L) {
            while (true) { value = seconds(); kotlinx.coroutines.delay(1000) }
        }
        MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp)
                ) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(text = "返回", onClick = { finish() }, modifier = Modifier.padding(start = 12.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("手机号登录", modifier = Modifier.padding(horizontal = 28.dp), style = MiuixTheme.textStyles.title1)
                    Text(
                        "获取短信验证码并创建本地登录会话",
                        modifier = Modifier.padding(horizontal = 28.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )

                    SmallTitle("账号信息")
                    Card(modifier = Modifier.padding(horizontal = 12.dp), insideMargin = NoCardPadding) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            TextField(
                                phone,
                                modifier = Modifier.fillMaxWidth(),
                                label = "手机号",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TextField(
                                    city,
                                    modifier = Modifier.weight(1f),
                                    label = "城市编码"
                                )
                                TextField(
                                    adCode,
                                    modifier = Modifier.weight(1f),
                                    label = "行政区编码"
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    regionStatus,
                                    modifier = Modifier.weight(1f),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                TextButton(
                                    text = if (locating) "定位中…" else "自动填写",
                                    onClick = { resolveRegion() },
                                    enabled = !locating
                                )
                            }
                        }
                    }

                    SmallTitle("短信验证码")
                    Card(modifier = Modifier.padding(horizontal = 12.dp), insideMargin = NoCardPadding) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            TextField(
                                code,
                                modifier = Modifier.fillMaxWidth(),
                                label = "6 位验证码",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("接收短信验证码", style = MiuixTheme.textStyles.body1)
                                    Text(
                                        "确认使用此手机号接收验证码并登录",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                                Switch(checked = consent.value, onCheckedChange = { consent.value = it })
                            }
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { send(phone.text.toString(), city.text.toString(), adCode.text.toString()) },
                                enabled = !busy && consent.value && seconds == 0L,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) { Text(if (seconds > 0) "${seconds} 秒后可重发" else "获取验证码") }
                            Spacer(Modifier.height(10.dp))
                            TextButton(
                                text = "登录",
                                onClick = { login(phone.text.toString(), code.text.toString(), city.text.toString(), adCode.text.toString()) },
                                enabled = !busy && consent.value,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        message,
                        modifier = Modifier.padding(horizontal = 28.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    private fun seconds(): Long = maxOf(0, minOf(60, (getSharedPreferences("login", 0).getLong("smsUntil", 0) - System.currentTimeMillis() + 999) / 1000))
    private fun api(): LoginApi {
        val prefs = getSharedPreferences("login", 0)
        val client = prefs.getString("clientId", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("clientId", it).apply() }
        val signer = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull() ?: error("签名信息不可用")
        val sig = signer.toByteArray()
        val hash = Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(sig), Base64.NO_WRAP)
        return LoginApi(Gateway.https { text -> message = text }, client, hash)
    }
    private fun send(phone: String, city: String, adCode: String) {
        try { LoginApi.phone(phone) } catch (e: Exception) { message = e.message ?: "手机号格式不正确"; return }
        busy = true; getSharedPreferences("login", 0).edit().putLong("smsUntil", System.currentTimeMillis() + 60000).apply()
        scope.launch(Dispatchers.IO) {
            try { api().send(phone, city, adCode); confirmedPhone = phone; message = "服务器已确认短信发送" }
            catch (e: LoginApi.CaptchaRequired) { message = "需要滑块验证，请使用官方 App 完成验证后重试" }
            catch (e: Exception) { message = ApiResponse.clean(e.message) }
            finally { busy = false }
        }
    }
    private fun login(phone: String, otp: String, city: String, adCode: String) {
        if (confirmedPhone != phone) { message = "请先为当前手机号成功发送验证码"; return }
        busy = true
        scope.launch(Dispatchers.IO) {
            try {
                val incoming = api().login(phone, otp, city, adCode, loginId)
                val store = SessionStore(this@LoginActivity); store.save(incoming)
                getSharedPreferences("region", 0).edit().putString("cityCode", city).putString("adCode", adCode).apply()
                setResult(RESULT_OK); finish()
            } catch (e: Exception) { message = ApiResponse.clean(e.message) }
            finally { busy = false }
        }
    }
}
