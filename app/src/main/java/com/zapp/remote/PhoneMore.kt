package com.zapp.remote

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

private fun activeDevice(ctx: Context): Device? {
    val l = Prefs.devices(ctx)
    return l.firstOrNull { it.id == Prefs.activeId(ctx) } ?: l.firstOrNull()
}

/** Wake-on-LAN: TV beklemedeyken (kapaliyken) uyandirmak icin sihirli paket. */
object Wol {
    suspend fun wake(mac: String, host: String): Boolean = withContext(Dispatchers.IO) {
        val hex = mac.replace(Regex("[^0-9A-Fa-f]"), "")
        if (hex.length != 12) return@withContext false
        val m = hex.chunked(2).map { it.toInt(16).toByte() }
        val pkt = ByteArray(6) { 0xFF.toByte() } + (1..16).flatMap { m }.toByteArray()
        val targets = listOf("255.255.255.255", host.substringBeforeLast('.') + ".255")
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                for (t in targets) for (port in listOf(9, 7)) {
                    try {
                        s.send(DatagramPacket(pkt, pkt.size, InetAddress.getByName(t), port))
                    } catch (e: Exception) {
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Violet else Panel2)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ink, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// ------------------------------------------------------------- Guc / uyku

@Composable
fun PowerRow() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val notice by RemoteClient.notice.collectAsState()
    var macDialog by remember { mutableStateOf(false) }
    var local by remember { mutableStateOf("") }

    fun wake() {
        val d = activeDevice(ctx) ?: return
        if (d.mac.isBlank()) {
            macDialog = true
            return
        }
        scope.launch {
            val ok = Wol.wake(d.mac, d.host)
            RemoteClient.notice.value =
                if (ok) "Uyandırma paketi gönderildi. TV'nin 'Wi-Fi/LAN ile aç' ayarı açıksa açılır."
                else "MAC adresi geçersiz"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ZButton("Uyut", Modifier.weight(1f)) { send("sleep") }
            ZButton("Güç menüsü", Modifier.weight(1f)) { send("power") }
            ZButton("Uyandır", Modifier.weight(1f)) { wake() }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Uyku zamanlayıcı:", color = Muted, fontSize = 14.sp)
            listOf(15, 30, 60, 90).forEach { m ->
                Chip("$m dk", false) { send("sleeptimer", "m" to m) }
            }
            Chip("İptal", false) { send("sleeptimer", "m" to 0) }
        }
        if (notice.isNotEmpty()) Text(notice, color = Cyan, fontSize = 14.sp)
    }

    if (macDialog) {
        AlertDialog(
            onDismissRequest = { macDialog = false },
            title = { Text("TV'nin MAC adresi") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "TV'yi kapalıyken açabilmek için MAC adresi gerekir. TV'de Ayarlar > Cihaz tercihleri > Hakkında > Durum içinde Wi-Fi/Ethernet MAC adresi yazar. TV'nin 'Wi-Fi/LAN ile uyandır' seçeneği de açık olmalı.",
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = local,
                        onValueChange = { local = it },
                        singleLine = true,
                        label = { Text("AA:BB:CC:DD:EE:FF") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val d = activeDevice(ctx)
                    if (d != null && local.replace(Regex("[^0-9A-Fa-f]"), "").length == 12) {
                        Prefs.upsert(ctx, d.copy(mac = local.trim()))
                        macDialog = false
                        wake()
                    }
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { macDialog = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
fun MediaRow() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ZButton("⏮", Modifier.weight(1f)) { send("key", "k" to "PREV") }
        ZButton("⏪", Modifier.weight(1f)) { send("key", "k" to "REW") }
        ZButton("⏯", Modifier.weight(1f), primary = true) { send("key", "k" to "PLAYPAUSE") }
        ZButton("⏩", Modifier.weight(1f)) { send("key", "k" to "FF") }
        ZButton("⏭", Modifier.weight(1f)) { send("key", "k" to "NEXT") }
    }
}

// ------------------------------------------------------------------ Dosya

@Composable
fun FilesTab() {
    val ctx = LocalContext.current
    val files by RemoteClient.files.collectAsState()
    val x by RemoteClient.xfer.collectAsState()
    val notice by RemoteClient.notice.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) RemoteClient.upload(ctx, uris)
    }
    LaunchedEffect(Unit) { send("ls") }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Dosya gönder", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Dosyalar doğrudan TV'ye gider, internet gerekmez. APK gönderip TV'ye kurabilirsin.",
            color = Muted,
            fontSize = 14.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ZButton("Telefondan TV'ye gönder", Modifier.weight(1f), primary = true) {
                picker.launch(arrayOf("*/*"))
            }
            ZButton("Yenile", Modifier.weight(0.5f)) { send("ls") }
        }
        if (x.dir.isNotEmpty()) {
            val f = if (x.total > 0) (x.done.toFloat() / x.total).coerceIn(0f, 1f) else 0f
            Text(
                (if (x.dir == "up") "Gönderiliyor: " else "İndiriliyor: ") + x.name +
                    "  ${fmtSize(x.done)}" + if (x.total > 0) " / ${fmtSize(x.total)}" else "",
                color = Ink,
                fontSize = 14.sp,
            )
            LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
        } else if (x.msg.isNotEmpty()) {
            Text(x.msg, color = Green, fontSize = 14.sp)
        }
        if (notice.isNotEmpty()) Text(notice, color = Cyan, fontSize = 13.sp)
        Text("TV'deki dosyalar", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (files.isEmpty()) {
            Text("Henüz dosya yok.", color = Muted, fontSize = 14.sp)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(files) { f ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Panel)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(f.name, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(fmtSize(f.size), color = Muted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ZButton("Telefona al") { send("get", "name" to f.name) }
                        if (f.name.endsWith(".apk", ignoreCase = true)) {
                            ZButton("TV'ye kur", primary = true) { send("install", "name" to f.name) }
                        }
                        ZButton("Sil") { send("rm", "name" to f.name) }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------- TV ekrani duzenle

private fun <T> List<T>.swap(i: Int, j: Int): List<T> {
    if (i !in indices || j !in indices) return this
    val m = toMutableList()
    val t = m[i]
    m[i] = m[j]
    m[j] = t
    return m
}

@Composable
fun TvEditTab() {
    val raw by RemoteClient.layout.collectAsState()
    val apps by RemoteClient.apps.collectAsState()
    val notice by RemoteClient.notice.collectAsState()
    var l by remember { mutableStateOf<Layout?>(null) }

    LaunchedEffect(Unit) {
        send("layout")
        send("apps")
    }
    LaunchedEffect(raw) {
        Layout.parse(raw)?.let { l = it }
    }
    val cur = l
    if (cur == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("TV ana ekranı", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("TV'den düzen bekleniyor. TV'ye bağlı olduğundan emin ol.", color = Muted, fontSize = 15.sp)
            ZButton("Tekrar dene") { send("layout") }
        }
        return
    }

    val templates = listOf(
        Triple("cinema", "Sinema", "Apple TV tarzı"),
        Triple("bar", "Alt çubuk", "Samsung tarzı"),
        Triple("strip", "Şerit", "LG tarzı"),
        Triple("grid", "Izgara", "Klasik"),
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("TV ana ekranını düzenle", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Kısayolların yerini, şablonu ve arka planı buradan seç. 'Kaydet'e basınca TV'de hemen değişir.",
            color = Muted,
            fontSize = 14.sp,
        )

        Text("Şablon", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            templates.forEach { (id, name, sub) ->
                Chip("$name · $sub", cur.tpl == id) { l = cur.copy(tpl = id) }
            }
        }

        Text("Arka plan", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WALLS.forEachIndexed { i, w ->
                Chip(w.first, cur.wall == i) { l = cur.copy(wall = i) }
            }
        }

        OutlinedTextField(
            value = cur.title,
            onValueChange = { l = cur.copy(title = it) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Başlık (boşsa Zapp)") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Saat göster", cur.clock) { l = cur.copy(clock = !cur.clock) }
        }

        Text("Kısayollar (sıra = TV'deki sıra)", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        cur.tiles.forEachIndexed { i, t ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Panel)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "${i + 1}. ${t.n}" + if (t.k == "sys") "  (sistem)" else "",
                    color = Ink,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                ZButton("▲") { l = cur.copy(tiles = cur.tiles.swap(i, i - 1)) }
                ZButton("▼") { l = cur.copy(tiles = cur.tiles.swap(i, i + 1)) }
                ZButton("✕") { l = cur.copy(tiles = cur.tiles.filterIndexed { j, _ -> j != i }) }
            }
        }

        Text("Sistem kısayolu ekle", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SYS_TILES.forEach { (p, n) ->
                Chip("+ $n", false) {
                    if (cur.tiles.none { it.k == "sys" && it.p == p }) {
                        l = cur.copy(tiles = cur.tiles + Tile("sys", p, n))
                    }
                }
            }
        }

        Text("Uygulama ekle", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (apps.isEmpty()) {
            Text("Uygulama listesi TV'den gelmedi.", color = Muted, fontSize = 13.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.forEach { a ->
                Chip("+ ${a.name}", false) {
                    if (cur.tiles.none { it.k == "app" && it.p == a.pkg }) {
                        l = cur.copy(tiles = cur.tiles + Tile("app", a.pkg, a.name))
                    }
                }
            }
        }

        ZButton("Kaydet ve TV'de göster", Modifier.fillMaxWidth(), primary = true) {
            send("setlayout", "v" to cur.toJson())
            send("openlauncher")
        }
        ZButton("Sadece kaydet", Modifier.fillMaxWidth()) { send("setlayout", "v" to cur.toJson()) }

        Text("Ana ekran tuşu", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            "İstersen TV'deki Ana ekran tuşu bu ekranı açsın. Bazı TV'ler üçüncü taraf ana ekranı kabul etmez; olmazsa eski haline döndür.",
            color = Muted,
            fontSize = 13.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ZButton("Ana ekran yap", Modifier.weight(1f)) { send("sethome", "on" to true) }
            ZButton("Eskisine dön", Modifier.weight(1f)) { send("sethome", "on" to false) }
        }
        if (notice.isNotEmpty()) Text(notice, color = Cyan, fontSize = 14.sp)
    }
}

// -------------------------------------------------------------------- Oyun

@Composable
fun GameTab() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Yılan oyunu", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Oyun TV'de açılır, telefon gamepad olur. OK ile başlar/yeniden başlar. Rekor TV'de saklanır.",
            color = Muted,
            fontSize = 14.sp,
        )
        ZButton("Oyunu TV'de aç", Modifier.fillMaxWidth(), primary = true) { send("game") }
        Box(Modifier.size(264.dp).clip(CircleShape).background(Panel)) {
            PadKey("▲", Modifier.align(Alignment.TopCenter).size(88.dp)) { send("key", "k" to "UP") }
            PadKey("▼", Modifier.align(Alignment.BottomCenter).size(88.dp)) { send("key", "k" to "DOWN") }
            PadKey("◀", Modifier.align(Alignment.CenterStart).size(88.dp)) { send("key", "k" to "LEFT") }
            PadKey("▶", Modifier.align(Alignment.CenterEnd).size(88.dp)) { send("key", "k" to "RIGHT") }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Grad)
                    .clickable { send("key", "k" to "OK") },
                contentAlignment = Alignment.Center,
            ) {
                Text("OK", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        ZButton("Çık (Geri)", Modifier.fillMaxWidth()) { send("key", "k" to "BACK") }
    }
}

// --------------------------------------------------------------- TV kaydi

@Composable
fun TvLogDialog() {
    val log by RemoteClient.tvLog.collectAsState()
    val text = log ?: return
    AlertDialog(
        onDismissRequest = { RemoteClient.tvLog.value = null },
        title = { Text("TV hata kaydı") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text.ifBlank { "Kayıt boş." },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = { RemoteClient.tvLog.value = null }) { Text("Kapat") } },
    )
}
