package com.abobi.video.downloader.ui.page.download

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abobi.video.downloader.R
import com.abobi.video.downloader.ui.component.PlaylistItem
import com.abobi.video.downloader.ui.component.SegmentedButtonValues
import com.abobi.video.downloader.ui.component.SingleChoiceSegmentedButton
import com.abobi.video.downloader.util.CollectionInvestigationResult
import com.abobi.video.downloader.util.DownloadUtil
import com.abobi.video.downloader.util.PlaylistResult
import com.abobi.video.downloader.util.SelectableCollectionEntry
import com.abobi.video.downloader.util.UrlLinkDetector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionSelectionContent(
    investigation: CollectionInvestigationResult?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onDownloadSelected: (extractAudio: Boolean, indexList: List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLoading) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.investigating_collection),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val result = investigation ?: return
    val playlistInfo = result.playlist
    val entries = result.entries

    val selectedItems = rememberSaveable(
        saver = listSaver<MutableList<Int>, Int>(
            save = { if (it.isNotEmpty()) it.toList() else emptyList() },
            restore = { it.toMutableStateList() },
        ),
        key = result.sourceUrl,
    ) { mutableStateListOf<Int>() }

    var extractAudio by rememberSaveable { mutableStateOf(false) }
    var showRangeDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val downloadableIndices = remember(entries) {
        entries.filter { it.isDownloadable }.map { it.index }
    }
    val allDownloadableSelected = downloadableIndices.isNotEmpty() &&
        selectedItems.containsAll(downloadableIndices)

    val pageTitle = when (result.kind) {
        UrlLinkDetector.LinkKind.PROFILE -> stringResource(R.string.download_profile)
        UrlLinkDetector.LinkKind.PLAYLIST -> stringResource(R.string.download_playlist)
        UrlLinkDetector.LinkKind.SINGLE_VIDEO -> stringResource(R.string.download_playlist)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedItems.isEmpty()) {
                            pageTitle
                        } else {
                            stringResource(R.string.selected_item_count).format(selectedItems.size)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.close))
                    }
                },
                actions = {
                    TextButton(
                        modifier = Modifier.padding(end = 8.dp),
                        onClick = {
                            onDownloadSelected(extractAudio, selectedItems.sorted())
                            onDismiss()
                        },
                        enabled = selectedItems.isNotEmpty(),
                    ) {
                        Text(text = stringResource(R.string.start_download))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .navigationBarsPadding(),
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SingleChoiceSegmentedButton(
                        text = stringResource(R.string.video),
                        selected = !extractAudio,
                        position = SegmentedButtonValues.START,
                    ) { extractAudio = false }
                    SingleChoiceSegmentedButton(
                        text = stringResource(R.string.audio),
                        selected = extractAudio,
                        position = SegmentedButtonValues.END,
                    ) { extractAudio = true }
                }
                Divider(modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.selectable(
                            selected = allDownloadableSelected,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {
                                if (allDownloadableSelected) {
                                    selectedItems.clear()
                                } else {
                                    selectedItems.clear()
                                    selectedItems.addAll(downloadableIndices)
                                }
                            },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            modifier = Modifier.padding(16.dp),
                            checked = allDownloadableSelected,
                            onCheckedChange = null,
                        )
                        Text(
                            text = stringResource(R.string.select_all_downloadable),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        modifier = Modifier.padding(end = 4.dp),
                        onClick = { showRangeDialog = true },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlaylistAdd,
                            contentDescription = stringResource(R.string.download_range_selection),
                        )
                    }
                }
            }
        },
    ) { paddings ->
        LazyColumn(modifier = Modifier.padding(paddings)) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = collectionDescription(result),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(
                            R.string.downloadable_count,
                            result.downloadableCount,
                            result.totalCount,
                        ),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            items(entries, key = { it.index }) { item ->
                CollectionEntryRow(
                    item = item,
                    playlistInfo = playlistInfo,
                    selected = selectedItems.contains(item.index),
                    onToggle = {
                        if (!item.isDownloadable) return@CollectionEntryRow
                        if (selectedItems.contains(item.index)) {
                            selectedItems.remove(item.index)
                        } else {
                            selectedItems.add(item.index)
                        }
                    },
                )
            }
        }
    }

    if (showRangeDialog) {
        PlaylistSelectionDialog(
            playlistInfo = playlistInfo,
            onDismissRequest = { showRangeDialog = false },
            onConfirm = { range ->
                selectedItems.clear()
                selectedItems.addAll(
                    range.filter { index ->
                        entries.any { it.index == index && it.isDownloadable }
                    },
                )
            },
        )
    }
}

@Composable
private fun collectionDescription(result: CollectionInvestigationResult): String =
    when (result.kind) {
        UrlLinkDetector.LinkKind.PROFILE ->
            stringResource(R.string.profile_selection_desc).format(result.title.orEmpty())
        UrlLinkDetector.LinkKind.PLAYLIST ->
            stringResource(R.string.download_selection_desc).format(result.title.orEmpty())
        UrlLinkDetector.LinkKind.SINGLE_VIDEO ->
            stringResource(R.string.download_selection_desc).format(result.title.orEmpty())
    }

@Composable
private fun CollectionEntryRow(
    item: SelectableCollectionEntry,
    playlistInfo: PlaylistResult,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val entry = item.entry
    val index = item.index
    TooltipBox(
        state = rememberTooltipState(),
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(
                    text = if (item.isDownloadable) {
                        entry.title ?: index.toString()
                    } else {
                        stringResource(R.string.not_downloadable)
                    },
                )
            }
        },
    ) {
        PlaylistItem(
            modifier = Modifier.padding(horizontal = 12.dp),
            imageModel = entry.thumbnails?.lastOrNull()?.url ?: "",
            title = entry.title ?: index.toString(),
            author = entry.channel ?: entry.uploader ?: playlistInfo.channel
                ?: playlistInfo.uploader,
            subtitle = if (!item.isDownloadable) {
                stringResource(R.string.not_downloadable)
            } else {
                null
            },
            enabled = item.isDownloadable,
            selected = selected,
            onClick = onToggle,
        )
    }
}

fun startCollectionDownload(
    investigation: CollectionInvestigationResult,
    extractAudio: Boolean,
    indexList: List<Int>,
) {
    val playlistUrl = investigation.playlist.originalUrl
        ?: investigation.playlist.webpageUrl?.toString().orEmpty()
        .ifEmpty { investigation.sourceUrl }
    val preferences = DownloadUtil.DownloadPreferences.forQuickDownload(extractAudio).copy(
        downloadPlaylist = true,
    )
    val selectedEntries = investigation.entries
        .filter { it.index in indexList }
        .map { it.entry }
    com.abobi.video.downloader.Downloader.downloadVideoInPlaylistByIndexList(
        url = playlistUrl,
        indexList = indexList,
        playlistItemList = selectedEntries,
        preferences = preferences,
    )
}
