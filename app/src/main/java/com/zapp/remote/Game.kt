package com.zapp.remote

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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

// ------------------------------------------------------------------ motor

private const val COLS = 32
private const val ROWS = 18
private enum class Phase { MENU, PLAY, PAUSE, OVER }
private data class P(val x: Int, val y: Int)

private class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val color: Color, val size: Float,
)

private class Pop(val x: Float, val y: Float, val text: String, var life: Float, val color: Color)

private class Sfx(ctx: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private val eat = pool.load(ctx, R.raw.snk_eat, 1)
    private val gold = pool.load(ctx, R.raw.snk_gold, 1)
    private val die = pool.load(ctx, R.raw.snk_die, 1)
    private val start = pool.load(ctx, R.raw.snk_start, 1)
    private val level = pool.load(ctx, R.raw.snk_level, 1)

    private fun play(id: Int, rate: Float = 1f) {
        try {
            pool.play(id, 1f, 1f, 1, 0, rate)
        } catch (e: Exception) {
        }
    }

    fun eat(combo: Int) = play(eat, 1f + (combo - 1) * 0.12f)
    fun gold() = play(gold)
    fun die() = play(die)
    fun start() = play(start)
    fun level() = play(level)
    fun release() = pool.release()
}

private class Engine(val ctx: Context, val sfx: Sfx) {
    var phase = Phase.MENU
    var mode = 0
    var snake = ArrayList<P>()
    var prev = ArrayList<P>()
    var dir = P(1, 0)
    val queue = ArrayDeque<P>()
    var food = P(20, 9)
    var gold: P? = null
    var goldLeft = 0f
    var power: P? = null
    var powerType = 0          // 0 yavaslat, 1 cift puan
    var powerLeft = 0f
    var slowLeft = 0f
    var dblLeft = 0f
    val obstacles = HashSet<P>()
    var score = 0
    var level = 1
    var eaten = 0
    var combo = 1
    var comboT = 0f
    var acc = 0f
    var time = 0f
    var shake = 0f
    var banner = ""
    var bannerT = 0f
    val particles = ArrayList<Particle>()
    val pops = ArrayList<Pop>()
    var newBest = false

    val modeNames = listOf("Klasik", "Sınırsız", "Engelli")
    val modeInfo = listOf("Duvara çarparsan biter", "Duvardan geçersin", "Engellere dikkat")

    fun best() = Prefs.gameBest(ctx, "m$mode")

    fun stepMs(): Float {
        val base = max(68f, 150f - (level - 1) * 8f)
        return if (slowLeft > 0f) base * 1.6f else base
    }

    fun startGame() {
        snake = arrayListOf(P(8, 9), P(7, 9), P(6, 9), P(5, 9))
        prev = ArrayList(snake)
        dir = P(1, 0)
        queue.clear()
        obstacles.clear()
        gold = null
        power = null
        slowLeft = 0f
        dblLeft = 0f
        score = 0
        level = 1
        eaten = 0
        combo = 1
        comboT = 0f
        acc = 0f
        particles.clear()
        pops.clear()
        newBest = false
        if (mode == 2) {
            var n = 0
            while (n < 16) {
                val c = P(Random.nextInt(COLS), Random.nextInt(ROWS))
                if (abs(c.x - 8) < 6 && abs(c.y - 9) < 3) continue
                if (obstacles.add(c)) n++
            }
        }
        food = freeCell()
        phase = Phase.PLAY
        sfx.start()
        banner("Başla!")
    }

    fun banner(t: String) {
        banner = t
        bannerT = 1.6f
    }

    fun freeCell(): P {
        while (true) {
            val c = P(Random.nextInt(COLS), Random.nextInt(ROWS))
            if (c in snake || c in obstacles || c == food || c == gold || c == power) continue
            return c
        }
    }

    fun input(k: String) {
        when (phase) {
            Phase.MENU -> when (k) {
                "LEFT" -> mode = (mode + modeNames.size - 1) % modeNames.size
                "RIGHT" -> mode = (mode + 1) % modeNames.size
                "OK" -> startGame()
            }
            Phase.PLAY -> {
                val d = when (k) {
                    "UP" -> P(0, -1)
                    "DOWN" -> P(0, 1)
                    "LEFT" -> P(-1, 0)
                    "RIGHT" -> P(1, 0)
                    else -> null
                }
                if (d != null) {
                    val last = queue.lastOrNull() ?: dir
                    if (d != last && !(d.x == -last.x && d.y == -last.y) && queue.size < 2) queue.addLast(d)
                } else if (k == "OK") phase = Phase.PAUSE
            }
            Phase.PAUSE -> if (k == "OK") phase = Phase.PLAY
            Phase.OVER -> when (k) {
                "OK" -> startGame()
                "DOWN", "LEFT", "RIGHT" -> phase = Phase.MENU
            }
        }
    }

    fun burst(c: P, color: Color, n: Int, speed: Float = 6f) {
        repeat(n) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val s = speed * (0.3f + Random.nextFloat())
            particles.add(
                Particle(c.x + .5f, c.y + .5f, cos(a) * s, sin(a) * s, 0.7f + Random.nextFloat() * .5f, color, 0.08f + Random.nextFloat() * .14f)
            )
        }
    }

    fun update(dt: Float) {
        time += dt
        if (bannerT > 0) bannerT -= dt
        if (shake > 0) shake = max(0f, shake - dt * 2.5f)
        particles.removeAll { it.life <= 0f }
        particles.forEach {
            it.x += it.vx * dt
            it.y += it.vy * dt
            it.vx *= 0.96f
            it.vy *= 0.96f
            it.life -= dt
        }
        pops.removeAll { it.life <= 0f }
        pops.forEach { it.life -= dt }
        if (phase != Phase.PLAY) return
        if (comboT > 0) comboT -= dt else combo = 1
        if (slowLeft > 0) slowLeft -= dt
        if (dblLeft > 0) dblLeft -= dt
        if (gold != null) {
            goldLeft -= dt
            if (goldLeft <= 0f) gold = null
        }
        if (power != null) {
            powerLeft -= dt
            if (powerLeft <= 0f) power = null
        }
        acc += dt * 1000f
        var guard = 0
        while (acc >= stepMs() && phase == Phase.PLAY && guard++ < 4) {
            acc -= stepMs()
            step()
        }
    }

    private fun die(head: P) {
        phase = Phase.OVER
        shake = 1f
        sfx.die()
        burst(head, Color(0xFFEF4444), 36, 9f)
        burst(head, Color(0xFFFFD166), 18, 7f)
        if (score > best()) {
            Prefs.setGameBest(ctx, "m$mode", score)
            newBest = true
        }
    }

    private fun step() {
        if (queue.isNotEmpty()) dir = queue.removeFirst()
        val h = snake.first()
        var nx = h.x + dir.x
        var ny = h.y + dir.y
        if (mode == 0) {
            if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS) {
                die(h)
                return
            }
        } else {
            nx = (nx + COLS) % COLS
            ny = (ny + ROWS) % ROWS
        }
        val nh = P(nx, ny)
        val eat = nh == food
        val body = if (eat) snake else snake.subList(0, snake.size - 1)
        if (nh in body || nh in obstacles) {
            die(h)
            return
        }
        prev = ArrayList(snake)
        val ns = ArrayList<P>(snake.size + 1)
        ns.add(nh)
        ns.addAll(body)
        snake = ns
        val dbl = if (dblLeft > 0f) 2 else 1
        if (eat) {
            combo = if (comboT > 0f) min(5, combo + 1) else 1
            comboT = 2.6f
            val pts = 10 * combo * dbl
            score += pts
            eaten++
            sfx.eat(combo)
            burst(nh, Color(0xFFEF4444), 14)
            pops.add(Pop(nh.x + .5f, nh.y.toFloat(), "+$pts" + if (combo > 1) " x$combo" else "", 1f, Color(0xFFFFD166)))
            food = freeCell()
            if (eaten % 5 == 0) {
                level++
                sfx.level()
                banner("Seviye $level")
                power = freeCell().also { powerType = Random.nextInt(2); powerLeft = 9f }
            }
            if (gold == null && Random.nextInt(100) < 22) {
                gold = freeCell()
                goldLeft = 7f
            }
        }
        if (nh == gold) {
            val pts = 50 * dbl
            score += pts
            gold = null
            sfx.gold()
            burst(nh, Color(0xFFFFD166), 30, 8f)
            pops.add(Pop(nh.x + .5f, nh.y.toFloat(), "ALTIN +$pts", 1.3f, Color(0xFFFFD166)))
        }
        if (nh == power) {
            power = null
            sfx.gold()
            if (powerType == 0) {
                slowLeft = 8f
                banner("Yavaş çekim!")
            } else {
                dblLeft = 8f
                banner("Çift puan!")
            }
            burst(nh, Color(0xFF22D3EE), 24, 7f)
        }
    }
}

// ----------------------------------------------------------------- arayuz

@Composable
private fun SnakeGame() {
    val ctx = LocalContext.current
    val sfx = remember { Sfx(ctx) }
    val e = remember { Engine(ctx, sfx) }
    var frame by remember { mutableStateOf(0L) }
    DisposableEffect(Unit) { onDispose { sfx.release() } }

    LaunchedEffect(Unit) {
        GameBus.keys.collect { k -> e.input(k) }
    }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                e.update(dt)
                frame = now
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF070A1A))) {
        Canvas(Modifier.fillMaxSize()) {
            val f = frame  // her karede yeniden ciz
            drawGame(e, f)
        }
        val ph = e.phase
        if (ph == Phase.PLAY || ph == Phase.PAUSE) Hud(e, frame)
        if (ph == Phase.MENU) MenuOverlay(e)
        if (ph == Phase.PAUSE) Overlay("Duraklatıldı", "OK ile devam et")
        if (ph == Phase.OVER) OverOverlay(e)
    }
}

@Composable
private fun Hud(e: Engine, frame: Long) {
    @Suppress("UNUSED_EXPRESSION") frame
    Row(
        Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("SKOR ${e.score}", color = Color(0xFFF3F5FF), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("REKOR ${max(e.best(), e.score)}", color = Color(0xFF9AA3C7), fontSize = 22.sp)
        Text("SEVİYE ${e.level}", color = Color(0xFF22D3EE), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (e.combo > 1) Text("KOMBO x${e.combo}", color = Color(0xFFFFD166), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (e.slowLeft > 0f) Text("YAVAŞ ${e.slowLeft.toInt() + 1}", color = Color(0xFF22D3EE), fontSize = 20.sp)
        if (e.dblLeft > 0f) Text("x2 ${e.dblLeft.toInt() + 1}", color = Color(0xFFFFD166), fontSize = 20.sp)
    }
    if (e.bannerT > 0f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                e.banner,
                color = Color.White.copy(alpha = min(1f, e.bannerT * 1.5f)),
                fontSize = (54 + (1.6f - e.bannerT) * 10).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun Overlay(title: String, sub: String) {
    Box(Modifier.fillMaxSize().background(Color(0xAA05070F)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color(0xFFF3F5FF), fontSize = 60.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Color(0xFF9AA3C7), fontSize = 24.sp)
        }
    }
}

@Composable
private fun OverOverlay(e: Engine) {
    Box(Modifier.fillMaxSize().background(Color(0xCC05070F)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OYUN BİTTİ", color = Color(0xFFEF4444), fontSize = 60.sp, fontWeight = FontWeight.Bold)
            Text("Skor ${e.score}   ·   Seviye ${e.level}   ·   Uzunluk ${e.snake.size}", color = Color(0xFFF3F5FF), fontSize = 28.sp)
            if (e.newBest) Text("YENİ REKOR!", color = Color(0xFFFFD166), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            else Text("Rekor ${e.best()}", color = Color(0xFF9AA3C7), fontSize = 24.sp)
            Spacer(Modifier.height(10.dp))
            Text("OK = tekrar oyna   ·   Aşağı = mod seç   ·   Geri = çık", color = Color(0xFF9AA3C7), fontSize = 20.sp)
        }
    }
}

@Composable
private fun MenuOverlay(e: Engine) {
    Box(Modifier.fillMaxSize().background(Color(0xB005070F)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("YILAN", color = Color(0xFF22D3EE), fontSize = 88.sp, fontWeight = FontWeight.Bold)
            Text("Elmaları ye, altını kap, büyü. Kendine çarpma!", color = Color(0xFF9AA3C7), fontSize = 22.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                e.modeNames.forEachIndexed { i, n ->
                    val sel = e.mode == i
                    Column(
                        Modifier
                            .width(220.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (sel) Color(0xFF2A1B5E) else Color(0xFF151A3A))
                            .border(if (sel) 3.dp else 1.dp, if (sel) Color(0xFF22D3EE) else Color(0xFF2A3166), RoundedCornerShape(24.dp))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(n, color = Color(0xFFF3F5FF), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text(e.modeInfo[i], color = Color(0xFF9AA3C7), fontSize = 16.sp, textAlign = TextAlign.Center)
                        Text("Rekor ${Prefs.gameBest(e.ctx, "m$i")}", color = Color(0xFFFFD166), fontSize = 18.sp)
                    }
                }
            }
            Text("◀ ▶ mod seç   ·   OK başla   ·   oyunda OK = duraklat", color = Color(0xFF9AA3C7), fontSize = 20.sp)
        }
    }
}

// ------------------------------------------------------------------ cizim

private fun DrawScope.drawGame(e: Engine, f: Long) {
    @Suppress("UNUSED_EXPRESSION") f
    val cs = min(size.width / (COLS + 1), size.height / (ROWS + 2.2f))
    val bw = cs * COLS
    val bh = cs * ROWS
    val ox = (size.width - bw) / 2
    val oy = (size.height - bh) / 2 + cs * 0.5f
    val shx = if (e.shake > 0f) (Random.nextFloat() - .5f) * cs * 0.8f * e.shake else 0f
    val shy = if (e.shake > 0f) (Random.nextFloat() - .5f) * cs * 0.8f * e.shake else 0f
    // arka plan: yumusak parlamalar
    drawRect(Brush.verticalGradient(listOf(Color(0xFF070A1A), Color(0xFF14103A))))
    val pulse = (sin(e.time * 1.5f) * .5f + .5f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0x337C5CFF), Color.Transparent), Offset(size.width * .85f, size.height * .15f), size.width * (.35f + .03f * pulse)),
        size.width * .5f, Offset(size.width * .85f, size.height * .15f),
    )
    drawCircle(
        Brush.radialGradient(listOf(Color(0x2822D3EE), Color.Transparent), Offset(size.width * .1f, size.height * .9f), size.width * .35f),
        size.width * .5f, Offset(size.width * .1f, size.height * .9f),
    )
    withTransform({ translate(shx, shy) }) {
        // tahta
        drawRoundRect(Color(0xFF0E1230), Offset(ox, oy), Size(bw, bh), CornerRadius(cs * .5f))
        for (y in 0 until ROWS) for (x in 0 until COLS) {
            if ((x + y) % 2 == 0) {
                drawRect(Color(0x0DFFFFFF), Offset(ox + x * cs, oy + y * cs), Size(cs, cs))
            }
        }
        val edge = if (e.mode == 0) Color(0xFFEF4444) else Color(0xFF22D3EE)
        drawRoundRect(edge.copy(alpha = .55f), Offset(ox, oy), Size(bw, bh), CornerRadius(cs * .5f), style = Stroke(cs * .12f))
        drawRoundRect(edge.copy(alpha = .15f), Offset(ox - cs * .1f, oy - cs * .1f), Size(bw + cs * .2f, bh + cs * .2f), CornerRadius(cs * .6f), style = Stroke(cs * .35f))

        // engeller
        e.obstacles.forEach { o ->
            drawRoundRect(Color(0xFF3A3D66), Offset(ox + o.x * cs + cs * .06f, oy + o.y * cs + cs * .06f), Size(cs * .88f, cs * .88f), CornerRadius(cs * .22f))
            drawRoundRect(Color(0xFF5A5E99), Offset(ox + o.x * cs + cs * .06f, oy + o.y * cs + cs * .06f), Size(cs * .88f, cs * .88f), CornerRadius(cs * .22f), style = Stroke(cs * .07f))
        }

        // elma
        val fp = 1f + .08f * sin(e.time * 6f)
        val fc = Offset(ox + (e.food.x + .5f) * cs, oy + (e.food.y + .5f) * cs)
        drawCircle(Brush.radialGradient(listOf(Color(0x66EF4444), Color.Transparent), fc, cs * 1.5f), cs * 1.5f, fc)
        drawCircle(Color(0xFFEF4444), cs * .38f * fp, fc)
        drawCircle(Color(0x66FFFFFF), cs * .12f, Offset(fc.x - cs * .12f, fc.y - cs * .14f))
        drawLine(Color(0xFF34D399), Offset(fc.x, fc.y - cs * .34f), Offset(fc.x + cs * .14f, fc.y - cs * .5f), cs * .09f, StrokeCap.Round)

        // altin
        e.gold?.let { g ->
            val c = Offset(ox + (g.x + .5f) * cs, oy + (g.y + .5f) * cs)
            val r = cs * (.4f + .06f * sin(e.time * 9f))
            drawCircle(Brush.radialGradient(listOf(Color(0x88FFD166), Color.Transparent), c, cs * 1.7f), cs * 1.7f, c)
            drawCircle(Color(0xFFFFD166), r, c)
            drawCircle(Color(0xFFFFF3C4), r * .5f, c)
            drawArc(Color.White, -90f, 360f * (e.goldLeft / 7f).coerceIn(0f, 1f), false, Offset(c.x - cs * .62f, c.y - cs * .62f), Size(cs * 1.24f, cs * 1.24f), style = Stroke(cs * .09f, cap = StrokeCap.Round))
        }
        // guc
        e.power?.let { g ->
            val c = Offset(ox + (g.x + .5f) * cs, oy + (g.y + .5f) * cs)
            val col = if (e.powerType == 0) Color(0xFF22D3EE) else Color(0xFFB794FF)
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = .5f), Color.Transparent), c, cs * 1.6f), cs * 1.6f, c)
            drawCircle(col, cs * .36f, c)
            drawCircle(Color(0xFF070A1A), cs * .2f, c)
            drawArc(Color.White, -90f, 360f * (e.powerLeft / 9f).coerceIn(0f, 1f), false, Offset(c.x - cs * .6f, c.y - cs * .6f), Size(cs * 1.2f, cs * 1.2f), style = Stroke(cs * .08f, cap = StrokeCap.Round))
        }

        // yilan (ara degerlemeli, akici)
        if (e.snake.isNotEmpty()) {
            val k = if (e.phase == Phase.PLAY) (e.acc / e.stepMs()).coerceIn(0f, 1f) else 1f
            val n = e.snake.size
            val pts = ArrayList<Offset>(n)
            for (i in 0 until n) {
                val c = e.snake[i]
                val p = e.prev.getOrNull(i) ?: c
                val jump = abs(c.x - p.x) > 1 || abs(c.y - p.y) > 1
                val x = if (jump) c.x.toFloat() else p.x + (c.x - p.x) * k
                val y = if (jump) c.y.toFloat() else p.y + (c.y - p.y) * k
                pts.add(Offset(ox + (x + .5f) * cs, oy + (y + .5f) * cs))
            }
            val dead = e.phase == Phase.OVER
            for (i in n - 1 downTo 1) {
                val a = pts[i]
                val b = pts[i - 1]
                if (abs(a.x - b.x) > cs * 1.6f || abs(a.y - b.y) > cs * 1.6f) continue
                val t = i / max(1f, n - 1f)
                val col = lerpC(Color(0xFF22D3EE), Color(0xFF7C5CFF), t).let { if (dead) it.copy(alpha = .55f) else it }
                val w = cs * (.86f - .22f * t)
                drawLine(col.copy(alpha = .25f), a, b, w * 1.5f, StrokeCap.Round)
                drawLine(col, a, b, w, StrokeCap.Round)
                // parlak sirt cizgisi
                drawLine(Color(0x33FFFFFF), Offset(a.x - cs * .1f, a.y - cs * .12f), Offset(b.x - cs * .1f, b.y - cs * .12f), w * .25f, StrokeCap.Round)
            }
            // bas
            val h = pts[0]
            drawCircle(Color(0xFF22D3EE).copy(alpha = if (dead) .6f else 1f), cs * .46f, h)
            val dx = e.dir.x.toFloat()
            val dy = e.dir.y.toFloat()
            val px = -dy
            val py = dx
            val eye = cs * .14f
            for (s in listOf(-1f, 1f)) {
                val ec = Offset(h.x + dx * cs * .16f + px * s * cs * .2f, h.y + dy * cs * .16f + py * s * cs * .2f)
                drawCircle(Color.White, eye, ec)
                drawCircle(if (dead) Color(0xFFEF4444) else Color(0xFF070A1A), eye * .55f, Offset(ec.x + dx * eye * .35f, ec.y + dy * eye * .35f))
            }
            // dil
            if (!dead && sin(e.time * 7f) > .55f) {
                val tp = Offset(h.x + dx * cs * .46f, h.y + dy * cs * .46f)
                drawLine(Color(0xFFEF4444), tp, Offset(tp.x + dx * cs * .3f, tp.y + dy * cs * .3f), cs * .05f, StrokeCap.Round)
            }
        }

        // parcaciklar
        e.particles.forEach {
            drawCircle(it.color.copy(alpha = (it.life).coerceIn(0f, 1f)), cs * it.size, Offset(ox + it.x * cs, oy + it.y * cs))
        }
    }
}

private fun lerpC(a: Color, b: Color, t: Float): Color = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    1f,
)
