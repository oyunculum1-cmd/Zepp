package com.zapp.remote

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** TV'deki ana akis: izin -> QR -> baglandi. */
@Composable
fun TvHome(onChangeMode: () -> Unit) {
    val ctx = LocalContext.current
    val state by TvBus.state.collectAsState()
    var enabled by remember { mutableStateOf(a11yEnabled(ctx)) }
    var host by remember { mutableStateOf(NetUtil.localIp()) }
    var showQr by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            enabled = a11yEnabled(ctx)
            host = NetUtil.localIp()
            delay(2000)
        }
    }
    // Bir telefon baglaninca QR'i kapat
    LaunchedEffect(state.clients) {
        if (state.clients > 0) showQr = false
    }

    val paired = state.paired || Prefs.tvPaired(ctx)

    when {
        showLog -> LogPage { showLog = false }
        !enabled -> PermissionPage(ctx, onChangeMode) { showLog = true }
        paired && !showQr -> ConnectedPage(
            clients = state.clients,
            onAdd = { showQr = true },
            onChangeMode = onChangeMode,
            onGame = { SysActions.open(ctx, "game") },
            onLog = { showLog = true },
        )
        else -> QrPage(
            ctx = ctx,
            host = host,
            onBack = if (paired) ({ showQr = false }) else null,
            onChangeMode = onChangeMode,
        )
    }
}

@Composable
private fun PermissionPage(ctx: Context, onChangeMode: () -> Unit, onLog: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ZappLogo(Modifier.width(72.dp).height(72.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            "Son bir adım",
            color = Ink,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Telefonun TV'yi yönetebilmesi için Zapp'a erişilebilirlik izni ver.",
            color = Muted,
            fontSize = 24.sp,
        )
        Spacer(Modifier.height(24.dp))
        Text("1. Aşağıdaki düğmeye bas", color = Ink, fontSize = 24.sp)
        Text("2. Listeden \"Zapp\" uygulamasını seç", color = Ink, fontSize = 24.sp)
        Text("3. Açık konuma getir ve onayla", color = Ink, fontSize = 24.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            if (A11yGuard.canWrite(ctx)) "Koruma açık: izin kapanırsa Zapp kendisi yeniden açar."
            else "İzin kendiliğinden kapanıyorsa bilgisayardan bir kez şunu çalıştır:\n" + A11yGuard.GRANT_CMD,
            color = Muted,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ZButton("Ayarları aç", primary = true, autoFocus = true) {
                try {
                    ctx.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                }
            }
            ZButton("Modu değiştir", onClick = onChangeMode)
            ZButton("Kayıt", onClick = onLog)
        }
    }
}

@Composable
private fun LogPage(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Zapp kaydı", color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text(
            ZLog.tail(18).ifBlank { "Kayıt boş." },
            color = Muted,
            fontSize = 16.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
        ZButton("Geri", primary = true, autoFocus = true, onClick = onBack)
    }
}

@Composable
private fun QrPage(ctx: Context, host: String?, onBack: (() -> Unit)?, onChangeMode: () -> Unit) {
    Row(
        Modifier.fillMaxSize().padding(64.dp),
        horizontalArrangement = Arrangement.spacedBy(64.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (host != null) {
            val payload = Pairing.toUri(host, Prefs.PORT, Prefs.tvToken(ctx), Prefs.tvName())
            QrCode(payload, 360.dp)
        } else {
            Text("Ağ bağlantısı yok", color = Red, fontSize = 32.sp)
        }
        Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                "Telefonunla bu kodu tara",
                color = Ink,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("1. Telefonda Zapp'ı aç", color = Ink, fontSize = 26.sp)
            Text("2. \"Telefon\" seçeneğine dokun", color = Ink, fontSize = 26.sp)
            Text("3. Kamerayı bu koda tut", color = Ink, fontSize = 26.sp)
            Text(
                "Telefon ve TV aynı Wi-Fi ağında olmalı.",
                color = Muted,
                fontSize = 20.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (onBack != null) ZButton("Geri", autoFocus = true, onClick = onBack)
                ZButton("Modu değiştir", onClick = onChangeMode)
            }
        }
    }
}

@Composable
private fun ConnectedPage(
    clients: Int,
    onAdd: () -> Unit,
    onChangeMode: () -> Unit,
    onGame: () -> Unit,
    onLog: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ZappLogo(Modifier.width(96.dp).height(96.dp))
        Spacer(Modifier.height(24.dp))
        Text("Zapp hazır", color = Ink, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            if (clients > 0) "$clients telefon bağlı" else "Telefon bekleniyor",
            color = if (clients > 0) Green else Muted,
            fontSize = 28.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Bu ekranı kapatabilirsin. Zapp arka planda çalışmaya devam eder; TV yeniden açılınca da kendiliğinden hazır olur.",
            color = Muted,
            fontSize = 20.sp,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ZButton("Yeni telefon ekle", primary = true, autoFocus = true, onClick = onAdd)
            ZButton("Oyun", onClick = onGame)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ZButton("Kayıt", onClick = onLog)
            ZButton("Modu değiştir", onClick = onChangeMode)
        }
    }
}
