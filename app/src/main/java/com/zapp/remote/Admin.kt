package com.zapp.remote

import android.content.Context
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

@Composable
fun AdminTab() {
    val ctx = LocalContext.current
    val status by RemoteClient.status.collectAsState()
    val admin by RemoteClient.admin.collectAsState()
    val st by RemoteClient.adminState.collectAsState()
    val notice by RemoteClient.notice.collectAsState()
    var shown by remember { mutableStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    var codeDialog by remember { mutableStateOf(false) }
    var newCode by remember { mutableStateOf("") }
    val connected = status == Status.CONNECTED

    LaunchedEffect(connected, admin) {
        if (connected && admin) {
            shown = 0
            for (i in 1..5) {
                delay(140)
                shown = i
            }
            while (true) {
                RemoteClient.send(JSONObject().put("t", "adminstate"))
                delay(1500)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Yönetici paneli", color = Gold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (!connected) {
            val inf = rememberInfiniteTransition(label = "wait")
            val a by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
            Text(
                if (Prefs.adminWant(ctx).isNotEmpty()) "TV'ye bağlanınca yetkin doğrulanacak…" else "Yönetici olmak için TV'ye bağlı olman gerekir.",
                color = Cyan.copy(alpha = a),
                fontSize = 16.sp,
            )
        } else if (!admin) {
            Text("Bu telefon yönetici değil.", color = Muted, fontSize = 16.sp)
        } else {
            val info = st?.optJSONObject("info")
            val clients = st?.optJSONArray("clients")
            val banned = st?.optJSONArray("banned")
            val locked = st?.optBoolean("lock") ?: false

            Section(0, shown) {
                Text("TV", color = Muted, fontSize = 13.sp)
                Text(info?.optString("model") ?: "—", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("TV IP: ${info?.optString("ip") ?: "—"}   ·   MAC: ${info?.optString("mac")?.ifBlank { "bilinmiyor" } ?: "—"}", color = Muted, fontSize = 14.sp)
                Text("Benim IP'm: ${NetUtil.localIp() ?: "—"}   ·   Kayıtlı yönetici IP: ${info?.optString("adminIp")?.ifBlank { "—" } ?: "—"}", color = Muted, fontSize = 14.sp)
                Text("Çalışma süresi: ${dur((info?.optLong("up") ?: 0L) * 1000)}", color = Muted, fontSize = 14.sp)
            }
            Section(1, shown) {
                Text("Bağlı cihazlar", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                val n = clients?.length() ?: 0
                if (n == 0) Text("Kimse bağlı değil.", color = Muted, fontSize = 14.sp)
                for (i in 0 until n) {
                    val o = clients!!.getJSONObject(i)
                    val me = o.optBoolean("me")
                    val isAdm = o.optBoolean("admin")
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel2).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            o.optString("name") + if (me) "  (sen)" else if (isAdm) "  (yönetici)" else "",
                            color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold,
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
                                    RemoteClient.send(
                                        JSONObject().put("t", "ban").put("id", o.optString("id")).put("name", o.optString("name"))
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Section(2, shown) {
                Text("Engellenenler", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                val n = banned?.length() ?: 0
                if (n == 0) Text("Engellenen cihaz yok.", color = Muted, fontSize = 14.sp)
                for (i in 0 until n) {
                    val o = banned!!.getJSONObject(i)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(o.optString("name"), color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        ZButton("Engeli kaldır") {
                            RemoteClient.send(JSONObject().put("t", "unban").put("id", o.optString("id")))
                        }
                    }
                }
            }
            Section(3, shown) {
                Text("Kontroller", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                ZButton(
                    if (locked) "Yeni bağlantılar KAPALI · aç" else "Yeni bağlantıları kapat",
                    Modifier.fillMaxWidth(),
                    primary = locked,
                ) { RemoteClient.send(JSONObject().put("t", "lock").put("on", !locked)) }
                ZButton("Tüm eşleşmeleri sıfırla", Modifier.fillMaxWidth()) { confirmReset = true }
                ZButton("Yönetici kodunu değiştir", Modifier.fillMaxWidth()) { codeDialog = true }
            }
            if (notice.isNotEmpty()) Text(notice, color = Cyan, fontSize = 14.sp)
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
