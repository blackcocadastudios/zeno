package com.blackcocadastudios.zeno.browser

import com.blackcocadastudios.zeno.browser.session.SessionSnapshot
import com.blackcocadastudios.zeno.browser.session.TabSnapshot
import com.blackcocadastudios.zeno.browser.state.TabOrigin
import com.blackcocadastudios.zeno.engine.EngineSessionState
import com.blackcocadastudios.zeno.engine.PromptRequest
import com.blackcocadastudios.zeno.engine.WindowRequest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserControllerTest {
    private var now = 1_000L
    private var nextId = 0
    private val engine = FakeEngine()
    private val controller = BrowserController(engine, clock = { now }, newId = { "t${nextId++}" })

    private val state get() = controller.state.value

    @Test
    fun `new tab with url creates a renderer and loads it`() {
        val id = controller.newTab("https://example.com")
        assertEquals(id, state.selectedTabId)
        assertEquals(listOf("https://example.com"), engine.created.single().loaded)
        assertTrue(state.tab(id)!!.isLive)
        assertEquals(engine.created.single(), engine.extensions.lastActive)
    }

    @Test
    fun `home tab has no renderer until something loads`() {
        val id = controller.newTab()
        assertTrue(engine.created.isEmpty())
        assertNull(controller.sessionFor(id))

        controller.load("https://zeno.example/")
        assertEquals(listOf("https://zeno.example/"), engine.created.single().loaded)
        assertEquals("https://zeno.example/", state.tab(id)!!.url)
    }

    @Test
    fun `load without tabs opens one`() {
        controller.load("https://a.example/")
        assertEquals(1, state.tabs.size)
        assertEquals("https://a.example/", state.selectedTab!!.url)
    }

    @Test
    fun `selecting a tab deactivates the previous renderer`() {
        val a = controller.newTab("https://a.example/")
        val b = controller.newTab("https://b.example/")
        val (sa, sb) = engine.created
        assertEquals(false, sa.active)
        assertEquals(true, sb.active)

        controller.selectTab(a)
        assertEquals(true, sa.active)
        assertEquals(false, sb.active)
        assertEquals(a, state.selectedTabId)
        assertEquals(b, state.tabs.last().id)
    }

    @Test
    fun `closing a tab selects its parent, then the most recent one`() {
        val a = controller.newTab("https://a.example/")
        now += 10
        val b = controller.newTab("https://b.example/")
        now += 10
        val child = controller.newTab("https://c.example/", parentId = a)
        assertEquals(listOf(a, child, b), state.tabs.map { it.id })

        controller.closeTab(child)
        assertEquals(a, state.selectedTabId)
        assertTrue(engine.created[2].closed)

        controller.closeTab(a)
        assertEquals(b, state.selectedTabId)
    }

    @Test
    fun `closing private tabs never selects a normal one`() {
        controller.newTab("https://normal.example/")
        val p = controller.newTab("https://private.example/", private = true)
        assertTrue(state.privateMode)
        controller.closeTab(p)
        assertNull(state.selectedTabId)
        assertTrue(state.privateMode)
    }

    @Test
    fun `close all private tabs keeps normal tabs`() {
        val n = controller.newTab("https://n.example/")
        controller.newTab("https://p1.example/", private = true)
        controller.newTab("https://p2.example/", private = true)
        controller.closeAllTabs(private = true)
        assertEquals(listOf(n), state.tabs.map { it.id })
        assertNull(state.selectedTabId)
    }

    @Test
    fun `back goes through history, then to the parent, then to the calling app`() {
        val parent = controller.newTab("https://a.example/")
        val child = controller.newTab("https://b.example/", parentId = parent)
        val childSession = engine.created[1]

        childSession.observer!!.onNavigationStateChange(canGoBack = true)
        assertEquals(BackResult.Handled, controller.handleBack())
        assertEquals(1, childSession.backCalls)

        childSession.observer!!.onNavigationStateChange(canGoBack = false)
        assertEquals(BackResult.Handled, controller.handleBack())
        assertNull(state.tab(child))
        assertEquals(parent, state.selectedTabId)

        controller.closeTab(parent)
        controller.newTab("https://from-app.example/", origin = TabOrigin.External)
        assertEquals(BackResult.LeaveApp, controller.handleBack())
        assertTrue(state.tabs.isEmpty())
    }

    @Test
    fun `back exits fullscreen first`() {
        controller.newTab("https://video.example/")
        val session = engine.created.single()
        session.observer!!.onFullscreenChange(true)
        session.observer!!.onNavigationStateChange(canGoBack = true)
        assertEquals(BackResult.Handled, controller.handleBack())
        assertTrue(session.exitedFullscreen)
        assertEquals(0, session.backCalls)
    }

    @Test
    fun `pages opening windows get a new tab with the engine's session`() {
        val opener = controller.newTab("https://a.example/", private = true)
        val popup = FakeSession(isPrivate = true)
        engine.created.single().observer!!.onWindowRequest(WindowRequest.Open(popup, "https://popup.example/"))

        val tab = state.selectedTab!!
        assertEquals(opener, tab.parentId)
        assertEquals(TabOrigin.Link, tab.origin)
        assertTrue(tab.isPrivate)
        assertEquals("https://popup.example/", tab.url)
        assertNotNull(popup.observer)
        assertEquals(popup, controller.sessionFor(tab.id))
    }

    @Test
    fun `engine events update the tab`() {
        val id = controller.newTab("https://a.example/")
        val observer = engine.created.single().observer!!
        observer.onLoadingStateChange(true)
        observer.onProgress(40)
        observer.onTitleChange("A")
        observer.onLocationChange("https://a.example/page", hasUserGesture = true)
        assertTrue(state.tab(id)!!.content.loading)
        assertEquals(40, state.tab(id)!!.content.progress)
        assertEquals("A", state.tab(id)!!.title)
        assertEquals("https://a.example/page", state.tab(id)!!.url)

        observer.onLocationChange("about:blank", hasUserGesture = false)
        assertEquals("https://a.example/page", state.tab(id)!!.url)

        observer.onLoadingStateChange(false)
        assertFalse(state.tab(id)!!.content.loading)
        assertEquals(100, state.tab(id)!!.content.progress)
    }

    @Test
    fun `prompts are dismissed when their tab closes`() {
        val id = controller.newTab("https://a.example/")
        var dismissed = false
        val prompt = PromptRequest.Alert(title = null, message = "hi", onDismiss = { dismissed = true })
        engine.created.single().observer!!.onPrompt(prompt)
        assertEquals(listOf<PromptRequest>(prompt), state.tab(id)!!.content.prompts)

        controller.closeTab(id)
        assertTrue(dismissed)
        assertTrue(prompt.isComplete)
    }

    @Test
    fun `suspended tabs are restored from their saved state when selected`() {
        val a = controller.newTab("https://a.example/")
        val first = engine.created.single()
        first.observer!!.onStateUpdated(EngineSessionState("saved-a"))
        now += 60_000
        controller.newTab("https://b.example/")

        controller.suspendInactiveTabs(inactiveMillis = 30_000)
        assertTrue(first.closed)
        assertFalse(state.tab(a)!!.isLive)

        controller.selectTab(a)
        val revived = engine.created.last()
        assertEquals(EngineSessionState("saved-a"), revived.restored)
        assertTrue(revived.loaded.isEmpty())
        assertTrue(state.tab(a)!!.isLive)
    }

    @Test
    fun `the visible tab is never suspended`() {
        controller.newTab("https://a.example/")
        now += 3_600_000
        controller.suspendInactiveTabs(inactiveMillis = 1)
        controller.suspendAllBackgroundTabs()
        assertFalse(engine.created.single().closed)
    }

    @Test
    fun `crashed tabs wait for a reload instead of restarting on their own`() {
        val id = controller.newTab("https://crash.example/")
        engine.created.single().observer!!.onCrash()
        assertTrue(state.tab(id)!!.content.crashed)
        assertNull(controller.sessionFor(id))

        controller.reload(id)
        assertEquals(2, engine.created.size)
        assertFalse(state.tab(id)!!.content.crashed)
    }

    @Test
    fun `killed renderer of the visible tab comes back quietly`() {
        val id = controller.newTab("https://a.example/")
        engine.created.single().observer!!.onKill()
        assertEquals(2, engine.created.size)
        assertTrue(state.tab(id)!!.isLive)
    }

    @Test
    fun `snapshot leaves private tabs out and restore wakes only the selected tab`() {
        val a = controller.newTab("https://a.example/")
        engine.created.single().observer!!.onStateUpdated(EngineSessionState("state-a"))
        controller.newTab("https://b.example/")
        controller.newTab("https://secret.example/", private = true)
        controller.selectTab(a)

        val snapshot = SessionSnapshot.decode(controller.snapshot().encode())!!
        assertEquals(listOf("https://a.example/", "https://b.example/"), snapshot.tabs.map { it.url })
        assertEquals(a, snapshot.selectedTabId)

        val fresh = FakeEngine()
        val restored = BrowserController(fresh, clock = { now })
        restored.restore(snapshot)
        assertEquals(2, restored.state.value.tabs.size)
        assertEquals(1, fresh.created.size)
        assertEquals(EngineSessionState("state-a"), fresh.created.single().restored)
        assertTrue(restored.state.value.tabs.all { it.origin == TabOrigin.Restored })
    }

    @Test
    fun `snapshot decoding tolerates garbage and future versions`() {
        assertNull(SessionSnapshot.decode("not json"))
        assertNull(SessionSnapshot.decode("""{"version": 99}"""))
        val tab = SessionSnapshot.decode("""{"tabs":[{"id":"x","url":"https://a.example/","extra":1}]}""")!!.tabs.single()
        assertEquals(TabSnapshot(id = "x", url = "https://a.example/"), tab)
    }

    @Test
    fun `desktop mode is applied to new renderers`() {
        controller.desktopModeByDefault = true
        val id = controller.newTab("https://a.example/")
        assertTrue(engine.created.single().desktop)
        controller.setDesktopMode(id, false)
        assertFalse(engine.created.single().desktop)
        assertFalse(state.tab(id)!!.desktopMode)
    }

    @Test
    fun `duplicate keeps history`() {
        val a = controller.newTab("https://a.example/")
        engine.created.single().observer!!.onStateUpdated(EngineSessionState("hist"))
        val copy = controller.duplicateTab(a)!!
        assertEquals(copy, state.selectedTabId)
        assertEquals(EngineSessionState("hist"), engine.created.last().restored)
    }
}

class ExtensionTabsTest {
    @Test
    fun `extensions open, focus and close tabs`() {
        val engine = FakeEngine()
        val controller = BrowserController(engine)
        val tabs = BrowserExtensionTabs(controller)
        val first = controller.newTab("https://a.example/")
        val dashboard = FakeSession(isPrivate = false)

        assertTrue(tabs.openTab(dashboard, "moz-extension://ublock/dashboard.html", private = false))
        val id = controller.tabIdOf(dashboard)!!
        assertEquals(id, controller.state.value.selectedTabId)
        assertEquals(TabOrigin.Extension, controller.state.value.tab(id)!!.origin)

        controller.selectTab(first)
        tabs.selectTab(dashboard)
        assertEquals(id, controller.state.value.selectedTabId)

        assertTrue(tabs.closeTab(dashboard))
        assertNull(controller.state.value.tab(id))
        assertFalse(tabs.closeTab(dashboard))
    }
}
