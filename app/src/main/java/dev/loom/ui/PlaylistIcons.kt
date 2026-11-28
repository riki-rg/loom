package dev.loom.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Rocket
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The icons a user can pin on a playlist. Names are what get persisted, so this list is a storage
 * contract: append only, never rename — an unknown name falls back to the mosaic.
 */
object PlaylistIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "music" to Icons.Rounded.MusicNote,
        "queue" to Icons.Rounded.QueueMusic,
        "heart" to Icons.Rounded.Favorite,
        "star" to Icons.Rounded.Star,
        "fire" to Icons.Rounded.LocalFireDepartment,
        "night" to Icons.Rounded.Bedtime,
        "bolt" to Icons.Rounded.Bolt,
        "chill" to Icons.Rounded.Forest,
        "game" to Icons.Rounded.SportsEsports,
        "drive" to Icons.Rounded.Flight,
        "pet" to Icons.Rounded.Pets,
        "rocket" to Icons.Rounded.Rocket,
    )

    fun of(name: String): ImageVector? = all.firstOrNull { it.first == name }?.second
}
