package com.blackcocadastudios.zeno.ui.prompts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.design.component.ZenoCheckRow
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.engine.AppPermissionRequest
import com.blackcocadastudios.zeno.engine.PermissionKind
import com.blackcocadastudios.zeno.engine.PermissionRequest
import com.blackcocadastudios.zeno.ui.common.ZenoDialog

/**
 * A site asks for a capability; the user decides. Closing the dialog without
 * an answer refuses for now, and the site may ask again.
 */
@Composable
fun PermissionHost(request: PermissionRequest, onDone: (PermissionRequest) -> Unit) {
    var keep by remember(request) { mutableStateOf(true) }
    LaunchedEffect(request) { request.shown() }
    ZenoDialog(
        onDismiss = {
            request.deny()
            onDone(request)
        },
        title = null,
        confirmLabel = stringResource(R.string.prompt_allow),
        onConfirm = {
            request.grant(keep)
            onDone(request)
        },
        dismissLabel = stringResource(R.string.prompt_block),
        onDismissClick = {
            request.deny(keep)
            onDone(request)
        },
    ) {
        PermissionQuestion(request.kind, request.origin, request.canRemember, keep) { keep = it }
    }
}

/** The question, and the choice to keep the answer. Separate so it can be rendered in tests. */
@Composable
fun PermissionQuestion(
    kind: PermissionKind,
    origin: String,
    canRemember: Boolean,
    remember: Boolean,
    onRememberChange: (Boolean) -> Unit,
) {
    val message = when (kind) {
        PermissionKind.Location -> R.string.permission_location
        PermissionKind.Camera -> R.string.permission_camera
        PermissionKind.Microphone -> R.string.permission_microphone
        PermissionKind.CameraAndMicrophone -> R.string.permission_camera_microphone
        PermissionKind.ProtectedMedia -> R.string.permission_drm
        PermissionKind.PersistentStorage -> R.string.permission_storage
        PermissionKind.StorageAccess -> R.string.permission_storage_access
        PermissionKind.LocalNetwork -> R.string.permission_local_network
        PermissionKind.LocalDevice -> R.string.permission_local_device
        PermissionKind.Notifications -> R.string.permission_site_notifications
        PermissionKind.Trackers, PermissionKind.Other -> R.string.permission_other
    }
    Column {
        Text(stringResource(message, origin), style = Zeno.type.body, color = Zeno.colors.ink)
        if (canRemember) {
            Spacer(Modifier.height(Ma.m))
            ZenoCheckRow(stringResource(R.string.permission_remember), checked = remember, onCheckedChange = onRememberChange)
        }
    }
}

/**
 * The engine needs an Android permission (camera, location…) before a site can
 * use it. [onDenied] tells the user why the site will not get it.
 */
@Composable
fun AppPermissionHost(request: AppPermissionRequest, onDenied: () -> Unit, onDone: (AppPermissionRequest) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        // Everything asked for: approximate location alone does not let Gecko locate.
        val granted = request.permissions.all { results[it] ?: isGranted(context, it) }
        request.complete(granted)
        if (!granted) onDenied()
        onDone(request)
    }
    LaunchedEffect(request) {
        val missing = request.permissions.filterNot { isGranted(context, it) }.toMutableSet()
        // Android 12 and later ignore a request for precise location made without approximate.
        if (Manifest.permission.ACCESS_FINE_LOCATION in missing) missing += Manifest.permission.ACCESS_COARSE_LOCATION
        if (missing.isEmpty()) {
            request.complete(true)
            onDone(request)
        } else {
            runCatching { launcher.launch(missing.toTypedArray()) }.onFailure {
                request.complete(false)
                onDone(request)
            }
        }
    }
}

private fun isGranted(context: android.content.Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
