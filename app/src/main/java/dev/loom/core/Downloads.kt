package dev.loom.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Offline audio at `Music/Loom/<playlist>/<title>.m4a`, or `Music/Loom/<title>.m4a` for a loose
 * track. Both levels are created on first write.
 *
 * Written through MediaStore rather than `File`: with targetSdk 35 the app cannot create files
 * under `Music/` directly, and MediaStore is the supported path. A side benefit is that the files
 * show up in other music players, which is what "saved to Music/Loom" should mean.
 *
 * `shortcut:` a trimmed track is cut into a real file by Media3's Transformer rather than stored
 * with a start marker, so it stays portable. Cost is one re-encode per trimmed track.
 */
@UnstableApi
object Downloads {
    private const val ROOT = "Loom"
    private const val MIME = "audio/mp4"
    /** `Music/Loom`; RELATIVE_PATH must end with a slash. */
    private val BASE = "${Environment.DIRECTORY_MUSIC}/$ROOT"

    private fun relative(playlist: String?): String =
        if (playlist.isNullOrBlank()) "$BASE/" else "$BASE/${safe(playlist)}/"

    /** Filesystem-safe: these characters are illegal in a MediaStore display name. */
    fun safe(raw: String): String =
        raw.replace(Regex("""[/\\:*?"<>|\n\r\t]"""), "_").trim().trim('.').take(120)

    fun fileName(t: Track): String {
        // Built from the raw parts, not the joined string: a blank title with a real artist would
        // otherwise name the file after the artist alone and lose the track identity.
        val base = when {
            t.title.isNotBlank() && t.artist.isNotBlank() -> safe("${t.title} - ${t.artist}")
            t.title.isNotBlank() -> safe(t.title)
            // Blank title: the id identifies the track; the artist alone would not.
            else -> safe(t.id)
        }
        return (base.ifBlank { safe(t.id) }) + ".m4a"
    }

    /** Every downloaded file, grouped by playlist folder. Null key = loose tracks. */
    fun all(ctx: Context): Map<String?, List<Downloaded>> {
        val out = mutableMapOf<String?, MutableList<Downloaded>>()
        val cols = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.SIZE,
        )
        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            cols,
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("$BASE%"),
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (c.moveToNext()) {
                val path = c.getString(pathCol) ?: ""
                // "Music/Loom/" -> null, "Music/Loom/Rock/" -> "Rock"
                val folder = path.removePrefix(BASE).trim('/').ifBlank { null }
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    c.getLong(idCol),
                )
                out.getOrPut(folder) { mutableListOf() } += Downloaded(
                    uri = uri,
                    name = c.getString(nameCol) ?: "",
                    sizeBytes = c.getLong(sizeCol),
                    folder = folder,
                )
            }
        }
        return out.filterValues { it.isNotEmpty() }
    }

    /**
     * Streams the resolved URL straight into MediaStore. The CDN link is signed and expires, so a
     * saved URL would be dead by the next launch — only the bytes last.
     */
    suspend fun download(
        ctx: Context,
        t: Track,
        playlist: String?,
        startMs: Long = 0,
        endMs: Long = 0,
        onProgress: (Float) -> Unit = {},
    ): Uri = withContext(Dispatchers.IO) {
        // Re-downloading a playlist must not leave two copies of every track in the library.
        if (!(endMs > startMs)) {
            alreadyThere(ctx, t, playlist)?.let { return@withContext it }
        }

        val stream = Music.bestStream(t)
        // A trimmed copy has to be re-encoded, and Transformer works on files, so those go through
        // the cache dir first and land in MediaStore at the end.
        val needsTrim = endMs > startMs
        val scratch = if (needsTrim) File(ctx.cacheDir, "dl").apply { mkdirs() } else null
        val raw = scratch?.let { File(it, "src.m4a") }
        val target = raw ?: File(ctx.cacheDir, "dl-direct.m4a")

        fetch(stream.url, stream.userAgent, target, onProgress)
        val finished = if (needsTrim) {
            val cut = File(scratch, "cut.m4a")
            trim(ctx, target, cut, startMs, endMs)
            target.delete()
            cut
        } else {
            target
        }
        val uri = publish(ctx, finished, t, playlist)
        finished.delete()
        if (scratch != null) scratch.deleteRecursively()
        uri
    }

    /** The finished file with this exact name, if it is already in the library. */
    private fun alreadyThere(ctx: Context, t: Track, playlist: String?): Uri? =
        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
            arrayOf(relative(playlist), fileName(t)),
            null,
        )?.use { c -> if (c.moveToFirst()) {
            android.content.ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0),
            )
        } else null }

    private fun fetch(url: String, ua: String, into: File, onProgress: (Float) -> Unit) {
        val conn = java.net.URL(url).openConnection().apply {
            setRequestProperty("User-Agent", ua.ifBlank { "Mozilla/5.0" })
            setRequestProperty("Referer", "https://music.youtube.com/")
            connectTimeout = 20_000
            readTimeout = 20_000
            connect()
        }
        val total = conn.contentLengthLong
        conn.getInputStream().use { input ->
            into.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        if (into.length() == 0L) error("download produced no data")
    }

    /**
     * Copies a finished file into `Music/Loom/...`.
     *
     * `shortcut:` RELATIVE_PATH and IS_PENDING both arrive in API 29. On 26-28 the file goes to
     * the app's own external Music dir instead, which needs no storage permission but is only
     * reachable through a file manager. Acceptable: the device this ships for is on 13+.
     */
    private fun publish(ctx: Context, src: File, t: Track, playlist: String?): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir, ROOT)
            val target = File(if (playlist.isNullOrBlank()) dir else File(dir, safe(playlist)), fileName(t))
            target.parentFile?.mkdirs()
            src.inputStream().use { i -> target.outputStream().use { i.copyTo(it) } }
            return Uri.fromFile(target)
        }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName(t))
            put(MediaStore.Audio.Media.MIME_TYPE, MIME)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relative(playlist))
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            // Hidden from other apps until the bytes are complete, so a half-written file never
            // shows up in someone else's library.
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore refused the entry")
        runCatching {
            resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                ?: error("cannot open output for $uri")
        }.onFailure {
            resolver.delete(uri, null, null)
            throw it
        }
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /** Cuts [startMs, endMs] out of [src] into [dst] with Media3's Transformer. */
    private suspend fun trim(ctx: Context, src: File, dst: File, startMs: Long, endMs: Long) =
        withContext(Dispatchers.Main) {
            val item = EditedMediaItem.Builder(
                MediaItem.Builder()
                    .setUri(Uri.fromFile(src))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs)
                            .setEndPositionMs(endMs)
                            .build(),
                    )
                    .build(),
            ).build()
            val latch = CountDownLatch(1)
            var failure: Throwable? = null
            val transformer = Transformer.Builder(ctx)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(c: Composition, r: ExportResult) = latch.countDown()
                    override fun onError(c: Composition, r: ExportResult, e: ExportException) {
                        failure = e
                        latch.countDown()
                    }
                })
                .build()
            transformer.start(item, dst.absolutePath)
            if (!latch.await(180, TimeUnit.SECONDS)) {
                transformer.cancel()
                error("trim timed out")
            }
            failure?.let { throw it }
        }

    fun remove(ctx: Context, uri: Uri): Boolean =
        runCatching { ctx.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)

    fun sizeText(bytes: Long): String = when {
        bytes >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes > 0 -> "${bytes / 1024} KB"
        else -> "—"
    }
}

/** One file on disk, as MediaStore reports it. */
data class Downloaded(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    /** Playlist folder under `Music/Loom`, or null for a loose track. */
    val folder: String?,
) {
    // The provider appends an extension on insert, so a name can come back as "X.m4a.m4a".
    val title: String get() = name.replace(Regex("""(\.[A-Za-z0-9]{1,5})+$"""), "")
}
