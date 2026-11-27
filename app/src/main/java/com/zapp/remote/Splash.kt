package com.zapp.remote

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun smooth(x: Float) = x * x * (3f - 2f * x)

/**
 * Acilis: beyaz bir nokta yukaridan duser, havada donar, etrafi mor isikla dolar,
 * nokta bir internet (dunya) simgesine donusur, sonra ZAPP yazisi belirir. Ses ayni zaman cizelgesine gore.
 */
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

    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) { clock.animateTo(3.8f, tween(3800, easing = LinearEasing)) }
    val purple = Violet
    val white = Color.White

    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            val w = size.width
            val h = size.height
            val ctr = Offset(w / 2f, h * 0.40f)
            val d = density
            val R = min(w, h) * 0.19f
            val dotR = 7f * d

            // 1) arka plan morlasir
            val aura = smooth(seg(t, 0.95f, 2.0f))
            drawRect(
                Brush.radialGradient(
                    listOf(purple.copy(alpha = 0.55f * aura), Color(0xFF1A1040).copy(alpha = 0.6f * aura), Color.Transparent),
                    ctr, maxOf(w, h) * (0.35f + 0.5f * aura),
                )
            )
            // 2) dusen nokta (hizlanarak) + iz
            val fall = seg(t, 0f, 0.95f)
            val y = if (t < 0.95f) (-30f * d) + (ctr.y + 30f * d) * fall * fall else ctr.y
            if (t < 0.95f) {
                val sp = fall * 26f * d
                for (k in 1..10) {
                    drawCircle(white.copy(alpha = (1f - k / 10f) * 0.45f * fall), dotR * (1f - k * 0.07f), Offset(ctr.x, y - k * sp * 0.5f))
                }
            }
            // 3) havada donma: sok halkalari + kuvvetli parlama
            val hit = seg(t, 0.95f, 2.0f)
            if (t >= 0.95f) {
                for (i in 0..2) {
                    val p = seg(t, 0.95f + i * 0.14f, 1.9f + i * 0.14f)
                    if (p > 0f && p < 1f) {
                        val col = if (i == 0) white else purple
                        drawCircle(col.copy(alpha = (1f - p) * 0.8f), R * (0.2f + 3.2f * smooth(p)), ctr, style = Stroke((4f - 2.5f * p) * d))
                    }
                }
            }
            // 4) dönen kıvılcımlar
            if (aura > 0f) {
                for (i in 0 until 18) {
                    val a = (i / 18f) * 2f * PI.toFloat() + t * 1.4f * (if (i % 2 == 0) 1f else -0.8f)
                    val rad = R * (1.3f + 0.35f * sin(t * 3f + i)) * (0.6f + 0.4f * aura)
                    val tw = (sin(t * 7f + i * 1.7f) * 0.5f + 0.5f)
                    drawCircle(
                        (if (i % 3 == 0) white else Color(0xFFB794FF)).copy(alpha = aura * (0.25f + 0.6f * tw)),
                        (1.5f + (i % 3)) * d, Offset(ctr.x + cos(a) * rad, ctr.y + sin(a) * rad),
                    )
                }
            }
            // 5) nokta -> dunya (internet simgesi)
            val g = seg(t, 1.8f, 2.7f)
            val dotAlpha = 1f - smooth(seg(t, 2.0f, 2.6f))
            val pulse = if (t in 0.95f..1.5f) 1f + 0.5f * sin((t - 0.95f) * 18f) * (1.5f - t) else 1f
            if (t >= 0f) {
                drawCircle(
                    Brush.radialGradient(listOf(white.copy(alpha = 0.55f * dotAlpha), Color.Transparent), Offset(ctr.x, y), dotR * 6f),
                    dotR * 6f, Offset(ctr.x, y),
                )
                drawCircle(white.copy(alpha = dotAlpha), dotR * pulse, Offset(ctr.x, y))
            }
            if (g > 0f) {
                val gr = dotR + (R - dotR) * smooth(g)
                val gw = 4f * d
                val col = white
                // dis halka cizilir
                drawArc(col, -90f, 360f * smooth(g), false, Offset(ctr.x - gr, ctr.y - gr), Size(gr * 2, gr * 2), style = Stroke(gw, cap = StrokeCap.Round))
                val inner = seg(g, 0.45f, 1f)
                if (inner > 0f) {
                    val ia = inner
                    // meridyenler
                    for (k in listOf(0.0f, 0.5f)) {
                        val ww = gr * (if (k == 0f) 0.46f else 0.0f)
                        if (k == 0f) drawOval(col.copy(alpha = ia), Offset(ctr.x - ww, ctr.y - gr), Size(ww * 2, gr * 2), style = Stroke(gw * 0.6f))
                    }
                    drawLine(col.copy(alpha = ia), Offset(ctr.x, ctr.y - gr), Offset(ctr.x, ctr.y + gr), gw * 0.6f, StrokeCap.Round)
                    // enlemler
                    for (lat in listOf(-0.55f, 0f, 0.55f)) {
                        val yy = ctr.y + gr * lat
                        val half = sqrt(maxOf(0f, gr * gr - (gr * lat) * (gr * lat)))
                        drawLine(col.copy(alpha = ia), Offset(ctr.x - half, yy), Offset(ctr.x + half, yy), gw * 0.6f, StrokeCap.Round)
                    }
                }
                // tamamlaninca parlama
                val done = seg(t, 2.65f, 3.4f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFF22D3EE).copy(alpha = 0.35f * sin(done * PI.toFloat())), Color.Transparent), ctr, R * 2.2f
                    ),
                    R * 2.2f, ctr,
                )
            }
        }
        // yazi: ZAPP harf harf
        Column(
            Modifier.padding(top = 0.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                "ZAPP".forEachIndexed { i, ch ->
                    Text(
                        ch.toString(),
                        color = if (i % 2 == 0) Cyan else Ink,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer {
                            val v = smooth(seg(clock.value, 2.7f + i * 0.09f, 3.15f + i * 0.09f))
                            alpha = v
                            translationY = (1f - v) * 60f
                        },
                    )
                }
            }
            Text(
                "Telefonun artık TV'nin kumandası.",
                color = Muted,
                fontSize = 16.sp,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .graphicsLayer { alpha = smooth(seg(clock.value, 3.2f, 3.6f)) },
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(90.dp))
        }
    }
}
