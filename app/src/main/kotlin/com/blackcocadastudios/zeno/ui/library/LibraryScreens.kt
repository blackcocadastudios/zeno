package com.blackcocadastudios.zeno.ui.library

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blackcocadastudios.zeno.R
import com.blackcocadastudios.zeno.browser.url.UrlDisplay
import com.blackcocadastudios.zeno.data.bookmarks.Bookmark
import com.blackcocadastudios.zeno.data.downloads.DownloadRecord
import com.blackcocadastudios.zeno.data.downloads.DownloadStatus
import com.blackcocadastudios.zeno.data.history.HistoryEntry
import com.blackcocadastudios.zeno.design.component.EmptyState
import com.blackcocadastudios.zeno.design.component.ProgressLine
import com.blackcocadastudios.zeno.design.component.SectionLabel
import com.blackcocadastudios.zeno.design.component.ZenoIconButton
import com.blackcocadastudios.zeno.design.icon.ZenoIcons
import com.blackcocadastudios.zeno.design.theme.Ma
import com.blackcocadastudios.zeno.design.theme.Zeno
import com.blackcocadastudios.zeno.design.theme.ZenoShapes
import com.blackcocadastudios.zeno.ui.common.SiteSeal
import com.blackcocadastudios.zeno.ui.common.ZenoDialog
import com.blackcocadastudios.zeno.ui.common.ZenoPage
import java.util.Calendar

enum class HistoryRange { LastHour, TodayAndYesterday, Everything }

@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    now: Long,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: (HistoryRange) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var clearing by remember { mutableStateOf(false) }
    ZenoPage(
        title = stringResource(R.string.history_title),
        onBack = onBack,
        actions = {
            if (entries.isNotEmpty()) ZenoIconButton(ZenoIcons.Delete, stringResource(R.string.history_clear), { clearing = true })
        },
    ) {
        if (entries.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.history_empty),
                body = stringResource(R.string.history_empty_body),
                modifier = Modifier.fillMaxSize(),
            )
            return@ZenoPage
        }
        SearchField(query, { query = it }, stringResource(R.string.history_search))
        val filtered = remember(entries, query) {
            val q = query.trim().lowercase()
            if (q.isEmpty()) entries else entries.filter { q in it.title.lowercase() || q in it.url.lowercase() }
        }
        val groups = remember(filtered, now) { filtered.groupBy { bucket(it.lastVisitedAt, now) } }
        LazyColumn(contentPadding = PaddingValues(bottom = Ma.xl)) {
            HistoryBucket.entries.forEach { bucket ->
                val items = groups[bucket].orEmpty()
                if (items.isNotEmpty()) {
                    item(key = bucket.name) { SectionLabel(stringResource(bucket.label)) }
                    items(items, key = { it.url }) { entry ->
                        LibraryRow(
                            url = entry.url,
                            title = entry.title.ifBlank { UrlDisplay.shortHost(entry.url) },
                            subtitle = UrlDisplay.shortHost(entry.url) + " · " +
                                DateUtils.getRelativeTimeSpanString(entry.lastVisitedAt, now, DateUtils.MINUTE_IN_MILLIS),
                            onClick = { onOpen(entry.url) },
                            trailing = {
                                ZenoIconButton(ZenoIcons.Close, stringResource(R.string.history_delete), { onDelete(entry.url) }, tint = Zeno.colors.inkFaint)
                            },
                        )
                    }
                }
            }
        }
    }
    if (clearing) {
        ClearHistoryDialog(onClear = { range ->
            clearing = false
            onClear(range)
        }, onDismiss = { clearing = false })
    }
}

private enum class HistoryBucket(val label: Int) {
    Today(R.string.history_today),
    Yesterday(R.string.history_yesterday),
    ThisWeek(R.string.history_this_week),
    Older(R.string.history_older),
}

private fun bucket(time: Long, now: Long): HistoryBucket {
    val startOfToday = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val day = DateUtils.DAY_IN_MILLIS
    return when {
        time >= startOfToday -> HistoryBucket.Today
        time >= startOfToday - day -> HistoryBucket.Yesterday
        time >= startOfToday - 6 * day -> HistoryBucket.ThisWeek
        else -> HistoryBucket.Older
    }
}

@Composable
private fun ClearHistoryDialog(onClear: (HistoryRange) -> Unit, onDismiss: () -> Unit) {
    ZenoDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.history_clear),
        confirmLabel = stringResource(R.string.prompt_cancel),
        onConfirm = onDismiss,
    ) {
        Column {
            Text(stringResource(R.string.history_clear_body), style = Zeno.type.bodySmall)
            Spacer(Modifier.height(Ma.s))
            listOf(
                HistoryRange.LastHour to R.string.history_clear_hour,
                HistoryRange.TodayAndYesterday to R.string.history_clear_day,
                HistoryRange.Everything to R.string.history_clear_all,
            ).forEach { (range, label) ->
                Text(
                    stringResource(label),
                    style = Zeno.type.body,
                    color = if (range == HistoryRange.Everything) Zeno.colors.seal else Zeno.colors.ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ZenoShapes.small)
                        .clickable { onClear(range) }
                        .padding(vertical = Ma.m, horizontal = Ma.xs),
                )
            }
        }
    }
}

@Composable
fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onTogglePin: (Bookmark) -> Unit,
    onBack: () -> Unit,
) {
    var renaming by remember { mutableStateOf<Bookmark?>(null) }
    ZenoPage(title = stringResource(R.string.bookmarks_title), onBack = onBack) {
        if (bookmarks.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.bookmarks_empty),
                body = stringResource(R.string.bookmarks_empty_body),
                modifier = Modifier.fillMaxSize(),
            )
            return@ZenoPage
        }
        LazyColumn(contentPadding = PaddingValues(vertical = Ma.s)) {
            items(bookmarks, key = { it.id }) { bookmark ->
                LibraryRow(
                    url = bookmark.url,
                    title = bookmark.title,
                    subtitle = UrlDisplay.shortHost(bookmark.url),
                    onClick = { onOpen(bookmark.url) },
                    trailing = {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            ZenoIconButton(ZenoIcons.More, stringResource(R.string.action_more), { menu = true }, tint = Zeno.colors.inkSoft)
                            DropdownMenu(menu, { menu = false }, containerColor = Zeno.colors.paperRaised, shape = ZenoShapes.medium) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.bookmarks_rename), style = Zeno.type.body) },
                                    onClick = {
                                        menu = false
                                        renaming = bookmark
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(if (bookmark.pinned) R.string.bookmarks_unpin else R.string.bookmarks_pin),
                                            style = Zeno.type.body,
                                        )
                                    },
                                    onClick = {
                                        menu = false
                                        onTogglePin(bookmark)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.bookmarks_delete), style = Zeno.type.body, color = Zeno.colors.seal) },
                                    onClick = {
                                        menu = false
                                        onDelete(bookmark.url)
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
    renaming?.let { bookmark ->
        var name by remember(bookmark) { mutableStateOf(bookmark.title) }
        ZenoDialog(
            onDismiss = { renaming = null },
            title = stringResource(R.string.bookmarks_rename),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = {
                onRename(bookmark.url, name)
                renaming = null
            },
            dismissLabel = stringResource(R.string.prompt_cancel),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.bookmarks_name)) },
                shape = ZenoShapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Zeno.colors.accent,
                    unfocusedBorderColor = Zeno.colors.line,
                    cursorColor = Zeno.colors.accent,
                    focusedTextColor = Zeno.colors.ink,
                    unfocusedTextColor = Zeno.colors.ink,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun DownloadsScreen(
    downloads: List<DownloadRecord>,
    now: Long,
    onOpen: (DownloadRecord) -> Unit,
    onDelete: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    ZenoPage(
        title = stringResource(R.string.downloads_title),
        onBack = onBack,
        actions = {
            if (downloads.any { it.status != DownloadStatus.Running }) {
                ZenoIconButton(ZenoIcons.Delete, stringResource(R.string.downloads_clear), onClear)
            }
        },
    ) {
        if (downloads.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.downloads_empty),
                body = stringResource(R.string.downloads_empty_body),
                modifier = Modifier.fillMaxSize(),
            )
            return@ZenoPage
        }
        LazyColumn(contentPadding = PaddingValues(vertical = Ma.s)) {
            items(downloads, key = { it.id }) { download ->
                val status = when (download.status) {
                    DownloadStatus.Running -> download.totalBytes?.let {
                        Formatter.formatShortFileSize(context, download.downloadedBytes) + " / " + Formatter.formatShortFileSize(context, it)
                    } ?: stringResource(R.string.downloads_running)
                    DownloadStatus.Completed -> listOfNotNull(
                        download.totalBytes?.let { Formatter.formatShortFileSize(context, it) },
                        DateUtils.getRelativeTimeSpanString(download.completedAt ?: download.createdAt, now, DateUtils.MINUTE_IN_MILLIS).toString(),
                    ).joinToString(" · ")
                    DownloadStatus.Failed -> stringResource(R.string.downloads_failed)
                    DownloadStatus.Cancelled -> stringResource(R.string.downloads_cancelled)
                }
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = download.status == DownloadStatus.Completed) { onOpen(download) }
                            .padding(horizontal = Ma.xl, vertical = Ma.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(ZenoShapes.seal)
                                .background(Zeno.colors.paperSunken),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(ZenoIcons.File, contentDescription = null, tint = Zeno.colors.inkSoft, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(Ma.l))
                        Column(Modifier.weight(1f)) {
                            Text(download.fileName, style = Zeno.type.body, color = Zeno.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                status,
                                style = Zeno.type.caption,
                                color = if (download.status == DownloadStatus.Failed) Zeno.colors.seal else Zeno.colors.inkSoft,
                            )
                        }
                        if (download.status == DownloadStatus.Running) {
                            ZenoIconButton(ZenoIcons.Close, stringResource(R.string.download_cancel), { onCancel(download.id) }, tint = Zeno.colors.inkSoft)
                        } else {
                            ZenoIconButton(ZenoIcons.Close, stringResource(R.string.downloads_delete), { onDelete(download.id) }, tint = Zeno.colors.inkFaint)
                        }
                    }
                    if (download.status == DownloadStatus.Running) {
                        ProgressLine(
                            progress = download.progress ?: 0f,
                            visible = true,
                            modifier = Modifier.padding(start = 80.dp, end = Ma.xl),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    url: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Ma.xl, end = Ma.xs, top = Ma.xs, bottom = Ma.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteSeal(url, size = 36.dp)
        Spacer(Modifier.width(Ma.l))
        Column(Modifier.weight(1f)) {
            Text(title, style = Zeno.type.body, color = Zeno.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Zeno.type.caption, color = Zeno.colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Ma.xl, vertical = Ma.s)
            .height(44.dp)
            .clip(ZenoShapes.pill)
            .background(Zeno.colors.paperSunken)
            .padding(horizontal = Ma.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ZenoIcons.Search, contentDescription = null, tint = Zeno.colors.inkSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Ma.s))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, style = Zeno.type.body, color = Zeno.colors.inkFaint)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = Zeno.type.body.copy(color = Zeno.colors.ink),
                cursorBrush = SolidColor(Zeno.colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
