package com.zapp.remote

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.java_websocket.WebSocket
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

data class TvState(
    val running: Boolean = false,
    val clients: Int = 0,
    val paired: Boolean = false,
)

/** TV ekranindaki arayuz ile servis arasindaki ortak durum. */
object TvBus {
    val state = MutableStateFlow(TvState())
}

/** Erisilebilirlik izni TV ayarlarinda acik mi? */
fun a11yEnabled(c: Context): Boolean {
    val s = Settings.Secure.getString(
        c.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return s.split(':').any { it.startsWith(c.packageName + "/") }
}

/**
 * TV'nin kalbi. Erisilebilirlik servisi sistem tarafindan acilista kendiliginden baslatilir ve
 * oldurulurse yeniden baslatilir; bu yuzden uygulama kapansa, internet gitse ya da elektrik
 * kesilip gelse bile telefondan gelen komutlari dinlemeye devam eder.
 * Her komut try/catch icindedir: servis cokerse Android izni kendiliginden kapatir.
 */
class ZappAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ZappServer? = null
    private var nsd: NsdManager? = null
    private var reg: NsdManager.RegistrationListener? = null
    private var netCb: ConnectivityManager.NetworkCallback? = null
    private var clients = 0

    private val recv = ConcurrentHashMap<WebSocket, FileReceiver>()
    private val gates = ConcurrentHashMap<WebSocket, AckGate>()

    @Volatile
    private var lastEvent = 0L
    private val recents = ArrayList<String>()
    private var homePkgs: Set<String> = emptySet()
    private val sleepRunnable = Runnable { sleepNow() }

    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ZLog.d("servis baglandi (izin acik)")
        try {
            homePkgs = packageManager
                .queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
                )
                .map { it.activityInfo.packageName }
                .toSet()
        } catch (e: Exception) {
        }
        startServer()
        registerNsd()
        watchNetwork()
        publish()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            lastEvent = SystemClock.uptimeMillis()
            val p = event.packageName?.toString() ?: return
            if (p == packageName || p in homePkgs) return
            if (p == "com.android.systemui" || p.contains("inputmethod") || p.contains("keyboard")) return
            recents.remove(p)
            recents.add(0, p)
            while (recents.size > 12) recents.removeAt(recents.size - 1)
        } catch (e: Exception) {
            ZLog.e("olay", e)
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        ZLog.d("servis baglantisi koptu (onUnbind)")
        stopAll()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ZLog.d("servis yok edildi (onDestroy)")
        stopAll()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- sunucu

    private val fileCtl = setOf("fbeg", "fend", "fack", "fdone")

    private fun startServer() {
        if (server != null) return
        val s = ZappServer(
            port = Prefs.PORT,
            token = Prefs.tvToken(this),
            tvName = Prefs.tvName(),
            tvMac = NetMac.get(),
            onCommand = { conn, j ->
                if (j.optString("t") in fileCtl) {
                    // Sirayi korumak icin ikili parcalarla ayni thread'de islenir
                    try {
                        fileMsg(conn, j)
                    } catch (e: Exception) {
                        ZLog.e("dosya mesaji", e)
                    }
                } else {
                    handler.post { handle(conn, j) }
                }
            },
            onBinary = { conn, b -> recv[conn]?.data(b) },
            onClients = { n ->
                handler.post {
                    clients = n
                    if (n > 0) Prefs.setTvPaired(this, true)
                    publish()
                }
            },
            onGone = { conn ->
                recv.remove(conn)?.abort()
                gates.remove(conn)
            },
            onFatal = {
                handler.postDelayed({
                    stopServerOnly()
                    startServer()
                }, 5000)
            },
        )
        s.isReuseAddr = true
        try {
            s.start()
            server = s
        } catch (e: Exception) {
            ZLog.e("sunucu baslatilamadi", e)
            handler.postDelayed({ startServer() }, 5000)
        }
    }

    private fun stopServerOnly() {
        try {
            server?.stop(300)
        } catch (e: Exception) {
        }
        server = null
    }

    private fun stopAll() {
        stopServerOnly()
        unregisterNsd()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        netCb?.let {
            try {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            } catch (e: Exception) {
            }
        }
        netCb = null
        TvBus.state.value = TvState()
    }

    private fun publish() {
        TvBus.state.value = TvState(
            running = true,
            clients = clients,
            paired = Prefs.tvPaired(this),
        )
    }

    // ------------------------------------------- agda gorunme (mDNS) ve ag takibi

    /** Telefon, TV'nin IP'si degisse bile bu kayit sayesinde TV'yi yeniden bulur. */
    private fun registerNsd() {
        unregisterNsd()
        try {
            val m = getSystemService(Context.NSD_SERVICE) as NsdManager
            val info = NsdServiceInfo().apply {
                serviceName = "Zapp-" + Prefs.tvToken(this@ZappAccessibilityService).take(8)
                serviceType = "_zapp._tcp."
                port = Prefs.PORT
            }
            val l = object : NsdManager.RegistrationListener {
                override fun onRegistrationFailed(i: NsdServiceInfo?, e: Int) {
                    ZLog.d("mDNS kaydi basarisiz: $e")
                }

                override fun onUnregistrationFailed(i: NsdServiceInfo?, e: Int) {}
                override fun onServiceRegistered(i: NsdServiceInfo?) {}
                override fun onServiceUnregistered(i: NsdServiceInfo?) {}
            }
            m.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
            nsd = m
            reg = l
        } catch (e: Exception) {
            ZLog.e("mDNS", e)
        }
    }

    private fun unregisterNsd() {
        try {
            reg?.let { nsd?.unregisterService(it) }
        } catch (e: Exception) {
        }
        reg = null
    }

    /** Wi-Fi/internet gidip gelince kaydi yenile. */
    private fun watchNetwork() {
        if (netCb != null) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.postDelayed({
                    registerNsd()
                    startServer()
                }, 1500)
            }
        }
        try {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .registerDefaultNetworkCallback(cb)
            netCb = cb
        } catch (e: Exception) {
        }
    }

    // ------------------------------------------------------------- komutlar

    private fun say(conn: WebSocket, v: String) {
        try {
            conn.send(JSONObject().put("t", "msg").put("v", v).toString())
        } catch (e: Exception) {
        }
    }

    private fun fileMsg(conn: WebSocket, j: JSONObject) {
        when (j.optString("t")) {
            "fbeg" -> recvFor(conn).begin(j)
            "fend" -> recvFor(conn).end()
            "fack" -> gateFor(conn).ack()
            "fdone" -> gateFor(conn).done(j.optBoolean("ok"))
        }
    }

    private fun recvFor(conn: WebSocket): FileReceiver = recv.getOrPut(conn) {
        FileReceiver(
            open = { n, _ -> TvFiles.open(this, n) },
            sendText = { try { conn.send(it) } catch (e: Exception) { } },
            onDone = { n, ok ->
                ZLog.d("dosya alindi: $n ok=$ok")
                say(conn, if (ok) "TV'ye alındı: $n" else "TV'de kaydedilemedi: $n")
                try {
                    conn.send(filesJson())
                } catch (e: Exception) {
                }
            },
        )
    }

    private fun gateFor(conn: WebSocket): AckGate = gates.getOrPut(conn) { AckGate() }

    private fun handle(conn: WebSocket, j: JSONObject) {
        try {
            when (j.optString("t")) {
                "key" -> key(j.optString("k"))
                "vol" -> audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (j.optInt("d") > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
                "mute" -> audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_TOGGLE_MUTE,
                    AudioManager.FLAG_SHOW_UI
                )
                "text" -> setFocusedText(j.optString("v"))
                "app" -> launchApp(j.optString("pkg"))
                "apps" -> conn.send(appsJson())
                "sys" -> SysActions.open(this, j.optString("p"))
                "game" -> SysActions.open(this, "game")
                "sleep" -> say(conn, if (sleepNow()) "Uyut komutu gönderildi" else "Bu TV uykuya almayı kabul etmedi; güç menüsünü dene")
                "power" -> say(conn, if (performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)) "Güç menüsü TV'de açıldı" else "Güç menüsü açılamadı")
                "sleeptimer" -> {
                    handler.removeCallbacks(sleepRunnable)
                    val m = j.optInt("m")
                    if (m > 0) {
                        handler.postDelayed(sleepRunnable, m * 60_000L)
                        say(conn, "$m dakika sonra TV uyutulacak")
                    } else {
                        say(conn, "Uyku zamanlayıcı iptal edildi")
                    }
                }
                "ls" -> conn.send(filesJson())
                "get" -> sendFileToPhone(conn, j.optString("name"))
                "rm" -> {
                    TvFiles.file(this, j.optString("name"))?.delete()
                    conn.send(filesJson())
                }
                "install" -> say(conn, TvFiles.install(this, j.optString("name")))
                "layout" -> conn.send(
                    JSONObject().put("t", "layout").put("v", LauncherStore.json(this)).toString()
                )
                "setlayout" -> say(conn, if (LauncherStore.save(this, j.optString("v"))) "Ana ekran kaydedildi" else "Düzen okunamadı")
                "openlauncher" -> SysActions.open(this, "launcher")
                "sethome" -> {
                    val on = j.optBoolean("on")
                    HomeMode.set(this, on)
                    say(
                        conn,
                        if (on) "Zapp ana ekranı etkin. Ana ekran tuşuna basınca TV 'Zapp Ana Ekran'ı seçtirebilir: 'Her zaman' de."
                        else "Eski ana ekrana dönüldü"
                    )
                }
                "log" -> conn.send(JSONObject().put("t", "log").put("v", ZLog.tail(60)).toString())
            }
        } catch (e: Exception) {
            ZLog.e("komut ${j.optString("t")}", e)
        }
    }

    private fun sleepNow(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            else performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        } catch (e: Exception) {
            false
        }
    }

    private fun sendFileToPhone(conn: WebSocket, name: String) {
        val f = TvFiles.file(this, name) ?: return say(conn, "Dosya bulunamadı")
        val gate = gateFor(conn)
        scope.launch {
            try {
                val ok = sendStream(
                    FileInputStream(f), f.name, f.length(),
                    sendText = { conn.send(it) },
                    sendBin = { conn.send(it) },
                    gate = gate,
                ) {}
                ZLog.d("dosya telefona gitti: ${f.name} ok=$ok")
            } catch (e: Exception) {
                ZLog.e("dosya gonderme", e)
            }
        }
    }

    private fun filesJson(): String {
        val a = JSONArray()
        TvFiles.list(this).forEach { a.put(JSONObject().put("n", it.name).put("s", it.length())) }
        return JSONObject().put("t", "files").put("list", a).toString()
    }

    private fun key(k: String) {
        when (k) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> goHome()
            "RECENTS" -> goRecents()
            "UP", "DOWN", "LEFT", "RIGHT", "OK" -> {
                if (GameBus.active) GameBus.emit(k) else dpad(k)
            }
            "PLAYPAUSE" -> media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "NEXT" -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
            "PREV" -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "FF" -> media(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            "REW" -> media(KeyEvent.KEYCODE_MEDIA_REWIND)
            "STOP" -> media(KeyEvent.KEYCODE_MEDIA_STOP)
        }
    }

    private fun media(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /** Sistemin Ana ekran eylemi bazi TV'lerde "basarili" deyip hicbir sey yapmaz: yedek yol var. */
    private fun goHome() {
        val t0 = SystemClock.uptimeMillis()
        val ok = performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            if (!ok || lastEvent < t0) startHomeIntent()
        }, 900)
    }

    private fun startHomeIntent() {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            ZLog.e("ana ekran", e)
        }
    }

    /** Android TV'de "son uygulamalar" ekrani genelde yoktur: bir onceki uygulamaya gecilir. */
    private fun goRecents() {
        val t0 = SystemClock.uptimeMillis()
        val ok = performGlobalAction(GLOBAL_ACTION_RECENTS)
        handler.postDelayed({
            if (!ok || lastEvent < t0) previousApp()
        }, 900)
    }

    private fun previousApp() {
        val cur = try {
            rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            null
        }
        val target = if (cur != null && cur == recents.firstOrNull()) recents.getOrNull(1)
        else recents.firstOrNull()
        if (target != null) launchApp(target)
    }

    private fun dpad(k: String) {
        // Android 13+ : dogrudan yon tusu olayi gonder
        if (Build.VERSION.SDK_INT >= 33) {
            val action = when (k) {
                "UP" -> GLOBAL_ACTION_DPAD_UP
                "DOWN" -> GLOBAL_ACTION_DPAD_DOWN
                "LEFT" -> GLOBAL_ACTION_DPAD_LEFT
                "RIGHT" -> GLOBAL_ACTION_DPAD_RIGHT
                else -> GLOBAL_ACTION_DPAD_CENTER
            }
            if (performGlobalAction(action)) return
        }
        // Daha eski TV'ler: odagi ekrandaki ogeler arasinda elle tasi
        focusFallback(k)
    }

    private fun focusFallback(k: String) {
        val root = rootInActiveWindow ?: return
        val current = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (k == "OK") {
            clickNode(current)
            return
        }
        val dir = when (k) {
            "UP" -> View.FOCUS_UP
            "DOWN" -> View.FOCUS_DOWN
            "LEFT" -> View.FOCUS_LEFT
            else -> View.FOCUS_RIGHT
        }
        val target = (current ?: root).focusSearch(dir) ?: return
        if (!target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
            target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        }
    }

    private fun clickNode(n: AccessibilityNodeInfo?) {
        var c = n
        while (c != null) {
            if (c.isClickable && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            c = c.parent
        }
    }

    private fun setFocusedText(text: String) {
        val root = rootInActiveWindow ?: return
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun launchApp(pkg: String) {
        if (pkg.isBlank()) return
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
            ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun appsJson(): String {
        val pm = packageManager
        val cats = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        val list = cats
            .flatMap {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0)
            }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("p", it.first).put("n", it.second)) }
        val r = JSONArray()
        val names = list.toMap()
        recents.forEach { p ->
            names[p]?.let { r.put(JSONObject().put("p", p).put("n", it)) }
        }
        return JSONObject().put("t", "apps").put("list", a).put("recent", r).toString()
    }
}
