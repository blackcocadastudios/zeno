package com.blackcocadastudios.zeno.ui.logins

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.browser.url.UrlDisplay
import com.blackcocadastudios.zeno.data.logins.SavedLogin
import com.blackcocadastudios.zeno.data.settings.UserSettings
import com.blackcocadastudios.zeno.design.component.EmptyState
import com.blackcocadastudios.zeno.design.component.SectionLabel
import com.blackcocadastudios.zeno.design.component.ZenoIconButton
import com.blackcocadastudios.zeno.design.component.ZenoListRow
import com.blackcocadastudios.zeno.design.component.ZenoPillButton
import com.blackcocadastudios.zeno.design.component.ZenoSwitchRow
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes
import com.blackcocadastudios.zeno.ui.common.SiteSeal
import com.blackcocadastudios.zeno.ui.common.ZenoDialog
import com.blackcocadastudios.zeno.ui.common.ZenoPage
import com.blackcocadastudios.zeno.ui.menu.SheetHandle
import kotlinx.coroutines.launch

/** What the passwords screen asks of the app. */
interface PasswordsActions {
    fun update(transform: (UserSettings) -> UserSettings)

    /** Decrypts a saved password, only when the user wants to see or copy it. */
    suspend fun password(guid: String): String?

    fun copy(text: String, isPassword: Boolean)
    fun change(guid: String, username: String, password: String)
    fun delete(login: SavedLogin)
    fun allowSaving(origin: String)
    fun openSite(origin: String)
    fun back()
}

/**
 * Saved logins, behind the screen lock. [logins] is null while they load.
 * [deviceSecure] is false when the phone has no screen lock to ask for.
 */
@Composable
fun PasswordsScreen(
    settings: UserSettings,
    logins: List<SavedLogin>?,
    neverSave: List<String>,
    deviceSecure: Boolean,
    actions: PasswordsActions,
) {
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<SavedLogin?>(null) }
    val shown = remember(logins, query) {
        val q = query.trim()
        logins.orEmpty().filter { q.isEmpty() || it.origin.contains(q, ignoreCase = true) || it.username.contains(q, ignoreCase = true) }
    }

    ZenoPage(title = stringResource(R.string.settings_passwords), onBack = actions::back) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item("settings") {
                Column {
                    ZenoSwitchRow(
                        title = stringResource(R.string.passwords_ask_to_save),
                        subtitle = stringResource(R.string.passwords_ask_to_save_desc),
                        icon = ZenoIcons.BookmarkAdd,
                        checked = settings.askToSavePasswords,
                        onCheckedChange = { on -> actions.update { it.copy(askToSavePasswords = on) } },
                    )
                    ZenoSwitchRow(
                        title = stringResource(R.string.passwords_autofill),
                        subtitle = stringResource(R.string.passwords_autofill_desc),
                        icon = ZenoIcons.Password,
                        checked = settings.autofillPasswords,
                        onCheckedChange = { on -> actions.update { it.copy(autofillPasswords = on) } },
                    )
                    if (!deviceSecure) {
                        Text(
                            stringResource(R.string.passwords_no_lock),
                            style = Zeno.type.bodySmall,
                            color = Zeno.colors.seal,
                            modifier = Modifier.padding(horizontal = Ma.xl, vertical = Ma.s),
                        )
                    }
                }
            }
            if (logins != null && logins.isEmpty()) {
                item("empty") {
                    EmptyState(
                        title = stringResource(R.string.passwords_empty),
                        body = stringResource(R.string.passwords_empty_body),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (!logins.isNullOrEmpty()) {
                item("saved") { SectionLabel(stringResource(R.string.passwords_saved)) }
                if (logins.size > SEARCH_FROM) {
                    item("search") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.passwords_search)) },
                            singleLine = true,
                            shape = ZenoShapes.pill,
                            colors = fieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Ma.l, vertical = Ma.xs),
                        )
                    }
                }
                items(shown, key = { it.guid }) { login ->
                    ZenoListRow(
                        title = UrlDisplay.shortHost(login.origin),
                        subtitle = login.username.ifBlank { stringResource(R.string.login_no_username) },
                        leading = { SiteSeal(login.origin, size = 36.dp) },
                        onClick = { open = login },
                    )
                }
            }
            if (neverSave.isNotEmpty()) {
                item("never") { SectionLabel(stringResource(R.string.passwords_never)) }
                items(neverSave, key = { "never $it" }) { origin ->
                    val site = UrlDisplay.shortHost(origin)
                    ZenoListRow(
                        title = site,
                        leading = { SiteSeal(origin, size = 36.dp) },
                        trailing = {
                            ZenoIconButton(
                                ZenoIcons.Close,
                                stringResource(R.string.passwords_allow_saving, site),
                                { actions.allowSaving(origin) },
                                tint = Zeno.colors.inkSoft,
                            )
                        },
                    )
                }
            }
            item("end") { Spacer(Modifier.height(Ma.xxl)) }
        }
    }

    open?.let { login ->
        LoginSheet(login, actions, onDismiss = { open = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginSheet(login: SavedLogin, actions: PasswordsActions, onDismiss: () -> Unit) {
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
        LoginDetails(login, actions, onDone = onDismiss)
    }
}

/** One login: copy, show, change or delete. Separate from the sheet so it can be rendered in tests. */
@Composable
fun LoginDetails(login: SavedLogin, actions: PasswordsActions, onDone: () -> Unit, initialPassword: String? = null) {
    val colors = Zeno.colors
    val scope = rememberCoroutineScope()
    val site = UrlDisplay.shortHost(login.origin)
    var password by remember(login.guid) { mutableStateOf(initialPassword) }
    var editing by remember(login.guid) { mutableStateOf(false) }
    var confirmDelete by remember(login.guid) { mutableStateOf(false) }

    fun withPassword(action: (String) -> Unit) {
        password?.let(action) ?: scope.launch { actions.password(login.guid)?.also { password = it }?.let(action) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Ma.xl),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Ma.xl, vertical = Ma.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SiteSeal(login.origin, size = 44.dp)
            Spacer(Modifier.width(Ma.l))
            Text(site, style = Zeno.type.heading, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            ZenoIconButton(ZenoIcons.OpenExternal, stringResource(R.string.login_open_site), { actions.openSite(login.origin) }, tint = colors.inkSoft)
        }
        if (editing) {
            EditLogin(
                username = login.username,
                password = password.orEmpty(),
                onSave = { user, pass ->
                    actions.change(login.guid, user, pass)
                    password = pass
                    editing = false
                },
                onCancel = { editing = false },
            )
        } else {
            ShowLogin(login, password, actions, withPassword = ::withPassword, onEdit = { withPassword { editing = true } }, onDelete = { confirmDelete = true })
        }
    }

    if (confirmDelete) {
        ZenoDialog(
            onDismiss = { confirmDelete = false },
            title = stringResource(R.string.login_delete_title, site),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                actions.delete(login)
                onDone()
            },
            dismissLabel = stringResource(R.string.prompt_cancel),
            destructive = true,
        ) { Text(stringResource(R.string.login_delete_body), style = Zeno.type.bodySmall) }
    }
}

@Composable
private fun ShowLogin(
    login: SavedLogin,
    password: String?,
    actions: PasswordsActions,
    withPassword: ((String) -> Unit) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Zeno.colors
    var visible by remember(login.guid) { mutableStateOf(false) }
    ZenoListRow(
        title = login.username.ifBlank { stringResource(R.string.login_no_username) },
        subtitle = stringResource(R.string.prompt_username),
        icon = ZenoIcons.User,
        trailing = {
            ZenoIconButton(ZenoIcons.Copy, stringResource(R.string.login_copy_username), { actions.copy(login.username, isPassword = false) }, tint = colors.inkSoft)
        },
    )
    ZenoListRow(
        title = if (visible && password != null) password else MASK,
        subtitle = stringResource(R.string.prompt_password),
        icon = ZenoIcons.Password,
        trailing = {
            Row {
                ZenoIconButton(
                    if (visible) ZenoIcons.EyeOff else ZenoIcons.Eye,
                    stringResource(if (visible) R.string.login_hide_password else R.string.login_show_password),
                    { if (visible) visible = false else withPassword { visible = true } },
                    tint = colors.inkSoft,
                )
                ZenoIconButton(
                    ZenoIcons.Copy,
                    stringResource(R.string.login_copy_password),
                    { withPassword { actions.copy(it, isPassword = true) } },
                    tint = colors.inkSoft,
                )
            }
        },
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Ma.xl, vertical = Ma.m),
    ) {
        ZenoPillButton(stringResource(R.string.login_edit), onEdit)
        Spacer(Modifier.width(Ma.s))
        ZenoPillButton(stringResource(R.string.login_delete), onDelete)
    }
}

@Composable
private fun EditLogin(username: String, password: String, onSave: (String, String) -> Unit, onCancel: () -> Unit) {
    var user by remember { mutableStateOf(username) }
    var pass by remember { mutableStateOf(password) }
    var showPass by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = Ma.xl, vertical = Ma.s)) {
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text(stringResource(R.string.prompt_username)) },
            singleLine = true,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Ma.s))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text(stringResource(R.string.prompt_password)) },
            singleLine = true,
            isError = pass.isEmpty(),
            supportingText = if (pass.isEmpty()) {
                { Text(stringResource(R.string.login_password_empty)) }
            } else {
                null
            },
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                ZenoIconButton(
                    if (showPass) ZenoIcons.EyeOff else ZenoIcons.Eye,
                    stringResource(if (showPass) R.string.login_hide_password else R.string.login_show_password),
                    { showPass = !showPass },
                    tint = Zeno.colors.inkSoft,
                )
            },
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.padding(top = Ma.m)) {
            ZenoPillButton(stringResource(R.string.action_save), { onSave(user.trim(), pass) }, filled = true, enabled = pass.isNotEmpty())
            Spacer(Modifier.width(Ma.s))
            ZenoPillButton(stringResource(R.string.prompt_cancel), onCancel)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Zeno.colors.accent,
    unfocusedBorderColor = Zeno.colors.line,
    cursorColor = Zeno.colors.accent,
    focusedTextColor = Zeno.colors.ink,
    unfocusedTextColor = Zeno.colors.ink,
    focusedLabelColor = Zeno.colors.accentInk,
)

/** Above this many logins a search field helps. */
private const val SEARCH_FROM = 8
