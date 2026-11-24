package com.zapp.remote

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Acilis animasyonu: logo doner ve buyur, etrafinda halkalar yayilir, yukleme cubugu dolar. */
@Composable
fun Splash() {
    val pop = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) {
        delay(500)
        rise.animateTo(1f, tween(700))
    }

    val inf = rememberInfiniteTransition(label = "splash")
    val ring by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "ring",
    )
    val wave by inf.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "wave",
    )
    val bar by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing)),
        label = "bar",
    )

    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = 70.dp.toPx()
            for (i in 0..2) {
                val p = (ring + i / 3f) % 1f
                drawCircle(
                    color = (if (i % 2 == 0) Violet else Cyan).copy(alpha = (1f - p) * 0.7f),
                    radius = base * (0.6f + 2.2f * p),
                    center = center,
                    style = Stroke(3.dp.toPx()),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ZappLogo(
                Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                        rotationZ = (1f - pop.value) * -18f
                        alpha = pop.value.coerceIn(0f, 1f)
                    },
                wave = wave,
            )
            Column(
                Modifier.graphicsLayer {
                    alpha = rise.value
                    translationY = (1f - rise.value) * 40f
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Zapp", color = Ink, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                Text("Telefonun artık TV'nin kumandası.", color = Muted, fontSize = 18.sp)
            }
            Box(
                Modifier
                    .width(180.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Line)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(bar)
                        .fillMaxHeight()
                        .background(Grad)
                )
            }
        }
    }
}
