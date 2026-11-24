package com.zapp.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.Inet4Address
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class TvApp(val name: String, val pkg: String)

enum class Status { IDLE, CONNECTING, CONNECTED, OFFLINE, DENIED }

/**
 * Telefon tarafi. Secili TV'ye surekli baglanmaya calisir: baglanti koparsa (TV kapandi,
 * Wi-Fi gitti, uygulama yeniden acildi) kendiliginden yeniden dener, IP degistiyse mDNS ile
 * TV'yi agda yeniden bulur.
 */
object RemoteClient {
    val status = MutableStateFlow(Status.IDLE)
    val apps = MutableStateFlow<List<TvApp>>(emptyList())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var ws: WebSocketClient? = null

    @Volatile
    private var denied = false

    fun connect(ctx: Context, device: Device) {
        val app = ctx.applicationContext
        job?.cancel()
        denied = false
        apps.value = emptyList()
        job = scope.launch { loop(app, device) }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        status.value = Status.IDLE
        apps.value = emptyList()
    }

    fun send(j: JSONObject) {
        val s = j.toString()
        scope.launch {
            try {
                ws?.takeIf { it.isOpen }?.send(s)
            } catch (e: Exception) {
            }
        }
    }

    private suspend fun loop(ctx: Context, first: Device) {
        var device = first
        var backoff = 1000L
        while (scope.isActive) {
            status.value = Status.CONNECTING
            val wasConnected = tryConnect(device)
            if (denied) {
                status.value = Status.DENIED
                return
            }
            status.value = Status.OFFLINE
            // TV'nin IP'si degismis olabilir: agda yeniden bul
            Discover.find(ctx, device.id, 4000)?.let { ip ->
                if (ip != device.host) {
                    device = device.copy(host = ip)
                    Prefs.upsert(ctx, device)
                }
            }
            delay(backoff)
            backoff = if (wasConnected) 1000L else minOf(backoff * 2, 8000L)
        }
    }

    /** Baglanti kopana kadar bekler. Hic yetkilendirme alindiysa true doner. */
    private suspend fun tryConnect(d: Device): Boolean {
        val closed = CompletableDeferred<Unit>()
        var authed = false
        val c = object : WebSocketClient(URI("ws://${d.host}:${d.port}")) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                send(JSONObject().put("t", "auth").put("token", d.token).toString())
            }

            override fun onMessage(message: String?) {
                if (message == null) return
                val j = try {
                    JSONObject(message)
                } catch (e: Exception) {
                    return
                }
                when (j.optString("t")) {
                    "ok" -> {
                        authed = true
                        status.value = Status.CONNECTED
                        send("""{"t":"apps"}""")
                    }
                    "denied" -> denied = true
                    "apps" -> {
                        val a = j.optJSONArray("list")
                        val out = ArrayList<TvApp>()
                        if (a != null) {
                            for (i in 0 until a.length()) {
                                val o = a.getJSONObject(i)
                                out.add(TvApp(o.optString("n"), o.optString("p")))
                            }
                        }
                        apps.value = out
                    }
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                closed.complete(Unit)
            }

            override fun onError(ex: Exception?) {
                closed.complete(Unit)
            }
        }
        c.connectionLostTimeout = 15
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
            try {
                c.close()
            } catch (e: Exception) {
            }
            ws = null
        }
        return authed
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
