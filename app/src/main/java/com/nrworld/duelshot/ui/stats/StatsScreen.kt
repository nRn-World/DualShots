package com.nrworld.duelshot.ui.stats

import android.content.Context
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nrworld.duelshot.ui.components.DuelIcon
import com.nrworld.duelshot.ui.components.DuelIconKind
import com.nrworld.duelshot.ui.components.ScreenHeader
import com.nrworld.duelshot.ui.theme.DuelBackground
import com.nrworld.duelshot.ui.theme.DuelGradient
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelLineStrong
import com.nrworld.duelshot.ui.theme.DuelOrange
import com.nrworld.duelshot.ui.theme.DuelOrangeLight
import com.nrworld.duelshot.ui.theme.DuelSurface
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextFaint
import com.nrworld.duelshot.ui.theme.DuelTextMuted

private data class StatsData(
    val count: Int,
    val totalMs: Long,
    val totalMb: Long,
    val byHour: IntArray
)

@Composable
fun StatsScreen() {
    val context = LocalContext.current
    var data by remember { mutableStateOf<StatsData?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        data = queryStats(context)
        loading = false
    }

    DuelBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            ScreenHeader("Stats", "Your DualShots recording history")
            Spacer(Modifier.height(16.dp))

            if (loading) {
                Text("Loading…", color = DuelTextMuted)
            } else {
                val d = data ?: return@Column
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroCard(d)
                    Spacer(Modifier.height(4.dp))
                    SavingsCard(d.totalMb)
                    Spacer(Modifier.height(4.dp))
                    HourDistributionCard(d.byHour)
                }
            }
        }
    }
}

@Composable
private fun HeroCard(d: StatsData) {
    val minutes = d.totalMs / 60000
    val gb = d.totalMb / 1024f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(DuelSurface2)
            .border(1.dp, DuelLineStrong, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DuelGradient),
                contentAlignment = Alignment.Center
            ) { DuelIcon(DuelIconKind.STATS, Color(0xFF04060D), 20.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("ALL-TIME RECORDINGS", color = DuelTextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.8.sp)
                Text(
                    "${d.count} ${if (d.count == 1) "clip" else "clips"}",
                    color = DuelText, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat("Duration", formatDuration(d.totalMs))
            MiniStat("Size", "${if (gb >= 1) gb.toString().take(4) else d.totalMb} ${if (gb >= 1) "GB" else "MB"}")
        }
    }
}

@Composable
private fun SavingsCard(totalMb: Long) {
    // Dual View kombinerar två kameror till EN fil – användaren sparar ca hälften av
    // vad separata dubbelinspelningar skulle ha kastat.
    val savedGb = totalMb / 1024f * 0.5
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(DuelOrange.copy(alpha = 0.18f), Color.Transparent)))
            .border(1.dp, DuelOrange.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DuelIcon(DuelIconKind.GEM, DuelOrangeLight, 18.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Storage saved", color = DuelOrangeLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Dual View put both cameras in one file instead of two — you've saved about ${savedGb.toString().take(4)} GB of separate dual recordings.",
                    color = DuelTextMuted, fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun HourDistributionCard(byHour: IntArray) {
    val max = byHour.maxOrNull() ?: 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DuelSurface2)
            .border(1.dp, DuelLineStrong, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text("RECORDINGS BY TIME OF DAY", color = DuelTextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            byHour.forEachIndexed { h, c ->
                val frac = if (max > 0) c.toFloat() / max else 0f
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        if (c > 0) c.toString() else "",
                        color = DuelOrangeLight, fontSize = 9.sp, fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(DuelGradient)
                            .padding(vertical = 0.dp),
                        contentAlignment = Alignment.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((frac * 46f).dp)
                        )
                    }
                    Text(
                        if (h % 3 == 0) "$h" else "",
                        color = DuelTextFaint, fontSize = 9.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val peak = byHour.indices.maxByOrNull { byHour[it] }
        Text(
            if (peak != null && byHour[peak] > 0)
                "Most active around ${peak}:00"
            else "Record something to see your pattern",
            color = DuelTextMuted, fontSize = 12.sp
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(DuelSurface)
            .border(1.dp, DuelLine, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(label.uppercase(), color = DuelTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.6.sp)
        Text(value, color = DuelText, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0m"
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "$hh $mm" else "$m min"
}

private fun queryStats(context: Context): StatsData {
    var count = 0
    var totalMs = 0L
    var totalBytes = 0L
    val byHour = IntArray(24)
    val proj = arrayOf(
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.DATE_ADDED
    )
    val sel = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
    val args = arrayOf("%/DualShots%", "%/DuelShots%")
    runCatching {
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, sel, args, null
        )?.use { c ->
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (c.moveToNext()) {
                count++
                totalBytes += c.getLong(sizeCol)
                val dur = c.getLong(durCol)
                totalMs += dur
                val addedSec = c.getLong(dateCol)
                byHour[((addedSec / 3600) % 24).toInt().coerceIn(0, 23)]++
            }
        }
    }
    return StatsData(count, totalMs, totalBytes / (1024 * 1024), byHour)
}
