package com.abobi.video.downloader.ui.page.settings.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContactSupport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.UpdateDisabled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.UrlAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.abobi.video.downloader.App
import com.abobi.video.downloader.App.Companion.packageInfo
import com.abobi.video.downloader.R
import com.abobi.video.downloader.ui.component.BackButton
import com.abobi.video.downloader.ui.component.ConfirmButton
import com.abobi.video.downloader.ui.component.LargeTopAppBar
import com.abobi.video.downloader.ui.component.PreferenceItem
import com.abobi.video.downloader.ui.component.PreferenceSwitchWithDivider
import com.abobi.video.downloader.util.AUTO_UPDATE
import com.abobi.video.downloader.util.PreferenceUtil
import com.abobi.video.downloader.util.ToastUtil

private const val repoUrl = "https://github.com/abobicaduco/abobi-video-downloader"
private const val developerUrl = "https://github.com/abobicaduco"
private const val githubIssueUrl = "https://github.com/abobicaduco/abobi-video-downloader/issues"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(onNavigateBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
        canScroll = { true })
    val clipboardManager = LocalClipboardManager.current
    var isAutoUpdateEnabled by remember { mutableStateOf(PreferenceUtil.isAutoUpdateEnabled()) }

    val info = App.getVersionReport()
    val versionName = packageInfo.versionName

    val uriHandler = LocalUriHandler.current
    fun openUrl(url: String) {
        uriHandler.openUri(url)
    }
    Scaffold(modifier = Modifier
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        LargeTopAppBar(title = {
            Text(
                modifier = Modifier,
                text = stringResource(id = R.string.about),
            )
        }, navigationIcon = {
            BackButton {
                onNavigateBack()
            }
        }, scrollBehavior = scrollBehavior
        )
    }, content = {
        LazyColumn(modifier = Modifier.padding(it)) {
            item {
                PreferenceItem(
                    title = stringResource(R.string.developer),
                    description = stringResource(R.string.developer_desc),
                    icon = Icons.Outlined.Person,
                ) { openUrl(developerUrl) }
            }
            item {
                PreferenceItem(
                    title = stringResource(R.string.github_repository),
                    description = stringResource(R.string.github_repository_desc),
                    icon = Icons.Outlined.Code,
                ) { openUrl(repoUrl) }
            }
            item {
                PreferenceItem(
                    title = stringResource(R.string.github_issue),
                    description = stringResource(R.string.github_issue_desc),
                    icon = Icons.Outlined.ContactSupport,
                ) { openUrl(githubIssueUrl) }
            }
            item {
                PreferenceSwitchWithDivider(
                    title = stringResource(R.string.auto_update),
                    description = stringResource(R.string.check_for_updates_desc),
                    icon = if (isAutoUpdateEnabled) Icons.Outlined.Update else Icons.Outlined.UpdateDisabled,
                    isChecked = isAutoUpdateEnabled,
                    isSwitchEnabled = !App.isFDroidBuild(),
                    onChecked = {
                        isAutoUpdateEnabled = !isAutoUpdateEnabled
                        PreferenceUtil.updateValue(AUTO_UPDATE, isAutoUpdateEnabled)
                    }
                )
            }
            item {
                PreferenceItem(
                    title = stringResource(R.string.version),
                    description = versionName,
                    icon = Icons.Outlined.Info,
                ) {
                    clipboardManager.setText(AnnotatedString(info))
                    ToastUtil.makeToast(R.string.info_copied)
                }
            }
        }
    })
}

@OptIn(ExperimentalTextApi::class)
@Composable
@Preview
fun AutoUpdateUnavailableDialog(onDismissRequest: () -> Unit = {}) {
    val uriHandler = LocalUriHandler.current
    val hapticFeedback = LocalHapticFeedback.current
    val hyperLinkText = stringResource(id = R.string.switch_to_github_builds)
    val text = stringResource(
        id = R.string.auto_update_disabled_msg,
        "F-Droid", hyperLinkText
    )

    val annotatedString = buildAnnotatedString {
        append(text)
        val startIndex = text.indexOf(hyperLinkText)
        val endIndex = startIndex + hyperLinkText.length
        addUrlAnnotation(
            UrlAnnotation("https://github.com/abobicaduco/abobi-video-downloader/actions"),
            start = startIndex,
            end = endIndex
        )
        addStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.tertiary,
                textDecoration = TextDecoration.Underline,
            ), start = startIndex,
            end = endIndex
        )

    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            ConfirmButton(stringResource(id = R.string.got_it)) {
                onDismissRequest()
            }
        },
        icon = { Icon(Icons.Outlined.UpdateDisabled, null) },
        title = {
            Text(
                text = stringResource(id = R.string.feature_unavailable),
                textAlign = TextAlign.Center
            )
        },
        text = {
            ClickableText(
                text = annotatedString,
                onClick = { index ->
                    annotatedString.getUrlAnnotations(index, index).firstOrNull()?.let {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        uriHandler.openUri(it.item.url)
                    }
                },
                style = MaterialTheme.typography.bodyMedium.copy(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        })
}
