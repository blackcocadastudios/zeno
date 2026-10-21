package com.blackcocadastudios.zeno.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blackcocadastudios.zeno.browser.session.SessionSnapshot
import com.blackcocadastudios.zeno.browser.session.TabSnapshot
import com.blackcocadastudios.zeno.data.bookmarks.BookmarksRepository
import com.blackcocadastudios.zeno.data.db.ZenoDatabase
import com.blackcocadastudios.zeno.data.db.likePattern
import com.blackcocadastudios.zeno.data.downloads.DownloadStatus
import com.blackcocadastudios.zeno.data.downloads.DownloadsRepository
import com.blackcocadastudios.zeno.data.history.HistoryRepository
import com.blackcocadastudios.zeno.data.session.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StorageTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: ZenoDatabase
    private var now = 1_760_000_000_000L
    private val history by lazy { HistoryRepository(db.history()) { now } }
    private val bookmarks by lazy { BookmarksRepository(db.bookmarks()) { now } }
    private val downloads by lazy { DownloadsRepository(db.downloads()) { now } }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ZenoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `visits are counted, reloads and non-web pages are not`() = runTest {
        assertTrue(history.onVisited("https://a.example/x", isRedirect = false, isReload = false))
        now += 1_000
        history.onVisited("https://a.example/x", isRedirect = false, isReload = false)
        history.onVisited("https://a.example/x", isRedirect = false, isReload = true)
        assertFalse(history.onVisited("about:config", isRedirect = false, isReload = false))
        assertFalse(history.onVisited("moz-extension://abc/popup.html", isRedirect = false, isReload = false))
        history.updateTitle("https://a.example/x", "Página X")

        val entry = history.recent().first().single()
        assertEquals(2, entry.visitCount)
        assertEquals("Página X", entry.title)
        assertEquals("a.example", entry.host)
        assertEquals(now, entry.lastVisitedAt)
        assertEquals(listOf(true, false), history.visited(listOf("https://a.example/x", "https://b.example/")))
    }

    @Test
    fun `top sites are one per site, most visited first`() = runTest {
        repeat(5) { history.onVisited("https://a.example/x", false, false) }
        repeat(3) { history.onVisited("https://a.example/y", false, false) }
        repeat(4) { history.onVisited("https://b.example/", false, false) }
        history.onVisited("https://c.example/", false, false)

        val top = history.topSites(limit = 2).first()
        assertEquals(listOf("https://a.example/x", "https://b.example/"), top.map { it.url })
    }

    @Test
    fun `address bar search puts sites starting with the text first`() = runTest {
        repeat(3) { history.onVisited("https://www.github.com/zeno", false, false) }
        repeat(10) { history.onVisited("https://gist.example/x", false, false) }
        history.updateTitle("https://gist.example/x", "my github gists")
        repeat(50) { history.onVisited("https://example.com/github", false, false) }

        val results = history.search("git")
        assertEquals("https://www.github.com/zeno", results.first().url)
        assertEquals(3, results.size)
        assertTrue(history.search("   ").isEmpty())
    }

    @Test
    fun `search text is literal, not a pattern`() = runTest {
        history.onVisited("https://a.example/1", false, false)
        history.updateTitle("https://a.example/1", "100% grátis")
        history.onVisited("https://b.example/2", false, false)
        history.updateTitle("https://b.example/2", "1000 coisas")

        assertEquals(listOf("https://a.example/1"), history.search("100%").map { it.url })
        assertEquals("%a\\_b\\%c\\\\%", likePattern(" a_b%c\\ "))
    }

    @Test
    fun `history can be cleared by site and by time`() = runTest {
        history.onVisited("https://a.example/1", false, false)
        history.onVisited("https://a.example/2", false, false)
        now += 60_000
        history.onVisited("https://b.example/", false, false)

        history.deleteSince(now)
        assertEquals(listOf("a.example", "a.example"), history.recent().first().map { it.host })
        history.deleteSite("a.example")
        assertTrue(history.recent().first().isEmpty())
    }

    @Test
    fun `bookmarks pin to the home page in order`() = runTest {
        bookmarks.add("https://a.example/", "A", pinned = true)
        now += 1
        bookmarks.add("https://b.example/", "", pinned = false)
        now += 1
        bookmarks.add("https://c.example/", "C", pinned = true)
        // Adding an existing bookmark as pinned pins it, at the end.
        bookmarks.add("https://b.example/", "B", pinned = true)

        assertEquals(listOf("https://a.example/", "https://c.example/", "https://b.example/"), bookmarks.pinned.first().map { it.url })
        assertEquals("https://b.example/", bookmarks.all.first().first { it.url == "https://b.example/" }.title)

        bookmarks.rename("https://b.example/", "Bê")
        bookmarks.rename("https://b.example/", "  ")
        assertEquals("Bê", bookmarks.all.first().first { it.url == "https://b.example/" }.title)

        bookmarks.setPinned("https://a.example/", false)
        assertEquals(listOf("https://c.example/", "https://b.example/"), bookmarks.pinned.first().map { it.url })
        assertTrue(bookmarks.isBookmarked("https://a.example/").first())
        bookmarks.remove("https://a.example/")
        assertFalse(bookmarks.isBookmarked("https://a.example/").first())
    }

    @Test
    fun `downloads cut off by a restart are failures`() = runTest {
        val running = downloads.start("https://a.example/f.pdf", "f.pdf", "application/pdf", 100)
        val done = downloads.start("https://a.example/g.pdf", "g.pdf", "application/pdf", null)
        downloads.progress(done, 50, 50)
        downloads.finish(done, DownloadStatus.Completed, "content://downloads/1")

        downloads.markInterrupted()
        assertEquals(DownloadStatus.Failed, downloads.get(running)!!.status)
        val completed = downloads.get(done)!!
        assertEquals(DownloadStatus.Completed, completed.status)
        assertEquals(1f, completed.progress)
        assertEquals("content://downloads/1", completed.contentUri)

        downloads.clearFinished()
        assertTrue(downloads.all.first().isEmpty())
    }

    @Test
    fun `session survives a write and ignores a corrupt file`() = runTest {
        val directory = folder.newFolder()
        val store = SessionStore(directory)
        assertNull(store.read())

        val snapshot = SessionSnapshot(tabs = listOf(TabSnapshot(id = "a", url = "https://a.example/", title = "A")), selectedTabId = "a")
        store.write(snapshot)
        assertEquals(snapshot, store.read())

        File(directory, "session.json").writeText("{ not json")
        assertNull(store.read())
        store.clear()
        assertNull(store.read())
    }
}
