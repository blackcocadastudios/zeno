package com.blackcocadastudios.zeno.ui

import com.blackcocadastudios.zeno.browser.search.SearchEngines
import com.blackcocadastudios.zeno.browser.state.ContentState
import com.blackcocadastudios.zeno.browser.state.TabState
import com.blackcocadastudios.zeno.engine.SecurityInfo
import com.blackcocadastudios.zeno.ui.browser.addressDisplay
import com.blackcocadastudios.zeno.ui.toolbar.AddressKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AddressDisplayTest {
    private val ddg = SearchEngines.DuckDuckGo

    private fun display(url: String?, content: ContentState = ContentState()) =
        addressDisplay(url?.let { TabState("t", it, content = content) }, ddg)

    @Test
    fun `home shows the hint`() {
        val home = addressDisplay(null, ddg)
        assertNull(home.text)
        assertEquals(AddressKind.Home, home.kind)
        assertFalse(home.canReload)
        assertEquals(AddressKind.Home, display(null).kind)
    }

    @Test
    fun `pages show their host and how safe they are`() {
        val secure = display("https://www.example.com/a/b", ContentState(security = SecurityInfo(secure = true)))
        assertEquals("example.com", secure.text)
        assertEquals(AddressKind.Secure, secure.kind)
        // Loading https: the certificate is not known yet; no open lock flashes.
        assertEquals(AddressKind.Secure, display("https://example.com/", ContentState(loading = true)).kind)
        assertEquals(AddressKind.Insecure, display("https://expired.example/").kind)
        assertEquals(AddressKind.Insecure, display("http://example.com/").kind)
        assertEquals(AddressKind.Internal, display("about:config").kind)
    }

    @Test
    fun `reader mode shows the host of the page being read`() {
        val reader = "moz-extension://abc/reader.html?url=https%3A%2F%2Fwww.example.com%2Fartigo"
        val display = addressDisplay(TabState("t", reader), ddg) { url -> if (url == reader) "https://www.example.com/artigo" else null }
        assertEquals("example.com", display.text)
        assertEquals(AddressKind.Reader, display.kind)
    }

    @Test
    fun `searches show their terms`() {
        val search = display(ddg.searchUrl("jardim zen"))
        assertEquals("jardim zen", search.text)
        assertEquals(AddressKind.Search, search.kind)
    }
}
