package dev.loom.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.loom.core.LyricLine
import dev.loom.core.Source
import dev.loom.core.Track
import dev.loom.theme.LoomTheme
import dev.loom.theme.Mode
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
 * Renders the screens that changed this round. Robolectric's NATIVE graphics mode runs real
 * Skia, so these are actual rasterisations of the Compose tree.
 *
 * The view hierarchy is drawn into a Bitmap directly: captureToImage() blocks on the Compose
 * test clock and Haze asks for a new frame every frame, so idle is never reached.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenShotsTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            LoomTheme(ThemeState()) {
                Box(Modifier.fillMaxSize().background(Color.White)) { content() }
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
        val out = File("build/screenshots").apply { mkdirs() }.resolve("$name.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SHOT ${out.absolutePath} ${bmp.width}x${bmp.height}")
    }

    private val track = Track("dQw4w9WgXcQ", "Satu Bulan", "Bernadya", "", Source.YT_MUSIC, 200_000)

    @Test
    fun navDetached() = shoot("nav-detached") {
        val haze = HazeState()
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            Box(Modifier.fillMaxSize().background(Color(0xFFEAF2FF)))
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                FloatingNav(Tab.FOR_YOU, haze) {}
            }
        }
    }

    @Test
    fun pillWithProgress() = shoot("pill-progress") {
        val haze = HazeState()
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            Box(Modifier.fillMaxSize().background(Color(0xFFEAF2FF)))
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                NowPlayingPill(
                    state = UiState(queue = listOf(track), index = 0, playing = true),
                    haze = haze,
                    progress = 0.42f,
                    onToggle = {},
                    onSkip = {},
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun lyricsPage() = shoot("lyrics") {
        LyricsScreen(
            track = track,
            lines = listOf(
                LyricLine(0.0, "Belum ada satu bulan"),
                LyricLine(4.2, "Ku yakin masih ada sisa wangiku"),
                LyricLine(9.1, "Namun kau tampak baik saja"),
                LyricLine(13.4, "Bahkan senyummu lebih lepas"),
                LyricLine(18.0, "Sedang aku di sini hampir gila"),
            ),
            plain = null,
            positionMs = 9_500,
            durationMs = 200_000,
            playing = true,
            onClose = {},
            onToggle = {},
            onSkip = {},
        )
    }

    /** Grey row placeholders in the final row geometry, shown while a collection loads. */
    @Test
    fun skeletonDetail() = shoot("skeleton") {
        Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
            Text(
                "Kompilasi Lagu Pop",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            TrackSkeleton()
        }
    }

    /** Home with ranked track rows before the card shelves. */
    @Test
    fun homeWithTrackRows() = shoot("home-tracks") {
        val rows = List(6) { i ->
            Track("v$i", "Lagu Contoh ${i + 1}", "Artis ${i + 1}", "", Source.YT_MUSIC, 200_000)
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 20.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mix lagu", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    Text("Putar semua", color = MaterialTheme.colorScheme.primary)
                }
            }
            itemsIndexed(rows) { i, t ->
                NumberedTrackRow(i + 1, t, false, onPlay = {}, onLike = {}, onMenu = {})
            }
        }
    }

    /** The three-dot action sheet. */
    @Test
    fun trackActionsSheet() = shoot("actions-sheet") {
        TrackActionsBody(
            track = Track("v1", "Negoro Angin", "Denny Caknan", "", Source.YT_MUSIC, 200_000),
            liked = false,
            playlists = listOf("Favorit", "Nostalgia"),
            onDismiss = {},
            onPlayNext = {},
            onQueue = {},
            onLike = {},
            onSaveTo = {},
            onCreate = {},
            onShare = {},
            onArtist = {},
        )
    }

    /** A user playlist page: mosaic cover, name, count, play button, ranked rows. */
    @Test
    fun localPlaylistPage() = shoot("playlist-page") {
        val rows = List(5) { i ->
            Track("v$i", "Lagu ${i + 1}", "Artis ${i + 1}", "", Source.YT_MUSIC, 200_000)
        }
        Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                MosaicCover(rows, 104.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Latihan", style = MaterialTheme.typography.headlineMedium)
                    Text("5 lagu", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            rows.forEachIndexed { i, t ->
                NumberedTrackRow(i + 1, t, false, onPlay = {}, onLike = {}, onMenu = {}, onRemove = {})
            }
        }
    }

    /** The default theme must actually be light: a light surface with a blue accent. */
    @Test
    fun defaultThemeIsLightBlue() {
        val s = ThemeState()
        assertTrue("default mode must be light", s.mode == Mode.LIGHT)
        assertTrue("default accent must be blue", s.palette.label == "Cobalt")
    }
}
