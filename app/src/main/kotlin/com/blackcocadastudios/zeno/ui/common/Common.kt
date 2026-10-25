package com.blackcocadastudios.zeno.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.design.component.Hairline
import com.blackcocadastudios.zeno.design.component.Seal
import com.blackcocadastudios.zeno.design.component.ZenoIconButton
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes
import com.blackcocadastudios.zeno.browser.url.UrlDisplay
import com.blackcocadastudios.zeno.ui.UiMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

/** Full-screen page (settings, history…) with a quiet top bar. */
@Composable
fun ZenoPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Zeno.colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = Ma.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZenoIconButton(ZenoIcons.Back, stringResource(R.string.action_back), onBack)
            Text(
                text = title,
                style = Zeno.type.title,
                color = Zeno.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Ma.xs),
            )
            actions()
        }
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            content()
        }
    }
}

/** A site's seal from its URL. */
@Composable
fun SiteSeal(url: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val host = remember(url) { UrlDisplay.host(url) ?: url }
    val letter = remember(url) { UrlDisplay.sealLetter(url) }
    Seal(letter = letter, key = host.removePrefix("www."), modifier = modifier, size = size)
}

/** Dialog in Zeno's colors. */
@Composable
fun ZenoDialog(
    onDismiss: () -> Unit,
    title: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismissClick: () -> Unit = onDismiss,
    destructive: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val colors = Zeno.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.paperRaised,
        titleContentColor = colors.ink,
        textContentColor = colors.inkSoft,
        shape = ZenoShapes.large,
        title = title?.let { { Text(it, style = Zeno.type.heading) } },
        text = content,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, style = Zeno.type.label, color = if (destructive) colors.seal else colors.accentInk)
            }
        },
        dismissButton = dismissLabel?.let {
            {
                TextButton(onClick = onDismissClick) {
                    Text(it, style = Zeno.type.label, color = colors.inkSoft)
                }
            }
        },
    )
}

/** Pick one of several options; used by settings. */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String?>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Zeno.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.paperRaised,
        shape = ZenoShapes.large,
        title = { Text(title, style = Zeno.type.heading, color = colors.ink) },
        text = {
            LazyColumn {
                itemsIndexed(options) { index, (label, description) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(ZenoShapes.small)
                            .clickable { onSelect(index) }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = Ma.s, vertical = Ma.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioMark(selected = index == selected)
                        Spacer(Modifier.width(Ma.m))
                        Column(Modifier.weight(1f)) {
                            Text(label, style = Zeno.type.body, color = colors.ink)
                            if (description != null) Text(description, style = Zeno.type.bodySmall, color = colors.inkSoft)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = colors.inkSoft) }
        },
    )
}

/** A selection dot drawn like an ensō point: ring when off, filled when on. */
@Composable
fun RadioMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = Zeno.colors
    Box(
        modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.paperSunken),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.paperRaised),
            )
        }
    }
}

/** Short messages that rise above the toolbar and leave on their own. A new one replaces the current one. */
@Composable
fun MessageHost(messages: SharedFlow<UiMessage>, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<UiMessage?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(messages) {
        messages.collectLatest { message ->
            current = message
            visible = true
            delay(if (message.action != null) 4_500 else 2_500)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible && current != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        val message = current ?: return@AnimatedVisibility
        val colors = Zeno.colors
        Row(
            Modifier
                .padding(horizontal = Ma.l, vertical = Ma.s)
                .shadow(8.dp, ZenoShapes.pill, clip = false)
                .clip(ZenoShapes.pill)
                .background(colors.ink)
                .padding(start = Ma.xl, end = if (message.action != null) Ma.s else Ma.xl)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Ma.s),
        ) {
            Text(
                message.text,
                style = Zeno.type.bodySmall,
                color = colors.paper,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            val label = message.actionLabel
            val action = message.action
            if (label != null && action != null) {
                TextButton(onClick = {
                    visible = false
                    action()
                }) {
                    Text(label, style = Zeno.type.label, color = colors.accentContainer)
                }
            }
        }
    }
}

/** Hairline-separated group of rows. */
@Composable
fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        content()
        Spacer(Modifier.height(Ma.s))
        Hairline(Modifier.padding(horizontal = Ma.xl))
    }
}
