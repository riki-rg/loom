package dev.loom.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.loom.core.Source
import dev.loom.core.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * Regressions that only show up on a device: a rebuilt window, a frozen clock, a background
 * service start. Each of these was a real bug in the first overlay build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandBehaviorTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val t1 = Track("a", "Satu Bulan", "Bernadya", "", Source.YT_MUSIC)
    private val t2 = Track("b", "Negoro Angin", "Denny Caklan", "", Source.SOUNDCLOUD)

    @Test
    fun repeatedShowKeepsOneWindow() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = IslandOverlay(ctx) { 0L to 0L }
        overlay.show(t1, true, 0L, 100_000L)
        val first = overlay.root
        assertNotNull(first)
        // A player event per second used to rebuild and leak a window each time.
        overlay.show(t1, true, 1_000L, 100_000L)
        overlay.show(t2, true, 0L, 90_000L)
        assertEquals("a second window was built", first, overlay.root)
    }

    @Test
    fun noWindowWithoutPermission() {
        ShadowSettings.setCanDrawOverlays(false)
        val overlay = IslandOverlay(ctx) { 0L to 0L }
        overlay.show(t1, true, 0L, 100_000L)
        assertNull("overlay attached without the permission", overlay.root)
    }

    @Test
    fun hideClearsTheWindow() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = IslandOverlay(ctx) { 0L to 0L }
        overlay.show(t1, true, 0L, 100_000L)
        overlay.hide()
        assertNull(overlay.root)
    }
}
