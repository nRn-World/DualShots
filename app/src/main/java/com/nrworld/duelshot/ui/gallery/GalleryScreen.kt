package com.nrworld.duelshot.ui.gallery

import android.graphics.Bitmap
import android.widget.MediaController
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.nrworld.duelshot.ui.components.rv
import com.nrworld.duelshot.ui.components.rs
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelOrange
import com.nrworld.duelshot.ui.theme.DuelOrangeLight
import com.nrworld.duelshot.ui.theme.DuelSurface
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextMuted

private enum class Filter(val label: String, val icon: RefIconKind) {
    ALL("All", RefIconKind.GRID4),
    VIDEOS("Videos", RefIconKind.VIDEO),
    PHOTOS("Photos", RefIconKind.PHOTO),
    FAVORITES("Favorites", RefIconKind.HEART)
}

/** Galleri med riktiga filter, sök, favoriter och ren tom-vy. */
@Composable
fun GalleryScreen(
    library: List<MediaItem>,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onOpenEntry: (LibraryEntry) -> Unit,
    onStartRecording: () -> Unit,
    onOpenAbout: () -> Unit
) {
    var filter by remember { mutableStateOf(Filter.ALL) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val demo = library.isEmpty()
    val all = if (demo) DemoEntries else library.map { it.toEntry() }
    val entries = all.filter { e ->
        val matchesFilter = when (filter) {
            Filter.ALL -> true
            Filter.VIDEOS -> e.item?.isPhoto != true
            Filter.PHOTOS -> e.item?.isPhoto == true
            Filter.FAVORITES -> e.key in favorites
        }
        val q = query.trim()
        matchesFilter && (q.isEmpty() || e.title.contains(q, ignoreCase = true))
    }
    val videoBytes = library.filter { !it.isPhoto }.sumOf { it.sizeBytes }
    val photoBytes = library.filter { it.isPhoto }.sumOf { it.sizeBytes }
    val gb = 1024.0 * 1024 * 1024

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(rv(132))) {
            Brand(50, 18, 53)
            RText("YOUR MOMENTS. TWO PERSPECTIVES.", 52, 76, 18, 24, color = RefFaint, mono = true, ls = 3.8)
            RCircle(RefIconKind.SEARCH, 809, 20, 70, 36) {
                searching = !searching
                if (!searching) query = ""
            }
            RCircle(RefIconKind.DOTS, 907, 20, 70, 36, onClick = onOpenAbout)
        }

        if (searching) {
            Row(
                Modifier.padding(start = rp(50), end = rp(50), bottom = rv(16)).fillMaxWidth().height(rv(64))
                    .clip(RoundedCornerShape(rp(20))).background(Color(0xFF121826))
                    .border(rp(2), RefOrange, RoundedCornerShape(rp(20)))
                    .padding(horizontal = rp(22)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rp(14))
            ) {
                RefIcon(RefIconKind.SEARCH, RefOrange, rp(28))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search clips and photos…", color = RefMuted, fontSize = rs(19))
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = rs(19)),
                        cursorBrush = SolidColor(RefOrange),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Text(
                        "Clear", color = RefOrange, fontSize = rs(17), fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { query = "" }
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = rp(50), end = rp(20)).height(rv(66)),
            horizontalArrangement = Arrangement.spacedBy(rp(12))
        ) {
            Filter.values().forEach { f ->
                val active = f == filter
                Row(
                    Modifier.fillMaxHeight().clip(RoundedCornerShape(rp(24)))
                        .background(if (active) Color(0xFF1C130F) else Color(0xFF121826))
                        .border(rp(if (active) 3 else 2), if (active) RefOrange else Color(0xFF232B3C), RoundedCornerShape(rp(24)))
                        .clickable { filter = f }
                        .padding(start = rp(26), end = rp(30)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rp(14))
                ) {
                    RefIcon(f.icon, if (active) RefOrange else RefSoft, rp(32))
                    Text(f.label, color = if (active) RefOrange else Color(0xFFD4DAE6), fontSize = rs(19), fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                }
            }
        }

        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(rv(24)))
            Column(
                Modifier.padding(start = rp(50), end = rp(49)),
                verticalArrangement = Arrangement.spacedBy(rp(17))
            ) {
                entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(rp(20))) {
                        row.forEach { e ->
                            GalleryCard(
                                e = e,
                                isFavorite = e.key in favorites,
                                onToggleFavorite = { onToggleFavorite(e.key) },
                                modifier = Modifier.weight(1f)
                            ) { onOpenEntry(e) }
                        }
                        repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Box(Modifier.padding(start = rp(50), end = rp(49), top = rv(36)).fillMaxWidth().height(rv(125))) {
                RText("Storage", 1, 0, 30, 36, weight = FontWeight.Medium)
                RText("${formatSize(videoBytes + photoBytes)} recorded", 2, 6, 17, 24, color = RefSub, right = true)
                val vFrac = (videoBytes / gb).toFloat().coerceIn(0f, 1f)
                val pFrac = (photoBytes / gb).toFloat().coerceIn(0f, 1f)
                Row(
                    Modifier.absoluteOffset(rp(2), rv(52)).fillMaxWidth().height(rv(16)).clip(RoundedCornerShape(rp(8))).background(Color(0xFF283044))
                ) {
                    if (vFrac > 0f) Box(
                        Modifier.fillMaxHeight().fillMaxWidth(vFrac).clip(RoundedCornerShape(rp(8)))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFF5A00), Color(0xFFFFA31A))))
                    )
                    if (pFrac > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(pFrac / (1f - vFrac).coerceAtLeast(.01f)).background(Color(0xFF4A5468)))
                }
                Row(Modifier.absoluteOffset(rp(2), rv(86)), horizontalArrangement = Arrangement.spacedBy(rp(22))) {
                    Legend(RefOrange, "Videos (${formatSize(videoBytes)})")
                    Legend(Color(0xFF6B7488), "Photos (${formatSize(photoBytes)})")
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = rp(60), vertical = rv(90)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(rp(16))
            ) {
                RefIcon(filter.icon, Color(0xFF5B6478), rp(72))
                Text(
                    if (query.isNotBlank()) "No matches for \"$query\"" else "No ${filter.label.lowercase()} yet",
                    color = Color.White, fontSize = rs(28), fontWeight = FontWeight.Medium, textAlign = TextAlign.Center
                )
                Text(
                    if (library.isEmpty()) "Record your first dual-camera video or photo — it will show up right here."
                    else "Try another filter or record a new clip.",
                    color = RefMuted, fontSize = rs(20), textAlign = TextAlign.Center
                )
                if (library.isEmpty()) {
                    Spacer(Modifier.height(rv(8)))
                    Box(
                        Modifier.clip(RoundedCornerShape(rp(24)))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFF5A0A), Color(0xFFFF9A1C))))
                            .clickable(onClick = onStartRecording)
                            .padding(horizontal = rp(40), vertical = rv(18))
                    ) {
                        Text("Start Recording", color = Color(0xFF1C0D05), fontSize = rs(21), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(rv(40)))
    }
}

@Composable
private fun GalleryCard(e: LibraryEntry, isFavorite: Boolean, onToggleFavorite: () -> Unit, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(rp(20))).background(RefCard)
            .border(rp(2), RefCardBorder, RoundedCornerShape(rp(20))).clickable(onClick = onClick).padding(rp(3))
    ) {
        EntryThumb(
            e,
            Modifier.fillMaxWidth().aspectRatio(1.72f)
                .clip(RoundedCornerShape(topStart = rp(18), topEnd = rp(18), bottomStart = rp(4), bottomEnd = rp(4)))
        )
        Row(
            Modifier.fillMaxWidth().padding(start = rp(12), end = rp(10), top = rv(8), bottom = rv(10)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(e.title, color = Color.White, fontSize = rs(19), lineHeight = rs(26), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e.date, color = RefMuted, fontSize = rs(15), lineHeight = rs(22), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.clickable(onClick = onToggleFavorite).padding(rp(6))) {
                RefIcon(RefIconKind.HEART, if (isFavorite) RefOrange else RefSub, rp(28))
            }
        }
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
            Modifier.absoluteOffset(rp(15), rv(21)).size(rp(64), rv(66)).clip(RoundedCornerShape(rp(12))).background(tile),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) Image(painterResource(image), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else if (icon != null) RefIcon(icon, RefOrange, rp(42))
        }
        RText(name, 100, 22, 19, 26, weight = FontWeight.Medium, maxWidth = 300)
        RText("$count items", 100, 58, 15, 22, color = RefMuted)
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(rp(17)).clip(CircleShape).background(color))
        Spacer(Modifier.width(rp(11)))
        Text(text, color = RefMuted, fontSize = rs(15), lineHeight = rs(22), maxLines = 1, softWrap = false)
    }
}

/** Kantlinjeknapp med text och pil, placerad i referenspixlar. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.OutlineButton(
    label: String, x: Number, top: Number, w: Number, h: Number, color: Color, borderPx: Number,
    border: Color = color, fontSize: Number = 21, pad: Number = 26, onClick: () -> Unit
) {
    Row(
        Modifier.absoluteOffset(rp(x), rv(top)).size(rp(w), rv(h)).clip(RoundedCornerShape(rp(18)))
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
                    .background(Color(0xC70A0C12)).padding(horizontal = rp(10), vertical = rv(3))
            )
        }
    }
}

/** Spelare för video (med MediaController/seekbar), bildvisare för foto – med favorit/dela/radera/stäng. */
@Composable
fun MediaDialog(
    item: MediaItem,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DuelSurface2)
                .border(1.dp, DuelLine, RoundedCornerShape(24.dp)).padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name, color = DuelText, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clickable(onClick = onToggleFavorite).padding(4.dp)) {
                    RefIcon(RefIconKind.HEART, if (isFavorite) RefOrange else DuelTextMuted, 20.dp)
                }
            }
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
                            val mc = MediaController(ctx)
                            mc.setAnchorView(this)
                            setMediaController(mc)
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
