package dev.loom.ui

import java.util.Locale

/**
 * Shared numbers for the floating island, so the window, the touch area and the notification all
 * agree. The island itself is plain views in `dev.loom.player.IslandOverlay`.
 *
 * Capped width and fixed height keep it identical on a 360dp phone and a 480dp one.
 */
object IslandSpec {
    /** Ceiling for wide phones and tablets. */
    const val MAX_W_DP = 420
    /** Margin kept on each side, so the capsule never touches the screen edge. */
    const val EDGE_DP = 14

    /**
     * The cap for a given screen. A fixed 420dp was wider than a 360dp phone, so the capsule ran
     * off both edges there. Never wider than the screen minus the margins.
     */
    fun maxWidthDp(screenWidthDp: Int): Int =
        minOf(MAX_W_DP, (screenWidthDp - 2 * EDGE_DP).coerceAtLeast(200))
    const val HEIGHT_DP = 52
    const val ART_DP = 36
    const val RADIUS_DP = 26
    const val PAD_DP = 8
    const val TOP_DP = 6

    fun clock(ms: Long): String {
        if (ms <= 0) return "0:00"
        val s = ms / 1000
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }
}
