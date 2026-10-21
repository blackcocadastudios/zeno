package com.blackcocadastudios.zeno.ui.site

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.browser.url.UrlDisplay
import com.blackcocadastudios.zeno.design.component.Hairline
import com.blackcocadastudios.zeno.design.component.SectionLabel
import com.blackcocadastudios.zeno.design.component.ZenoListRow
import com.blackcocadastudios.zeno.design.component.ZenoSwitchRow
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes
import com.blackcocadastudios.zeno.engine.SecurityInfo
import com.blackcocadastudios.zeno.engine.SitePermission
import com.blackcocadastudios.zeno.ui.common.SiteSeal
import com.blackcocadastudios.zeno.ui.common.ZenoDialog
import com.blackcocadastudios.zeno.ui.menu.SheetHandle

/** What the panel says about the page in the current tab. */
data class SiteInfo(
    val url: String,
    val secure: Boolean,
    val security: SecurityInfo,
    val trackersBlocked: Int,
    /** Tracking protection is on in the settings. */
    val protectionAvailable: Boolean,
    /** …but off for this site. */
    val protectionException: Boolean,
)

interface SitePanelActions {
    fun setProtection(enabled: Boolean)
    fun changePermission(permission: SitePermission, allowed: Boolean?)
    fun clearSiteData()
}

/** Opened from the lock in the address bar: the connection, tracking protection, permissions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitePanelSheet(
    info: SiteInfo,
    permissions: List<SitePermission>,
    actions: SitePanelActions,
    onDismiss: () -> Unit,
) {
    val colors = Zeno.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paperRaised,
        contentColor = colors.ink,
        scrimColor = colors.scrim,
        shape = ZenoShapes.sheet,
        dragHandle = { SheetHandle() },
    ) {
        SitePanelContent(info, permissions, actions)
    }
}

@Composable
fun SitePanelContent(info: SiteInfo, permissions: List<SitePermission>, actions: SitePanelActions) {
    val colors = Zeno.colors
    var editing by remember { mutableStateOf<SitePermission?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val host = UrlDisplay.shortHost(info.url)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Ma.l),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Ma.xl, vertical = Ma.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SiteSeal(info.url, size = 44.dp)
            Spacer(Modifier.width(Ma.l))
            Column(Modifier.weight(1f)) {
                Text(host, style = Zeno.type.heading, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (info.secure) ZenoIcons.Lock else ZenoIcons.LockOpen,
                        contentDescription = null,
                        tint = if (info.secure) colors.inkSoft else colors.seal,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(Ma.xs))
                    Text(
                        stringResource(if (info.secure) R.string.toolbar_secure else R.string.toolbar_insecure),
                        style = Zeno.type.bodySmall,
                        color = if (info.secure) colors.inkSoft else colors.seal,
                    )
                }
            }
        }
        val detail = when {
            !info.secure -> stringResource(R.string.site_insecure_body)
            info.security.mixedContent -> stringResource(R.string.site_mixed)
            info.security.issuer != null -> stringResource(R.string.site_issuer, info.security.issuer!!)
            else -> null
        }
        if (detail != null) {
            Text(
                detail,
                style = Zeno.type.caption,
                color = colors.inkSoft,
                modifier = Modifier.padding(start = Ma.xl + 44.dp + Ma.l, end = Ma.xl, bottom = Ma.s),
            )
        }
        Hairline(Modifier.padding(horizontal = Ma.xl, vertical = Ma.s))

        val protected = info.protectionAvailable && !info.protectionException
        ZenoSwitchRow(
            title = stringResource(R.string.site_protection),
            subtitle = when {
                !info.protectionAvailable -> stringResource(R.string.site_protection_off_everywhere)
                info.protectionException -> stringResource(R.string.site_protection_off_here)
                info.trackersBlocked > 0 -> pluralStringResource(R.plurals.site_trackers_blocked, info.trackersBlocked, info.trackersBlocked)
                else -> stringResource(R.string.site_no_trackers)
            },
            icon = if (protected) ZenoIcons.ShieldCheck else ZenoIcons.ShieldOff,
            checked = protected,
            enabled = info.protectionAvailable,
            onCheckedChange = actions::setProtection,
        )

        if (permissions.isNotEmpty()) {
            SectionLabel(stringResource(R.string.site_permissions))
            permissions.forEach { permission ->
                ZenoListRow(
                    title = permissionLabel(permission.kind),
                    subtitle = permissionState(permission),
                    icon = permissionIcon(permission.kind),
                    onClick = { editing = permission },
                )
            }
        }

        Hairline(Modifier.padding(horizontal = Ma.xl, vertical = Ma.s))
        ZenoListRow(
            title = stringResource(R.string.site_clear),
            icon = ZenoIcons.Cookie,
            titleColor = colors.seal,
            onClick = { confirmClear = true },
        )
    }

    editing?.let { permission ->
        ChangePermissionDialog(
            permission = permission,
            onChange = { allowed -> actions.changePermission(permission, allowed) },
            onDismiss = { editing = null },
        )
    }
    if (confirmClear) {
        ZenoDialog(
            onDismiss = { confirmClear = false },
            title = stringResource(R.string.site_clear_title, host),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmClear = false
                actions.clearSiteData()
            },
            dismissLabel = stringResource(R.string.prompt_cancel),
            destructive = true,
        ) { Text(stringResource(R.string.site_clear_body), style = Zeno.type.bodySmall) }
    }
}
