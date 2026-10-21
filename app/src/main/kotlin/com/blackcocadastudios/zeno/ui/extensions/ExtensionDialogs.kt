package com.blackcocadastudios.zeno.ui.extensions

import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes
import com.blackcocadastudios.zeno.engine.ExtensionInstallRequest
import com.blackcocadastudios.zeno.engine.ExtensionPopup
import com.blackcocadastudios.zeno.engine.gecko.GeckoEngineView
import com.blackcocadastudios.zeno.ui.PendingInstall
import com.blackcocadastudios.zeno.ui.common.ZenoDialog
import com.blackcocadastudios.zeno.ui.menu.SheetHandle

/** "Install uBlock Origin? It will be able to…" */
@Composable
fun InstallDialog(pending: PendingInstall) {
    val request = pending.request
    ZenoDialog(
        onDismiss = { pending.respond(false) },
        title = stringResource(R.string.extensions_prompt_title, request.name),
        confirmLabel = stringResource(R.string.extensions_install),
        onConfirm = { pending.respond(true) },
        dismissLabel = stringResource(R.string.prompt_cancel),
        onDismissClick = { pending.respond(false) },
    ) {
        val lines = permissionLines(request)
        Column(verticalArrangement = Arrangement.spacedBy(Ma.s)) {
            if (lines.isEmpty()) {
                Text(stringResource(R.string.extensions_prompt_no_permissions), style = Zeno.type.bodySmall)
            } else {
                Text(stringResource(R.string.extensions_prompt_body), style = Zeno.type.bodySmall)
                lines.forEach { Text("·  $it", style = Zeno.type.bodySmall, color = Zeno.colors.ink) }
            }
        }
    }
}

/** Human descriptions for WebExtension permissions, most important first. */
@Composable
private fun permissionLines(request: ExtensionInstallRequest): List<String> {
    val lines = mutableListOf<String>()
    val allSites = request.origins.any { it == "<all_urls>" || it.startsWith("*://*/") || it == "http://*/*" || it == "https://*/*" } ||
        "<all_urls>" in request.permissions
    when {
        allSites -> lines += stringResource(R.string.permission_all_sites)
        request.origins.isNotEmpty() -> lines += pluralStringResource(R.plurals.permission_some_sites, request.origins.size, request.origins.size)
    }
    val known = mapOf(
        "tabs" to R.string.permission_tabs,
        "history" to R.string.permission_history,
        "bookmarks" to R.string.permission_bookmarks,
        "downloads" to R.string.permission_downloads,
        "unlimitedStorage" to R.string.permission_storage_ext,
        "privacy" to R.string.permission_privacy,
        "clipboardRead" to R.string.permission_clipboard,
        "clipboardWrite" to R.string.permission_clipboard,
        "notifications" to R.string.permission_notifications,
        "webRequestBlocking" to R.string.permission_web_requests,
        "declarativeNetRequest" to R.string.permission_web_requests,
    )
    val quiet = setOf("storage", "contextMenus", "menus", "alarms", "activeTab", "webNavigation", "webRequest", "dns", "scripting", "<all_urls>")
    request.permissions.distinct().forEach { permission ->
        val res = known[permission]
        when {
            res != null -> lines += stringResource(res)
            permission !in quiet -> lines += stringResource(R.string.permission_generic, permission)
        }
    }
    return lines.distinct()
}

/** An extension's popup (uBlock Origin's panel, for instance) in a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionPopupSheet(popup: ExtensionPopup, onDismiss: () -> Unit) {
    val colors = Zeno.colors
    val context = LocalContext.current
    val view = remember(popup) { GeckoEngineView(context) }
    DisposableEffect(view) {
        onDispose { view.release() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.paperRaised,
        scrimColor = colors.scrim,
        shape = ZenoShapes.sheet,
        dragHandle = { SheetHandle() },
    ) {
        Text(popup.title, style = Zeno.type.heading, color = colors.ink, modifier = Modifier.padding(horizontal = Ma.xl, vertical = Ma.s))
        AndroidView(
            factory = {
                view.apply {
                    (parent as? ViewGroup)?.removeView(this)
                    render(popup.session)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .padding(bottom = Ma.l),
        )
    }
}
