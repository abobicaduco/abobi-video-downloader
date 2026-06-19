package com.abobi.video.downloader.ui.page.download

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abobi.video.downloader.Downloader

@Composable
fun PlaylistSelectionPage(onNavigateBack: () -> Unit = {}) {
    val investigation by Downloader.collectionInvestigation.collectAsStateWithLifecycle()

    CollectionSelectionContent(
        modifier = Modifier.fillMaxSize(),
        investigation = investigation,
        isLoading = false,
        onDismiss = onNavigateBack,
        onDownloadSelected = { extractAudio, indexList ->
            investigation?.let { result ->
                startCollectionDownload(result, extractAudio, indexList)
            }
        },
    )
}
