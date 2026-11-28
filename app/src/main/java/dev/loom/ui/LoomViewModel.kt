package dev.loom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.loom.core.Collection
import dev.loom.core.Music
import dev.loom.core.Shelf
import dev.loom.core.Source
import dev.loom.core.Downloads
import dev.loom.core.Store
import dev.loom.core.Track
import dev.loom.theme.Mode
import dev.loom.theme.Palette
import dev.loom.theme.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    DOWNLOADS("Download"),
    HISTORY("History"),
    LIKED("Liked"),
    WEEKLY("Weekly"),
    QUEUE("Queue"),
}

/** A page opened above a tab: a provider playlist, or one of the user's own (`local`). */
data class OpenPlaylist(
    val title: String,
    val tracks: List<Track>,
    val local: Boolean = false,
    /** The shelf card that opened this page, so it can be saved from inside. Null for local ones. */
    val collection: Collection? = null,
)

/** Rotating genre row on the feed. `loading` drives the grey skeleton. */
data class GenreBlock(val name: String, val tracks: List<Track>, val loading: Boolean = false)

/** Rotating mood row ("Rain + Feels", "Night Drive", ...). Same shape as [GenreBlock]. */
data class MoodBlock(val name: String, val tracks: List<Track>, val loading: Boolean = false)

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
    /** Tracks the user picked from Search; separate from `history`, which is everything played. */
    val searchPlayed: List<Track> = emptyList(),
    /** The search page is a separate screen: bar, title history, and suggestions while typing. */
    val searchOpen: Boolean = false,
    /** Titles offered while typing; from one YouTube Music lookup, not all four sources. */
    val suggest: List<String> = emptyList(),
    val lib: Lib = Lib.PLAYLISTS,
    val playlists: List<dev.loom.core.NamedPlaylist> = emptyList(),
    val lyrics: String? = null,
    /** Track the current `lyrics` belong to; null means nothing fetched yet. */
    val lyricsFor: String? = null,
    val syncedLyrics: List<dev.loom.core.LyricLine> = emptyList(),
    /** Per-track lyrics shift in seconds; survives track changes within the session. */
    val lyricOffsets: Map<String, Double> = emptyMap(),
    /** Downloaded files grouped by playlist folder; null key = loose tracks. */
    val downloads: Map<String?, List<dev.loom.core.Downloaded>> = emptyMap(),
    /** 0f..1f while a download runs, null when idle. */
    val downloadProgress: Float? = null,
    val downloading: String? = null,
    /** Live bytes/size/speed for the running download; null when idle. */
    val downloadSample: dev.loom.core.Downloads.Sample? = null,
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
    /** Flat track rows for the home screen; shelves alone read as a wall of cards. */
    val topTracks: List<Track> = emptyList(),
    val forYou: List<Shelf> = emptyList(),
    /** Radio seeded from the user's own history; the personal half of For You. */
    val forYouTracks: List<Track> = emptyList(),
    /**
     * The rotating genre block: a genre, the tracks found for it, and whether it is still loading.
     * Home and For You both read this, so a refresh on either tab shows the same block.
     */
    val genreBlock: GenreBlock? = null,
    /** Mood block: "Rain + Feels", "Night Drive", ... rotates on refresh like the genre block. */
    val moodBlock: MoodBlock? = null,
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
    private val app = app
    private val store = Store(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { observeDownloads() }

    private val _theme = MutableStateFlow(store.theme)
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

    /** Wired by Playback; ExoPlayer owns repeat and shuffle so it can loop without ending. */
    var onRepeat: ((Boolean) -> Unit)? = null
    var onShuffle: ((Boolean) -> Unit)? = null

    init {
        _state.value = _state.value.copy(
            history = store.history,
            liked = store.liked,
            saved = store.saved,
            queries = store.queries,
            searchPlayed = store.searchPlayed,
            playlists = store.playlists,
            // Restored but paused: the card is there, the audio is not.
            queue = store.queue,
            index = store.queueIndex.coerceIn(-1, store.queue.lastIndex),
        )
        // Only the visible tab loads at startup. Fetching all three meant a cold start pulled
        // hundreds of covers while the user was still on Home, which is what made the player
        // stutter and sometimes stop when a second tab was opened.
        when (_state.value.tab) {
            Tab.HOME -> loadHome()
            Tab.FOR_YOU -> loadForYou()
            else -> Unit
        }
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
            Tab.PLAYLIST -> if (_state.value.weekly.isEmpty()) loadWeekly()
            else -> Unit
        }
    }

    fun setLib(l: Lib) { _state.value = _state.value.copy(lib = l) }

    /**
     * Genre and mood blocks for the feed. Picks differ from the previous pick, so a refresh visibly
     * changes the page instead of occasionally returning the same row twice.
     *
     * `shortcut:` one genre and one mood at a time, ten tracks each. More would triple the network
     * cost of opening a tab for content that sits below the fold.
     */
    fun loadRotatingBlocks() {
        val genres = listOf("Pop Indonesia", "Hip hop", "Dangdut", "Rock", "Lo-fi", "K-pop", "Indie", "R&B")
        val moods = listOf(
            "Rain + Feels" to "hujan galau akustik",
            "Night Drive" to "night drive synth",
            "Pagi Santai" to "pagi santai akustik",
            "Fokus Kerja" to "instrumental fokus",
            "Teman Jalan" to "jalan jalan ceria",
            "Sesi Galau" to "galau melankolis",
        )
        val prevGenre = _state.value.genreBlock?.name
        val prevMood = _state.value.moodBlock?.name
        val genre = genres.filter { it != prevGenre }.random()
        val (moodName, moodQuery) = moods.filter { it.first != prevMood }.random()

        _state.value = _state.value.copy(
            genreBlock = GenreBlock(genre, emptyList(), loading = true),
            moodBlock = MoodBlock(moodName, emptyList(), loading = true),
        )
        viewModelScope.launch {
            // One source each: these are background rows, and YouTube Music already has both.
            val res = withContext(Dispatchers.IO) {
                val g = async { runCatching { Music.search(genre, Source.YT_MUSIC) }.getOrDefault(emptyList()).take(10) }
                val m = async { runCatching { Music.search(moodQuery, Source.YT_MUSIC) }.getOrDefault(emptyList()).take(10) }
                g.await() to m.await()
            }
            val (g, m) = res
            _state.value = _state.value.copy(
                genreBlock = GenreBlock(genre, g, loading = false),
                moodBlock = MoodBlock(moodName, m, loading = false),
            )
        }
    }

    fun loadHome() {
        if (_state.value.loading) return
        if (_state.value.genreBlock == null) loadRotatingBlocks()
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            // Shelves and track rows are independent; fetch them together so the screen does not
            // render in two visible passes.
            val res = withContext(Dispatchers.IO) {
                val shelves = runCatching { Music.home() }
                val top = runCatching { Music.homeTop() }.getOrDefault(emptyList())
                shelves.map { it to top }
            }
            _state.value = res.fold(
                onSuccess = { (shelves, top) ->
                    _state.value.copy(shelves = shelves, topTracks = top, loading = false)
                },
                onFailure = { _state.value.copy(loading = false, error = "Could not load home: " + it.short()) },
            )
        }
    }

    fun loadForYou() {
        loadRotatingBlocks()
        viewModelScope.launch {
            // Personal first: seed radio from the tracks actually played, then fall back to the
            // generic Explore page when there is no history yet.
            val res = withContext(Dispatchers.IO) {
                val seeds = store.history.distinctBy { it.artist }.take(4)
                // Four radios, each returning its own long list: unbounded, this page pulled
                // hundreds of tracks and their covers and took seconds to open. Twelve per seed
                // keeps the variety across artists while bounding both the fetch and the scroll.
                val personalised = seeds.map { seed ->
                    async { runCatching { Music.radio(seed) }.getOrDefault(emptyList()).take(12) }
                }.awaitAll().flatten().distinctBy { it.id }.take(40)
                personalised to runCatching { Music.forYou() }.getOrDefault(emptyList())
            }
            val (mine, generic) = res
            _state.value = _state.value.copy(forYouTracks = mine, forYou = generic)
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
        // Push the detail page before the fetch, with no tracks yet: the tap answers instantly
        // and the screen can show its own skeleton instead of a spinner over the old list.
        val tab = _state.value.tab
        _state.value = _state.value.copy(
            openPlaylist = _state.value.openPlaylist + (tab to OpenPlaylist(c.title, emptyList(), collection = c)),
            loading = true,
            error = null,
        )
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { runCatching { Music.playlist(c.id, c.params) } }
            _state.value = res.fold(
                onSuccess = { tracks ->
                    _state.value.copy(
                        openPlaylist = _state.value.openPlaylist + (tab to OpenPlaylist(c.title, tracks, collection = c)),
                        loading = false,
                    )
                },
                onFailure = {
                    // Drop the empty page again, otherwise the user is stuck on a blank detail.
                    _state.value.copy(
                        openPlaylist = _state.value.openPlaylist - tab,
                        loading = false,
                        error = "Could not open playlist: " + it.short(),
                    )
                },
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
            _state.value.copy(query = q, results = emptyList(), searched = false, suggest = emptyList())
        } else _state.value.copy(query = q)
        suggestJob?.cancel()
        if (q.isBlank() || !_state.value.searchOpen) return
        // Debounced: one lookup per pause, not one per keystroke.
        suggestJob = viewModelScope.launch {
            delay(320)
            val hits = withContext(Dispatchers.IO) {
                runCatching { Music.search(q.trim(), Source.YT_MUSIC) }.getOrDefault(emptyList())
            }
            if (_state.value.query == q) {
                _state.value = _state.value.copy(suggest = hits.map { it.title }.distinct().take(8))
            }
        }
    }

    private var suggestJob: kotlinx.coroutines.Job? = null

    fun openSearch() { _state.value = _state.value.copy(searchOpen = true) }

    fun closeSearch() {
        _state.value = _state.value.copy(searchOpen = false, query = "", suggest = emptyList(), searched = false)
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

    /**
     * A genre tap opens a real page with a grey skeleton, rather than reusing the search tab: the
     * user sees where they are going while the four sources answer.
     */
    fun openGenre(genre: String) {
        val tab = _state.value.tab
        _state.value = _state.value.copy(
            openPlaylist = _state.value.openPlaylist + (tab to OpenPlaylist(genre, emptyList())),
            searchOpen = false,
            loading = true,
            error = null,
        )
        viewModelScope.launch {
            val merged = withContext(Dispatchers.IO) {
                Source.entries.map { s -> async { runCatching { Music.search(genre, s) }.getOrDefault(emptyList()) } }
                    .awaitAll()
            }.flatten().distinctBy { it.source.name + it.id }
            _state.value = _state.value.copy(
                openPlaylist = _state.value.openPlaylist + (tab to OpenPlaylist(genre, merged)),
                loading = false,
                error = if (merged.isEmpty()) "Tidak ada hasil untuk \"$genre\"" else null,
            )
        }
    }

    fun removeQuery(q: String) {
        store.removeQuery(q)
        _state.value = _state.value.copy(queries = store.queries)
    }

    fun clearQueries() {
        store.clearQueries()
        _state.value = _state.value.copy(queries = emptyList())
    }

    /** Track whose three-dot sheet is open, or null. */
    private val _menuTrack = MutableStateFlow<Track?>(null)
    val menuTrack: StateFlow<Track?> = _menuTrack.asStateFlow()

    fun openMenu(t: Track) { _menuTrack.value = t }
    fun closeMenu() { _menuTrack.value = null }

    /** Insert after the current track, keeping the rest of the queue intact. */
    fun playNext(track: Track) {
        val s = _state.value
        val q = s.queue.toMutableList()
        if (s.index in q.indices) q.add(s.index + 1, track) else q.add(track)
        _state.value = s.copy(queue = q)
        store.saveQueue(q, s.index)
        _menuTrack.value = null
    }

    /** Append to the end without changing what is playing. */
    fun enqueue(track: Track) {
        val s = _state.value
        val q = s.queue + track
        _state.value = s.copy(queue = q)
        store.saveQueue(q, s.index)
        _menuTrack.value = null
    }

    fun saveToPlaylist(name: String, track: Track) {
        store.addToPlaylist(name, track)
        _state.value = _state.value.copy(playlists = store.playlists)
        _menuTrack.value = null
    }

    /** Share a link the receiving app can open; every source has a canonical URL. */
    fun shareLink(track: Track): String = when (track.source) {
        Source.YT_MUSIC -> "https://music.youtube.com/watch?v=${track.id}"
        Source.SPOTIFY -> "https://open.spotify.com/track/${track.id}"
        Source.DEEZER -> "https://www.deezer.com/track/${track.id}"
        Source.SOUNDCLOUD -> "https://soundcloud.com/tracks/${track.id}"
    }

    /** Open the artist by searching the name; no artist-id plumbing needed. */
    fun openArtist(track: Track) {
        _menuTrack.value = null
        if (track.artist.isBlank()) return
        _state.value = _state.value.copy(tab = Tab.SEARCH, query = track.artist)
        search()
    }

    /**
     * `byUser` is true only when the user picked this track themselves (a tap, or their own
     * next/previous). Autoplay advances with it false, which is what keeps Search's "recently
     * played" from filling up with tracks nobody chose.
     */
    fun play(track: Track, queue: List<Track> = _state.value.results, byUser: Boolean = true) {
        // Playing a single track always appends to the live queue instead of replacing it,
        // so tapping one song from search never throws away what is already lined up.
        val target = if (queue.size <= 1 && _state.value.queue.isNotEmpty()) {
            val existing = _state.value.queue
            if (existing.any { it.id == track.id && it.source == track.source }) existing
            else existing + track
        } else queue
        val idx = target.indexOf(track).coerceAtLeast(0)
        // Captured now: the resolve below is async and the user may switch tabs meanwhile.
        val fromSearch = _state.value.tab == Tab.SEARCH
        _state.value = _state.value.copy(queue = target, index = idx, resolving = true, error = null)
        store.saveQueue(target, idx)
        viewModelScope.launch {
            val stream = withContext(Dispatchers.IO) { runCatching { Music.bestStream(track) } }
            stream.fold(
                onSuccess = {
                    store.addHistory(track)
                    if (byUser && fromSearch) store.addSearchPlayed(track)
                    _state.value = _state.value.copy(
                        resolving = false,
                        playing = true,
                        history = store.history,
                        searchPlayed = store.searchPlayed,
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

    /** Refresh the Download tab from disk. Cheap: one directory listing per playlist folder. */
    fun refreshDownloads() {
        _state.value = _state.value.copy(downloads = Downloads.all(app))
    }

    /**
     * One track, optionally trimmed to [startMs]..[endMs]. A trimmed copy is a real cut file, not
     * a marker, so it still plays if the user copies it off the phone.
     */
    fun downloadTrack(t: Track, playlist: String? = null, startMs: Long = 0, endMs: Long = 0) {
        if (_state.value.downloading != null) return
        _state.value = _state.value.copy(downloading = t.title, downloadProgress = 0f)
        viewModelScope.launch {
            val res = runCatching {
                Downloads.download(
                    app, t, playlist, startMs, endMs,
                    onProgress = { p -> _state.value = _state.value.copy(downloadProgress = p) },
                    onSample = { s -> _state.value = _state.value.copy(downloadSample = s) },
                )
            }
            _state.value = _state.value.copy(
                downloading = null,
                downloadProgress = null,
                downloadSample = null,
                downloads = Downloads.all(app),
                error = res.exceptionOrNull()?.let { "Gagal mengunduh: " + it.short() },
            )
        }
    }

    /**
     * A whole playlist goes to DownloadService: a 50-track run outlives a screen, and
     * `viewModelScope` would be cancelled the moment the app is backgrounded.
     */
    fun downloadPlaylist(name: String, tracks: List<Track>) {
        dev.loom.player.DownloadService.start(app, tracks, name)
    }

    /** Mirrors the service's progress into UI state and refreshes the tab when it finishes. */
    private fun observeDownloads() {
        viewModelScope.launch {
            dev.loom.player.DownloadService.progress.collect { p ->
                _state.value = _state.value.copy(
                    downloading = p.title?.let { "${p.done + 1}/${p.total} $it" },
                    downloadProgress = p.fraction,
                    downloadSample = p.sample,
                )
            }
        }
        viewModelScope.launch {
            dev.loom.player.DownloadService.finished.collect { done ->
                if (done) _state.value = _state.value.copy(downloads = Downloads.all(app))
            }
        }
    }

    /** Plays a downloaded file directly; no stream resolve is involved. */
    fun playFile(d: dev.loom.core.Downloaded) {
        onPlayFile?.invoke(d)
    }

    fun removeDownloadFile(d: dev.loom.core.Downloaded) {
        Downloads.remove(app, d.uri)
        refreshDownloads()
    }

    /** Wired by Playback: hands a downloaded file to the player. */
    var onPlayFile: ((dev.loom.core.Downloaded) -> Unit)? = null

    fun toggleSaved(c: Collection) {
        store.toggleSaved(c)
        _state.value = _state.value.copy(saved = store.saved)
    }

    fun openPlayer() { _state.value = _state.value.copy(openPlayer = true) }
    fun closePlayer() { _state.value = _state.value.copy(openPlayer = false, lyricsFull = false) }
    fun openLyrics() { _state.value = _state.value.copy(lyricsFull = true) }
    fun closeLyrics() { _state.value = _state.value.copy(lyricsFull = false) }

    /**
     * Lyrics belong to a track, not to the player. Guarding on `lyrics != null` alone left the
     * previous song's words on screen after a skip, so the guard is keyed on the track id.
     */
    /** Shift every lyrics timestamp for the current track; lrclib edits drift by seconds. */
    fun setLyricOffset(seconds: Double) {
        val id = _state.value.current?.id ?: return
        _state.value = _state.value.copy(lyricOffsets = _state.value.lyricOffsets + (id to seconds))
    }

    fun lyricOffset(): Double = _state.value.current?.let { _state.value.lyricOffsets[it.id] } ?: 0.0

    fun loadLyrics() {
        val t = _state.value.current ?: return
        if (_state.value.lyricsFor == t.id) return
        _state.value = _state.value.copy(lyricsFor = t.id, lyrics = null, syncedLyrics = emptyList())
        viewModelScope.launch {
            val (text, synced) = withContext(Dispatchers.IO) { runCatching { Music.lyrics(t) } }
                .getOrDefault(null to emptyList())
            // A slow response for a track that is no longer playing must not overwrite the new one.
            if (_state.value.current?.id != t.id) return@launch
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

    fun setPlaylistIcon(name: String, icon: String) {
        store.setPlaylistIcon(name, icon)
        _state.value = _state.value.copy(playlists = store.playlists)
    }

    fun removePlaylist(name: String) {
        store.removePlaylist(name)
        _state.value = _state.value.copy(playlists = store.playlists)
    }

    fun renamePlaylist(old: String, new: String): Boolean {
        val ok = store.renamePlaylist(old, new)
        if (ok) {
            _state.value = _state.value.copy(
                playlists = store.playlists,
                // Keep the open page's title in step with the rename.
                openPlaylist = _state.value.openPlaylist.mapValues { (_, v) ->
                    if (v.title == old) v.copy(title = new.trim()) else v
                },
            )
        }
        return ok
    }

    /** Open a user playlist as a real page, so it gets a header, rows and its own actions. */
    fun openLocal(name: String) {
        val pl = store.playlists.firstOrNull { it.name == name } ?: return
        val tab = _state.value.tab
        _state.value = _state.value.copy(
            openPlaylist = _state.value.openPlaylist + (tab to OpenPlaylist(pl.name, pl.tracks, local = true)),
        )
    }

    fun removeFromPlaylist(t: Track) {
        val open = _state.value.openHere ?: return
        if (!open.local) return
        store.removeFromPlaylist(open.title, t)
        val fresh = store.playlists.firstOrNull { it.name == open.title }?.tracks ?: emptyList()
        val tab = _state.value.tab
        _state.value = _state.value.copy(
            playlists = store.playlists,
            openPlaylist = _state.value.openPlaylist + (tab to open.copy(tracks = fresh)),
        )
    }

    /** Create a playlist and drop the track straight into it, from the track menu. */
    fun createPlaylistWith(name: String, t: Track): Boolean {
        if (!createPlaylist(name)) return false
        store.addToPlaylist(name.trim(), t)
        _state.value = _state.value.copy(playlists = store.playlists)
        _menuTrack.value = null
        return true
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
                    val base = name.trim().ifBlank { imported.name.ifBlank {
                        "${req.source.label} · ${imported.tracks.size} tracks"
                    } }
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

    /**
     * Repeat and shuffle are delegated to ExoPlayer. Handling them in `onTrackEnded` meant the
     * player itself sat at STATE_ENDED, so the track stopped and the next play press moved on.
     */
    fun toggleShuffle() {
        val on = !_state.value.shuffle
        _state.value = _state.value.copy(shuffle = on)
        onShuffle?.invoke(on)
    }

    fun toggleRepeat() {
        val on = !_state.value.repeat
        _state.value = _state.value.copy(repeat = on)
        onRepeat?.invoke(on)
    }

    /** Push the stored repeat/shuffle state into a freshly created player. */
    fun reapplyModes() {
        onRepeat?.invoke(_state.value.repeat)
        onShuffle?.invoke(_state.value.shuffle)
    }

    /** Fired by the player whenever it actually starts or stops producing audio. */
    fun onPlayingChanged(isPlaying: Boolean) {
        if (_state.value.playing == isPlaying) return
        _state.value = _state.value.copy(playing = isPlaying)
    }

    fun togglePlay() {
        val s = _state.value
        if (s.current == null) return
        // No optimistic flip here: `onPlayingChanged` reports the real result, so a request the
        // player refuses cannot leave the button lying about the state.
        if (s.playing) onPause?.invoke() else onResume?.invoke()
    }

    /**
     * Called when the player itself moved to another item — the notification's next/prev, or an
     * automatic advance. The UI state has to follow without re-resolving the stream.
     */
    fun onPlayerMoved(t: Track, automatic: Boolean) {
        val s = _state.value
        val idx = s.queue.indexOfFirst { it.id == t.id && it.source == t.source }
        // `current` is derived from queue+index, so moving the index is what changes the track.
        _state.value = s.copy(
            index = if (idx >= 0) idx else s.index,
            playing = true,
        )
        store.saveQueue(s.queue, if (idx >= 0) idx else s.index)
        store.addHistory(t)
        if (!automatic && s.tab == Tab.SEARCH) store.addSearchPlayed(t)
        _state.value = _state.value.copy(history = store.history, searchPlayed = store.searchPlayed)
        // Lyrics are keyed to the track id, so a new item needs a new fetch.
        if (_state.value.lyricsFor != t.id) loadLyrics()
    }

    /** `byUser` false when the player advanced on its own rather than a button press. */
    fun skip(delta: Int, byUser: Boolean = true) {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val next = (s.index + delta + s.queue.size) % s.queue.size
        _state.value = s.copy(index = next)
        store.saveQueue(s.queue, next)
        play(s.queue[next], s.queue, byUser)
    }

    /** Player reached the end of an item: advance, or grow the queue when it runs out. */
    fun onTrackEnded() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        // Repeat and shuffle never reach here: ExoPlayer handles them itself, so a repeating
        // track loops without the player ever reporting STATE_ENDED.
        if (s.index < s.queue.lastIndex) {
            skip(1, byUser = false)
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
            play(grown[_state.value.index + 1], grown, byUser = false)
        }
    }

    // Every theme change is written through, so a restart keeps the look. Three small fields in
    // the same preferences file the app already owns; no schema, no migration.
    private fun saveTheme(t: ThemeState) {
        store.theme = t
        _theme.value = t
    }

    fun setPalette(p: Palette) { saveTheme(_theme.value.copy(palette = p, customSeed = null)) }
    fun setMode(m: Mode) { saveTheme(_theme.value.copy(mode = m)) }
    fun setCustomSeed(seed: Long?) { saveTheme(_theme.value.copy(customSeed = seed)) }
}
