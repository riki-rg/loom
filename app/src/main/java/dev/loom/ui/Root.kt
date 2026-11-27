package dev.loom.ui

import androidx.activity.compose.BackHandler
import android.content.Intent
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.fillMaxHeight
import dev.loom.core.NamedPlaylist
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import dev.loom.core.Music
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.loom.player.Playback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.loom.core.Collection
import dev.loom.core.Source
import dev.loom.core.Track
import dev.loom.theme.Mode
import dev.loom.theme.Palette
import dev.loom.theme.ThemeState

@Composable
fun Root(vm: LoomViewModel) {
    val state by vm.state.collectAsState()
    val theme by vm.theme.collectAsState()
    val accent = MaterialTheme.colorScheme.primary
    val accent2 = MaterialTheme.colorScheme.secondary
    val haze = remember { HazeState() }

    // Media3 has no position callback, so the position is polled. This used to run only while the
    // sheet was open, which froze the pill's progress line until the user expanded it.
    var position by remember { mutableStateOf(0L to 0L) }
    LaunchedEffect(state.current?.id, state.playing, state.openPlayer, state.lyricsFull) {
        while (state.current != null && (state.playing || state.openPlayer || state.lyricsFull)) {
            position = Playback.position()
            kotlinx.coroutines.delay(500)
        }
        // Paused with everything closed: one last read so the line lands where playback stopped.
        position = Playback.position()
    }

    // Opening the sheet is also when lyrics are worth fetching.
    LaunchedEffect(state.openPlayer, state.current?.id) {
        if (state.openPlayer) vm.loadLyrics()
    }

    // Hardware back must unwind the same stack the UI shows, in this order: lyrics page,
    // player sheet, detail page, and only then leave the app.
    BackHandler(enabled = state.lyricsFull) { vm.closeLyrics() }
    BackHandler(enabled = !state.lyricsFull && state.openPlayer) { vm.closePlayer() }
    BackHandler(enabled = !state.lyricsFull && !state.openPlayer && state.openHere != null) {
        vm.closePlaylist()
    }

    // One SaveableStateHolder keeps each tab's scroll offset, search text and expanded rows
    // alive while it is off screen, so returning to a tab resumes where it was.
    val tabStates = rememberSaveableStateHolder()

    Box(Modifier.fillMaxSize().background(ambientBrush(accent, accent2))) {
        // hazeSource marks this subtree as the thing the glass chrome blurs.
        Box(Modifier.fillMaxSize().statusBarsPadding().hazeSource(haze)) {
            tabStates.SaveableStateProvider(state.tab.name) {
                val open = state.openHere
                if (open != null) {
                    PlaylistDetail(
                        state = state,
                        onBack = vm::closePlaylist,
                        onPlay = vm::play,
                        onLike = vm::toggleLike,
                        onRemoveTrack = vm::removeFromPlaylist,
                        onRename = { vm.renamePlaylist(open.title, it) },
                        onDelete = { vm.removePlaylist(open.title); vm.closePlaylist() },
                    )
                } else {
                    when (state.tab) {
                        Tab.HOME -> HomeScreen(state, vm::openCollection, vm::play, vm::toggleLike, vm::openMenu)
                        Tab.FOR_YOU -> ForYouScreen(state, vm::openCollection, vm::play, vm::toggleLike, vm::openMenu)
                        Tab.PLAYLIST -> {
                            val imp by vm.importing.collectAsState()
                            QueueScreen(
                                state,
                                vm::setLib,
                                vm::openCollection,
                                { t, q -> vm.play(t, q) },
                                vm::toggleLike,
                                vm::playQuery,
                                vm::createPlaylist,
                                vm::removePlaylist,
                                vm::openLocal,
                                imp,
                                vm::setImportSource,
                                vm::setImportInput,
                                vm::runImport,
                                vm::closeImport,
                            )
                        }
                        Tab.SEARCH -> SearchScreen(
                            state,
                            vm::setQuery,
                            vm::search,
                            vm::playQuery,
                            { t -> vm.play(t) },
                            vm::toggleLike,
                            vm::removeQuery,
                            vm::clearQueries,
                        )
                        Tab.SETTINGS -> SettingsScreen(vm, theme)
                    }
                }
            }
        }

        // Chrome floats over the content: pill above the nav, both clear of the gesture bar.
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.error?.let { msg ->
                Box(Modifier.padding(horizontal = 16.dp).glass(haze, corner = 18)) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            NowPlayingPill(
                state = state,
                haze = haze,
                progress = if (position.second > 0) position.first.toFloat() / position.second else 0f,
                onToggle = vm::togglePlay,
                onSkip = vm::skip,
            ) { vm.openPlayer() }
            FloatingNav(state.tab, haze) { vm.setTab(it) }
        }

        // Full player slides over everything, including the nav.
        AnimatedVisibility(
            visible = state.openPlayer && !state.lyricsFull,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(180)),
        ) {
            NowPlayingSheet(
                state = state,
                lyrics = state.lyrics,
                playlists = state.playlists,
                positionMs = position.first,
                durationMs = position.second,
                liked = state.current?.let { vm.isLiked(it) } ?: false,
                shuffle = state.shuffle,
                repeat = state.repeat,
                eq = state.eq,
                onShuffle = vm::toggleShuffle,
                onRepeat = vm::toggleRepeat,
                onEq = vm::setEq,
                onLyrics = vm::openLyrics,
                onClose = vm::closePlayer,
                onToggle = vm::togglePlay,
                onSkip = vm::skip,
                onSeek = { Playback.seek(it) },
                onSeekBy = { Playback.seekBy(it) },
                onLike = { state.current?.let { vm.toggleLike(it) } },
                onLikeTrack = { vm.toggleLike(it) },
                onPlayTrack = { vm.play(it, state.queue) },
                onAddToPlaylist = { name -> state.current?.let { vm.addToPlaylist(name, it) } },
                onCreatePlaylist = { name ->
                    vm.createPlaylist(name)
                    state.current?.let { vm.addToPlaylist(name, it) }
                },
            )
        }

        // Track actions, opened from any three-dot button.
        val menu by vm.menuTrack.collectAsState()
        // Set when "Buat playlist baru" is tapped: the track waiting for a new home.
        var newPl by remember { mutableStateOf<Track?>(null) }
        var newPlName by remember { mutableStateOf("") }
        menu?.let { t ->
            val ctx = LocalContext.current
            TrackActionsSheet(
                track = t,
                liked = vm.isLiked(t),
                playlists = state.playlists.map { it.name },
                onDismiss = vm::closeMenu,
                onPlayNext = { vm.playNext(t) },
                onQueue = { vm.enqueue(t) },
                onLike = { vm.toggleLike(t); vm.closeMenu() },
                onSaveTo = { name -> vm.saveToPlaylist(name, t) },
                onCreate = { newPl = t },
                onShare = {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, vm.shareLink(t))
                    }
                    ctx.startActivity(Intent.createChooser(i, "Bagikan"))
                    vm.closeMenu()
                },
                onArtist = { vm.openArtist(t) },
            )
        }

        newPl?.let { t ->
            AlertDialog(
                onDismissRequest = { newPl = null; newPlName = "" },
                title = { Text("Playlist baru") },
                text = {
                    OutlinedTextField(
                        value = newPlName,
                        onValueChange = { newPlName = it },
                        singleLine = true,
                        placeholder = { Text("Nama playlist") },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.createPlaylistWith(newPlName, t)
                        newPl = null
                        newPlName = ""
                    }) { Text("Buat") }
                },
                dismissButton = {
                    TextButton(onClick = { newPl = null; newPlName = "" }) { Text("Batal") }
                },
            )
        }

        // Lyrics get their own page, above the player, with the same back behaviour.
        AnimatedVisibility(
            visible = state.lyricsFull,
            enter = slideInVertically(tween(300)) { it / 3 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(260)) { it / 3 } + fadeOut(tween(160)),
        ) {
            state.current?.let { t ->
                LyricsScreen(
                    track = t,
                    lines = state.syncedLyrics,
                    plain = state.lyrics,
                    positionMs = position.first,
                    durationMs = position.second,
                    playing = state.playing,
                    onClose = vm::closeLyrics,
                    onToggle = vm::togglePlay,
                    onSkip = vm::skip,
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: UiState,
    onOpen: (Collection) -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onLike: (Track) -> Unit,
    onMenu: (Track) -> Unit,
) {
    when {
        state.loading && state.shelves.isEmpty() -> CenterSpinner()
        state.shelves.isEmpty() && state.topTracks.isEmpty() ->
            EmptyState("Nothing to show", "Could not reach YouTube Music.")
        else -> LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 190.dp)) {
            item { BrandHeader("Loom", "Home") }
            // Tracks before cards: the first screen should be playable, not a wall of covers.
            if (state.topTracks.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Mix lagu", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onPlay(state.topTracks.first(), state.topTracks) }) {
                            Text("Putar semua")
                        }
                    }
                }
                itemsIndexed(state.topTracks, key = { _, t -> "ht" + t.id }) { i, t ->
                    NumberedTrackRow(i + 1, t, state.current?.id == t.id, onPlay = { onPlay(t, state.topTracks) }, onLike = { onLike(t) }, onMenu = { onMenu(t) })
                }
            }
            items(state.shelves, key = { "hs" + it.title }) { shelf -> ShelfRow(shelf.title, shelf.items, onOpen) }
        }
    }
}

@Composable
private fun ForYouScreen(
    state: UiState,
    onOpen: (Collection) -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onLike: (Track) -> Unit,
    onMenu: (Track) -> Unit,
) {
    if (state.forYou.isEmpty() && state.forYouTracks.isEmpty()) {
        CenterSpinner()
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 190.dp)) {
        item { BrandHeader("For You", "Built from what you listen to") }
        // Personal rows lead: they are the reason this tab exists.
        if (state.forYouTracks.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("For you today", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onPlay(state.forYouTracks.first(), state.forYouTracks) }) {
                        Text("Putar semua")
                    }
                }
            }
            itemsIndexed(state.forYouTracks, key = { _, t -> "fy" + t.id }) { i, t ->
                NumberedTrackRow(i + 1, t, state.current?.id == t.id, onPlay = { onPlay(t, state.forYouTracks) }, onLike = { onLike(t) }, onMenu = { onMenu(t) })
            }
        }
        items(state.forYou, key = { "fs" + it.title }) { shelf -> ShelfRow(shelf.title, shelf.items, onOpen) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueueScreen(
    state: UiState,
    onLib: (Lib) -> Unit,
    onOpen: (Collection) -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onLike: (Track) -> Unit,
    onPlayQuery: (String) -> Unit,
    onCreatePlaylist: (String) -> Boolean,
    onRemovePlaylist: (String) -> Unit,
    onOpenLocal: (String) -> Unit,
    importState: LoomViewModel.Importing,
    onImportSource: (Source) -> Unit,
    onImportInput: (String) -> Unit,
    onImportRun: (String) -> Unit,
    onImportClose: () -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importName by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 190.dp)) {
        item { BrandHeader("Library", "Yours, kept on this device") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Lib.entries) { l -> Chip(l.label, l == state.lib, onClick = { onLib(l) }) }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        when (state.lib) {
            Lib.PLAYLISTS -> {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("My playlists", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { importing = true }) {
                            Icon(Icons.Rounded.FileDownload, contentDescription = "Import playlist")
                        }
                        IconButton(onClick = { naming = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "New playlist")
                        }
                    }
                }
                if (state.playlists.isEmpty()) {
                    item { EmptyState("Belum ada playlist", "Tap + untuk membuat, atau ikon unduh untuk impor.") }
                } else {
                    items(state.playlists, key = { "p" + it.name }) { pl ->
                        LocalPlaylistCard(
                            pl,
                            onOpen = { onOpenLocal(pl.name) },
                            onPlay = { pl.tracks.firstOrNull()?.let { t -> onPlay(t, pl.tracks) } },
                            onDelete = { onRemovePlaylist(pl.name) },
                        )
                    }
                }
                if (state.saved.isNotEmpty()) {
                    item { Spacer(Modifier.height(18.dp)) }
                    item { SectionTitle("Saved from Home", state.saved.size) }
                    items(state.saved, key = { "s" + it.id }) { c -> CollectionCard(c) { onOpen(c) } }
                }
            }
            Lib.HISTORY -> if (state.history.isEmpty()) {
                item { EmptyState("Nothing played yet", "Anything you play lands here.") }
            } else {
                items(state.history, key = { "h" + it.source.name + it.id }) { t ->
                    TrackRow(t, state.current?.id == t.id, onLike = { onLike(t) }) { onPlay(t, state.history) }
                }
            }
            Lib.LIKED -> if (state.liked.isEmpty()) {
                item { EmptyState("No likes yet", "Tap the heart on any row.") }
            } else {
                items(state.liked, key = { "l" + it.source.name + it.id }) { t ->
                    TrackRow(t, state.current?.id == t.id, onLike = { onLike(t) }) { onPlay(t, state.liked) }
                }
            }
            Lib.WEEKLY -> if (state.weekly.isEmpty()) {
                item { CenterSpinner() }
            } else {
                item { Caption("Built from the artists you played this week") }
                items(state.weekly, key = { "w" + it.source.name + it.id }) { t ->
                    TrackRow(t, state.current?.id == t.id, onLike = { onLike(t) }) { onPlay(t, state.weekly) }
                }
            }
            Lib.QUEUE -> if (state.queue.isEmpty()) {
                item { EmptyState("No queue yet", "Open a playlist from Home, or play a search result.") }
            } else {
                items(state.queue, key = { "q" + it.source.name + it.id }) { t ->
                    TrackRow(t, state.current?.id == t.id, onLike = { onLike(t) }) { onPlay(t, state.queue) }
                }
            }
        }
    }

    if (importing) {
        AlertDialog(
            onDismissRequest = { importing = false; importName = ""; onImportClose() },
            title = { Text("Import playlist") },
            text = {
                Column {
                    Text(
                        "Paste a playlist link. Spotify, YouTube Music, Deezer and SoundCloud all work.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    // Four providers no longer fit one row on a narrow screen; wrap instead of
                    // shrinking the labels.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Music.importable.forEach { s ->
                            Chip(s.label, s == importState.source, onClick = { onImportSource(s) })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = importState.input,
                        onValueChange = onImportInput,
                        placeholder = {
                            Text(
                                when (importState.source) {
                                    Source.SPOTIFY -> "open.spotify.com/playlist/…"
                                    Source.DEEZER -> "deezer.com/playlist/…"
                                    Source.SOUNDCLOUD -> "soundcloud.com/artist/sets/…"
                                    else -> "music.youtube.com/playlist?list=…"
                                },
                            )
                        },
                        singleLine = true,
                        enabled = !importState.busy,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = importName,
                        onValueChange = { importName = it },
                        placeholder = { Text("Name (optional)") },
                        singleLine = true,
                        enabled = !importState.busy,
                    )
                    importState.error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    importState.done?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = importState.input.isNotBlank() && !importState.busy,
                    onClick = { onImportRun(importName) },
                ) {
                    if (importState.busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Import")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { importing = false; importName = ""; onImportClose() }) { Text("Close") }
            },
        )
    }

    if (naming) {
        AlertDialog(
            onDismissRequest = { naming = false; draft = "" },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Name it anything") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = draft.isNotBlank(), onClick = {
                    onCreatePlaylist(draft)
                    draft = ""
                    naming = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { naming = false; draft = "" }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PlaylistDetail(
    state: UiState,
    onBack: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onLike: (Track) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val open = state.openHere ?: return
    val title = open.title
    val tracks = open.tracks
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(title) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.weight(1f))
            if (open.local) {
                IconButton(onClick = { draft = title; renaming = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Rename playlist")
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete playlist")
                }
            }
        }
        if (state.loading && tracks.isEmpty()) {
            // Title already known from the tapped card, so the header is real, not a placeholder.
            PlaylistHero(title, tracks, null)
            TrackSkeleton()
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                item { PlaylistHero(title, tracks) { onPlay(tracks.first(), tracks) } }
                if (tracks.isEmpty()) {
                    item {
                        EmptyState(
                            if (open.local) "Playlist masih kosong" else "No tracks",
                            if (open.local) "Tambahkan lagu lewat menu titik tiga di tiap lagu."
                            else "This playlist returned nothing.",
                        )
                    }
                }
                itemsIndexed(tracks, key = { _, t -> t.source.name + t.id }) { i, t ->
                    NumberedTrackRow(
                        rank = i + 1,
                        t = t,
                        selected = state.current?.id == t.id,
                        onPlay = { onPlay(t, tracks) },
                        onLike = { onLike(t) },
                        onMenu = { onLike(t) },
                        // Only a playlist the user owns can lose a track.
                        onRemove = if (open.local) ({ onRemoveTrack(t) }) else null,
                    )
                }
            }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Ganti nama playlist") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text("Nama playlist") },
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(draft); renaming = false }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Batal") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus \"$title\"?") },
            text = { Text("Playlist dan ${tracks.size} lagu di dalamnya akan hilang.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}

/**
 * Header for a playlist page: a 2x2 mosaic of the first four covers, the name, the count and a
 * play button. A bare title line gave no sense of what was inside.
 */
@Composable
private fun PlaylistHero(title: String, tracks: List<Track>, onPlayAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        MosaicCover(tracks, 104.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (tracks.isEmpty()) "Kosong" else "${tracks.size} lagu",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onPlayAll != null && tracks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onPlayAll,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Putar")
                }
            }
        }
    }
}

/** One cover when there is one, a 2x2 grid of the first four when there are more. */
@Composable
fun MosaicCover(tracks: List<Track>, size: androidx.compose.ui.unit.Dp) {
    val arts = tracks.mapNotNull { it.art.takeIf(String::isNotBlank) }.distinct().take(4)
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        when (arts.size) {
            0 -> Icon(
                Icons.Rounded.QueueMusic,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(size / 3),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            1, 2, 3 -> AsyncImage(
                model = arts.first(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    arts.take(2).forEach { a ->
                        AsyncImage(
                            model = a,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
                Row(Modifier.weight(1f)) {
                    arts.drop(2).forEach { a ->
                        AsyncImage(
                            model = a,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A playlist row in the library: cover, name, count and a play button. Tapping the body opens the
 * playlist so its tracks are visible; the old row jumped straight to playback with no preview.
 */
@Composable
private fun LocalPlaylistCard(
    pl: NamedPlaylist,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MosaicCover(pl.tracks, 60.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                pl.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (pl.tracks.isEmpty()) "Kosong" else "${pl.tracks.size} lagu",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (pl.tracks.isNotEmpty()) {
            IconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.PlayCircle,
                    contentDescription = "Putar ${pl.name}",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Playlist menu",
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Buka") },
                    onClick = { menu = false; onOpen() },
                )
                DropdownMenuItem(
                    text = { Text("Hapus playlist") },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

/**
 * Row-shaped placeholders shown while a detail page loads. Grey blocks in the real row geometry
 * beat a centred spinner: the screen answers the tap immediately and nothing jumps when the
 * data lands, because the placeholder already occupies the final layout.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackSkeleton() {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonPulse",
    )
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f * (alpha / 0.5f))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Artwork + two text lines: exactly the TrackRow footprint.
        repeat(7) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).background(block, RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.62f).height(13.dp).background(block, RoundedCornerShape(4.dp)))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.38f).height(11.dp).background(block, RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}

/** A track row with a rank number and a three-dot menu, for the playable lists on Home/For You. */
@Composable
fun NumberedTrackRow(
    rank: Int,
    t: Track,
    selected: Boolean,
    onPlay: () -> Unit,
    onLike: () -> Unit,
    onMenu: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlay)
            .padding(start = 6.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        AsyncImage(
            model = t.art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOfNotNull(t.artist.takeIf { it.isNotBlank() }, t.source.label).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onLike, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.Rounded.FavoriteBorder,
                contentDescription = "Like",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMenu, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "More",
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Rounded.RemoveCircleOutline,
                    contentDescription = "Remove from playlist",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Track actions. Covers what the app can actually do — play next, queue, save to a playlist,
 * like, share, open the artist — instead of mirroring YouTube's menu, whose Download and
 * Credits rows would be dead ends here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    track: Track,
    liked: Boolean,
    playlists: List<String>,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onLike: () -> Unit,
    onSaveTo: (String) -> Unit,
    onCreate: () -> Unit,
    onShare: () -> Unit,
    onArtist: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        TrackActionsBody(track, liked, playlists, onDismiss, onPlayNext, onQueue, onLike, onSaveTo, onCreate, onShare, onArtist)
    }
}

/** Sheet contents without the sheet chrome, so it can be rendered in a test without a window. */
@Composable
fun TrackActionsBody(
    track: Track,
    liked: Boolean,
    playlists: List<String>,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onLike: () -> Unit,
    onSaveTo: (String) -> Unit,
    onCreate: () -> Unit,
    onShare: () -> Unit,
    onArtist: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = track.art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        track.artist.ifBlank { track.source.label },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
            }
            Spacer(Modifier.height(8.dp))
            // Weighted, not fixed width: three 104dp tiles overflow a 300dp phone.
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                ActionTile("Putar setelah ini", Icons.Rounded.PlaylistPlay, onPlayNext, Modifier.weight(1f))
                ActionTile("Tambahkan ke antrean", Icons.Rounded.QueueMusic, onQueue, Modifier.weight(1f))
                ActionTile("Bagikan", Icons.Rounded.Share, onShare, Modifier.weight(1f))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetRow(if (liked) "Hapus dari suka" else "Suka", Icons.Rounded.FavoriteBorder, onLike)
            if (playlists.isEmpty()) {
                SheetRow("Simpan ke playlist", Icons.Rounded.PlaylistAdd, null)
            } else {
                playlists.forEach { name ->
                    SheetRow("Simpan ke $name", Icons.Rounded.PlaylistAdd) { onSaveTo(name) }
                }
            }
            // The menu is where a user decides a track belongs somewhere; creating the target here
            // avoids backing out to the library first.
            SheetRow("Buat playlist baru", Icons.Rounded.Add) { onCreate() }
            SheetRow("Buka halaman artis", Icons.Rounded.Person) { onArtist() }
        }
}

@Composable
private fun ActionTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun SheetRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            tint = if (onClick != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (onClick != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchScreen(
    state: UiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onPlayQuery: (String) -> Unit,
    onPlay: (Track) -> Unit,
    onLike: (Track) -> Unit,
    onRemoveQuery: (String) -> Unit,
    onClearQueries: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        BrandHeader("Search", "All sources at once")
        TextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Songs, artists, albums") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.height(10.dp))
        when {
            state.loading -> CenterSpinner()
            // Empty box: recent queries first, then what was played last.
            !state.searched -> LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                if (state.queries.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionTitle("Recent searches", modifier = Modifier.weight(1f))
                            TextButton(onClick = onClearQueries) {
                                Text("Hapus semua", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    item {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.queries.forEach { q ->
                                Chip(q, false, onClick = { onPlayQuery(q) }, onRemove = { onRemoveQuery(q) })
                            }
                        }
                    }
                    item { Spacer(Modifier.height(18.dp)) }
                }
                if (state.searchPlayed.isNotEmpty()) {
                    item { SectionTitle("Recently played", state.searchPlayed.size) }
                    items(state.searchPlayed, key = { "sp" + it.source.name + it.id }) { t ->
                        TrackRow(t, selected = state.current?.id == t.id, onLike = { onLike(t) }) { onPlay(t) }
                    }
                }
                if (state.queries.isEmpty() && state.searchPlayed.isEmpty()) {
                    item { EmptyState("Search to start", "Type a song, artist or album. All three sources answer at once.") }
                }
            }
            state.results.isEmpty() -> EmptyState("No results", state.error ?: "Try a different spelling.")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                item { SectionTitle("Results", state.results.size) }
                items(state.results, key = { it.source.name + it.id }) { t ->
                    TrackRow(t, selected = state.current?.id == t.id, onLike = { onLike(t) }) { onPlay(t) }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: LoomViewModel, theme: ThemeState) {
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 190.dp)) {
        item { BrandHeader("Settings", "Theme and playback") }
        item { Caption("Accent") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Palette.entries.forEach { p ->
                    val sel = theme.palette == p && theme.customSeed == null
                    Box(
                        Modifier
                            .size(if (sel) 42.dp else 34.dp)
                            .clip(RoundedCornerShape(50))
                            .background(p.color())
                            .clickable { vm.setPalette(p) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(22.dp)) }
        item { Caption("Mode") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Mode.entries.forEach { m ->
                    Chip(m.name.lowercase().replaceFirstChar { it.uppercase() }, theme.mode == m, onClick = {
                        vm.setMode(m)
                    })
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
        item {
            Text(
                "Spotify plays 30-second previews; full tracks need a Premium sign-in. " +
                    "YouTube Music and SoundCloud play in full.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun BrandHeader(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
    )
}

@Composable
private fun CenterSpinner() {
    Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
    }
}
