package com.blackcocadastudios.zeno.ui.prompts

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.design.component.Hairline
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes
import com.blackcocadastudios.zeno.engine.ChoiceItem
import com.blackcocadastudios.zeno.engine.ChoiceMode
import com.blackcocadastudios.zeno.engine.DateTimeKind
import com.blackcocadastudios.zeno.engine.PromptRequest
import com.blackcocadastudios.zeno.engine.ShareResult
import com.blackcocadastudios.zeno.ui.common.RadioMark
import com.blackcocadastudios.zeno.ui.common.ZenoDialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import androidx.core.graphics.toColorInt

/**
 * Shows the page's question (alert, confirm, select, file picker…) and sends the
 * answer back. [onDone] removes the prompt from the tab once answered.
 */
@Composable
fun PromptHost(prompt: PromptRequest, origin: String, onDone: (PromptRequest) -> Unit) {
    fun finish(action: () -> Unit) {
        action()
        onDone(prompt)
    }
    when (prompt) {
        is PromptRequest.Alert -> ZenoDialog(
            onDismiss = { finish(prompt::dismiss) },
            title = prompt.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.prompt_page_says, origin),
            confirmLabel = stringResource(R.string.prompt_ok),
            onConfirm = { finish(prompt::confirm) },
        ) { ScrollableText(prompt.message) }

        is PromptRequest.Confirm -> ZenoDialog(
            onDismiss = { finish(prompt::dismiss) },
            title = prompt.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.prompt_page_says, origin),
            confirmLabel = stringResource(R.string.prompt_ok),
            onConfirm = { finish { prompt.confirm(true) } },
            dismissLabel = stringResource(R.string.prompt_cancel),
            onDismissClick = { finish { prompt.confirm(false) } },
        ) { ScrollableText(prompt.message) }

        is PromptRequest.TextInput -> TextInputDialog(prompt, origin, ::finish)
        is PromptRequest.Auth -> AuthDialog(prompt, ::finish)
        is PromptRequest.Choice -> ChoiceDialog(prompt, ::finish)
        is PromptRequest.Color -> ColorDialog(prompt, ::finish)
        is PromptRequest.DateTime -> DateTimeDialog(prompt, ::finish)
        is PromptRequest.File -> FilePicker(prompt, ::finish)
        is PromptRequest.Share -> ShareLauncher(prompt, ::finish)

        // The browser shows these itself: a save offer with its own rules, a bar over the keyboard.
        is PromptRequest.LoginSave, is PromptRequest.LoginSelect -> Unit

        is PromptRequest.Popup -> ZenoDialog(
            onDismiss = { finish { prompt.confirm(false) } },
            title = stringResource(R.string.prompt_popup_title),
            confirmLabel = stringResource(R.string.prompt_allow),
            onConfirm = { finish { prompt.confirm(true) } },
            dismissLabel = stringResource(R.string.prompt_block),
            onDismissClick = { finish { prompt.confirm(false) } },
        ) {
            Text(
                prompt.targetUrl?.let { stringResource(R.string.prompt_popup_body, it) }
                    ?: stringResource(R.string.prompt_popup_body_unknown),
                style = Zeno.type.bodySmall,
            )
        }

        is PromptRequest.BeforeUnload -> ZenoDialog(
            onDismiss = { finish { prompt.confirm(false) } },
            title = stringResource(R.string.prompt_leave_title),
            confirmLabel = stringResource(R.string.prompt_leave),
            onConfirm = { finish { prompt.confirm(true) } },
            dismissLabel = stringResource(R.string.prompt_stay),
            onDismissClick = { finish { prompt.confirm(false) } },
        ) { Text(stringResource(R.string.prompt_leave_body), style = Zeno.type.bodySmall) }

        is PromptRequest.Repost -> ZenoDialog(
            onDismiss = { finish { prompt.confirm(false) } },
            title = stringResource(R.string.prompt_repost_title),
            confirmLabel = stringResource(R.string.prompt_resend),
            onConfirm = { finish { prompt.confirm(true) } },
            dismissLabel = stringResource(R.string.prompt_cancel),
            onDismissClick = { finish { prompt.confirm(false) } },
        ) { Text(stringResource(R.string.prompt_repost_body), style = Zeno.type.bodySmall) }
    }
}

@Composable
private fun ScrollableText(text: String) {
    Text(
        text,
        style = Zeno.type.body,
        color = Zeno.colors.ink,
        modifier = Modifier
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
    )
}

@Composable
private fun zenoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Zeno.colors.accent,
    unfocusedBorderColor = Zeno.colors.line,
    cursorColor = Zeno.colors.accent,
    focusedTextColor = Zeno.colors.ink,
    unfocusedTextColor = Zeno.colors.ink,
    focusedLabelColor = Zeno.colors.accentInk,
    unfocusedLabelColor = Zeno.colors.inkSoft,
)

@Composable
private fun TextInputDialog(prompt: PromptRequest.TextInput, origin: String, finish: (() -> Unit) -> Unit) {
    var text by remember { mutableStateOf(prompt.defaultValue) }
    ZenoDialog(
        onDismiss = { finish(prompt::dismiss) },
        title = prompt.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.prompt_page_says, origin),
        confirmLabel = stringResource(R.string.prompt_ok),
        onConfirm = { finish { prompt.confirm(text) } },
        dismissLabel = stringResource(R.string.prompt_cancel),
        onDismissClick = { finish(prompt::dismiss) },
    ) {
        Column {
            prompt.message?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = Zeno.type.bodySmall)
                Spacer(Modifier.size(Ma.m))
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                shape = ZenoShapes.small,
                colors = zenoFieldColors(),
                textStyle = Zeno.type.body,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AuthDialog(prompt: PromptRequest.Auth, finish: (() -> Unit) -> Unit) {
    var user by remember { mutableStateOf(prompt.username) }
    var password by remember { mutableStateOf(prompt.password) }
    ZenoDialog(
        onDismiss = { finish(prompt::dismiss) },
        title = stringResource(R.string.prompt_auth_title, prompt.origin ?: ""),
        confirmLabel = stringResource(R.string.prompt_ok),
        onConfirm = { finish { prompt.confirm(user, password) } },
        dismissLabel = stringResource(R.string.prompt_cancel),
        onDismissClick = { finish(prompt::dismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Ma.m)) {
            if (prompt.previousFailed) Text(stringResource(R.string.prompt_auth_failed), style = Zeno.type.bodySmall, color = Zeno.colors.seal)
            prompt.message?.takeIf { it.isNotBlank() }?.let { Text(it, style = Zeno.type.bodySmall) }
            if (!prompt.onlyPassword) {
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text(stringResource(R.string.prompt_username)) },
                    singleLine = true,
                    shape = ZenoShapes.small,
                    colors = zenoFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.prompt_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = ZenoShapes.small,
                colors = zenoFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ChoiceDialog(prompt: PromptRequest.Choice, finish: (() -> Unit) -> Unit) {
    val multiple = prompt.mode == ChoiceMode.Multiple
    val flat = remember(prompt) { flatten(prompt.items) }
    val selected = remember(prompt) { mutableStateListOf<String>().apply { addAll(flat.filter { it.selected }.map { it.id }) } }
    ZenoDialog(
        onDismiss = { finish(prompt::dismiss) },
        title = prompt.title?.takeIf { it.isNotBlank() },
        confirmLabel = stringResource(if (multiple) R.string.prompt_ok else R.string.prompt_cancel),
        onConfirm = { finish { if (multiple) prompt.confirm(selected.toList()) else prompt.dismiss() } },
    ) {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(flat) { item ->
                when {
                    item.isSeparator -> Hairline(Modifier.padding(vertical = Ma.xs))
                    item.isGroup -> Text(
                        item.label,
                        style = Zeno.type.caption,
                        color = Zeno.colors.inkSoft,
                        modifier = Modifier.padding(top = Ma.m, bottom = Ma.xs),
                    )
                    else -> Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(ZenoShapes.small)
                            .clickable(enabled = item.enabled) {
                                if (multiple) {
                                    if (item.id in selected) selected.remove(item.id) else selected.add(item.id)
                                } else {
                                    finish { prompt.confirm(listOf(item.id)) }
                                }
                            }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = Ma.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (multiple) {
                            Checkbox(
                                checked = item.id in selected,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = Zeno.colors.accent, uncheckedColor = Zeno.colors.inkFaint),
                            )
                        } else if (prompt.mode == ChoiceMode.Single) {
                            RadioMark(item.selected)
                        }
                        Spacer(Modifier.width(Ma.m))
                        Text(
                            item.label,
                            style = Zeno.type.body,
                            color = if (item.enabled) Zeno.colors.ink else Zeno.colors.inkFaint,
                        )
                    }
                }
            }
        }
    }
}

private fun flatten(items: List<ChoiceItem>): List<ChoiceItem> =
    items.flatMap { if (it.isGroup) listOf(it) + flatten(it.children) else listOf(it) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorDialog(prompt: PromptRequest.Color, finish: (() -> Unit) -> Unit) {
    var chosen by remember { mutableStateOf(prompt.defaultColor) }
    val palette = listOf(
        "#000000", "#434343", "#999999", "#FFFFFF", "#C8442E", "#E08A3C", "#E8C547", "#5B8930",
        "#00A3AF", "#2792C3", "#165E83", "#6A4C9C", "#8B81C3", "#D06A8F", "#B4866B", "#FCFAF2",
    )
    ZenoDialog(
        onDismiss = { finish(prompt::dismiss) },
        title = stringResource(R.string.prompt_color_title),
        confirmLabel = stringResource(R.string.prompt_ok),
        onConfirm = { finish { prompt.confirm(chosen) } },
        dismissLabel = stringResource(R.string.prompt_cancel),
        onDismissClick = { finish(prompt::dismiss) },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Ma.s), verticalArrangement = Arrangement.spacedBy(Ma.s)) {
            palette.forEach { hex ->
                val color = Color(hex.toColorInt())
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            if (hex.equals(chosen, ignoreCase = true)) 3.dp else 0.5.dp,
                            if (hex.equals(chosen, ignoreCase = true)) Zeno.colors.accent else Zeno.colors.line,
                            CircleShape,
                        )
                        .clickable { chosen = hex.lowercase() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeDialog(prompt: PromptRequest.DateTime, finish: (() -> Unit) -> Unit) {
    val needsDate = prompt.kind != DateTimeKind.Time
    val needsTime = prompt.kind == DateTimeKind.Time || prompt.kind == DateTimeKind.DateTime
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    if (needsDate && pickedDate == null) {
        val initial = remember(prompt) { parseDate(prompt.kind, prompt.value) }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { finish(prompt::dismiss) },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis == null) {
                        finish(prompt::dismiss)
                    } else {
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (needsTime) pickedDate = date else finish { prompt.confirm(formatDate(prompt.kind, date)) }
                    }
                }) { Text(stringResource(R.string.prompt_ok), color = Zeno.colors.accentInk) }
            },
            dismissButton = {
                TextButton(onClick = { finish { prompt.confirm("") } }) {
                    Text(stringResource(R.string.prompt_clear), color = Zeno.colors.inkSoft)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = Zeno.colors.paperRaised),
        ) {
            DatePicker(
                state = state,
                colors = DatePickerDefaults.colors(
                    containerColor = Zeno.colors.paperRaised,
                    selectedDayContainerColor = Zeno.colors.accent,
                    todayDateBorderColor = Zeno.colors.accent,
                    todayContentColor = Zeno.colors.accentInk,
                ),
            )
        }
        return
    }

    val initialTime = remember(prompt) { parseTime(prompt.value) }
    val timeState = rememberTimePickerState(initialTime.hour, initialTime.minute, is24Hour = true)
    ZenoDialog(
        onDismiss = { finish(prompt::dismiss) },
        title = null,
        confirmLabel = stringResource(R.string.prompt_ok),
        onConfirm = {
            val time = LocalTime.of(timeState.hour, timeState.minute)
            val value = pickedDate?.let { LocalDateTime.of(it, time).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")) }
                ?: time.format(DateTimeFormatter.ofPattern("HH:mm"))
            finish { prompt.confirm(value) }
        },
        dismissLabel = stringResource(R.string.prompt_cancel),
        onDismissClick = { finish(prompt::dismiss) },
    ) { TimePicker(state = timeState) }
}

private fun parseDate(kind: DateTimeKind, value: String?): LocalDate? = runCatching {
    when {
        value.isNullOrBlank() -> null
        kind == DateTimeKind.Month -> YearMonth.parse(value).atDay(1)
        kind == DateTimeKind.Week -> {
            val year = value.substringBefore("-W").toInt()
            val week = value.substringAfter("-W").toLong()
            LocalDate.of(year, 1, 4).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
        }
        else -> LocalDate.parse(value.take(10))
    }
}.getOrNull()

private fun formatDate(kind: DateTimeKind, date: LocalDate): String = when (kind) {
    DateTimeKind.Month -> date.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    DateTimeKind.Week -> "%d-W%02d".format(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
    else -> date.toString()
}

private fun parseTime(value: String?): LocalTime = runCatching {
    LocalTime.parse(value!!.substringAfter('T').take(5))
}.getOrDefault(LocalTime.now())

@Composable
private fun FilePicker(prompt: PromptRequest.File, finish: (() -> Unit) -> Unit) {
    val types = remember(prompt) { prompt.mimeTypes.mapNotNull(::normalizeMime).ifEmpty { listOf("*/*") }.toTypedArray() }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        finish { if (uri == null) prompt.dismiss() else prompt.confirm(listOf(uri.toString())) }
    }
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        finish { if (uris.isEmpty()) prompt.dismiss() else prompt.confirm(uris.map { it.toString() }) }
    }
    LaunchedEffect(prompt) {
        runCatching { if (prompt.multiple) multiple.launch(types) else single.launch(types) }
            .onFailure { finish(prompt::dismiss) }
    }
}

/** Accept attributes can hold extensions (".pdf"); the system picker wants MIME types. */
private fun normalizeMime(value: String): String? {
    val v = value.trim().lowercase()
    return when {
        v.isEmpty() -> null
        v.contains('/') -> v
        v.startsWith('.') -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(v.drop(1))
        else -> null
    }
}

@Composable
private fun ShareLauncher(prompt: PromptRequest.Share, finish: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val title = stringResource(R.string.share_via)
    LaunchedEffect(prompt) {
        val text = listOfNotNull(prompt.text, prompt.url).joinToString("\n").ifBlank { null }
        if (text == null) {
            finish { prompt.finish(ShareResult.Failure) }
            return@LaunchedEffect
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
            .apply { prompt.title?.let { putExtra(Intent.EXTRA_SUBJECT, it) } }
        val result = runCatching { context.startActivity(Intent.createChooser(send, title)) }
        finish { prompt.finish(if (result.isSuccess) ShareResult.Success else ShareResult.Failure) }
    }
}
