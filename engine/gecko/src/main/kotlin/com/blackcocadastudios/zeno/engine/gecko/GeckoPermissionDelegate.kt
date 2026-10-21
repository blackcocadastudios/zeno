package com.blackcocadastudios.zeno.engine.gecko

import android.net.Uri
import com.blackcocadastudios.zeno.engine.AppPermissionRequest
import com.blackcocadastudios.zeno.engine.EngineSession
import com.blackcocadastudios.zeno.engine.PermissionKind
import com.blackcocadastudios.zeno.engine.PermissionRequest
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource

/**
 * Site permissions. Quiet ones are answered here (autoplay, notifications);
 * the rest become [PermissionRequest]s for the user to decide.
 */
internal class GeckoPermissionDelegate(
    private val engine: GeckoEngine,
    private val observer: () -> EngineSession.Observer?,
) : GeckoSession.PermissionDelegate {

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<String>?,
        callback: GeckoSession.PermissionDelegate.Callback,
    ) {
        val requested = permissions?.toList().orEmpty()
        val target = observer()
        when {
            requested.isEmpty() -> callback.grant()
            target == null -> callback.reject()
            else -> target.onAppPermissionRequest(
                AppPermissionRequest(requested) { granted -> if (granted) callback.grant() else callback.reject() },
            )
        }
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: ContentPermission,
    ): GeckoResult<Int>? {
        val allow = GeckoResult.fromValue(ContentPermission.VALUE_ALLOW)
        val deny = GeckoResult.fromValue(ContentPermission.VALUE_DENY)
        val kind = when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> return allow
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ->
                return if (engine.settings.blockAudibleAutoplay) deny else allow
            // Zeno does not show web notifications: sites cannot interrupt the user.
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> return deny
            GeckoSession.PermissionDelegate.PERMISSION_XR -> return deny
            GeckoSession.PermissionDelegate.PERMISSION_TRACKING -> return GeckoResult.fromValue(perm.value)
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> PermissionKind.Location
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> PermissionKind.PersistentStorage
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> PermissionKind.ProtectedMedia
            GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> PermissionKind.StorageAccess
            GeckoSession.PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS,
            GeckoSession.PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS,
            -> PermissionKind.LocalNetwork
            else -> PermissionKind.Other
        }
        val target = observer() ?: return deny
        val result = GeckoResult<Int>()
        target.onPermissionRequest(
            PermissionRequest(
                origin = originOf(perm.thirdPartyOrigin ?: perm.uri),
                kind = kind,
                onGrant = { result.complete(ContentPermission.VALUE_ALLOW) },
                onDeny = { result.complete(ContentPermission.VALUE_DENY) },
            ),
        )
        return result
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<MediaSource>?,
        audio: Array<MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback,
    ) {
        val camera = video?.let(::preferredCamera)
        val microphone = audio?.firstOrNull()
        val kind = when {
            camera != null && microphone != null -> PermissionKind.CameraAndMicrophone
            camera != null -> PermissionKind.Camera
            microphone != null -> PermissionKind.Microphone
            else -> {
                callback.reject()
                return
            }
        }
        val target = observer()
        if (target == null) {
            callback.reject()
            return
        }
        target.onPermissionRequest(
            PermissionRequest(
                origin = originOf(uri),
                kind = kind,
                onGrant = { callback.grant(camera, microphone) },
                onDeny = { callback.reject() },
            ),
        )
    }

    /** Front camera first: video calls are the common case on a phone. */
    private fun preferredCamera(sources: Array<MediaSource>): MediaSource? {
        val cameras = sources.filter { it.source == MediaSource.SOURCE_CAMERA }
        return cameras.firstOrNull { it.name?.contains("front", ignoreCase = true) == true }
            ?: cameras.firstOrNull()
            ?: sources.firstOrNull()
    }

    private fun originOf(uri: String): String =
        runCatching { Uri.parse(uri).host }.getOrNull()?.removePrefix("www.") ?: uri
}
