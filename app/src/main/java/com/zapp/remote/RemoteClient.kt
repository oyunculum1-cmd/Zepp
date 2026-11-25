package com.zapp.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.provider.OpenableColumns
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.Inet4Address
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class TvApp(val name: String, val pkg: String)

enum class Status { IDLE, CONNECTING, CONNECTED, OFFLINE, DENIED }

/**
 * Telefon tarafi. Secili TV'ye surekli baglanmaya calisir: baglanti koparsa (TV kapandi,
 * Wi-Fi gitti, uygulama arka plandaydi) kendiliginden yeniden dener, IP degistiyse mDNS ile
 * TV'yi agda yeniden bulur. Uygulamaya donunce kick() beklemeden yeniden baglanir.
 */
object RemoteClient {
    val status = MutableStateFlow(Status.IDLE)
    val apps = MutableStateFlow<List<TvApp>>(emptyList())
    val recent = MutableStateFlow<List<TvApp>>(emptyList())
    val files = MutableStateFlow<List<TvFile>>(emptyList())
    val xfer = MutableStateFlow(XferState())
    val notice = MutableStateFlow("")
    val tvLog = MutableStateFlow<String?>(null)
    val layout = MutableStateFlow<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var ws: WebSocketClient? = null
    private var current: Device? = null
    private var appCtx: Context? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val gate = AckGate()

    @Volatile
    private var denied = false

    @Volatile
    private var lastPong = 0L

    private val receiver: FileReceiver by lazy {
        FileReceiver(
            open = { n, _ -> appCtx?.let { PhoneFiles.open(it, n) } },
            sendText = { sendRaw(it) },
            onProgress = { n, g, t -> xfer.value = XferState(n, g, t, "down") },
            onDone = { n, ok ->
                xfer.value = XferState(
                    msg = if (ok) "İndirildi: $n (İndirilenler/Zapp)" else "İndirilemedi: $n"
                )
            },
        )
    }

    fun connect(ctx: Context, device: Device) {
        val app = ctx.applicationContext
        appCtx = app
        if (job?.isActive == true && current?.id == device.id) {
            current = device
            kick()
            return
        }
        job?.cancel()
        denied = false
        current = device
        apps.value = emptyList()
        recent.value = emptyList()
        files.value = emptyList()
        job = scope.launch { loop(app, device) }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        current = null
        try {
            ws?.close()
        } catch (e: Exception) {
        }
        status.value = Status.IDLE
        apps.value = emptyList()
    }

    /** Uygulamaya donunce / ag degisince: beklemeden yeniden baglan, canli mi diye yokla. */
    fun kick() {
        wake.trySend(Unit)
        val w = ws
        if (status.value == Status.CONNECTED && w != null && w.isOpen) {
            val t0 = System.currentTimeMillis()
            try {
                w.send("""{"t":"ping"}""")
            } catch (e: Exception) {
            }
            scope.launch {
                delay(2500)
                if (lastPong < t0 && status.value == Status.CONNECTED) {
                    ZLog.d("ping cevapsiz, baglanti yenileniyor")
                    try {
                        w.close()
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    fun send(j: JSONObject) {
        val s = j.toString()
        scope.launch { sendRaw(s) }
    }

    private fun sendRaw(s: String) {
        try {
            ws?.takeIf { it.isOpen }?.send(s)
        } catch (e: Exception) {
        }
    }

    // ------------------------------------------------------------- dosyalar

    fun upload(ctx: Context, uris: List<Uri>) {
        val app = ctx.applicationContext
        scope.launch {
            for (u in uris) {
                val (name, size) = nameAndSize(app, u)
                xfer.value = XferState(name, 0, size, "up")
                val ok = try {
                    val ins = app.contentResolver.openInputStream(u)
                    if (ins == null) false else sendStream(
                        ins, name, size,
                        sendText = { ws!!.send(it) },
                        sendBin = { ws!!.send(it) },
                        gate = gate,
                    ) { xfer.value = XferState(name, it, size, "up") }
                } catch (e: Exception) {
                    ZLog.e("gonderme", e)
                    false
                }
                xfer.value = XferState(msg = if (ok) "TV'ye gönderildi: $name" else "Gönderilemedi: $name")
                if (!ok) break
            }
            send(JSONObject().put("t", "ls"))
        }
    }

    private fun nameAndSize(c: Context, u: Uri): Pair<String, Long> {
        var name = u.lastPathSegment?.substringAfterLast('/') ?: "dosya"
        var size = -1L
        try {
            c.contentResolver.query(u, null, null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    val ni = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = cur.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = cur.getString(ni) ?: name
                    if (si >= 0 && !cur.isNull(si)) size = cur.getLong(si)
                }
            }
        } catch (e: Exception) {
        }
        return name to size
    }

    // ------------------------------------------------------------ baglanti

    private suspend fun loop(ctx: Context, first: Device) {
        var device = first
        var fails = 0
        var backoff = 700L
        while (true) {
            status.value = Status.CONNECTING
            val authed = tryConnect(ctx, device)
            if (denied) {
                status.value = Status.DENIED
                return
            }
            if (authed) {
                fails = 0
                backoff = 400L
            } else {
                fails++
                if (fails >= 2) status.value = Status.OFFLINE
                backoff = minOf(backoff * 2, 6000L)
            }
            // TV'nin IP'si degismis olabilir: agda yeniden bul
            if (fails >= 2) {
                Discover.find(ctx, device.id, 3500)?.let { ip ->
                    if (ip != device.host) {
                        ZLog.d("TV yeni IP: $ip")
                        device = device.copy(host = ip)
                        current = device
                        Prefs.upsert(ctx, device)
                    }
                }
            }
            withTimeoutOrNull(backoff) { wake.receive() }
        }
    }

    /** Baglanti kopana kadar bekler. Yetkilendirme alindiysa true doner. */
    private suspend fun tryConnect(ctx: Context, d: Device): Boolean {
        val closed = CompletableDeferred<Unit>()
        var authed = false
        val c = object : WebSocketClient(URI("ws://${d.host}:${d.port}")) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                send(JSONObject().put("t", "auth").put("token", d.token).toString())
            }

            override fun onMessage(message: String?) {
                if (message == null) return
                try {
                    handle(ctx, d, this, message) { authed = true }
                } catch (e: Exception) {
                    ZLog.e("mesaj", e)
                }
            }

            override fun onMessage(bytes: ByteBuffer?) {
                if (bytes == null) return
                try {
                    val b = ByteArray(bytes.remaining())
                    bytes.get(b)
                    receiver.data(b)
                } catch (e: Exception) {
                    ZLog.e("ikili", e)
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                closed.complete(Unit)
            }

            override fun onError(ex: Exception?) {
                closed.complete(Unit)
            }
        }
        c.connectionLostTimeout = 12
        ws = c
        withContext(Dispatchers.IO) {
            try {
                c.connectBlocking(4, TimeUnit.SECONDS)
            } catch (e: Exception) {
            }
        }
        if (!c.isOpen) {
            try {
                c.close()
            } catch (e: Exception) {
            }
            ws = null
            return false
        }
        try {
            closed.await()
        } finally {
            receiver.abort()
            try {
                c.close()
            } catch (e: Exception) {
            }
            ws = null
        }
        return authed
    }

    private fun handle(ctx: Context, d: Device, c: WebSocketClient, message: String, onAuthed: () -> Unit) {
        val j = try {
            JSONObject(message)
        } catch (e: Exception) {
            return
        }
        when (j.optString("t")) {
            "ok" -> {
                onAuthed()
                lastPong = System.currentTimeMillis()
                status.value = Status.CONNECTED
                val mac = j.optString("mac")
                if (mac.isNotEmpty() && mac != d.mac) {
                    Prefs.upsert(ctx, d.copy(mac = mac))
                }
                c.send("""{"t":"apps"}""")
            }
            "denied" -> denied = true
            "pong" -> lastPong = System.currentTimeMillis()
            "apps" -> {
                apps.value = parseApps(j.optJSONArray("list"))
                recent.value = parseApps(j.optJSONArray("recent"))
            }
            "files" -> {
                val a = j.optJSONArray("list")
                val out = ArrayList<TvFile>()
                if (a != null) {
                    for (i in 0 until a.length()) {
                        val o = a.getJSONObject(i)
                        out.add(TvFile(o.optString("n"), o.optLong("s")))
                    }
                }
                files.value = out
            }
            "msg" -> notice.value = j.optString("v")
            "log" -> tvLog.value = j.optString("v")
            "layout" -> layout.value = j.optString("v")
            "fbeg" -> receiver.begin(j)
            "fend" -> receiver.end()
            "fack" -> gate.ack()
            "fdone" -> gate.done(j.optBoolean("ok"))
        }
    }

    private fun parseApps(a: org.json.JSONArray?): List<TvApp> {
        val out = ArrayList<TvApp>()
        if (a != null) {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(TvApp(o.optString("n"), o.optString("p")))
            }
        }
        return out
    }
}

/**
 * Telefonun surecini Wi-Fi aginin uzerine baglar. Boylece Wi-Fi'nin interneti olmasa bile
 * (yalnizca yerel ag) ve telefon mobil veriyi "varsayilan" saysa da kumanda TV'ye ulasir.
 */
object NetBinder {
    private var cb: ConnectivityManager.NetworkCallback? = null

    fun start(ctx: Context) {
        if (cb != null) return
        try {
            val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            val c = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    try {
                        cm.bindProcessToNetwork(network)
                        ZLog.d("Wi-Fi'ye baglandi")
                    } catch (e: Exception) {
                    }
                    RemoteClient.kick()
                }

                override fun onLost(network: Network) {
                    try {
                        cm.bindProcessToNetwork(null)
                    } catch (e: Exception) {
                    }
                }
            }
            cm.registerNetworkCallback(req, c)
            cb = c
        } catch (e: Exception) {
            ZLog.e("NetBinder", e)
        }
    }
}

/** mDNS ile "_zapp._tcp" servisini bulup TV'nin guncel IP'sini dondurur. */
object Discover {
    suspend fun find(ctx: Context, id: String, timeoutMs: Long): String? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<String?> { cont ->
                val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
                val done = AtomicBoolean(false)
                lateinit var listener: NsdManager.DiscoveryListener

                fun finish(v: String?) {
                    if (done.compareAndSet(false, true)) {
                        try {
                            nsd.stopServiceDiscovery(listener)
                        } catch (e: Exception) {
                        }
                        if (cont.isActive) cont.resume(v)
                    }
                }

                listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String?) {}
                    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                        finish(null)
                    }

                    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                    override fun onDiscoveryStopped(serviceType: String?) {}
                    override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}

                    override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                        val name = serviceInfo?.serviceName ?: return
                        if (!name.startsWith("Zapp-$id")) return
                        nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(s: NsdServiceInfo?, errorCode: Int) {}
                            override fun onServiceResolved(s: NsdServiceInfo?) {
                                val h = s?.host
                                if (h is Inet4Address) finish(h.hostAddress)
                            }
                        })
                    }
                }

                cont.invokeOnCancellation { finish(null) }
                try {
                    nsd.discoverServices("_zapp._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
                } catch (e: Exception) {
                    finish(null)
                }
            }
        }
}
