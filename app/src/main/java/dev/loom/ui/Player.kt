package dev.loom.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.loom.core.NamedPlaylist
import dev.loom.core.Track

/**
 * Full player. Cover doubles as the lyrics toggle: tap it and the artwork cross-fades into
 * the words, tap again to bring the art back. Transport, seek bar and the usual row of
 * actions sit underneath in the order every music player uses.
 */
@Composable
fun NowPlayingSheet(
    state: UiState,
    lyrics: String?,
    playlists: List<NamedPlaylist>,
    positionMs: Long,
    durationMs: Long,
    liked: Boolean,
    shuffle: Boolean,
    repeat: Boolean,
    eq: String,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onEq: (String) -> Unit,
    onLyrics: () -> Unit,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onSkip: (Int) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onLike: () -> Unit,
    onLikeTrack: (Track) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onMore: () -> Unit,
) {
    val t = state.current ?: return
    var picker by remember { mutableStateOf(false) }
    var eqPicker by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Tinted wash over an opaque base: the sheet must never let the library show through.
        AsyncImage(
            model = t.art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(70.dp)
                .graphicsLayer { alpha = 0.28f },
        )
        Box(Modifier.fillMaxSize().background(ambientBrush(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Now playing",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onLyrics) {
                    Icon(Icons.Rounded.Lyrics, contentDescription = "Lyrics", modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Cover opens the full lyrics page: words deserve the whole screen, not a box.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(onClick = onLyrics),
            ) {
                AsyncImage(
                    model = t.art,
                    contentDescription = "Lyrics",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(22.dp))
            Text(t.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(t.artist.takeIf { it.isNotBlank() }, t.source.label).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            Slider(
                value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                onValueChange = { onSeek((it * durationMs).toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clock(positionMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(clock(durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onSkip(-1) }) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = { onSeekBy(-10_000) }) {
                    Icon(Icons.Rounded.Replay10, contentDescription = "Mundur 10 detik", modifier = Modifier.size(28.dp))
                }
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = !state.resolving, onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.resolving) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(
                            if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.playing) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
                IconButton(onClick = { onSeekBy(10_000) }) {
                    Icon(Icons.Rounded.Forward10, contentDescription = "Maju 10 detik", modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = { onSkip(1) }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(30.dp))
                }
            }

            Spacer(Modifier.height(18.dp))
            // One entry point for every track action. Five icon buttons here were cramped and
            // duplicated what the three-dot sheet already offers; the sheet is the single home now.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onLike) {
                    Icon(
                        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (liked) "Unlike" else "Like",
                        modifier = Modifier.size(23.dp),
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onMore) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Opsi lagu",
                        modifier = Modifier.size(23.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            // Up next: what the queue will play after this, so the sheet is not a dead end.
            if (state.queue.size > 1) {
                val upcoming = (1 until state.queue.size)
                    .map { state.queue[(state.index + it) % state.queue.size] }
                    .take(6)
                SectionTitle("Up next", state.queue.size - 1)
                upcoming.forEach { t ->
                    TrackRow(
                        t = t,
                        selected = false,
                        liked = state.liked.any { it.id == t.id && it.source == t.source },
                        onLike = { onLikeTrack(t) },
                        onClick = { onPlayTrack(t) },
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (eqPicker) {
        AlertDialog(
            onDismissRequest = { eqPicker = false },
            title = { Text("Equalizer") },
            text = {
                Column {
                    dev.loom.player.Eq.names.forEach { name ->
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (name == eq) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEq(name); eqPicker = false }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { eqPicker = false }) { Text("Done") } },
        )
    }

    if (picker) {
        AlertDialog(
            onDismissRequest = { picker = false; newName = "" },
            title = { Text("Add to playlist") },
            text = {
                Column {
                    playlists.forEach { pl ->
                        Text(
                            "${pl.name}  ·  ${pl.tracks.size}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAddToPlaylist(pl.name); picker = false; newName = "" }
                                .padding(vertical = 12.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("New playlist name") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = { onCreatePlaylist(newName); newName = ""; picker = false },
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { picker = false; newName = "" }) { Text("Cancel") } },
        )
    }
}

private fun clock(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
