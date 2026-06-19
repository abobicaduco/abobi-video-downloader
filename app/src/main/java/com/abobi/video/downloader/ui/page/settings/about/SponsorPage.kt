package com.abobi.video.downloader.ui.page.settings.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.abobi.video.downloader.R
import com.abobi.video.downloader.ui.component.BackButton
import com.abobi.video.downloader.ui.component.LargeTopAppBar
import com.abobi.video.downloader.ui.component.PreferenceItem
import com.abobi.video.downloader.ui.theme.SealTheme
import com.abobi.video.downloader.util.PreferenceUtil.updateInt
import com.abobi.video.downloader.util.SHOW_SPONSOR_MSG
import com.abobi.video.downloader.util.ToastUtil

private const val PIX_KEY = "f74458dc-2a36-49bd-9250-1cef4365ebb8"
private const val SITE_URL = "https://abobiferramentas.com"
private const val REPO_URL = "https://github.com/abobicaduco/abobi-video-downloader"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonatePage(onNavigateBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
        canScroll = { true },
    )
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        SHOW_SPONSOR_MSG.updateInt(0)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        modifier = Modifier,
                        text = stringResource(id = R.string.sponsors),
                    )
                },
                navigationIcon = {
                    BackButton {
                        onNavigateBack()
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    text = stringResource(id = R.string.sponsor_msg),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    text = stringResource(id = R.string.sponsor_msg2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PreferenceItem(
                    title = stringResource(id = R.string.pix_key),
                    description = PIX_KEY,
                    icon = Icons.Outlined.ContentCopy,
                ) {
                    clipboardManager.setText(AnnotatedString(PIX_KEY))
                    ToastUtil.makeToast(R.string.pix_copied)
                }
                PreferenceItem(
                    title = stringResource(id = R.string.website),
                    description = SITE_URL,
                    icon = Icons.Outlined.Home,
                ) {
                    uriHandler.openUri(SITE_URL)
                }
                PreferenceItem(
                    title = stringResource(id = R.string.readme),
                    description = REPO_URL,
                    icon = Icons.Outlined.Link,
                ) {
                    uriHandler.openUri(REPO_URL)
                }
                FilledTonalButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(PIX_KEY))
                        ToastUtil.makeToast(R.string.pix_copied)
                    },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp, end = 12.dp),
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(ButtonDefaults.IconSize),
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                    )
                    Text(text = stringResource(id = R.string.copy_pix_key))
                }
            }
        },
    )
}

@Composable
@Preview
fun SponsorPagePreview() {
    SealTheme {
        DonatePage {}
    }
}
