package com.blackcocadastudios.zeno.browser.session

import com.blackcocadastudios.zeno.browser.state.BrowserState
import com.blackcocadastudios.zeno.browser.state.TabOrigin
import com.blackcocadastudios.zeno.browser.state.TabState
import com.blackcocadastudios.zeno.engine.EngineSessionState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What survives a restart. Private tabs are never part of it. */
@Serializable
data class SessionSnapshot(
    val version: Int = VERSION,
    val tabs: List<TabSnapshot> = emptyList(),
    val selectedTabId: String? = null,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val VERSION = 1

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        /** Returns null for unreadable or incompatible data instead of throwing. */
        fun decode(text: String): SessionSnapshot? =
            runCatching { json.decodeFromString(serializer(), text) }
                .getOrNull()
                ?.takeIf { it.version <= VERSION }

        fun from(state: BrowserState): SessionSnapshot {
            val tabs = state.tabs.filterNot { it.isPrivate }.map { tab ->
                TabSnapshot(
                    id = tab.id,
                    url = tab.url,
                    title = tab.title,
                    parentId = tab.parentId?.takeIf { parent -> state.tab(parent)?.isPrivate == false },
                    createdAt = tab.createdAt,
                    lastAccessedAt = tab.lastAccessedAt,
                    desktopMode = tab.desktopMode,
                    engineState = tab.engineState?.serialized,
                )
            }
            val selected = state.selectedTab?.takeUnless { it.isPrivate }?.id
            return SessionSnapshot(tabs = tabs, selectedTabId = selected)
        }
    }
}

@Serializable
data class TabSnapshot(
    val id: String,
    val url: String? = null,
    val title: String = "",
    val parentId: String? = null,
    val createdAt: Long = 0,
    val lastAccessedAt: Long = 0,
    val desktopMode: Boolean = false,
    val engineState: String? = null,
) {
    fun toTabState(): TabState = TabState(
        id = id,
        url = url,
        title = title,
        parentId = parentId,
        origin = TabOrigin.Restored,
        createdAt = createdAt,
        lastAccessedAt = lastAccessedAt,
        desktopMode = desktopMode,
        engineState = engineState?.let(::EngineSessionState),
        isLive = false,
    )
}
