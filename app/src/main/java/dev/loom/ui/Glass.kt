package dev.loom.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Close
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState

/** Floating glass "now playing" pill that sits above the nav bar. */
@Composable
fun NowPlayingPill(
    state: UiState,
    haze: HazeState,
    progress: Float,
    onToggle: () -> Unit,
    onSkip: (Int) -> Unit,
    onClick: () -> Unit,
) {
    val t = state.current ?: return
    // Previous target, used to decide between tweening forward and snapping back.
    var prev by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .glass(haze, corner = 26)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                AsyncImage(
                    model = t.art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)),
                )
                // Slow-spinning ring while playing would be noise here; a tint is enough.
                if (state.playing) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    t.artist.ifBlank { t.source.label },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onSkip(-1) }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(22.dp))
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(enabled = !state.resolving, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (state.resolving) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(onClick = { onSkip(1) }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(22.dp))
            }
        }
        // Progress line hugs the bottom edge of the card, inset so it reads as part of the
        // glass rather than a border.
        val target = progress.coerceIn(0f, 1f)
        // Polled in 500ms steps, so the fill is tweened forward instead of jumping; a step back
        // (new track, seek) snaps rather than sliding backwards.
        val fill by animateFloatAsState(
            targetValue = target,
            animationSpec = if (target >= prev) tween(500, easing = LinearEasing) else snap(),
            label = "pillProgress",
        )
        // Recompose-safe: this only records the value for the next comparison.
        SideEffect { prev = target }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 9.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** Pill row used for source switching and mode selection. */
@Composable
fun Chip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val scale by animateFloatAsState(if (active) 1f else 0.97f, spring(), label = "chipScale")
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.08f),
            )
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = if (onRemove == null) 16.dp else 6.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRemove != null) {
            Spacer(Modifier.width(2.dp))
            // 28dp keeps the tap target usable without inflating the chip height.
            Box(
                Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Hapus $label",
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Ambient wash behind everything: two soft glows so glass has something to refract.
 * Light mode gets pale blue-white air; dark mode gets the deep tinted night.
 */
@Composable
fun ambientBrush(primary: Color, secondary: Color): Brush {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.35f
    return if (dark) {
        Brush.linearGradient(
            listOf(
                primary.copy(alpha = 0.42f),
                secondary.copy(alpha = 0.18f),
                Color(0xFF05060A),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                primary.copy(alpha = 0.30f),
                secondary.copy(alpha = 0.14f),
                Color.White,
            ),
        )
    }
}
