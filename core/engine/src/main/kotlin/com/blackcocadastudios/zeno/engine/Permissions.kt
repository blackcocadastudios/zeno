package com.blackcocadastudios.zeno.engine

import java.util.concurrent.atomic.AtomicBoolean

/** A site asks for a capability (location, camera, notifications…). */
class PermissionRequest(
    val origin: String,
    val kind: PermissionKind,
    private val onGrant: () -> Unit,
    private val onDeny: () -> Unit,
) {
    private val done = AtomicBoolean(false)

    fun grant() {
        if (done.compareAndSet(false, true)) onGrant()
    }

    fun deny() {
        if (done.compareAndSet(false, true)) onDeny()
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
    LocalNetwork,
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
