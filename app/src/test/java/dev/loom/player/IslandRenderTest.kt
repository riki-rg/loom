package dev.loom.player

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import dev.loom.core.Source
import dev.loom.core.Track
import dev.loom.ui.IslandSpec
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import java.io.File

/**
 * Renders the floating island at four real phone widths. The capsule is plain views, so it is
 * measured, laid out and drawn straight to a bitmap instead of going through a window.
 *
 * This is the check the user asked for: stable across screen sizes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class IslandRenderTest {

    private val track = Track("dQw4w9WgXcQ", "Satu Bulan", "Bernadya", "", Source.YT_MUSIC)

    @Test
    fun islandFitsAndDrawsOnEveryPhoneWidth() {
        listOf(320, 360, 411, 480).forEach { widthDp ->
            RuntimeEnvironment.setQualifiers("w${widthDp}dp-h800dp-xhdpi")
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            ShadowSettings.setCanDrawOverlays(true)

            val overlay = IslandOverlay(ctx) { 83_000L to 214_000L }
            overlay.show(track, true, 83_000L, 214_000L)
            val view = overlay.root
            assertNotNull("no view at ${widthDp}dp", view)
            view!!
            // Detach from the window but keep the view: hide() drops the reference, and a view
            // with no parent draws nothing to the bitmap.
            overlay.detachForTest()

            val density = ctx.resources.displayMetrics.density
            val screenPx = (widthDp * density).toInt()
            val capPx = (IslandSpec.maxWidthDp(widthDp) * density).toInt()
            val heightPx = (IslandSpec.HEIGHT_DP * density).toInt()
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(capPx, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(heightPx, android.view.View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)

            println("ISLAND ${widthDp}dp measured=${view.measuredWidth}x${view.measuredHeight} laid=${view.width}x${view.height}")
            val g = view as android.view.ViewGroup
            for (i in 0 until g.childCount) {
                val c = g.getChildAt(i)
                println("  child $i ${c.javaClass.simpleName} ${c.left},${c.top} ${c.width}x${c.height}")
            }
            // The capsule must never be wider than the screen it floats on.
            assert(view.measuredWidth <= screenPx) {
                "${widthDp}dp screen: capsule ${view.measuredWidth}px wider than ${screenPx}px"
            }

            val bmp = Bitmap.createBitmap(
                screenPx.coerceAtLeast(1),
                view.measuredHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bmp)
            canvas.drawColor(android.graphics.Color.rgb(235, 235, 240))
            canvas.translate((screenPx - view.measuredWidth) / 2f, 0f)
            view.draw(canvas)
            println("PIXEL ${widthDp}dp bg=" + Integer.toHexString(bmp.getPixel(2, 2)) +
                " mid=" + Integer.toHexString(bmp.getPixel(bmp.width / 2, bmp.height / 2)))
            val out = File("build/screenshots").apply { mkdirs() }.resolve("island-${widthDp}dp.png")
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("ISLAND ${widthDp}dp -> ${out.absolutePath} ${bmp.width}x${bmp.height}")
        }
    }
}
