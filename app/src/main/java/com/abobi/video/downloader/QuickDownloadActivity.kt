package com.abobi.video.downloader

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.abobi.video.downloader.ui.common.LocalDarkTheme
import com.abobi.video.downloader.ui.common.LocalDynamicColorSwitch
import com.abobi.video.downloader.ui.common.SettingsProvider
import com.abobi.video.downloader.ui.page.download.CollectionSelectionContent
import com.abobi.video.downloader.ui.page.download.QuickDownloadTypeDialog
import com.abobi.video.downloader.ui.page.download.startCollectionDownload
import com.abobi.video.downloader.ui.theme.SealTheme
import com.abobi.video.downloader.util.CollectionInvestigationResult
import com.abobi.video.downloader.util.DownloadUtil
import com.abobi.video.downloader.util.PreferenceUtil
import com.abobi.video.downloader.util.StoragePermissionHelper
import com.abobi.video.downloader.util.ToastUtil
import com.abobi.video.downloader.util.UrlLinkDetector
import com.abobi.video.downloader.util.matchUrlFromSharedText
import com.abobi.video.downloader.util.setLanguage
import kotlinx.coroutines.runBlocking

private const val TAG = "ShareActivity"

class QuickDownloadActivity : ComponentActivity() {
    private var url: String = ""

    private fun handleShareIntent(intent: Intent) {
        Log.d(TAG, "handleShareIntent: $intent")
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.dataString?.let { url = it }
            }

            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?.let { sharedContent ->
                        intent.removeExtra(Intent.EXTRA_TEXT)
                        url = matchUrlFromSharedText(sharedContent)
                    }
            }
        }
    }

    private fun onSingleVideoDownload(extractAudio: Boolean) {
        Downloader.quickDownload(
            url = url,
            downloadPreferences = DownloadUtil.DownloadPreferences.forQuickDownload(extractAudio),
        )
    }

    @OptIn(
        ExperimentalMaterial3WindowSizeClassApi::class,
        ExperimentalMaterial3Api::class,
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }

        window.run {
            setBackgroundDrawable(ColorDrawable(0))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
            }
        }
        handleShareIntent(intent)
        runBlocking {
            if (Build.VERSION.SDK_INT < 33) {
                setLanguage(PreferenceUtil.getLocaleFromPreference())
            }
        }

        if (url.isEmpty()) {
            finish()
            return
        }

        val isCollectionUrl = UrlLinkDetector.isCollectionUrl(url)

        setContent {
            SettingsProvider(
                windowWidthSizeClass = calculateWindowSizeClass(this).widthSizeClass,
            ) {
                SealTheme(
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                    isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                    isDynamicColorEnabled = LocalDynamicColorSwitch.current,
                ) {
                    if (isCollectionUrl) {
                        QuickCollectionShareFlow(
                            url = url,
                            onDismiss = { finish() },
                            onDownloadStarted = { finish() },
                        )
                    } else {
                        var showDialog by remember { mutableStateOf(true) }
                        QuickDownloadTypeDialog(
                            showDialog = showDialog,
                            onDownloadConfirm = { extractAudio ->
                                showDialog = false
                                onSingleVideoDownload(extractAudio)
                                finish()
                            },
                            onDismissRequest = {
                                showDialog = false
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        intent?.let { handleShareIntent(it) }
        super.onNewIntent(intent)
    }
}

@Composable
private fun QuickCollectionShareFlow(
    url: String,
    onDismiss: () -> Unit,
    onDownloadStarted: () -> Unit,
) {
    val context = LocalContext.current
    var investigation by remember(url) { mutableStateOf<CollectionInvestigationResult?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }

    LaunchedEffect(url) {
        if (!StoragePermissionHelper.hasStorageAccess(context)) {
            ToastUtil.makeToast(R.string.storage_permission_rationale)
            onDismiss()
            return@LaunchedEffect
        }
        isLoading = true
        DownloadUtil.investigateCollection(url)
            .onSuccess { result ->
                investigation = result
                Downloader.updateCollectionInvestigation(result)
            }
            .onFailure {
                ToastUtil.makeToast(
                    it.message ?: context.getString(R.string.collection_investigation_failed),
                )
                onDismiss()
            }
        isLoading = false
    }

    CollectionSelectionContent(
        modifier = Modifier.fillMaxSize(),
        investigation = investigation,
        isLoading = isLoading,
        onDismiss = onDismiss,
        onDownloadSelected = { extractAudio, indexList ->
            investigation?.let { result ->
                startCollectionDownload(result, extractAudio, indexList)
                onDownloadStarted()
            }
        },
    )
}
