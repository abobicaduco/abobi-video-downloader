package com.abobi.video.downloader.util

/**
 * User-facing download error that preserves yt-dlp / strategy debug details separately.
 */
class DownloadDebugException(
    userMessage: String,
    val debugDetails: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause)
