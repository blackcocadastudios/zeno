package com.blackcocadastudios.zeno

import android.app.SearchManager
import android.content.Intent
import com.blackcocadastudios.zeno.browser.search.SearchEngine
import com.blackcocadastudios.zeno.browser.url.UrlInput

/** What an intent from the system or another app asks Zeno to do. */
sealed interface IntentAction {
    /** A web page from another app; back on its first page returns there. */
    data class Open(val url: String) : IntentAction

    /** The launcher shortcuts: Zeno's home page with the address bar ready. */
    data class NewTab(val private: Boolean) : IntentAction

    data object None : IntentAction
}

/**
 * Reads intents. Other apps can only get web pages opened (http and https);
 * text they send is searched, never run or loaded as a local file.
 */
object IntentProcessor {
    const val ACTION_NEW_TAB = "com.blackcocadastudios.zeno.action.NEW_TAB"
    const val ACTION_NEW_PRIVATE_TAB = "com.blackcocadastudios.zeno.action.NEW_PRIVATE_TAB"

    fun parse(intent: Intent, searchEngine: SearchEngine): IntentAction = when (intent.action) {
        Intent.ACTION_VIEW -> {
            val scheme = intent.data?.scheme?.lowercase()
            val url = intent.dataString
            if (url != null && (scheme == "http" || scheme == "https")) IntentAction.Open(url) else IntentAction.None
        }
        Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH -> text(intent.getStringExtra(SearchManager.QUERY), searchEngine)
        Intent.ACTION_SEND -> text(intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT), searchEngine)
        Intent.ACTION_PROCESS_TEXT -> text(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString(), searchEngine)
        ACTION_NEW_TAB -> IntentAction.NewTab(private = false)
        ACTION_NEW_PRIVATE_TAB -> IntentAction.NewTab(private = true)
        else -> IntentAction.None
    }

    private fun text(text: String?, searchEngine: SearchEngine): IntentAction {
        if (text.isNullOrBlank()) return IntentAction.None
        return UrlInput.fromSharedText(text, searchEngine)?.let(IntentAction::Open) ?: IntentAction.None
    }

    /** Android re-delivers the first intent when the app is reopened from recents; it was handled already. */
    fun isReplay(intent: Intent): Boolean = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
}
