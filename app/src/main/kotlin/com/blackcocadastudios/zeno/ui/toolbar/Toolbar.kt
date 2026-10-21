package com.blackcocadastudios.zeno.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.design.component.Hairline
import com.blackcocadastudios.zeno.design.component.ProgressLine
import com.blackcocadastudios.zeno.design.component.TabCounter
import com.blackcocadastudios.zeno.design.component.ZenoIconButton
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes

/** What the address pill shows when not editing. */
data class AddressDisplay(
    /** Host, search terms, or null for the hint. */
    val text: String?,
    val kind: AddressKind,
    val loading: Boolean,
    val progress: Int,
    val canReload: Boolean,
)

enum class AddressKind { Home, Secure, Insecure, Search, Internal }

/**
 * The bottom bar, Firefox-style: address pill, tab counter, menu. Everything a
 * thumb needs, nothing else.
 */
@Composable
fun Toolbar(
    display: AddressDisplay,
    tabCount: Int,
    privateMode: Boolean,
    onAddressClick: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Zeno.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.paper),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Hairline()
            ProgressLine(progress = display.progress / 100f, visible = display.loading)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(start = Ma.m, end = Ma.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AddressPill(
                display = display,
                privateMode = privateMode,
                onClick = onAddressClick,
                onReload = onReload,
                onStop = onStop,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Ma.xs))
            TabsButton(tabCount, onTabsClick)
            ZenoIconButton(ZenoIcons.More, stringResource(R.string.toolbar_menu), onMenuClick)
        }
    }
}

@Composable
private fun AddressPill(
    display: AddressDisplay,
    privateMode: Boolean,
    onClick: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Zeno.colors
    val hint = stringResource(if (privateMode) R.string.toolbar_hint_private else R.string.toolbar_hint)
    Row(
        modifier
            .height(44.dp)
            .clip(ZenoShapes.pill)
            .background(colors.paperSunken)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(start = Ma.l, end = Ma.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (icon, description) = leadingIcon(display.kind, privateMode)
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (display.kind == AddressKind.Insecure) colors.seal else colors.inkSoft,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Ma.s))
        Text(
            text = display.text ?: hint,
            style = Zeno.type.url,
            color = if (display.text == null) colors.inkFaint else colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        when {
            display.loading -> PillButton(ZenoIcons.Close, stringResource(R.string.toolbar_stop), onStop)
            display.canReload -> PillButton(ZenoIcons.Reload, stringResource(R.string.toolbar_reload), onReload)
        }
    }
}

@Composable
private fun leadingIcon(kind: AddressKind, privateMode: Boolean): Pair<ImageVector, String?> = when {
    kind == AddressKind.Home && privateMode -> ZenoIcons.MoonStar to stringResource(R.string.toolbar_private)
    kind == AddressKind.Home || kind == AddressKind.Search -> ZenoIcons.Search to null
    kind == AddressKind.Secure -> ZenoIcons.Lock to stringResource(R.string.toolbar_secure)
    kind == AddressKind.Insecure -> ZenoIcons.LockOpen to stringResource(R.string.toolbar_insecure)
    else -> ZenoIcons.Globe to null
}

@Composable
private fun PillButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(ZenoShapes.pill)
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Zeno.colors.inkSoft, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TabsButton(count: Int, onClick: () -> Unit) {
    val description = pluralStringResource(R.plurals.toolbar_tab_count, count, count)
    Box(
        Modifier
            .size(Ma.touch)
            .clip(ZenoShapes.pill)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        TabCounter(count)
    }
}
