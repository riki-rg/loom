package dev.loom.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import org.json.JSONArray
import org.json.JSONObject

/** JNI entry points implemented in Rust (core/src/lib.rs). */
object Core {
    init { System.loadLibrary("loom_core") }

    @JvmStatic external fun searchYtMusic(query: String): String
    @JvmStatic external fun resolveYtMusic(id: String): String
    @JvmStatic external fun searchSoundCloud(query: String): String
    @JvmStatic external fun resolveSoundCloud(id: String): String
    @JvmStatic external fun searchSpotify(query: String): String
    @JvmStatic external fun resolveSpotify(id: String): String
    @JvmStatic external fun ytHome(): String
    @JvmStatic external fun ytForYou(): String
    @JvmStatic external fun ytRadio(id: String): String
    @JvmStatic external fun ytPlaylist(id: String, params: String): String
    @JvmStatic external fun ytLyrics(id: String): String
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val art: String,
    val source: Source,
    val durationMs: Long = 0,
)

/** A playlist or album card on the home / for-you feeds. */
data class Collection(
    val id: String,
    val params: String,
    val title: String,
    val subtitle: String,
    val art: String,
    /** "playlist" opens as a browse page; "track" is a single music video. */
    val kind: String = "playlist",
)

enum class Source(val label: String) {
    YT_MUSIC("YouTube Music"),
    SPOTIFY("Spotify"),
    SOUNDCLOUD("SoundCloud"),
}

data class Stream(val url: String, val userAgent: String, val preview: Boolean = false)

/** One timed lyric line. `start` is seconds from the beginning of the track. */
data class LyricLine(val start: Double, val text: String)

class CoreException(message: String) : Exception(message)

private fun unwrap(raw: String): Any {
    val o = JSONObject(raw)
    if (!o.optBoolean("ok")) throw CoreException(o.optString("error", "unknown error"))
    return o.get("data")
}

private fun parseTracks(raw: String, source: Source): List<Track> {
    val arr = unwrap(raw) as JSONArray
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Track(
            id = o.optString("id"),
            title = o.optString("title"),
            artist = o.optString("artist"),
            art = o.optString("art"),
            source = source,
            durationMs = o.optLong("duration"),
        )
    }
}

private fun parseCollections(raw: String): List<Collection> =
    collectionsOf(unwrap(raw) as JSONArray)

private fun collectionsOf(arr: JSONArray): List<Collection> =
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.getJSONObject(i)
        val id = o.optString("id")
        if (id.isBlank()) return@mapNotNull null
        Collection(
            id = id,
            params = o.optString("params"),
            title = o.optString("title"),
            subtitle = o.optString("subtitle"),
            art = o.optString("art"),
            kind = o.optString("kind", "playlist"),
        )
    }

/** Home feed as shelves of collection cards. */
data class Shelf(val title: String, val items: List<Collection>)

object Music {
    fun search(query: String, source: Source): List<Track> = when (source) {
        Source.YT_MUSIC -> parseTracks(Core.searchYtMusic(query), source)
        Source.SPOTIFY -> parseTracks(Core.searchSpotify(query), source)
        Source.SOUNDCLOUD -> parseTracks(Core.searchSoundCloud(query), source)
    }

    fun stream(track: Track): Stream {
        val raw = when (track.source) {
            Source.YT_MUSIC -> Core.resolveYtMusic(track.id)
            Source.SPOTIFY -> Core.resolveSpotify(track.id)
            Source.SOUNDCLOUD -> Core.resolveSoundCloud(track.id)
        }
        val o = unwrap(raw) as JSONObject
        return Stream(
            url = o.getString("url"),
            userAgent = o.optString("ua"),
            preview = o.optBoolean("preview"),
        )
    }

    fun home(): List<Shelf> = homeForTest(Core.ytHome())

    /** Test seam: the parser is pure, only the fetch needs a device. */
    internal fun homeForTest(raw: String): List<Shelf> {
        val arr = unwrap(raw) as JSONArray
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val items = o.optJSONArray("items") ?: JSONArray()
            Shelf(
                title = o.optString("title"),
                items = collectionsOf(items),
            )
        }
    }

    fun forYou(): List<Shelf> = shelvesForTest(Core.ytForYou())

    internal fun shelvesForTest(raw: String): List<Shelf> {
        val arr = unwrap(raw) as JSONArray
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Shelf(o.optString("title"), collectionsOf(o.optJSONArray("items") ?: JSONArray()))
        }
    }

    fun playlist(id: String, params: String): List<Track> =
        parseTracks(Core.ytPlaylist(id, params), Source.YT_MUSIC)

    /** Lyrics plus their timings; `synced` is empty when only plain text exists. */
    fun lyrics(track: Track): Pair<String?, List<LyricLine>> {
        if (track.source != Source.YT_MUSIC) return null to emptyList()
        val o = unwrap(Core.ytLyrics(track.id)) as JSONObject
        val text = o.optString("text").takeIf { it.isNotBlank() }
        val synced = o.optJSONArray("synced")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val l = a.getJSONObject(i)
                LyricLine(l.optDouble("start", -1.0), l.optString("text"))
            }.filter { it.start >= 0 && it.text.isNotBlank() }
        } ?: emptyList()
        return text to synced
    }

    /** Autoplay queue: same artist and genre, seeded by the track that just finished. */
    fun radio(track: Track): List<Track> =
        parseTracks(Core.ytRadio(track.id), Source.YT_MUSIC)

    /**
     * Play from whichever source answers first with a full track. A Spotify hit is a 30-second
     * preview, so a YouTube Music copy of the same song is preferred even if it arrives later.
     * `shortcut:` no match scoring — the top YouTube hit is trusted; add a title/artist
     * similarity check if wrong songs start playing.
     */
    suspend fun bestStream(track: Track): Stream {
        if (track.source == Source.YT_MUSIC) return stream(track)
        return coroutineScope {
            val q = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ")
            val yt = async(Dispatchers.IO) {
                runCatching {
                    search(q, Source.YT_MUSIC).firstOrNull()?.let { stream(it) }
                }.getOrNull()
            }
            val own = async(Dispatchers.IO) { runCatching { stream(track) }.getOrNull() }
            // Whichever lands first; if it is only a preview, wait for the other and prefer it.
            val (first, fromYt) = select<Pair<Stream?, Boolean>> {
                yt.onAwait { it to true }
                own.onAwait { it to false }
            }
            val second = runCatching { if (fromYt) own.await() else yt.await() }.getOrNull()
            val winner = listOfNotNull(first, second).firstOrNull { !it.preview }
                ?: first ?: second
            yt.cancel()
            own.cancel()
            winner ?: throw CoreException("no stream for ${track.title}")
        }
    }
}
