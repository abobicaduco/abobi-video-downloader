package com.abobi.video.downloader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abobi.video.downloader.App.Companion.context
import com.abobi.video.downloader.R
import com.abobi.video.downloader.ui.common.LocalDarkTheme
import com.abobi.video.downloader.ui.common.LocalDynamicColorSwitch
import com.abobi.video.downloader.ui.common.SettingsProvider
import com.abobi.video.downloader.ui.page.HomeEntry
import com.abobi.video.downloader.ui.page.download.DownloadViewModel
import com.abobi.video.downloader.ui.page.settings.network.CookiesViewModel
import com.abobi.video.downloader.ui.theme.SealTheme
import com.abobi.video.downloader.util.PreferenceUtil
import com.abobi.video.downloader.util.StoragePermissionHelper
import com.abobi.video.downloader.util.ToastUtil
import com.abobi.video.downloader.util.matchUrlFromSharedText
import com.abobi.video.downloader.util.setLanguage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val downloadViewModel: DownloadViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }
        runBlocking {
            if (Build.VERSION.SDK_INT < 33) {
                setLanguage(PreferenceUtil.getLocaleFromPreference())
            }
        }
        context = this.baseContext
        setContent {
            val cookiesViewModel: CookiesViewModel = viewModel()

            val storagePermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                if (results.isNotEmpty() && results.values.any { !it }) {
                    ToastUtil.makeToast(getString(R.string.storage_permission_rationale))
                }
            }
            LaunchedEffect(Unit) {
                val missing = StoragePermissionHelper.requiredPermissions().filter {
                    checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (missing.isNotEmpty()) {
                    storagePermissionLauncher.launch(missing.toTypedArray())
                }
            }

            val isUrlSharingTriggered =
                downloadViewModel.viewStateFlow.collectAsState().value.isUrlSharingTriggered
            val windowSizeClass = calculateWindowSizeClass(this)
            SettingsProvider(windowWidthSizeClass = windowSizeClass.widthSizeClass) {
                SealTheme(
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                    isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                    isDynamicColorEnabled = LocalDynamicColorSwitch.current,
                ) {
                    HomeEntry(
                        downloadViewModel = downloadViewModel,
                        cookiesViewModel = cookiesViewModel,
                        isUrlShared = isUrlSharingTriggered
                    )
                }
            }

            handleShareIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        handleShareIntent(intent)
        super.onNewIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        Log.d(TAG, "handleShareIntent: $intent")

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.dataString?.let {
                    sharedUrl = it
                    downloadViewModel.updateUrl(sharedUrl, true)
                }
            }

            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?.let { sharedContent ->
                        intent.removeExtra(Intent.EXTRA_TEXT)
                        matchUrlFromSharedText(sharedContent)
                            .let { matchedUrl ->
                                if (sharedUrl != matchedUrl) {
                                    sharedUrl = matchedUrl
                                    downloadViewModel.updateUrl(sharedUrl, true)
                                }
                            }
                    }
            }
        }

    }

    companion object {
        private const val TAG = "MainActivity"
        private var sharedUrl = ""

    }

}





