package com.nrworld.duelshot.ui.gallery

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.nrworld.duelshot.ui.components.DuelIcon
import com.nrworld.duelshot.ui.components.DuelIconKind
import com.nrworld.duelshot.ui.components.ScreenHeader
import com.nrworld.duelshot.ui.theme.DuelGradient
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelOrange
import com.nrworld.duelshot.ui.theme.DuelOrangeLight
import com.nrworld.duelshot.ui.theme.DuelSurface
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextFaint
import com.nrworld.duelshot.ui.theme.DuelTextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Clip(val id: Long, val name: String, val sizeMb: Long, val addedAt: Long)

@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var clips by remember { mutableStateOf<List<Clip>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf<Clip?>(null) }
    var pendingDelete by remember { mutableStateOf<Clip?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        clips = queryClips(context)
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        ScreenHeader("Gallery", "Saved to Movies/DualShots")
        Spacer(Modifier.height(14.dp))
        message?.let {
            Text(it, color = DuelOrangeLight, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }

        if (loading) {
            Text("Loading…", color = DuelTextMuted)
        } else if (clips.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp))
                    .background(DuelSurface).border(1.dp, DuelLine, RoundedCornerShape(20.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                DuelIcon(DuelIconKind.GALLERY, DuelTextFaint, 36.dp)
                Spacer(Modifier.height(12.dp))
                Text("No recordings yet", color = DuelText, fontWeight = FontWeight.SemiBold)
                Text("Start a recording from Home", color = DuelTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(clips, key = { it.id }) { clip ->
                    ClipRow(clip) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        playing = clip
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    playing?.let { clip ->
        ClipPlayerDialog(
            clip = clip,
            onClose = { playing = null },
            onShare = { shareClip(context, clip.id) },
            onDelete = { pendingDelete = clip; playing = null }
        )
    }

    pendingDelete?.let { clip ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = DuelSurface2,
            title = { Text("Delete clip?", color = DuelText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${clip.name} is removed from Movies/DualShots.",
                    color = DuelTextMuted, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val ok = deleteClip(context, clip.id)
                    pendingDelete = null
                    message = if (ok) "Clip deleted" else "Could not delete the clip"
                    reload++
                }) { Text("Delete", color = DuelOrange) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = DuelTextMuted) }
            }
        )
    }
}

@Composable
private fun ClipRow(clip: Clip, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DuelSurface)
            .border(1.dp, DuelLine, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClipThumb(clip)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(clip.name, color = DuelText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                metaLine(clip), color = DuelTextMuted, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        DuelIcon(DuelIconKind.CHEVRON, DuelTextFaint, 18.dp)
    }
}

@Composable
private fun ClipThumb(clip: Clip) {
    val context = LocalContext.current
    var bitmap by remember(clip.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(clip.id) {
        bitmap = loadThumb(context, clip.id)
    }

    Box(
        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(DuelGradient),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DuelIcon(DuelIconKind.PLAY, Color(0xFF04060D), 20.dp)
        }
    }
}

@Composable
private fun ClipPlayerDialog(clip: Clip, onClose: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DuelSurface2)
                .border(1.dp, DuelLine, RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Text(clip.name, color = DuelText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            AndroidView(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp)).background(Color.Black),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(uriFor(clip.id))
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                metaLine(clip), color = DuelTextMuted, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
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
private fun ActionPill(kind: DuelIconKind, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DuelSurface)
            .border(1.dp, DuelLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DuelIcon(kind, DuelOrangeLight, 15.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = DuelText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun metaLine(clip: Clip): String {
    val stamp = SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault())
        .format(Date(clip.addedAt * 1000))
    val size = if (clip.sizeMb > 0) "${clip.sizeMb} MB" else "MP4"
    return "$size · $stamp"
}

private fun uriFor(id: Long) =
    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

private fun loadThumb(context: Context, id: Long): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return runCatching {
        context.contentResolver.loadThumbnail(uriFor(id), Size(160, 160), null)
    }.getOrNull()
}

private fun shareClip(context: Context, id: Long) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uriFor(id))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share clip")) }
}

private fun deleteClip(context: Context, id: Long): Boolean =
    runCatching { context.contentResolver.delete(uriFor(id), null, null) > 0 }.getOrDefault(false)

private fun queryClips(context: Context): List<Clip> {
    val out = mutableListOf<Clip>()
    val proj = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DATE_ADDED
    )
    // Nya klipp hamnar i Movies/DualShots; gamla i Movies/DuelShots listas också.
    val sel = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
    val args = arrayOf("%/DualShots%", "%/DuelShots%")
    val order = "${MediaStore.Video.Media.DATE_ADDED} DESC"
    runCatching {
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, sel, args, order
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (c.moveToNext()) {
                out += Clip(
                    id = c.getLong(idCol),
                    name = c.getString(nameCol) ?: "video",
                    sizeMb = c.getLong(sizeCol) / (1024 * 1024),
                    addedAt = c.getLong(dateCol)
                )
            }
        }
    }
    return out
}
