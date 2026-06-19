package com.abobi.video.downloader.ui.page.download

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NewLabel
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abobi.video.downloader.R
import com.abobi.video.downloader.ui.common.booleanState
import com.abobi.video.downloader.ui.common.intState
import com.abobi.video.downloader.ui.common.motion.materialSharedAxisYIn
import com.abobi.video.downloader.ui.component.ButtonChip
import com.abobi.video.downloader.ui.component.DismissButton
import com.abobi.video.downloader.ui.component.DrawerSheetSubtitle
import com.abobi.video.downloader.ui.component.FilledButtonWithIcon
import com.abobi.video.downloader.ui.component.OutlinedButtonWithIcon
import com.abobi.video.downloader.ui.component.SealModalBottomSheet
import com.abobi.video.downloader.ui.component.SealModalBottomSheetM2
import com.abobi.video.downloader.ui.component.SegmentedButtonValues
import com.abobi.video.downloader.ui.component.SingleChoiceChip
import com.abobi.video.downloader.ui.component.SingleChoiceSegmentedButton
import com.abobi.video.downloader.ui.component.VideoFilterChip
import com.abobi.video.downloader.ui.page.command.TemplatePickerDialog
import com.abobi.video.downloader.ui.page.settings.command.CommandTemplateDialog
import com.abobi.video.downloader.ui.page.settings.format.AudioConversionQuickSettingsDialog
import com.abobi.video.downloader.ui.page.settings.format.AudioQuickSettingsDialog
import com.abobi.video.downloader.ui.page.settings.format.FormatSortingDialog
import com.abobi.video.downloader.ui.page.settings.format.VideoFormatDialog
import com.abobi.video.downloader.ui.page.settings.network.CookiesQuickSettingsDialog
import com.abobi.video.downloader.util.AUDIO_CONVERSION_FORMAT
import com.abobi.video.downloader.util.AUDIO_CONVERT
import com.abobi.video.downloader.util.CONVERT_M4A
import com.abobi.video.downloader.util.CONVERT_MP3
import com.abobi.video.downloader.util.CookieHelper
import com.abobi.video.downloader.util.CUSTOM_COMMAND
import com.abobi.video.downloader.util.DOWNLOAD_TYPE_INITIALIZATION
import com.abobi.video.downloader.util.DatabaseUtil
import com.abobi.video.downloader.util.DownloadUtil
import com.abobi.video.downloader.util.DownloadUtil.toFormatSorter
import com.abobi.video.downloader.util.EXTRACT_AUDIO
import com.abobi.video.downloader.util.FORMAT_SORTING
import com.abobi.video.downloader.util.FileUtil
import com.abobi.video.downloader.util.FileUtil.getCookiesFile
import com.abobi.video.downloader.util.PLAYLIST
import com.abobi.video.downloader.util.PreferenceStrings
import com.abobi.video.downloader.util.PreferenceUtil
import com.abobi.video.downloader.util.PreferenceUtil.getBoolean
import com.abobi.video.downloader.util.PreferenceUtil.getInt
import com.abobi.video.downloader.util.PreferenceUtil.getString
import com.abobi.video.downloader.util.PreferenceUtil.updateBoolean
import com.abobi.video.downloader.util.PreferenceUtil.updateInt
import com.abobi.video.downloader.util.PreferenceUtil.updateString
import com.abobi.video.downloader.util.SORTING_FIELDS
import com.abobi.video.downloader.util.SUBTITLE
import com.abobi.video.downloader.util.TEMPLATE_ID
import com.abobi.video.downloader.util.USE_PREVIOUS_SELECTION
import com.abobi.video.downloader.util.VIDEO_FORMAT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


private enum class DownloadType {
    Audio, Video, Command, None
}

@OptIn(
    ExperimentalMaterial3Api::class,
)
@Composable
fun DownloadSettingDialog(
    useDialog: Boolean = false,
    showDialog: Boolean = false,
    isQuickDownload: Boolean = false,
    onNavigateToCookieGeneratorPage: (String) -> Unit = {},
    onDownloadConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
//    val audio by remember { mutableStateOf(PreferenceUtil.getValue(EXTRACT_AUDIO)) }

    var playlist by remember { mutableStateOf(PreferenceUtil.getValue(PLAYLIST)) }
    var subtitle by remember { mutableStateOf(PreferenceUtil.getValue(SUBTITLE)) }
    var videoFormatPreference by VIDEO_FORMAT.intState
    var formatSorting by FORMAT_SORTING.booleanState

    var type by remember(showDialog) {
        mutableStateOf(
            when (DOWNLOAD_TYPE_INITIALIZATION.getInt()) {
                USE_PREVIOUS_SELECTION -> {
                    if (CUSTOM_COMMAND.getBoolean()) {
                        DownloadType.Command
                    } else if (EXTRACT_AUDIO.getBoolean()) {
                        DownloadType.Audio
                    } else {
                        DownloadType.Video
                    }
                }

                else -> {
                    DownloadType.None
                }
            }
        )
    }


    var showAudioSettingsDialog by remember { mutableStateOf(false) }
    var showVideoFormatDialog by remember { mutableStateOf(false) }
    var showAudioConversionDialog by remember { mutableStateOf(false) }
    var showFormatSortingDialog by remember { mutableStateOf(false) }

    var sortingFields by remember(showFormatSortingDialog) { mutableStateOf(SORTING_FIELDS.getString()) }

    var showTemplateSelectionDialog by remember { mutableStateOf(false) }
    var showTemplateCreatorDialog by remember { mutableStateOf(false) }
    var showTemplateEditorDialog by remember { mutableStateOf(false) }

    var showCookiesDialog by rememberSaveable { mutableStateOf(false) }

    val cookiesProfiles by DatabaseUtil.getCookiesFlow().collectAsStateWithLifecycle(emptyList())

    val template by remember(
        showTemplateCreatorDialog,
        showTemplateSelectionDialog,
        showTemplateEditorDialog
    ) {
        mutableStateOf(PreferenceUtil.getTemplate())
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(showCookiesDialog) {
        withContext(Dispatchers.IO) {
            DownloadUtil.getCookiesContentFromDatabase().getOrNull()?.let {
                FileUtil.writeContentToFile(it, context.getCookiesFile())
            }
        }
    }

    LaunchedEffect(showDialog) {
        if (showDialog) {

        }
    }

    val updatePreferences = {
        scope.launch {
            PreferenceUtil.updateValue(EXTRACT_AUDIO, type == DownloadType.Audio)
            PreferenceUtil.updateValue(CUSTOM_COMMAND, type == DownloadType.Command)
            PreferenceUtil.updateValue(PLAYLIST, playlist)
            PreferenceUtil.updateValue(SUBTITLE, subtitle)
        }
    }

    val downloadButtonCallback = {
        updatePreferences()
        onDismissRequest()
        onDownloadConfirm()
    }

    val sheetContent: @Composable () -> Unit = {
        Column {
            Text(
                text = stringResource(R.string.settings_before_download_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
//                    .clickable { }
            )
            DrawerSheetSubtitle(text = stringResource(id = R.string.download_type))
            Row(
                modifier = Modifier
//                    .horizontalScroll(rememberScrollState())

            ) {

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SingleChoiceSegmentedButton(
                        text = stringResource(id = R.string.audio),
                        selected = type == DownloadType.Audio,
                        position = SegmentedButtonValues.START
                    ) {
                        type = DownloadType.Audio
                        updatePreferences()
                    }
                    SingleChoiceSegmentedButton(
                        text = stringResource(id = R.string.video),
                        selected = type == DownloadType.Video
                    ) {
                        type = DownloadType.Video
                        updatePreferences()
                    }
                    SingleChoiceSegmentedButton(
                        text = stringResource(id = R.string.commands),
                        selected = type == DownloadType.Command,
                        position = SegmentedButtonValues.END
                    ) {
                        type = DownloadType.Command
                        updatePreferences()
                    }
                }
            }
            DrawerSheetSubtitle(text = stringResource(id = if (type == DownloadType.Command) R.string.template_selection else R.string.format_preference))
            AnimatedContent(targetState = type, label = "", transitionSpec = {
                (materialSharedAxisYIn(initialOffsetX = { it / 4 })).togetherWith(
                    fadeOut(tween(durationMillis = 80))
                )
            }) { type ->
                when (type) {
                    DownloadType.Command -> {
                        LazyRow(
                            modifier = Modifier,
                        ) {
                            item {
                                ButtonChip(
                                    icon = Icons.Outlined.Code,
                                    label = template.name,
                                    onClick = { showTemplateSelectionDialog = true }
                                )
                            }
                            item {
                                ButtonChip(
                                    icon = Icons.Outlined.NewLabel,
                                    label = stringResource(id = R.string.new_template),
                                    onClick = { showTemplateCreatorDialog = true }
                                )
                            }
                            item {
                                ButtonChip(
                                    icon = Icons.Outlined.Edit,
                                    label = stringResource(
                                        id = R.string.edit_template,
                                        template.name
                                    ),
                                    onClick = { showTemplateEditorDialog = true }
                                )
                            }
                        }
                    }

                    else -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            if (type != DownloadType.Audio) {
                                ButtonChip(
                                    onClick = {
                                        showVideoFormatDialog = true
                                    },
                                    enabled = !formatSorting && type != DownloadType.None,
                                    label = PreferenceStrings.getVideoFormatLabel(
                                        videoFormatPreference
                                    ),
                                    icon = Icons.Outlined.VideoFile,
                                    iconDescription = stringResource(id = R.string.video_format_preference)
                                )
                            }
                            ButtonChip(
                                onClick = {
                                    showAudioSettingsDialog = true
                                },
                                enabled = !formatSorting && type != DownloadType.None,
                                label = stringResource(R.string.audio_format),
                                icon = Icons.Outlined.AudioFile
                            )
                            val convertToMp3 = stringResource(id = R.string.convert_to, "mp3")
                            val convertToM4a = stringResource(id = R.string.convert_to, "m4a")
                            val notConvert = stringResource(id = R.string.not_convert)

                            if (type == DownloadType.Audio) {
                                val convertAudioLabelText by remember(
                                    showAudioConversionDialog,
                                    type
                                ) {
                                    derivedStateOf {
                                        if (!AUDIO_CONVERT.getBoolean()) {
                                            notConvert
                                        } else {
                                            val format = AUDIO_CONVERSION_FORMAT.getInt()
                                            when (format) {
                                                CONVERT_MP3 -> convertToMp3
                                                CONVERT_M4A -> convertToM4a
                                                else -> notConvert
                                            }
                                        }
                                    }
                                }
                                ButtonChip(
                                    label = convertAudioLabelText,
                                    icon = Icons.Outlined.Sync,
                                ) {
                                    showAudioConversionDialog = true
                                }
                            }
                        }
                    }
                }

            }

            AnimatedVisibility(visible = type == DownloadType.Command) {


            }

            DrawerSheetSubtitle(
                text = stringResource(
                    R.string.additional_settings
                )
            )

            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                if (cookiesProfiles.isNotEmpty()) {
                    VideoFilterChip(
                        selected = CookieHelper.cookiesFileAvailable(),
                        onClick = { showCookiesDialog = true },
                        label = stringResource(id = R.string.cookies)
                    )
                }
                if (sortingFields.isNotEmpty()) {
                    FilterChip(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        selected = formatSorting,
                        enabled = type != DownloadType.Command,
                        onClick = { showFormatSortingDialog = true },
                        label = {
                            Text(text = stringResource(id = R.string.format_sorting))
                        }
                    )
                }
                if (!isQuickDownload) {
                    VideoFilterChip(
                        selected = playlist, enabled = type != DownloadType.Command, onClick = {
                            playlist = !playlist
                            updatePreferences()
                        }, label = stringResource(R.string.download_playlist)
                    )
                }
                VideoFilterChip(
                    selected = subtitle, enabled = type != DownloadType.Command, onClick = {
                        subtitle = !subtitle
                        updatePreferences()
                    }, label = stringResource(id = R.string.download_subtitles)
                )
            }


        }
    }
    if (showDialog) {

        @Composable
        fun SheetContent(onDismissRequest: () -> Unit) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Icon(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    imageVector = Icons.Outlined.DoneAll,
                    contentDescription = null
                )
                Text(
                    text = stringResource(R.string.settings_before_download),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 16.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                sheetContent()
                val state = rememberLazyListState()
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End,
                    state = state,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        OutlinedButtonWithIcon(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            onClick = onDismissRequest,
                            icon = Icons.Outlined.Cancel,
                            text = stringResource(R.string.cancel)
                        )
                    }
                    item {
                        FilledButtonWithIcon(
                            onClick = downloadButtonCallback,
                            icon = Icons.Outlined.DownloadDone,
                            text = stringResource(R.string.start_download),
                            enabled = type != DownloadType.None
                        )
                    }
                }
            }
        }

        if (!useDialog) {
            val useMD2BottomSheet = Build.VERSION.SDK_INT < 30
            if (useMD2BottomSheet) {
                val sheetState = androidx.compose.material.rememberModalBottomSheetState(
                    initialValue = ModalBottomSheetValue.Hidden,
                    skipHalfExpanded = true
                )

                BackHandler(sheetState.targetValue == ModalBottomSheetValue.Expanded) {
                    scope.launch {
                        sheetState.hide()
                    }
                }

                LaunchedEffect(Unit) {
                    sheetState.show()
                }

                LaunchedEffect(sheetState.isVisible) {
                    if (sheetState.targetValue == ModalBottomSheetValue.Hidden) {
                        onDismissRequest()
                    }
                }

                SealModalBottomSheetM2(
                    sheetState = sheetState,
                    horizontalPadding = PaddingValues(horizontal = 20.dp),
                    sheetContent = {
                        SheetContent(onDismissRequest = {
                            scope.launch {
                                sheetState.hide()
                            }
                        })
                    }
                )
            } else {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val onSheetDismiss: () -> Unit =
                    {
                        scope.launch {
                            sheetState.hide()
                        }.invokeOnCompletion { onDismissRequest() }
                    }


                SealModalBottomSheet(
                    sheetState = sheetState,
                    horizontalPadding = PaddingValues(horizontal = 20.dp),
                    onDismissRequest = onDismissRequest,
                    content = {
                        SheetContent(onDismissRequest = onSheetDismiss)
                    }
                )
            }
        } else {
            AlertDialog(onDismissRequest = onDismissRequest, confirmButton = {
                TextButton(onClick = downloadButtonCallback) {
                    Text(text = stringResource(R.string.start_download))
                }
            }, dismissButton = { DismissButton { onDismissRequest() } }, icon = {
                Icon(
                    imageVector = Icons.Outlined.DoneAll, contentDescription = null
                )
            }, title = {
                Text(
                    stringResource(R.string.settings_before_download),
                    textAlign = TextAlign.Center
                )
            }, text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    sheetContent()
                }
            })
        }
    }



    if (showAudioSettingsDialog) {
        AudioQuickSettingsDialog(onDismissRequest = { showAudioSettingsDialog = false })
    }
    if (showVideoFormatDialog) {
        VideoFormatDialog(videoFormatPreference = videoFormatPreference,
            onDismissRequest = { showVideoFormatDialog = false },
            onConfirm = {
                videoFormatPreference = it
                VIDEO_FORMAT.updateInt(it)
            })
    }


    if (showTemplateSelectionDialog) {
        TemplatePickerDialog { showTemplateSelectionDialog = false }
    }
    if (showTemplateCreatorDialog) {
        CommandTemplateDialog(
            onDismissRequest = { showTemplateCreatorDialog = false },
            confirmationCallback = {
                scope.launch {
                    TEMPLATE_ID.updateInt(it)
                }
            })
    }
    if (showTemplateEditorDialog) {
        CommandTemplateDialog(
            commandTemplate = template,
            onDismissRequest = { showTemplateEditorDialog = false }
        )
    }
    if (showCookiesDialog && cookiesProfiles.isNotEmpty()) {
        CookiesQuickSettingsDialog(
            onDismissRequest = { showCookiesDialog = false },
            onConfirm = {},
            cookieProfiles = cookiesProfiles,
            onCookieProfileClicked = {
                onNavigateToCookieGeneratorPage(it.url)
            },
        )
    }
    if (showAudioConversionDialog) {
        AudioConversionQuickSettingsDialog(onDismissRequest = {
            showAudioConversionDialog = false
        })
    }
    if (showFormatSortingDialog) {
        FormatSortingDialog(
            fields = sortingFields,
            showSwitch = true,
            toggleableValue = formatSorting,
            onSwitchChecked = {
                formatSorting = it
                FORMAT_SORTING.updateBoolean(it)
            }, onImport = {
                sortingFields = DownloadUtil.DownloadPreferences().toFormatSorter()
            }, onDismissRequest = { showFormatSortingDialog = false },
            onConfirm = {
                sortingFields = it
                SORTING_FIELDS.updateString(it)
            })
    }
}