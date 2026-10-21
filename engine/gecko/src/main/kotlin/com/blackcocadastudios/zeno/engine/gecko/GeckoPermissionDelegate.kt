package com.blackcocadastudios.zeno.engine.gecko

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.OptIn
import com.blackcocadastudios.zeno.engine.AppPermissionRequest
import com.blackcocadastudios.zeno.engine.EngineSession
import com.blackcocadastudios.zeno.engine.PermissionKind
import com.blackcocadastudios.zeno.engine.PermissionRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource
import kotlin.coroutines.resume

/**
 * Site permissions. Quiet ones are answered here (autoplay, XR); a decision
 * the user kept is applied without asking; the rest become [PermissionRequest]s.
 *
 * GeckoView stores whatever this delegate answers: ALLOW and DENY for good,
 * PROMPT as "no decision". So a refusal that should not be remembered is
 * answered with PROMPT, and an "only this time" grant is taken back later
 * ([OneTimeGrants]).
 */
internal class GeckoPermissionDelegate(
    private val engine: GeckoEngine,
    private val session: GeckoEngineSession,
) : PermissionDelegate {

    private val observer: EngineSession.Observer? get() = session.observer

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<String>?,
        callback: PermissionDelegate.Callback,
    ) {
        val requested = permissions?.toList().orEmpty()
        val target = observer
        when {
            requested.all(::hasAppPermission) -> callback.grant()
            target == null -> callback.reject()
            else -> target.onAppPermissionRequest(
                AppPermissionRequest(requested) { granted -> if (granted) callback.grant() else callback.reject() },
            )
        }
    }

    override fun onContentPermissionRequest(session: GeckoSession, perm: ContentPermission): GeckoResult<Int>? {
        val kind = when (perm.permission) {
            PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> return answer(ContentPermission.VALUE_ALLOW)
            PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> return answer(
                if (engine.settings.blockAudibleAutoplay) ContentPermission.VALUE_DENY else ContentPermission.VALUE_ALLOW,
            )
            PermissionDelegate.PERMISSION_XR -> return answer(ContentPermission.VALUE_PROMPT)
            PermissionDelegate.PERMISSION_TRACKING -> return answer(perm.value)
            PermissionDelegate.PERMISSION_GEOLOCATION -> PermissionKind.Location
            // The app refuses these while site notifications are off in the settings.
            PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> PermissionKind.Notifications
            PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> PermissionKind.PersistentStorage
            PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> PermissionKind.ProtectedMedia
            PermissionDelegate.PERMISSION_STORAGE_ACCESS -> PermissionKind.StorageAccess
            PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> PermissionKind.LocalNetwork
            PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS -> PermissionKind.LocalDevice
            else -> PermissionKind.Other
        }
        val result = GeckoResult<Int>()
        engine.scope.launch { result.complete(decide(perm, kind)) }
        return result
    }

    private suspend fun decide(perm: ContentPermission, kind: PermissionKind): Int {
        when (remembered(perm)) {
            ContentPermission.VALUE_DENY -> return ContentPermission.VALUE_DENY
            // Location also needs Android's permission, which may have been taken away since.
            ContentPermission.VALUE_ALLOW -> if (kind != PermissionKind.Location || hasLocationPermission()) {
                return ContentPermission.VALUE_ALLOW
            }
        }
        val target = observer ?: return ContentPermission.VALUE_PROMPT
        // Storage access grants are kept (and expired) by Gecko itself.
        val canRemember = !perm.privateMode && kind != PermissionKind.StorageAccess
        val (allowed, remember) = ask(target, perm, kind, canRemember)
        if (!allowed) return if (remember) ContentPermission.VALUE_DENY else ContentPermission.VALUE_PROMPT
        // Without Android's permission Gecko would keep a refusal the user never made.
        if (kind == PermissionKind.Location && !ensureLocationPermission(target)) return ContentPermission.VALUE_PROMPT
        when {
            remember -> engine.oneTimeGrants.forget(perm)
            kind != PermissionKind.StorageAccess -> engine.oneTimeGrants.add(session, perm)
        }
        return ContentPermission.VALUE_ALLOW
    }

    /** The decision kept for this site, if the user made one. */
    private suspend fun remembered(perm: ContentPermission): Int? =
        engine.permissionStore.stored(perm.uri, perm.privateMode)
            .firstOrNull { it.permission == perm.permission }
            ?.value
            ?.takeIf { it == ContentPermission.VALUE_ALLOW || it == ContentPermission.VALUE_DENY }

    // notifyShown (experimental) lets Gecko leave fullscreen while the question is on screen.
    @OptIn(markerClass = [ExperimentalGeckoViewApi::class])
    private suspend fun ask(
        target: EngineSession.Observer,
        perm: ContentPermission,
        kind: PermissionKind,
        canRemember: Boolean,
    ): Pair<Boolean, Boolean> = suspendCancellableCoroutine { cont ->
        // Decisions are kept for the page's site; storage access is asked by the site embedded in it.
        val shown = if (kind == PermissionKind.StorageAccess) perm.thirdPartyOrigin ?: perm.uri else perm.uri
        target.onPermissionRequest(
            PermissionRequest(
                origin = displayHost(shown),
                kind = kind,
                canRemember = canRemember,
                onShown = { runCatching { perm.notifyShown() } },
                onAnswer = { allowed, remember -> if (cont.isActive) cont.resume(allowed to remember) },
            ),
        )
    }

    private suspend fun ensureLocationPermission(target: EngineSession.Observer): Boolean {
        if (hasLocationPermission()) return true
        return suspendCancellableCoroutine { cont ->
            // Android 12 and later ignore a request for precise location made without approximate.
            target.onAppPermissionRequest(
                AppPermissionRequest(listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) { granted ->
                    if (cont.isActive) cont.resume(granted)
                },
            )
        }
    }

    /** Gecko's location provider needs precise location. */
    private fun hasLocationPermission() = hasAppPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasAppPermission(name: String) =
        engine.appContext.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<MediaSource>?,
        audio: Array<MediaSource>?,
        callback: PermissionDelegate.MediaCallback,
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
        val target = observer
        if (target == null) {
            callback.reject()
            return
        }
        // Camera and microphone are asked every time: GeckoView keeps no decision for them.
        target.onPermissionRequest(
            PermissionRequest(
                origin = displayHost(uri),
                kind = kind,
                onAnswer = { allowed, _ -> if (allowed) callback.grant(camera, microphone) else callback.reject() },
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

    private fun answer(value: Int): GeckoResult<Int> = GeckoResult.fromValue(value)

    private fun displayHost(uri: String): String =
        runCatching { Uri.parse(uri).host }.getOrNull()?.removePrefix("www.") ?: uri
}
