package com.zapp.remote

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZappTheme {
                App()
            }
        }
    }
}

fun isTv(ctx: Context): Boolean {
    val m = ctx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return m.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var splash by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(Prefs.mode(ctx)) }

    LaunchedEffect(Unit) {
        delay(2800)
        splash = false
    }

    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        when {
            splash -> Splash()
            mode == null -> ModeSelect { chosen ->
                Prefs.setMode(ctx, chosen)
                mode = chosen
            }
            mode == "tv" -> TvHome(onChangeMode = {
                Prefs.setMode(ctx, null)
                mode = null
            })
            else -> PhoneRoot(onChangeMode = {
                RemoteClient.disconnect()
                Prefs.setMode(ctx, null)
                mode = null
            })
        }
    }
}

/** Ilk acilista bir kez sorulur: bu cihaz TV mi, telefon mu? */
@Composable
fun ModeSelect(onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val tv = remember { isTv(ctx) }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ZappLogo(Modifier.size(72.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            "Bu uygulamayı hangi cihazda açtın?",
            color = Ink,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("Seçimini bir kez yaparsın, sonra hatırlar.", color = Muted, fontSize = 18.sp)
        Spacer(Modifier.height(28.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth > 600.dp
            val tvCard: @Composable (Modifier) -> Unit = { m ->
                BigCard(
                    title = "Android TV",
                    subtitle = "Bu cihaz televizyon",
                    highlighted = tv,
                    autoFocus = tv,
                    modifier = m,
                ) { onPick("tv") }
            }
            val phoneCard: @Composable (Modifier) -> Unit = { m ->
                BigCard(
                    title = "Telefon",
                    subtitle = "Kumanda olarak kullan",
                    highlighted = !tv,
                    autoFocus = !tv,
                    modifier = m,
                ) { onPick("phone") }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    tvCard(Modifier.weight(1f))
                    phoneCard(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    tvCard(Modifier.fillMaxWidth())
                    phoneCard(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
