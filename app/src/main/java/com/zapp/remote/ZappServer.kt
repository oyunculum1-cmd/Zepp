package com.zapp.remote

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.Collections

/**
 * TV'de calisan WebSocket sunucusu. Telefon once {"t":"auth","token":"..."} gonderir;
 * token dogruysa komutlar kabul edilir. Token'i yalnizca TV'deki QR'i tarayan telefon bilir.
 */
class ZappServer(
    port: Int,
    private val token: String,
    private val tvName: String,
    private val onCommand: (WebSocket, JSONObject) -> Unit,
    private val onClients: (Int) -> Unit,
    private val onFatal: () -> Unit,
) : WebSocketServer(InetSocketAddress(port)) {

    private val authed: MutableSet<WebSocket> = Collections.synchronizedSet(HashSet())

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {}

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        if (conn != null && authed.remove(conn)) onClients(authed.size)
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        if (conn == null || message == null) return
        val j = try {
            JSONObject(message)
        } catch (e: Exception) {
            return
        }

        if (j.optString("t") == "auth") {
            val ok = MessageDigest.isEqual(
                j.optString("token").toByteArray(),
                token.toByteArray()
            )
            if (ok) {
                authed.add(conn)
                conn.send(JSONObject().put("t", "ok").put("name", tvName).toString())
                onClients(authed.size)
            } else {
                conn.send("""{"t":"denied"}""")
                conn.close()
            }
            return
        }

        if (!authed.contains(conn)) return
        onCommand(conn, j)
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        // conn == null: sunucunun kendisi hata verdi (ornek: port dolu) -> yeniden dene
        if (conn == null) onFatal()
    }

    override fun onStart() {
        connectionLostTimeout = 20
    }
}
