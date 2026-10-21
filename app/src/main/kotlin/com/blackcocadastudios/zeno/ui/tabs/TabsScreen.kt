package com.blackcocadastudios.zeno.ui.tabs

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.browser.state.TabState
import com.blackcocadastudios.zeno.browser.url.UrlDisplay
import com.blackcocadastudios.zeno.design.component.EmptyState
import com.blackcocadastudios.zeno.design.component.Enso
import com.blackcocadastudios.zeno.design.component.ZenoIconButton
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes
import com.blackcocadastudios.zeno.ui.common.SiteSeal

/**
 * The tab grid. Normal and private tabs live on two sides of one sheet;
 * the selected tab is outlined in the accent.
 */
@Composable
fun TabsScreen(
    tabs: List<TabState>,
    selectedTabId: String?,
    privateMode: Boolean,
    thumbnail: (String) -> Bitmap?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onCloseAll: (private: Boolean) -> Unit,
    onNewTab: (private: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showingPrivate by remember { mutableStateOf(privateMode) }
    val colors = Zeno.colors
    val visible = tabs.filter { it.isPrivate == showingPrivate }
    val normalCount = tabs.count { !it.isPrivate }
    val privateCount = tabs.size - normalCount

    Box(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = Ma.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZenoIconButton(ZenoIcons.Close, stringResource(R.string.action_close), onDismiss)
                Spacer(Modifier.weight(1f))
                Segmented(
                    left = stringResource(R.string.tabs_normal) + "  $normalCount",
                    right = stringResource(R.string.tabs_private) + "  $privateCount",
                    rightSelected = showingPrivate,
                    onSelect = { showingPrivate = it },
                )
                Spacer(Modifier.weight(1f))
                var menu by remember { mutableStateOf(false) }
                Box {
                    ZenoIconButton(ZenoIcons.More, stringResource(R.string.action_more), { menu = true }, enabled = visible.isNotEmpty())
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        containerColor = colors.paperRaised,
                        shape = ZenoShapes.medium,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(if (showingPrivate) R.string.tabs_close_all_private else R.string.tabs_close_all),
                                    style = Zeno.type.body,
                                    color = colors.seal,
                                )
                            },
                            onClick = {
                                menu = false
                                onCloseAll(showingPrivate)
                            },
                        )
                    }
                }
            }
            if (visible.isEmpty()) {
                EmptyState(
                    title = stringResource(if (showingPrivate) R.string.tabs_empty_private else R.string.tabs_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(152.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = Ma.l, end = Ma.l, top = Ma.s, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(Ma.m),
                    verticalArrangement = Arrangement.spacedBy(Ma.m),
                ) {
                    items(visible, key = { it.id }) { tab ->
                        // A sideways swipe closes the tab, like brushing a card off the table.
                        SwipeToDismissBox(
                            state = rememberSwipeToDismissBoxState(),
                            backgroundContent = {},
                            onDismiss = { onClose(tab.id) },
                        ) {
                            TabCard(
                                tab = tab,
                                selected = tab.id == selectedTabId,
                                thumbnail = thumbnail(tab.id),
                                onSelect = { onSelect(tab.id) },
                                onClose = { onClose(tab.id) },
                            )
                        }
                    }
                }
            }
        }
        NewTabButton(
            label = stringResource(if (showingPrivate) R.string.tabs_new_private else R.string.tabs_new),
            onClick = { onNewTab(showingPrivate) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(Ma.xl),
        )
    }
}

@Composable
private fun Segmented(left: String, right: String, rightSelected: Boolean, onSelect: (Boolean) -> Unit) {
    val colors = Zeno.colors
    Row(
        Modifier
            .clip(ZenoShapes.pill)
            .background(colors.paperSunken)
            .padding(3.dp),
    ) {
        listOf(false to left, true to right).forEach { (isRight, label) ->
            val selected = isRight == rightSelected
            Box(
                Modifier
                    .clip(ZenoShapes.pill)
                    .then(if (selected) Modifier.background(colors.paperRaised) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(isRight) }
                    .padding(horizontal = Ma.l, vertical = Ma.s),
            ) {
                Text(label, style = Zeno.type.label, color = if (selected) colors.ink else colors.inkSoft)
            }
        }
    }
}

@Composable
private fun TabCard(tab: TabState, selected: Boolean, thumbnail: Bitmap?, onSelect: () -> Unit, onClose: () -> Unit) {
    val colors = Zeno.colors
    val url = tab.url
    val title = when {
        url == null -> stringResource(R.string.tabs_new_tab_title)
        tab.title.isNotBlank() -> tab.title
        else -> UrlDisplay.shortHost(url)
    }
    Column(
        Modifier
            .clip(ZenoShapes.medium)
            .background(colors.paperRaised)
            .border(if (selected) 2.dp else 0.5.dp, if (selected) colors.accent else colors.line, ZenoShapes.medium)
            .clickable(onClick = onSelect),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(start = Ma.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (url != null) {
                SiteSeal(url, size = 22.dp)
                Spacer(Modifier.width(Ma.s))
            }
            Text(
                title,
                style = Zeno.type.caption.copy(color = colors.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(ZenoShapes.pill)
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ZenoIcons.Close, stringResource(R.string.tabs_close), tint = colors.inkSoft, modifier = Modifier.size(18.dp))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .background(colors.paperSunken),
            contentAlignment = Alignment.Center,
        ) {
            when {
                thumbnail != null -> Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
                url != null -> SiteSeal(url, size = 56.dp)
                else -> Enso(Modifier.size(44.dp), color = colors.accent.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun NewTabButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Zeno.colors
    Row(
        modifier
            .shadow(6.dp, ZenoShapes.pill)
            .clip(ZenoShapes.pill)
            .background(colors.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = Ma.l, end = Ma.xl)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ZenoIcons.Add, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Ma.s))
        Text(label, style = Zeno.type.label, color = colors.onAccent)
    }
}
