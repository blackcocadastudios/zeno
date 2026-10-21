package com.blackcocadastudios.zeno.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.design.component.Hairline
import com.blackcocadastudios.zeno.design.component.ZenoIconButton
import com.blackcocadastudios.zeno.design.component.ZenoListRow
import com.blackcocadastudios.zeno.design.component.ZenoSwitchRow
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes

/** What the menu needs to know about the current page. */
data class MenuPage(
    val hasPage: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val loading: Boolean,
    val bookmarked: Boolean,
    val desktopMode: Boolean,
    val translated: Boolean,
    val trackersBlocked: Int,
)

/** An extension's toolbar button, shown as a menu row. */
data class MenuExtension(val id: String, val title: String, val badge: String?)

/** Every action in the menu; the sheet itself holds no logic. */
interface MenuActions {
    fun back()
    fun forward()
    fun reload()
    fun stop()
    fun toggleBookmark()
    fun share()
    fun newTab(private: Boolean)
    fun find()
    fun translate()
    fun setDesktopMode(enabled: Boolean)
    fun addToHome()
    fun extension(id: String)
    fun bookmarks()
    fun history()
    fun downloads()
    fun extensions()
    fun settings()
    fun quit()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    page: MenuPage,
    extensions: List<MenuExtension>,
    showQuit: Boolean,
    actions: MenuActions,
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
        MenuContent(page, extensions, showQuit, actions)
    }
}

/** The menu's rows; separate from the sheet so it can be rendered in tests. */
@Composable
fun MenuContent(page: MenuPage, extensions: List<MenuExtension>, showQuit: Boolean, actions: MenuActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Ma.l),
    ) {
        if (page.hasPage) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Ma.l, vertical = Ma.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ZenoIconButton(ZenoIcons.Back, stringResource(R.string.menu_back), actions::back, enabled = page.canGoBack)
                ZenoIconButton(ZenoIcons.Forward, stringResource(R.string.menu_forward), actions::forward, enabled = page.canGoForward)
                if (page.loading) {
                    ZenoIconButton(ZenoIcons.Close, stringResource(R.string.toolbar_stop), actions::stop)
                } else {
                    ZenoIconButton(ZenoIcons.Reload, stringResource(R.string.menu_reload), actions::reload)
                }
                ZenoIconButton(
                    if (page.bookmarked) ZenoIcons.BookmarkCheck else ZenoIcons.Bookmark,
                    stringResource(if (page.bookmarked) R.string.menu_bookmarked else R.string.menu_bookmark),
                    actions::toggleBookmark,
                    tint = if (page.bookmarked) Zeno.colors.accent else Zeno.colors.ink,
                )
                ZenoIconButton(ZenoIcons.Share, stringResource(R.string.menu_share), actions::share)
            }
            Hairline(Modifier.padding(horizontal = Ma.xl))
        }

        if (extensions.isNotEmpty()) {
            extensions.forEach { ext ->
                ZenoListRow(
                    title = ext.title,
                    icon = ZenoIcons.Extensions,
                    trailing = ext.badge?.let { badge -> { Badge(badge) } },
                    onClick = { actions.extension(ext.id) },
                )
            }
            Hairline(Modifier.padding(horizontal = Ma.xl))
        }

        ZenoListRow(stringResource(R.string.menu_new_tab), icon = ZenoIcons.Add, onClick = { actions.newTab(private = false) })
        ZenoListRow(stringResource(R.string.menu_new_private_tab), icon = ZenoIcons.MoonStar, onClick = { actions.newTab(private = true) })
        if (page.hasPage) {
            ZenoListRow(stringResource(R.string.menu_find), icon = ZenoIcons.FindInPage, onClick = actions::find)
            ZenoListRow(
                stringResource(if (page.translated) R.string.menu_show_original else R.string.menu_translate),
                icon = ZenoIcons.Translate,
                onClick = actions::translate,
            )
            ZenoSwitchRow(
                title = stringResource(R.string.menu_desktop),
                icon = ZenoIcons.Desktop,
                checked = page.desktopMode,
                onCheckedChange = actions::setDesktopMode,
            )
            ZenoListRow(stringResource(R.string.menu_add_to_home), icon = ZenoIcons.Pin, onClick = actions::addToHome)
        }
        Hairline(Modifier.padding(horizontal = Ma.xl))
        ZenoListRow(stringResource(R.string.menu_bookmarks), icon = ZenoIcons.Bookmark, onClick = actions::bookmarks)
        ZenoListRow(stringResource(R.string.menu_history), icon = ZenoIcons.History, onClick = actions::history)
        ZenoListRow(stringResource(R.string.menu_downloads), icon = ZenoIcons.Download, onClick = actions::downloads)
        ZenoListRow(stringResource(R.string.menu_extensions), icon = ZenoIcons.Extensions, onClick = actions::extensions)
        ZenoListRow(stringResource(R.string.menu_settings), icon = ZenoIcons.Settings, onClick = actions::settings)
        if (showQuit) {
            ZenoListRow(
                stringResource(R.string.menu_quit),
                icon = ZenoIcons.Flame,
                titleColor = Zeno.colors.seal,
                onClick = actions::quit,
            )
        }
        if (page.hasPage && page.trackersBlocked > 0) {
            Text(
                stringResource(R.string.toolbar_trackers_blocked, page.trackersBlocked),
                style = Zeno.type.caption,
                color = Zeno.colors.inkSoft,
                modifier = Modifier.padding(start = Ma.xl, top = Ma.m),
            )
        }
    }
}

@Composable
private fun Badge(text: String) {
    Box(
        Modifier
            .clip(ZenoShapes.pill)
            .background(Zeno.colors.accentContainer)
            .padding(horizontal = Ma.s, vertical = Ma.xxs),
    ) {
        Text(text, style = Zeno.type.caption, color = Zeno.colors.onAccentContainer)
    }
}

@Composable
fun SheetHandle() {
    Box(
        Modifier
            .padding(top = Ma.m, bottom = Ma.s)
            .width(36.dp)
            .height(4.dp)
            .clip(ZenoShapes.pill)
            .background(Zeno.colors.line),
    )
}
