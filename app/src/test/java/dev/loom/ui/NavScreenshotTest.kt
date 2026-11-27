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
import dev.loom.theme.LoomTheme
import dev.loom.theme.ThemeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * Renders the nav bar off-device. Robolectric's NATIVE graphics mode runs the real Skia
 * pipeline, so this rasterises the actual Compose tree rather than mocking it.
 *
 * The view hierarchy is drawn straight into a Bitmap instead of using captureToImage():
 * that helper blocks on the Compose test clock, and Haze requests a new frame every
 * frame it is on screen, so idle is never reached and the call times out.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class NavScreenshotTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            LoomTheme(ThemeState()) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0A0B10))) { content() }
            }
        }
        // Let layout, draw and the one-shot spring animations run without waiting for idle.
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

    @Test
    fun navHome() = shoot("nav-home") {
        val haze = HazeState()
        // A real source behind the bar: hazeEffect has something to sample and stops asking
        // for frames it cannot satisfy.
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            Box(Modifier.fillMaxSize().background(Color(0xFF1B2A4A)))
            FloatingNav(Tab.HOME, haze) {}
        }
    }

    @Test
    fun navForYou() = shoot("nav-foryou") {
        val haze = HazeState()
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            Box(Modifier.fillMaxSize().background(Color(0xFF1B2A4A)))
            FloatingNav(Tab.FOR_YOU, haze) {}
        }
    }
}
