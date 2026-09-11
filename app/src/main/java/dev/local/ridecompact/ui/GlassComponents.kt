package dev.local.ridecompact.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.blur
import dev.local.ridecompact.ui.glass.lens
import dev.local.ridecompact.ui.glass.vibrancy
import dev.local.ridecompact.ui.glass.InnerShadow
import dev.local.ridecompact.ui.glass.innerShadow
import dev.local.ridecompact.ui.glass.iosIndicatorSpecular
import dev.local.ridecompact.ui.glass.rememberGravityRotatedHighlight
import dev.local.ridecompact.ui.glass.liquidGlassSurface
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Glass parameters follow KernelSU's floating bottom bar
 * (manager/.../ui/component/FloatingBottomBar.kt): 64dp capsule, 4dp inset,
 * blur 4dp, lens 24dp/24dp, sampling padding 40dp.
 *
 * Every interactive surface passes `indication = null`: without a Material theme
 * foundation falls back to its debug indication, which paints a black rectangle.
 */

val MainTabs = listOf("车辆", "订单", "账号")

/** Backdrop sampled by in-page glass surfaces; null falls back to a flat tint. */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

private val GlassPill: Shape = CircleShape

private val LightGlassBackground = Brush.linearGradient(
    listOf(Color(0xFFE9F1FD), Color(0xFFF6F7F9), Color(0xFFE7F3ED))
)

private val DarkGlassBackground = Brush.linearGradient(
    listOf(Color(0xFF0E1419), Color(0xFF151A21), Color(0xFF111A16))
)

@Composable
fun glassBackground(): Brush = if (isSystemInDarkTheme()) DarkGlassBackground else LightGlassBackground

@Composable
private fun Modifier.glassSurface(
    backdrop: Backdrop?,
    tint: Color,
    shape: Shape = GlassPill,
    refractionHeight: Dp = 16.dp,
    refractionAmount: Dp = 16.dp,
    highlightAlpha: Float = 0.6f,
): Modifier {
    if (backdrop == null) return this.background(tint, shape)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            padding = maxOf(padding, 40.dp.toPx())
            vibrancy()
            blur(4.dp.toPx(), 4.dp.toPx())
            lens(refractionHeight.toPx(), refractionAmount.toPx())
        },
        highlight = { Highlight.Default.copy(alpha = highlightAlpha) },
        onDrawSurface = { drawRect(tint) },
    )
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val backdrop = LocalGlassBackdrop.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = when {
        !enabled -> colors.surfaceContainer.copy(alpha = 0.16f)
        primary -> colors.primary.copy(alpha = 0.72f)
        else -> colors.surfaceContainer.copy(alpha = 0.32f)
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(GlassPill)
            .glassSurface(backdrop, tint)
            .alpha(if (pressed && enabled) 0.72f else 1f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides when {
                !enabled -> colors.onSurfaceVariantSummary
                primary -> colors.onPrimary
                else -> colors.onSurface
            }
        ) { content() }
    }
}

@Composable
fun GlassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MiuixTheme.colorScheme
    val backdrop = LocalGlassBackdrop.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(GlassPill)
            .glassSurface(
                backdrop = backdrop,
                tint = colors.surfaceContainer.copy(alpha = if (enabled) 0.28f else 0.14f),
                refractionHeight = 12.dp,
                refractionAmount = 14.dp,
                highlightAlpha = 0.45f,
            )
            .alpha(if (pressed && enabled) 0.72f else 1f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MiuixTheme.textStyles.button,
            color = if (enabled) colors.primary else colors.onSurfaceVariantSummary,
        )
    }
}

/** iOS 26 alert proportions: 300dp card, 30dp corner radius, capsule action row. */
private val AlertShape: Shape = RoundedCornerShape(30.dp)

private val AlertScrim = Color.Black.copy(alpha = 0.15f)

private val DestructiveRed = Color(0xFFFF3B30)

/**
 * Modal confirmation in the iOS alert style: a centred liquid-glass card with a message and a row
 * of capsule actions. The card reuses the floating bottom bar's glass recipe (vibrancy, 4dp blur,
 * 24dp lens, specular bloom highlight, inner shadow); [confirm] is filled and [dismiss] is glass.
 * Dismisses on scrim tap or system back.
 */
@Composable
fun GlassAlertDialog(
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    dismissText: String = "取消",
    title: String? = null,
    destructive: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    val isInDark = isSystemInDarkTheme()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 900f)) }
    BackHandler(onBack = onDismiss)
    val cardHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = -45f)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AlertScrim.copy(alpha = AlertScrim.alpha * progress.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
        )
        Column(
            modifier = Modifier
                .width(300.dp)
                .graphicsLayer {
                    val scale = 0.94f + 0.06f * progress.value
                    scaleX = scale
                    scaleY = scale
                    alpha = progress.value
                }
                .shadow(elevation = 10.dp, shape = AlertShape, clip = false)
                .then(
                    if (backdrop == null) {
                        Modifier.background(colors.surfaceContainer, AlertShape)
                    } else {
                        Modifier.liquidGlassSurface(
                            backdrop = backdrop,
                            shape = AlertShape,
                            tint = if (isInDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f),
                            blurRadius = 24.dp,
                            depthEffect = true,
                            chromaticAberration = 0.5f,
                            highlightProvider = { cardHighlight.value.copy(alpha = 0.4f) },
                        )
                    }
                )
                .innerShadow(shape = AlertShape) {
                    InnerShadow(
                        radius = 10.dp,
                        color = Color.Black.copy(alpha = 0.15f),
                        alpha = 0.6f,
                    )
                }
                .padding(20.dp),
        ) {
            if (title != null) {
                Text(title, style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
            }
            Text(message, style = MiuixTheme.textStyles.body2)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AlertCapsule(
                    text = dismissText,
                    modifier = Modifier.weight(1f),
                    backdrop = backdrop,
                    glass = true,
                    textColor = colors.onSurface,
                    onClick = onDismiss,
                )
                AlertCapsule(
                    text = confirmText,
                    modifier = Modifier.weight(1f),
                    backdrop = backdrop,
                    glass = false,
                    fillColor = if (destructive) DestructiveRed else colors.primary,
                    textColor = Color.White,
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun AlertCapsule(
    text: String,
    modifier: Modifier,
    backdrop: Backdrop?,
    glass: Boolean,
    textColor: Color,
    fillColor: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    val isInDark = isSystemInDarkTheme()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(CircleShape)
            .then(
                if (glass) {
                    if (backdrop == null) {
                        Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                    } else {
                        Modifier.liquidGlassSurface(
                            backdrop = backdrop,
                            shape = CircleShape,
                            tint = if (isInDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.14f),
                            blurRadius = 24.dp,
                            refractionHeight = 12.dp,
                            refractionAmount = 14.dp,
                            depthEffect = true,
                            highlightProvider = { Highlight.Default.copy(alpha = 0.3f) },
                        )
                    }
                } else {
                    Modifier.background(fillColor, CircleShape)
                }
            )
            .alpha(if (pressed) 0.72f else 1f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MiuixTheme.textStyles.button,
            fontWeight = if (glass) null else FontWeight.SemiBold,
            color = textColor,
        )
    }
}
