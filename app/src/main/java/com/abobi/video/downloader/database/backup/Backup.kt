package com.abobi.video.downloader.database.backup

import com.abobi.video.downloader.database.objects.CommandTemplate
import com.abobi.video.downloader.database.objects.DownloadedVideoInfo
import com.abobi.video.downloader.database.objects.OptionShortcut
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val templates: List<CommandTemplate>? = null,
    val shortcuts: List<OptionShortcut>? = null,
    val downloadHistory: List<DownloadedVideoInfo>? = null
)