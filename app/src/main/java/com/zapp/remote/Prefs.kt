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
    val mac: String = "",
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

    // ---- Oyun ----
    fun gameHigh(c: Context): Int = sp(c).getInt("game_high", 0)

    fun setGameHigh(c: Context, v: Int) {
        sp(c).edit().putInt("game_high", v).apply()
    }

    fun gameBest(c: Context, mode: String): Int = sp(c).getInt("game_best_$mode", 0)

    fun setGameBest(c: Context, mode: String, v: Int) {
        sp(c).edit().putInt("game_best_$mode", v).apply()
    }

    // ---- Admin (TV tarafi) ----
    const val DEFAULT_ADMIN_CODE = "OOOUUDDO"   // 3xOK, 2xYukari, 2xAsagi, 1xOK

    fun hashOf(code: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(("zapp|" + code).toByteArray()).joinToString("") { "%02x".format(it) }

    fun adminCodeHash(c: Context): String =
        sp(c).getString("admin_code", null) ?: hashOf(DEFAULT_ADMIN_CODE)

    fun setAdminCode(c: Context, code: String) {
        sp(c).edit().putString("admin_code", hashOf(code)).apply()
    }

    fun adminDev(c: Context): String = sp(c).getString("admin_dev", "") ?: ""
    fun adminIp(c: Context): String = sp(c).getString("admin_ip", "") ?: ""

    fun setAdmin(c: Context, dev: String, ip: String) {
        sp(c).edit().putString("admin_dev", dev).putString("admin_ip", ip).apply()
    }

    fun banned(c: Context): Map<String, String> {
        val o = try {
            JSONObject(sp(c).getString("banned", "{}"))
        } catch (e: Exception) {
            JSONObject()
        }
        val m = LinkedHashMap<String, String>()
        o.keys().forEach { m[it] = o.optString(it) }
        return m
    }

    fun setBanned(c: Context, m: Map<String, String>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        sp(c).edit().putString("banned", o.toString()).apply()
    }

    fun locked(c: Context): Boolean = sp(c).getBoolean("locked", false)

    fun setLocked(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("locked", v).apply()
    }

    /** Tum eslesmeleri sifirlar: yeni anahtar uretilir, eski telefonlar baglanamaz. */
    fun resetToken(c: Context) {
        sp(c).edit().remove("tv_token").putBoolean("tv_paired", false).apply()
    }

    // ---- Admin (telefon tarafi) ----
    /** Bu telefonu taniyan kalici kimlik. */
    fun devId(c: Context): String {
        sp(c).getString("dev_id", null)?.let { return it }
        val b = ByteArray(8)
        SecureRandom().nextBytes(b)
        val t = b.joinToString("") { "%02x".format(it) }
        sp(c).edit().putString("dev_id", t).apply()
        return t
    }

    /** Bu telefonun bildigi yonetici kodu (varsayilan ya da yonetici olarak degistirdigi). */
    fun adminLocalCode(c: Context): String = sp(c).getString("admin_local", null) ?: DEFAULT_ADMIN_CODE

    fun setAdminLocalCode(c: Context, code: String) {
        sp(c).edit().putString("admin_local", code).apply()
    }

    fun adminWant(c: Context): String = sp(c).getString("admin_want", "") ?: ""

    fun setAdminWant(c: Context, code: String) {
        sp(c).edit().putString("admin_want", code).apply()
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
                    mac = o.optString("mac", ""),
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
                    .put("mac", it.mac)
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

object NetMac {
    /** TV'nin MAC adresi (Wake-on-LAN icin). Android 11+ cogu zaman vermez; bos donebilir. */
    fun get(): String {
        for (n in listOf("eth0", "wlan0")) {
            try {
                val b = NetworkInterface.getByName(n)?.hardwareAddress
                if (b != null && b.size == 6 && b.any { it.toInt() != 0 }) {
                    return b.joinToString(":") { "%02x".format(it) }
                }
            } catch (e: Exception) {
            }
            try {
                val t = java.io.File("/sys/class/net/$n/address").readText().trim()
                if (t.length == 17 && t != "02:00:00:00:00:00") return t
            } catch (e: Exception) {
            }
        }
        return ""
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
