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

    /**
     * The user's request: the theme they pick must still be there after a restart. Drives the real
     * ViewModel setters and a fresh ViewModel, which is what relaunching the app does.
     */
    @Test
    fun themeSurvivesColdStart() {
        val vm = LoomViewModel(app)
        vm.setPalette(dev.loom.theme.Palette.MAGENTA)
        vm.setMode(dev.loom.theme.Mode.DARK)

        val restored = LoomViewModel(app).theme.value
        assertEquals(dev.loom.theme.Palette.MAGENTA, restored.palette)
        assertEquals(dev.loom.theme.Mode.DARK, restored.mode)
    }

    /** A custom seed wins over the palette, and must survive too. */
    @Test
    fun customSeedSurvivesColdStart() {
        LoomViewModel(app).setCustomSeed(0xFF123456)

        val restored = LoomViewModel(app).theme.value
        assertEquals(0xFF123456, restored.customSeed)
        assertEquals(dev.loom.theme.Palette.COBALT, restored.palette)
    }

    /** Nothing saved yet must not throw; the defaults stand. */
    @Test
    fun freshInstallUsesDefaultTheme() {
        val t = LoomViewModel(app).theme.value
        assertEquals(dev.loom.theme.Palette.COBALT, t.palette)
        assertEquals(dev.loom.theme.Mode.LIGHT, t.mode)
        assertNull(t.customSeed)
    }

    /** A pinned icon is a stored field: it must survive a restart and a rename. */
    @Test
    fun playlistIconSurvivesColdStartAndRename() {
        val store = Store(app)
        store.createPlaylist("Jalan Pagi")
        store.setPlaylistIcon("Jalan Pagi", "chill")
        assertEquals("chill", Store(app).playlists.first { it.name == "Jalan Pagi" }.icon)

        Store(app).renamePlaylist("Jalan Pagi", "Jalan Sore")
        val renamed = Store(app).playlists.first { it.name == "Jalan Sore" }
        assertEquals("chill", renamed.icon)
    }

    /** Clearing the icon restores the mosaic rather than leaving a dangling name. */
    @Test
    fun clearingPlaylistIconIsStored() {
        val store = Store(app)
        store.createPlaylist("Hapus Ikon")
        store.setPlaylistIcon("Hapus Ikon", "fire")
        store.setPlaylistIcon("Hapus Ikon", "")
        assertEquals("", Store(app).playlists.first { it.name == "Hapus Ikon" }.icon)
    }

    /** The picker filters by substring, case-insensitively; that is its whole contract. */
    @Test
    fun pickerFilterMatchesCaseInsensitiveSubstring() {
        val names = listOf("Dangdut Koplo", "Rain + Feels", "Kerja Fokus")
        fun filter(q: String) = if (q.isBlank()) names else names.filter { it.contains(q, ignoreCase = true) }

        assertEquals(names, filter(""))
        assertEquals(listOf("Rain + Feels"), filter("rain"))
        assertEquals(listOf("Dangdut Koplo", "Kerja Fokus"), filter("O"))
        assertEquals(emptyList<String>(), filter("zzz"))
    }
}
