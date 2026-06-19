package com.abobi.video.downloader.util

/**
 * Classifies pasted/shared URLs so the app can route single videos vs playlists/profiles.
 */
object UrlLinkDetector {

    enum class LinkKind {
        SINGLE_VIDEO,
        PLAYLIST,
        PROFILE,
    }

    data class LinkClassification(
        val kind: LinkKind,
        val platform: DownloadFallback.Platform,
    )

    private val INSTAGRAM_RESERVED = setOf(
        "p", "reel", "reels", "tv", "stories", "explore", "accounts", "direct",
        "about", "legal", "privacy", "terms", "developer", "nametag",
    )

    fun classify(url: String): LinkClassification {
        val trimmed = url.trim()
        val lower = trimmed.lowercase()
        val platform = DownloadFallback.detectPlatform(trimmed)
        return when (platform) {
            DownloadFallback.Platform.YOUTUBE ->
                if (isYoutubePlaylist(lower)) {
                    LinkClassification(LinkKind.PLAYLIST, platform)
                } else {
                    LinkClassification(LinkKind.SINGLE_VIDEO, platform)
                }

            DownloadFallback.Platform.TIKTOK ->
                if (isTiktokProfile(lower)) {
                    LinkClassification(LinkKind.PROFILE, platform)
                } else {
                    LinkClassification(LinkKind.SINGLE_VIDEO, platform)
                }

            DownloadFallback.Platform.INSTAGRAM ->
                if (isInstagramProfile(trimmed, lower)) {
                    LinkClassification(LinkKind.PROFILE, platform)
                } else {
                    LinkClassification(LinkKind.SINGLE_VIDEO, platform)
                }

            DownloadFallback.Platform.OTHER ->
                if (isYoutubePlaylist(lower)) {
                    LinkClassification(LinkKind.PLAYLIST, DownloadFallback.Platform.YOUTUBE)
                } else {
                    LinkClassification(LinkKind.SINGLE_VIDEO, platform)
                }
        }
    }

    fun isCollectionUrl(url: String): Boolean =
        classify(url).kind != LinkKind.SINGLE_VIDEO

    private fun isYoutubePlaylist(lower: String): Boolean =
        lower.contains("music.youtube.com/playlist") ||
            lower.contains("youtube.com/playlist") ||
            (lower.contains("list=") && (lower.contains("youtube.com") || lower.contains("youtu.be")))

    private fun isTiktokProfile(lower: String): Boolean {
        if ("/video/" in lower || "/photo/" in lower) return false
        if (Regex("tiktok\\.com/t/").containsMatchIn(lower)) return false
        if (Regex("tiktok\\.com/@[\\w.]+/?$").containsMatchIn(lower)) return true
        if (Regex("tiktok\\.com/@[\\w.]+/?\\?").containsMatchIn(lower)) return true
        if ("/user/" in lower && "/video/" !in lower) return true
        return false
    }

    private fun isInstagramProfile(original: String, lower: String): Boolean {
        if ("/p/" in lower || "/reel/" in lower || "/reels/" in lower || "/tv/" in lower) return false
        if ("/stories/" in lower || "/explore/" in lower || "/direct/" in lower) return false
        val pathMatch = Regex(
            pattern = "(?i)instagram\\.com/([A-Za-z0-9._]+)/?(?:\\?.*)?$",
        ).find(original.trimEnd('/')) ?: return false
        val segment = pathMatch.groupValues[1].lowercase()
        return segment !in INSTAGRAM_RESERVED
    }
}
