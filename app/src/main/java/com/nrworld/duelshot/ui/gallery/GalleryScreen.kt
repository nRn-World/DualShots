package com.nrworld.duelshot.ui.gallery

import android.content.Intent
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelPurple
import com.nrworld.duelshot.ui.theme.DuelRecord
import com.nrworld.duelshot.ui.theme.DuelSurface
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextMuted

private data class Clip(val id: Long, val name: String, val sizeMb: Long)

@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    var clips by remember { mutableStateOf<List<Clip>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        clips = queryClips(context)
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Gallery", color = DuelText, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(
            "Inspelningar sparas i Galleri → Movies/DuelShots",
            color = DuelTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(16.dp))

        if (loading) {
            Text("Laddar…", color = DuelTextMuted)
        } else if (clips.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp))
                    .background(DuelSurface),
                contentAlignment = Alignment.Center
            ) {
                Text("Inga inspelningar än. Tryck ”Start Recording”.", color = DuelTextMuted)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(clips, key = { it.id }) { clip ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DuelSurface)
                            .clickable { openClip(context, clip.id) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                .background(DuelPurple.copy(alpha = .25f)),
                            contentAlignment = Alignment.Center
                        ) { Text("▶", color = DuelRecord, fontSize = 16.sp) }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(clip.name, color = DuelText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                if (clip.sizeMb > 0) "${clip.sizeMb} MB" else "MP4",
                                color = DuelTextMuted, fontSize = 12.sp
                            )
                        }
                        Text("›", color = DuelTextMuted, fontSize = 20.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun queryClips(context: android.content.Context): List<Clip> {
    val out = mutableListOf<Clip>()
    val proj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE)
    val sel = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
    val args = arrayOf("%/DuelShots%")
    val order = "${MediaStore.Video.Media.DATE_ADDED} DESC"
    runCatching {
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, sel, args, order
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            while (c.moveToNext()) {
                out += Clip(
                    id = c.getLong(idCol),
                    name = c.getString(nameCol) ?: "video",
                    sizeMb = c.getLong(sizeCol) / (1024 * 1024)
                )
            }
        }
    }
    return out
}

private fun openClip(context: android.content.Context, id: Long) {
    val uri = android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}
