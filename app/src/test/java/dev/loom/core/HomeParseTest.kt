package dev.loom.core

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shape mismatch that shipped in 0.3.0: the home feed hands a bare array to the
 * collection parser while every other endpoint hands an envelope, so the raw JSON ended up
 * on screen as an error message.
 */
class HomeParseTest {
    private val payload = """
        {"ok":true,"data":[
          {"title":"Trending community playlists","items":[
            {"id":"VLRDCLAK5uy_n4jth1BoYT7FxNFJAGmJw5WQFF_ZzBTBM","params":"ggMEgALjBA%3D%3D",
             "title":"Indo Indie On Repeat","subtitle":"Bernadya, Nadin Amizah",
             "art":"https://yt3.googleusercontent.com/x=w544-h544","kind":"playlist"},
            {"id":"","title":"broken","subtitle":"","art":"","params":"","kind":"playlist"}
          ]},
          {"title":"Mellow Pop Classics","items":[]}
        ]}
    """.trimIndent()

    @Test
    fun homeParsesShelvesAndSkipsBlankIds() {
        val shelves = Music.homeForTest(payload)
        assertEquals(2, shelves.size)
        assertEquals("Trending community playlists", shelves[0].title)
        assertEquals(1, shelves[0].items.size) // blank id dropped
        val c = shelves[0].items[0]
        assertEquals("Indo Indie On Repeat", c.title)
        assertEquals("ggMEgALjBA%3D%3D", c.params)
        assertEquals("Bernadya, Nadin Amizah", c.subtitle)
        assertTrue(shelves[1].items.isEmpty())
    }

    @Test
    fun envelopeErrorsBecomeCoreException() {
        val err = """{"ok":false,"error":"radio: no tracks"}"""
        val e = runCatching { Music.shelvesForTest(err) }.exceptionOrNull()
        assertTrue(e is CoreException)
        assertEquals("radio: no tracks", e?.message)
    }

    @Test
    fun forYouReturnsShelfRowsNotOneFlatList() {
        val payload = """
            {"ok":true,"data":[
              {"title":"New albums & singles","items":[
                {"id":"MPREb_x","params":"","title":"Album","subtitle":"Artist","art":"","kind":"playlist"}]},
              {"title":"New music videos","items":[
                {"id":"dQw4w9WgXcQ","params":"","title":"Video","subtitle":"Artist","art":"","kind":"track"}]}
            ]}
        """.trimIndent()
        val shelves = Music.shelvesForTest(payload)
        assertEquals(2, shelves.size)
        assertEquals("New albums & singles", shelves[0].title)
        assertEquals("playlist", shelves[0].items[0].kind)
        assertEquals("track", shelves[1].items[0].kind)
    }

    @Test
    fun bareArrayIsNotMistakenForEnvelope() {
        // The old code passed a bare JSONArray here and org.json threw with the payload inline.
        val bare = JSONArray().put(org.json.JSONObject().put("id", "abc")).toString()
        val e = runCatching { Music.shelvesForTest(bare) }.exceptionOrNull()
        assertTrue("expected a parse failure, got $e", e != null)
        assertTrue("message must be short, was ${e?.message?.length}", (e?.message?.length ?: 0) < 400)
    }
}
