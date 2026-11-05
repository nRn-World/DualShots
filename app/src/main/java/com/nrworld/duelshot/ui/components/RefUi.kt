package com.nrworld.duelshot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/*
 * Skärmarna (Home, Gallery, Stats, Settings) är ritade i referensbildernas
 * egna pixlar (1024 breda, design/reference). [LocalRefScale] är dp per
 * referenspixel, så proportionerna blir desamma på alla telefoner.
 */

val LocalRefScale = staticCompositionLocalOf { 0.4f }

/** Lodrät utsträckning: telefoner är högre än referensbildernas 2:3, så höjder och avstånd växer. */
val LocalRefStretch = staticCompositionLocalOf { 1f }

/** Textstorlek relativt referensen (större text på hög skärm). */
val LocalRefFont = staticCompositionLocalOf { 1f }

/** Referenspixlar → dp. */
@Composable
fun rp(px: Number): Dp = (px.toFloat() * LocalRefScale.current).dp

/** Referenspixlar lodrätt (höjder, avstånd, y-lägen) → dp, utsträckt efter skärmens höjd. */
@Composable
fun rv(px: Number): Dp = (px.toFloat() * LocalRefScale.current * LocalRefStretch.current).dp

/** Referenspixlar → dp med textfaktorn (för text och dess radhöjd). */
@Composable
fun rf(px: Number): Dp = (px.toFloat() * LocalRefScale.current * LocalRefFont.current).dp

/** Referenspixlar → sp, oberoende av systemets textstorlek (layouten är fast). */
@Composable
fun rs(px: Number): TextUnit = with(LocalDensity.current) { rf(px).toSp() }

// Färger från referensbilderna
val RefBg = Color(0xFF0A0D14)
val RefCard = Color(0xFF111724)
val RefCardBorder = Color(0xFF1F2738)
val RefChip = Color(0xFF161C2A)
val RefChipBorder = Color(0xFF252D3F)
val RefOrange = Color(0xFFFF6A10)
val RefOrangeLight = Color(0xFFFF9A1C)
val RefGold = Color(0xFFFFA53A)
val RefCrown = Color(0xFFFFB02E)
val RefMuted = Color(0xFF8F99AD)
val RefSub = Color(0xFF9AA3B5)
val RefLabel = Color(0xFFA3ABBD)
val RefFaint = Color(0xFF7D879C)
val RefSoft = Color(0xFFC9D0DC)
val RefGreen = Color(0xFF3DDC84)

/** Text placerad med vänsterkant [x] och överkant [top] (radhöjd [lh]). */
@Composable
fun BoxScope.RText(
    text: String,
    x: Number,
    top: Number,
    size: Number,
    lh: Number,
    color: Color = Color.White,
    weight: FontWeight = FontWeight.Normal,
    mono: Boolean = false,
    ls: Number = 0,
    right: Boolean = false,
    maxWidth: Number? = null
) {
    RText(AnnotatedString(text), x, top, size, lh, color, weight, mono, ls, right, maxWidth)
}

@Composable
fun BoxScope.RText(
    text: AnnotatedString,
    x: Number,
    top: Number,
    size: Number,
    lh: Number,
    color: Color = Color.White,
    weight: FontWeight = FontWeight.Normal,
    mono: Boolean = false,
    ls: Number = 0,
    right: Boolean = false,
    maxWidth: Number? = null
) {
    var m: Modifier = if (right) Modifier.align(Alignment.TopEnd).absoluteOffset(x = -rp(x), y = rv(top))
    else Modifier.absoluteOffset(x = rp(x), y = rv(top))
    if (maxWidth != null) m = m.then(Modifier.size(width = rp(maxWidth), height = rf(lh)))
    Text(
        text,
        modifier = m,
        color = color,
        fontSize = rs(size),
        lineHeight = rs(lh),
        fontWeight = weight,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        letterSpacing = rs(ls),
        textAlign = if (right) TextAlign.End else TextAlign.Start,
        maxLines = if (maxWidth != null) 1 else Int.MAX_VALUE,
        overflow = if (maxWidth != null) TextOverflow.Ellipsis else TextOverflow.Clip,
        softWrap = maxWidth == null
    )
}

/** "Dual" vit + "Shots" orange. */
fun brandText(): AnnotatedString = buildAnnotatedString {
    append("Dual")
    withStyle(SpanStyle(color = RefOrange)) { append("Shots") }
}

@Composable
fun BoxScope.Brand(x: Number, top: Number, size: Number) {
    RText(brandText(), x, top, size, size, weight = FontWeight.Bold, ls = -1.6)
}

/** Rund ikonknapp (profil, sök, meny, kugghjul). */
@Composable
fun BoxScope.RCircle(icon: RefIconKind, x: Number, top: Number, d: Number, iconSize: Number, bg: Color = RefChip, onClick: () -> Unit) {
    Box(
        Modifier.absoluteOffset(rp(x), rv(top)).size(rp(d)).clip(CircleShape)
            .background(bg).border(rp(2), RefChipBorder, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { RefIcon(icon, Color(0xFFE8ECF4), rp(iconSize)) }
}

/** "See All ›" i orange. */
@Composable
fun SeeAll(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rp(14))
    ) {
        Text("See All", color = RefOrange, fontSize = rs(23), lineHeight = rs(30), fontWeight = FontWeight.Medium)
        RefIcon(RefIconKind.CHEVRON, RefOrange, rp(24), stroke = 2.6f)
    }
}

/** Linjeikoner i 24×24-rutnät, ritade från SVG-banor (samma som webbversionen). */
enum class RefIconKind(vararg val parts: String) {
    HOME("M3 10.5 12 3.5l9 7V20h-6v-5.5H9V20H3z"),
    GALLERY(rr(3f, 5f, 18f, 14f, 3f), "F" + "M10 9.5 15.5 12 10 14.5z"),
    STATS("M3.5 18.5 9 9.5l4.5 6L20.5 4.5", "F" + circ(3.5f, 18.5f, 1.4f), "F" + circ(9f, 9.5f, 1.4f), "F" + circ(13.5f, 15.5f, 1.4f), "F" + circ(20.5f, 4.5f, 1.4f)),
    SLIDERS("M3 6h18M3 12h18M3 18h18", "F" + circ(8f, 6f, 2.6f), "F" + circ(16f, 12f, 2.6f), "F" + circ(11f, 18f, 2.6f)),
    USER(circ(12f, 8f, 4f), "M4 21c0-4 3.6-7 8-7s8 3 8 7"),
    SEARCH(circ(11f, 11f, 7f), "M20 20l-3.5-3.5"),
    DOTS("F" + circ(12f, 5f, 1.7f), "F" + circ(12f, 12f, 1.7f), "F" + circ(12f, 19f, 1.7f)),
    CHEVRON("M9 5l7 7-7 7"),
    CHEV_DOWN("m5 9 7 7 7-7"),
    VIDEO(rr(2.5f, 6.5f, 13f, 11f, 2.5f), "M15.5 10.5 21.5 7v10l-6-3.5z"),
    CAMERA(rr(3f, 7f, 18f, 13f, 3f), "M8 7l1.5-2.5h5L16 7", circ(12f, 13.5f, 3.2f)),
    LAYERS("M12 3 3 8l9 5 9-5z", "m3 13 9 5 9-5"),
    GEAR(circ(12f, 12f, 3f), "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"),
    CROWN("M3 8l4.5 4.5L12 5l4.5 7.5L21 8l-2 11H5z"),
    BULB("M9 18h6M10 21h4", "M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z"),
    GRID4(rr(3.5f, 3.5f, 7f, 7f, 1.6f), rr(13.5f, 3.5f, 7f, 7f, 1.6f), rr(3.5f, 13.5f, 7f, 7f, 1.6f), rr(13.5f, 13.5f, 7f, 7f, 1.6f)),
    PHOTO(rr(3f, 4f, 18f, 16f, 3f), circ(9f, 10f, 1.8f), "m21 16-5-5-8 8"),
    HEART("M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"),
    TRASH("M4 7h16", "M9.5 7V4.5h5V7", "M6.5 7l1.1 13h8.8L17.5 7", "M10.5 10.5 11 16.5M13.5 10.5 13 16.5"),
    CLOUD("M7 18a4.5 4.5 0 0 1-.6-9A6 6 0 0 1 18 9.5a4 4 0 0 1 .5 8.5z", "M12 12v6M9.5 14.5 12 12l2.5 2.5"),
    MOUNTAIN("M3 19 9.5 9l4 6 2.5-3.5L21 19z"),
    CALENDAR(rr(3.5f, 5f, 17f, 15.5f, 3f), "M3.5 10h17M8 3v4M16 3v4"),
    FILE("M7 3h7l5 5v13H7z", "M14 3v5h5"),
    FILM(rr(3.5f, 3f, 17f, 18f, 1.5f), "M7.5 3v18M16.5 3v18M3.5 8h4M3.5 12h4M3.5 16h4M16.5 8h4M16.5 12h4M16.5 16h4"),
    STABILIZE("M12 3v18M3.5 12h5M15.5 12h5M5.5 6.5l2.5 2.5M18.5 6.5 16 9M5.5 17.5 8 15M18.5 17.5 16 15", "F" + rr(10f, 9.5f, 4f, 5f, 1f)),
    AUDIO("M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M16 9a4 4 0 0 1 0 6M18.5 6.5a7.5 7.5 0 0 1 0 11"),
    PIN("M12 21s-6-5.2-6-10.2a6 6 0 0 1 12 0C18 15.8 12 21 12 21z", circ(12f, 11f, 2.2f)),
    SEPARATE(rr(2.5f, 4.5f, 8.5f, 15f, 1.8f), rr(13f, 4.5f, 8.5f, 15f, 1.8f)),
    OVERLAP(rr(2.5f, 3f, 13f, 13f, 2.2f), rr(8.5f, 8.5f, 13f, 13f, 2.2f)),
    DATABASE("M4.5 5.5a7.5 2.8 0 1 0 15 0a7.5 2.8 0 1 0-15 0", "M4.5 5.5v13c0 1.5 3.4 2.8 7.5 2.8s7.5-1.3 7.5-2.8v-13", "M4.5 12c0 1.5 3.4 2.8 7.5 2.8s7.5-1.3 7.5-2.8"),
    STAR("m12 3.2 2.7 5.6 6.1.8-4.5 4.2 1.1 6.1L12 17l-5.4 2.9 1.1-6.1-4.5-4.2 6.1-.8z"),
    TREND("M3.5 17.5 9 11.5l3.5 3.5 7-8", "M15 7h4.5v4.5", "F" + circ(3.5f, 17.5f, 1.3f), "F" + circ(9f, 11.5f, 1.3f), "F" + circ(12.5f, 15f, 1.3f)),
    ARROW_UP_RIGHT("M6 18 18 6M8.5 6H18v9.5")
}

private fun circ(cx: Float, cy: Float, r: Float) = "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0z"

private fun rr(x: Float, y: Float, w: Float, h: Float, r: Float): String {
    val iw = w - 2 * r
    val ih = h - 2 * r
    return "M${x + r},$y h$iw a$r,$r 0 0 1 $r,$r v$ih a$r,$r 0 0 1 ${-r},$r h${-iw} a$r,$r 0 0 1 ${-r},${-r} v${-ih} a$r,$r 0 0 1 $r,${-r}z"
}

/** Ritar en [RefIconKind]. Delar som börjar med "F" fylls, resten är linjer. */
@Composable
fun RefIcon(kind: RefIconKind, tint: Color, size: Dp, modifier: Modifier = Modifier, stroke: Float = 1.8f) {
    val paths = remember(kind) {
        kind.parts.map { part ->
            val fill = part.startsWith("F")
            PathParser().parsePathString(if (fill) part.substring(1) else part).toPath() to fill
        }
    }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            paths.forEach { (path, fill) ->
                if (fill) drawPath(path, tint)
                else drawPath(path, tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
