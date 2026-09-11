// Adapted from compose-miuix-ui example (IosLiquidGlassNavigationBar) — Apache 2.0.

package dev.local.ridecompact.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight

/**
 * The floating bottom bar's glass recipe, shared so other surfaces render identically:
 * 40dp sampling padding, vibrancy, blur, capsule lens and the dual-peak specular bloom highlight.
 *
 * [tint] is painted by `onDrawSurface`, so it sits on top of the refracted backdrop.
 */
fun Modifier.liquidGlassSurface(
    backdrop: Backdrop,
    shape: Shape,
    tint: Color,
    blurRadius: Dp = 4.dp,
    refractionHeight: Dp = 24.dp,
    refractionAmount: Dp = 24.dp,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f,
    highlightAlpha: Float = 0.75f,
    highlightProvider: (BackdropEffectScope.() -> Highlight)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        padding = maxOf(padding, 40.dp.toPx())
        vibrancy()
        blur(blurRadius.toPx(), blurRadius.toPx())
        lens(
            refractionHeight = refractionHeight.toPx(),
            refractionAmount = refractionAmount.toPx(),
            depthEffect = depthEffect,
            chromaticAberration = chromaticAberration,
        )
    },
    highlight = highlightProvider ?: { Highlight.Default.copy(alpha = highlightAlpha) },
    layerBlock = layerBlock,
    onDrawSurface = { drawRect(tint) },
)
