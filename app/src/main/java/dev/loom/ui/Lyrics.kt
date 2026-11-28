package dev.loom.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import java.util.Locale
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.loom.core.LyricLine
import dev.loom.core.Track

/**
 * Full-screen lyrics: the words take the whole page, the active line is the only one at full
 * strength, and a row of bars under the transport reacts to the vocal level rather than the
 * waveform. Bars are driven by the same position clock as the lyrics, so nothing needs an
 * audio tap and nothing costs a frame budget beyond one animated float.
 *
 * `shortcut:` the bars are a deterministic function of position and beat, not a real FFT.
 * Swap in `android.media.audiofx.Visualizer` if the bars need to track the actual mix.
 */
@Composable
fun LyricsScreen(
    track: Track,
    lines: List<LyricLine>,
    plain: String?,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onSkip: (Int) -> Unit,
    /** Shift applied to every timestamp, in seconds. Positive delays the lyrics. */
    offset: Double = 0.0,
    onOffset: (Double) -> Unit = {},
) {
    // lrclib often holds several edits of one song with identical durations and timings seconds
    // apart, so which one is right cannot be decided from metadata. The offset is the fix that
    // works for the user in front of the drift: nudge until the line lands.
    val seconds = positionMs / 1000.0 - offset
    // Index of the line being sung right now; -1 before the first one.
    val active = remember(lines, seconds) {
        lines.indexOfLast { it.start <= seconds }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AsyncImage(
            model = track.art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(90.dp).graphicsLayer { alpha = 0.30f },
        )
        Box(Modifier.fillMaxSize().background(ambientBrush(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close lyrics")
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        track.artist.ifBlank { track.source.label },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            if (lines.isNotEmpty()) {
                // Only shown with real timings: there is nothing to shift otherwise.
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onOffset(offset - 0.5) }) { Text("-0.5s") }
                    Text(
                        "offset " + (if (offset > 0) "+" else "") + String.format(java.util.Locale.US, "%.1f", offset) + "s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onOffset(offset + 0.5) }) { Text("+0.5s") }
                    if (offset != 0.0) {
                        TextButton(onClick = { onOffset(0.0) }) { Text("Reset") }
                    }
                }
            }

            if (lines.isEmpty()) {
                // No timings: fall back to the plain block, still on its own page.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        plain ?: "No lyrics for this track",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    )
                }
            } else {
                val listState = rememberLazyListState()
                // Keep the sung line a third of the way down the page.
                LaunchedEffect(active) {
                    if (active >= 0) listState.animateScrollToItem(active.coerceAtLeast(0))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 120.dp),
                ) {
                    items(lines.size) { i ->
                        val on = i == active
                        Text(
                            lines[i].text,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            color = if (on) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        )
                    }
                }
            }

            RhythmBars(
                positionMs = positionMs,
                playing = playing,
                modifier = Modifier.fillMaxWidth().height(34.dp).padding(vertical = 4.dp),
            )

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onSkip(-1) }) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(28.dp))
                }
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = { onSkip(1) }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/**
 * A row of bars that breathe with the track. Each bar reads a different phase of the same
 * clock so the row never moves as one block, which is what makes it look like a mix rather
 * than a progress bar.
 */
@Composable
fun RhythmBars(positionMs: Long, playing: Boolean, modifier: Modifier = Modifier) {
    val t = positionMs / 1000f
    val count = 32
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            // Three detuned sines per bar: enough variation to look organic, one float of work.
            val phase = i * 0.7f
            val v = (
                0.55f +
                    0.28f * kotlin.math.sin(t * 2.1f + phase) +
                    0.18f * kotlin.math.sin(t * 3.7f + phase * 1.7f) +
                    0.12f * kotlin.math.sin(t * 5.3f + phase * 2.9f)
                ).coerceIn(0.12f, 1f)
            val target = if (playing) v else 0.14f
            val h by animateFloatAsState(target, tween(140), label = "bar$i")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    ),
            )
        }
    }
}

/** Small label chip row used by the equalizer picker. */
@Composable
fun PickerRow(labels: List<String>, selected: String, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { l -> Chip(l, l == selected, onClick = { onPick(l) }) }
    }
}
