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
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import org.java_websocket.WebSocket
import org.json.JSONArray
import org.json.JSONObject

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
 */
class ZappAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var server: ZappServer? = null
    private var nsd: NsdManager? = null
    private var reg: NsdManager.RegistrationListener? = null
    private var netCb: ConnectivityManager.NetworkCallback? = null
    private var clients = 0

    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    override fun onServiceConnected() {
        super.onServiceConnected()
        startServer()
        registerNsd()
        watchNetwork()
        publish()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        stopAll()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- sunucu

    private fun startServer() {
        if (server != null) return
        val s = ZappServer(
            port = Prefs.PORT,
            token = Prefs.tvToken(this),
            tvName = Prefs.tvName(),
            onCommand = { conn, j -> handler.post { handle(conn, j) } },
            onClients = { n ->
                handler.post {
                    clients = n
                    if (n > 0) Prefs.setTvPaired(this, true)
                    publish()
                }
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
                override fun onRegistrationFailed(i: NsdServiceInfo?, e: Int) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo?, e: Int) {}
                override fun onServiceRegistered(i: NsdServiceInfo?) {}
                override fun onServiceUnregistered(i: NsdServiceInfo?) {}
            }
            m.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
            nsd = m
            reg = l
        } catch (e: Exception) {
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
            }
        } catch (e: Exception) {
        }
    }

    private fun key(k: String) {
        when (k) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "UP", "DOWN", "LEFT", "RIGHT", "OK" -> dpad(k)
        }
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
        return JSONObject().put("t", "apps").put("list", a).toString()
    }
}
