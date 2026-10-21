package com.blackcocadastudios.zeno.web

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.engine.EngineSession
import com.blackcocadastudios.zeno.engine.Interception
import com.blackcocadastudios.zeno.engine.LoadFlags
import com.blackcocadastudios.zeno.engine.LoadRequest
import com.blackcocadastudios.zeno.engine.RequestInterceptor
import java.net.URISyntaxException
import androidx.core.net.toUri

/**
 * Hands non-web links (mailto:, tel:, intent:, market:…) to other apps.
 * Web links always stay in Zeno. A page can only launch an app in response to
 * a user gesture, never on its own.
 */
class ExternalAppInterceptor(private val context: Context) : RequestInterceptor {

    private val engineSchemes = setOf("http", "https", "about", "data", "blob", "file", "moz-extension", "view-source", "javascript", "resource")

    override fun onLoadRequest(session: EngineSession, request: LoadRequest): Interception {
        val url = request.url
        val scheme = url.toUri().scheme?.lowercase() ?: return Interception.Allow
        if (scheme in engineSchemes) return Interception.Allow
        // Other apps' content providers are not for web pages to open.
        if (scheme == "content") return Interception.Handled
        if (!request.hasUserGesture && !request.isDirectNavigation) return Interception.Handled

        val intent = when (scheme) {
            "intent" -> intentFromPage(url) ?: return Interception.Handled
            else -> Intent(Intent.ACTION_VIEW, url.toUri())
        }
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (fallback != null && (fallback.startsWith("https://") || fallback.startsWith("http://"))) {
                session.load(fallback, LoadFlags(replaceHistory = true))
            } else {
                Toast.makeText(context, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
            }
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
        }
        return Interception.Handled
    }

    internal companion object {
        private const val URI_GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

        /**
         * `intent:` URIs come from web pages: they may name an app, never one
         * of its components, and never borrow Zeno's own permissions (a granted
         * `content://` URI would hand Zeno's files to the other app).
         */
        fun intentFromPage(url: String): Intent? = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                component = null
                selector = null
                clipData = null
                flags = flags and URI_GRANTS.inv()
                if (data?.scheme.equals("content", ignoreCase = true)) data = null
            }
        } catch (_: URISyntaxException) {
            null
        } catch (_: RuntimeException) {
            // parseUri lets some malformed values through as runtime errors (launchFlags=zzz).
            null
        }
    }
}
