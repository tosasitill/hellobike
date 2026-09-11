package dev.local.ridecompact

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import dev.local.ridecompact.ui.glassBackground
import dev.local.ridecompact.ui.GlassButton
import dev.local.ridecompact.ui.GlassAlertDialog
import dev.local.ridecompact.ui.GlassTextButton
import dev.local.ridecompact.ui.LocalGlassBackdrop
import dev.local.ridecompact.ui.MainTabs
import dev.local.ridecompact.ui.glass.FloatingBottomBar
import dev.local.ridecompact.ui.glass.rememberCombinedBackdrop
import dev.local.ridecompact.ui.glass.FloatingBottomBarItem
import java.util.concurrent.Executors

private val NoCardPadding = PaddingValues(0.dp)

private enum class Confirmation { OPEN, CLOSE }

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val ride = RideState()
    private val messages = ArrayList<String>()
    private lateinit var sessions: SessionStore
    private lateinit var flow: RideFlow

    private var busy by mutableStateOf(false)
    private var authorized by mutableStateOf(false)
    private var connected by mutableStateOf(false)
    private var accountText by mutableStateOf("未导入账号")
    private var vehicleText by mutableStateOf("尚未选择车辆")
    private var statusText by mutableStateOf("无活动订单")
    private var logText by mutableStateOf("准备就绪")
    private var priceRule by mutableStateOf("")
    private var confirmation by mutableStateOf<Confirmation?>(null)
    private var confirmationText by mutableStateOf("")
    private var locationRequested by mutableStateOf(false)

    private var locationManager: LocationManager? = null
    private val warmUp = LocationListener { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TEMP window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        sessions = SessionStore(this)
        flow = RideFlow(ride, ::api, ::currentLocation, ::persist, ::log, { Thread.sleep(it) }, { System.currentTimeMillis() }, { SystemClock.elapsedRealtime() })
        restore()
        update()
        enableEdgeToEdge()
        setContent { MainMiuixScreen() }
    }

    override fun onDestroy() {
        stopWarmUp()
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun restore() {
        val prefs = getSharedPreferences("ride", 0)
        ride.order = prefs.getString("order", "") ?: ""
        ride.bike = prefs.getString("bike", "") ?: ""
        ride.specialBike = prefs.getBoolean("specialBike", false)
        val previous = prefs.getString("stage", "IDLE") ?: "IDLE"
        if (previous != "IDLE" && previous != "ENDED") {
            ride.stage = if (previous == "CLOSING") RideState.Stage.CLOSING else RideState.Stage.UNKNOWN
        }
        if (!ride.canPrepare() && (prefs.getString("owner", "") ?: "").isEmpty()) {
            try {
                sessions.forReplacement()?.let { prefs.edit().putString("owner", it.getString("userGuid")).commit() }
            } catch (ignored: Exception) { }
        }
    }

    private fun persist() {
        val edit = getSharedPreferences("ride", 0).edit()
            .putString("order", ride.order).putString("bike", ride.bike)
            .putBoolean("specialBike", ride.specialBike).putString("stage", ride.stage.name)
        if (ride.canPrepare()) edit.remove("owner") else try {
            sessions.forReplacement()?.let { edit.putString("owner", it.getString("userGuid")) }
        } catch (ignored: Exception) { }
        edit.commit()
    }

    private fun replaceSession(incoming: JSONObject) {
        val previous = sessions.forReplacement()
        val owner = getSharedPreferences("ride", 0).getString("owner", previous?.optString("userGuid") ?: "") ?: ""
        if (!ride.canPrepare() && owner.isNotEmpty() && owner != incoming.getString("userGuid")) {
            throw IllegalStateException("存在待确认订单，仅能更新同一账号的会话")
        }
        sessions.save(incoming)
    }

    private fun initialRegion(): Pair<String, String> {
        val prefs = getSharedPreferences("region", 0)
        val session = try { sessions.forReplacement() } catch (ignored: Exception) { null }
        val city = session?.optString("cityCode").orEmpty().ifEmpty { prefs.getString("cityCode", "") ?: "" }
        val district = session?.optString("adCode").orEmpty().ifEmpty { prefs.getString("adCode", "") ?: "" }
        return city to district
    }

    private fun afterSessionReplaced() {
        if (ride.canPrepare()) ride.clear()
        persist()
        connected = false
        update()
    }

    private fun api(): RideApi {
        val session = sessions.load() ?: throw IllegalStateException("请先导入账号或登录")
        return RideApi(session, Gateway.https { text -> log(text) }, bluetoothEnabled())
    }

    private fun bluetoothEnabled(): Boolean = try {
        (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
    } catch (ignored: Exception) {
        false
    }

    private fun currentLocation(): DoubleArray {
        if (!DeviceLocation.permitted(this)) throw IllegalStateException("需要精确定位权限才能预校验或还车")
        return DeviceLocation.requireGcj02(this)
    }

    private fun statusLabel(): String = when (ride.stage) {
        RideState.Stage.PREPARED -> "等待确认开锁"
        RideState.Stage.OPENING -> "开锁请求已提交，等待确认"
        RideState.Stage.RIDING -> "骑行中"
        RideState.Stage.CLOSING -> "关锁请求已提交，等待确认"
        RideState.Stage.UNKNOWN -> "订单状态待确认"
        RideState.Stage.ENDED -> "骑行已结束"
        else -> "无活动订单"
    }

    private fun update() {
        val session = try {
            sessions.load()
        } catch (e: SessionStore.RecoveryRequired) {
            accountText = "本地密钥失效，请重新导入或短信登录"
            authorized = false
            null
        } catch (e: Exception) {
            accountText = "账号读取失败：" + e.javaClass.simpleName
            authorized = false
            null
        }
        if (session != null) {
            authorized = true
            accountText = "账号已导入 · 城市 " + session.getString("cityCode") + " / " + session.getString("adCode")
        } else if (accountText == "未导入账号") {
            authorized = false
        }
        vehicleText = if (ride.bike.isEmpty()) "尚未选择车辆"
        else "车辆 " + mask(ride.bike) + if (ride.specialBike) " · 服务端识别的特殊车型" else ""
        statusText = statusLabel() + if (ride.order.isEmpty()) "" else "\n订单 " + mask(ride.order)
        priceRule = flow.priceRule
    }

    private fun syncFromFlow() {
        update()
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else runOnUiThread(block)
    }

    private fun log(text: String) {
        val snapshot: String
        synchronized(messages) {
            messages.add(0, text)
            while (messages.size > 40) messages.removeAt(messages.size - 1)
            snapshot = messages.take(8).joinToString("\n")
        }
        post { logText = snapshot }
    }

    private fun error(text: String) {
        log(text)
        post { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    }

    private fun describe(failure: Throwable): String = when (failure) {
        is ApiResponse.Failure -> failure.message ?: "请求失败"
        is org.json.JSONException -> "JSON 格式不正确"
        else -> ApiResponse.clean(failure.message)
    }

    private fun task(title: String, onDone: (() -> Unit)? = null, block: () -> Unit) {
        if (busy) return
        busy = true
        log(title + "…")
        worker.execute {
            var failure: Exception? = null
            try { block() } catch (e: Exception) { failure = e }
            post {
                busy = false
                val thrown = failure
                if (thrown != null) error(title + "：" + describe(thrown)) else onDone?.invoke()
                syncFromFlow()
            }
        }
    }

    @SuppressLint("MissingPermission") // guarded by DeviceLocation.permitted below; SecurityException is caught per provider
    private fun startWarmUp() {
        if (!DeviceLocation.permitted(this)) return
        val manager = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return
        locationManager = manager
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (manager.isProviderEnabled(provider)) manager.requestLocationUpdates(provider, 3000L, 0f, warmUp)
            } catch (ignored: Exception) { }
        }
    }

    private fun stopWarmUp() {
        val manager = locationManager ?: return
        locationManager = null
        try { manager.removeUpdates(warmUp) } catch (ignored: Exception) { }
    }

    private fun readDocument(uri: Uri): String {
        val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("文件无法读取")
        val text = input.use { stream ->
            val buffer = ByteArray(8192)
            val output = StringBuilder()
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                total += read
                if (total > 16 * 1024 * 1024) throw IllegalStateException("文件超过 16MB")
                output.append(String(buffer, 0, read, Charsets.UTF_8))
            }
            output.toString()
        }
        return text
    }

    @Composable private fun MainMiuixScreen() {
        val bike = remember { TextFieldState(ride.bike) }
        val tokenInput = remember { TextFieldState() }
        var tokenPanel by remember { mutableStateOf(false) }
        val regionDefaults = remember(tokenPanel) { initialRegion() }
        val cityInput = remember(tokenPanel) { TextFieldState(regionDefaults.first) }
        val adInput = remember(tokenPanel) { TextFieldState(regionDefaults.second) }
        var tick by remember { mutableStateOf(0L) }
        LaunchedEffect(Unit) { while (true) { delay(1000L); tick = System.currentTimeMillis() } }

        val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
            val contents = result?.contents
            if (contents != null) {
                task("扫码", onDone = { bike.setTextAndPlaceCursorAtEnd(ride.bike) }) {
                    flow.selectBike(contents)
                }
            }
        }
        val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                connected = false
                update()
                log("账号已更新，请刷新订单状态")
            }
        }
        val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            task("导入账号", block = {
                replaceSession(SessionStore.validate(AccountImporter.parse(readDocument(uri))))
            }, onDone = {
                afterSessionReplaced()
                log("已导入账号，未连接业务服务器")
            })
        }
        val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            task("导出诊断", block = {
                val snapshot = "RideCompact · Android " + android.os.Build.VERSION.SDK_INT + "\n" + synchronized(messages) { messages.joinToString("\n") }
                contentResolver.openOutputStream(uri)?.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) } ?: throw IllegalStateException("文件无法写入")
            })
        }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.any { it }) {
                startWarmUp()
                connected = true
                update()
                log("已连接真实账号")
            } else {
                log("没有定位权限，无法预校验或还车")
            }
        }
        LaunchedEffect(locationRequested) {
            if (locationRequested) {
                locationRequested = false
                permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
        DisposableEffect(connected) {
            if (connected) startWarmUp()
            onDispose { stopWarmUp() }
        }

        val canOpen = remember(tick, busy, connected, authorized, confirmation) { flow.canOpen() }
        val canClose = remember(tick, busy, connected, authorized, confirmation) { flow.canClose() }
        val canPrepare = remember(busy, connected, authorized, confirmation) { flow.canPrepare() }
        val ready = connected && authorized && !busy && confirmation == null

        val glassBackground = rememberLayerBackdrop()
        val glassContent = rememberLayerBackdrop()
        val glassBar = rememberCombinedBackdrop(glassBackground, glassContent)
        var tab by remember { mutableIntStateOf(0) }

        MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(glassBackground)
                            .background(glassBackground())
                    )
                    CompositionLocalProvider(LocalGlassBackdrop provides glassBackground) {
                        Box(modifier = Modifier.fillMaxSize().layerBackdrop(glassContent)) {
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
                                if (tab == 0) {
                                    SmallTitle("车辆与操作")
                                    Card(modifier = Modifier.padding(horizontal = 12.dp), insideMargin = NoCardPadding) {
                                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                            TextField(bike, Modifier.fillMaxWidth(), label = "车号")
                                            Spacer(Modifier.height(12.dp))
                                            GlassButton(
                                                onClick = {
                                                    val options = ScanOptions()
                                                    options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                                    options.setPrompt("")
                                                    options.setBeepEnabled(false)
                                                    options.setCaptureActivity(PortraitScanActivity::class.java)
                                                    options.setOrientationLocked(true)
                                                    scanner.launch(options)
                                                },
                                                enabled = !busy && flow.canPrepare(),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) { Text("扫码") }
                                            Spacer(Modifier.height(10.dp))
                                            GlassTextButton(
                                                text = "预校验 / 计费规则",
                                                onClick = { task("预校验") { flow.prepare(bike.text.toString()) } },
                                                enabled = ready && canPrepare && bike.text.isNotBlank(),
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            GlassTextButton(
                                                text = "确认开锁",
                                                onClick = {
                                                    confirmationText = buildString {
                                                        append("车辆 ").append(mask(ride.bike))
                                                        if (priceRule.isNotEmpty()) append("\n计费规则：").append(priceRule)
                                                        append("\n\n将创建真实骑行订单。")
                                                    }
                                                    confirmation = Confirmation.OPEN
                                                },
                                                enabled = ready && canOpen,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            GlassTextButton(
                                                text = "还车",
                                                onClick = {
                                                    task("还车位置校验", block = { flow.checkClose() }, onDone = {
                                                        confirmationText = "车辆 " + mask(ride.bike) + "\n当前还车校验已通过，是否提交关锁？"
                                                        confirmation = Confirmation.CLOSE
                                                    })
                                                },
                                                enabled = ready && canClose,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            GlassTextButton(
                                                text = "打开官方 App",
                                                onClick = {
                                                    val intent = packageManager.getLaunchIntentForPackage("com.jingyao.easybike")
                                                    if (intent == null) error("未安装官方 App") else startActivity(intent)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            Text(vehicleText, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
                                            if (priceRule.isNotEmpty()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text("计费规则：" + priceRule, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.primary)
                                            }
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

                                }
                                if (tab == 1) {
                                    SmallTitle("订单状态")
                                    Card(modifier = Modifier.padding(horizontal = 12.dp), insideMargin = NoCardPadding) {
                                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                            Text("当前状态", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                            Spacer(Modifier.height(2.dp))
                                            Text(statusText, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Medium)
                                            Spacer(Modifier.height(12.dp))
                                            HorizontalDivider(thickness = 0.6.dp, color = MiuixTheme.colorScheme.dividerLine)
                                            Spacer(Modifier.height(12.dp))
                                            Text("事件记录", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                            Spacer(Modifier.height(2.dp))
                                            Text(logText, style = MiuixTheme.textStyles.body1)
                                            Spacer(Modifier.height(12.dp))
                                            GlassTextButton(
                                                text = "刷新订单状态",
                                                onClick = { task("刷新订单") { flow.refresh() } },
                                                enabled = ready,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            GlassTextButton(
                                                text = "导出诊断记录",
                                                onClick = { exporter.launch("ride-compact-diagnostic.txt") },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                                if (tab == 2) {
                                    SmallTitle("账号")
                                    Card(modifier = Modifier.padding(horizontal = 12.dp), insideMargin = NoCardPadding) {
                                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("登录状态", modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.body1)
                                                Text(
                                                    accountText,
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
                                                Switch(
                                                    checked = connected,
                                                    onCheckedChange = { checked ->
                                                        if (!checked) {
                                                            connected = false
                                                            update()
                                                        } else if (DeviceLocation.permitted(this@MainActivity)) {
                                                            connected = true
                                                            update()
                                                            log("已连接真实账号")
                                                        } else {
                                                            locationRequested = true
                                                        }
                                                    }
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            GlassButton(
                                                onClick = { login.launch(Intent(this@MainActivity, LoginActivity::class.java)) },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) { Text("手机号验证码登录") }
                                            Spacer(Modifier.height(10.dp))
                                            GlassTextButton(
                                                text = if (tokenPanel) "收起 Token 登录" else "Token 登录",
                                                onClick = { tokenPanel = !tokenPanel },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            if (tokenPanel) {
                                                Spacer(Modifier.height(8.dp))
                                                TextField(tokenInput, Modifier.fillMaxWidth(), label = "token 或会话内容")
                                                Spacer(Modifier.height(10.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    TextField(cityInput, modifier = Modifier.weight(1f), label = "城市编码")
                                                    TextField(adInput, modifier = Modifier.weight(1f), label = "行政区编码")
                                                }
                                                Spacer(Modifier.height(10.dp))
                                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "可粘贴完整会话 JSON 或 token=… 键值文本；只粘贴 token 时沿用本机已保存的用户号",
                                                        modifier = Modifier.weight(1f),
                                                        style = MiuixTheme.textStyles.footnote1,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                                    )
                                                    GlassTextButton(
                                                        text = "自动填写",
                                                        onClick = {
                                                            task("定位行政区", block = {
                                                                val fix = DeviceLocation.requireGcj02(this@MainActivity)
                                                                val resolved = RegionApi(Gateway.lbs { text -> log(text) }).resolve(fix[0], fix[1])
                                                                getSharedPreferences("region", 0).edit()
                                                                    .putString("cityCode", resolved.cityCode).putString("adCode", resolved.adCode).apply()
                                                                post {
                                                                    cityInput.edit { replace(0, length, resolved.cityCode) }
                                                                    adInput.edit { replace(0, length, resolved.adCode) }
                                                                }
                                                            })
                                                        },
                                                        enabled = !busy
                                                    )
                                                }
                                                Spacer(Modifier.height(10.dp))
                                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                    GlassTextButton(
                                                        text = "取消",
                                                        onClick = { tokenPanel = false },
                                                        modifier = Modifier.weight(1f),
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    GlassButton(
                                                        onClick = {
                                                            task("Token 登录", block = {
                                                                replaceSession(SessionStore.validate(TokenSession.parse(
                                                                    tokenInput.text.toString(), sessions.forReplacement(),
                                                                    cityInput.text.toString().trim(), adInput.text.toString().trim()
                                                                )))
                                                            }, onDone = {
                                                                tokenPanel = false
                                                                afterSessionReplaced()
                                                                log("已用 token 登录，未连接业务服务器")
                                                            })
                                                        },
                                                        enabled = !busy,
                                                        modifier = Modifier.weight(1f),
                                                    ) { Text("保存并登录") }
                                                }
                                                Spacer(Modifier.height(8.dp))
                                            }
                                            Spacer(Modifier.height(10.dp))
                                            GlassTextButton(
                                                text = "导入账号 / HAR",
                                                onClick = { importer.launch(arrayOf("*/*")) },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            GlassTextButton(
                                                text = "清除本地账号",
                                                onClick = {
                                                    if (!ride.canPrepare()) {
                                                        error("仍有待确认订单；可以导入同一账号的新会话，不能清除账号")
                                                    } else task("清除账号", block = { sessions.clear() }, onDone = {
                                                        connected = false
                                                        update()
                                                    })
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }

                                }
                                Spacer(Modifier.height(140.dp))
                            }
                        }
                    }
                    FloatingBottomBar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 28.dp, end = 28.dp, bottom = 18.dp),
                        selectedIndex = tab,
                        onSelected = { tab = it },
                        backdrop = glassBar,
                        tabsCount = MainTabs.size,
                    ) { activateTab ->
                        MainTabs.forEachIndexed { index, label ->
                            FloatingBottomBarItem(
                                selected = tab == index,
                                onClick = { activateTab(index) },
                                modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                            ) {
                                Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    val pending = confirmation
                    if (pending != null) {
                        val opening = pending == Confirmation.OPEN
                        GlassAlertDialog(
                            title = if (opening) "确认开锁" else "确认还车",
                            message = confirmationText,
                            confirmText = if (opening) "确认开锁" else "确认还车",
                            onConfirm = {
                                confirmation = null
                                if (opening) task("提交开锁") { flow.open() } else task("提交关锁") { flow.commitClose() }
                            },
                            onDismiss = { confirmation = null },
                            backdrop = glassBar,
                            destructive = !opening,
                        )
                    }
                }
            }
        }
    }
}
