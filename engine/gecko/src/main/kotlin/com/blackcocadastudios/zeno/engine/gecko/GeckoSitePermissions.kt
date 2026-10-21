package com.blackcocadastudios.zeno.engine.gecko

import android.net.Uri
import com.blackcocadastudios.zeno.engine.PermissionKind
import com.blackcocadastudios.zeno.engine.SitePermission
import com.blackcocadastudios.zeno.engine.SitePermissionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.StorageController
import java.io.File

/**
 * Site permissions as Gecko keeps them. GeckoView stores every answer a site
 * gets (allowed or blocked, for good), and also the sites where tracking
 * protection is off. Zeno shows and changes that store; it keeps no copy.
 */
internal class GeckoSitePermissions(private val engine: GeckoEngine) : SitePermissionStore {
    private val storage: StorageController get() = engine.runtime.storageController

    override suspend fun all(): List<SitePermission> =
        runCatching { storage.getAllPermissions().await() }.getOrNull().orEmpty()
            .filter { !it.privateMode }
            .mapNotNull { it.toSitePermission() }

    override suspend fun forSite(url: String, private: Boolean): List<SitePermission> =
        stored(url, private).mapNotNull { it.toSitePermission() }

    override suspend fun set(permission: SitePermission, allowed: Boolean?) {
        val target = stored(permission.origin, permission.private)
            .firstOrNull { it.toSitePermission()?.kind == permission.kind }
            ?: return
        engine.oneTimeGrants.forget(target)
        storage.setPermission(target, valueOf(allowed))
    }

    /** What Gecko keeps for the site of [url]. */
    suspend fun stored(url: String, private: Boolean): List<ContentPermission> =
        runCatching { storage.getPermissions(url, private).await() }.getOrNull().orEmpty()

    companion object {
        fun valueOf(allowed: Boolean?) = when (allowed) {
            true -> ContentPermission.VALUE_ALLOW
            false -> ContentPermission.VALUE_DENY
            null -> ContentPermission.VALUE_PROMPT
        }
    }
}

/**
 * A stored permission the user can see and change. Autoplay answers (given by
 * Zeno from the settings), storage access (kept and expired by Gecko itself)
 * and other internal entries are left out.
 */
internal fun ContentPermission.toSitePermission(): SitePermission? {
    val kind = kindOf(permission) ?: return null
    val allowed = when (value) {
        ContentPermission.VALUE_ALLOW -> true
        // A tracking entry that is not an exception is Gecko's placeholder for "protection on".
        ContentPermission.VALUE_DENY -> if (kind == PermissionKind.Trackers) return null else false
        else -> return null
    }
    return SitePermission(origin = originOf(uri), kind = kind, allowed = allowed, private = privateMode)
}

private fun kindOf(permission: Int): PermissionKind? = when (permission) {
    PermissionDelegate.PERMISSION_GEOLOCATION -> PermissionKind.Location
    PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> PermissionKind.Notifications
    PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> PermissionKind.PersistentStorage
    PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> PermissionKind.ProtectedMedia
    PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> PermissionKind.LocalNetwork
    PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS -> PermissionKind.LocalDevice
    PermissionDelegate.PERMISSION_TRACKING -> PermissionKind.Trackers
    else -> null
}

/** Scheme, host and port of [uri]: where a permission applies. */
internal fun originOf(uri: String): String {
    val parsed = runCatching { Uri.parse(uri) }.getOrNull()
    val scheme = parsed?.scheme
    val host = parsed?.host
    if (scheme == null || host == null) return uri
    val port = if (parsed.port == -1) "" else ":${parsed.port}"
    return "$scheme://$host$port"
}

/**
 * Permissions given "only this time". GeckoView keeps every answer, so these
 * are taken back when the user leaves the site or closes the tab and, in case
 * Zeno stopped before that, when it starts again (they are listed in [file]).
 */
internal class OneTimeGrants(private val file: File, private val scope: CoroutineScope) {
    private class Grant(val session: GeckoEngineSession, val permission: ContentPermission) {
        val origin = originOf(permission.uri)
    }

    private val grants = mutableListOf<Grant>()

    /** Writes in order, one at a time. */
    private val disk = Dispatchers.IO.limitedParallelism(1)

    fun add(session: GeckoEngineSession, permission: ContentPermission) {
        forget(permission)
        grants += Grant(session, permission)
        save()
    }

    /** Takes back what [session] was given, except on [keepOrigin], the site it still shows. */
    fun revoke(storage: StorageController, session: GeckoEngineSession, keepOrigin: String? = null) {
        val gone = grants.filter { it.session === session && it.origin != keepOrigin }
        if (gone.isEmpty()) return
        gone.forEach { storage.setPermission(it.permission, ContentPermission.VALUE_PROMPT) }
        grants.removeAll(gone.toSet())
        save()
    }

    /** The decision was made for good, or changed by hand: nothing to take back. */
    fun forget(permission: ContentPermission) {
        val origin = originOf(permission.uri)
        val removed = grants.removeAll {
            it.origin == origin &&
                it.permission.permission == permission.permission &&
                it.permission.privateMode == permission.privateMode
        }
        if (removed) save()
    }

    /** At start: takes back what the previous run could not. */
    fun revokeLeftovers(storage: StorageController) {
        scope.launch {
            val saved = withContext(disk) {
                runCatching { file.readText() }.getOrNull().also { file.delete() }
            } ?: return@launch
            val list = runCatching { JSONArray(saved) }.getOrNull() ?: return@launch
            for (i in 0 until list.length()) {
                val permission = list.optJSONObject(i)?.let { runCatching { ContentPermission.fromJson(it) }.getOrNull() }
                permission?.let { storage.setPermission(it, ContentPermission.VALUE_PROMPT) }
            }
        }
    }

    private fun save() {
        // Private permissions die with the private tabs; only normal ones need a note.
        val json = grants.filter { !it.permission.privateMode }.mapNotNull { runCatching { it.permission.toJson() }.getOrNull() }
        val text = if (json.isEmpty()) null else JSONArray(json).toString()
        scope.launch(disk) {
            runCatching {
                if (text == null) {
                    file.delete()
                } else {
                    val temp = File(file.parentFile, file.name + ".tmp")
                    temp.writeText(text)
                    temp.renameTo(file)
                }
            }
        }
    }
}
