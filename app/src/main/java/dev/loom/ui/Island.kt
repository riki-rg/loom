package dev.loom.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.loom.core.Track
import java.util.Locale

/**
 * Now-playing as a Dynamic Island: a black capsule that expands from the top of the screen.
 *
 * Always black, in both themes — that is the point of the shape, and it is what makes the
 * artwork and the progress read against any wallpaper behind it.
 *
 * Tapping opens the lyrics page instead of the full player: the island is the compact form, and
 * lyrics are what a user reaches for when they want more than the title.
 */
@Composable
fun DynamicIsland(
    track: Track,
    playing: Boolean,
    resolving: Boolean,
    positionMs: Long,
    durationMs: Long,
    haze: HazeState,
    onToggle: () -> Unit,
    onSkip: (Int) -> Unit,
    onOpenLyrics: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(shape)
            .background(Color.Black)
            .clickable(onClick = onOpenLyrics)
            .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpinningDisc(playing = playing)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    clock(positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
                Spacer(Modifier.width(6.dp))
                // Four bars, not thirty-two: at island width the row reads as a level meter.
                RhythmBars(
                    positionMs = positionMs,
                    playing = playing,
                    modifier = Modifier.width(30.dp).height(10.dp),
                    count = 4,
                    gap = 2.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    clock(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
        }
        IslandButton(Icons.Rounded.SkipPrevious, "Previous") { onSkip(-1) }
        IslandButton(
            if (resolving) Icons.Rounded.PlayArrow else if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (playing) "Pause" else "Play",
            accent = true,
        ) { if (!resolving) onToggle() }
        IslandButton(Icons.Rounded.SkipNext, "Next") { onSkip(1) }
    }
}

/** A record: dark disc, bright label, and the whole thing turns only while audio runs. */
@Composable
private fun SpinningDisc(playing: Boolean) {
    val spin by rememberInfiniteTransition(label = "disc").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "discSpin",
    )
    Box(
        Modifier
            .size(38.dp)
            .rotate(if (playing) spin else 0f)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF2A2A2E), Color(0xFF0B0B0D)),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Grooves, drawn as one ring so the disc reads as vinyl at 38dp.
        Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF17171A)))
        Box(Modifier.size(11.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Box(Modifier.size(3.dp).clip(CircleShape).background(Color.Black))
    }
}

@Composable
private fun IslandButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .then(if (accent) Modifier.background(Color.White) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = if (accent) Color.Black else Color.White.copy(alpha = 0.85f),
        )
    }
}

private fun clock(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
