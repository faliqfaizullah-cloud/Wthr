package com.wthr.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Glassmorphism: translucent tinted gradient fill, bright 1dp edge highlight and a soft shadow. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = Color.White,
    a: Float = 0.55f,
    edge: Color = Color.White
): Modifier = this
    .shadow(10.dp, shape, ambientColor = Color(0x1A000000), spotColor = Color(0x22000000))
    .clip(shape)
    .background(Brush.linearGradient(listOf(tint.copy(alpha = (a + 0.2f).coerceAtMost(1f)), tint.copy(alpha = (a - 0.15f).coerceAtLeast(0.05f)))), shape)
    .border(1.dp, Brush.linearGradient(listOf(edge.copy(alpha = 0.95f), edge.copy(alpha = 0.12f), edge.copy(alpha = 0.5f))), shape)

/** Frosted white background: soft pastel light blobs drifting slowly under a white haze. */
@Composable
fun FrostedBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "frost")
    val d by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "d")
    Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF4EFF9), Color(0xFFEAF5F7))))) {
        Canvas(Modifier.fillMaxSize()) {
            fun blob(c: Color, x: Float, y: Float, r: Float) {
                val ctr = Offset(x * size.width, y * size.height); val rad = r * size.width
                drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), ctr, rad), rad, ctr)
            }
            blob(Color(0x77E9C9F7), 0.15f + (d - .5f) * 0.12f, 0.12f, 0.65f)   // lilac
            blob(Color(0x66FFB3C1), 0.95f - (d - .5f) * 0.10f, 0.38f, 0.55f)   // pink
            blob(Color(0x6674E2E5), 0.20f + (d - .5f) * 0.10f, 0.88f, 0.65f)   // aqua
        }
        content()
    }
}
