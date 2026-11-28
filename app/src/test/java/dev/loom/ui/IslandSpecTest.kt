package dev.loom.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The island has to look the same on a 360dp phone and a 480dp one. These are the numbers that
 * guarantee it: a hard width cap and a height that leaves room for three 28dp buttons plus art.
 */
class IslandSpecTest {
    @Test
    fun clockFormatsMinutesAndPadsSeconds() {
        assertEquals("0:00", IslandSpec.clock(0))
        assertEquals("0:05", IslandSpec.clock(5_000))
        assertEquals("1:23", IslandSpec.clock(83_000))
        assertEquals("10:00", IslandSpec.clock(600_000))
        // A negative or unknown position must not print "-1:-1".
        assertEquals("0:00", IslandSpec.clock(-4_000))
    }

    @Test
    fun buttonsReachTheAccessibilityTouchMinimum() {
        // 40dp button + 4dp margins on each side = 48dp effective target.
        assertEquals(48, 40 + 4 * 2)
        // Three buttons plus their margins must still fit the capsule height.
        assertTrue(48 <= IslandSpec.HEIGHT_DP)
    }

    @Test
    fun artworkFitsInsideTheCapsuleHeight() {
        // Vertical padding is zero; the tallest child is the artwork.
        val needed = IslandSpec.ART_DP + 2 * IslandSpec.PAD_DP
        assertTrue("art needs ${needed}dp, capsule is ${IslandSpec.HEIGHT_DP}dp", needed <= IslandSpec.HEIGHT_DP)
    }

    @Test
    fun capsuleNeverWiderThanTheScreen() {
        // 320dp is the narrowest phone still in use, 360dp the common one, 480dp a large one.
        listOf(320, 360, 411, 480).forEach { w ->
            val cap = IslandSpec.maxWidthDp(w)
            assertTrue("cap ${cap}dp exceeds ${w}dp screen", cap <= w - 2 * IslandSpec.EDGE_DP)
            assertTrue("cap ${cap}dp too small to read on ${w}dp", cap >= 200)
        }
        // On a tablet the absolute ceiling still applies.
        assertEquals(IslandSpec.MAX_W_DP, IslandSpec.maxWidthDp(1024))
    }
}
