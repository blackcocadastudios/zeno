package com.blackcocadastudios.zeno.browser

import com.blackcocadastudios.zeno.browser.search.SearchEngine
import com.blackcocadastudios.zeno.browser.search.SearchEngines
import com.blackcocadastudios.zeno.browser.url.UrlDisplay
import com.blackcocadastudios.zeno.browser.url.UrlInput
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlAndSearchTest {
    private val ddg = SearchEngines.DuckDuckGo

    private fun resolve(input: String) = UrlInput.resolve(input, ddg)

    @Test
    fun `addresses become urls`() {
        assertEquals("https://example.com", resolve("example.com"))
        assertEquals("https://example.com/a?b=1#c", resolve("  example.com/a?b=1#c "))
        assertEquals("https://pt.wikipedia.org/wiki/Zen", resolve("pt.wikipedia.org/wiki/Zen"))
        assertEquals("https://example.com:8443/x", resolve("example.com:8443/x"))
        assertEquals("http://localhost:3000", resolve("localhost:3000"))
        assertEquals("http://localhost", resolve("localhost"))
        assertEquals("https://192.168.0.1/admin", resolve("192.168.0.1/admin"))
        assertEquals("https://xn--e1afmkfd.xn--p1ai", resolve("xn--e1afmkfd.xn--p1ai"))
        assertEquals("https://café.fr", resolve("café.fr"))
    }

    @Test
    fun `explicit schemes are kept`() {
        assertEquals("http://example.com", resolve("http://example.com"))
        assertEquals("HTTPS://Example.com/Path", resolve("HTTPS://Example.com/Path"))
        assertEquals("about:config", resolve("about:config"))
        assertEquals("mailto:a@b.c", resolve("mailto:a@b.c"))
        assertEquals("view-source:https://a.b", resolve("view-source:https://a.b"))
    }

    @Test
    fun `everything else is searched`() {
        assertEquals("https://duckduckgo.com/?q=what+is+zen", resolve("what is zen"))
        assertEquals("https://duckduckgo.com/?q=node.js", resolve("node.js"))
        assertEquals("https://duckduckgo.com/?q=2.5", resolve("2.5"))
        assertEquals("https://duckduckgo.com/?q=zeno", resolve("zeno"))
        assertEquals("https://duckduckgo.com/?q=a%26b%3Dc", resolve("a&b=c"))
        assertEquals("https://duckduckgo.com/?q=999.1.1.1", resolve("999.1.1.1"))
        assertNull(resolve("   "))
    }

    @Test
    fun `javascript urls are never run from the address bar`() {
        assertEquals("https://duckduckgo.com/?q=javascript%3Aalert%281%29", resolve("javascript:alert(1)"))
        assertFalse(UrlInput.isUrl("JavaScript:void(0)"))
        assertTrue(UrlInput.isUrl("example.org"))
    }

    @Test
    fun `display strips noise`() {
        assertEquals("github.com", UrlDisplay.shortHost("https://www.github.com/x/y"))
        assertEquals("pt.wikipedia.org", UrlDisplay.shortHost("https://pt.wikipedia.org/wiki/A"))
        assertEquals("example.com", UrlDisplay.shortHost("https://m.example.com/"))
        assertEquals("wikipedia", UrlDisplay.siteName("https://pt.wikipedia.org/wiki/A"))
        // uol.com.br hosts many sites; the one before it is the site.
        assertEquals("folha", UrlDisplay.siteName("https://www1.folha.uol.com.br/"))
        assertEquals("bbc", UrlDisplay.siteName("https://www.bbc.co.uk/news"))
        assertEquals("G", UrlDisplay.sealLetter("https://github.com"))
    }

    @Test
    fun `search results show their terms`() {
        assertEquals("zen garden", UrlDisplay.searchTerms("https://duckduckgo.com/?q=zen+garden&t=h_", ddg))
        assertEquals("ação", UrlDisplay.searchTerms(SearchEngines.Google.searchUrl("ação"), SearchEngines.Google))
        assertNull(UrlDisplay.searchTerms("https://duckduckgo.com/about", ddg))
        assertNull(UrlDisplay.searchTerms("https://example.com/?q=x", ddg))
        assertEquals("https://example.com/?q=x", UrlDisplay.editableText("https://example.com/?q=x", ddg))
    }

    @Test
    fun `suggestions parse opensearch and survive junk`() {
        assertEquals(listOf("zen", "zen garden"), SearchEngine.parseSuggestions("""["ze",["zen","zen garden","zen"]]"""))
        assertEquals(listOf("a"), SearchEngine.parseSuggestions("""["q",["a"],["desc"],["https://x"]]"""))
        assertEquals(emptyList(), SearchEngine.parseSuggestions("<html>"))
        assertEquals(emptyList(), SearchEngine.parseSuggestions("""{"q":1}"""))
        assertEquals(2, SearchEngine.parseSuggestions("""["q",["a","b","c"]]""", limit = 2).size)
    }

    @Test
    fun `site names come from the registrable domain, or the hosted site`() {
        assertEquals("wikipedia", UrlDisplay.siteName("https://pt.wikipedia.org/wiki/Zen"))
        assertEquals("folha", UrlDisplay.siteName("https://www.folha.uol.com.br/"))
        assertEquals("uol", UrlDisplay.siteName("https://www.uol.com.br/"))
        assertEquals("g1", UrlDisplay.siteName("https://g1.globo.com/"))
        assertEquals("zeno", UrlDisplay.siteName("https://zeno.github.io/docs"))
        assertEquals("nhk", UrlDisplay.siteName("https://www.nhk.or.jp/"))
        assertEquals("localhost", UrlDisplay.siteName("http://localhost:8080/"))
        assertEquals("F", UrlDisplay.sealLetter("https://www.folha.uol.com.br/"))
    }

    @Test
    fun `shared text opens its first link or is searched`() {
        fun shared(text: String) = UrlInput.fromSharedText(text, ddg)
        assertEquals("https://example.com/a?b=1", shared("Olha isso: https://example.com/a?b=1."))
        assertEquals("https://en.wikipedia.org/wiki/Zen_(disambiguation)", shared("(see https://en.wikipedia.org/wiki/Zen_(disambiguation))"))
        assertEquals("http://a.example/x", shared("\"http://a.example/x\", disse ele"))
        assertEquals("https://zeno.example", shared("zeno.example"))
        assertEquals(ddg.searchUrl("jardim zen em Kyoto"), shared("  jardim zen em Kyoto "))
        assertNull(shared("   "))
        assertEquals(ddg.searchUrl("javascript:alert(1)"), shared("javascript:alert(1)"))
        // Another app cannot make Zeno open local files or internal pages.
        assertEquals(ddg.searchUrl("file:///data/data/com.blackcocadastudios.zeno/files"), shared("file:///data/data/com.blackcocadastudios.zeno/files"))
        assertEquals(ddg.searchUrl("about:config"), shared("about:config"))
        assertEquals(ddg.searchUrl("data:text/html,<b>x</b>"), shared("data:text/html,<b>x</b>"))
    }

    @Test
    fun `every engine builds valid urls`() {
        SearchEngines.all.forEach { engine ->
            val url = engine.searchUrl("café com leite")
            assertTrue(url.startsWith("https://"), url)
            assertEquals("café com leite", engine.termsFrom(url), engine.id)
            engine.suggestUrl("x")?.let { assertTrue(it.startsWith("https://")) }
        }
        assertEquals(SearchEngines.DuckDuckGo, SearchEngines.byId("nope"))
    }
}
