package dev.loom.ui

import androidx.activity.compose.BackHandler
import android.content.Intent
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.fillMaxHeight
import dev.loom.core.NamedPlaylist
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    LaunchedEffect(state.openPlayer, state.lyricsFull, state.current?.id) {
        if (state.openPlayer || state.lyricsFull) vm.loadLyrics()
    }

    // Hardware back must unwind the same stack the UI shows, in this order: lyrics page,
    // player sheet, detail page, and only then leave the app.
    BackHandler(enabled = state.lyricsFull) { vm.closeLyrics() }
    BackHandler(enabled = !state.lyricsFull && state.openPlayer) { vm.closePlayer() }
    BackHandler(enabled = !state.lyricsFull && !state.openPlayer && state.openHere != null) {
        vm.closePlaylist()
    }
    // The search page is a page, not a tab: back returns to the search landing page.
    BackHandler(enabled = state.searchOpen) { vm.closeSearch() }

    // One SaveableStateHolder keeps each tab's scroll offset, search text and expanded rows
    // alive while it is off screen, so returning to a tab resumes where it was.
    val tabStates = rememberSaveableStateHolder()

    Box(Modifier.fillMaxSize().background(ambientBrush(accent, accent2))) {
        // hazeSource marks this subtree as the thing the glass chrome blurs.
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .hazeSource(haze),
        ) {
            tabStates.SaveableStateProvider(state.tab.name) {
                val open = state.openHere
                if (open != null) {
                    PlaylistDetail(
                        state = state,
                        onBack = vm::closePlaylist,
                        onPlay = vm::play,
                        onLike = vm::toggleLike,
                        onMenu = vm::openMenu,
                        onRemoveTrack = vm::removeFromPlaylist,
                        onRename = { vm.renamePlaylist(open.title, it) },
                        onDelete = { vm.removePlaylist(open.title); vm.closePlaylist() },
                        onSave = { open.collection?.let(vm::toggleSaved) },
                        onDownloadAll = { vm.downloadPlaylist(open.title, open.tracks) },
                    )
                } else {
                    // Tabs cross-fade and slide the way the nav knob does, so switching feels like
                    // one motion instead of a hard cut. Cheap: only opacity + offset animate.
                    AnimatedContent(
                        targetState = state.tab,
                        transitionSpec = {
                            (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 14 })
                                .togetherWith(fadeOut(tween(120)))
                        },
                        label = "tab",
                    ) { tab ->
                    when (tab) {
                        Tab.HOME -> HomeScreen(state, vm::openCollection, vm::play, vm::toggleLike, vm::openMenu, vm::loadRotatingBlocks)
                        Tab.FOR_YOU -> ForYouScreen(state, vm::openCollection, vm::play, vm::toggleLike, vm::openMenu, vm::loadRotatingBlocks)
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
                                vm::setPlaylistIcon,
                                vm::openLocal,
                                imp,
                                vm::setImportSource,
                                vm::setImportInput,
                                vm::runImport,
                                vm::closeImport,
                                vm::playFile,
                                vm::removeDownloadFile,
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
                            vm::openSearch,
                            vm::closeSearch,
                            vm::openGenre,
                        )
                        Tab.SETTINGS -> SettingsScreen(vm, theme)
                    }
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
                // The player's own three-dot opens the same sheet every other row uses.
                onMore = { state.current?.let { c -> vm.openMenu(c) } },
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
        // The track waiting for a destination playlist; non-null while the picker page is open.
        var pickFor by remember { mutableStateOf<Track?>(null) }
        var dlTrack by remember { mutableStateOf<Track?>(null) }
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
                onSaveTo = { vm.closeMenu(); pickFor = t },
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
                onDownload = { vm.closeMenu(); dlTrack = t },
                // Only when the sheet was opened from the player: the extras act on playback.
                player = if (state.openPlayer) PlayerActions(
                    shuffle = state.shuffle,
                    repeat = state.repeat,
                    eq = state.eq,
                    onShuffle = { vm.toggleShuffle() },
                    onRepeat = { vm.toggleRepeat() },
                    onEq = { vm.setEq(nextEq(state.eq)); vm.closeMenu() },
                ) else null,
            )
        }

        dlTrack?.let { t ->
        var startM by remember(t.id) { mutableStateOf("0") }
        var startS by remember(t.id) { mutableStateOf("0") }
        val totalMin = (t.durationMs / 60000).toInt()
        val totalSec = ((t.durationMs / 1000) % 60).toInt()
        var endM by remember(t.id) { mutableStateOf(totalMin.toString()) }
        var endS by remember(t.id) { mutableStateOf(totalSec.toString()) }
        AlertDialog(
            onDismissRequest = { dlTrack = null },
            title = { Text("Unduh lagu") },
            text = {
                Column {
                    Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Kosongkan atau samakan dengan durasi untuk mengunduh utuh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dari", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
                        MinuteSecondField(startM, startS) { m, s -> startM = m; startS = s }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sampai", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
                        MinuteSecondField(endM, endS) { m, s -> endM = m; endS = s }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = (startM.toLongOrNull() ?: 0L) * 60_000 + (startS.toLongOrNull() ?: 0L) * 1000
                    val b = (endM.toLongOrNull() ?: 0L) * 60_000 + (endS.toLongOrNull() ?: 0L) * 1000
                    // A trim that covers the whole track is just a normal download.
                    val trimmed = b > a && b < t.durationMs
                    vm.downloadTrack(t, playlist = null, startMs = if (trimmed) a else 0L, endMs = if (trimmed) b else 0L)
                    dlTrack = null
                }) { Text("Unduh") }
            },
            dismissButton = { TextButton(onClick = { dlTrack = null }) { Text("Batal") } },
        )
    }

    pickFor?.let { t ->
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(onDismissRequest = { pickFor = null }) {
            SaveToPlaylistSheet(
                track = t,
                playlists = state.playlists.map { it.name },
                onDismiss = { pickFor = null },
                onPick = { name -> vm.saveToPlaylist(name, t); pickFor = null },
                onCreate = { newPl = t; pickFor = null },
            )
        }
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
                    offset = state.lyricOffsets[t.id] ?: 0.0,
                    onOffset = vm::setLyricOffset,
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
    onRefresh: (Tab) -> Unit,
) {
    when {
        state.loading && state.shelves.isEmpty() -> CenterSpinner()
        state.shelves.isEmpty() && state.topTracks.isEmpty() ->
            EmptyState("Nothing to show", "Could not reach YouTube Music.")
        else -> LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 190.dp)) {
            item { BrandHeader("Loom", "Home") }
            feed(state, personal = false, onOpen, onPlay, onLike, onMenu, onRefresh = { onRefresh(Tab.HOME) })
        }
    }
}

/** Community playlists as a plain cover grid: no numbers, no rows, just the artwork. */
@Composable
private fun CommunityPlaylists(title: String, items: List<Collection>, onOpen: (Collection) -> Unit) {
    if (items.isEmpty()) return
    Column(Modifier.padding(bottom = 18.dp)) {
        SectionTitle(title, items.size)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items.take(10), key = { it.id }) { c -> CollectionCard(c, { onOpen(c) }) }
        }
    }
}

/**
 * Header for a block that rerolls. The refresh icon sits on the header so the user does not have
 * to hunt for a control, and the label says what will change.
 */
@Composable
private fun RotatingHeader(name: String, loading: Boolean, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Genre pilihan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRefresh, enabled = !loading) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = "Ganti genre",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Same shape as [RotatingHeader]; the label differs because the block is a mood, not a genre. */
@Composable
private fun MoodHeader(name: String, loading: Boolean, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Suasana", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRefresh, enabled = !loading) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = "Ganti suasana",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The feed both tabs draw: three vertical track rows, then a horizontal carousel, then the
 * community playlists, then a rotating genre block and a rotating mood block.
 *
 * One composable for both tabs keeps the rhythm identical, which is what the user asked for
 * ("for you today gunakan konsep yang sama"). Order is fixed here rather than taken from the
 * server: the alternation is a layout decision, not data.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.feed(
    state: UiState,
    personal: Boolean,
    onOpen: (Collection) -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onLike: (Track) -> Unit,
    onMenu: (Track) -> Unit,
    onRefresh: (Tab) -> Unit,
) {
    // No cross-tab fallback: Home reads the charts, For You reads the personal radio. Borrowing
    // the other tab's list when one is empty is what made both pages show the same songs.
    val homeIds = state.topTracks.map { it.source.name + it.id }.toSet()
    val top = (if (personal) state.forYouTracks.filterNot { it.source.name + it.id in homeIds } else state.topTracks)
        .take(3)
    if (top.isNotEmpty()) {
        itemsIndexed(top, key = { _, t -> "ft" + t.id }) { i, t ->
            NumberedTrackRow(
                i + 1, t, state.current?.id == t.id,
                liked = state.liked.any { it.id == t.id && it.source == t.source },
                onPlay = { onPlay(t, top) }, onLike = { onLike(t) }, onMenu = { onMenu(t) },
            )
        }
    }

    // One carousel, then the community covers. Two carousels pushed the genre and mood blocks
    // below two screens, and the user asked not to scroll far.
    val shelves = if (personal) state.forYou else state.shelves
    val carousel = shelves.firstOrNull { it.kind != "playlists" } ?: shelves.firstOrNull()
    if (carousel != null) {
        item { ShelfRow(carousel.title, carousel.items, carousel.kind, onOpen) }
    }

    // Community playlists: covers only, the shape the user pointed at in their screenshot.
    val community = shelves.firstOrNull { it.kind == "playlists" && it !== carousel }
    if (community != null) {
        item { CommunityPlaylists(community.title, community.items, onOpen) }
    }

    // Rotating genre block: a horizontal carousel, so it costs one row of height instead of three
    // and gives the page the sideways scroll it was missing.
    (if (personal) state.genreBlocks[Tab.FOR_YOU] else state.genreBlocks[Tab.HOME])?.let { block ->
        item { RotatingHeader(block.name, block.loading) { onRefresh(if (personal) Tab.FOR_YOU else Tab.HOME) } }
        item {
            TrackCarousel(block.tracks, block.loading, state, onPlay, onLike, onMenu)
        }
    }

    // Rotating mood block: "Rain + Feels" is only one of six, and it changes on refresh.
    (if (personal) state.moodBlocks[Tab.FOR_YOU] else state.moodBlocks[Tab.HOME])?.let { block ->
        item { MoodHeader(block.name, block.loading) { onRefresh(if (personal) Tab.FOR_YOU else Tab.HOME) } }
        item {
            TrackCarousel(block.tracks, block.loading, state, onPlay, onLike, onMenu)
        }
    }
}

/**
 * Compact horizontal track row for the rotating blocks: artwork, title, artist. Tapping plays, the
 * three-dot opens the same sheet as everywhere else.
 */
@Composable
private fun TrackCarousel(
    tracks: List<Track>,
    loading: Boolean,
    state: UiState,
    onPlay: (Track, List<Track>) -> Unit,
    onLike: (Track) -> Unit,
    onMenu: (Track) -> Unit,
) {
    if (loading) {
        // Three grey cards, the same footprint as a loaded row, so nothing jumps.
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .padding(end = 12.dp)
                        .size(132.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                )
            }
        }
        return
    }
    if (tracks.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(tracks) { _, t ->
            Column(
                Modifier
                    .width(132.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onPlay(t, tracks) },
            ) {
                AsyncImage(
                    model = t.art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(132.dp).clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    t.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    t.artist.ifBlank { t.source.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    onRefresh: (Tab) -> Unit,
) {
    if (state.forYou.isEmpty() && state.forYouTracks.isEmpty()) {
        CenterSpinner()
        return
    }
    // Same feed, same rhythm as Home. "For you today" is the header, and the personal tracks
    // lead because the feed takes `forYouTracks` first.
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 190.dp)) {
        item { BrandHeader("For You", "For you today · built from what you listen to") }
        feed(state, personal = true, onOpen, onPlay, onLike, onMenu, onRefresh = { onRefresh(Tab.FOR_YOU) })
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
    onPlaylistIcon: (String, String) -> Unit,
    onOpenLocal: (String) -> Unit,
    importState: LoomViewModel.Importing,
    onImportSource: (Source) -> Unit,
    onImportInput: (String) -> Unit,
    onImportRun: (String) -> Unit,
    onImportClose: () -> Unit,
    onPlayFile: (dev.loom.core.Downloaded) -> Unit,
    onRemoveFile: (dev.loom.core.Downloaded) -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    // Which playlist is choosing an icon; null closes the dialog.
    var iconFor by remember { mutableStateOf<NamedPlaylist?>(null) }

    iconFor?.let { pl ->
        AlertDialog(
            onDismissRequest = { iconFor = null },
            title = { Text("Ikon playlist") },
            text = {
                // Four per row: twelve marks fit without scrolling.
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PlaylistIcons.all.forEach { (name, icon) ->
                        IconButton(
                            onClick = { onPlaylistIcon(pl.name, name); iconFor = null },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                icon,
                                contentDescription = name,
                                modifier = Modifier.size(24.dp),
                                tint = if (pl.icon == name) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onPlaylistIcon(pl.name, ""); iconFor = null }) { Text("Pakai sampul") }
            },
            dismissButton = { TextButton(onClick = { iconFor = null }) { Text("Batal") } },
        )
    }
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
                            onIcon = { iconFor = pl },
                        )
                    }
                }
                if (state.saved.isNotEmpty()) {
                    item { Spacer(Modifier.height(18.dp)) }
                    item { SectionTitle("Saved from Home", state.saved.size) }
                    // Two per row: these are square cards, and one per row wasted the width.
                    // chunked rows instead of a nested LazyVerticalGrid, which would fight the
                    // parent list for scrolling.
                    items(state.saved.chunked(2), key = { row -> "s" + row.first().id }) { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            row.forEach { c ->
                                Box(Modifier.weight(1f)) { CollectionCard(c, { onOpen(c) }, fillWidth = true) }
                            }
                            if (row.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }
            Lib.HISTORY -> if (state.history.isEmpty()) {
                item { EmptyState("Nothing played yet", "Anything you play lands here.") }
            } else {
                items(state.history, key = { "h" + it.source.name + it.id }) { t ->
                    TrackRow(t, state.current?.id == t.id, liked = state.liked.any { it.id == t.id && it.source == t.source }, onLike = { onLike(t) }) { onPlay(t, state.history) }
                }
            }
            Lib.LIKED -> if (state.liked.isEmpty()) {
                item { EmptyState("No likes yet", "Tap the heart on any row.") }
            } else {
                items(state.liked, key = { "l" + it.source.name + it.id }) { t ->
                    TrackRow(t, state.current?.id == t.id, liked = state.liked.any { it.id == t.id && it.source == t.source }, onLike = { onLike(t) }) { onPlay(t, state.liked) }
                }
            }
            Lib.WEEKLY -> if (state.weekly.isEmpty()) {
                item { CenterSpinner() }
            } else {
                item { Caption("Built from the artists you played this week") }
                items(state.weekly, key = { "w" + it.source.name + it.id }) { t ->
                    TrackRow(t, state.current?.id == t.id, liked = state.liked.any { it.id == t.id && it.source == t.source }, onLike = { onLike(t) }) { onPlay(t, state.weekly) }
                }
            }
            Lib.DOWNLOADS -> if (state.downloads.isEmpty() && state.downloading == null) {
                item { EmptyState("Belum ada unduhan", "Unduh satu lagu dari menu titik tiga, atau satu playlist dari halaman playlist.") }
            } else {
                // Live row: percentage, bytes, speed and the estimate, all from one sample.
                state.downloading?.let { label ->
                    item {
                        val pct = ((state.downloadProgress ?: 0f) * 100).toInt()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { state.downloadProgress ?: 0f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                buildString {
                                    append("$pct%")
                                    state.downloadSample?.let { s ->
                                        if (s.total > 0) append(" · ${sizeText(s.bytes)} / ${sizeText(s.total)}")
                                        if (s.bytesPerSecond > 0) append(" · ${sizeText(s.bytesPerSecond)}/s")
                                        s.etaSeconds?.let { append(" · sisa ${if (it < 60) "$it dtk" else "${it / 60} mnt"}") }
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                state.downloads.forEach { (folder, files) ->
                    item {
                        SectionTitle(folder ?: "Lagu lepas", files.size)
                    }
                    items(files, key = { "dl" + it.uri }) { f ->
                        // Offline rows are plain files; the title comes from the filename, which
                        // was written from the track at download time.
                        DownloadedRow(f, onPlay = { onPlayFile(f) }, onRemove = { onRemoveFile(f) })
                    }
                }
            }
            Lib.QUEUE -> if (state.queue.isEmpty()) {
                item { EmptyState("No queue yet", "Open a playlist from Home, or play a search result.") }
            } else {
                items(state.queue, key = { "q" + it.source.name + it.id }) { t ->
                    TrackRow(t, state.current?.id == t.id, liked = state.liked.any { it.id == t.id && it.source == t.source }, onLike = { onLike(t) }) { onPlay(t, state.queue) }
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
    onMenu: (Track) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onDownloadAll: () -> Unit,
) {
    val open = state.openHere ?: return
    val title = open.title
    val tracks = open.tracks
    val saved = open.collection?.let { c -> state.saved.any { it.id == c.id } } == true
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
            } else if (open.collection != null) {
                // Provider playlists opened from Home / For You can be kept in the library.
                IconButton(onClick = onSave) {
                    Icon(
                        if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (saved) "Hapus dari koleksi" else "Simpan playlist",
                        tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Any playlist with tracks can be taken offline, local or provider.
            if (tracks.isNotEmpty()) {
                IconButton(onClick = onDownloadAll) {
                    Icon(Icons.Rounded.Download, contentDescription = "Unduh playlist")
                }
            }
        }
        if (state.loading && tracks.isEmpty()) {
            // Title already known from the tapped card, so the header is real, not a placeholder.
            PlaylistHero(title, tracks, null, state.playlists.firstOrNull { it.name == title }?.icon ?: "")
            TrackSkeleton()
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                item {
                    PlaylistHero(title, tracks, { onPlay(tracks.first(), tracks) }, state.playlists.firstOrNull { it.name == title }?.icon ?: "")
                }
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
                        liked = state.liked.any { it.id == t.id && it.source == t.source },
                        onPlay = { onPlay(t, tracks) },
                        onLike = { onLike(t) },
                        onMenu = { onMenu(t) },
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
private fun PlaylistHero(title: String, tracks: List<Track>, onPlayAll: (() -> Unit)?, icon: String = "") {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        MosaicCover(tracks, 104.dp, icon)
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

/**
 * One cover when there is one, a 2x2 grid of the first four when there are more. A pinned [icon]
 * replaces the artwork entirely — a user-chosen mark is more recognisable than a mosaic.
 */
@Composable
fun MosaicCover(tracks: List<Track>, size: androidx.compose.ui.unit.Dp, icon: String = "") {
    val arts = tracks.mapNotNull { it.art.takeIf(String::isNotBlank) }.distinct().take(4)
    val shape = RoundedCornerShape(18.dp)
    val pinned = PlaylistIcons.of(icon)
    Box(Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (pinned != null) {
            Icon(
                pinned,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(size / 2.2f),
                tint = MaterialTheme.colorScheme.primary,
            )
            return@Box
        }
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
    onIcon: () -> Unit,
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
        MosaicCover(pl.tracks, 60.dp, pl.icon)
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
                    text = { Text("Ubah ikon") },
                    onClick = { menu = false; onIcon() },
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
fun TrackSkeleton(rows: Int = 7) {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonPulse",
    )
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f * (alpha / 0.5f))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Artwork + two text lines: exactly the TrackRow footprint.
        repeat(rows) {
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

/** Two small number fields for mm / ss, used by the download trim dialog. */
@Composable
private fun MinuteSecondField(minute: String, second: String, onChange: (String, String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = minute,
            onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) onChange(v, second) },
            label = { Text("menit") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp),
        )
        Text(" : ", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = second,
            onValueChange = { v -> if (v.length <= 2 && v.all { it.isDigit() }) onChange(minute, v) },
            label = { Text("detik") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp),
        )
    }
}

/** A file on disk: no track metadata survives a download, so the filename is the title. */
@Composable
private fun DownloadedRow(f: dev.loom.core.Downloaded, onPlay: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlay)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                f.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Offline · " + dev.loom.core.Downloads.sizeText(f.sizeBytes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Delete, contentDescription = "Hapus unduhan", modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A track row with a rank number and a three-dot menu, for the playable lists on Home/For You. */
@Composable
fun NumberedTrackRow(
    rank: Int,
    t: Track,
    selected: Boolean,
    liked: Boolean = false,
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
                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (liked) "Unlike" else "Like",
                modifier = Modifier.size(18.dp),
                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
    onSaveTo: () -> Unit,
    onCreate: () -> Unit,
    onShare: () -> Unit,
    onArtist: () -> Unit,
    onDownload: () -> Unit,
    /** Player-only extras; null hides the section so the plain track sheet stays as it was. */
    player: PlayerActions? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        TrackActionsBody(track, liked, playlists, onDismiss, onPlayNext, onQueue, onLike, onSaveTo, onCreate, onShare, onArtist, onDownload, player)
    }
}

/**
 * The controls that used to sit as five separate icons under the cover. They live in the sheet now:
 * one entry point, larger targets, and the artwork keeps its space.
 */
data class PlayerActions(
    val shuffle: Boolean,
    val repeat: Boolean,
    val eq: String,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onEq: () -> Unit,
)

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
    onSaveTo: () -> Unit,
    onCreate: () -> Unit,
    onShare: () -> Unit,
    onArtist: () -> Unit,
    onDownload: () -> Unit,
    player: PlayerActions? = null,
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
            // One entry, always: with 100 playlists the old inline list was unusable. The picker
            // has a search box, which is what makes a long list navigable.
            SheetRow("Simpan ke playlist", Icons.Rounded.PlaylistAdd, onSaveTo)
            // The menu is where a user decides a track belongs somewhere; creating the target here
            // avoids backing out to the library first.
            SheetRow("Buat playlist baru", Icons.Rounded.Add) { onCreate() }
            SheetRow("Unduh lagu", Icons.Rounded.Download) { onDownload() }
            SheetRow("Buka halaman artis", Icons.Rounded.Person) { onArtist() }
            if (player != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SheetRow(if (player.shuffle) "Acak: aktif" else "Acak: mati", Icons.Rounded.Shuffle) { player.onShuffle() }
                SheetRow(if (player.repeat) "Ulangi: aktif" else "Ulangi: mati", Icons.Rounded.Repeat) { player.onRepeat() }
                SheetRow("Equalizer · ${player.eq}", Icons.Rounded.Equalizer) { player.onEq() }
            }
        }
}

/**
 * "Simpan ke playlist" as its own page: a search box over the user's playlists, plus a way to make
 * a new one. With one entry in the sheet the track is never buried under a hundred playlist rows.
 */
@Composable
fun SaveToPlaylistSheet(
    track: Track,
    playlists: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreate: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val shown = remember(q, playlists) {
        if (q.isBlank()) playlists else playlists.filter { it.contains(q, ignoreCase = true) }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Simpan ke playlist", style = MaterialTheme.typography.titleLarge)
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
        }
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            placeholder = { Text("Cari playlist") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        )
        // Capped height: the sheet must not try to lay out a hundred rows at once.
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (playlists.isEmpty()) "Belum ada playlist." else "Tidak ada playlist yang cocok.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
            items(shown, key = { it }) { name ->
                SheetRow(name, Icons.Rounded.QueueMusic) { onPick(name) }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        SheetRow("Buat playlist baru", Icons.Rounded.Add) { onCreate() }
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
/**
 * Two pages, one box.
 *
 * Landing: a tap target that looks like a search bar, the genres people actually start from, then
 * what the user searched and played before. Tapping it opens [SearchPage], which is only the box,
 * the title history and live title suggestions — nothing else competes with typing.
 */
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
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onGenre: (String) -> Unit,
) {
    if (state.searchOpen) {
        SearchPage(state, onQuery, onSearch, onPlayQuery, onPlay, onLike, onRemoveQuery, onClose)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 190.dp)) {
        item { BrandHeader("Search", "All sources at once") }
        item {
            // Not a real field: it only opens the page, so the landing page never fights the IME.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Lagu, artis, album", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.queries.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("Riwayat pencarian", modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearQueries) { Text("Hapus semua", style = MaterialTheme.typography.labelMedium) }
                }
            }
            item {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.queries.forEach { q -> Chip(q, false, onClick = { onPlayQuery(q) }, onRemove = { onRemoveQuery(q) }) }
                }
            }
        }
        // Six genres: enough to browse, few enough to scan without scrolling.
        item { SectionTitle("Telusuri genre") }
        items(Genres.take(6), key = { "g" + it }) { g ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onGenre(g) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(g, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.searchPlayed.isNotEmpty()) {
            item { SectionTitle("Terakhir diputar", state.searchPlayed.size) }
            items(state.searchPlayed, key = { "sp" + it.source.name + it.id }) { t ->
                TrackRow(t, selected = state.current?.id == t.id, liked = state.liked.any { it.id == t.id && it.source == t.source }, onLike = { onLike(t) }) { onPlay(t) }
            }
        }
    }
}

/** Genres the app can actually answer for: each one is a plain search query. */
private val Genres = listOf(
    "Pop Indonesia", "Dangdut", "Rock", "Hip hop", "Lo-fi", "K-pop", "Jazz", "Akustik",
)

/** The typing page: box, title history, live suggestions. Results replace the suggestions. */
@Composable
private fun SearchPage(
    state: UiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onPlayQuery: (String) -> Unit,
    onPlay: (Track) -> Unit,
    onLike: (Track) -> Unit,
    onRemoveQuery: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            TextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text("Lagu, artis, album") },
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
        }
        Spacer(Modifier.height(10.dp))
        when {
            state.loading -> CenterSpinner()
            state.searched && state.results.isNotEmpty() -> LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                item { SectionTitle("Hasil", state.results.size) }
                items(state.results, key = { it.source.name + it.id }) { t ->
                    TrackRow(t, selected = state.current?.id == t.id, liked = state.liked.any { it.id == t.id && it.source == t.source }, onLike = { onLike(t) }) { onPlay(t) }
                }
            }
            state.searched -> EmptyState("Tidak ada hasil", state.error ?: "Coba ejaan lain.")
            // While typing: suggested titles first, then the user's own title history.
            state.query.isNotBlank() -> LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                if (state.suggest.isNotEmpty()) {
                    item { SectionTitle("Saran") }
                    items(state.suggest, key = { "sg" + it }) { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPlayQuery(s) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(s, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
                if (state.queries.isNotEmpty()) {
                    item { SectionTitle("Riwayat pencarian") }
                    items(state.queries, key = { "q" + it }) { q ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPlayQuery(q) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(q, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { onRemoveQuery(q) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Hapus", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    item { EmptyState("Cari lagu", "Ketik judul, artis atau album. Semua sumber menjawab sekaligus.") }
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
        item { Caption("Floating island") }
        item {
            val ctx = LocalContext.current
            val granted = remember { android.provider.Settings.canDrawOverlays(ctx) }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Show over other apps", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (granted) "Granted. Tap the island to open lyrics."
                        else "Needs the draw-over-apps permission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = vm.islandOn && granted,
                    onCheckedChange = { on ->
                        if (granted) {
                            vm.islandOn = on
                        } else {
                            // Only the system can grant this; send the user to the right page.
                            runCatching {
                                ctx.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:" + ctx.packageName),
                                    )
                                )
                            }
                        }
                    },
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
        item {
            Text(
                "Spotify tracks are matched to the same recording on YouTube Music and play in full; " +
                    "the 30-second preview is only the fallback when no match is found.",
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

/** Same unit switch as the download notification, so both readouts agree. */
private fun sizeText(b: Long): String = when {
    b >= 1_048_576 -> String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)
    b >= 1024 -> "${b / 1024} KB"
    else -> "$b B"
}

/** Cycles the equalizer presets; the player's old dialog listed them, a menu row needs one tap. */
private fun nextEq(current: String): String {
    val all = dev.loom.player.Eq.names
    return all[(all.indexOf(current).coerceAtLeast(0) + 1) % all.size]
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
