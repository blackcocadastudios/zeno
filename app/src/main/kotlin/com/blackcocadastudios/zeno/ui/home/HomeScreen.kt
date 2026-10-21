package com.blackcocadastudios.zeno.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.design.component.Enso
import com.blackcocadastudios.zeno.design.component.Seigaiha
import com.blackcocadastudios.zeno.design.component.ZenoWordmark
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoMotion
import com.blackcocadastudios.zeno.design.theme.ZenoShapes
import com.blackcocadastudios.zeno.ui.Shortcut
import com.blackcocadastudios.zeno.ui.common.SiteSeal

/** The ensō is painted once per launch; after that it simply is. */
private var introPlayed = false

/**
 * Zeno's new tab page: the mark, a few shortcuts, waves at the horizon.
 * In private mode, the night and a sentence about what is (not) kept.
 */
@Composable
fun HomeScreen(
    privateMode: Boolean,
    shortcuts: List<Shortcut>,
    onOpen: (Shortcut) -> Unit,
    onOpenPrivate: (Shortcut) -> Unit,
    onRemove: (Shortcut) -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    /** A quiet card under the shortcuts (first-run suggestion). */
    footer: (@Composable () -> Unit)? = null,
) {
    val colors = Zeno.colors
    val stroke = remember { Animatable(if (introPlayed || !animate) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (stroke.value < 1f) {
            stroke.animateTo(1f, tween(900, delayMillis = 120, easing = ZenoMotion.Calm))
            introPlayed = true
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.paper),
    ) {
        Seigaiha(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(132.dp),
            lineAlpha = if (colors.isDark) 0.22f else 0.26f,
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Ma.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.9f))
            if (privateMode) {
                Enso(Modifier.size(72.dp), color = colors.accent, drawProgress = stroke.value)
                Spacer(Modifier.height(Ma.xl))
                Text(stringResource(R.string.home_private_title), style = Zeno.type.mincho.copy(fontSize = 22.sp), color = colors.ink)
                Spacer(Modifier.height(Ma.m))
                Text(
                    stringResource(R.string.home_private_body),
                    style = Zeno.type.bodySmall,
                    color = colors.inkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 340.dp),
                )
            } else {
                ZenoWordmark(fontSize = 52.sp, drawProgress = stroke.value)
            }
            Spacer(Modifier.height(Ma.xxxl))
            if (!privateMode && shortcuts.isNotEmpty()) {
                ShortcutGrid(shortcuts, onOpen, onOpenPrivate, onRemove)
            }
            if (footer != null) {
                Spacer(Modifier.height(Ma.xl))
                footer()
            }
            Spacer(Modifier.weight(1.3f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutGrid(
    shortcuts: List<Shortcut>,
    onOpen: (Shortcut) -> Unit,
    onOpenPrivate: (Shortcut) -> Unit,
    onRemove: (Shortcut) -> Unit,
) {
    FlowRow(
        modifier = Modifier.widthIn(max = 380.dp),
        horizontalArrangement = Arrangement.spacedBy(Ma.s, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Ma.l),
        maxItemsInEachRow = 4,
    ) {
        shortcuts.forEach { shortcut ->
            ShortcutTile(shortcut, onOpen, onOpenPrivate, onRemove)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTile(
    shortcut: Shortcut,
    onOpen: (Shortcut) -> Unit,
    onOpenPrivate: (Shortcut) -> Unit,
    onRemove: (Shortcut) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .width(80.dp)
                .clip(ZenoShapes.medium)
                .combinedClickable(onClick = { onOpen(shortcut) }, onLongClick = { menu = true })
                .padding(vertical = Ma.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SiteSeal(shortcut.url, size = 52.dp)
            Spacer(Modifier.height(Ma.s))
            Text(
                shortcut.title,
                style = Zeno.type.caption,
                color = Zeno.colors.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Ma.xs),
            )
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            containerColor = Zeno.colors.paperRaised,
            shape = ZenoShapes.medium,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_shortcut_open_private), style = Zeno.type.body) },
                onClick = {
                    menu = false
                    onOpenPrivate(shortcut)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(if (shortcut.pinned) R.string.home_shortcut_remove else R.string.home_shortcut_hide),
                        style = Zeno.type.body,
                    )
                },
                onClick = {
                    menu = false
                    onRemove(shortcut)
                },
            )
        }
    }
}
