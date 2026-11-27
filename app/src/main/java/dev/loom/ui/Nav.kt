package dev.loom.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

private fun Tab.icon(): ImageVector = when (this) {
    Tab.PLAYLIST -> Icons.Rounded.LibraryMusic
    Tab.FOR_YOU -> Icons.Rounded.Favorite
    Tab.HOME -> Icons.Rounded.Home
    Tab.SEARCH -> Icons.Rounded.Search
    Tab.SETTINGS -> Icons.Rounded.Settings
}

/**
 * Floating centered nav bar. A filled bubble marks the active tab: the pill grows, the label
 * fades in, and a soft halo behind it expands with a spring so the move reads as one gesture.
 * Labels only show for the active tab so five items fit a narrow phone.
 */
@Composable
fun FloatingNav(current: Tab, haze: HazeState, onPick: (Tab) -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .glass(haze, color = Color(0xFF12141C).copy(alpha = 0.35f), corner = 28, blur = 30)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val active = tab == current
                val pad by animateDpAsState(
                    if (active) 14.dp else 12.dp,
                    spring(dampingRatio = 0.55f, stiffness = 400f),
                    label = "navPad",
                )
                // Bubble: scales up and fades in; the reverse plays on the tab losing focus.
                val bubble by animateFloatAsState(
                    if (active) 1f else 0f,
                    spring(dampingRatio = 0.6f, stiffness = 500f),
                    label = "bubble",
                )
                val labelAlpha by animateFloatAsState(
                    if (active) 1f else 0f,
                    tween(if (active) 180 else 90, delayMillis = if (active) 60 else 0),
                    label = "label",
                )
                Box(
                    Modifier
                        .graphicsLayer {
                            // Halo swells slightly past the pill, then settles.
                            scaleX = 1f + (bubble * 0.06f)
                            scaleY = 1f + (bubble * 0.06f)
                        }
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onPick(tab) }
                        .padding(horizontal = pad, vertical = 10.dp),
                ) {
                    // The bubble itself, drawn behind the content.
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = bubble }
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            tab.icon(),
                            contentDescription = tab.label,
                            tint = if (active) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp),
                        )
                        if (labelAlpha > 0.01f) {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = labelAlpha),
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Empty state that does not look like an error. */
@Composable
fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
