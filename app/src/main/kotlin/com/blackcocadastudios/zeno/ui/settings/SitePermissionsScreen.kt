package com.blackcocadastudios.zeno.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.browser.url.UrlDisplay
import com.blackcocadastudios.zeno.design.component.EmptyState
import com.blackcocadastudios.zeno.design.component.ZenoListRow
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.engine.SitePermission
import com.blackcocadastudios.zeno.ui.common.SiteSeal
import com.blackcocadastudios.zeno.ui.common.ZenoPage
import com.blackcocadastudios.zeno.ui.site.ChangePermissionDialog
import com.blackcocadastudios.zeno.ui.site.permissionIcon
import com.blackcocadastudios.zeno.ui.site.permissionLabel
import com.blackcocadastudios.zeno.ui.site.permissionState

/**
 * Every decision kept for a site, grouped by site. [permissions] is null while
 * they load.
 */
@Composable
fun SitePermissionsScreen(
    permissions: List<SitePermission>?,
    onChange: (SitePermission, Boolean?) -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<SitePermission?>(null) }
    val sites = remember(permissions) {
        permissions.orEmpty()
            .groupBy { it.origin }
            .toSortedMap(compareBy({ UrlDisplay.shortHost(it) }, { it }))
    }
    ZenoPage(title = stringResource(R.string.settings_site_permissions), onBack = onBack) {
        when {
            permissions == null -> Unit
            sites.isEmpty() -> EmptyState(
                title = stringResource(R.string.site_permissions_empty),
                body = stringResource(R.string.site_permissions_empty_body),
                modifier = Modifier.fillMaxWidth(),
            )
            else -> LazyColumn(Modifier.fillMaxWidth()) {
                sites.forEach { (origin, list) ->
                    item(key = origin) { SiteHeader(origin) }
                    items(list, key = { "$origin ${it.kind}" }) { permission ->
                        ZenoListRow(
                            title = permissionLabel(permission.kind),
                            subtitle = permissionState(permission),
                            icon = permissionIcon(permission.kind),
                            onClick = { editing = permission },
                        )
                    }
                }
            }
        }
    }
    editing?.let { permission ->
        ChangePermissionDialog(
            permission = permission,
            onChange = { allowed -> onChange(permission, allowed) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SiteHeader(origin: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Ma.xl, end = Ma.xl, top = Ma.l, bottom = Ma.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteSeal(origin, size = 28.dp)
        Spacer(Modifier.width(Ma.m))
        Text(
            UrlDisplay.shortHost(origin),
            style = Zeno.type.label,
            color = Zeno.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
