package com.zapp.remote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Telefon tarafinin ana iskeleti: ust cubuk + icerik + alt sekmeler. */
@Composable
fun PhoneRoot(onChangeMode: () -> Unit) {
    val ctx = LocalContext.current
    var devices by remember { mutableStateOf(Prefs.devices(ctx)) }
    var activeId by remember { mutableStateOf(Prefs.activeId(ctx)) }
    var tab by remember { mutableStateOf(if (Prefs.devices(ctx).isEmpty()) "scan" else "remote") }
    val active = devices.firstOrNull { it.id == activeId } ?: devices.firstOrNull()
    val status by RemoteClient.status.collectAsState()
    val isAdmin by RemoteClient.admin.collectAsState()
    val notice by RemoteClient.notice.collectAsState()
    var intro by remember { mutableStateOf(false) }
    var introShown by remember { mutableStateOf(false) }
    // Yonetici oldugun anlasilinca panel animasyonla acilir (surec basina bir kez)
    LaunchedEffect(isAdmin) {
        if (isAdmin && !introShown) {
            introShown = true
            intro = true
        }
    }

    // Wi-Fi'ye bagla (internetsiz yerel agda da calissin) ve uygulamaya donunce hemen yeniden baglan
    LaunchedEffect(Unit) { NetBinder.start(ctx) }
    val lifeOwner = LocalLifecycleOwner.current
    DisposableEffect(lifeOwner) {
        val ob = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START) RemoteClient.kick()
        }
        lifeOwner.lifecycle.addObserver(ob)
        onDispose { lifeOwner.lifecycle.removeObserver(ob) }
    }

    // Uygulama acilinca (cikip girsen, silip yeniden acsan da) kayitli TV'ye kendiliginden baglan
    LaunchedEffect(active) {
        if (active != null) RemoteClient.connect(ctx, active)
    }

    fun paired(d: Device) {
        Prefs.upsert(ctx, d)
        Prefs.setActive(ctx, d.id)
        devices = Prefs.devices(ctx)
        activeId = d.id
        tab = "remote"
    }

    if (tab == "scan") {
        ScanTab(
            canCancel = devices.isNotEmpty(),
            onCancel = { tab = "remote" },
            onPaired = { paired(it) },
        )
        return
    }

    TvLogDialog()
    if (intro) {
        AdminIntro(NetUtil.localIp() ?: "?") {
            intro = false
            tab = "admin"
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        // Ust cubuk
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(12.dp).clip(CircleShape).background(
                    when (status) {
                        Status.CONNECTED -> Green
                        Status.CONNECTING -> Cyan
                        else -> Red
                    }
                )
            )
            Column {
                Text(
                    active?.name ?: "TV yok",
                    color = Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when (status) {
                        Status.CONNECTED -> "Bağlı"
                        Status.CONNECTING -> "Bağlanıyor…"
                        Status.OFFLINE -> "Bağlantı yok · yeniden deneniyor"
                        Status.DENIED -> "Eşleşme reddedildi · QR'ı yeniden tara"
                        Status.KICKED -> "Yönetici bağlantını kesti"
                        Status.BANNED -> "Bu TV'den engellendin"
                        Status.LOCKED -> "TV yeni bağlantıya kapalı"
                        Status.IDLE -> ""
                    },
                    color = Muted,
                    fontSize = 14.sp,
                )
            }
            if (status == Status.KICKED || status == Status.LOCKED || status == Status.OFFLINE) {
                Spacer(Modifier.weight(1f))
                ZButton("Yeniden bağlan") { RemoteClient.reconnect(ctx) }
            }
        }
        if (notice.isNotBlank()) {
            Text(
                notice,
                color = Cyan,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 6.dp),
            )
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                "touch" -> TouchTab()
                "kbd" -> KeyboardTab()
                "apps" -> AppsTab()
                "files" -> FilesTab()
                "wall" -> WallTab()
                "admin" -> AdminTab()
                "game" -> GameTab()
                "devs" -> DevicesTab(
                    devices = devices,
                    activeId = active?.id,
                    onSelect = {
                        Prefs.setActive(ctx, it.id)
                        activeId = it.id
                        tab = "remote"
                    },
                    onDelete = {
                        Prefs.remove(ctx, it.id)
                        devices = Prefs.devices(ctx)
                        activeId = Prefs.activeId(ctx)
                        if (devices.isEmpty()) {
                            RemoteClient.disconnect()
                            tab = "scan"
                        }
                    },
                    onAdd = { tab = "scan" },
                    onChangeMode = onChangeMode,
                )
                else -> RemoteTab()
            }
        }

        // Alt sekmeler
        Row(
            Modifier.fillMaxWidth().background(Panel).padding(vertical = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                "remote" to "Kumanda",
                "touch" to "Dokunmatik",
                "kbd" to "Klavye",
                "apps" to "Uygulama",
                "files" to "Dosya",
                "wall" to "Duvar kağıdı",
                "game" to "Oyun",
                "devs" to "Cihazlar",
            ).let { if (isAdmin) it + ("admin" to "Admin") else it }.forEach { (id, label) ->
                Box(
                    Modifier
                        .width(84.dp)
                        .heightMin48()
                        .clickable { tab = id },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (tab == id) Cyan else Muted,
                        fontSize = 13.sp,
                        fontWeight = if (tab == id) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun Modifier.heightMin48(): Modifier =
    this.then(Modifier.height(48.dp))

fun send(t: String, vararg kv: Pair<String, Any>) {
    val j = JSONObject().put("t", t)
    kv.forEach { j.put(it.first, it.second) }
    RemoteClient.send(j)
}

// ------------------------------------------------------------------ Kumanda

@Composable
private fun RemoteTab() {
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current
    val st by RemoteClient.status.collectAsState()
    fun key(k: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        // TV'ye bagli degilken girilen gizli dizi yonetici modunu acar
        if (st != Status.CONNECTED) AdminCode.feed(ctx, k)
        send("key", "k" to k)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(Modifier.size(264.dp).clip(CircleShape).background(Panel)) {
            PadKey("▲", Modifier.align(Alignment.TopCenter).size(88.dp)) { key("UP") }
            PadKey("▼", Modifier.align(Alignment.BottomCenter).size(88.dp)) { key("DOWN") }
            PadKey("◀", Modifier.align(Alignment.CenterStart).size(88.dp)) { key("LEFT") }
            PadKey("▶", Modifier.align(Alignment.CenterEnd).size(88.dp)) { key("RIGHT") }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Grad)
                    .clickable { key("OK") },
                contentAlignment = Alignment.Center,
            ) {
                Text("OK", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ZButton("Geri", Modifier.weight(1f)) { key("BACK") }
            ZButton("Ana ekran", Modifier.weight(1f)) { key("HOME") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ZButton("Ses −", Modifier.weight(1f)) { send("vol", "d" to -1) }
            ZButton("Sessiz", Modifier.weight(1f)) { send("mute") }
            ZButton("Ses +", Modifier.weight(1f)) { send("vol", "d" to 1) }
        }
        MediaRow()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ZButton("Ayarlar", Modifier.weight(1f)) { send("sys", "p" to "settings") }
            ZButton("Wi-Fi", Modifier.weight(1f)) { send("sys", "p" to "wifi") }
        }
        PowerRow()
    }
}

// ------------------------------------------------------------- Dokunmatik

@Composable
private fun TouchTab() {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(36.dp))
                .background(Panel)
                .border(2.dp, Line, RoundedCornerShape(36.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        send("key", "k" to "OK")
                    })
                }
                .pointerInput(Unit) {
                    var ax = 0f
                    var ay = 0f
                    val step = 70f
                    detectDragGestures(
                        onDragStart = {
                            ax = 0f
                            ay = 0f
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            ax += drag.x
                            ay += drag.y
                            if (abs(ax) > step && abs(ax) >= abs(ay)) {
                                send("key", "k" to if (ax > 0) "RIGHT" else "LEFT")
                                ax = 0f
                                ay = 0f
                            } else if (abs(ay) > step) {
                                send("key", "k" to if (ay > 0) "DOWN" else "UP")
                                ax = 0f
                                ay = 0f
                            }
                        },
                    )
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                "Kaydır = yön · Dokun = seç",
                color = Muted,
                fontSize = 16.sp,
                modifier = Modifier.padding(24.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ZButton("Geri", Modifier.weight(1f)) { send("key", "k" to "BACK") }
            ZButton("Seç", Modifier.weight(1f), primary = true) { send("key", "k" to "OK") }
            ZButton("Ana ekran", Modifier.weight(1f)) { send("key", "k" to "HOME") }
        }
    }
}

// ------------------------------------------------------- Klavye + sesle arama

@Composable
private fun KeyboardTab() {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (t != null) {
            text = t
            send("text", "v" to t)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("TV'de yazı yaz", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "TV'de bir arama kutusunu seç, sonra buraya yaz.",
            color = Muted,
            fontSize = 15.sp,
        )
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                send("text", "v" to it)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Yazı") },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ZButton("Sesle yaz", Modifier.weight(1f), primary = true) {
                try {
                    voice.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                    )
                } catch (e: Exception) {
                }
            }
            ZButton("Tamam / Ara", Modifier.weight(1f)) { send("key", "k" to "OK") }
        }
        ZButton("Temizle", Modifier.fillMaxWidth()) {
            text = ""
            send("text", "v" to "")
        }
    }
}

// ------------------------------------------------------------ Uygulamalar

@Composable
private fun AppsTab() {
    val apps by RemoteClient.apps.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text(
            "TV'deki uygulamalar",
            color = Ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        if (apps.isEmpty()) {
            Text(
                "Liste TV'den bekleniyor. TV'ye bağlı olduğundan emin ol.",
                color = Muted,
                fontSize = 16.sp,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(apps) { a ->
                    Box(
                        Modifier
                            .height(96.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Panel2)
                            .clickable { send("app", "pkg" to a.pkg) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            a.name,
                            color = Ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------- Cihazlar

@Composable
private fun DevicesTab(
    devices: List<Device>,
    activeId: String?,
    onSelect: (Device) -> Unit,
    onDelete: (Device) -> Unit,
    onAdd: () -> Unit,
    onChangeMode: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cihazlarım", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(devices) { d ->
                val isActive = d.id == activeId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Panel)
                        .border(2.dp, if (isActive) Violet else Line, RoundedCornerShape(24.dp))
                        .clickable { onSelect(d) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (isActive) "Şu an kullanılıyor" else "Dokun: bu TV'ye geç",
                            color = Muted,
                            fontSize = 13.sp
                        )
                    }
                    ZButton("Sil") { onDelete(d) }
                }
            }
        }
        ZButton("TV hata kaydını göster", Modifier.fillMaxWidth()) { send("log") }
        ZButton("+ Yeni TV ekle (QR tara)", Modifier.fillMaxWidth(), primary = true, onClick = onAdd)
        ZButton("Modu değiştir (TV / Telefon)", Modifier.fillMaxWidth(), onClick = onChangeMode)
    }
}

// ------------------------------------------------------------- QR tarama

@Composable
private fun ScanTab(canCancel: Boolean, onCancel: () -> Unit, onPaired: (Device) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) {
        if (!granted) request.launch(Manifest.permission.CAMERA)
    }
    var error by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf("") }
    val done = remember { AtomicBoolean(false) }

    fun handle(raw: String) {
        val d = Pairing.parse(raw)
        if (d == null) {
            error = "Bu bir Zapp QR kodu değil."
        } else if (done.compareAndSet(false, true)) {
            onPaired(d)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("QR kodu tara", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Televizyondaki kodu çerçevenin içine al.",
            color = Muted,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Panel)
                .border(3.dp, Cyan, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (granted) {
                QrScanner(onResult = { handle(it) })
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        "QR taramak için kamera izni gerekli.",
                        color = Ink,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                    )
                    ZButton("İzin ver", primary = true) {
                        request.launch(Manifest.permission.CAMERA)
                    }
                }
            }
        }
        error?.let { Text(it, color = Red, fontSize = 15.sp) }
        OutlinedTextField(
            value = manual,
            onValueChange = { manual = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Ya da kodu yapıştır (zapp://…)") },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (canCancel) ZButton("Vazgeç", Modifier.weight(1f), onClick = onCancel)
            ZButton("Eşleştir", Modifier.weight(1f), primary = true) { handle(manual) }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun QrScanner(onResult: (String) -> Unit) {
    val owner = LocalLifecycleOwner.current
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            val previewView = PreviewView(c)
            val future = ProcessCameraProvider.getInstance(c)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val scanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                )
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(c)) { proxy ->
                    val media = proxy.image
                    if (media == null) {
                        proxy.close()
                    } else {
                        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        scanner.process(img)
                            .addOnSuccessListener { list ->
                                list.firstOrNull()?.rawValue?.let(onResult)
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        owner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (e: Exception) {
                }
            }, ContextCompat.getMainExecutor(c))
            previewView
        },
    )
}
