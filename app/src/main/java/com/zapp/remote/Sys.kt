package com.zapp.remote

import android.content.Context
import android.content.Intent
import android.provider.Settings

val SYS_TILES: List<Pair<String, String>> = listOf(
    "settings" to "Ayarlar",
    "wifi" to "Wi-Fi",
    "bluetooth" to "Bluetooth",
    "display" to "Ekran",
    "sound" to "Ses",
    "access" to "Erişilebilirlik",
    "apps" to "Uygulamalar",
    "input" to "Klavye",
    "date" to "Tarih ve saat",
)

object SysActions {
    fun open(ctx: Context, p: String) {
        try {
            when (p) {
                "game" -> start(ctx, Intent(ctx, GameActivity::class.java))
                "zapp" -> start(ctx, Intent(ctx, MainActivity::class.java))
                else -> {
                    val a = when (p) {
                        "wifi" -> Settings.ACTION_WIFI_SETTINGS
                        "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                        "display" -> Settings.ACTION_DISPLAY_SETTINGS
                        "sound" -> Settings.ACTION_SOUND_SETTINGS
                        "access" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                        "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                        "input" -> Settings.ACTION_INPUT_METHOD_SETTINGS
                        "date" -> Settings.ACTION_DATE_SETTINGS
                        else -> Settings.ACTION_SETTINGS
                    }
                    try {
                        start(ctx, Intent(a))
                    } catch (e: Exception) {
                        start(ctx, Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }
        } catch (e: Exception) {
            ZLog.e("kisayol $p", e)
        }
    }

    private fun start(ctx: Context, i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun openApp(ctx: Context, pkg: String) {
        try {
            val i = ctx.packageManager.getLeanbackLaunchIntentForPackage(pkg)
                ?: ctx.packageManager.getLaunchIntentForPackage(pkg)
                ?: return
            start(ctx, i)
        } catch (e: Exception) {
            ZLog.e("uygulama $pkg", e)
        }
    }
}

