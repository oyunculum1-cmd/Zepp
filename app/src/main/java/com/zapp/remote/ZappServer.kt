package com.zapp.remote

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Collections

/**
 * TV'de calisan WebSocket sunucusu. Telefon once {"t":"auth","token":"...","dev":"...","name":"..."}
 * gonderir; token dogruysa ve admin politikasi izin verirse komutlar kabul edilir.
 * authHook: kabul icin ok mesajina eklenecek alanlari dondurur; "deny" alani varsa reddeder.
 */
class ZappServer(
    port: Int,
    private val token: String,
    private val tvName: String,
    private val tvMac: String,
    private val authHook: (WebSocket, JSONObject) -> JSONObject,
    private val onCommand: (WebSocket, JSONObject) -> Unit,
    private val onBinary: (WebSocket, ByteArray) -> Unit,
    private val onClients: (Int) -> Unit,
    private val onGone: (WebSocket) -> Unit,
    private val onFatal: () -> Unit,
) : WebSocketServer(InetSocketAddress(port)) {

    private val authed: MutableSet<WebSocket> = Collections.synchronizedSet(HashSet())

    fun isAuthed(c: WebSocket) = authed.contains(c)

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {}

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        if (conn == null) return
        try {
            onGone(conn)
            if (authed.remove(conn)) onClients(authed.size)
        } catch (e: Exception) {
            ZLog.e("onClose", e)
        }
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        if (conn == null || message == null) return
        try {
            val j = try {
                JSONObject(message)
            } catch (e: Exception) {
                return
            }
            val t = j.optString("t")

            if (t == "auth") {
                val ok = MessageDigest.isEqual(
                    j.optString("token").toByteArray(),
                    token.toByteArray()
                )
                if (!ok) {
                    conn.send("""{"t":"denied"}""")
                    conn.close()
                    return
                }
                val extra = authHook(conn, j)
                val deny = extra.optString("deny")
                if (deny.isNotEmpty()) {
                    conn.send(JSONObject().put("t", deny).toString())
                    conn.close()
                    return
                }
                authed.add(conn)
                val r = JSONObject().put("t", "ok").put("name", tvName).put("mac", tvMac)
                extra.keys().forEach { r.put(it, extra.get(it)) }
                conn.send(r.toString())
                onClients(authed.size)
                return
            }

            if (!authed.contains(conn)) return
            if (t == "ping") {
                conn.send("""{"t":"pong"}""")
                return
            }
            onCommand(conn, j)
        } catch (e: Exception) {
            ZLog.e("onMessage", e)
        }
    }

    override fun onMessage(conn: WebSocket?, message: ByteBuffer?) {
        if (conn == null || message == null || !authed.contains(conn)) return
        try {
            val b = ByteArray(message.remaining())
            message.get(b)
            onBinary(conn, b)
        } catch (e: Exception) {
            ZLog.e("onBinary", e)
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        ZLog.d("sunucu hatasi: ${ex?.javaClass?.simpleName} ${ex?.message}")
        if (conn == null) onFatal()
    }

    override fun onStart() {
        connectionLostTimeout = 20
        ZLog.d("sunucu basladi")
    }
}
