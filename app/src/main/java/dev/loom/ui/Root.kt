package dev.loom.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.rounded.Add
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

    // Media3 has no position callback, so the sheet polls while it is open.
    var position by remember { mutableStateOf(0L to 0L) }
    LaunchedEffect(state.openPlayer, state.lyricsFull, state.current?.id, state.playing) {
        while (state.openPlayer || state.lyricsFull) {
            position = Playback.position()
            kotlinx.coroutines.delay(500)
        }
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
                    PlaylistDetail(open.title, state, vm::closePlaylist, vm::play, vm::toggleLike)
                } else {
                    when (state.tab) {
                        Tab.HOME -> HomeScreen(state, vm::openCollection)
                        Tab.FOR_YOU -> ForYouScreen(state, vm::openCollection)
                        Tab.PLAYLIST -> QueueScreen(
                            state,
                            vm::setLib,
                            vm::openCollection,
                            { t, q -> vm.play(t, q) },
                            vm::toggleLike,
                            vm::playQuery,
                            vm::createPlaylist,
                            vm::removePlaylist,
                        )
                        Tab.SEARCH -> SearchScreen(
                            state,
                            vm::setQuery,
                            vm::search,
                            vm::playQuery,
                            { t -> vm.play(t) },
                            vm::toggleLike,
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
private fun HomeScreen(state: UiState, onOpen: (Collection) -> Unit) {
    when {
        state.loading && state.shelves.isEmpty() -> CenterSpinner()
        state.shelves.isEmpty() -> EmptyState("Nothing to show", "Could not reach YouTube Music.")
        else -> LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 190.dp)) {
            item { BrandHeader("Loom", "Home") }
            items(state.shelves, key = { "hs" + it.title }) { shelf -> ShelfRow(shelf.title, shelf.items, onOpen) }
        }
    }
}

@Composable
private fun ForYouScreen(state: UiState, onOpen: (Collection) -> Unit) {
    if (state.forYou.isEmpty()) {
        CenterSpinner()
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 190.dp)) {
        item { BrandHeader("For You", "New releases, charts and videos") }
        items(state.forYou, key = { "fs" + it.title }) { shelf -> ShelfRow(shelf.title, shelf.items, onOpen) }
    }
}

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
) {
    var naming by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 190.dp)) {
        item { BrandHeader("Library", "Yours, kept on this device") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Lib.entries) { l -> Chip(l.label, l == state.lib) { onLib(l) } }
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
                        IconButton(onClick = { naming = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "New playlist")
                        }
                    }
                }
                if (state.playlists.isEmpty()) {
                    item { Caption("No playlists yet — tap + to name one") }
                } else {
                    items(state.playlists, key = { "p" + it.name }) { pl ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable { onPlay(pl.tracks.firstOrNull() ?: return@clickable, pl.tracks) }
                                    .padding(vertical = 12.dp),
                            ) {
                                Text(pl.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${pl.tracks.size} tracks",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onRemovePlaylist(pl.name) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Delete ${pl.name}",
                                    modifier = Modifier.size(19.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
    title: String,
    state: UiState,
    onBack: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onLike: (Track) -> Unit,
) {
    val tracks = state.openHere?.tracks ?: emptyList()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
        }
        if (state.loading) {
            CenterSpinner()
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                item {
                    Text(
                        title,
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                items(tracks, key = { it.source.name + it.id }) { t ->
                    TrackRow(t, selected = state.current?.id == t.id, onLike = { onLike(t) }) {
                        onPlay(t, tracks)
                    }
                }
            }
        }
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
                    item { SectionTitle("Recent searches", modifier = Modifier.padding(start = 4.dp)) }
                    item {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.queries.forEach { q -> Chip(q, false) { onPlayQuery(q) } }
                        }
                    }
                    item { Spacer(Modifier.height(18.dp)) }
                }
                if (state.history.isNotEmpty()) {
                    item { SectionTitle("Recently played", state.history.size) }
                    items(state.history.take(12), key = { "h" + it.source.name + it.id }) { t ->
                        TrackRow(t, selected = state.current?.id == t.id, onLike = { onLike(t) }) { onPlay(t) }
                    }
                }
                if (state.queries.isEmpty() && state.history.isEmpty()) {
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
                    Chip(m.name.lowercase().replaceFirstChar { it.uppercase() }, theme.mode == m) {
                        vm.setMode(m)
                    }
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
