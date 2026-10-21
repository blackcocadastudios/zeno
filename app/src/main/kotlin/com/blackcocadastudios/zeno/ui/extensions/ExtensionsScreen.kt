package com.blackcocadastudios.zeno.ui.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.design.component.Seal
import com.blackcocadastudios.zeno.design.component.SectionLabel
import com.blackcocadastudios.zeno.design.component.ZenoIconButton
import com.blackcocadastudios.zeno.design.component.ZenoListRow
import com.blackcocadastudios.zeno.design.component.ZenoSwitch
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes
import com.blackcocadastudios.zeno.engine.ExtensionInfo
import com.blackcocadastudios.zeno.ui.common.ZenoPage

/** Extensions Zeno suggests, by AMO slug. uBlock Origin first: it is how Zeno blocks ads. */
data class Recommended(val id: String, val slug: String, val name: Int, val description: Int) {
    val installUrl get() = "https://addons.mozilla.org/firefox/downloads/latest/$slug/latest.xpi"
}

val RecommendedExtensions = listOf(
    Recommended("uBlock0@raymondhill.net", "ublock-origin", R.string.extensions_ublock_name, R.string.extensions_ublock_desc),
    Recommended("addon@darkreader.org", "darkreader", R.string.extensions_darkreader_name, R.string.extensions_darkreader_desc),
    Recommended("{aecec67f-0d10-4fa7-b7c7-609a2db280cf}", "violentmonkey", R.string.extensions_violentmonkey_name, R.string.extensions_violentmonkey_desc),
    Recommended("sponsorBlocker@ajay.app", "sponsorblock", R.string.extensions_sponsorblock_name, R.string.extensions_sponsorblock_desc),
)

interface ExtensionsActions {
    fun install(url: String)
    fun setEnabled(id: String, enabled: Boolean)
    fun setAllowedInPrivate(id: String, allowed: Boolean)
    fun remove(id: String)
    fun openUrl(url: String)
    fun back()
}

@Composable
fun ExtensionsScreen(installed: List<ExtensionInfo>, installing: Set<String>, actions: ExtensionsActions) {
    ZenoPage(title = stringResource(R.string.extensions_title), onBack = actions::back) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Ma.xxl),
        ) {
            SectionLabel(stringResource(R.string.extensions_installed))
            if (installed.isEmpty()) {
                Text(
                    stringResource(R.string.extensions_none),
                    style = Zeno.type.bodySmall,
                    color = Zeno.colors.inkSoft,
                    modifier = Modifier.padding(horizontal = Ma.xl, vertical = Ma.s),
                )
            }
            installed.forEach { extension -> InstalledRow(extension, actions) }

            val available = RecommendedExtensions.filter { rec -> installed.none { it.id == rec.id } }
            if (available.isNotEmpty()) {
                SectionLabel(stringResource(R.string.extensions_recommended))
                available.forEach { rec ->
                    RecommendedRow(rec, installing = rec.installUrl in installing, onInstall = { actions.install(rec.installUrl) })
                }
            }
            Spacer(Modifier.height(Ma.l))
            ZenoListRow(
                title = stringResource(R.string.extensions_browse),
                icon = ZenoIcons.OpenExternal,
                onClick = { actions.openUrl("https://addons.mozilla.org/android/") },
            )
        }
    }
}

@Composable
private fun InstalledRow(extension: ExtensionInfo, actions: ExtensionsActions) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Ma.xl, end = Ma.xs, top = Ma.s, bottom = Ma.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Seal(letter = extension.name, key = extension.id, size = 40.dp)
        Spacer(Modifier.width(Ma.l))
        Column(Modifier.weight(1f)) {
            Text(extension.name, style = Zeno.type.body, color = Zeno.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(extension.version, extension.description).joinToString(" · "),
                style = Zeno.type.caption,
                color = Zeno.colors.inkSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ZenoSwitch(checked = extension.enabled, onCheckedChange = { actions.setEnabled(extension.id, it) })
        Box {
            ZenoIconButton(ZenoIcons.More, stringResource(R.string.action_more), { menu = true }, tint = Zeno.colors.inkSoft)
            DropdownMenu(menu, { menu = false }, containerColor = Zeno.colors.paperRaised, shape = ZenoShapes.medium) {
                extension.optionsUrl?.let { url ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.extensions_options), style = Zeno.type.body) },
                        onClick = {
                            menu = false
                            actions.openUrl(url)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.extensions_allow_private), style = Zeno.type.body) },
                    trailingIcon = { if (extension.allowedInPrivate) Icon(ZenoIcons.Check, null, tint = Zeno.colors.accent) },
                    onClick = {
                        menu = false
                        actions.setAllowedInPrivate(extension.id, !extension.allowedInPrivate)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.extensions_remove), style = Zeno.type.body, color = Zeno.colors.seal) },
                    onClick = {
                        menu = false
                        actions.remove(extension.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun RecommendedRow(rec: Recommended, installing: Boolean, onInstall: () -> Unit) {
    val name = stringResource(rec.name)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Ma.xl, vertical = Ma.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Seal(letter = name, key = rec.id, size = 40.dp)
        Spacer(Modifier.width(Ma.l))
        Column(Modifier.weight(1f)) {
            Text(name, style = Zeno.type.body, color = Zeno.colors.ink)
            Text(stringResource(rec.description), style = Zeno.type.caption, color = Zeno.colors.inkSoft, maxLines = 2)
        }
        Spacer(Modifier.width(Ma.s))
        Box(
            Modifier
                .clip(ZenoShapes.pill)
                .border(1.dp, Zeno.colors.accent, ZenoShapes.pill)
                .then(if (installing) Modifier.background(Zeno.colors.accentContainer) else Modifier)
                .clickable(enabled = !installing, role = Role.Button, onClick = onInstall)
                .padding(horizontal = Ma.l, vertical = Ma.s),
        ) {
            Text(
                stringResource(if (installing) R.string.extensions_installing else R.string.extensions_install),
                style = Zeno.type.label,
                color = Zeno.colors.accentInk,
            )
        }
    }
}
