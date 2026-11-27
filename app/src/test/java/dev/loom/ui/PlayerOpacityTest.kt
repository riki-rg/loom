package dev.loom.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.loom.core.Source
import dev.loom.core.Track
import dev.loom.theme.LoomTheme
import dev.loom.theme.ThemeState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * Checks the bug the user reported: the player sheet used to be a translucent gradient, so the
 * library behind it showed through. This renders a red page under the sheet and asserts no red
 * survives — the sheet must be opaque. Also writes a PNG for eyeballing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class PlayerOpacityTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val track = Track(
        id = "dQw4w9WgXcQ",
        title = "Satu Bulan",
        artist = "Bernadya",
        art = "",
        source = Source.YT_MUSIC,
        durationMs = 200_000,
    )

    @Test
    fun sheetHidesThePageBehindIt() {
        val haze = HazeState()
        compose.setContent {
            LoomTheme(ThemeState()) {
                Box(Modifier.fillMaxSize().hazeSource(haze)) {
                    // Loud red page behind: any leak is obvious to the eye and to the assert.
                    Box(Modifier.fillMaxSize().background(Color(0xFFFF0000)))
                    NowPlayingSheet(
                        state = UiState(tab = Tab.HOME, queue = listOf(track), index = 0),
                        lyrics = null,
                        playlists = emptyList(),
                        positionMs = 42_000,
                        durationMs = 200_000,
                        liked = false,
                        shuffle = false,
                        repeat = false,
                        onShuffle = {},
                        onRepeat = {},
                        eq = "Flat",
                        onEq = {},
                        onLyrics = {},
                        onClose = {},
                        onToggle = {},
                        onSkip = {},
                        onSeek = {},
                        onLike = {},
                        onLikeTrack = {},
                        onPlayTrack = {},
                        onAddToPlaylist = {},
                        onCreatePlaylist = {},
                    )
                }
            }
        }
        repeat(6) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
        }

        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        ShadowLooper.idleMainLooper()
        view.draw(Canvas(bmp))

        val out = File("build/screenshots").apply { mkdirs() }.resolve("player-sheet.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // Count strongly-red pixels in the sheet's own area, skipping the outer 8px frame
        // where the sheet's rounded corners legitimately reveal the page.
        var red = 0
        var total = 0
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (y in 8 until bmp.height - 8) {
            for (x in 8 until bmp.width - 8) {
                val c = px[y * bmp.width + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r > 180 && g < 70 && b < 70) red++
                total++
            }
        }
        println("OPACITY ${out.absolutePath} redPixels=$red of $total")
        assertTrue("sheet let $red red pixels through; it must be opaque", red < total / 200)
    }
}
