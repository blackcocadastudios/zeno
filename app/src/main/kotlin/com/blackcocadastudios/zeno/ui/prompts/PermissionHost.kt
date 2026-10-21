package com.blackcocadastudios.zeno.ui.prompts

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.engine.AppPermissionRequest
import com.blackcocadastudios.zeno.engine.PermissionKind
import com.blackcocadastudios.zeno.engine.PermissionRequest
import com.blackcocadastudios.zeno.ui.common.ZenoDialog

/** A site asks for a capability; the user decides. */
@Composable
fun PermissionHost(request: PermissionRequest, onDone: (PermissionRequest) -> Unit) {
    val message = when (request.kind) {
        PermissionKind.Location -> R.string.permission_location
        PermissionKind.Camera -> R.string.permission_camera
        PermissionKind.Microphone -> R.string.permission_microphone
        PermissionKind.CameraAndMicrophone -> R.string.permission_camera_microphone
        PermissionKind.ProtectedMedia -> R.string.permission_drm
        PermissionKind.PersistentStorage -> R.string.permission_storage
        PermissionKind.StorageAccess -> R.string.permission_storage_access
        PermissionKind.LocalNetwork -> R.string.permission_local_network
        PermissionKind.Notifications, PermissionKind.Other -> R.string.permission_other
    }
    ZenoDialog(
        onDismiss = {
            request.deny()
            onDone(request)
        },
        title = null,
        confirmLabel = stringResource(R.string.prompt_allow),
        onConfirm = {
            request.grant()
            onDone(request)
        },
        dismissLabel = stringResource(R.string.prompt_block),
        onDismissClick = {
            request.deny()
            onDone(request)
        },
    ) {
        Text(stringResource(message, request.origin), style = Zeno.type.body, color = Zeno.colors.ink)
    }
}

/** The engine needs an Android permission (camera, location…) before a site can use it. */
@Composable
fun AppPermissionHost(request: AppPermissionRequest, onDone: (AppPermissionRequest) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        request.complete(results.isNotEmpty() && results.values.all { it })
        onDone(request)
    }
    LaunchedEffect(request) {
        val missing = request.permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
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
