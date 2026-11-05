package com.nrworld.duelshot.ui.gallery

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.DrawableRes
import com.nrworld.duelshot.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** En video eller ett foto i Movies/DualShots resp. Pictures/DualShots. */
data class MediaItem(
    val id: Long,
    val isPhoto: Boolean,
    val name: String,
    val sizeBytes: Long,
    val addedAt: Long,
    val durationMs: Long
) {
    val uri: Uri
        get() = ContentUris.withAppendedId(
            if (isPhoto) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
        )
}

/** Rad i listor: riktig fil eller exempel ur referensbilden (innan man spelat in). */
data class LibraryEntry(
    val key: String,
    val title: String,
    val date: String,
    val badge: String,
    val item: MediaItem? = null,
    @DrawableRes val demoImage: Int? = null
)

val DemoEntries = listOf(
    LibraryEntry("demo0", "Kista Sunset", "Today, 09:12", "00:24", demoImage = R.drawable.ref_kista_sunset),
    LibraryEntry("demo1", "Live Event", "Yesterday, 21:47", "01:03", demoImage = R.drawable.ref_live_event),
    LibraryEntry("demo2", "Road Trip", "3 days ago", "00:42", demoImage = R.drawable.ref_road_trip),
    LibraryEntry("demo3", "At the Beach", "4 days ago", "00:18", demoImage = R.drawable.ref_at_the_beach),
    LibraryEntry("demo4", "Ski Trip", "1 week ago", "01:21", demoImage = R.drawable.ref_ski_trip),
    LibraryEntry("demo5", "Stockholm", "1 week ago", "00:37", demoImage = R.drawable.ref_stockholm),
)

fun MediaItem.toEntry(): LibraryEntry = LibraryEntry(
    key = (if (isPhoto) "p" else "v") + id,
    title = name.substringBeforeLast('.'),
    date = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(addedAt * 1000)),
    badge = if (isPhoto) "PHOTO" else shortDuration(durationMs),
    item = this
)

fun shortDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "${mb.toLong()} MB"
}

/** Alla klipp och foton, nyast först. */
fun queryLibrary(context: Context): List<MediaItem> {
    val out = mutableListOf<MediaItem>()
    out += query(context, isPhoto = false)
    out += query(context, isPhoto = true)
    return out.sortedByDescending { it.addedAt }
}

private fun query(context: Context, isPhoto: Boolean): List<MediaItem> {
    val out = mutableListOf<MediaItem>()
    val base = if (isPhoto) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val proj = buildList {
        add(MediaStore.MediaColumns._ID)
        add(MediaStore.MediaColumns.DISPLAY_NAME)
        add(MediaStore.MediaColumns.SIZE)
        add(MediaStore.MediaColumns.DATE_ADDED)
        if (!isPhoto) add(MediaStore.Video.Media.DURATION)
    }.toTypedArray()
    // Nya filer hamnar i …/DualShots; gamla i …/DuelShots listas också.
    val sel = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
    val args = arrayOf("%/DualShots%", "%/DuelShots%")
    runCatching {
        context.contentResolver.query(base, proj, sel, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val durCol = if (isPhoto) -1 else c.getColumnIndex(MediaStore.Video.Media.DURATION)
            while (c.moveToNext()) {
                out += MediaItem(
                    id = c.getLong(idCol),
                    isPhoto = isPhoto,
                    name = c.getString(nameCol) ?: if (isPhoto) "photo" else "video",
                    sizeBytes = c.getLong(sizeCol),
                    addedAt = c.getLong(dateCol),
                    durationMs = if (durCol >= 0) c.getLong(durCol) else 0L
                )
            }
        }
    }
    return out
}

fun loadThumb(context: Context, item: MediaItem, size: Int = 360): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return runCatching { context.contentResolver.loadThumbnail(item.uri, Size(size, size), null) }.getOrNull()
}

/** Fotot i skärmstorlek för visning. */
fun loadPhoto(context: Context, item: MediaItem): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1600 || bounds.outHeight / (sample * 2) >= 1600) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()

fun shareItem(context: Context, item: MediaItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = if (item.isPhoto) "image/jpeg" else "video/mp4"
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, if (item.isPhoto) "Share photo" else "Share clip")) }
}

fun deleteItem(context: Context, item: MediaItem): Boolean =
    runCatching { context.contentResolver.delete(item.uri, null, null) > 0 }.getOrDefault(false)

/** Dagens datum minus [daysAgo], vid midnatt (sekunder). */
fun dayStart(daysAgo: Int): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    c.add(Calendar.DAY_OF_YEAR, -daysAgo)
    return c.timeInMillis / 1000
}
