package com.blackcocadastudios.zeno.browser

import com.blackcocadastudios.zeno.engine.EngineSession
import com.blackcocadastudios.zeno.engine.ExtensionTabs

/** Lets extensions open, close and focus Zeno tabs. */
class BrowserExtensionTabs(private val controller: BrowserController) : ExtensionTabs {
    override fun openTab(session: EngineSession, url: String, private: Boolean): Boolean {
        controller.adoptSession(session, url, private)
        return true
    }

    override fun closeTab(session: EngineSession): Boolean {
        val id = controller.tabIdOf(session) ?: return false
        controller.closeTab(id)
        return true
    }

    override fun selectTab(session: EngineSession) {
        controller.tabIdOf(session)?.let(controller::selectTab)
    }
}
