package dev.loom.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the app remembers between launches. SharedPreferences with JSON blobs: no new
 * dependency, and the payloads are small enough that a database would be overhead.
 */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("loom", Context.MODE_PRIVATE)

    private fun Track.toJson() = JSONObject()
        .put("id", id).put("title", title).put("artist", artist)
        .put("art", art).put("source", source.name).put("duration", durationMs)

    private fun trackOf(o: JSONObject) = Track(
        id = o.optString("id"),
        title = o.optString("title"),
        artist = o.optString("artist"),
        art = o.optString("art"),
        source = runCatching { Source.valueOf(o.optString("source")) }.getOrDefault(Source.YT_MUSIC),
        durationMs = o.optLong("duration"),
    )

    private fun Collection.toJson() = JSONObject()
        .put("id", id).put("params", params).put("title", title)
        .put("subtitle", subtitle).put("art", art).put("kind", kind)

    private fun collectionOf(o: JSONObject) = Collection(
        id = o.optString("id"),
        params = o.optString("params"),
        title = o.optString("title"),
        subtitle = o.optString("subtitle"),
        art = o.optString("art"),
        kind = o.optString("kind", "playlist"),
    )

    private fun readTracks(key: String): List<Track> =
        runCatching {
            val a = JSONArray(p.getString(key, "[]"))
            (0 until a.length()).map { trackOf(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())

    private fun write(key: String, items: List<JSONObject>) =
        p.edit().putString(key, JSONArray(items).toString()).apply()

    var history: List<Track>
        get() = readTracks("history")
        private set(v) = write("history", v.take(60).map { it.toJson() })

    var liked: List<Track>
        get() = readTracks("liked")
        private set(v) = write("liked", v.map { it.toJson() })

    var saved: List<Collection>
        get() = runCatching {
            val a = JSONArray(p.getString("saved", "[]"))
            (0 until a.length()).map { collectionOf(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        private set(v) = write("saved", v.map { it.toJson() })

    val queries: List<String>
        get() = runCatching {
            val a = JSONArray(p.getString("queries", "[]"))
            (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(emptyList())

    fun addQuery(q: String) {
        p.edit().putString("queries", JSONArray((listOf(q) + queries.filter { it != q }).take(12)).toString()).apply()
    }

    /** Newest first, no duplicate of the same track from the same source. */
    fun addHistory(t: Track) {
        history = listOf(t) + history.filterNot { it.id == t.id && it.source == t.source }
    }

    fun toggleLike(t: Track): Boolean {
        val on = liked.none { it.id == t.id && it.source == t.source }
        liked = if (on) listOf(t) + liked else liked.filterNot { it.id == t.id && it.source == t.source }
        return on
    }

    fun toggleSaved(c: Collection): Boolean {
        val on = saved.none { it.id == c.id }
        saved = if (on) listOf(c) + saved else saved.filterNot { it.id == c.id }
        return on
    }

    /** Custom playlists the user names themselves. */
    var playlists: List<NamedPlaylist>
        get() = runCatching {
            val a = JSONArray(p.getString("playlists", "[]"))
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val items = o.optJSONArray("tracks") ?: JSONArray()
                NamedPlaylist(
                    name = o.optString("name"),
                    tracks = (0 until items.length()).map { trackOf(items.getJSONObject(it)) },
                )
            }
        }.getOrDefault(emptyList())
        private set(v) = p.edit().putString(
            "playlists",
            JSONArray(
                v.map { pl ->
                    JSONObject()
                        .put("name", pl.name)
                        .put("tracks", JSONArray(pl.tracks.map { it.toJson() }))
                },
            ).toString(),
        ).apply()

    fun createPlaylist(name: String): Boolean {
        if (name.isBlank() || playlists.any { it.name.equals(name, ignoreCase = true) }) return false
        playlists = playlists + NamedPlaylist(name.trim(), emptyList())
        return true
    }

    fun addToPlaylist(name: String, t: Track): Boolean {
        val target = playlists.firstOrNull { it.name == name } ?: return false
        if (target.tracks.any { it.id == t.id && it.source == t.source }) return false
        playlists = playlists.map { if (it.name == name) it.copy(tracks = it.tracks + t) else it }
        return true
    }

    fun removePlaylist(name: String) {
        playlists = playlists.filterNot { it.name == name }
    }

    /** Weekly recommendation is computed once per ISO week and reused for the rest of it. */
    fun weekly(week: String): List<Track> = readTracks("weekly:$week")

    fun setWeekly(week: String, items: List<Track>) = write("weekly:$week", items.map { it.toJson() })
}

/** A user-named playlist. */
data class NamedPlaylist(val name: String, val tracks: List<Track>)
