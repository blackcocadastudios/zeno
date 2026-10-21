package com.blackcocadastudios.zeno.engine

import java.util.concurrent.atomic.AtomicBoolean

/** A site asks for a capability (location, camera, notifications…). */
class PermissionRequest(
    val origin: String,
    val kind: PermissionKind,
    /**
     * The answer can be kept, so the site does not ask again. False for camera
     * and microphone, which are asked every time, and in private tabs, where
     * every answer lasts only while the user stays on the site.
     */
    val canRemember: Boolean = false,
    private val onShown: () -> Unit = {},
    private val onAnswer: (allowed: Boolean, remember: Boolean) -> Unit,
) {
    private val done = AtomicBoolean(false)
    private val shownOnce = AtomicBoolean(false)

    /** The question is on screen (the engine may need to know, e.g. to leave fullscreen). */
    fun shown() {
        if (shownOnce.compareAndSet(false, true)) onShown()
    }

    /** Without [remember] the permission lasts until the user leaves the site or closes the tab. */
    fun grant(remember: Boolean = false) {
        if (done.compareAndSet(false, true)) onAnswer(true, remember && canRemember)
    }

    /** Without [remember] the site may ask again later. */
    fun deny(remember: Boolean = false) {
        if (done.compareAndSet(false, true)) onAnswer(false, remember && canRemember)
    }
}

enum class PermissionKind {
    Location,
    Notifications,
    Camera,
    Microphone,
    CameraAndMicrophone,
    ProtectedMedia,
    PersistentStorage,
    StorageAccess,

    /** Devices on the same network (a router, a printer). */
    LocalNetwork,

    /** Apps and services running on this phone. */
    LocalDevice,

    /** Not asked by sites: a remembered [SitePermission] that turns tracking protection off for a site. */
    Trackers,
    Other,
}

/** The engine needs Android runtime permissions (e.g. camera) before a site can use them. */
class AppPermissionRequest(
    /** Android permission names, e.g. `android.permission.CAMERA`. */
    val permissions: List<String>,
    private val onResult: (Boolean) -> Unit,
) {
    private val done = AtomicBoolean(false)

    fun complete(granted: Boolean) {
        if (done.compareAndSet(false, true)) onResult(granted)
    }
}

/**
 * A decision the engine keeps for a site: a permission the user chose to
 * remember, or tracking protection turned off there.
 */
data class SitePermission(
    /** Where the decision applies: scheme, host and port, e.g. `https://maps.example.com`. */
    val origin: String,
    val kind: PermissionKind,
    /** For [PermissionKind.Trackers], true means tracking protection is off on this site. */
    val allowed: Boolean,
    /** Kept only until the private tabs close. */
    val private: Boolean = false,
)

/** What sites are allowed, for the site panel and the settings. */
interface SitePermissionStore {
    /** Every decision kept for normal tabs. */
    suspend fun all(): List<SitePermission>

    /** The decisions for the site of [url], in normal or private tabs. */
    suspend fun forSite(url: String, private: Boolean): List<SitePermission>

    /**
     * Changes a decision. With [allowed] null it is forgotten: the site asks
     * again next time, or tracking protection is back on.
     */
    suspend fun set(permission: SitePermission, allowed: Boolean?)
}
