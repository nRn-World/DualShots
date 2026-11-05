package com.nrworld.duelshot.ui.gallery

import android.app.Activity
import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.nrworld.duelshot.data.AppPrefs
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

private data class Clip(val id: Long, val name: String, val sizeMb: Long, val addedAt: Long)

@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { AppPrefs(context) }
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

    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeFolder by remember { mutableStateOf<String?>(null) }
    var moveSheetOpen by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

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
        folders = prefs.folders
        if (loaded.isEmpty()) {
            selectionMode = false
        }
        loading = false
    }

    fun toggleSelect(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    /** Visuella klipp för den valda mappen (alla om ingen vald). */
    fun visibleClips(): List<Clip> {
        val f = activeFolder
        if (f == null) return clips
        return clips.filter { prefs.folderOf(it.id) == f }
    }

    fun moveSelectedTo(folder: String) {
        val ids = selectedIds.toList()
        if (ids.isEmpty()) return
        prefs.moveToFolder(ids, folder)
        moveSheetOpen = false
        message = if (folder.isEmpty()) "${ids.size} clips unassigned" else "Moved ${ids.size} clips to $folder"
        reload++
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

    val shown = if (loading) emptyList() else visibleClips()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        if (selectionMode) {
            SelectionHeaderBar(
                selectedCount = selectedIds.size,
                allSelected = shown.isNotEmpty() && selectedIds.size == shown.size,
                onCancel = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectionMode = false
                    selectedIds = emptySet()
                },
                onToggleSelectAll = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedIds = if (selectedIds.size == shown.size) {
                        emptySet()
                    } else {
                        shown.mapTo(LinkedHashSet()) { it.id }
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

        Spacer(Modifier.height(10.dp))
        message?.let {
            Text(it, color = DuelOrangeLight, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }

        // Mappchips
        if (!loading && clips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .horizontalScroll(rememberLazyListState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FolderChip(
                    label = "All", icon = DuelIconKind.GRID,
                    active = activeFolder == null,
                    onClick = { activeFolder = null; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                )
                folders.forEach { f ->
                    val count = clips.count { prefs.folderOf(it.id) == f }
                    FolderChip(
                        label = f, icon = DuelIconKind.FOLDER, count = count,
                        active = activeFolder == f,
                        onClick = { activeFolder = f; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    )
                }
                FolderChip(
                    label = "New", icon = DuelIconKind.PLUS,
                    active = false,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showNewFolderDialog = true
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        if (loading) {
            Text("Loading…", color = DuelTextMuted)
        } else if (shown.isEmpty()) {
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
                Text(
                    if (clips.isEmpty()) "No recordings yet" else "Nothing in this folder",
                    color = DuelText, fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Start a recording from Home",
                    color = DuelTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(shown, key = { it.id }) { clip ->
                    val isSelected = clip.id in selectedIds
                    ClipRow(
                        clip = clip,
                        selectionMode = selectionMode,
                        selected = isSelected,
                        folder = prefs.folderOf(clip.id),
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
                val selectedClips = shown.filter { it.id in selectedIds }
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
                    onMoveSelected = {
                        if (selectedClips.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            moveSheetOpen = true
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

    // Flytta-kontroll för markerade klipp
    if (moveSheetOpen) {
        val entries = folders.map { it to clips.count { c -> prefs.folderOf(c.id) == it } }
        val unfiled = clips.count { c ->
            val f = prefs.folderOf(c.id); f == null || f == "Unfiled"
        }
        FolderPickerDialog(
            entries = entries,
            unfiledCount = unfiled,
            onPick = { moveSelectedTo(it) },
            onDismiss = { moveSheetOpen = false }
        )
    }

    if (showNewFolderDialog) {
        NewFolderDialog(
            existing = folders,
            onCreate = { name ->
                val clean = name.trim()
                if (prefs.addFolder(clean)) {
                    folders = prefs.folders
                    newFolderName = ""
                    showNewFolderDialog = false
                    message = "Created folder \"$clean\""
                }
            },
            onDismiss = { showNewFolderDialog = false }
        )
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
        val singleName = if (count == 1) shown.firstOrNull { it.id == ids[0] }?.name
            ?: clips.firstOrNull { it.id == ids[0] }?.name else null
        AlertDialog(
            onDismissRequest = { pendingDeleteIds = null },
            containerColor = DuelSurface2,
            title = {
                Text(
                    if (count == 1) "Delete clip?" else "Delete $count clips?",
                    color = DuelText, fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (singleName != null) "$singleName is removed from Movies/DualShots."
                    else "$count selected recordings will be removed from Movies/DualShots.",
                    color = DuelTextMuted, fontSize = 13.sp
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
private fun FolderChip(
    label: String, icon: DuelIconKind, count: Int = 0,
    active: Boolean, onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) DuelGradient else DuelSurface2)
            .border(1.dp, if (active) DuelOrange.copy(alpha = 0.5f) else DuelLineStrong, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DuelIcon(icon, if (active) Color(0xFF04060D) else DuelOrangeLight, 13.dp)
        Text(
            text = label,
            color = if (active) Color(0xFF04060D) else DuelText,
            fontSize = 11.sp, fontWeight = FontWeight.Bold
        )
        if (count > 0) {
            Text(
                text = count.toString(),
                color = if (active) Color(0xFF04060D).copy(alpha = 0.7f) else DuelTextMuted,
                fontSize = 10.sp, fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun FolderPickerDialog(
    entries: List<Pair<String, Int>>,
    unfiledCount: Int,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DuelSurface2,
        title = { Text("Move to folder", color = DuelText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Marked clips will move here", color = DuelTextMuted, fontSize = 12.sp)
                if (entries.isEmpty()) {
                    Text("No folders yet — tap “New folder” below to create one.", color = DuelTextFaint, fontSize = 12.sp)
                }
                entries.forEach { (f, count) ->
                    FolderRow(name = f, count = count, onClick = { onPick(f) })
                }
                FolderRow(name = "No folder", count = unfiledCount, onClick = { onPick("") })
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DuelTextMuted) } }
    )
}

/** Ny mapp: skriver in namn och skapar. */
@Composable
private fun NewFolderDialog(
    existing: List<String>,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DuelSurface2,
        title = { Text("New folder", color = DuelText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DuelText,
                        unfocusedTextColor = DuelText,
                        focusedBorderColor = DuelOrange,
                        unfocusedBorderColor = DuelLineStrong,
                        cursorColor = DuelOrange
                    )
                )
                if (existing.isNotEmpty()) {
                    Text("Existing: ${existing.joinToString(", ")}", color = DuelTextFaint, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }) { Text("Create", color = DuelOrange) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DuelTextMuted) } }
    )
}

@Composable
private fun FolderRow(name: String, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DuelSurface)
            .border(1.dp, DuelLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DuelIcon(DuelIconKind.FOLDER, DuelOrangeLight, 17.dp)
        Spacer(Modifier.width(12.dp))
        Text(name, color = DuelText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("$count", color = DuelTextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        DuelIcon(DuelIconKind.CHEVRON, DuelTextFaint, 16.dp)
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
        ) { DuelIcon(DuelIconKind.CLOSE, DuelText, 15.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (selectedCount == 0) "Select clips" else "$selectedCount selected",
                color = DuelText, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp
            )
            Text("Tap clips to mark or unmark", color = DuelTextMuted, fontSize = 11.sp)
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
                color = DuelOrangeLight, fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    totalMb: Long,
    onShareSelected: () -> Unit,
    onMoveSelected: () -> Unit,
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
                color = DuelTextMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                letterSpacing = 0.7.sp, modifier = Modifier.padding(bottom = 10.dp, start = 2.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f).height(48.dp)
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
                    color = Color(0xFF04060D), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f).height(48.dp)
                    .alpha(if (enabled) 1f else 0.42f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DuelSurface)
                    .border(1.dp, DuelLineStrong, RoundedCornerShape(14.dp))
                    .clickable(enabled = enabled, onClick = onMoveSelected)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                DuelIcon(DuelIconKind.FOLDER, DuelOrangeLight, 17.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (enabled) "Move ($selectedCount)" else "Move",
                    color = DuelText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f).height(48.dp)
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
                    color = Color(0xFFFF8080), fontWeight = FontWeight.Bold, fontSize = 13.sp
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
    folder: String?,
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                clip.name, color = DuelText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                metaLine(clip), color = DuelTextMuted, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (folder != null && folder != "Unfiled") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    DuelIcon(DuelIconKind.FOLDER, DuelOrangeLight, 11.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(folder, color = DuelOrangeLight, fontSize = 10.sp)
                }
            }
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
            modifier = Modifier.size(24.dp).clip(CircleShape).background(DuelGradient),
            contentAlignment = Alignment.Center
        ) { DuelIcon(DuelIconKind.CHECK, Color(0xFF04060D), 14.dp) }
    } else {
        Box(
            modifier = Modifier
                .size(24.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
                .border(1.5.dp, DuelLineStrong, CircleShape)
        )
    }
}

@Composable
private fun ClipThumb(clip: Clip) {
    val context = LocalContext.current
    var bitmap by remember(clip.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(clip.id) { bitmap = loadThumb(context, clip.id) }
    Box(
        modifier = Modifier
            .size(56.dp).clip(RoundedCornerShape(14.dp)).background(DuelGradient),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else {
            DuelIcon(DuelIconKind.PLAY, Color(0xFF04060D), 20.dp)
        }
    }
}

/**
 * Spelare med tidslinje: thumbnail-filmstrip (klickbar för att hoppa) +
 * egen scrubber som visar position och låter klicka/dra för att scrubba.
 */
@Composable
private fun ClipPlayerDialog(
    clip: Clip,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    var durationMs by remember { mutableStateOf(0L) }
    var positionMs by remember { mutableStateOf(0L) }
    var thumbs by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }

    // Ladda thumbnails i bakgrunden
    LaunchedEffect(clip.id) {
        thumbs = withContext(Dispatchers.IO) { buildThumbs(context, uriFor(clip.id), 6) }
    }

    // Peka position med 250 ms intervall så tidslinjen rör sig med videon
    LaunchedEffect(videoView) {
        val vv = videoView ?: return@LaunchedEffect
        while (isActive) {
            positionMs = vv.currentPosition.toLong()
            if (vv.duration > 0) durationMs = vv.duration.toLong()
            delay(250)
        }
    }

    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DuelSurface2)
                .border(1.dp, DuelLine, RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(clip.name, color = DuelText, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                DuelIcon(DuelIconKind.TRASH, Color(0xFFFF8080), 18.dp)
            }
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
                        setMediaController(true)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                            videoView = this
                        }
                    }
                }
            )

            Spacer(Modifier.height(10.dp))

            // Tidslinje: text + scrubber (klicka för att hoppa)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatMs(positionMs), color = DuelOrangeLight, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.width(44.dp)
                )
                BoxWithConstraints(modifier = Modifier.weight(1f).height(28.dp)) {
                    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    val thumbX = (maxWidth.value * frac - 7.dp.value).coerceAtLeast(0f)
                    // spår
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(DuelSurface)
                    ) {
                        Box(
                            modifier = Modifier
                                .width((maxWidth * frac).coerceAtLeast(0.dp))
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(DuelGradient)
                        )
                        // topphand
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(thumbX.toInt(), (-5.dp).value.toInt()) }
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(DuelOrangeLight)
                                .border(2.dp, Color.Black, CircleShape)
                        )
                    }
                    // klick-yta för att scrubba
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                            .pointerInput(maxWidth.value) {
                                detectTapGestures(onTap = { offset ->
                                    val vv = videoView
                                    if (vv != null && durationMs > 0 && maxWidth.value > 0) {
                                        val f = (offset.x / maxWidth.value).coerceIn(0f, 1f)
                                        vv.seekTo((durationMs * f).toInt())
                                    }
                                })
                            }
                    )
                }
                Text(
                    formatMs(durationMs), color = DuelTextFaint, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.width(44.dp)
                )
            }
            Spacer(Modifier.height(8.dp))

            // Thumbnail-filmstrip
            if (thumbs.isNotEmpty()) {
                val nonNull = thumbs.filterNotNull()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .horizontalScroll(rememberLazyListState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    nonNull.forEach { bmp ->
                        val tMs = thumbs.indexOf(bmp).let { idx ->
                            if (durationMs > 0) durationMs * (idx + 1) / (nonNull.size + 1) else 0L
                        }
                        Box(
                            modifier = Modifier
                                .size(52.dp, 32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DuelSurface)
                                .border(1.dp, DuelLine, RoundedCornerShape(6.dp))
                                .clickable {
                                    videoView?.seekTo(tMs.toInt())
                                }
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(), contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Tap a frame to jump", color = DuelTextFaint, fontSize = 11.sp)
            }

            Spacer(Modifier.height(6.dp))
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
    val stamp = SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date(clip.addedAt * 1000))
    val size = if (clip.sizeMb > 0) "${clip.sizeMb} MB" else "MP4"
    return "$size · $stamp"
}

private fun uriFor(id: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

private fun loadThumb(context: Context, id: Long): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return runCatching { context.contentResolver.loadThumbnail(uriFor(id), Size(160, 160), null) }.getOrNull()
}

/** Laddar frame vid jämna intervall för thumbnail-filmstripen. */
private fun buildThumbs(context: Context, uri: Uri, count: Int): List<Bitmap?> {
    val out = mutableListOf<Bitmap?>()
    val mr = MediaMetadataRetriever()
    try {
        mr.setDataSource(context, uri)
        val dur = runCatching {
            mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }.getOrNull() ?: 0L
        for (i in 1..count) {
            val tMs = if (dur > 0) dur * i / (count + 1) else 0L
            out += runCatching { mr.getFrameAtTime(tMs * 1000L, true) }.getOrNull()
        }
    } finally {
        runCatching { mr.release() }
    }
    return out
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return if (m >= 60) {
        val h = m / 60; val mm = m % 60
        "${h}:${mm.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    } else {
        "${m}:${sec.toString().padStart(2, '0')}"
    }
}

/** Skickar vidare ett eller flera klipp via delningsark med korrekt läsbehörighet. */
private fun shareClips(context: Context, ids: List<Long>) {
    if (ids.isEmpty()) return
    val uris = ArrayList(ids.map { uriFor(it) })
    val clipData = ClipData.newRawUri("DualShots videos", uris[0]).apply {
        for (i in 1 until uris.size) addItem(ClipData.Item(uris[i]))
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
