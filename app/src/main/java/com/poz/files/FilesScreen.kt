package com.poz.files

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

/** Всё, что показывает левое меню. Собирает MainActivity. */
data class SidebarData(
    val storages: List<Pair<Source.Local, Usage>> = emptyList(),
    val dlna: List<DlnaServer> = emptyList(),
    val dlnaSearching: Boolean = false,
    val smb: List<SmbServer> = emptyList(),
    val ram: Usage = Usage(0, 0),
    val ramMessage: String? = null,
    val freeing: Boolean = false,
)

/** Что экран просит сделать у MainActivity. */
interface FilesActions {
    fun selectSource(source: Source)
    fun open(entry: Entry)
    fun addSmb()
    fun editSmb(server: SmbServer)
    fun refreshDlna()
    fun freeRam()
    fun exit()
}

/**
 * Главный экран: слева накопители, сеть и память, справа — файлы.
 * Назад: на уровень вверх → в меню слева → выход.
 */
@Composable
fun FilesScreen(browser: Browser, sidebar: SidebarData, actions: FilesActions) {
    val sidebarTarget = remember { FocusRequester() }
    var listHasFocus by remember { mutableStateOf(false) }

    BackHandler {
        when {
            browser.up() -> Unit
            listHasFocus -> runCatching { sidebarTarget.requestFocus() }
            else -> actions.exit()
        }
    }

    Row(
        Modifier.fillMaxSize().background(Look.ScreenBackground).padding(horizontal = 36.dp, vertical = 32.dp)
    ) {
        Sidebar(
            data = sidebar,
            selected = browser.source,
            focusTarget = sidebarTarget,
            actions = actions,
            modifier = Modifier.width(340.dp).fillMaxHeight(),
        )
        FileList(
            browser = browser,
            actions = actions,
            onEmpty = { runCatching { sidebarTarget.requestFocus() } },
            modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 28.dp)
                .onFocusChanged { listHasFocus = it.hasFocus },
        )
    }

    // При запуске фокус — на первый пункт меню
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { sidebarTarget.requestFocus() }
    }
}

// ================= Меню слева =================

@Composable
private fun Sidebar(
    data: SidebarData,
    selected: Source?,
    focusTarget: FocusRequester,
    actions: FilesActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    // Фокус при возврате в меню — на выбранный источник, а если ничего не выбрано — на первый
    val focusKey = selected?.key ?: data.storages.firstOrNull()?.first?.key
    fun target(key: String) = if (key == focusKey) Modifier.focusRequester(focusTarget) else Modifier

    Column(
        modifier.clip(Look.PanelShape).background(Look.Panel)
            .border(1.dp, Look.PanelLine, Look.PanelShape).padding(12.dp)
    ) {
        Text(
            "PozFiles",
            modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 2.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )

        LazyColumn(Modifier.weight(1f)) {
            item { SectionTitle(stringResource(R.string.section_storage)) }
            items(data.storages, key = { it.first.key }) { (source, usage) ->
                SourceRow(
                    icon = if (source.removable) Icons.Rounded.Usb else Icons.Rounded.SdStorage,
                    title = source.title,
                    usage = usage,
                    subtitle = stringResource(R.string.used_of, size(usage.used), size(usage.total)),
                    selected = selected?.key == source.key,
                    onClick = { actions.selectSource(source) },
                    modifier = target(source.key),
                )
            }

            item { SectionTitle(stringResource(R.string.section_network)) }
            items(data.dlna, key = { "dlna:" + it.location }) { server ->
                val source = Source.Dlna(server)
                SourceRow(
                    icon = Icons.Rounded.Dns,
                    iconTint = Look.Audio,
                    title = server.name,
                    subtitle = "DLNA",
                    selected = selected?.key == source.key,
                    onClick = { actions.selectSource(source) },
                    modifier = target(source.key),
                )
            }
            items(data.smb, key = { "smb:" + it.id }) { server ->
                val source = Source.Smb(server)
                SourceRow(
                    icon = Icons.Rounded.FolderShared,
                    iconTint = Look.Image,
                    title = server.title,
                    subtitle = "SMB · " + server.host,
                    selected = selected?.key == source.key,
                    onClick = { actions.selectSource(source) },
                    // Долгое нажатие OK — изменить или удалить сервер
                    onLongClick = { actions.editSmb(server) },
                    modifier = target(source.key),
                )
            }
            item(key = "dlna-refresh") {
                SourceRow(
                    icon = Icons.Rounded.Refresh,
                    iconTint = Look.TextDim,
                    title = stringResource(if (data.dlnaSearching) R.string.dlna_searching else R.string.refresh),
                    subtitle = if (!data.dlnaSearching && data.dlna.isEmpty()) stringResource(R.string.dlna_none) else "DLNA",
                    onClick = actions::refreshDlna,
                )
            }
            item(key = "smb-add") {
                SourceRow(
                    icon = Icons.Rounded.Add,
                    iconTint = Look.TextDim,
                    title = stringResource(R.string.add_smb),
                    onClick = actions::addSmb,
                )
            }
        }

        RamCard(data, actions::freeRam, ::size)
    }
}

/** Оперативная память: полоска, цифры и кнопка «Освободить». */
@Composable
private fun RamCard(data: SidebarData, onFree: () -> Unit, size: (Long) -> String) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Memory, null, Modifier.size(22.dp), tint = Look.TextDim)
            Text(stringResource(R.string.ram), Modifier.padding(start = 10.dp), fontSize = 15.sp)
        }
        UsageBar(data.ram, Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                data.ramMessage ?: stringResource(R.string.used_of, size(data.ram.used), size(data.ram.total)),
                modifier = Modifier.weight(1f),
                color = Look.TextDim,
                fontSize = 13.sp,
            )
            Button(
                onClick = onFree,
                enabled = !data.freeing,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                colors = ButtonDefaults.colors(
                    containerColor = Look.RowFocus,
                    contentColor = Look.Text,
                    focusedContainerColor = Look.Accent,
                    focusedContentColor = androidx.compose.ui.graphics.Color.White,
                ),
            ) {
                Text(if (data.freeing) "…" else stringResource(R.string.free_ram), fontSize = 14.sp)
            }
        }
    }
}

// ================= Список файлов =================

@Composable
private fun FileList(
    browser: Browser,
    actions: FilesActions,
    onEmpty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // Путь: «Внутренняя память › Movies › 2024»
        Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 14.dp)) {
            browser.path.forEachIndexed { i, crumb ->
                val last = i == browser.path.lastIndex
                Text(
                    (if (i > 0) "  ›  " else "") + crumb.title,
                    color = if (last) Look.Text else Look.TextDim,
                    fontSize = if (last) 22.sp else 18.sp,
                    fontWeight = if (last) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Bottom),
                )
            }
        }

        when (val listing = browser.listing) {
            Listing.Idle -> Message(stringResource(R.string.pick_source))
            Listing.Loading -> Message(stringResource(R.string.loading))
            is Listing.Error -> {
                Message(stringResource(R.string.error_open, listing.message))
                LaunchedEffect(listing) { onEmpty() }
            }
            is Listing.Ready -> if (listing.entries.isEmpty()) {
                Message(stringResource(R.string.empty_folder))
                LaunchedEffect(listing) { onEmpty() }
            } else {
                Entries(listing.entries, browser.focusLocation, actions)
            }
        }
    }
}

@Composable
private fun Entries(entries: List<Entry>, focusLocation: String?, actions: FilesActions) {
    val context = LocalContext.current
    val state = rememberLazyListState()
    val target = remember { FocusRequester() }
    // Куда поставить фокус: на папку, из которой вышли, иначе на первую строку
    val targetIndex = entries.indexOfFirst { it.location == focusLocation }.coerceAtLeast(0)

    LaunchedEffect(entries) {
        state.scrollToItem((targetIndex - 3).coerceAtLeast(0))
        withFrameNanos { }
        runCatching { target.requestFocus() }
    }

    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(entries, key = { _, e -> e.location }) { index, entry ->
            FocusRow(
                onClick = { actions.open(entry) },
                modifier = if (index == targetIndex) Modifier.focusRequester(target) else Modifier,
            ) {
                val (icon, tint) = entry.kind.icon()
                Icon(icon, null, Modifier.size(28.dp), tint = tint)
                Text(
                    entry.name,
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    entry.size.takeIf { it >= 0 }?.let { Formatter.formatShortFileSize(context, it) },
                    entry.modified.takeIf { it > 0 }?.let { DateFormat.getDateFormat(context).format(it) },
                ).joinToString("   ")
                if (meta.isNotEmpty()) {
                    Text(meta, Modifier.padding(start = 16.dp), color = Look.TextDim, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, color = Look.TextDim, fontSize = 18.sp)
    }
}

/** Значок и цвет по типу файла. */
private fun Kind.icon(): Pair<ImageVector, androidx.compose.ui.graphics.Color> = when (this) {
    Kind.Folder -> Icons.Rounded.Folder to Look.Folder
    Kind.Video -> Icons.Rounded.Movie to Look.Video
    Kind.Audio -> Icons.Rounded.MusicNote to Look.Audio
    Kind.Image -> Icons.Rounded.Image to Look.Image
    Kind.Apk -> Icons.Rounded.Android to Look.Apk
    Kind.Other -> Icons.Rounded.Description to Look.Other
}
