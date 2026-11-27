package dev.loom.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState

private fun Tab.icon(): ImageVector = when (this) {
    Tab.PLAYLIST -> Icons.Rounded.LibraryMusic
    Tab.FOR_YOU -> Icons.Rounded.Favorite
    Tab.HOME -> Icons.Rounded.Home
    Tab.SEARCH -> Icons.Rounded.Search
    Tab.SETTINGS -> Icons.Rounded.Settings
}

/**
 * Floating centered nav bar with a sliding bubble.
 *
 * Every tab gets an equal-width slot and the bar keeps a fixed height, so moving the bubble
 * never changes the bar's own size. The bubble is one element translated behind the icons:
 * changing its position cannot reflow the row, which is what made the earlier version distort.
 */
@Composable
fun FloatingNav(current: Tab, haze: HazeState, onPick: (Tab) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .glass(haze, color = Color(0xFF12141C).copy(alpha = 0.35f), corner = 31, blur = 30),
        ) {
            val tabs = Tab.entries
            val slot = maxWidth / tabs.size
            val index = tabs.indexOf(current).coerceAtLeast(0)

            // One spring drives the bubble; no per-tab width animation anywhere.
            val slide by animateFloatAsState(
                targetValue = index * slot.value,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
                label = "bubbleSlide",
            )

            Box(
                Modifier
                    .offset(x = slide.dp)
                    .width(slot)
                    .fillMaxHeight()
                    .padding(horizontal = 7.dp, vertical = 7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )

            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                tabs.forEach { tab ->
                    val active = tab == current
                    val tint by animateFloatAsState(if (active) 1f else 0f, tween(200), label = "tint")
                    Column(
                        Modifier
                            .width(slot)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onPick(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            tab.icon(),
                            contentDescription = tab.label,
                            tint = lerpColor(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                MaterialTheme.colorScheme.onPrimary,
                                tint,
                            ),
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        // Always present so the row height never depends on the selection.
                        Text(
                            tab.label,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            color = lerpColor(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                MaterialTheme.colorScheme.onPrimary,
                                tint,
                            ),
                            modifier = Modifier.graphicsLayer { alpha = 0.55f + 0.45f * tint },
                        )
                    }
                }
            }
        }
    }
}

private fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = from.alpha + (to.alpha - from.alpha) * t,
)

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
