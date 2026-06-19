package com.abobi.video.downloader.ui.page.settings.general

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PlaylistAddCheck
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.abobi.video.downloader.App
import com.abobi.video.downloader.R
import com.abobi.video.downloader.ui.common.booleanState
import com.abobi.video.downloader.ui.common.intState
import com.abobi.video.downloader.ui.component.BackButton
import com.abobi.video.downloader.ui.component.ConfirmButton
import com.abobi.video.downloader.ui.component.DismissButton
import com.abobi.video.downloader.ui.component.PreferenceItem
import com.abobi.video.downloader.ui.component.PreferenceSwitch
import com.abobi.video.downloader.ui.component.SealDialog
import com.abobi.video.downloader.ui.page.download.NotificationPermissionDialog
import com.abobi.video.downloader.util.NOTIFICATION
import com.abobi.video.downloader.util.NotificationUtil
import com.abobi.video.downloader.util.PLAYLIST
import com.abobi.video.downloader.util.PreferenceStrings.getUpdateIntervalText
import com.abobi.video.downloader.util.PreferenceUtil
import com.abobi.video.downloader.util.PreferenceUtil.getLong
import com.abobi.video.downloader.util.PreferenceUtil.getString
import com.abobi.video.downloader.util.PreferenceUtil.updateBoolean
import com.abobi.video.downloader.util.PreferenceUtil.updateInt
import com.abobi.video.downloader.util.PreferenceUtil.updateLong
import com.abobi.video.downloader.util.ToastUtil
import com.abobi.video.downloader.util.UpdateIntervalList
import com.abobi.video.downloader.util.UpdateUtil
import com.abobi.video.downloader.util.YT_DLP_AUTO_UPDATE
import com.abobi.video.downloader.util.YT_DLP_NIGHTLY
import com.abobi.video.downloader.util.YT_DLP_STABLE
import com.abobi.video.downloader.util.YT_DLP_UPDATE_CHANNEL
import com.abobi.video.downloader.util.YT_DLP_UPDATE_INTERVAL
import com.abobi.video.downloader.util.YT_DLP_VERSION
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class
)
@Composable
fun GeneralDownloadPreferences(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    var showYtdlpDialog by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }

    var downloadPlaylist by remember { mutableStateOf(PreferenceUtil.getValue(PLAYLIST)) }
    var downloadNotification by remember {
        mutableStateOf(PreferenceUtil.getValue(NOTIFICATION))
    }

    var isNotificationPermissionGranted by remember {
        mutableStateOf(NotificationUtil.areNotificationsEnabled())
    }

    val notificationPermission =
        if (Build.VERSION.SDK_INT >= 33) rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS) { status ->
            if (!status) ToastUtil.makeToast(context.getString(R.string.permission_denied))
            else isNotificationPermissionGranted = true
        } else null

    var showNotificationDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
        canScroll = { true })
    Scaffold(modifier = Modifier
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            com.abobi.video.downloader.ui.component.LargeTopAppBar(
                title = { Text(text = stringResource(id = R.string.general_settings)) },
                navigationIcon = {
                    BackButton { onNavigateBack() }
                },
                scrollBehavior = scrollBehavior
            )
        },
        content = {
            LazyColumn(
                modifier = Modifier.padding(it)
            ) {
                item {
                    var ytdlpVersion by remember {
                        mutableStateOf(
                            YoutubeDL.getInstance().version(context.applicationContext)
                                ?: context.getString(R.string.ytdlp_update)
                        )
                    }
                    PreferenceItem(
                        title = stringResource(id = R.string.ytdlp_update_action),
                        description = ytdlpVersion,
                        leadingIcon = {
                            if (isUpdating) UpdateProgressIndicator() else {
                                Icon(
                                    imageVector = Icons.Outlined.Update,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(start = 8.dp, end = 16.dp)
                                        .size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }, onClick = {
                            scope.launch {
                                runCatching {
                                    isUpdating = true
                                    UpdateUtil.updateYtDlp()
                                    ytdlpVersion = YT_DLP_VERSION.getString()
                                }.onFailure { th ->
                                    th.printStackTrace()
                                    ToastUtil.makeToastSuspend(App.context.getString(R.string.yt_dlp_update_fail))
                                }.onSuccess {
                                    ToastUtil.makeToastSuspend(context.getString(R.string.yt_dlp_up_to_date) + " (${YT_DLP_VERSION.getString()})")
                                }
                                isUpdating = false
                            }
                        }, onClickLabel = stringResource(id = R.string.update),
                        trailingIcon = {
                            IconButton(onClick = { showYtdlpDialog = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = stringResource(
                                        id = R.string.open_settings
                                    )
                                )
                            }
                        }
                    )
                }
                item {
                    PreferenceSwitch(title = stringResource(id = R.string.download_notification),
                        description = stringResource(
                            id = if (isNotificationPermissionGranted) R.string.download_notification_desc
                            else R.string.permission_denied
                        ),
                        icon = if (!isNotificationPermissionGranted) Icons.Outlined.NotificationsOff
                        else if (!downloadNotification) Icons.Outlined.Notifications
                        else Icons.Outlined.NotificationsActive,
                        isChecked = downloadNotification && isNotificationPermissionGranted,
                        onClick = {
                            if (notificationPermission?.status is PermissionStatus.Denied) {
                                showNotificationDialog = true
                            } else if (isNotificationPermissionGranted) {
                                if (downloadNotification)
                                    NotificationUtil.cancelAllNotifications()
                                downloadNotification = !downloadNotification
                                PreferenceUtil.updateValue(
                                    NOTIFICATION, downloadNotification
                                )
                            }
                        })
                }

                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.download_playlist),
                        onClick = {
                            downloadPlaylist = !downloadPlaylist
                            PreferenceUtil.updateValue(
                                PLAYLIST, downloadPlaylist
                            )
                        },
                        icon = Icons.Outlined.PlaylistAddCheck,
                        enabled = true,
                        description = stringResource(R.string.download_playlist_desc),
                        isChecked = downloadPlaylist
                    )
                }
            }
        })
    if (showYtdlpDialog) {
        var ytdlpUpdateChannel by YT_DLP_UPDATE_CHANNEL.intState
        var ytdlpAutoUpdate by YT_DLP_AUTO_UPDATE.booleanState
        var updateInterval by remember { mutableLongStateOf(YT_DLP_UPDATE_INTERVAL.getLong()) }

        SealDialog(
            onDismissRequest = { showYtdlpDialog = false },
            confirmButton = {
                ConfirmButton {
                    YT_DLP_AUTO_UPDATE.updateBoolean(ytdlpAutoUpdate)
                    YT_DLP_UPDATE_CHANNEL.updateInt(ytdlpUpdateChannel)
                    YT_DLP_UPDATE_INTERVAL.updateLong(updateInterval)
                    showYtdlpDialog = false
                }
            },
            dismissButton = {
                DismissButton {
                    showYtdlpDialog = false
                }
            },
            title = { Text(text = stringResource(id = R.string.update)) },
            icon = { Icon(Icons.Outlined.SyncAlt, null) },
            text = {
                LazyColumn() {
                    item {
                        Text(
                            text = stringResource(id = R.string.update_channel),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 16.dp, bottom = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    item {
                        DialogSingleChoiceItem(
                            text = "yt-dlp",
                            selected = ytdlpUpdateChannel == YT_DLP_STABLE,
                            label = "Stable"
                        ) {
                            ytdlpUpdateChannel = YT_DLP_STABLE
                        }
                    }
                    item {
                        DialogSingleChoiceItem(
                            text = "yt-dlp-nightly-builds",
                            selected = ytdlpUpdateChannel == YT_DLP_NIGHTLY,
                            label = "Nightly",
                            labelContainerColor = MaterialTheme.colorScheme.tertiary
                        ) {
                            ytdlpUpdateChannel = YT_DLP_NIGHTLY
                        }
                    }
                    item {
                        Text(
                            text = stringResource(id = R.string.additional_settings),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 16.dp, bottom = 16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    item {
                        var expanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            expanded = expanded,
                            onExpandedChange = { expanded = it }) {
                            OutlinedTextField(
                                value = if (!ytdlpAutoUpdate) stringResource(id = R.string.disabled)
                                else getUpdateIntervalText(updateInterval),
                                onValueChange = {},
                                label = { Text(text = stringResource(id = R.string.auto_update)) },
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
                            )
                            ExposedDropdownMenu(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                expanded = expanded,
                                onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.disabled)) },
                                    onClick = {
                                        ytdlpAutoUpdate = false
                                        expanded = false
                                    })
                                for ((interval, stringId) in UpdateIntervalList) {
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(id = stringId)) },
                                        onClick = {
                                            ytdlpAutoUpdate = true
                                            updateInterval = interval
                                            expanded = false
                                        })
                                }
                            }
                        }
                    }
                }
            },
        )
    }
    if (showNotificationDialog) {
        NotificationPermissionDialog(onDismissRequest = {
            showNotificationDialog = false
        }, onPermissionGranted = {
            notificationPermission?.launchPermissionRequest()
            NOTIFICATION.updateBoolean(true)
            downloadNotification = true
            showNotificationDialog = false
        })
    }

}

@Composable
private fun DialogSingleChoiceItem(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    label: String,
    labelContainerColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = true,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        RadioButton(
            modifier = Modifier.clearAndSetSemantics { }, selected = selected, onClick = onClick
        )

        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.weight(1f))
        Surface(
            modifier = modifier.padding(end = 12.dp),
            shape = CircleShape,
            color = labelContainerColor
        ) {
            Text(
                modifier = Modifier.padding(4.dp),
                text = label,
                color = MaterialTheme.colorScheme.contentColorFor(labelContainerColor),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun DialogCheckBoxItem(
    modifier: Modifier = Modifier,
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = true,
                onValueChange = { onClick() },
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            modifier = Modifier.clearAndSetSemantics { },
            checked = checked, onCheckedChange = { onClick() },
        )
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun UpdateProgressIndicator() {
    CircularProgressIndicator(
        modifier = Modifier
            .padding(start = 8.dp, end = 16.dp)
            .size(24.dp)
            .padding(2.dp)
    )
}
