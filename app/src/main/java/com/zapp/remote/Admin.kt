package com.zapp.remote

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gizli yonetici kodu: TV'ye bagli degilken kumandada
 * OK OK OK, Yukari Yukari, Asagi Asagi, OK tuslarina sirayla basilir.
 */
object AdminCode {
    private val buf = StringBuilder()

    /** Kod tamamlanirsa true doner (tuslar yalnizca TV'ye bagli degilken sayilir). */
    fun feed(ctx: Context, key: String): Boolean {
        val ch = when (key) {
            "OK" -> 'O'
            "UP" -> 'U'
            "DOWN" -> 'D'
            "LEFT" -> 'L'
            "RIGHT" -> 'R'
            else -> return false
        }
        buf.append(ch)
        if (buf.length > 16) buf.delete(0, buf.length - 16)
        val code = Prefs.adminLocalCode(ctx)
        if (buf.endsWith(code)) {
            buf.clear()
            Prefs.setAdminWant(ctx, code)
            try {
                android.widget.Toast.makeText(ctx, "Yönetici isteği gönderildi…", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
            }
            RemoteClient.reconnect(ctx)
            return true
        }
        return false
    }
}

private val Gold = Color(0xFFFFD166)

/** Yonetici girisi animasyonu (~3.4 sn): izgara, tarama cizgisi, halkalar, donen altigen, yazilar. */
@Composable
fun AdminIntro(ip: String, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(3400, easing = LinearEasing))
        onDone()
    }
    val p = t.value
    val full = "YÖNETİCİ ERİŞİMİ"
    val typed = full.take((p * 2.4f * full.length).toInt().coerceIn(0, full.length))
    val lines = listOf(
        0.35f to "IP adresin öğrenildi: ${ip.ifBlank { "—" }}",
        0.52f to "Cihaz kimliği doğrulanıyor",
        0.68f to "Yetki katmanı açılıyor",
        0.84f to "Hoş geldin, yönetici",
    )
    Box(Modifier.fillMaxSize().background(Color(0xFF05070F)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 44.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(Color(0x1A22D3EE), Offset(x, 0f), Offset(x, size.height))
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(Color(0x1A22D3EE), Offset(0f, y), Offset(size.width, y))
                y += step
            }
            // tarama cizgisi
            val sy = size.height * ((p * 2.2f) % 1f)
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0x5522D3EE), Color.Transparent), sy - 80f, sy + 80f),
                Offset(0f, sy - 80f),
                androidx.compose.ui.geometry.Size(size.width, 160f),
            )
            val c = Offset(size.width / 2, size.height * 0.34f)
            // yayilan halkalar
            for (i in 0..3) {
                val q = (p * 2.6f + i * 0.25f) % 1f
                drawCircle(
                    (if (i % 2 == 0) Cyan else Violet).copy(alpha = (1f - q) * 0.55f),
                    radius = q * size.minDimension * 0.55f,
                    center = c,
                    style = Stroke(3.dp.toPx()),
                )
            }
            // donen altigen + ilerleme yayi
            val r = 62.dp.toPx()
            val hex = Path()
            for (i in 0..5) {
                val a = (PI / 3 * i + p * 6.0).toFloat()
                val px = c.x + r * cos(a)
                val py = c.y + r * sin(a)
                if (i == 0) hex.moveTo(px, py) else hex.lineTo(px, py)
            }
            hex.close()
            drawPath(hex, Gold.copy(alpha = 0.9f), style = Stroke(4.dp.toPx()))
            drawArc(
                Cyan,
                -90f,
                360f * p,
                false,
                topLeft = Offset(c.x - r * 0.62f, c.y - r * 0.62f),
                size = androidx.compose.ui.geometry.Size(r * 1.24f, r * 1.24f),
                style = Stroke(6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            if (p > 0.84f) {
                val k = ((p - 0.84f) / 0.12f).coerceIn(0f, 1f)
                val chk = Path().apply {
                    moveTo(c.x - r * 0.28f, c.y)
                    lineTo(c.x - r * 0.05f, c.y + r * 0.24f * k)
                    if (k > .5f) lineTo(c.x - r * 0.05f + (r * 0.4f) * ((k - .5f) * 2f), c.y + r * 0.24f - (r * 0.5f) * ((k - .5f) * 2f))
                }
                drawPath(chk, Green, style = Stroke(7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
        Column(
            Modifier.fillMaxSize().padding(top = 330.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                typed + if (typed.length < full.length) "▌" else "",
                color = Gold,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
            lines.forEach { (at, txt) ->
                if (p >= at) {
                    val a = ((p - at) / 0.06f).coerceIn(0f, 1f)
                    Text(
                        (if (p >= at + 0.12f) "✓ " else "› ") + txt,
                        color = (if (p >= at + 0.12f) Green else Cyan).copy(alpha = a),
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

private fun dur(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "${s} sn"
        s < 3600 -> "${s / 60} dk"
        else -> "${s / 3600} sa ${(s % 3600) / 60} dk"
    }
}

@Composable
private fun Section(index: Int, shown: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = shown > index,
        enter = fadeIn(tween(350)) + slideInVertically(tween(450)) { it / 3 },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Panel)
                .border(1.dp, Line, RoundedCornerShape(24.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

private enum class APage { HOME, DEVICES, SECURITY, NETWORK, SYSTEM }

private val AdmBlue = Color(0xFF4F6BDE)
private val AdmYellow = Color(0xFFF4C13B)
private val AdmTeal = Color(0xFF00C9A7)
private val AdmCyan = Color(0xFF37B5CC)

/** Beyaz cizgi simgeler (daire icinde). kind: 0 cihazlar, 1 guvenlik, 2 ag, 3 sistem */
@Composable
private fun TileIcon(kind: Int, color: Color) {
    Canvas(Modifier.size(64.dp)) {
        val c = center
        val r = size.minDimension / 2f
        val w = 2.6.dp.toPx()
        drawCircle(color, r, c)
        val s = r * 0.5f
        val white = Color.White
        when (kind) {
            0 -> {
                val a = Offset(c.x, c.y - s)
                val b = Offset(c.x - s, c.y + s * 0.7f)
                val d = Offset(c.x + s, c.y + s * 0.7f)
                drawLine(white, a, b, w); drawLine(white, b, d, w); drawLine(white, d, a, w)
                listOf(a, b, d).forEach { drawCircle(color, 6.dp.toPx(), it); drawCircle(white, 6.dp.toPx(), it, style = Stroke(w)) }
            }
            1 -> {
                drawRoundRect(white, Offset(c.x - s * 0.8f, c.y - s * 0.1f), androidx.compose.ui.geometry.Size(s * 1.6f, s * 1.3f), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                drawArc(white, 180f, 180f, false, Offset(c.x - s * 0.5f, c.y - s * 1.0f), androidx.compose.ui.geometry.Size(s, s * 1.8f), style = Stroke(w * 1.2f))
                drawCircle(color, 3.dp.toPx(), Offset(c.x, c.y + s * 0.4f))
            }
            2 -> {
                val top = Offset(c.x, c.y - s * 0.8f)
                val bw = 11.dp.toPx()
                drawRect(white, Offset(top.x - bw / 2, top.y - bw / 2), androidx.compose.ui.geometry.Size(bw, bw))
                drawLine(white, top, Offset(c.x, c.y + s * 0.1f), w)
                drawLine(white, Offset(c.x - s, c.y + s * 0.1f), Offset(c.x + s, c.y + s * 0.1f), w)
                for (dx in listOf(-s, 0f, s)) {
                    drawLine(white, Offset(c.x + dx, c.y + s * 0.1f), Offset(c.x + dx, c.y + s * 0.6f), w)
                    drawRect(white, Offset(c.x + dx - bw / 2, c.y + s * 0.6f), androidx.compose.ui.geometry.Size(bw, bw))
                }
            }
            else -> {
                drawCircle(white, s * 0.55f, c, style = Stroke(w * 1.3f))
                for (i in 0 until 8) {
                    val a = i * PI.toFloat() / 4f
                    drawLine(white, Offset(c.x + cos(a) * s * 0.75f, c.y + sin(a) * s * 0.75f), Offset(c.x + cos(a) * s * 1.05f, c.y + sin(a) * s * 1.05f), w * 1.6f)
                }
            }
        }
    }
}

@Composable
private fun Tile(title: String, sub: String, color: Color, kind: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 22.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TileIcon(kind, color)
        Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(AdmBlue).padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Ust kart: Ag -- TV -- Musteriler, aralarinda hareket eden cizgi ve onay isareti. */
@Composable
private fun Topology(upSec: Long, clients: Int, ip: String) {
    val inf = rememberInfiniteTransition(label = "topo")
    val flow by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "flow")
    @Composable
    fun Link(modifier: Modifier) {
        Canvas(modifier.height(24.dp)) {
            val y = size.height / 2f
            val teal = AdmTeal
            drawLine(teal.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 3.dp.toPx())
            val x = size.width * flow
            drawLine(teal, Offset(maxOf(0f, x - 26.dp.toPx()), y), Offset(x, y), 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val m = Offset(size.width / 2f, y)
            val p = Path().apply {
                moveTo(m.x - 6.dp.toPx(), m.y)
                lineTo(m.x - 1.dp.toPx(), m.y + 5.dp.toPx())
                lineTo(m.x + 7.dp.toPx(), m.y - 5.dp.toPx())
            }
            drawCircle(Color(0xFF101428), 10.dp.toPx(), m)
            drawPath(p, teal, style = Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(24.dp)).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(30.dp))
                Canvas(Modifier.size(64.dp)) {
                    val c = center; val r = size.minDimension * 0.42f; val st = Stroke(2.5.dp.toPx())
                    drawCircle(AdmTeal, r, c, style = st)
                    drawOval(AdmTeal, Offset(c.x - r * 0.45f, c.y - r), androidx.compose.ui.geometry.Size(r * 0.9f, r * 2), style = st)
                    drawLine(AdmTeal, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 2.5.dp.toPx())
                }
                Text("Ağ", color = Muted, fontSize = 13.sp)
            }
            Link(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Pill(dur(upSec * 1000))
                Canvas(Modifier.size(64.dp)) {
                    val st = Stroke(2.5.dp.toPx())
                    drawRoundRect(AdmTeal, Offset(4.dp.toPx(), 10.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - 8.dp.toPx(), size.height * 0.55f), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()), style = st)
                    drawLine(AdmTeal, Offset(size.width / 2, size.height * 0.62f + 10.dp.toPx()), Offset(size.width / 2, size.height - 6.dp.toPx()), 2.5.dp.toPx())
                    drawLine(AdmTeal, Offset(size.width * 0.3f, size.height - 6.dp.toPx()), Offset(size.width * 0.7f, size.height - 6.dp.toPx()), 2.5.dp.toPx())
                }
                Text("Zapp TV", color = Muted, fontSize = 13.sp)
            }
            Link(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Pill("$clients bağlı")
                Canvas(Modifier.size(64.dp)) {
                    val st = Stroke(2.5.dp.toPx())
                    drawRoundRect(AdmTeal, Offset(size.width * 0.28f, 6.dp.toPx()), androidx.compose.ui.geometry.Size(size.width * 0.44f, size.height - 12.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()), style = st)
                    drawCircle(AdmTeal, 2.dp.toPx(), Offset(size.width / 2, size.height - 12.dp.toPx()))
                }
                Text("Müşteriler", color = Muted, fontSize = 13.sp)
            }
        }
        Text("TV adresi: $ip", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(v, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun AdminTab() {
    val ctx = LocalContext.current
    val status by RemoteClient.status.collectAsState()
    val admin by RemoteClient.admin.collectAsState()
    val st by RemoteClient.adminState.collectAsState()
    val notice by RemoteClient.notice.collectAsState()
    var page by remember { mutableStateOf(APage.HOME) }
    var confirmReset by remember { mutableStateOf(false) }
    var codeDialog by remember { mutableStateOf(false) }
    var newCode by remember { mutableStateOf("") }
    val connected = status == Status.CONNECTED

    LaunchedEffect(connected, admin) {
        if (connected && admin) {
            while (true) {
                RemoteClient.send(JSONObject().put("t", "adminstate"))
                delay(1500)
            }
        }
    }

    val info = st?.optJSONObject("info")
    val clients = st?.optJSONArray("clients")
    val banned = st?.optJSONArray("banned")
    val locked = st?.optBoolean("lock") ?: false
    val nClients = clients?.length() ?: 0
    val nBanned = banned?.length() ?: 0
    val tvIp = info?.optString("ip")?.ifBlank { "—" } ?: "—"

    Column(Modifier.fillMaxSize()) {
        // ust cubuk
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(AdmBlue, Color(0xFF7C5CFF))))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("zapp", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(2.dp).height(26.dp).background(Color.White.copy(alpha = 0.7f)))
            Spacer(Modifier.width(12.dp))
            Text(
                if (page == APage.HOME) "YÖNETİCİ" else when (page) {
                    APage.DEVICES -> "CİHAZLAR"; APage.SECURITY -> "GÜVENLİK"; APage.NETWORK -> "AĞ"; else -> "SİSTEM"
                },
                color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).border(2.dp, Color.White, RoundedCornerShape(10.dp))
                    .clickable { page = APage.HOME },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(22.dp)) {
                    if (page == APage.HOME) {
                        for (i in 0..2) drawLine(Color.White, Offset(0f, size.height * (0.15f + i * 0.35f)), Offset(size.width, size.height * (0.15f + i * 0.35f)), 2.5.dp.toPx())
                    } else {
                        drawLine(Color.White, Offset(size.width * 0.65f, size.height * 0.1f), Offset(size.width * 0.25f, size.height * 0.5f), 3.dp.toPx())
                        drawLine(Color.White, Offset(size.width * 0.25f, size.height * 0.5f), Offset(size.width * 0.65f, size.height * 0.9f), 3.dp.toPx())
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!connected) {
                val inf = rememberInfiniteTransition(label = "wait")
                val a by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
                Text(
                    if (Prefs.adminWant(ctx).isNotEmpty()) "TV'ye bağlanınca yetkin doğrulanacak…" else "Yönetici olmak için TV'ye bağlı olman gerekir.",
                    color = Cyan.copy(alpha = a), fontSize = 16.sp,
                )
            } else if (!admin) {
                Text("Bu telefon yönetici değil.", color = Muted, fontSize = 16.sp)
            } else when (page) {
                APage.HOME -> {
                    Topology(info?.optLong("up") ?: 0L, nClients, tvIp)
                    ZButton("⟳  Zapp sunucusunu yeniden başlat", Modifier.fillMaxWidth(), primary = true) {
                        RemoteClient.send(JSONObject().put("t", "restartsrv"))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Tile("Cihazlar", "$nClients çevrimiçi cihaz", AdmBlue, 0, Modifier.weight(1f)) { page = APage.DEVICES }
                        Tile("Güvenlik", if (locked) "Yeni bağlantı kapalı" else "$nBanned engelli · kilit açık", AdmYellow, 1, Modifier.weight(1f)) { page = APage.SECURITY }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Tile("Ağ", "TV adresi ve bilgiler", AdmTeal, 2, Modifier.weight(1f)) { page = APage.NETWORK }
                        Tile("Sistem", "Kod ve eşleşmeler", AdmCyan, 3, Modifier.weight(1f)) { page = APage.SYSTEM }
                    }
                }
                APage.DEVICES -> {
                    Text("Bağlı cihazlar", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (nClients == 0) Text("Kimse bağlı değil.", color = Muted, fontSize = 14.sp)
                    for (i in 0 until nClients) {
                        val o = clients!!.getJSONObject(i)
                        val me = o.optBoolean("me")
                        val isAdm = o.optBoolean("admin")
                        Card {
                            Text(
                                o.optString("name") + if (me) "  (sen)" else if (isAdm) "  (yönetici)" else "",
                                color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${o.optString("ip")}  ·  ${dur(System.currentTimeMillis() - o.optLong("since"))} önce bağlandı",
                                color = Muted, fontSize = 13.sp,
                            )
                            if (!me && !isAdm) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ZButton("Bağlantıyı kes") {
                                        RemoteClient.send(JSONObject().put("t", "kick").put("id", o.optString("id")))
                                    }
                                    ZButton("Engelle") {
                                        RemoteClient.send(JSONObject().put("t", "ban").put("id", o.optString("id")).put("name", o.optString("name")))
                                    }
                                }
                            }
                        }
                    }
                }
                APage.SECURITY -> {
                    Card {
                        Text("Yeni bağlantılar", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (locked) "Şu an KAPALI: yeni telefon bağlanamaz." else "Açık: QR'ı taratan bağlanabilir.", color = Muted, fontSize = 14.sp)
                        ZButton(if (locked) "Yeni bağlantıları aç" else "Yeni bağlantıları kapat", Modifier.fillMaxWidth(), primary = locked) {
                            RemoteClient.send(JSONObject().put("t", "lock").put("on", !locked))
                        }
                    }
                    Text("Engellenenler", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (nBanned == 0) Text("Engellenen cihaz yok.", color = Muted, fontSize = 14.sp)
                    for (i in 0 until nBanned) {
                        val o = banned!!.getJSONObject(i)
                        Card {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(o.optString("name"), color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                ZButton("Engeli kaldır") {
                                    RemoteClient.send(JSONObject().put("t", "unban").put("id", o.optString("id")))
                                }
                            }
                        }
                    }
                }
                APage.NETWORK -> Card {
                    InfoRow("TV modeli", info?.optString("model") ?: "—")
                    InfoRow("TV IP", tvIp)
                    InfoRow("TV MAC", info?.optString("mac")?.ifBlank { "bilinmiyor" } ?: "—")
                    InfoRow("Port", Prefs.PORT.toString())
                    InfoRow("Benim IP'm", NetUtil.localIp() ?: "—")
                    InfoRow("Kayıtlı yönetici IP", info?.optString("adminIp")?.ifBlank { "—" } ?: "—")
                    InfoRow("Çalışma süresi", dur((info?.optLong("up") ?: 0L) * 1000))
                    InfoRow("Zapp sürümü (TV)", "v${RemoteClient.tvVer.value}")
                }
                APage.SYSTEM -> {
                    Card {
                        Text("Yönetici kodu", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Kumandada, TV'ye bağlı değilken girilen gizli tuş dizisi.", color = Muted, fontSize = 13.sp)
                        ZButton("Yönetici kodunu değiştir", Modifier.fillMaxWidth()) { codeDialog = true }
                    }
                    Card {
                        Text("Eşleşmeler", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Herkesin bağlantısını koparır, TV'de yeni QR çıkar.", color = Muted, fontSize = 13.sp)
                        ZButton("Tüm eşleşmeleri sıfırla", Modifier.fillMaxWidth()) { confirmReset = true }
                    }
                    Card {
                        Text("Sunucu", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        ZButton("Zapp sunucusunu yeniden başlat", Modifier.fillMaxWidth()) {
                            RemoteClient.send(JSONObject().put("t", "restartsrv"))
                        }
                    }
                }
            }
            if (notice.isNotEmpty() && connected && admin) Text(notice, color = Cyan, fontSize = 14.sp)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Eşleşmeler sıfırlansın mı?") },
            text = { Text("Kendin dahil tüm telefonların bağlantısı kopar. TV'de yeni QR çıkar; herkes yeniden taramalı.") },
            confirmButton = {
                TextButton(onClick = {
                    RemoteClient.send(JSONObject().put("t", "resetpairing"))
                    confirmReset = false
                }) { Text("Sıfırla") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Vazgeç") } },
        )
    }
    if (codeDialog) {
        AlertDialog(
            onDismissRequest = { codeDialog = false },
            title = { Text("Yeni yönetici kodu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Harflerle yaz: O = OK, U = yukarı, D = aşağı, L = sol, R = sağ. En az 4, en çok 12 tuş. Örnek: OOOUUDDO",
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = newCode,
                        onValueChange = { newCode = it.uppercase().filter { c -> c in "OUDLR" }.take(12) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCode.length >= 4) {
                        RemoteClient.send(JSONObject().put("t", "admincode").put("code", newCode))
                        Prefs.setAdminLocalCode(ctx, newCode)
                        codeDialog = false
                        newCode = ""
                    }
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { codeDialog = false }) { Text("Vazgeç") } },
        )
    }
}
