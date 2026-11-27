package dev.loom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.loom.core.Collection
import dev.loom.core.Music
import dev.loom.core.Shelf
import dev.loom.core.Source
import dev.loom.core.Store
import dev.loom.core.Track
import dev.loom.theme.Mode
import dev.loom.theme.Palette
import dev.loom.theme.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** org.json puts the whole payload in its message; never let that reach the screen. */
private fun Throwable.short(): String =
    (message ?: "something went wrong").lineSequence().first().take(120)

/** Bottom navigation, in display order. */
enum class Tab(val label: String) {
    PLAYLIST("Playlist"),
    FOR_YOU("For You"),
    HOME("Home"),
    SEARCH("Search"),
    SETTINGS("Settings"),
}

/** Tabs inside Playlist: saved collections, history, likes, weekly picks, the live queue. */
enum class Lib(val label: String) {
    PLAYLISTS("Playlist"),
    HISTORY("History"),
    LIKED("Liked"),
    WEEKLY("Weekly"),
    QUEUE("Queue"),
}

/** A playlist the user opened from home / for-you. */
data class OpenPlaylist(val title: String, val tracks: List<Track>)

data class UiState(
    val tab: Tab = Tab.HOME,
    val query: String = "",
    val results: List<Track> = emptyList(),
    val searched: Boolean = false,
    val history: List<Track> = emptyList(),
    val liked: List<Track> = emptyList(),
    val saved: List<Collection> = emptyList(),
    val weekly: List<Track> = emptyList(),
    val queries: List<String> = emptyList(),
    val lib: Lib = Lib.PLAYLISTS,
    val playlists: List<dev.loom.core.NamedPlaylist> = emptyList(),
    val lyrics: String? = null,
    val syncedLyrics: List<dev.loom.core.LyricLine> = emptyList(),
    /** Equalizer preset name; applied by the player service. */
    val eq: String = "Flat",
    /** Lyrics take over the whole screen instead of swapping into the cover box. */
    val lyricsFull: Boolean = false,
    val openPlayer: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val shelves: List<Shelf> = emptyList(),
    val forYou: List<Shelf> = emptyList(),
    /** Keyed by tab: a detail page opened from For You must not appear on Home. */
    val openPlaylist: Map<Tab, OpenPlaylist> = emptyMap(),
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val resolving: Boolean = false,
) {
    val current: Track? get() = queue.getOrNull(index)
    /** The detail page for the tab currently shown, if any. */
    val openHere: OpenPlaylist? get() = openPlaylist[tab]
}

class LoomViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _theme = MutableStateFlow(ThemeState())
    val theme: StateFlow<ThemeState> = _theme.asStateFlow()

    /** Called by the UI when a stream resolves; the player lives in PlaybackService. */
    var onPlay: ((Track, dev.loom.core.Stream) -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onResume: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null
    /** Wired by Playback; fires when the player reaches the end of the current item. */
    var onEnded: (() -> Unit)? = null
    /** Wired by Playback; applies an equalizer preset to the live audio session. */
    var onEq: ((String) -> Unit)? = null

    init {
        _state.value = _state.value.copy(
            history = store.history,
            liked = store.liked,
            saved = store.saved,
            queries = store.queries,
            playlists = store.playlists,
            // Restored but paused: the card is there, the audio is not.
            queue = store.queue,
            index = store.queueIndex.coerceIn(-1, store.queue.lastIndex),
        )
        loadHome()
        loadForYou()
        loadWeekly()
    }

    fun setTab(t: Tab) {
        val s = _state.value
        // Tapping the tab you are already on goes back to its root, the way every
        // bottom-nav app behaves. Switching tabs keeps each tab's detail page intact.
        _state.value = if (s.tab == t && s.openPlaylist.containsKey(t)) {
            s.copy(openPlaylist = s.openPlaylist - t)
        } else {
            s.copy(tab = t)
        }
        when (t) {
            Tab.HOME -> if (_state.value.shelves.isEmpty()) loadHome()
            Tab.FOR_YOU -> if (_state.value.forYou.isEmpty()) loadForYou()
            else -> Unit
        }
    }

    fun setLib(l: Lib) { _state.value = _state.value.copy(lib = l) }

    fun loadHome() {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { runCatching { Music.home() } }
            _state.value = res.fold(
                onSuccess = { _state.value.copy(shelves = it, loading = false) },
                onFailure = { _state.value.copy(loading = false, error = "Could not load home: " + it.short()) },
            )
        }
    }

    fun loadForYou() {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { runCatching { Music.forYou() } }
            res.onSuccess { _state.value = _state.value.copy(forYou = it) }
        }
    }

    /**
     * Weekly picks are rebuilt once per ISO week from the artists the user actually played,
     * falling back to new releases on a cold install. Cached so a reopen is instant.
     */
    private fun loadWeekly() {
        val week = LocalDate.now().format(DateTimeFormatter.ofPattern("YYYY-ww", Locale.US))
        val cached = store.weekly(week)
        if (cached.isNotEmpty()) {
            _state.value = _state.value.copy(weekly = cached)
            return
        }
        viewModelScope.launch {
            val seeds = withContext(Dispatchers.IO) {
                runCatching {
                    val artists = store.history.map { it.artist }.filter { it.isNotBlank() }.distinct().take(5)
                    if (artists.isEmpty()) {
                        Music.forYou().flatMap { it.items }.mapNotNull {
                            it.subtitle.substringBefore(" • ").takeIf { s -> s.isNotBlank() }
                        }.distinct().take(5)
                    } else artists
                }.getOrDefault(emptyList())
            }
            val picks = withContext(Dispatchers.IO) {
                seeds.map { seed ->
                    async { runCatching { Music.search(seed, Source.YT_MUSIC) }.getOrDefault(emptyList()) }
                }.awaitAll().flatten().distinctBy { it.id }.take(30)
            }
            if (picks.isEmpty()) return@launch
            store.setWeekly(week, picks)
            _state.value = _state.value.copy(weekly = picks)
        }
    }

    fun openCollection(c: Collection) {
        if (c.kind == "track") {
            play(Track(c.id, c.title, c.subtitle.substringBefore(" • "), c.art, Source.YT_MUSIC))
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { runCatching { Music.playlist(c.id, c.params) } }
            _state.value = res.fold(
                onSuccess = {
                    val tab = _state.value.tab
                    _state.value.copy(
                        openPlaylist = _state.value.openPlaylist + (tab to OpenPlaylist(c.title, it)),
                        loading = false,
                    )
                },
                onFailure = { _state.value.copy(loading = false, error = "Could not open playlist: " + it.short()) },
            )
        }
    }

    fun closePlaylist() {
        val tab = _state.value.tab
        _state.value = _state.value.copy(openPlaylist = _state.value.openPlaylist - tab)
    }

    fun setQuery(q: String) {
        // Clearing the box returns to the history/recent view instead of a stale result list.
        _state.value = if (q.isBlank()) {
            _state.value.copy(query = q, results = emptyList(), searched = false)
        } else _state.value.copy(query = q)
    }

    /**
     * One search box, all three sources at once. YouTube Music answers first and usually wins,
     * so its rows lead the list; the other two fill in whatever it missed.
     */
    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        store.addQuery(q)
        _state.value = _state.value.copy(
            loading = true,
            error = null,
            queries = store.queries,
        )
        viewModelScope.launch {
            val perSource = withContext(Dispatchers.IO) {
                Source.entries.map { s -> async { runCatching { Music.search(q, s) }.getOrDefault(emptyList()) } }
                    .awaitAll()
            }
            val merged = perSource.flatten().distinctBy { it.source.name + it.id }
            _state.value = _state.value.copy(
                results = merged,
                loading = false,
                searched = true,
                error = if (merged.isEmpty()) "Nothing found for \"$q\"" else null,
            )
        }
    }

    fun playQuery(q: String) {
        _state.value = _state.value.copy(query = q)
        search()
    }

    fun play(track: Track, queue: List<Track> = _state.value.results) {
        // Playing a single track always appends to the live queue instead of replacing it,
        // so tapping one song from search never throws away what is already lined up.
        val target = if (queue.size <= 1 && _state.value.queue.isNotEmpty()) {
            val existing = _state.value.queue
            if (existing.any { it.id == track.id && it.source == track.source }) existing
            else existing + track
        } else queue
        val idx = target.indexOf(track).coerceAtLeast(0)
        _state.value = _state.value.copy(queue = target, index = idx, resolving = true, error = null)
        store.saveQueue(target, idx)
        viewModelScope.launch {
            val stream = withContext(Dispatchers.IO) { runCatching { Music.bestStream(track) } }
            stream.fold(
                onSuccess = {
                    store.addHistory(track)
                    _state.value = _state.value.copy(
                        resolving = false,
                        playing = true,
                        history = store.history,
                    )
                    onPlay?.invoke(track, it)
                },
                onFailure = {
                    _state.value = _state.value.copy(resolving = false, error = "Could not play: " + it.short())
                },
            )
        }
    }

    fun toggleLike(track: Track) {
        store.toggleLike(track)
        _state.value = _state.value.copy(liked = store.liked)
    }

    fun isLiked(track: Track): Boolean = _state.value.liked.any { it.id == track.id && it.source == track.source }

    fun toggleSaved(c: Collection) {
        store.toggleSaved(c)
        _state.value = _state.value.copy(saved = store.saved)
    }

    fun openPlayer() { _state.value = _state.value.copy(openPlayer = true) }
    fun closePlayer() { _state.value = _state.value.copy(openPlayer = false, lyricsFull = false) }
    fun openLyrics() { _state.value = _state.value.copy(lyricsFull = true) }
    fun closeLyrics() { _state.value = _state.value.copy(lyricsFull = false) }

    /** Lyrics are fetched on demand and cached per track id, so the toggle is instant after. */
    fun loadLyrics() {
        val t = _state.value.current ?: return
        if (_state.value.lyrics != null) return
        viewModelScope.launch {
            val (text, synced) = withContext(Dispatchers.IO) { runCatching { Music.lyrics(t) } }
                .getOrDefault(null to emptyList())
            _state.value = _state.value.copy(lyrics = text, syncedLyrics = synced)
        }
    }

    fun setEq(name: String) {
        _state.value = _state.value.copy(eq = name)
        onEq?.invoke(name)
    }

    fun createPlaylist(name: String): Boolean {
        val ok = store.createPlaylist(name)
        if (ok) _state.value = _state.value.copy(playlists = store.playlists)
        return ok
    }

    fun addToPlaylist(name: String, t: Track) {
        store.addToPlaylist(name, t)
        _state.value = _state.value.copy(playlists = store.playlists)
    }

    fun removePlaylist(name: String) {
        store.removePlaylist(name)
        _state.value = _state.value.copy(playlists = store.playlists)
    }

    /** Import state, kept separate so the library list never flickers during a fetch. */
    data class Importing(
        val source: Source = Source.SPOTIFY,
        val input: String = "",
        val busy: Boolean = false,
        val error: String? = null,
        val done: String? = null,
    )

    private val _import = MutableStateFlow(Importing())
    val importing: StateFlow<Importing> = _import.asStateFlow()

    fun setImportSource(s: Source) { _import.value = _import.value.copy(source = s, error = null, done = null) }
    fun setImportInput(v: String) { _import.value = _import.value.copy(input = v, error = null, done = null) }
    fun closeImport() { _import.value = Importing() }

    /**
     * Pull a playlist and save it as a named playlist. The name comes from the user when they
     * gave one, otherwise from the provider and the track count, so an import never lands as
     * an unlabelled blob.
     */
    fun runImport(name: String) {
        val req = _import.value
        if (req.input.isBlank() || req.busy) return
        _import.value = req.copy(busy = true, error = null, done = null)
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { Music.importPlaylist(req.source, req.input) }
            }
            res.fold(
                onSuccess = { imported ->
                    val base = name.trim().ifBlank {
                        "${req.source.label} · ${imported.tracks.size} tracks"
                    }
                    // Pick a free name first, then create once — createPlaylist refuses duplicates.
                    var final = base
                    var n = 2
                    while (store.playlists.any { it.name.equals(final, ignoreCase = true) }) {
                        final = "$base ($n)"
                        n++
                    }
                    store.createPlaylist(final)
                    val added = store.addAllToPlaylist(final, imported.tracks)
                    _state.value = _state.value.copy(playlists = store.playlists, lib = Lib.PLAYLISTS)
                    _import.value = _import.value.copy(
                        busy = false,
                        done = "$added tracks imported into $final",
                        input = "",
                    )
                },
                onFailure = {
                    _import.value = _import.value.copy(busy = false, error = it.short())
                },
            )
        }
    }

    fun toggleShuffle() { _state.value = _state.value.copy(shuffle = !_state.value.shuffle) }

    /** Repeat replays the same track on end instead of advancing. */
    fun toggleRepeat() { _state.value = _state.value.copy(repeat = !_state.value.repeat) }

    fun togglePlay() {
        val s = _state.value
        if (s.current == null) return
        if (s.playing) { onPause?.invoke(); _state.value = s.copy(playing = false) }
        else { onResume?.invoke(); _state.value = s.copy(playing = true) }
    }

    fun skip(delta: Int) {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val next = (s.index + delta + s.queue.size) % s.queue.size
        _state.value = s.copy(index = next)
        store.saveQueue(s.queue, next)
        play(s.queue[next], s.queue)
    }

    /** Player reached the end of an item: advance, or grow the queue when it runs out. */
    fun onTrackEnded() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        if (s.repeat) {
            play(s.queue[s.index], s.queue)
            return
        }
        if (s.shuffle && s.queue.size > 1) {
            // Never repeat the track that just ended.
            val next = s.queue.indices.filter { it != s.index }.random()
            _state.value = s.copy(index = next)
            play(s.queue[next], s.queue)
            return
        }
        if (s.index < s.queue.lastIndex) {
            skip(1)
        } else {
            extendQueue()
        }
    }

    /**
     * Autoplay. YouTube Music has a real radio endpoint keyed on the track, so its queue keeps
     * the same artist and genre. Spotify and SoundCloud have no radio here, so fall back to a
     * search on the artist name.
     */
    private fun extendQueue() {
        val seed = _state.value.queue.lastOrNull() ?: return
        _state.value = _state.value.copy(resolving = true)
        viewModelScope.launch {
            val more = withContext(Dispatchers.IO) {
                runCatching {
                    if (seed.source == Source.YT_MUSIC) Music.radio(seed)
                    else Music.search(seed.artist.ifBlank { seed.title }, seed.source)
                }.getOrDefault(emptyList())
            }
            val seen = _state.value.queue.map { it.source.name + it.id }.toSet()
            val fresh = more.filter { (it.source.name + it.id) !in seen }
            if (fresh.isEmpty()) {
                _state.value = _state.value.copy(resolving = false)
                return@launch
            }
            val grown = _state.value.queue + fresh
            _state.value = _state.value.copy(queue = grown, resolving = false)
            store.saveQueue(grown, _state.value.index + 1)
            play(grown[_state.value.index + 1], grown)
        }
    }

    fun setPalette(p: Palette) { _theme.value = _theme.value.copy(palette = p, customSeed = null) }
    fun setMode(m: Mode) { _theme.value = _theme.value.copy(mode = m) }
    fun setCustomSeed(seed: Long?) { _theme.value = _theme.value.copy(customSeed = seed) }
}
