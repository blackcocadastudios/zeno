package com.blackcocadastudios.zeno.web

import android.app.Application
import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExternalAppInterceptorTest {

    @Test
    fun `page intents cannot target components or borrow permissions`() {
        val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        val url = "intent://files/x#Intent;scheme=content;component=com.blackcocadastudios.zeno/.MainActivity;" +
            "launchFlags=0x${Integer.toHexString(grants or Intent.FLAG_ACTIVITY_CLEAR_TOP)};" +
            "SEL;component=com.example/.Secret;end"
        val intent = assertNotNull(ExternalAppInterceptor.intentFromPage(url))
        assertNull(intent.component)
        assertNull(intent.selector)
        assertNull(intent.data)
        assertEquals(0, intent.flags and grants)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    @Test
    fun `ordinary app links keep their package and data`() {
        val intent = assertNotNull(
            ExternalAppInterceptor.intentFromPage("intent://maps.example/place#Intent;scheme=geo;package=com.example.maps;end"),
        )
        assertEquals("com.example.maps", intent.`package`)
        assertEquals("geo", intent.data?.scheme)
    }

    @Test
    fun `broken intent links are refused`() {
        assertNull(ExternalAppInterceptor.intentFromPage("intent://x#Intent;launchFlags=zzz;end"))
    }
}
