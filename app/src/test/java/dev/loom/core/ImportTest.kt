package dev.loom.core

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Import plumbing that can be checked without the network: the provider mapping and the bulk
 * store write. The network side is covered by `core`'s ignored live tests, which hit every
 * reachable provider for real.
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

    /**
     * The import payload is `{name, tracks:[…]}` inside the `{ok,data}` envelope. Reading it must
     * not go through `unwrap()` on the inner array: that parses the text as a JSONObject and
     * throws, which is exactly how the reported "Value [{…}]" error happened.
     */
    @Test
    fun importPayloadParsesTracksAndName() {
        val payload = JSONObject(
            """
            {"ok":true,"data":{"name":"Banda Neira","tracks":[
              {"id":"abc","title":"Bertaut","artist":"Nadin Amizah","art":"","duration":200000},
              {"id":"def","title":"Senja","artist":"Kunto Aji","art":"","duration":180000}
            ]}}
            """.trimIndent(),
        )
        val data = payload.getJSONObject("data")
        assertEquals("Banda Neira", data.getString("name"))
        val arr = data.getJSONArray("tracks")
        assertEquals(2, arr.length())
        assertEquals("Bertaut", arr.getJSONObject(0).getString("title"))
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

    /** Create / read / update / delete on a user playlist. */
    @Test
    fun playlistCrudRoundTrip() {
        val store = Store(app)
        assertTrue(store.createPlaylist("Latihan"))
        // Create is idempotent by name, case-insensitively.
        assertTrue(!store.createPlaylist("latihan"))

        assertEquals(2, store.addAllToPlaylist("Latihan", listOf(track("1"), track("2"))))
        assertEquals(2, store.playlists.first { it.name == "Latihan" }.tracks.size)

        // Update: rename keeps the tracks, rejects a duplicate name.
        assertTrue(!store.renamePlaylist("Latihan", "Latihan"))
        assertTrue(store.createPlaylist("Lain"))
        assertTrue(!store.renamePlaylist("Latihan", "Lain"))
        assertTrue(store.renamePlaylist("Latihan", "  Latihan Baru  "))
        assertEquals(2, store.playlists.first { it.name == "Latihan Baru" }.tracks.size)

        // Delete one track, not the playlist.
        assertTrue(store.removeFromPlaylist("Latihan Baru", track("1")))
        assertTrue(!store.removeFromPlaylist("Latihan Baru", track("1")))
        assertEquals(1, store.playlists.first { it.name == "Latihan Baru" }.tracks.size)

        // Delete the playlist itself.
        store.removePlaylist("Latihan Baru")
        assertTrue(store.playlists.none { it.name == "Latihan Baru" })
    }

    @Test
    fun bulkAddToMissingPlaylistIsANoOp() {
        assertEquals(0, Store(app).addAllToPlaylist("nope", listOf(track("1"))))
    }
}
