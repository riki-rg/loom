package dev.loom.core

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.loom.ui.LoomViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The bug the user reported: play a track, kill the app, reopen — the now-playing card was gone.
 * Cause was `queue`/`index` never being written. These drive the real Store and a real
 * ViewModel against the same SharedPreferences file, which is what a cold start looks like.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueRestoreTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun track(id: String) =
        Track(id, "title-$id", "artist", "art", Source.YT_MUSIC, 1000)

    @Test
    fun queueSurvivesColdStart() {
        Store(app).saveQueue(listOf(track("a"), track("b")), 1)

        // Fresh Store and ViewModel: same process, same prefs file, no in-memory carryover.
        val restored = LoomViewModel(app).state.value
        assertEquals(listOf("a", "b"), restored.queue.map { it.id })
        assertEquals(1, restored.index)
        assertEquals("b", restored.current?.id)
    }

    @Test
    fun emptyQueueStaysEmpty() {
        val fresh = LoomViewModel(app).state.value
        assertNull(fresh.current)
        assertEquals(emptyList<Track>(), fresh.queue)
    }

    /** The index must not point past the end when a stored queue shrank. */
    @Test
    fun indexIsClampedToQueue() {
        Store(app).saveQueue(listOf(track("only")), 7)
        val s = LoomViewModel(app).state.value
        assertEquals(0, s.index)
        assertEquals("only", s.current?.id)
    }
}
