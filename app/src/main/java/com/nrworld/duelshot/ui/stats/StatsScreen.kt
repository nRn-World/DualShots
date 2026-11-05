package com.nrworld.duelshot.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.nrworld.duelshot.ui.components.Brand
import com.nrworld.duelshot.ui.components.RText
import com.nrworld.duelshot.ui.components.RefCard
import com.nrworld.duelshot.ui.components.RefCardBorder
import com.nrworld.duelshot.ui.components.RefCrown
import com.nrworld.duelshot.ui.components.RefFaint
import com.nrworld.duelshot.ui.components.RefGold
import com.nrworld.duelshot.ui.components.RefGreen
import com.nrworld.duelshot.ui.components.RefIcon
import com.nrworld.duelshot.ui.components.RefIconKind
import com.nrworld.duelshot.ui.components.RefLabel
import com.nrworld.duelshot.ui.components.RefMuted
import com.nrworld.duelshot.ui.components.RefOrange
import com.nrworld.duelshot.ui.components.RefSoft
import com.nrworld.duelshot.ui.components.RefSub
import com.nrworld.duelshot.ui.components.rp
import com.nrworld.duelshot.ui.components.rs
import com.nrworld.duelshot.ui.gallery.MediaItem
import com.nrworld.duelshot.ui.gallery.dayStart
import com.nrworld.duelshot.ui.gallery.formatSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private data class Stats(
    val total: Int,
    val totalDelta: String,
    val cards: List<Triple<RefIconKind, String, Pair<String, String>>>,
    val days: List<Pair<String, String>>,
    val series: Map<String, List<Float>>,
    val usage: Triple<Int, Int, Int>,
    val storageLabel: String,
    val storageFrac: Float,
    val rows: List<String>
)

private val DemoStats = Stats(
    total = 24, totalDelta = "+60%",
    cards = listOf(
        Triple(RefIconKind.CAMERA, "RECORDING TIME", "52 min" to "+32%"),
        Triple(RefIconKind.DATABASE, "STORAGE USED", "2.4 GB" to "+18%"),
        Triple(RefIconKind.FILM, "AVERAGE LENGTH", "2:10" to "+12%"),
        Triple(RefIconKind.STAR, "FAVORITES", "8 clips" to "+100%"),
    ),
    days = listOf("Mon" to "12 Aug", "Tue" to "13 Aug", "Wed" to "14 Aug", "Thu" to "15 Aug", "Fri" to "16 Aug", "Sat" to "17 Aug", "Sun" to "18 Aug"),
    series = mapOf(
        "clips" to listOf(3f, 5f, 8f, 6f, 4f, 7f, 9f),
        "minutes" to listOf(6f, 11f, 17f, 13f, 9f, 15f, 19f),
        "storage" to listOf(.3f, .5f, .8f, .6f, .4f, .7f, .9f),
    ),
    usage = Triple(75, 15, 10),
    storageLabel = "2.4 GB of 64 GB used", storageFrac = .4f,
    rows = listOf("2.1 GB", "0.3 GB", "0.0 GB")
)

/** Statistik ur de egna videorna; exempelvärdena visas tills man spelat in. */
private fun realStats(items: List<MediaItem>): Stats {
    val videos = items.filter { !it.isPhoto }
    val photos = items.filter { it.isPhoto }
    val totalSec = videos.sumOf { it.durationMs } / 1000
    val avg = if (videos.isEmpty()) 0L else totalSec / videos.size
    val videoBytes = videos.sumOf { it.sizeBytes }
    val photoBytes = photos.sumOf { it.sizeBytes }
    val dayFmt = SimpleDateFormat("EEE", Locale.ENGLISH)
    val dateFmt = SimpleDateFormat("d MMM", Locale.ENGLISH)
    val days = mutableListOf<Pair<String, String>>()
    val clips = mutableListOf<Float>()
    val minutes = mutableListOf<Float>()
    val storage = mutableListOf<Float>()
    for (i in 6 downTo 0) {
        val from = dayStart(i)
        val to = from + 86_400
        val same = videos.filter { it.addedAt in from until to }
        days += dayFmt.format(Date(from * 1000)) to dateFmt.format(Date(from * 1000))
        clips += same.size.toFloat()
        minutes += (same.sumOf { it.durationMs } / 60_000f).roundToInt().toFloat()
        storage += ((same.sumOf { it.sizeBytes } / (1024f * 1024f * 1024f)) * 10).roundToInt() / 10f
    }
    val n = videos.size.coerceAtLeast(1)
    val both = videos.count { it.name.contains("_combined_") }
    val front = videos.count { it.name.contains("_front_") }
    val gb = 1024.0 * 1024 * 1024
    return Stats(
        total = videos.size, totalDelta = "",
        cards = listOf(
            Triple(RefIconKind.CAMERA, "RECORDING TIME", "${totalSec / 60} min" to ""),
            Triple(RefIconKind.DATABASE, "STORAGE USED", formatSize(videoBytes) to ""),
            Triple(RefIconKind.FILM, "AVERAGE LENGTH", "${avg / 60}:${"%02d".format(avg % 60)}" to ""),
            Triple(RefIconKind.STAR, "FAVORITES", "0 clips" to ""),
        ),
        days = days,
        series = mapOf("clips" to clips, "minutes" to minutes, "storage" to storage),
        usage = Triple(both * 100 / n, front * 100 / n, (videos.size - both - front) * 100 / n),
        storageLabel = "${formatSize(videoBytes + photoBytes)} recorded",
        storageFrac = ((videoBytes + photoBytes) / gb).toFloat().coerceIn(0f, 1f),
        rows = listOf(formatSize(videoBytes), formatSize(photoBytes), "0 MB")
    )
}

/** Stats enligt design/reference/stats.png. */
@Composable
fun StatsScreen(library: List<MediaItem>, isPremium: Boolean, onSoon: (String) -> Unit, onOpenPremium: () -> Unit) {
    val s = if (library.any { !it.isPhoto }) realStats(library) else DemoStats
    var metric by remember { mutableStateOf("clips") }
    val values = s.series.getValue(metric)
    val maxV = values.maxOrNull() ?: 0f
    val top = if (maxV <= 10f) 10f else ceil(maxV / 5f) * 5f

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(rp(184))) {
            Brand(55, 7, 49)
            RText("Stats", 55, 71.5, 54, 60, weight = FontWeight.Bold, ls = -.5)
            RText("Your DualShots recording history", 57, 133, 25, 30, color = RefSub)
            Row(
                Modifier.absoluteOffset(rp(680), rp(75)).size(rp(293), rp(63)).clip(RoundedCornerShape(rp(32)))
                    .background(Color(0xFF121826)).border(rp(2), Color(0xFF252D3F), RoundedCornerShape(rp(32)))
                    .clickable { onSoon("Date range") }.padding(start = rp(24), end = rp(26)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rp(16))
            ) {
                RefIcon(RefIconKind.CALENDAR, RefSoft, rp(30))
                Text("Last 7 days", color = Color(0xFFE3E8F0), fontSize = rs(22), modifier = Modifier.weight(1f))
                RefIcon(RefIconKind.CHEV_DOWN, RefSoft, rp(26), stroke = 2.4f)
            }
        }

        // Totalt antal klipp
        Box(
            Modifier.padding(start = rp(50), end = rp(49)).fillMaxWidth().height(rp(196)).clip(RoundedCornerShape(rp(26)))
                .background(Brush.horizontalGradient(listOf(Color(0xFF2A170D), Color(0xFF1D140F), Color(0xFF26160D))))
                .border(rp(2), Color(0xFF5A2C12), RoundedCornerShape(rp(26)))
        ) {
            Canvas(Modifier.absoluteOffset(rp(423), rp(78)).size(rp(500), rp(100))) {
                val sx = size.width / 500f
                val sy = size.height / 100f
                val p = Path().apply {
                    moveTo(0f, 92f * sy)
                    cubicTo(40f * sx, 90f * sy, 70f * sx, 72f * sy, 110f * sx, 78f * sy)
                    cubicTo(150f * sx, 84f * sy, 160f * sx, 20f * sy, 210f * sx, 22f * sy)
                    cubicTo(250f * sx, 24f * sy, 255f * sx, 80f * sy, 300f * sx, 80f * sy)
                    cubicTo(345f * sx, 80f * sy, 350f * sx, 52f * sy, 385f * sx, 52f * sy)
                    cubicTo(420f * sx, 52f * sy, 430f * sx, 84f * sy, 460f * sx, 60f * sy)
                    cubicTo(480f * sx, 44f * sy, 490f * sx, 22f * sy, 500f * sx, 22f * sy)
                }
                val brush = Brush.horizontalGradient(0f to Color.Transparent, .25f to RefOrange)
                drawPath(p, brush, style = Stroke(width = 10f * sx, cap = StrokeCap.Round), alpha = .25f)
                drawPath(p, brush, style = Stroke(width = 4f * sx, cap = StrokeCap.Round))
            }
            Box(
                Modifier.absoluteOffset(rp(29), rp(37)).size(rp(93), rp(92)).clip(RoundedCornerShape(rp(20)))
                    .background(Brush.linearGradient(listOf(Color(0xFFFF7A1A), Color(0xFFFF5A00)))),
                contentAlignment = Alignment.Center
            ) { RefIcon(RefIconKind.TREND, Color(0xFF1C0D05), rp(52), stroke = 2.4f) }
            RText("TOTAL RECORDINGS", 153, 38, 18, 24, color = RefLabel, mono = true, ls = 2)
            RText("${s.total} clips", 153, 64, 54, 60, weight = FontWeight.Bold)
            Row(Modifier.absoluteOffset(rp(153), rp(136)), verticalAlignment = Alignment.CenterVertically) {
                if (s.totalDelta.isNotEmpty()) {
                    RefIcon(RefIconKind.ARROW_UP_RIGHT, RefGreen, rp(26), stroke = 2.6f)
                    Spacer(Modifier.width(rp(8)))
                    Text(s.totalDelta, color = RefGreen, fontSize = rs(22), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(rp(14)))
                    Text("vs. last week", color = RefSub, fontSize = rs(22))
                } else {
                    Text("Last 7 days", color = RefSub, fontSize = rs(22))
                }
            }
            RText(
                buildAnnotatedString {
                    append("RECORD BOTH SIDES.\n")
                    withStyle(SpanStyle(color = RefOrange)) { append("AT ONCE.") }
                },
                23, 22, 16, 24, color = RefLabel, mono = true, ls = 2.4, right = true
            )
        }

        // Fyra nyckeltal
        Row(
            Modifier.padding(start = rp(50), end = rp(49), top = rp(21)).fillMaxWidth().height(rp(192)),
            horizontalArrangement = Arrangement.spacedBy(rp(16))
        ) {
            s.cards.forEach { (icon, label, value) ->
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(rp(22))).background(RefCard).border(rp(2), RefCardBorder, RoundedCornerShape(rp(22)))) {
                    RefIcon(icon, RefOrange, rp(38), Modifier.absoluteOffset(rp(26), rp(21)))
                    RText(label, 26, 72, 15, 20, color = RefLabel, mono = true, ls = 2.1)
                    RText(value.first, 26, 98, 27, 34, weight = FontWeight.Bold)
                    Row(Modifier.absoluteOffset(rp(26), rp(146)), verticalAlignment = Alignment.CenterVertically) {
                        if (value.second.isNotEmpty()) {
                            RefIcon(RefIconKind.ARROW_UP_RIGHT, RefGreen, rp(22), stroke = 2.6f)
                            Spacer(Modifier.width(rp(8)))
                            Text(value.second, color = RefGreen, fontSize = rs(19), fontWeight = FontWeight.SemiBold)
                        } else Text("–", color = RefMuted, fontSize = rs(19))
                    }
                }
            }
        }

        // Inspelningar per dag
        Box(
            Modifier.padding(start = rp(50), end = rp(49), top = rp(18)).fillMaxWidth().height(rp(314))
                .clip(RoundedCornerShape(rp(22))).background(RefCard).border(rp(2), RefCardBorder, RoundedCornerShape(rp(22)))
        ) {
            RText("RECORDINGS BY DAY", 28, 22, 20, 26, color = RefLabel, mono = true, ls = 2.6)
            Row(Modifier.absoluteOffset(rp(556), rp(15)), horizontalArrangement = Arrangement.spacedBy(rp(10))) {
                listOf(Triple("clips", "Clips", 96), Triple("minutes", "Minutes", 115), Triple("storage", "Storage", 116)).forEach { (k, label, w) ->
                    val active = metric == k
                    Box(
                        Modifier.size(rp(w), rp(45)).clip(RoundedCornerShape(rp(23)))
                            .background(if (active) Brush.horizontalGradient(listOf(Color(0xFFFF5A0A), Color(0xFFFF8A1C))) else Brush.horizontalGradient(listOf(Color(0xFF1B2232), Color(0xFF1B2232))))
                            .clickable { metric = k },
                        contentAlignment = Alignment.Center
                    ) { Text(label, color = if (active) Color.White else RefSoft, fontSize = rs(20), fontWeight = FontWeight.Medium) }
                }
            }
            // Axel, rutnät och staplar (staplarnas mitt följer referensen).
            for (i in 0..5) {
                val v = top * (5 - i) / 5f
                val label = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)
                Text(
                    label, color = RefMuted, fontSize = rs(17), lineHeight = rs(20), textAlign = TextAlign.End,
                    modifier = Modifier.absoluteOffset(rp(0), rp(89 + i * 30.4 - 10)).width(rp(45))
                )
                Box(Modifier.absoluteOffset(rp(60), rp(89 + i * 30.4)).padding(end = rp(85)).fillMaxWidth().height(rp(1)).background(Color(0xFF1B2232)))
            }
            values.forEachIndexed { i, v ->
                val center = 109 + i * 123.8
                val h = if (top > 0f) v / top * 152f else 0f
                Box(
                    Modifier.absoluteOffset(rp(center - 31), rp(89 + 152 - h)).size(rp(62), rp(h))
                        .clip(RoundedCornerShape(topStart = rp(5), topEnd = rp(5)))
                        .background(Brush.verticalGradient(listOf(Color(0xFFFF5A0A), Color(0xFFFF9A2A))))
                )
                val label = if (v % 1f == 0f) v.toInt().toString() else v.toString()
                Text(
                    label, color = Color.White, fontSize = rs(19), lineHeight = rs(24), textAlign = TextAlign.Center,
                    modifier = Modifier.absoluteOffset(rp(center - 40), rp(89 + 152 - h - 28)).width(rp(80))
                )
                Column(
                    Modifier.absoluteOffset(rp(center - 60), rp(89 + 161)).width(rp(120)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.days[i].first, color = RefSoft, fontSize = rs(19), lineHeight = rs(24))
                    Text(s.days[i].second, color = RefFaint, fontSize = rs(17), lineHeight = rs(24))
                }
            }
        }

        // Kameraanvändning och lagring
        Row(
            Modifier.padding(start = rp(50), end = rp(49), top = rp(16)).fillMaxWidth().height(rp(245)),
            horizontalArrangement = Arrangement.spacedBy(rp(16))
        ) {
            val (both, front, back) = s.usage
            Box(Modifier.weight(454f).fillMaxHeight().clip(RoundedCornerShape(rp(22))).background(RefCard).border(rp(2), RefCardBorder, RoundedCornerShape(rp(22)))) {
                RText("Camera Usage", 27, 18, 22, 28, weight = FontWeight.Medium)
                Box(Modifier.absoluteOffset(rp(29), rp(66)).size(rp(156)), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = size.width * 20f / 156f
                        val inset = stroke / 2
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        val tl = Offset(inset, inset)
                        val b = both * 3.6f
                        val f = front * 3.6f
                        drawArc(Color(0xFF2A3245), -90f, 360f, false, tl, arcSize, style = Stroke(stroke))
                        if (b > 0) drawArc(Brush.sweepGradient(listOf(Color(0xFFFF4D0A), Color(0xFFFF8A1C), Color(0xFFFF4D0A))), -90f, b, false, tl, arcSize, style = Stroke(stroke))
                        if (f > 0) drawArc(Color(0xFFFFA53A), -90f + b, f, false, tl, arcSize, style = Stroke(stroke))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${s.total}", color = Color.White, fontSize = rs(30), lineHeight = rs(34), fontWeight = FontWeight.Bold)
                        Text("clips", color = RefSub, fontSize = rs(18), lineHeight = rs(22))
                    }
                }
                Column(Modifier.absoluteOffset(rp(219), rp(66)).width(rp(207))) {
                    LegendRow(RefOrange, "Both cameras", "$both%", 48.5f)
                    LegendRow(RefGold, "Front camera", "$front%", 48.5f)
                    LegendRow(Color(0xFF3A4256), "Back camera", "$back%", 48.5f)
                }
            }
            Box(Modifier.weight(455f).fillMaxHeight().clip(RoundedCornerShape(rp(22))).background(RefCard).border(rp(2), RefCardBorder, RoundedCornerShape(rp(22)))) {
                RText("Storage Overview", 27, 18, 22, 28, weight = FontWeight.Medium)
                RText(s.storageLabel, 21, 45, 17, 24, color = RefSub, right = true)
                Box(Modifier.absoluteOffset(rp(27), rp(79)).size(rp(404), rp(19)).clip(RoundedCornerShape(rp(10))).background(Color(0xFF262D3D))) {
                    if (s.storageFrac > 0f) Box(
                        Modifier.fillMaxHeight().fillMaxWidth(s.storageFrac).clip(RoundedCornerShape(rp(10)))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFF5200), Color(0xFFFFA31A))))
                    )
                }
                Column(Modifier.absoluteOffset(rp(27), rp(119)).padding(end = rp(48)).fillMaxWidth()) {
                    LegendRow(RefOrange, "Videos", s.rows[0], 37.5f)
                    LegendRow(Color(0xFF4B7BE0), "Photos", s.rows[1], 37.5f)
                    LegendRow(Color(0xFF3A4256), "Other", s.rows[2], 37.5f)
                }
            }
        }

        // Premium
        Box(
            Modifier.padding(start = rp(50), end = rp(49), top = rp(19)).fillMaxWidth().height(rp(107))
                .shadow(rp(20), RoundedCornerShape(rp(22)), spotColor = RefOrange, ambientColor = RefOrange)
                .clip(RoundedCornerShape(rp(22)))
                .background(Brush.horizontalGradient(listOf(Color(0xFF24140C), Color(0xFF1A110D))))
                .border(rp(3), RefOrange, RoundedCornerShape(rp(22)))
        ) {
            Box(
                Modifier.absoluteOffset(rp(26), rp(19)).size(rp(63), rp(64)).clip(RoundedCornerShape(rp(14))).background(Color(0xFF2E1C11)),
                contentAlignment = Alignment.Center
            ) { RefIcon(RefIconKind.CROWN, RefCrown, rp(42), stroke = 2.2f) }
            RText("Unlock deeper insights", 116, 22, 24, 30, color = RefGold, weight = FontWeight.Bold)
            RText("Get advanced stats, export data and more.", 116, 57, 19, 24, color = RefSoft)
            if (isPremium) {
                RText("Premium active", 40, 36, 22, 30, color = RefGold, right = true)
            } else {
                Row(
                    Modifier.absoluteOffset(rp(671), rp(15)).size(rp(230), rp(66)).clip(RoundedCornerShape(rp(22)))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFF7A00), Color(0xFFFFA01E))))
                        .clickable(onClick = onOpenPremium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rp(22), Alignment.CenterHorizontally)
                ) {
                    Text("GO PREMIUM", color = Color(0xFF1C0D05), fontSize = rs(21), fontWeight = FontWeight.Bold, letterSpacing = rs(.5))
                    RefIcon(RefIconKind.CHEVRON, Color(0xFF1C0D05), rp(24), stroke = 2.8f)
                }
            }
        }
        Spacer(Modifier.height(rp(40)))
    }
}

@Composable
private fun LegendRow(dot: Color, label: String, value: String, height: Float) {
    Row(Modifier.fillMaxWidth().height(rp(height)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(rp(17)).clip(CircleShape).background(dot))
        Spacer(Modifier.width(rp(13)))
        Text(label, color = RefSoft, fontSize = rs(19), modifier = Modifier.weight(1f))
        Text(value, color = RefSoft, fontSize = rs(19))
    }
}
