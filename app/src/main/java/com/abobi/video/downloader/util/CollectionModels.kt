package com.abobi.video.downloader.util

import com.abobi.video.downloader.util.UrlLinkDetector.LinkKind

data class SelectableCollectionEntry(
    val index: Int,
    val entry: Entries,
    val isDownloadable: Boolean,
)

data class CollectionInvestigationResult(
    val sourceUrl: String,
    val kind: LinkKind,
    val platform: DownloadFallback.Platform,
    val playlist: PlaylistResult,
    val entries: List<SelectableCollectionEntry>,
) {
    val title: String? get() = playlist.title
    val downloadableCount: Int get() = entries.count { it.isDownloadable }
    val totalCount: Int get() = entries.size
}

private val UNAVAILABLE_AVAILABILITY = setOf(
    "private",
    "premium_only",
    "subscriber_only",
    "needs_auth",
    "unavailable",
    "deleted",
)

fun Entries.isLikelyDownloadable(): Boolean {
    availability?.lowercase()?.let { if (it in UNAVAILABLE_AVAILABILITY) return false }
    if (!url.isNullOrBlank()) return true
    if (!id.isNullOrBlank()) return true
    return false
}

fun PlaylistResult.toInvestigationResult(
    sourceUrl: String,
    kind: LinkKind,
    platform: DownloadFallback.Platform,
): CollectionInvestigationResult {
    val mapped = entries.orEmpty().mapIndexed { index, entry ->
        SelectableCollectionEntry(
            index = index + 1,
            entry = entry,
            isDownloadable = entry.isLikelyDownloadable(),
        )
    }
    return CollectionInvestigationResult(
        sourceUrl = sourceUrl,
        kind = kind,
        platform = platform,
        playlist = this,
        entries = mapped,
    )
}
