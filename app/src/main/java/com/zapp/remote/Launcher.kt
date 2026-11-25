package com.zapp.remote

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ana ekrandaki tek bir kisayol. k = "app" (paket) ya da "sys" (sistem/Zapp kisayolu). */
data class Tile(val k: String, val p: String, val n: String)

/**
 * TV ana ekrani duzeni.
 * tpl: cinema (Apple TV tarzi) | bar (Samsung tarzi) | strip (LG tarzi) | grid
 */
data class Layout(
    val tpl: String,
    val wall: Int,
    val title: String,
    val clock: Boolean,
    val tiles: List<Tile>,
) {
    fun toJson(): String {
        val a = JSONArray()
        tiles.forEach { a.put(JSONObject().put("k", it.k).put("p", it.p).put("n", it.n)) }
        return JSONObject()
            .put("tpl", tpl).put("wall", wall).put("title", title)
            .put("clock", clock).put("tiles", a).toString()
    }

    companion object {
        fun parse(s: String?): Layout? {
            if (s.isNullOrBlank()) return null
            return try {
                val o = JSONObject(s)
                val a = o.optJSONArray("tiles") ?: JSONArray()
                val t = ArrayList<Tile>()
                for (i in 0 until a.length()) {
                    val x = a.getJSONObject(i)
                    t.add(Tile(x.optString("k"), x.optString("p"), x.optString("n")))
                }
                Layout(
                    tpl = o.optString("tpl", "cinema"),
                    wall = o.optInt("wall", 0),
                    title = o.optString("title", ""),
                    clock = o.optBoolean("clock", true),
                    tiles = t,
                )
            } catch (e: Exception) {
                null
            }
        }

        /** Ilk acilis: kurulu TV uygulamalarindan bir kac tane + Ayarlar + Oyun. */
        fun default(ctx: Context): Layout {
            val pm = ctx.packageManager
            val apps = try {
                pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
                )
                    .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                    .filter { it.first != ctx.packageName }
                    .distinctBy { it.first }
                    .take(6)
            } catch (e: Exception) {
                emptyList()
            }
            val tiles = apps.map { Tile("app", it.first, it.second) } +
                Tile("sys", "settings", "Ayarlar") +
                Tile("sys", "game", "Yılan oyunu")
            return Layout("cinema", 0, "", true, tiles)
        }
    }
}

object LauncherStore {
    val layout = MutableStateFlow<Layout?>(null)

    fun load(ctx: Context): Layout {
        layout.value?.let { return it }
        val l = Layout.parse(Prefs.layoutJson(ctx)) ?: Layout.default(ctx)
        layout.value = l
        return l
    }

    fun json(ctx: Context): String = load(ctx).toJson()

    fun save(ctx: Context, s: String): Boolean {
        val l = Layout.parse(s) ?: return false
        Prefs.setLayoutJson(ctx, l.toJson())
        layout.value = l
        return true
    }
}

val WALLS: List<Pair<String, List<Color>>> = listOf(
    "Gece" to listOf(Color(0xFF0B0F24), Color(0xFF2A1B5E)),
    "Okyanus" to listOf(Color(0xFF031B34), Color(0xFF0E6BA8)),
    "Gün batımı" to listOf(Color(0xFF2B0B3F), Color(0xFFD9534F)),
    "Orman" to listOf(Color(0xFF06150F), Color(0xFF1F6B4A)),
    "Mor" to listOf(Color(0xFF14082E), Color(0xFF7C5CFF)),
    "Grafit" to listOf(Color(0xFF101114), Color(0xFF3A3D46)),
)

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
    "game" to "Yılan oyunu",
    "zapp" to "Zapp",
)

object SysActions {
    fun open(ctx: Context, p: String) {
        try {
            when (p) {
                "game" -> start(ctx, Intent(ctx, GameActivity::class.java))
                "zapp" -> start(ctx, Intent(ctx, MainActivity::class.java))
                "launcher" -> start(ctx, Intent(ctx, LauncherActivity::class.java))
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

/** "Ana ekran" tusuna Zapp'in ana ekranini atamak icin gizli takma adi acip kapatir. */
object HomeMode {
    private fun cn(ctx: Context) = ComponentName(ctx.packageName, "com.zapp.remote.HomeAlias")

    fun set(ctx: Context, on: Boolean) {
        ctx.packageManager.setComponentEnabledSetting(
            cn(ctx),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}

private val artCache = HashMap<String, ImageBitmap>()

private fun appArt(ctx: Context, pkg: String, wide: Boolean): ImageBitmap? {
    val key = "$pkg/$wide"
    artCache[key]?.let { return it }
    val pm = ctx.packageManager
    val bmp = try {
        if (wide) {
            val b = pm.getApplicationBanner(pkg)
            if (b != null) b.toBitmap(320, 180).asImageBitmap()
            else pm.getApplicationIcon(pkg).toBitmap(160, 160).asImageBitmap()
        } else {
            pm.getApplicationIcon(pkg).toBitmap(160, 160).asImageBitmap()
        }
    } catch (e: Exception) {
        null
    }
    if (bmp != null) artCache[key] = bmp
    return bmp
}

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherStore.load(this)
        // Ana ekranda "Geri" hicbir sey yapmaz
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        setContent { ZappTheme { LauncherScreen() } }
    }
}

@Composable
fun LauncherScreen() {
    val ctx = LocalContext.current
    val layout by LauncherStore.layout.collectAsState()
    val l = layout
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    if (l == null) {
        Box(Modifier.fillMaxSize().background(Bg))
        return
    }
    val wall = WALLS[l.wall.coerceIn(0, WALLS.lastIndex)].second
    val open: (Tile) -> Unit = { t ->
        if (t.k == "app") SysActions.openApp(ctx, t.p) else SysActions.open(ctx, t.p)
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(wall))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    l.title.ifBlank { "Zapp" },
                    color = Ink,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (l.clock) {
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)),
                        color = Ink,
                        fontSize = 34.sp,
                    )
                }
            }
            when (l.tpl) {
                "bar" -> BarTemplate(l, open)
                "strip" -> StripTemplate(l, open)
                "grid" -> GridTemplate(l, open)
                else -> CinemaTemplate(l, open)
            }
        }
    }
}

/** Apple TV tarzi: genis kartlar, ekranin alt yarisinda tek sira. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CinemaTemplate(l: Layout, open: (Tile) -> Unit) {
    Spacer(Modifier.weight(1f))
    TileRow(l.tiles, 280.dp, 158.dp, wide = true, labelInside = false, open = open)
    Spacer(Modifier.height(56.dp))
}

/** Samsung tarzi: altta yarim saydam cubuk icinde kucuk simgeler. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.BarTemplate(l: Layout, open: (Tile) -> Unit) {
    Spacer(Modifier.weight(1f))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0x66000000))
            .padding(vertical = 18.dp)
    ) {
        TileRow(l.tiles, 112.dp, 112.dp, wide = false, labelInside = false, open = open)
    }
    Spacer(Modifier.height(24.dp))
}

/** LG tarzi: altta dikdortgen kartlar, etiket kartin icinde. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.StripTemplate(l: Layout, open: (Tile) -> Unit) {
    Spacer(Modifier.weight(1f))
    TileRow(l.tiles, 220.dp, 124.dp, wide = true, labelInside = true, open = open)
    Spacer(Modifier.height(40.dp))
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.GridTemplate(l: Layout, open: (Tile) -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        try {
            fr.requestFocus()
        } catch (e: Exception) {
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier.weight(1f).padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        gridItemsIndexed(l.tiles) { i, t ->
            TileCard(t, 140.dp, 140.dp, false, false, open, if (i == 0) fr else null)
        }
    }
}

@Composable
private fun TileRow(
    tiles: List<Tile>,
    w: Dp,
    h: Dp,
    wide: Boolean,
    labelInside: Boolean,
    open: (Tile) -> Unit,
) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        try {
            fr.requestFocus()
        } catch (e: Exception) {
        }
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 24.dp),
    ) {
        itemsIndexed(tiles) { i, t ->
            TileCard(t, w, h, wide, labelInside, open, if (i == 0) fr else null)
        }
    }
}

@Composable
private fun TileCard(
    t: Tile,
    w: Dp,
    h: Dp,
    wide: Boolean,
    labelInside: Boolean,
    open: (Tile) -> Unit,
    fr: FocusRequester?,
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "scale")
    val shape = RoundedCornerShape(20.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Box(
            Modifier
                .size(w, h)
                .clip(shape)
                .background(Panel2)
                .border(if (focused) 3.dp else 0.dp, if (focused) Cyan else Color.Transparent, shape)
                .then(if (fr != null) Modifier.focusRequester(fr) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .clickable { open(t) },
            contentAlignment = Alignment.Center,
        ) {
            val art = if (t.k == "app") appArt(ctx, t.p, wide) else null
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = t.n,
                    contentScale = if (wide && art.width > art.height * 1.4f) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(if (wide && art.width <= art.height * 1.4f) 24.dp else 0.dp),
                )
            } else {
                Text(
                    t.n.take(2).uppercase(),
                    color = Ink,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (labelInside) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xAA000000))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t.n, color = Ink, fontSize = 15.sp, maxLines = 1)
                }
            }
        }
        if (!labelInside) {
            Spacer(Modifier.height(8.dp))
            Text(t.n, color = Ink, fontSize = 16.sp, maxLines = 1)
        }
    }
}
