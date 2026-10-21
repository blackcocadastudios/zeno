package com.blackcocadastudios.zeno.ui.site

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.browser.url.UrlDisplay
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.engine.PermissionKind
import com.blackcocadastudios.zeno.engine.SitePermission
import com.blackcocadastudios.zeno.ui.common.ChoiceDialog
import com.blackcocadastudios.zeno.ui.common.ZenoDialog

@Composable
fun permissionLabel(kind: PermissionKind): String = stringResource(
    when (kind) {
        PermissionKind.Location -> R.string.site_permission_location
        PermissionKind.Notifications -> R.string.site_permission_notifications
        PermissionKind.PersistentStorage -> R.string.site_permission_storage
        PermissionKind.ProtectedMedia -> R.string.site_permission_drm
        PermissionKind.LocalNetwork -> R.string.site_permission_local_network
        PermissionKind.LocalDevice -> R.string.site_permission_local_device
        PermissionKind.Trackers -> R.string.site_permission_trackers
        else -> R.string.site_permission_other
    },
)

fun permissionIcon(kind: PermissionKind): ImageVector = when (kind) {
    PermissionKind.Location -> ZenoIcons.MapPin
    PermissionKind.Notifications -> ZenoIcons.Bell
    PermissionKind.PersistentStorage -> ZenoIcons.HardDrive
    PermissionKind.ProtectedMedia -> ZenoIcons.MonitorPlay
    PermissionKind.LocalNetwork -> ZenoIcons.Network
    PermissionKind.LocalDevice -> ZenoIcons.Phone
    PermissionKind.Trackers -> ZenoIcons.ShieldOff
    else -> ZenoIcons.Key
}

@Composable
fun permissionState(permission: SitePermission): String = stringResource(
    when {
        permission.kind == PermissionKind.Trackers -> R.string.site_protection_off_here
        permission.allowed -> R.string.site_permission_allowed
        else -> R.string.site_permission_blocked
    },
)

/**
 * Changes one decision: allow, block, or ask every time. For a site without
 * tracking protection the only change is turning it back on.
 */
@Composable
fun ChangePermissionDialog(permission: SitePermission, onChange: (allowed: Boolean?) -> Unit, onDismiss: () -> Unit) {
    val site = UrlDisplay.shortHost(permission.origin)
    if (permission.kind == PermissionKind.Trackers) {
        ZenoDialog(
            onDismiss = onDismiss,
            title = stringResource(R.string.site_protect_title, site),
            confirmLabel = stringResource(R.string.site_protect),
            onConfirm = {
                onChange(null)
                onDismiss()
            },
            dismissLabel = stringResource(R.string.prompt_cancel),
        ) { Text(stringResource(R.string.site_protect_body), style = Zeno.type.bodySmall) }
        return
    }
    val choices = listOf(true, false, null)
    ChoiceDialog(
        title = stringResource(R.string.site_permission_in, permissionLabel(permission.kind), site),
        options = listOf(
            stringResource(R.string.prompt_allow) to null,
            stringResource(R.string.prompt_block) to null,
            stringResource(R.string.site_permission_ask) to null,
        ),
        selected = if (permission.allowed) 0 else 1,
        onSelect = { index ->
            onChange(choices[index])
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}
