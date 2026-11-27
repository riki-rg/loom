package dev.loom.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
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
/** How far the knob rises above the bar: a third of itself, so it reads as seated, not floating. */
private val KnobLift = KnobSize * 0.3f + 6.dp

/**
 * Floating pill where the active tab sinks into a socket in the board.
 *
 * The reference does not lift the knob above a flat edge — the board's own glass wraps up and
 * around the circle, so the knob looks set into it. That collar is a second glass box of the
 * same material behind the knob, not a boolean path on the bar: a path boolean would rebuild the
 * outline every frame while the knob slides, and this has to stay cheap.
 *
 * Nothing about the layout animates — fixed bar height, equal slots — so the knob can travel
 * without reflowing the row.
 */
@Composable
fun FloatingNav(current: Tab, haze: HazeState, onPick: (Tab) -> Unit) {
    val tabs = Tab.entries
    // Reserve the knob's own space above the bar so nothing is clipped.
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = KnobLift + 16.dp)) {
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

            // The socket. What makes the reference read as a carved cradle is not a filled
            // shape — it is the bar's own top hairline curving up and around the circle. So
            // this draws one arc, no second glass pass, no extra blur cost per frame.
            Box(
                Modifier
                    .offset(x = slide.dp)
                    .width(slot)
                    .height(BarHeight),
                contentAlignment = Alignment.TopCenter,
            ) {
                // In light mode the bar's own edge is white-on-white, so the socket has to be
                // an engraved dark line to read at all; in dark mode it is a lit edge.
                val hairline = if (MaterialTheme.colorScheme.surface.luminance() < 0.35f) {
                    Color.White.copy(alpha = 0.32f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)
                }
                val arc = KnobSize + 12.dp
                Canvas(Modifier.offset(y = -KnobLift).size(arc)) {
                    drawArc(
                        color = hairline,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }

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

            // The knob. Sunk into the collar rather than floating: it rises only a third of its
            // own height above the bar, which is what makes it read as seated in a socket.
            Box(
                Modifier.offset(x = slide.dp).width(slot).height(BarHeight),
                contentAlignment = Alignment.TopCenter,
            ) {
                val primary = MaterialTheme.colorScheme.primary
                // Halo first, behind everything: a radial wash is what reads as glow; a shadow
                // alone just reads as elevation.
                Box(
                    Modifier
                        .offset(y = -KnobLift - 16.dp)
                        .size(KnobSize + 44.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(primary.copy(alpha = 0.50f), primary.copy(alpha = 0f)),
                            ),
                            CircleShape,
                        ),
                )
                Box(
                    Modifier
                        .offset(y = -KnobLift)
                        .size(KnobSize)
                        .shadow(10.dp, CircleShape, ambientColor = primary, spotColor = primary)
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
                // Short dash inside the bar, marking which slot the knob belongs to.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 9.dp)
                        .width(16.dp)
                        .height(3.dp)
                        .background(primary, RoundedCornerShape(50)),
                )
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
