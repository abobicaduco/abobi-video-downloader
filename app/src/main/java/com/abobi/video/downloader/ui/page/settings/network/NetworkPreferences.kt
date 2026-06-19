package com.abobi.video.downloader.ui.page.settings.network

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material.icons.outlined.SignalCellular4Bar
import androidx.compose.material.icons.outlined.SignalCellularConnectedNoInternet4Bar
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.abobi.video.downloader.R
import com.abobi.video.downloader.ui.common.booleanState
import com.abobi.video.downloader.ui.component.BackButton
import com.abobi.video.downloader.ui.component.LargeTopAppBar
import com.abobi.video.downloader.ui.component.PreferenceInfo
import com.abobi.video.downloader.ui.component.PreferenceItem
import com.abobi.video.downloader.ui.component.PreferenceSubtitle
import com.abobi.video.downloader.ui.component.PreferenceSwitch
import com.abobi.video.downloader.ui.component.PreferenceSwitchWithDivider
import com.abobi.video.downloader.util.ARIA2C
import com.abobi.video.downloader.util.CELLULAR_DOWNLOAD
import com.abobi.video.downloader.util.PROXY
import com.abobi.video.downloader.util.PreferenceUtil.getValue
import com.abobi.video.downloader.util.PreferenceUtil.updateBoolean
import com.abobi.video.downloader.util.PreferenceUtil.updateValue
import com.abobi.video.downloader.util.RATE_LIMIT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkPreferences(
    navigateToCookieProfilePage: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
        canScroll = { true }
    )

    var showConcurrentDownloadDialog by remember { mutableStateOf(false) }
    var showRateLimitDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var aria2c by remember { mutableStateOf(getValue(ARIA2C)) }
    var proxy by PROXY.booleanState

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        modifier = Modifier,
                        text = stringResource(id = R.string.network),
                    )
                }, navigationIcon = {
                    BackButton {
                        onNavigateBack()
                    }
                }, scrollBehavior = scrollBehavior
            )
        }, content = {
            LazyColumn(Modifier.padding(it)) {
                item {
                    PreferenceSubtitle(text = stringResource(R.string.general_settings))
                }
                item {
                    var isRateLimitEnabled by remember {
                        mutableStateOf(getValue(RATE_LIMIT))
                    }

                    PreferenceSwitchWithDivider(
                        title = stringResource(R.string.rate_limit),
                        description = stringResource(R.string.rate_limit_desc),
                        icon = Icons.Outlined.Speed,
                        isChecked = isRateLimitEnabled,
                        onChecked = {
                            isRateLimitEnabled = !isRateLimitEnabled
                            updateValue(
                                RATE_LIMIT,
                                isRateLimitEnabled
                            )
                        },
                        onClick = { showRateLimitDialog = true }
                    )
                }
                item {
                    var isDownloadWithCellularEnabled by remember {
                        mutableStateOf(getValue(CELLULAR_DOWNLOAD))
                    }
                    PreferenceSwitch(
                        title = stringResource(R.string.download_with_cellular),
                        description = stringResource(R.string.download_with_cellular_desc),
                        icon = if (isDownloadWithCellularEnabled) Icons.Outlined.SignalCellular4Bar
                        else Icons.Outlined.SignalCellularConnectedNoInternet4Bar,
                        isChecked = isDownloadWithCellularEnabled,
                        onClick = {
                            isDownloadWithCellularEnabled = !isDownloadWithCellularEnabled
                            updateValue(
                                CELLULAR_DOWNLOAD,
                                isDownloadWithCellularEnabled
                            )
                        }
                    )
                }

                item {
                    PreferenceSubtitle(text = stringResource(id = R.string.advanced_settings))
                }

                item {
                    PreferenceSwitch(
                        title = stringResource(R.string.aria2),
                        icon = Icons.Outlined.Bolt,
                        description = stringResource(
                            R.string.aria2_desc
                        ),
                        isChecked = aria2c,
                        onClick = {
                            aria2c = !aria2c
                            updateValue(ARIA2C, aria2c)
                        }
                    )
                }
                item {
                    PreferenceSwitchWithDivider(
                        title = stringResource(id = R.string.proxy),
                        description = stringResource(id = R.string.proxy_desc),
                        icon = Icons.Outlined.VpnKey,
                        isChecked = proxy,
                        onChecked = {
                            proxy = !proxy
                            PROXY.updateBoolean(proxy)
                        },
                        onClick = { showProxyDialog = true },
                    )
                }
                item {
                    PreferenceItem(
                        title = stringResource(id = R.string.concurrent_download),
                        description = stringResource(R.string.concurrent_download_desc),
                        icon = Icons.Outlined.OfflineBolt,
                        enabled = !aria2c,
                    ) { showConcurrentDownloadDialog = true }
                }
                item {
                    PreferenceItem(title = stringResource(R.string.cookies),
                        description = stringResource(R.string.cookies_desc),
                        icon = Icons.Outlined.Cookie,
                        onClick = { navigateToCookieProfilePage() })
                }

            }
        })

    if (showConcurrentDownloadDialog) {
        ConcurrentDownloadDialog {
            showConcurrentDownloadDialog = false
        }
    }

    if (showRateLimitDialog) {
        RateLimitDialog {
            showRateLimitDialog = false
        }
    }
    if (showProxyDialog) {
        ProxyConfigurationDialog {
            showProxyDialog = false
        }
    }
}