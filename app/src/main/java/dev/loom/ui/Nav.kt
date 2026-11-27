package dev.loom.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

private fun Tab.icon(): ImageVector = when (this) {
    Tab.PLAYLIST -> Icons.Rounded.LibraryMusic
    Tab.FOR_YOU -> Icons.Rounded.Favorite
    Tab.HOME -> Icons.Rounded.Home
    Tab.SEARCH -> Icons.Rounded.Search
    Tab.SETTINGS -> Icons.Rounded.Settings
}

private val BarHeight = 62.dp
private val KnobSize = 46.dp
/** Clear air between the knob and the board. This is what makes it read as detached. */
private val KnobGap = 7.dp

/**
 * Floating pill where the active tab detaches from the board: the knob sits entirely above the
 * bar with a gap and a drop shadow, so it reads as lifted off rather than bulging out of it.
 * Nothing about the layout animates — fixed bar height, equal slots — so the knob can travel
 * without reflowing the row.
 */
@Composable
fun FloatingNav(current: Tab, haze: HazeState, onPick: (Tab) -> Unit) {
    val tabs = Tab.entries
    // Reserve the knob's own space above the bar so nothing is clipped.
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = KnobSize + KnobGap)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(BarHeight)) {
            val slot = maxWidth / tabs.size
            val index = tabs.indexOf(current).coerceAtLeast(0)

            val slide by animateFloatAsState(
                targetValue = index * slot.value,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "knobSlide",
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(BarHeight)
                    .glass(haze, corner = 31, blur = 30),
            )

            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                tabs.forEach { tab ->
                    val active = tab == current
                    Box(
                        Modifier
                            .width(slot)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onPick(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!active) {
                            Icon(
                                tab.icon(),
                                contentDescription = tab.label,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }

            // The detached knob. Drawn last, fully outside the bar's bounds.
            Box(
                Modifier.offset(x = slide.dp).width(slot).height(BarHeight),
                contentAlignment = Alignment.TopCenter,
            ) {
                val primary = MaterialTheme.colorScheme.primary
                Box(
                    Modifier
                        .offset(y = -(KnobSize + KnobGap))
                        .size(KnobSize)
                        .shadow(16.dp, CircleShape, ambientColor = primary, spotColor = primary)
                        .background(primary, CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        current.icon(),
                        contentDescription = current.label,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(23.dp),
                    )
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
