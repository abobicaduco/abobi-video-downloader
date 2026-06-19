package com.abobi.video.downloader.ui.page.settings.format

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.abobi.video.downloader.R
import com.abobi.video.downloader.ui.component.BackButton
import com.abobi.video.downloader.ui.component.LargeTopAppBar
import com.abobi.video.downloader.ui.component.PreferenceInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadFormatPreferences(onNavigateBack: () -> Unit, navigateToSubtitlePage: () -> Unit) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        modifier = Modifier,
                        text = stringResource(id = R.string.format),
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
        content = {
            LazyColumn(Modifier.padding(it)) {
                item {
                    PreferenceInfo(text = stringResource(id = R.string.auto_video_quality_info))
                }
            }
        },
    )
}
