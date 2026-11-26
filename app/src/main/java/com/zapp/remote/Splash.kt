package com.zapp.remote

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Acilis: aurora, sicrayan logo, sok dalgasi, harf harf beliren ZAPP, parlayan yukleme cubugu + acilis sesi. */
@Composable
fun Splash() {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        var mp: MediaPlayer? = null
        try {
            mp = MediaPlayer.create(ctx, R.raw.zapp_intro)
            mp?.setOnCompletionListener { it.release() }
            mp?.start()
        } catch (e: Exception) {
        }
        onDispose {
            try {
                mp?.release()
            } catch (e: Exception) {
            }
        }
    }

    val pop = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val shock = remember { Animatable(0f) }
    val letters = remember { List(4) { Animatable(0f) } }
    val bar = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
    LaunchedEffect(Unit) {
        delay(350)
        shock.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    letters.forEachIndexed { i, a ->
        LaunchedEffect(Unit) {
            delay(800L + i * 130L)
            a.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    LaunchedEffect(Unit) {
        delay(1400)
        rise.animateTo(1f, tween(700))
    }
    LaunchedEffect(Unit) { bar.animateTo(1f, tween(3300, easing = FastOutSlowInEasing)) }

    val inf = rememberInfiniteTransition(label = "splash")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "t")
    val wave by inf.animateFloat(0.2f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "wave")
    val glow by inf.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow")

    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // aurora lekeleri
            for (i in 0..2) {
                val a = (t + i / 3f) * 2f * PI.toFloat()
                val c = Offset(w * (0.5f + 0.35f * cos(a)), h * (0.5f + 0.25f * sin(a * 1.3f)))
                val col = when (i) { 0 -> Violet; 1 -> Cyan; else -> Color(0xFFFF5CA8) }
                drawCircle(
                    Brush.radialGradient(listOf(col.copy(alpha = 0.30f), Color.Transparent), c, w * 0.7f),
                    w * 0.7f, c,
                )
            }
            // sok dalgasi halkalari
            val ctr = Offset(w / 2f, h / 2f)
            val base = 70.dp.toPx()
            for (i in 0..2) {
                val p = (shock.value - i * 0.12f).coerceIn(0f, 1f)
                if (p > 0f && p < 1f) {
                    drawCircle(
                        (if (i % 2 == 0) Cyan else Violet).copy(alpha = (1f - p) * 0.85f),
                        base * (0.6f + 6f * p), ctr, style = Stroke((5f - 3f * p).dp.toPx()),
                    )
                }
            }
            // surekli suzulen halkalar
            for (i in 0..2) {
                val p = (t * 2.5f + i / 3f) % 1f
                drawCircle(
                    (if (i % 2 == 0) Violet else Cyan).copy(alpha = (1f - p) * 0.35f),
                    base * (0.7f + 2f * p), ctr, style = Stroke(2.dp.toPx()),
                )
            }
            // yildiz parcaciklari
            for (i in 0 until 28) {
                val ang = i * 0.618f * 2f * PI.toFloat()
                val rad = (0.15f + (i % 7) / 9f) * w * 0.6f
                val tw = (sin((t * 6f + i) * 2f * PI.toFloat() / 3f) * 0.5f + 0.5f)
                drawCircle(
                    Color.White.copy(alpha = 0.15f + 0.5f * tw),
                    1.2.dp.toPx() + (i % 3).dp.toPx() * 0.6f,
                    Offset(ctr.x + cos(ang + t * 0.8f) * rad, ctr.y + sin(ang + t * 0.8f) * rad * 0.8f),
                )
            }
            // logo arkasi isik
            drawCircle(
                Brush.radialGradient(listOf(Cyan.copy(alpha = 0.35f * glow), Color.Transparent), ctr, base * 2.2f),
                base * 2.2f, ctr,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ZappLogo(
                Modifier
                    .size(150.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                        rotationZ = (1f - pop.value) * -25f
                        alpha = pop.value.coerceIn(0f, 1f)
                    },
                wave = wave,
            )
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                "ZAPP".forEachIndexed { i, ch ->
                    val v = letters[i].value
                    Text(
                        ch.toString(),
                        color = if (i % 2 == 0) Cyan else Ink,
                        fontSize = 60.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer {
                            alpha = v.coerceIn(0f, 1f)
                            translationY = (1f - v) * 70f
                            scaleX = 0.6f + 0.4f * v
                            scaleY = 0.6f + 0.4f * v
                        },
                    )
                }
            }
            Text(
                "Telefonun artık TV'nin kumandası.",
                color = Muted,
                fontSize = 17.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = rise.value
                    translationY = (1f - rise.value) * 24f
                },
            )
            Box(
                Modifier
                    .width(200.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Line)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(bar.value)
                        .fillMaxHeight()
                        .background(Grad)
                )
            }
        }
    }
}
