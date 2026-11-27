package dev.loom.core

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Import plumbing that can be checked without the network: the provider mapping and the bulk
 * store write. The network side is covered by `core`'s ignored live test, which hit all three
 * providers for real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun track(id: String) = Track(id, "t$id", "a", "", Source.SPOTIFY, 1000)

    @Test
    fun importableProvidersAreSpotifyYtDeezerAndSoundcloud() {
        assertEquals(
            listOf(Source.SPOTIFY, Source.YT_MUSIC, Source.DEEZER, Source.SOUNDCLOUD),
            Music.importable,
        )
    }

    @Test
    fun providerKeysMatchTheRustSide() {
        // A typo here surfaces only as "unknown provider" at runtime, after a network round trip.
        assertEquals("spotify", Music.providerKey(Source.SPOTIFY))
        assertEquals("ytm", Music.providerKey(Source.YT_MUSIC))
        assertEquals("deezer", Music.providerKey(Source.DEEZER))
        assertEquals("soundcloud", Music.providerKey(Source.SOUNDCLOUD))
    }

    @Test
    fun bulkAddSkipsDuplicates() {
        val store = Store(app)
        store.createPlaylist("mix")
        assertEquals(2, store.addAllToPlaylist("mix", listOf(track("1"), track("2"))))
        // Same ids again: nothing added, no duplicate rows.
        assertEquals(0, store.addAllToPlaylist("mix", listOf(track("1"), track("2"))))
        // One new track plus two known ones.
        assertEquals(1, store.addAllToPlaylist("mix", listOf(track("2"), track("3"))))
        assertEquals(3, store.playlists.first { it.name == "mix" }.tracks.size)
    }

    @Test
    fun bulkAddToMissingPlaylistIsANoOp() {
        assertEquals(0, Store(app).addAllToPlaylist("nope", listOf(track("1"))))
    }
}
