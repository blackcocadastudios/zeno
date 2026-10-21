package com.blackcocadastudios.zeno.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.browser.url.UrlDisplay
import com.blackcocadastudios.zeno.design.component.Hairline
import com.blackcocadastudios.zeno.design.component.ZenoIconButton
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes
import com.blackcocadastudios.zeno.ui.common.SiteSeal
import com.blackcocadastudios.zeno.web.Suggestion
import com.blackcocadastudios.zeno.web.Suggestions
import kotlinx.coroutines.delay

/**
 * Editing the address: the field stays at the bottom, above the keyboard, and
 * suggestions grow upwards from it so the best one is nearest the thumb.
 * Typing the start of a known site completes it inline.
 */
@Composable
fun AddressEditor(
    initialText: String,
    privateMode: Boolean,
    suggest: suspend (String) -> Suggestions,
    searchUrl: (String) -> String,
    onSubmit: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Zeno.colors
    var value by remember { mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length))) }
    var typed by remember { mutableStateOf(initialText) }
    var allowCompletion by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(Suggestions()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    LaunchedEffect(typed) {
        if (typed.isBlank() || typed == initialText) {
            suggestions = Suggestions()
            return@LaunchedEffect
        }
        delay(90)
        val result = suggest(typed)
        suggestions = result
        val completion = result.completion
        if (allowCompletion && completion != null && value.text == typed && value.selection.collapsed &&
            completion.startsWith(typed.lowercase()) && completion.length > typed.length
        ) {
            value = TextFieldValue(typed + completion.substring(typed.length), TextRange(typed.length, completion.length))
        }
    }

    val hasSuggestions = suggestions.items.isNotEmpty()
    Box(
        modifier
            .fillMaxSize()
            .then(if (hasSuggestions) Modifier.background(colors.paper) else Modifier),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            if (hasSuggestions) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = Ma.s),
                ) {
                    items(suggestions.items) { item ->
                        SuggestionRow(
                            suggestion = item,
                            onClick = {
                                when (item) {
                                    is Suggestion.Search -> onOpenUrl(searchUrl(item.query))
                                    is Suggestion.Address -> onOpenUrl(item.url)
                                    is Suggestion.Site -> onOpenUrl(item.url)
                                    is Suggestion.Term -> onOpenUrl(searchUrl(item.text))
                                }
                            },
                            onFill = { text ->
                                val filled = "$text "
                                typed = filled
                                allowCompletion = false
                                value = TextFieldValue(filled, TextRange(filled.length))
                            },
                        )
                    }
                }
            } else {
                // Tapping the page around the field leaves editing.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                )
            }
            Column(Modifier.background(colors.paper)) {
                Hairline()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(start = Ma.m, end = Ma.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(ZenoShapes.pill)
                            .background(colors.paperSunken)
                            .padding(start = Ma.l, end = Ma.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (privateMode) ZenoIcons.MoonStar else ZenoIcons.Search,
                            contentDescription = null,
                            tint = colors.inkSoft,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Ma.s))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (value.text.isEmpty()) {
                                Text(
                                    stringResource(if (privateMode) R.string.toolbar_hint_private else R.string.toolbar_hint),
                                    style = Zeno.type.url,
                                    color = colors.inkFaint,
                                    maxLines = 1,
                                )
                            }
                            BasicTextField(
                                value = value,
                                onValueChange = { next ->
                                    allowCompletion = next.text.length > typed.length && next.selection.collapsed
                                    typed = next.text
                                    value = next
                                },
                                singleLine = true,
                                textStyle = Zeno.type.url.copy(color = colors.ink),
                                cursorBrush = SolidColor(colors.accent),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go,
                                    autoCorrectEnabled = false,
                                    capitalization = KeyboardCapitalization.None,
                                ),
                                keyboardActions = KeyboardActions(onGo = { onSubmit(value.text) }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focus),
                            )
                        }
                        if (value.text.isNotEmpty()) {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button) {
                                        typed = ""
                                        allowCompletion = false
                                        value = TextFieldValue("")
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(ZenoIcons.Clear, stringResource(R.string.toolbar_clear), tint = colors.inkSoft, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    ZenoIconButton(ZenoIcons.Close, stringResource(R.string.toolbar_cancel), onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: Suggestion, onClick: () -> Unit, onFill: (String) -> Unit) {
    val colors = Zeno.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Ma.l, vertical = Ma.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (suggestion) {
            is Suggestion.Site -> SiteSeal(suggestion.url, size = 36.dp)
            is Suggestion.Address -> RoundIcon(ZenoIcons.Globe)
            is Suggestion.Search, is Suggestion.Term -> RoundIcon(ZenoIcons.Search)
        }
        Spacer(Modifier.width(Ma.m))
        Column(Modifier.weight(1f)) {
            val (title, subtitle) = when (suggestion) {
                is Suggestion.Search -> suggestion.query to stringResource(R.string.suggestion_search_with, suggestion.engineName)
                is Suggestion.Address -> UrlDisplay.shortHost(suggestion.url) to suggestion.url
                is Suggestion.Site -> suggestion.title to suggestion.url.substringAfter("://").removePrefix("www.")
                is Suggestion.Term -> suggestion.text to null
            }
            Text(title, style = Zeno.type.body, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = Zeno.type.caption, color = colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when (suggestion) {
            is Suggestion.Site -> if (suggestion.bookmarked) {
                Icon(ZenoIcons.Bookmark, stringResource(R.string.suggestion_bookmark), tint = colors.accent, modifier = Modifier.size(18.dp))
            }
            is Suggestion.Term -> ZenoIconButton(
                ZenoIcons.ArrowUpLeft,
                stringResource(R.string.suggestion_fill, suggestion.text),
                onClick = { onFill(suggestion.text) },
                tint = colors.inkSoft,
            )
            else -> Unit
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Zeno.colors.paperSunken),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Zeno.colors.inkSoft, modifier = Modifier.size(18.dp))
    }
}
