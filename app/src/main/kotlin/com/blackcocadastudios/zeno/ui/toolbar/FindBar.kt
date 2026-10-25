package com.blackcocadastudios.zeno.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.design.component.Hairline
import com.blackcocadastudios.zeno.design.component.ZenoIconButton
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes
import com.blackcocadastudios.zeno.engine.FindResult
import kotlinx.coroutines.delay

/** Replaces the toolbar while searching the page. */
@Composable
fun FindBar(
    result: FindResult?,
    onSearch: (String) -> Unit,
    onNext: (String) -> Unit,
    onPrevious: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Zeno.colors
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(text) {
        delay(120)
        onSearch(text)
    }
    Column(modifier.fillMaxWidth().background(colors.paper)) {
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
                    .padding(horizontal = Ma.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(stringResource(R.string.find_hint), style = Zeno.type.url, color = colors.inkFaint, maxLines = 1)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = Zeno.type.url.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onNext(text) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }
                if (text.isNotEmpty() && result != null) {
                    Spacer(Modifier.width(Ma.s))
                    Text(
                        if (result.found) stringResource(R.string.find_count, result.current, result.total) else stringResource(R.string.find_none),
                        style = Zeno.type.caption,
                        color = if (result.found) colors.inkSoft else colors.seal,
                    )
                }
            }
            ZenoIconButton(ZenoIcons.ChevronUp, stringResource(R.string.find_previous), { onPrevious(text) }, enabled = result?.found == true)
            ZenoIconButton(ZenoIcons.ChevronDown, stringResource(R.string.find_next), { onNext(text) }, enabled = result?.found == true)
            ZenoIconButton(ZenoIcons.Close, stringResource(R.string.find_close), onClose)
        }
    }
}
