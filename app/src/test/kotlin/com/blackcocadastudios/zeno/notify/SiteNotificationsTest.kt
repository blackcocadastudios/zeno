package com.blackcocadastudios.zeno.notify

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.blackcocadastudios.zeno.IntentAction
import com.blackcocadastudios.zeno.IntentProcessor
import com.blackcocadastudios.zeno.browser.search.SearchEngines
import com.blackcocadastudios.zeno.engine.WebNotification
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SiteNotificationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var enabled = true
    private val notifications = SiteNotifications(context) { enabled }
    private val events = mutableListOf<String>()

    private fun notification(tag: String = "chat-1", private: Boolean = false) = WebNotification(
        tag = tag,
        title = "Nova mensagem",
        body = "Oi!",
        origin = "https://chat.example",
        source = "https://chat.example/inbox",
        silent = false,
        private = private,
        onClick = { events += "click $tag" },
        onDismiss = { events += "dismiss $tag" },
    )

    @Test
    fun `a tap on the notification reaches the site, once`() {
        val shown = notification()
        notifications.show(shown)

        val posted = shadowOf(manager).allNotifications.single()
        val intent = shadowOf(posted.contentIntent).savedIntent
        val action = IntentProcessor.parse(intent, SearchEngines.DuckDuckGo) as IntentAction.SiteNotification
        assertEquals("chat-1", action.tag)

        assertSame(shown, notifications.tapped(action.tag, action.token))
        assertNull(notifications.tapped(action.tag, action.token))
    }

    @Test
    fun `another app cannot tap for the user`() {
        notifications.show(notification())
        assertNull(notifications.tapped("chat-1", token = "guessed"))
        assertNull(notifications.tapped("chat-1", token = null))
    }

    @Test
    fun `nothing is shown while the setting is off or from private tabs`() {
        enabled = false
        notifications.show(notification("a"))
        enabled = true
        notifications.show(notification("b", private = true))

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        assertEquals(listOf("dismiss a", "dismiss b"), events)
    }

    @Test
    fun `the site closing it, or turning the setting off, clears it`() {
        notifications.show(notification("a"))
        notifications.show(notification("b"))
        notifications.close("a")
        assertEquals(1, shadowOf(manager).allNotifications.size)

        notifications.cancelAll()
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        assertEquals(listOf("dismiss b"), events)
    }
}
