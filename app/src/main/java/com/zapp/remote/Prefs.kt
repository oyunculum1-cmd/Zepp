package com.zapp.remote

import android.content.Context
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

/** Telefonun kayitli oldugu bir TV. */
data class Device(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
)

object Prefs {
    const val PORT = 8765

    private fun sp(c: Context) = c.getSharedPreferences("zapp", Context.MODE_PRIVATE)

    // ---- Ortak: "tv" veya "phone" secimi (ilk acilista bir kez sorulur) ----
    fun mode(c: Context): String? = sp(c).getString("mode", null)

    fun setMode(c: Context, m: String?) {
        val e = sp(c).edit()
        if (m == null) e.remove("mode") else e.putString("mode", m)
        e.apply()
    }

    // ---- TV tarafi ----
    /** TV'ye ozel gizli anahtar. Bir kez uretilir ve hep ayni kalir: eslesme boylece kalici olur. */
    fun tvToken(c: Context): String {
        sp(c).getString("tv_token", null)?.let { return it }
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        val t = b.joinToString("") { "%02x".format(it) }
        sp(c).edit().putString("tv_token", t).apply()
        return t
    }

    fun tvName(): String = Build.MODEL ?: "TV"

    fun tvPaired(c: Context): Boolean = sp(c).getBoolean("tv_paired", false)

    fun setTvPaired(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("tv_paired", v).apply()
    }

    // ---- Telefon tarafi ----
    fun devices(c: Context): List<Device> {
        val a = try {
            JSONArray(sp(c).getString("devices", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<Device>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(
                Device(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    host = o.getString("host"),
                    port = o.getInt("port"),
                    token = o.getString("token"),
                )
            )
        }
        return out
    }

    private fun saveDevices(c: Context, list: List<Device>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("host", it.host)
                    .put("port", it.port)
                    .put("token", it.token)
            )
        }
        sp(c).edit().putString("devices", a.toString()).apply()
    }

    fun upsert(c: Context, d: Device) {
        saveDevices(c, devices(c).filter { it.id != d.id } + d)
    }

    fun remove(c: Context, id: String) {
        saveDevices(c, devices(c).filter { it.id != id })
        if (activeId(c) == id) sp(c).edit().remove("active").apply()
    }

    fun activeId(c: Context): String? = sp(c).getString("active", null)

    fun setActive(c: Context, id: String) {
        sp(c).edit().putString("active", id).apply()
    }
}

object NetUtil {
    /** Bu cihazin yerel agdaki IPv4 adresi (ornek 192.168.1.20). */
    fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }
}

object Pairing {
    /** TV'de gosterilen QR'in icerigi. */
    fun toUri(host: String, port: Int, token: String, name: String): String =
        Uri.Builder()
            .scheme("zapp")
            .authority("pair")
            .appendQueryParameter("h", host)
            .appendQueryParameter("p", port.toString())
            .appendQueryParameter("t", token)
            .appendQueryParameter("n", name)
            .build()
            .toString()

    fun parse(s: String): Device? {
        val u = try {
            Uri.parse(s.trim())
        } catch (e: Exception) {
            return null
        }
        if (u.scheme != "zapp") return null
        val h = u.getQueryParameter("h") ?: return null
        val p = u.getQueryParameter("p")?.toIntOrNull() ?: return null
        val t = u.getQueryParameter("t") ?: return null
        if (t.length < 8) return null
        return Device(
            id = t.take(8),
            name = u.getQueryParameter("n") ?: "TV",
            host = h,
            port = p,
            token = t,
        )
    }
}
