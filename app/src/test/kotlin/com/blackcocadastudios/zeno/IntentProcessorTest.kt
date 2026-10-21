package com.blackcocadastudios.zeno

import android.app.Application
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import com.blackcocadastudios.zeno.browser.search.SearchEngines
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class IntentProcessorTest {
    private val ddg = SearchEngines.DuckDuckGo

    private fun parse(intent: Intent) = IntentProcessor.parse(intent, ddg)

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    @Test
    fun `web links from other apps open`() {
        assertEquals(IntentAction.Open("https://example.com/a?b=1"), parse(view("https://example.com/a?b=1")))
        assertEquals(IntentAction.Open("http://example.com/"), parse(view("http://example.com/")))
    }

    @Test
    fun `other schemes from other apps are ignored`() {
        listOf(
            "file:///data/data/com.blackcocadastudios.zeno/files/session.json",
            "javascript:alert(1)",
            "about:config",
            "content://com.example/x",
            "moz-extension://abc/page.html",
        ).forEach { assertEquals(IntentAction.None, parse(view(it)), it) }
        assertEquals(IntentAction.None, parse(Intent(Intent.ACTION_VIEW)))
    }

    @Test
    fun `shared text opens its link or searches it`() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Olha: https://zeno.example/x.")
        assertEquals(IntentAction.Open("https://zeno.example/x"), parse(send))

        val selected = Intent(Intent.ACTION_PROCESS_TEXT).putExtra(Intent.EXTRA_PROCESS_TEXT, "jardim zen" as CharSequence)
        assertEquals(IntentAction.Open(ddg.searchUrl("jardim zen")), parse(selected))

        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, "file:///sdcard/x")
        assertEquals(IntentAction.Open(ddg.searchUrl("file:///sdcard/x")), parse(search))

        assertEquals(IntentAction.None, parse(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "  ")))
    }

    @Test
    fun `launcher shortcuts open the home page`() {
        assertEquals(IntentAction.NewTab(private = false), parse(Intent(IntentProcessor.ACTION_NEW_TAB)))
        assertEquals(IntentAction.NewTab(private = true), parse(Intent(IntentProcessor.ACTION_NEW_PRIVATE_TAB)))
        assertEquals(IntentAction.None, parse(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun `intents replayed from recents are recognised`() {
        assertTrue(IntentProcessor.isReplay(view("https://a.example/").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)))
        assertFalse(IntentProcessor.isReplay(view("https://a.example/")))
    }
}
