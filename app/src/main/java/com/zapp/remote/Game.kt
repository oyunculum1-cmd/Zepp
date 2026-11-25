package com.zapp.remote

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.random.Random

/** Servis calisirken telefondaki yon tuslari buraya gelir (oyun on plandaysa). */
object GameBus {
    val keys = MutableSharedFlow<String>(extraBufferCapacity = 16)

    @Volatile
    var active = false

    fun emit(k: String) {
        keys.tryEmit(k)
    }
}

class GameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { ZappTheme { SnakeGame() } }
    }

    override fun onResume() {
        super.onResume()
        GameBus.active = true
    }

    override fun onPause() {
        GameBus.active = false
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val k = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "UP"
            KeyEvent.KEYCODE_DPAD_DOWN -> "DOWN"
            KeyEvent.KEYCODE_DPAD_LEFT -> "LEFT"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "RIGHT"
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> "OK"
            else -> null
        }
        if (k != null) {
            GameBus.emit(k)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

private const val COLS = 28
private const val ROWS = 15

@Composable
private fun SnakeGame() {
    val ctx = LocalContext.current
    var snake by remember { mutableStateOf(listOf(Pair(6, 7), Pair(5, 7), Pair(4, 7))) }
    var dir by remember { mutableStateOf(Pair(1, 0)) }
    var pending by remember { mutableStateOf(Pair(1, 0)) }
    var food by remember { mutableStateOf(Pair(18, 7)) }
    var score by remember { mutableStateOf(0) }
    var high by remember { mutableStateOf(Prefs.gameHigh(ctx)) }
    var over by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }

    fun reset() {
        snake = listOf(Pair(6, 7), Pair(5, 7), Pair(4, 7))
        dir = Pair(1, 0)
        pending = Pair(1, 0)
        food = Pair(18, 7)
        score = 0
        over = false
        started = true
    }

    LaunchedEffect(Unit) {
        GameBus.keys.collect { k ->
            when (k) {
                "UP" -> if (dir.second == 0) pending = Pair(0, -1)
                "DOWN" -> if (dir.second == 0) pending = Pair(0, 1)
                "LEFT" -> if (dir.first == 0) pending = Pair(-1, 0)
                "RIGHT" -> if (dir.first == 0) pending = Pair(1, 0)
                "OK" -> if (!started || over) reset()
            }
        }
    }

    LaunchedEffect(started, over) {
        while (started && !over) {
            delay(maxOf(70, 160 - score * 4).toLong())
            dir = pending
            val head = snake.first()
            val nh = Pair(
                (head.first + dir.first + COLS) % COLS,
                (head.second + dir.second + ROWS) % ROWS,
            )
            val eat = nh == food
            val body = if (eat) snake else snake.dropLast(1)
            if (nh in body) {
                over = true
                if (score > high) {
                    high = score
                    Prefs.setGameHigh(ctx, score)
                }
            } else {
                snake = listOf(nh) + body
                if (eat) {
                    score++
                    var f: Pair<Int, Int>
                    do {
                        f = Pair(Random.nextInt(COLS), Random.nextInt(ROWS))
                    } while (f in snake)
                    food = f
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0F24))) {
        Canvas(Modifier.fillMaxSize().padding(32.dp)) {
            val cell = minOf(size.width / COLS, size.height / ROWS)
            val ox = (size.width - cell * COLS) / 2
            val oy = (size.height - cell * ROWS) / 2
            drawRoundRect(
                Color(0xFF151A3A),
                Offset(ox, oy),
                Size(cell * COLS, cell * ROWS),
                CornerRadius(cell / 2),
            )
            drawCircle(
                Color(0xFFEF4444),
                radius = cell * 0.38f,
                center = Offset(ox + (food.first + 0.5f) * cell, oy + (food.second + 0.5f) * cell),
            )
            snake.forEachIndexed { i, c ->
                drawRoundRect(
                    if (i == 0) Color(0xFF22D3EE) else Color(0xFF7C5CFF),
                    Offset(ox + c.first * cell + cell * 0.06f, oy + c.second * cell + cell * 0.06f),
                    Size(cell * 0.88f, cell * 0.88f),
                    CornerRadius(cell * 0.28f),
                )
            }
        }
        Text(
            "Skor $score   ·   Rekor $high",
            color = Color(0xFFF3F5FF),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
        )
        if (!started || over) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (over) "Oyun bitti" else "Yılan oyunu",
                    color = Color(0xFFF3F5FF),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "OK ile başla · yön tuşları ya da telefon kumandası ile oyna · Geri ile çık",
                    color = Color(0xFF9AA3C7),
                    fontSize = 22.sp,
                )
            }
        }
    }
}
