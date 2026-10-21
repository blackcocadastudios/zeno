package com.blackcocadastudios.zeno.ui.logins

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.browser.url.UrlDisplay
import com.blackcocadastudios.zeno.design.component.Hairline
import com.blackcocadastudios.zeno.design.component.ZenoIconButton
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.engine.LoginChoice
import com.blackcocadastudios.zeno.engine.PromptRequest
import com.blackcocadastudios.zeno.ui.common.ZenoDialog

/** After a sign-in: keep this login? Closing the dialog is "not now". */
@Composable
fun LoginSaveDialog(
    prompt: PromptRequest.LoginSave,
    onSave: () -> Unit,
    onNever: () -> Unit,
    onNotNow: () -> Unit,
) {
    val site = UrlDisplay.shortHost(prompt.login.origin)
    ZenoDialog(
        onDismiss = onNotNow,
        title = stringResource(if (prompt.isUpdate) R.string.login_update_title else R.string.login_save_title, site),
        confirmLabel = stringResource(if (prompt.isUpdate) R.string.login_update else R.string.login_save),
        onConfirm = onSave,
        dismissLabel = stringResource(R.string.login_not_now),
        onDismissClick = onNotNow,
    ) {
        LoginSaveBody(prompt.login.username, showNever = !prompt.isUpdate, onNever = onNever)
    }
}

/** What will be kept, and the way to never be asked on this site. Separate so it can be rendered in tests. */
@Composable
fun LoginSaveBody(username: String, showNever: Boolean, onNever: () -> Unit) {
    val colors = Zeno.colors
    Column {
        Text(username.ifBlank { stringResource(R.string.login_no_username) }, style = Zeno.type.body, color = colors.ink)
        Text(MASK, style = Zeno.type.body, color = colors.inkSoft)
        if (showNever) {
            Spacer(Modifier.size(Ma.s))
            TextButton(onClick = onNever) {
                Text(stringResource(R.string.login_never), style = Zeno.type.label, color = colors.inkSoft)
            }
        }
    }
}

/**
 * Over the keyboard while a sign-in field has focus: saved logins for the
 * site (or a strong password made up for a sign-up form). It does not cover
 * the page: the user can just keep typing.
 */
@Composable
fun LoginBar(
    prompt: PromptRequest.LoginSelect,
    onSelect: (LoginChoice) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Zeno.colors
    var expanded by remember(prompt) { mutableStateOf(false) }
    val saved = prompt.choices.filterNot { it.generated }
    val single = prompt.choices.singleOrNull()
    Column(
        modifier
            .fillMaxWidth()
            .shadow(8.dp)
            .background(colors.paperRaised),
    ) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Ma.touch)
                .clickable {
                    if (single != null) onSelect(single) else expanded = !expanded
                }
                .padding(start = Ma.l, end = Ma.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ZenoIcons.Password, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Ma.m))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (single?.generated == true) R.string.login_bar_generated else R.string.login_bar_title),
                    style = Zeno.type.label,
                    color = colors.ink,
                )
                val detail = when {
                    single != null && !single.generated -> single.login.username.ifBlank { stringResource(R.string.login_no_username) }
                    saved.isNotEmpty() && single == null -> pluralStringResource(R.plurals.login_bar_count, saved.size, saved.size)
                    else -> null
                }
                if (detail != null) {
                    Text(detail, style = Zeno.type.bodySmall, color = colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (single == null) {
                Icon(
                    if (expanded) ZenoIcons.ChevronDown else ZenoIcons.ChevronUp,
                    contentDescription = stringResource(R.string.login_bar_more),
                    tint = colors.inkSoft,
                    modifier = Modifier.size(20.dp),
                )
            }
            ZenoIconButton(ZenoIcons.Close, stringResource(R.string.login_bar_close), onClose, tint = colors.inkSoft)
        }
        if (prompt.insecure) {
            Text(
                stringResource(R.string.login_bar_insecure),
                style = Zeno.type.caption,
                color = colors.seal,
                modifier = Modifier.padding(start = Ma.l + 20.dp + Ma.m, end = Ma.l, bottom = Ma.s),
            )
        }
        if (expanded && single == null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                prompt.choices.forEach { choice ->
                    LoginChoiceRow(choice) { onSelect(choice) }
                }
            }
        }
    }
}

@Composable
private fun LoginChoiceRow(choice: LoginChoice, onClick: () -> Unit) {
    val colors = Zeno.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Ma.touch)
            .clickable(onClick = onClick)
            .padding(start = Ma.l + 20.dp + Ma.m, end = Ma.l, top = Ma.xs, bottom = Ma.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (choice.generated) stringResource(R.string.login_bar_generated) else choice.login.username.ifBlank { stringResource(R.string.login_no_username) },
                style = Zeno.type.body,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(UrlDisplay.shortHost(choice.login.origin), style = Zeno.type.bodySmall, color = colors.inkSoft, maxLines = 1)
        }
    }
}

/** A password as it is shown before being revealed. */
internal const val MASK = "••••••••"
