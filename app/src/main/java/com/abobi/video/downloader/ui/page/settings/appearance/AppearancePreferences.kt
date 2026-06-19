package com.abobi.video.downloader.ui.page.settings.appearance

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
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
import com.abobi.video.downloader.ui.common.LocalDarkTheme
import com.abobi.video.downloader.ui.common.Route
import com.abobi.video.downloader.ui.component.BackButton
import com.abobi.video.downloader.ui.component.LargeTopAppBar
import com.abobi.video.downloader.ui.component.PreferenceItem
import com.abobi.video.downloader.util.toDisplayName
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearancePreferences(
    onNavigateBack: () -> Unit,
    onNavigateTo: (String) -> Unit
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )

    val isDarkTheme = LocalDarkTheme.current.isDarkTheme()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        modifier = Modifier,
                        text = stringResource(id = R.string.look_and_feel),
                    )
                },
                navigationIcon = {
                    BackButton(onNavigateBack)
                },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            LazyColumn(Modifier.padding(it)) {
                item {
                    PreferenceItem(
                        title = stringResource(id = R.string.dark_theme),
                        icon = if (isDarkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        description = LocalDarkTheme.current.getDarkThemeDesc(),
                    ) { onNavigateTo(Route.DARK_THEME) }
                }
                if (Build.VERSION.SDK_INT >= 24) {
                    item {
                        PreferenceItem(
                            title = stringResource(R.string.language),
                            icon = Icons.Outlined.Language,
                            description = Locale.getDefault().toDisplayName(),
                        ) { onNavigateTo(Route.LANGUAGES) }
                    }
                }
            }
        },
    )
}
