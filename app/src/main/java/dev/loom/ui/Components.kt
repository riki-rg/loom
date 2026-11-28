package dev.loom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.loom.core.Collection
import dev.loom.core.Track

/**
 * Shared glass surface: real backdrop blur plus a hairline highlight along the top edge.
 * Tint follows the scheme so the same chrome reads as frosted white in light mode and
 * smoked glass in dark, instead of one hardcoded dark panel.
 */
@Composable
fun Modifier.glass(
    haze: HazeState,
    color: Color = Color.Unspecified,
    corner: Int = 24,
    blur: Int = 24,
    shape: Shape? = null,
): Modifier {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.35f
    val base = MaterialTheme.colorScheme.surface
    val outline = shape ?: RoundedCornerShape(corner.dp)
    val fill = if (color == Color.Unspecified) {
        if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.55f)
    } else color
    return this
        .clip(outline)
        .hazeEffect(
            state = haze,
            style = HazeStyle(
                backgroundColor = base,
                tints = listOf(
                    HazeTint(if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.35f)),
                ),
                blurRadius = blur.dp,
                noiseFactor = if (dark) 0.06f else 0.02f,
            ),
        )
        .background(fill)
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                if (dark) {
                    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))
                } else {
                    listOf(Color.White.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.06f))
                },
            ),
            shape = outline,
        )
}

@Composable
fun SectionTitle(text: String, count: Int = 0, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f, false))
        if (count > 0) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 3.dp),
            )
        }
    }
}

/** Square playlist card for the horizontal shelves. */
@Composable
fun CollectionCard(c: Collection, onClick: () -> Unit) {
    // Press feedback only: a small scale and dim, no looping motion.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, spring(stiffness = 700f), label = "card")
    Column(
        Modifier
            .width(if (c.kind == "track") 200.dp else 152.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(2.dp),
    ) {
        Box {
            AsyncImage(
                model = c.art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .then(if (c.kind == "track") Modifier.width(196.dp).height(110.dp) else Modifier.size(148.dp))
                    .clip(RoundedCornerShape(if (c.kind == "track") 14.dp else 20.dp)),
            )
            // Scrim only where text could sit; never a full-frame overlay.
            Box(
                Modifier
                    .then(if (c.kind == "track") Modifier.width(196.dp).height(110.dp) else Modifier.size(148.dp))
                    .clip(RoundedCornerShape(if (c.kind == "track") 14.dp else 20.dp))
                    .background(
                        Brush.verticalGradient(
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.35f),
                        ),
                    ),
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            c.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            c.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One feed shelf. The shape follows `kind` so the page is not a stack of identical carousels:
 * track shelves draw a short list of playable rows, everything else keeps the cover carousel.
 *
 * Track shelves are capped at [maxRows] — the full list is one tap away on the detail page, and a
 * feed that never ends is a feed nobody scrolls.
 */
@Composable
fun ShelfRow(
    title: String,
    items: List<Collection>,
    kind: String,
    onOpen: (Collection) -> Unit,
    maxRows: Int = 4,
) {
    if (items.isEmpty()) return
    Column(Modifier.padding(bottom = 18.dp)) {
        SectionTitle(title, items.size)
        if (kind == "tracks") {
            items.take(maxRows).forEach { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(c) }
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = c.art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            c.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.id }) { c -> CollectionCard(c) { onOpen(c) } }
            }
        }
    }
}

@Composable
fun TrackRow(
    t: Track,
    selected: Boolean,
    liked: Boolean = false,
    onLike: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        if (onLike != null) {
            IconButton(onClick = onLike, modifier = Modifier.size(40.dp)) {
                // Filled + primary when liked: the border-only heart never showed state at all.
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (liked) "Unlike" else "Like",
                    modifier = Modifier.size(19.dp),
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.RemoveCircleOutline,
                    contentDescription = "Remove from playlist",
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
