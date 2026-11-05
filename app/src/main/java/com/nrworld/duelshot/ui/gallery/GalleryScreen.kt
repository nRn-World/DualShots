package com.nrworld.duelshot.ui.gallery

import android.app.Activity
import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.nrworld.duelshot.ui.components.DuelIcon
import com.nrworld.duelshot.ui.components.DuelIconKind
import com.nrworld.duelshot.ui.components.ScreenHeader
import com.nrworld.duelshot.ui.theme.DuelGradient
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelLineStrong
import com.nrworld.duelshot.ui.theme.DuelOrange
import com.nrworld.duelshot.ui.theme.DuelOrangeLight
import com.nrworld.duelshot.ui.theme.DuelRecord
import com.nrworld.duelshot.ui.theme.DuelSurface
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextFaint
import com.nrworld.duelshot.ui.theme.DuelTextMuted
import java.text.SimpleDateFormat
import java.util.ArrayList
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
    var pendingDeleteIds by remember { mutableStateOf<List<Long>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var preDeletedCount by remember { mutableIntStateOf(0) }
    var requestedSystemCount by remember { mutableIntStateOf(0) }

    val systemDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val totalDeleted = preDeletedCount + if (result.resultCode == Activity.RESULT_OK) requestedSystemCount else 0
        preDeletedCount = 0
        requestedSystemCount = 0
        selectedIds = emptySet()
        selectionMode = false
        message = when {
            totalDeleted > 1 -> "$totalDeleted clips deleted"
            totalDeleted == 1 -> "Clip deleted"
            else -> "Deletion cancelled"
        }
        reload++
    }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    LaunchedEffect(reload) {
        val loaded = queryClips(context)
        clips = loaded
        val existingIds = loaded.mapTo(HashSet()) { it.id }
        selectedIds = selectedIds.intersect(existingIds)
        if (loaded.isEmpty()) {
            selectionMode = false
        }
        loading = false
    }

    fun toggleSelect(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun performDelete(ids: List<Long>) {
        if (ids.isEmpty()) return
        val failedUris = mutableListOf<Uri>()
        var deletedDirect = 0
        ids.forEach { id ->
            if (deleteClip(context, id)) {
                deletedDirect++
            } else {
                failedUris += uriFor(id)
            }
        }

        if (failedUris.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val launched = runCatching {
                val pending = MediaStore.createDeleteRequest(context.contentResolver, failedUris)
                preDeletedCount = deletedDirect
                requestedSystemCount = failedUris.size
                systemDeleteLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                true
            }.getOrDefault(false)
            if (launched) return
        }

        selectedIds = selectedIds - ids.toSet()
        if (selectedIds.isEmpty()) selectionMode = false
        message = when {
            deletedDirect > 1 -> "$deletedDirect clips deleted"
            deletedDirect == 1 -> "Clip deleted"
            else -> "Could not delete selected files"
        }
        reload++
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        if (selectionMode) {
            SelectionHeaderBar(
                selectedCount = selectedIds.size,
                allSelected = clips.isNotEmpty() && selectedIds.size == clips.size,
                onCancel = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectionMode = false
                    selectedIds = emptySet()
                },
                onToggleSelectAll = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedIds = if (selectedIds.size == clips.size) {
                        emptySet()
                    } else {
                        clips.mapTo(LinkedHashSet()) { it.id }
                    }
                }
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    ScreenHeader(
                        "Gallery",
                        if (clips.isEmpty()) "Saved to Movies/DualShots"
                        else "Saved to Movies/DualShots · Hold to select"
                    )
                }
                if (clips.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(DuelSurface2)
                            .border(1.dp, DuelLineStrong, RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectionMode = true
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "Select",
                            color = DuelText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        message?.let {
            Text(it, color = DuelOrangeLight, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }

        if (loading) {
            Text("Loading…", color = DuelTextMuted)
        } else if (clips.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(DuelSurface)
                    .border(1.dp, DuelLine, RoundedCornerShape(20.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                DuelIcon(DuelIconKind.GALLERY, DuelTextFaint, 36.dp)
                Spacer(Modifier.height(12.dp))
                Text("No recordings yet", color = DuelText, fontWeight = FontWeight.SemiBold)
                Text(
                    "Start a recording from Home",
                    color = DuelTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(clips, key = { it.id }) { clip ->
                    val isSelected = clip.id in selectedIds
                    ClipRow(
                        clip = clip,
                        selectionMode = selectionMode,
                        selected = isSelected,
                        onClick = {
                            if (selectionMode) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                toggleSelect(clip.id)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playing = clip
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!selectionMode) {
                                selectionMode = true
                                selectedIds = setOf(clip.id)
                            } else {
                                toggleSelect(clip.id)
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (selectionMode) {
                val selectedClips = clips.filter { it.id in selectedIds }
                val totalMb = selectedClips.sumOf { it.sizeMb }
                SelectionActionBar(
                    selectedCount = selectedClips.size,
                    totalMb = totalMb,
                    onShareSelected = {
                        if (selectedClips.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            shareClips(context, selectedClips.map { it.id })
                        }
                    },
                    onDeleteSelected = {
                        if (selectedClips.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pendingDeleteIds = selectedClips.map { it.id }
                        }
                    }
                )
            }
        }
    }

    playing?.let { clip ->
        ClipPlayerDialog(
            clip = clip,
            onClose = { playing = null },
            onShare = { shareClips(context, listOf(clip.id)) },
            onDelete = {
                pendingDeleteIds = listOf(clip.id)
                playing = null
            }
        )
    }

    pendingDeleteIds?.let { ids ->
        val count = ids.size
        val singleName = if (count == 1) clips.firstOrNull { it.id == ids[0] }?.name else null
        AlertDialog(
            onDismissRequest = { pendingDeleteIds = null },
            containerColor = DuelSurface2,
            title = {
                Text(
                    if (count == 1) "Delete clip?" else "Delete $count clips?",
                    color = DuelText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (singleName != null) {
                        "$singleName is removed from Movies/DualShots."
                    } else {
                        "$count selected recordings will be removed from Movies/DualShots."
                    },
                    color = DuelTextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val toRemove = ids
                    pendingDeleteIds = null
                    performDelete(toRemove)
                }) {
                    Text(if (count == 1) "Delete" else "Delete ($count)", color = DuelOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIds = null }) {
                    Text("Cancel", color = DuelTextMuted)
                }
            }
        )
    }
}

@Composable
private fun SelectionHeaderBar(
    selectedCount: Int,
    allSelected: Boolean,
    onCancel: () -> Unit,
    onToggleSelectAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DuelSurface2)
            .border(1.dp, DuelLineStrong, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(DuelSurface)
                .border(1.dp, DuelLine, CircleShape)
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center
        ) {
            DuelIcon(DuelIconKind.CLOSE, DuelText, 15.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (selectedCount == 0) "Select clips" else "$selectedCount selected",
                color = DuelText,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            Text(
                text = "Tap clips to mark or unmark",
                color = DuelTextMuted,
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(DuelSurface)
                .border(1.dp, DuelLineStrong, RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleSelectAll)
                .padding(horizontal = 11.dp, vertical = 7.dp)
        ) {
            Text(
                text = if (allSelected) "Deselect all" else "Select all",
                color = DuelOrangeLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    totalMb: Long,
    onShareSelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    val enabled = selectedCount > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DuelSurface2)
            .border(1.dp, DuelLineStrong, RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        if (enabled) {
            Text(
                text = "$selectedCount ${if (selectedCount == 1) "FILE" else "FILES"} SELECTED" +
                    if (totalMb > 0) " · $totalMb MB" else "",
                color = DuelTextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 0.7.sp,
                modifier = Modifier.padding(bottom = 10.dp, start = 2.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .alpha(if (enabled) 1f else 0.42f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DuelGradient)
                    .clickable(enabled = enabled, onClick = onShareSelected)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                DuelIcon(DuelIconKind.SHARE, Color(0xFF04060D), 17.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (enabled) "Share ($selectedCount)" else "Share",
                    color = Color(0xFF04060D),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .alpha(if (enabled) 1f else 0.42f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DuelRecord.copy(alpha = 0.16f))
                    .border(1.dp, DuelRecord.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .clickable(enabled = enabled, onClick = onDeleteSelected)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                DuelIcon(DuelIconKind.TRASH, Color(0xFFFF6B6B), 17.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (enabled) "Delete ($selectedCount)" else "Delete",
                    color = Color(0xFFFF8080),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipRow(
    clip: Clip,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val borderColor = when {
        selected -> DuelOrange.copy(alpha = 0.85f)
        selectionMode -> DuelLineStrong
        else -> DuelLine
    }
    val bgColor = if (selected) DuelSurface2 else DuelSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionCheckCircle(selected = selected)
            Spacer(Modifier.width(12.dp))
        }
        ClipThumb(clip)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                clip.name,
                color = DuelText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                metaLine(clip),
                color = DuelTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (!selectionMode) {
            DuelIcon(DuelIconKind.CHEVRON, DuelTextFaint, 18.dp)
        }
    }
}

@Composable
private fun SelectionCheckCircle(selected: Boolean) {
    if (selected) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(DuelGradient),
            contentAlignment = Alignment.Center
        ) {
            DuelIcon(DuelIconKind.CHECK, Color(0xFF04060D), 14.dp)
        }
    } else {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
                .border(1.5.dp, DuelLineStrong, CircleShape)
        )
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
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DuelGradient),
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
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DuelSurface2)
                .border(1.dp, DuelLine, RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Text(clip.name, color = DuelText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
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
                metaLine(clip),
                color = DuelTextMuted,
                fontSize = 11.sp,
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

private fun uriFor(id: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

private fun loadThumb(context: Context, id: Long): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return runCatching {
        context.contentResolver.loadThumbnail(uriFor(id), Size(160, 160), null)
    }.getOrNull()
}

/**
 * Skickar vidare ett eller flera klipp via Androids delningsark (WhatsApp, Snapchat, Quick Share, Meddelanden m.m.)
 * med korrekt ClipData-läsbehörighet för samtliga valda videofiler.
 */
private fun shareClips(context: Context, ids: List<Long>) {
    if (ids.isEmpty()) return
    val uris = ArrayList(ids.map { uriFor(it) })
    val clipData = ClipData.newRawUri("DualShots videos", uris[0]).apply {
        for (i in 1 until uris.size) {
            addItem(ClipData.Item(uris[i]))
        }
    }

    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uris[0])
            this.clipData = clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/mp4"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            this.clipData = clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    val title = if (uris.size == 1) "Share clip" else "Share ${uris.size} clips"
    runCatching { context.startActivity(Intent.createChooser(send, title)) }
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
