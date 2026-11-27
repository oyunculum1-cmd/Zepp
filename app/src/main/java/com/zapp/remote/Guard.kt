package com.zapp.remote

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings

/**
 * Erisilebilirlik iznini kapali birakmaz.
 * Gerekli tek sey: bir kerelik  adb shell pm grant com.zapp.remote android.permission.WRITE_SECURE_SETTINGS
 * Bu izin varsa, Android izni kapattiginda hemen yeniden acilir.
 */
object A11yGuard {
    const val GRANT_CMD = "adb shell pm grant com.zapp.remote android.permission.WRITE_SECURE_SETTINGS"

    private fun comp(c: Context) = ComponentName(c, ZappAccessibilityService::class.java).flattenToString()

    fun canWrite(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun isOn(c: Context): Boolean = try {
        val list = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val on = Settings.Secure.getInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        on && list.split(':').any { it.equals(comp(c), true) || it.equals(c.packageName + "/" + ZappAccessibilityService::class.java.name, true) }
    } catch (e: Exception) {
        false
    }

    /** Izin kapaliysa acar. Basariliysa true. */
    fun ensure(c: Context): Boolean {
        if (isOn(c)) return true
        if (!canWrite(c)) return false
        return try {
            val cur = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val parts = cur.split(':').filter { it.isNotBlank() }.toMutableList()
            if (parts.none { it.equals(comp(c), true) }) parts.add(comp(c))
            Settings.Secure.putString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, parts.joinToString(":"))
            Settings.Secure.putInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            ZLog.d("erisilebilirlik izni geri acildi")
            true
        } catch (e: Exception) {
            ZLog.e("izin acilamadi", e)
            false
        }
    }

    /** Koruma servisini baslatir (sadece izin varsa anlamli). */
    fun start(c: Context) {
        if (!canWrite(c)) return
        try {
            val i = Intent(c, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        } catch (e: Exception) {
            ZLog.e("koruma baslatilamadi", e)
        }
    }
}

class GuardService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            try {
                A11yGuard.ensure(this@GuardService)
            } catch (e: Throwable) {
            }
            handler.postDelayed(this, 3000)
        }
    }
    private var obs: ContentObserver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel("guard", "Zapp koruma", NotificationManager.IMPORTANCE_MIN))
            }
            val n = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, "guard")
            } else {
                @Suppress("DEPRECATION") Notification.Builder(this)
            }.setContentTitle("Zapp açık tutuluyor").setSmallIcon(android.R.drawable.ic_lock_idle_lock).build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(7, n)
            }
        } catch (e: Throwable) {
            ZLog.e("koruma bildirimi", e)
        }
        obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                try {
                    A11yGuard.ensure(this@GuardService)
                } catch (e: Throwable) {
                }
            }
        }
        try {
            contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, obs!!)
            contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false, obs!!)
        } catch (e: Throwable) {
            ZLog.e("koruma gozcusu", e)
        }
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        try {
            obs?.let { contentResolver.unregisterContentObserver(it) }
        } catch (e: Throwable) {
        }
        super.onDestroy()
    }
}

/** TV acilinca / uygulama guncellenince korumayi tekrar baslatir. */
class GuardBoot : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        try {
            if (Prefs.mode(c) == "tv") {
                A11yGuard.ensure(c)
                A11yGuard.start(c)
            }
        } catch (e: Throwable) {
        }
    }
}
