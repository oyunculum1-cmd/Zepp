package com.zapp.remote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

// ---- Renkler (onizlemedekiyle ayni) ----
val Bg = Color(0xFF0B0F24)
val Panel = Color(0xFF151A3A)
val Panel2 = Color(0xFF1F2552)
val Line = Color(0xFF2A3166)
val Violet = Color(0xFF7C5CFF)
val Cyan = Color(0xFF22D3EE)
val Muted = Color(0xFF9AA3C7)
val Green = Color(0xFF34D399)
val Red = Color(0xFFEF4444)
val Ink = Color(0xFFF3F5FF)
val Grad = Brush.linearGradient(listOf(Violet, Cyan))

@Composable
fun ZappTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet,
            secondary = Cyan,
            background = Bg,
            surface = Panel,
            onBackground = Ink,
            onSurface = Ink,
        ),
        content = content,
    )
}

/** Zapp logosu (120x120 birimlik cizim, istenen boyuta olceklenir). */
@Composable
fun ZappLogo(modifier: Modifier = Modifier, wave: Float = 1f) {
    Canvas(modifier) {
        val s = size.width / 120f
        drawRoundRect(
            brush = Grad,
            size = Size(120f * s, 120f * s),
            cornerRadius = CornerRadius(28f * s, 28f * s),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(22f * s, 22f * s),
            size = Size(36f * s, 36f * s),
            cornerRadius = CornerRadius(9f * s, 9f * s),
            style = Stroke(7f * s),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(33f * s, 33f * s),
            size = Size(14f * s, 14f * s),
            cornerRadius = CornerRadius(3f * s, 3f * s),
        )
        drawCircle(Color.White, radius = 7f * s, center = Offset(74f * s, 74f * s))
        drawArc(
            color = Color.White.copy(alpha = wave),
            startAngle = -90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(52f * s, 52f * s),
            size = Size(44f * s, 44f * s),
            style = Stroke(7f * s, cap = StrokeCap.Round),
        )
        drawArc(
            color = Color.White.copy(alpha = wave * 0.6f),
            startAngle = -90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(34f * s, 34f * s),
            size = Size(80f * s, 80f * s),
            style = Stroke(7f * s, cap = StrokeCap.Round),
        )
    }
}

/** Hem dokunmatik hem TV kumandasi (yon tuslari) ile kullanilabilen dugme. */
@Composable
fun ZButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    autoFocus: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            try {
                fr.requestFocus()
            } catch (e: Exception) {
            }
        }
    }
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(if (primary) Violet else Panel2)
            .border(if (focused) 3.dp else 2.dp, if (focused) Cyan else Line, shape)
            .focusRequester(fr)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Ink,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Yon tusu: yuvarlak, seffaf. */
@Composable
fun PadKey(glyph: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(CircleShape)
            .background(if (f) Panel2 else Color.Transparent)
            .then(if (f) Modifier.border(3.dp, Cyan, CircleShape) else Modifier)
            .onFocusChanged { f = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = Ink, fontSize = 30.sp)
    }
}

/** Ilk acilistaki "TV mi telefon mu?" kartlari. */
@Composable
fun BigCard(
    title: String,
    subtitle: String,
    highlighted: Boolean,
    autoFocus: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            try {
                fr.requestFocus()
            } catch (e: Exception) {
            }
        }
    }
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier
            .heightIn(min = 180.dp)
            .clip(shape)
            .background(if (highlighted) Violet.copy(alpha = 0.25f) else Panel)
            .border(
                if (focused) 4.dp else 3.dp,
                if (focused) Cyan else if (highlighted) Violet else Line,
                shape
            )
            .focusRequester(fr)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(title, color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

/** QR kodu cizer. */
@Composable
fun QrCode(text: String, size: Dp) {
    val matrix = remember(text) {
        QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.MARGIN to 0)
        )
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(20.dp)
    ) {
        Canvas(Modifier.size(size - 40.dp)) {
            val cell = this.size.width / matrix.width
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    if (matrix.get(x, y)) {
                        drawRect(
                            Color(0xFF0B0F24),
                            topLeft = Offset(x * cell, y * cell),
                            size = Size(cell + 0.6f, cell + 0.6f),
                        )
                    }
                }
            }
        }
    }
}
