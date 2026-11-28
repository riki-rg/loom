package dev.loom.core

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The download path is where a bug costs the user files, so it gets the checks.
 *
 * `all()` reads MediaStore, `safe()` decides filenames, and the playlist folder is what the
 * Download tab groups by. Robolectric's MediaStore is a stub, so these drive the resolver through
 * a fake cursor rather than proving the real provider accepts the insert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    /** Playlist names come from YouTube and contain anything, including slashes. */
    @Test
    fun safeNameStripsPathCharacters() {
        assertEquals("AC_DC", Downloads.safe("AC/DC"))
        assertEquals("a_b_c", Downloads.safe("a\\b:c"))
        assertEquals("Rock", Downloads.safe("  Rock  "))
        // A name that is only dots would be a hidden or relative path.
        assertEquals("x", Downloads.safe("x."))
        assertTrue(Downloads.safe("y".repeat(400)).length <= 120)
    }

    @Test
    fun fileNameIsTitleAndArtist() {
        val t = Track(id = "abc", title = "Satu Bulan", artist = "Bernadya", art = "", source = Source.YT_MUSIC, durationMs = 200_000)
        assertEquals("Satu Bulan - Bernadya.m4a", Downloads.fileName(t))
        // A missing artist must not leave a dangling separator.
        assertEquals("Satu Bulan.m4a", Downloads.fileName(t.copy(artist = "")))
        // A missing title falls back to the id, never an empty filename.
        assertEquals("abc.m4a", Downloads.fileName(t.copy(title = "")))
    }

    /** Two tracks with the same title and artist land on one file; that is intended, not a clash. */
    @Test
    fun sameTitleAndArtistProduceTheSameFile() {
        val a = Track(id = "1", title = "X", artist = "Y", art = "", source = Source.YT_MUSIC, durationMs = 1)
        val b = a.copy(id = "2")
        assertEquals(Downloads.fileName(a), Downloads.fileName(b))
    }

    @Test
    fun sizeTextSwitchesUnitAtOneMegabyte() {
        assertEquals("—", Downloads.sizeText(0))
        assertEquals("512 KB", Downloads.sizeText(512 * 1024L))
        assertEquals("1.5 MB", Downloads.sizeText((1.5 * 1048576).toLong()))
    }

    /**
     * `all()` must split loose tracks from playlist folders, and must not invent an entry for
     * `Music/Loom` itself.
     *
     * Robolectric 4.15 backs MediaStore with real SQLite, so this inserts rows the way the app
     * does and reads them back — the projection, the LIKE filter and the folder split all run.
     */
    @Test
    fun allGroupsByFolderAndSkipsTheRoot() {
        insert("Loose - A.m4a", "Music/Loom/", 1024L)
        insert("One - B.m4a", "Music/Loom/Rock/", 2048L)
        insert("Two - C.m4a", "Music/Loom/Rock/", 4096L)
        // A file from another app must never show up in the Download tab.
        insert("Someone Else.m4a", "Music/Other/", 999L)

        val grouped = Downloads.all(ctx)

        assertEquals(2, grouped.size)
        assertEquals(1, grouped[null]?.size)
        assertEquals("Loose - A", grouped[null]?.first()?.title)
        assertEquals(2, grouped["Rock"]?.size)
        assertEquals(4096L, grouped["Rock"]?.last()?.sizeBytes)
    }

    /** An empty library is the normal first launch, not an error. */
    @Test
    fun allReturnsEmptyWhenNothingIsDownloaded() {
        assertTrue(Downloads.all(ctx).isEmpty())
    }

    private fun insert(name: String, path: String, size: Long) {
        val values = android.content.ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, path)
            put(MediaStore.Audio.Media.SIZE, size)
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        assertNotNull(ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values))
    }

    /** Removing a download is a provider delete; a failure must report false, not throw. */
    @Test
    fun removeReportsFailureInsteadOfThrowing() {
        assertFalse(Downloads.remove(ctx, Uri.parse("content://media/external/audio/media/999")))
    }

    @Test
    fun downloadedTitleDropsTheExtension() {
        val d = Downloaded(Uri.parse("content://x/1"), "Satu Bulan - Bernadya.m4a", 10, null)
        assertEquals("Satu Bulan - Bernadya", d.title)
        assertNull(d.folder)
    }

}
