package com.nrworld.duelshot.ui.gallery

import android.graphics.Bitmap
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.nrworld.duelshot.R
import com.nrworld.duelshot.ui.components.Brand
import com.nrworld.duelshot.ui.components.DuelIcon
import com.nrworld.duelshot.ui.components.DuelIconKind
import com.nrworld.duelshot.ui.components.RCircle
import com.nrworld.duelshot.ui.components.RText
import com.nrworld.duelshot.ui.components.RefCard
import com.nrworld.duelshot.ui.components.RefCardBorder
import com.nrworld.duelshot.ui.components.RefFaint
import com.nrworld.duelshot.ui.components.RefIcon
import com.nrworld.duelshot.ui.components.RefIconKind
import com.nrworld.duelshot.ui.components.RefMuted
import com.nrworld.duelshot.ui.components.RefOrange
import com.nrworld.duelshot.ui.components.RefSoft
import com.nrworld.duelshot.ui.components.RefSub
import com.nrworld.duelshot.ui.components.SeeAll
import com.nrworld.duelshot.ui.components.rp
import com.nrworld.duelshot.ui.components.rs
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelOrange
import com.nrworld.duelshot.ui.theme.DuelOrangeLight
import com.nrworld.duelshot.ui.theme.DuelSurface
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextMuted

private enum class Filter(val label: String, val icon: RefIconKind, val width: Int) {
    ALL("All", RefIconKind.GRID4, 146),
    VIDEOS("Videos", RefIconKind.VIDEO, 177),
    PHOTOS("Photos", RefIconKind.PHOTO, 177),
    FAVORITES("Favorites", RefIconKind.HEART, 198),
    DELETED("Deleted", RefIconKind.TRASH, 172)
}

/** Gallery enligt design/reference/gallery.png. */
@Composable
fun GalleryScreen(
    library: List<MediaItem>,
    onOpenEntry: (LibraryEntry) -> Unit,
    onSoon: (String) -> Unit,
    onOpenAbout: () -> Unit
) {
    var filter by remember { mutableStateOf(Filter.ALL) }
    val demo = library.isEmpty()
    val all = if (demo) DemoEntries else library.map { it.toEntry() }
    val entries = when (filter) {
        Filter.ALL -> all
        Filter.VIDEOS -> all.filter { it.item?.isPhoto != true }
        Filter.PHOTOS -> all.filter { it.item?.isPhoto == true }
        else -> emptyList()
    }
    val videoBytes = library.filter { !it.isPhoto }.sumOf { it.sizeBytes }
    val photoBytes = library.filter { it.isPhoto }.sumOf { it.sizeBytes }
    val gb = 1024.0 * 1024 * 1024

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(rp(132))) {
            Brand(50, 18, 53)
            RText("YOUR MOMENTS. TWO PERSPECTIVES.", 52, 76, 18, 24, color = RefFaint, mono = true, ls = 3.8)
            RCircle(RefIconKind.SEARCH, 809, 20, 70, 36) { onSoon("Search") }
            RCircle(RefIconKind.DOTS, 907, 20, 70, 36, onClick = onOpenAbout)
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = rp(50), end = rp(20)).height(rp(66)),
            horizontalArrangement = Arrangement.spacedBy(rp(14.5))
        ) {
            Filter.values().forEach { f ->
                val active = f == filter
                Row(
                    Modifier.width(rp(f.width)).fillMaxHeight().clip(RoundedCornerShape(rp(24)))
                        .background(if (active) Color(0xFF1C130F) else Color(0xFF121826))
                        .border(rp(if (active) 3 else 2), if (active) RefOrange else Color(0xFF232B3C), RoundedCornerShape(rp(24)))
                        .clickable { filter = f }
                        .padding(start = rp(33)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rp(20))
                ) {
                    RefIcon(f.icon, if (active) RefOrange else RefSoft, rp(34))
                    Text(f.label, color = if (active) RefOrange else Color(0xFFD4DAE6), fontSize = rs(21), fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(rp(25)))
        Box(
            Modifier.padding(horizontal = rp(47)).fillMaxWidth().height(rp(106)).clip(RoundedCornerShape(rp(22)))
                .background(Brush.horizontalGradient(listOf(Color(0xFF20140E), Color(0xFF15110F), Color(0xFF1A120E))))
                .border(rp(2), Color(0xFF4A2612), RoundedCornerShape(rp(22)))
        ) {
            Box(
                Modifier.absoluteOffset(rp(21), rp(18)).size(rp(98), rp(67)).clip(RoundedCornerShape(rp(16))).background(Color(0xFF2A1B12)),
                contentAlignment = Alignment.Center
            ) { RefIcon(RefIconKind.CLOUD, RefOrange, rp(58)) }
            RText("Back up your recordings", 149, 25, 25, 32, weight = FontWeight.Medium)
            RText("Keep your memories safe in the cloud.", 149, 59, 19, 24, color = RefSub)
            OutlineButton("Enable Backup", 665, 22, 244, 59, RefOrange, 3) { onSoon("Cloud backup") }
        }

        if (entries.isNotEmpty()) {
            SectionHead("Recent", top = 25) { onSoon("See all") }
            Column(
                Modifier.padding(start = rp(50), end = rp(49), top = rp(17)),
                verticalArrangement = Arrangement.spacedBy(rp(17))
            ) {
                entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(rp(20))) {
                        row.forEach { e -> GalleryCard(e, Modifier.weight(1f)) { onOpenEntry(e) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            SectionHead("Folders", top = 35) { onSoon("Folders") }
            val videos = library.count { !it.isPhoto }
            Row(
                Modifier.padding(start = rp(49), end = rp(48), top = rp(16)).fillMaxWidth().height(rp(111)),
                horizontalArrangement = Arrangement.spacedBy(rp(12))
            ) {
                Folder("All Videos", if (demo) 86 else videos, null, Color.Transparent, R.drawable.ref_folder_all) { onSoon("Folders") }
                Folder("Favorites", if (demo) 12 else 0, RefIconKind.HEART, Color(0xFF2B1B13)) { onSoon("Folders") }
                Folder("Trips", if (demo) 24 else 0, RefIconKind.MOUNTAIN, Color(0xFF1A2131)) { onSoon("Folders") }
                Folder("Events", if (demo) 8 else 0, RefIconKind.CALENDAR, Color(0xFF1A2131)) { onSoon("Folders") }
            }

            Box(Modifier.padding(start = rp(50), end = rp(49), top = rp(36)).fillMaxWidth().height(rp(110))) {
                RText("Storage", 1, 0, 30, 36, weight = FontWeight.Medium)
                RText(
                    if (demo) "12.4 GB of 64 GB used" else "${formatSize(videoBytes + photoBytes)} recorded",
                    2, 6, 18, 24, color = RefSub, right = true
                )
                val vFrac = if (demo) .175f else (videoBytes / gb).toFloat().coerceIn(0f, 1f)
                val pFrac = if (demo) .019f else (photoBytes / gb).toFloat().coerceIn(0f, 1f)
                Row(
                    Modifier.absoluteOffset(rp(2), rp(48)).size(rp(676), rp(16)).clip(RoundedCornerShape(rp(8))).background(Color(0xFF283044))
                ) {
                    if (vFrac > 0f) Box(
                        Modifier.fillMaxHeight().fillMaxWidth(vFrac).clip(RoundedCornerShape(rp(8)))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFF5A00), Color(0xFFFFA31A))))
                    )
                    if (pFrac > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(pFrac / (1f - vFrac).coerceAtLeast(.01f)).background(Color(0xFF4A5468)))
                }
                Row(Modifier.absoluteOffset(rp(2), rp(84)), horizontalArrangement = Arrangement.spacedBy(rp(28))) {
                    Legend(RefOrange, if (demo) "Videos (11.2 GB)" else "Videos (${formatSize(videoBytes)})")
                    Legend(Color(0xFF6B7488), if (demo) "Photos (1.2 GB)" else "Photos (${formatSize(photoBytes)})")
                    Legend(Color(0xFF2F3646), if (demo) "Other (0 GB)" else "Other (0 MB)")
                }
                OutlineButton("Manage Storage", 709, 45, 216, 57, Color(0xFFE3E8F0), 2, border = Color(0xFF39425A), fontSize = 19, pad = 18) { onSoon("Storage management") }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(top = rp(90)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(rp(14))
            ) {
                RefIcon(filter.icon, Color(0xFF5B6478), rp(70))
                Text("No ${filter.label.lowercase()} yet", color = Color.White, fontSize = rs(30), fontWeight = FontWeight.Medium)
                Text("Nothing to show here yet.", color = RefMuted, fontSize = rs(22))
            }
        }
        Spacer(Modifier.height(rp(40)))
    }
}

@Composable
private fun SectionHead(title: String, top: Int, onSeeAll: () -> Unit) {
    Row(
        Modifier.padding(start = rp(50), end = rp(52), top = rp(top)).fillMaxWidth().height(rp(36)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = rs(30), lineHeight = rs(36), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        SeeAll(onSeeAll)
    }
}

@Composable
private fun GalleryCard(e: LibraryEntry, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(rp(248)).clip(RoundedCornerShape(rp(20))).background(RefCard)
            .border(rp(2), RefCardBorder, RoundedCornerShape(rp(20))).clickable(onClick = onClick)
    ) {
        EntryThumb(
            e,
            Modifier.absoluteOffset(rp(3), rp(3)).padding(end = rp(6)).fillMaxWidth().height(rp(173))
                .clip(RoundedCornerShape(topStart = rp(18), topEnd = rp(18), bottomStart = rp(4), bottomEnd = rp(4)))
        )
        RText(e.title, 15, 183, 21, 26, weight = FontWeight.Medium, maxWidth = 230)
        RText(e.date, 15, 211, 19, 24, color = RefMuted, maxWidth = 250)
        RefIcon(RefIconKind.DOTS, RefSub, rp(28), Modifier.align(Alignment.TopEnd).absoluteOffset(x = -rp(12), y = rp(197)))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Folder(
    name: String, count: Int, icon: RefIconKind?, tile: Color, image: Int? = null, onClick: () -> Unit
) {
    Box(
        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(rp(18))).background(RefCard)
            .border(rp(2), RefCardBorder, RoundedCornerShape(rp(18))).clickable(onClick = onClick)
    ) {
        Box(
            Modifier.absoluteOffset(rp(15), rp(21)).size(rp(64), rp(66)).clip(RoundedCornerShape(rp(12))).background(tile),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) Image(painterResource(image), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else if (icon != null) RefIcon(icon, RefOrange, rp(42))
        }
        RText(name, 95, 32, 19, 24, weight = FontWeight.Medium)
        RText("$count items", 95, 59, 18, 22, color = RefMuted)
        RefIcon(RefIconKind.CHEVRON, RefMuted, rp(26), Modifier.align(Alignment.TopEnd).absoluteOffset(x = -rp(16), y = rp(48)), stroke = 2f)
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(rp(17)).clip(CircleShape).background(color))
        Spacer(Modifier.width(rp(11)))
        Text(text, color = RefMuted, fontSize = rs(18), lineHeight = rs(22))
    }
}

/** Kantlinjeknapp med text och pil, placerad i referenspixlar. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.OutlineButton(
    label: String, x: Number, top: Number, w: Number, h: Number, color: Color, borderPx: Number,
    border: Color = color, fontSize: Number = 21, pad: Number = 26, onClick: () -> Unit
) {
    Row(
        Modifier.absoluteOffset(rp(x), rp(top)).size(rp(w), rp(h)).clip(RoundedCornerShape(rp(18)))
            .border(rp(borderPx), border, RoundedCornerShape(rp(18))).clickable(onClick = onClick)
            .padding(horizontal = rp(pad)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = color, fontSize = rs(fontSize), fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        RefIcon(RefIconKind.CHEVRON, color, rp(26), stroke = 2.4f)
    }
}

/** Miniatyr: exempelbild ur referensen eller riktig miniatyr + längd/PHOTO. */
@Composable
fun EntryThumb(e: LibraryEntry, modifier: Modifier) {
    val context = LocalContext.current
    Box(modifier.background(Color(0xFF0B0F18))) {
        if (e.demoImage != null) {
            // Exempelbilderna har redan längden inritad.
            Image(painterResource(e.demoImage), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (e.item != null) {
            var bmp by remember(e.key) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(e.key) {
                bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadThumb(context, e.item) }
            }
            bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Text(
                e.badge, color = Color.White, fontSize = rs(21), fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomEnd).padding(rp(8)).clip(RoundedCornerShape(rp(10)))
                    .background(Color(0xC70A0C12)).padding(horizontal = rp(10), vertical = rp(3))
            )
        }
    }
}

/** Spelare för video, bildvisare för foto – med dela/radera/stäng. */
@Composable
fun MediaDialog(item: MediaItem, onClose: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DuelSurface2)
                .border(1.dp, DuelLine, RoundedCornerShape(24.dp)).padding(16.dp)
        ) {
            Text(item.name, color = DuelText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            if (item.isPhoto) {
                var bmp by remember(item.id) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(item.id) {
                    bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadPhoto(context, item) }
                }
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
                    bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color.Black),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(item.uri)
                            setOnPreparedListener { mp -> mp.isLooping = true; start() }
                        }
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                formatSize(item.sizeBytes) + if (item.isPhoto) " · photo" else " · " + shortDuration(item.durationMs),
                color = DuelTextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill(DuelIconKind.SHARE, "Share", onShare)
                ActionPill(DuelIconKind.TRASH, "Delete", onDelete)
                ActionPill(DuelIconKind.CLOSE, "Close", onClose)
            }
        }
    }
}

@Composable
fun ConfirmDelete(item: MediaItem, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = DuelSurface2,
        title = { Text(if (item.isPhoto) "Delete photo?" else "Delete clip?", color = DuelText, fontWeight = FontWeight.Bold) },
        text = { Text("${item.name} is removed from the DualShots folder.", color = DuelTextMuted, fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = DuelOrange) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel", color = DuelTextMuted) } }
    )
}

@Composable
private fun ActionPill(kind: DuelIconKind, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(DuelSurface).border(1.dp, DuelLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DuelIcon(kind, DuelOrangeLight, 15.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = DuelText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
