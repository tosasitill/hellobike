package dev.local.ridecompact

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.skydoves.cloudy.liquidGlass
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private val DemoBackdrop = Brush.linearGradient(
    listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF7C3AED), Color(0xFFDB2777))
)

/**
 * Offline playground for the Cloudy liquid glass lens. Not part of the ride flow:
 * launch with `adb shell am start -n dev.local.ridecompact/.GlassDemoActivity`.
 */
class GlassDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GlassDemoScreen() }
    }
}

@Composable
private fun GlassDemoScreen() {
    val density = LocalDensity.current
    val lensSize = with(density) { 300.dp.toPx() }
    val cornerRadius = with(density) { 84.dp.toPx() }
    var lensCenter by remember { mutableStateOf(Offset.Zero) }
    val fullEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(DemoBackdrop)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            if (lensCenter == Offset.Zero) {
                                lensCenter = Offset(size.width / 2f, size.height / 2f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                lensCenter += dragAmount
                                change.consume()
                            }
                        }
                        .liquidGlass(
                            lensCenter = lensCenter,
                            lensSize = Size(lensSize, lensSize),
                            cornerRadius = cornerRadius,
                            refraction = 0.35f,
                            curve = 0.25f,
                            dispersion = 0.12f,
                            saturation = 1.05f,
                            edge = 0.2f,
                            enabled = lensCenter != Offset.Zero,
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(DemoBackdrop))
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Spacer(Modifier.height(28.dp))
                        Text("液态玻璃 Demo", style = MiuixTheme.textStyles.title1, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (fullEffect) {
                                "RuntimeShader (AGSL) · 全效果"
                            } else {
                                "API " + Build.VERSION.SDK_INT + " · 降级路径，无折射"
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "拖动屏幕移动玻璃透镜",
                            style = MiuixTheme.textStyles.footnote1,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(28.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            "Cloudy 1.0.0-alpha01 · skydoves",
                            style = MiuixTheme.textStyles.footnote1,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                }
            }
        }
    }
}
