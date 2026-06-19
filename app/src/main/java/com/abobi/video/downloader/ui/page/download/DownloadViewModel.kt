@file:OptIn(ExperimentalMaterial3Api::class)

package com.abobi.video.downloader.ui.page.download

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abobi.video.downloader.Downloader
import com.abobi.video.downloader.Downloader.State
import com.abobi.video.downloader.Downloader.manageDownloadError
import com.abobi.video.downloader.Downloader.updateCollectionInvestigation
import com.abobi.video.downloader.R
import com.abobi.video.downloader.App.Companion.context
import com.abobi.video.downloader.util.CollectionInvestigationResult
import com.abobi.video.downloader.util.DownloadUtil
import com.abobi.video.downloader.util.PLAYLIST
import com.abobi.video.downloader.util.PlaylistResult
import com.abobi.video.downloader.util.PreferenceUtil.getBoolean
import com.abobi.video.downloader.util.ToastUtil
import com.abobi.video.downloader.util.UrlLinkDetector
import com.abobi.video.downloader.util.VideoInfo
import com.abobi.video.downloader.util.toInvestigationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalMaterial3Api::class)
class DownloadViewModel @Inject constructor() : ViewModel() {

    private val mutableViewStateFlow = MutableStateFlow(ViewState())
    val viewStateFlow = mutableViewStateFlow.asStateFlow()

    val videoInfoFlow = MutableStateFlow(VideoInfo())

    data class ViewState(
        val showPlaylistSelectionDialog: Boolean = false,
        val url: String = "",
        val isUrlSharingTriggered: Boolean = false,
        val isInvestigatingCollection: Boolean = false,
    )

    fun updateUrl(url: String, isUrlSharingTriggered: Boolean = false) =
        mutableViewStateFlow.update {
            it.copy(url = url, isUrlSharingTriggered = isUrlSharingTriggered)
        }

    fun startDownloadVideo() {
        val url = viewStateFlow.value.url
        Downloader.clearErrorState()
        if (!Downloader.isDownloaderAvailable()) return
        if (url.isBlank()) {
            ToastUtil.makeToast(context.getString(R.string.url_empty))
            return
        }
        when {
            UrlLinkDetector.isCollectionUrl(url) ->
                viewModelScope.launch(Dispatchers.IO) { investigateCollection(url) }

            PLAYLIST.getBoolean() ->
                viewModelScope.launch(Dispatchers.IO) { parsePlaylistInfo(url) }

            else -> Downloader.getInfoAndDownload(url)
        }
    }

    private suspend fun investigateCollection(url: String) {
        if (!Downloader.isDownloaderAvailable()) return
        Downloader.clearErrorState()
        mutableViewStateFlow.update { it.copy(isInvestigatingCollection = true) }
        Downloader.updateState(State.FetchingInfo)
        DownloadUtil.investigateCollection(url)
            .onSuccess { result -> showCollectionSelection(result) }
            .onFailure {
                manageDownloadError(
                    th = it,
                    url = url,
                    isFetchingInfo = true,
                    isTaskAborted = true,
                )
            }
        Downloader.updateState(State.Idle)
        mutableViewStateFlow.update { it.copy(isInvestigatingCollection = false) }
    }

    private fun parsePlaylistInfo(url: String): Unit =
        Downloader.run {
            if (!Downloader.isDownloaderAvailable()) return
            clearErrorState()
            updateState(State.FetchingInfo)
            DownloadUtil.getPlaylistOrVideoInfo(url).onSuccess { info ->
                updateState(State.Idle)
                when (info) {
                    is PlaylistResult -> {
                        val classification = UrlLinkDetector.classify(url)
                        showCollectionSelection(
                            info.toInvestigationResult(
                                sourceUrl = url,
                                kind = classification.kind,
                                platform = classification.platform,
                            )
                        )
                    }

                    is VideoInfo -> {
                        if (isDownloaderAvailable()) {
                            downloadVideoWithInfo(info = info)
                        }
                    }
                }
            }.onFailure {
                manageDownloadError(
                    th = it,
                    url = url,
                    isFetchingInfo = true,
                    isTaskAborted = true
                )
            }
        }

    private fun showCollectionSelection(result: CollectionInvestigationResult) {
        updateCollectionInvestigation(result)
        mutableViewStateFlow.update {
            it.copy(showPlaylistSelectionDialog = true)
        }
    }

    fun hidePlaylistDialog() {
        mutableViewStateFlow.update { it.copy(showPlaylistSelectionDialog = false) }
        updateCollectionInvestigation(null)
    }

    fun onShareIntentConsumed() {
        mutableViewStateFlow.update { it.copy(isUrlSharingTriggered = false) }
    }
}
