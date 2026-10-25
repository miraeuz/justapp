@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package uz.mirae.prep

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme

class Pal(
    val dark: Boolean, val bg: Color, val card: Color, val text: Color, val sub: Color, val line: Color,
    val green: Color, val greenD: Color, val greenSoft: Color, val blue: Color, val blueD: Color, val blueSoft: Color,
    val orange: Color, val orangeD: Color, val red: Color, val redD: Color, val redSoft: Color,
    val purple: Color, val purpleD: Color, val yellow: Color
)
val LightPal = Pal(false, Color(0xFFF4F7FB), Color.White, Color(0xFF1F2A37), Color(0xFF6B7785), Color(0xFFE2E8F0),
    Color(0xFF58CC02), Color(0xFF46A302), Color(0xFFE3F7CF), Color(0xFF1CB0F6), Color(0xFF1899D6), Color(0xFFDDF4FF),
    Color(0xFFFF9600), Color(0xFFD98000), Color(0xFFFF4B4B), Color(0xFFEA2B2B), Color(0xFFFFE0E0),
    Color(0xFFCE82FF), Color(0xFFA568CC), Color(0xFFFFC800))
val DarkPal = Pal(true, Color(0xFF131F24), Color(0xFF1F2C34), Color(0xFFEAF2F6), Color(0xFF9FB1BD), Color(0xFF37464F),
    Color(0xFF58CC02), Color(0xFF46A302), Color(0xFF213A17), Color(0xFF1CB0F6), Color(0xFF1899D6), Color(0xFF16384A),
    Color(0xFFFF9600), Color(0xFFD98000), Color(0xFFFF4B4B), Color(0xFFEA2B2B), Color(0xFF3D1E1E),
    Color(0xFFCE82FF), Color(0xFFA568CC), Color(0xFFFFC800))
val LocalPal = staticCompositionLocalOf { LightPal }
val P: Pal @Composable get() = LocalPal.current

val Nunito = FontFamily(listOf(400, 600, 700, 800, 900).map { w ->
    Font(R.font.nunito, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
})

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val pal = if (dark) DarkPal else LightPal
    CompositionLocalProvider(LocalPal provides pal) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme(primary = pal.green, background = pal.bg, surface = pal.card)
            else lightColorScheme(primary = pal.green, background = pal.bg, surface = pal.card),
            content = content
        )
    }
}

@Composable
fun T(text: String, size: Int = 16, w: Int = 700, color: Color = P.text, modifier: Modifier = Modifier,
      align: TextAlign? = null, mono: Boolean = false, lh: Int = 0, maxLines: Int = Int.MAX_VALUE) =
    Text(text, modifier, color = color, fontSize = size.sp, fontWeight = FontWeight(w),
        fontFamily = if (mono) FontFamily.Monospace else Nunito, textAlign = align,
        lineHeight = if (lh > 0) lh.sp else TextUnit.Unspecified, maxLines = maxLines)

@Composable fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
@Composable fun HSpace(w: Int) = Spacer(Modifier.width(w.dp))

@Composable
fun Chunky(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, face: Color = P.green, edge: Color = P.greenD,
           enabled: Boolean = true, textColor: Color = Color.White, height: Int = 54, icon: ImageVector? = null) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val off by animateDpAsState(if (pressed && enabled) 1.dp else 5.dp, label = "chunky")
    val shape = RoundedCornerShape(16.dp)
    val f = if (enabled) face else P.line
    val e = if (enabled) edge else P.sub.copy(alpha = .3f)
    val tc = if (enabled) textColor else P.sub
    Box(modifier.height((height + 5).dp).clickable(src, null, enabled = enabled, onClick = onClick)) {
        Box(Modifier.fillMaxSize().padding(top = 5.dp).background(e, shape))
        Box(Modifier.fillMaxWidth().height(height.dp).offset(y = 5.dp - off).background(f, shape), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { Icon(icon, null, tint = tc, modifier = Modifier.size(22.dp)); HSpace(8) }
                T(text.uppercase(), 15, 800, tc)
            }
        }
    }
}

@Composable
fun Card2(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color = P.card, border: Color = P.line,
          content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val m = modifier.clip(shape).background(color).border(2.dp, border, shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Column(m.padding(16.dp), content = content)
}

@Composable
fun Ring(progress: Float, dia: Dp, stroke: Dp, color: Color, track: Color = P.line, content: @Composable BoxScope.() -> Unit = {}) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "ring")
    Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val sz = Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, Offset(s / 2, s / 2), sz, style = Stroke(s, cap = StrokeCap.Round))
            if (p > 0f) drawArc(color, -90f, 360f * p, false, Offset(s / 2, s / 2), sz, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun Mascot(size: Dp, mood: Int = 1, bob: Boolean = true) {
    val tr = rememberInfiniteTransition(label = "mascot")
    val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val navy = Color(0xFF1F2A44); val body = Color(0xFF1CB0F6); val orange = Color(0xFFFF9600); val gold = Color(0xFFFFC800)
    Canvas(Modifier.size(size).offset(y = if (bob) (-5 * v).dp else 0.dp)) {
        val w = this.size.width
        drawCircle(body, w * .40f, Offset(w / 2, w * .58f))
        drawOval(Color.White.copy(alpha = .88f), Offset(w * .3f, w * .55f), Size(w * .4f, w * .36f))
        drawOval(orange, Offset(w * .3f, w * .93f), Size(w * .16f, w * .06f))
        drawOval(orange, Offset(w * .54f, w * .93f), Size(w * .16f, w * .06f))
        listOf(.37f, .63f).forEach { cx ->
            drawCircle(Color.White, w * .115f, Offset(w * cx, w * .5f))
            val dy = if (mood == 2) .03f else 0f
            drawCircle(navy, w * .055f, Offset(w * cx, w * (.51f + dy)))
            if (mood != 2) drawCircle(Color.White, w * .016f, Offset(w * cx + w * .02f, w * .495f))
        }
        val beak = Path().apply { moveTo(w * .44f, w * .6f); lineTo(w * .56f, w * .6f); lineTo(w * .5f, w * .69f); close() }
        drawPath(beak, orange)
        if (mood == 2) {
            drawLine(navy, Offset(w * .27f, w * .36f), Offset(w * .43f, w * .4f), w * .025f, StrokeCap.Round)
            drawLine(navy, Offset(w * .73f, w * .36f), Offset(w * .57f, w * .4f), w * .025f, StrokeCap.Round)
        }
        if (mood == 1) {
            drawCircle(Color(0xFFFF8FA3).copy(alpha = .6f), w * .04f, Offset(w * .26f, w * .6f))
            drawCircle(Color(0xFFFF8FA3).copy(alpha = .6f), w * .04f, Offset(w * .74f, w * .6f))
        }
        val cap = Path().apply { moveTo(w * .14f, w * .26f); lineTo(w * .5f, w * .1f); lineTo(w * .86f, w * .26f); lineTo(w * .5f, w * .4f); close() }
        drawPath(cap, navy)
        drawRect(navy, Offset(w * .34f, w * .3f), Size(w * .32f, w * .1f))
        drawLine(gold, Offset(w * .8f, w * .27f), Offset(w * .8f, w * .42f), w * .02f, StrokeCap.Round)
        drawCircle(gold, w * .03f, Offset(w * .8f, w * .44f))
    }
}

private class Piece(val x: Float, val vx: Float, val vy: Float, val c: Color, val s: Float, val r: Float, val rv: Float)

@Composable
fun Confetti() {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2800, easing = LinearEasing)) }
    val cols = listOf(Color(0xFF58CC02), Color(0xFF1CB0F6), Color(0xFFFF9600), Color(0xFFCE82FF), Color(0xFFFF4B4B), Color(0xFFFFC800))
    val ps = remember {
        val r = java.util.Random(7)
        List(80) { Piece(r.nextFloat(), (r.nextFloat() - .5f) * .4f, .5f + r.nextFloat() * .7f, cols[r.nextInt(cols.size)],
            8f + r.nextFloat() * 10f, r.nextFloat() * 360f, (r.nextFloat() - .5f) * 720f) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val tt = t.value
        if (tt < 1f) ps.forEach { p ->
            val x = (p.x + p.vx * tt) * this.size.width
            val y = (-.05f + p.vy * tt + .5f * tt * tt) * this.size.height
            rotate(p.r + p.rv * tt, Offset(x, y)) {
                drawRect(p.c.copy(alpha = 1f - tt * tt), Offset(x, y), Size(p.s * 2, p.s))
            }
        }
    }
}

@Composable
fun StatChip(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp)); HSpace(4); T(text, 17, 800, tint)
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, bg: Color, size: Int = 48) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size / 2 + 2).dp))
    }
}
