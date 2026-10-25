package com.blackcocadastudios.zeno.ui.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.design.component.Hairline
import com.blackcocadastudios.zeno.design.component.ZenoListRow
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes
import com.blackcocadastudios.zeno.engine.HitResult
import com.blackcocadastudios.zeno.engine.HitType
import com.blackcocadastudios.zeno.ui.menu.SheetHandle

interface ContextMenuActions {
    fun openInNewTab(url: String, private: Boolean)
    fun copy(text: String)
    fun share(url: String)
    fun download(url: String)
}

/** Long press on a link, image or video. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenuSheet(hit: HitResult, privateMode: Boolean, actions: ContextMenuActions, onDismiss: () -> Unit) {
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
        Column(Modifier.fillMaxWidth().padding(bottom = Ma.l)) {
            val header = hit.linkUrl ?: hit.mediaUrl
            if (header != null) {
                hit.linkText?.takeIf { it.isNotBlank() && hit.linkUrl != null }?.let {
                    Text(
                        it,
                        style = Zeno.type.heading,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = Ma.xl),
                    )
                }
                Text(
                    header,
                    style = Zeno.type.caption,
                    color = colors.inkSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Ma.xl, vertical = Ma.xs),
                )
                Hairline(Modifier.padding(horizontal = Ma.xl, vertical = Ma.s))
            }
            fun run(action: () -> Unit) {
                action()
                onDismiss()
            }
            hit.linkUrl?.let { link ->
                if (!privateMode) {
                    ZenoListRow(stringResource(R.string.context_open_new_tab), icon = ZenoIcons.Add, onClick = { run { actions.openInNewTab(link, private = false) } })
                }
                ZenoListRow(stringResource(R.string.context_open_private_tab), icon = ZenoIcons.MoonStar, onClick = { run { actions.openInNewTab(link, private = true) } })
                ZenoListRow(stringResource(R.string.context_copy_link), icon = ZenoIcons.Copy, onClick = { run { actions.copy(link) } })
                ZenoListRow(stringResource(R.string.context_share_link), icon = ZenoIcons.Share, onClick = { run { actions.share(link) } })
                if (hit.type == HitType.Link) {
                    ZenoListRow(stringResource(R.string.context_download_link), icon = ZenoIcons.Download, onClick = { run { actions.download(link) } })
                }
            }
            hit.mediaUrl?.let { media ->
                when (hit.type) {
                    HitType.Image, HitType.ImageLink -> {
                        ZenoListRow(stringResource(R.string.context_open_image), icon = ZenoIcons.Image, onClick = { run { actions.openInNewTab(media, privateMode) } })
                        ZenoListRow(stringResource(R.string.context_save_image), icon = ZenoIcons.Download, onClick = { run { actions.download(media) } })
                        ZenoListRow(stringResource(R.string.context_copy_image_link), icon = ZenoIcons.Link, onClick = { run { actions.copy(media) } })
                        ZenoListRow(stringResource(R.string.context_share_image), icon = ZenoIcons.Share, onClick = { run { actions.share(media) } })
                    }
                    HitType.Video, HitType.Audio -> {
                        ZenoListRow(stringResource(R.string.context_copy_media_link), icon = ZenoIcons.Link, onClick = { run { actions.copy(media) } })
                        ZenoListRow(stringResource(R.string.context_download_link), icon = ZenoIcons.Download, onClick = { run { actions.download(media) } })
                    }
                    else -> Unit
                }
            }
        }
    }
}
