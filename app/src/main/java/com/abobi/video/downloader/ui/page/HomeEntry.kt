package com.abobi.video.downloader.ui.page

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.abobi.video.downloader.Downloader
import com.abobi.video.downloader.R
import com.abobi.video.downloader.ui.common.LocalWindowWidthState
import com.abobi.video.downloader.ui.common.Route
import com.abobi.video.downloader.ui.common.animatedComposable
import com.abobi.video.downloader.ui.common.animatedComposableVariant
import com.abobi.video.downloader.ui.common.arg
import com.abobi.video.downloader.ui.common.id
import com.abobi.video.downloader.ui.common.slideInVerticallyComposable
import com.abobi.video.downloader.ui.page.command.TaskListPage
import com.abobi.video.downloader.ui.page.command.TaskLogPage
import com.abobi.video.downloader.ui.page.download.DownloadPage
import com.abobi.video.downloader.ui.page.download.DownloadViewModel
import com.abobi.video.downloader.ui.page.download.PlaylistSelectionPage
import com.abobi.video.downloader.ui.page.settings.SettingsPage
import com.abobi.video.downloader.ui.page.settings.about.AboutPage
import com.abobi.video.downloader.ui.page.settings.general.GeneralDownloadPreferences
import com.abobi.video.downloader.ui.page.settings.network.CookieProfilePage
import com.abobi.video.downloader.ui.page.settings.network.CookiesViewModel
import com.abobi.video.downloader.ui.page.settings.network.NetworkPreferences
import com.abobi.video.downloader.ui.page.settings.network.WebViewPage
import com.abobi.video.downloader.ui.page.videolist.VideoListPage
import com.abobi.video.downloader.util.CookieHelper
import com.abobi.video.downloader.util.PreferenceUtil
import com.abobi.video.downloader.util.PreferenceUtil.getBoolean
import com.abobi.video.downloader.util.PreferenceUtil.getInt
import com.abobi.video.downloader.util.PreferenceUtil.getLong
import com.abobi.video.downloader.util.PreferenceUtil.getString
import com.abobi.video.downloader.util.ToastUtil
import com.abobi.video.downloader.util.UpdateUtil
import com.abobi.video.downloader.util.YT_DLP_AUTO_UPDATE
import com.abobi.video.downloader.util.YT_DLP_UPDATE_INTERVAL
import com.abobi.video.downloader.util.YT_DLP_UPDATE_TIME
import com.abobi.video.downloader.util.YT_DLP_VERSION
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "HomeEntry"

@Composable
fun HomeEntry(
    downloadViewModel: DownloadViewModel,
    cookiesViewModel: CookiesViewModel,
    isUrlShared: Boolean
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var currentDownloadStatus by remember { mutableStateOf(UpdateUtil.DownloadStatus.NotYet as UpdateUtil.DownloadStatus) }
    val scope = rememberCoroutineScope()
    var updateJob: Job? = null
    var latestRelease by remember { mutableStateOf(UpdateUtil.LatestRelease()) }
    val settings =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            UpdateUtil.installLatestApk()
        }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        if (result) {
            UpdateUtil.installLatestApk()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls())
                    settings.launch(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                else
                    UpdateUtil.installLatestApk()
            }
        }
    }

    val onNavigateBack: () -> Unit = {
        with(navController) {
            if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                popBackStack()
            }
        }
    }

    if (isUrlShared) {
        if (navController.currentDestination?.route != Route.HOME) {
            navController.popBackStack(route = Route.HOME, inclusive = false, saveState = true)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        NavHost(
            modifier = Modifier
                .fillMaxWidth(
                    when (LocalWindowWidthState.current) {
                        WindowWidthSizeClass.Compact -> 1f
                        WindowWidthSizeClass.Expanded -> 0.5f
                        else -> 0.8f
                    }
                )
                .align(Alignment.Center),
            navController = navController,
            startDestination = Route.HOME
        ) {
            animatedComposable(Route.HOME) {
                DownloadPage(
                    navigateToDownloads = { navController.navigate(Route.DOWNLOADS) },
                    navigateToSettings = {
                        navController.navigate(Route.SETTINGS) {
                            launchSingleTop = true
                        }
                    },
                    navigateToPlaylistPage = { navController.navigate(Route.PLAYLIST) },
                    downloadViewModel = downloadViewModel
                )
            }
            animatedComposable(Route.DOWNLOADS) { VideoListPage { onNavigateBack() } }
            animatedComposableVariant(Route.TASK_LIST) {
                TaskListPage(
                    onNavigateBack = onNavigateBack,
                    onNavigateToDetail = { navController.navigate(Route.TASK_LOG id it) }
                )
            }
            slideInVerticallyComposable(
                Route.TASK_LOG arg Route.TASK_HASHCODE,
                arguments = listOf(navArgument(Route.TASK_HASHCODE) { type = NavType.IntType })
            ) {
                TaskLogPage(
                    onNavigateBack = onNavigateBack,
                    taskHashCode = it.arguments?.getInt(Route.TASK_HASHCODE) ?: -1
                )
            }

//            animatedComposable(Route.DOWNLOAD_QUEUE) { DownloadQueuePage { onNavigateBack() } }
            slideInVerticallyComposable(Route.PLAYLIST) { PlaylistSelectionPage { onNavigateBack() } }
            settingsGraph(
                cookiesViewModel = cookiesViewModel,
                onNavigateBack = onNavigateBack,
                onNavigateTo = { route ->
                    navController.navigate(route = route) {
                        launchSingleTop = true
                    }
                }
            )

        }

        WelcomeDialog {
            navController.navigate(Route.SETTINGS)
        }

        val downloaderState by Downloader.downloaderState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            if (downloaderState !is Downloader.State.Idle) return@LaunchedEffect

            if (!YT_DLP_AUTO_UPDATE.getBoolean() && YT_DLP_VERSION.getString()
                    .isNotEmpty()
            ) return@LaunchedEffect

            if (!PreferenceUtil.isNetworkAvailableForDownload()) {
                return@LaunchedEffect
            }

            val lastUpdateTime = YT_DLP_UPDATE_TIME.getLong()
            val currentTime = System.currentTimeMillis()

            if (currentTime < lastUpdateTime + YT_DLP_UPDATE_INTERVAL.getLong()) {
                return@LaunchedEffect
            }

            runCatching {
                Downloader.updateState(state = Downloader.State.Updating)
                withContext(Dispatchers.IO) {
                    UpdateUtil.updateYtDlp()
                }
            }.onFailure {
                it.printStackTrace()
            }
            Downloader.updateState(state = Downloader.State.Idle)
        }

        LaunchedEffect(Unit) {
            if (!PreferenceUtil.isNetworkAvailableForDownload() || !PreferenceUtil.isAutoUpdateEnabled()
            )
                return@LaunchedEffect
            launch(Dispatchers.IO) {
                runCatching {
                    UpdateUtil.checkForUpdate()?.let {
                        latestRelease = it
                        showUpdateDialog = true
                    }
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }

        if (showUpdateDialog) {
            UpdateDialogImpl(
                onDismissRequest = {
                    showUpdateDialog = false
                    updateJob?.cancel()
                },
                title = latestRelease.name.toString(),
                onConfirmUpdate = {
                    updateJob = scope.launch(Dispatchers.IO) {
                        runCatching {
                            UpdateUtil.downloadApk(latestRelease = latestRelease)
                                .collect { downloadStatus ->
                                    currentDownloadStatus = downloadStatus
                                    if (downloadStatus is UpdateUtil.DownloadStatus.Finished) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            launcher.launch(Manifest.permission.REQUEST_INSTALL_PACKAGES)
                                        }
                                    }
                                }
                        }.onFailure {
                            it.printStackTrace()
                            currentDownloadStatus = UpdateUtil.DownloadStatus.NotYet
                            ToastUtil.makeToastSuspend(context.getString(R.string.app_update_failed))
                            return@launch
                        }
                    }
                },
                releaseNote = latestRelease.body.toString(),
                downloadStatus = currentDownloadStatus
            )
        }
    }
}

fun NavGraphBuilder.settingsGraph(
    cookiesViewModel: CookiesViewModel,
    onNavigateBack: () -> Unit,
    onNavigateTo: (route: String) -> Unit
) {
    navigation(startDestination = Route.SETTINGS_PAGE, route = Route.SETTINGS) {
        animatedComposable(Route.SETTINGS_PAGE) {
            SettingsPage(
                onNavigateBack = onNavigateBack,
                onNavigateTo = onNavigateTo
            )
        }
        animatedComposable(Route.GENERAL_DOWNLOAD_PREFERENCES) {
            GeneralDownloadPreferences(onNavigateBack = { onNavigateBack() })
        }
        animatedComposable(Route.ABOUT) {
            AboutPage(onNavigateBack = onNavigateBack)
        }
        animatedComposable(Route.NETWORK_PREFERENCES) {
            NetworkPreferences(navigateToCookieProfilePage = {
                onNavigateTo(Route.COOKIE_PROFILE)
            }) { onNavigateBack() }
        }
        animatedComposable(Route.COOKIE_PROFILE) {
            CookieProfilePage(
                cookiesViewModel = cookiesViewModel,
                navigateToCookieGeneratorPage = { onNavigateTo(Route.COOKIE_GENERATOR_WEBVIEW) },
            ) { onNavigateBack() }
        }
        animatedComposable(
            Route.COOKIE_GENERATOR_WEBVIEW
        ) {
            val webViewContext = LocalContext.current
            val webViewScope = rememberCoroutineScope()
            WebViewPage(cookiesViewModel) {
                val profileUrl = cookiesViewModel.stateFlow.value.editingCookieProfile.url
                onNavigateBack()
                webViewScope.launch(Dispatchers.IO) {
                    val result = CookieHelper.refreshCookiesForSite(profileUrl)
                    withContext(Dispatchers.Main) {
                        result.onSuccess { saveResult ->
                            ToastUtil.makeToast(
                                webViewContext.getString(
                                    R.string.cookies_saved_detail,
                                    saveResult.cookieCount,
                                    saveResult.siteLabel,
                                    saveResult.siteFilePath,
                                )
                            )
                        }.onFailure {
                            ToastUtil.makeToast(
                                webViewContext.getString(R.string.cookies_saved_msg)
                            )
                        }
                    }
                }
            }
        }
    }
}

