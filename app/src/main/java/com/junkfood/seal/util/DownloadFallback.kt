package com.junkfood.seal.util

import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.R
import com.junkfood.seal.util.FileUtil.getCookiesFile
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse

/**
 * Multi-strategy yt-dlp fallbacks for platforms that often fail on Android
 * (TikTok, Instagram, YouTube).
 */
object DownloadFallback {

    private const val TAG = "DownloadFallback"

    const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private const val TIKTOK_APP_UA =
        "com.zhiliaoapp.musically/2022600030 (Linux; U; Android 13; pt_BR; Pixel 7; Build/TQ3A.230805.001; Cronet/TTNetVersion:5f9640e3 2022-11-23 QuicVersion:0144d358 2022-05-05)"

    private const val DESKTOP_CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** yt-dlp `-f` fallback when format sorter fails. */
    const val YOUTUBE_FORMAT_FALLBACK = "bestvideo*+bestaudio/best"

    enum class Platform { TIKTOK, INSTAGRAM, YOUTUBE, OTHER }

    enum class Phase { FETCH_INFO, DOWNLOAD }

    data class Strategy(
        val name: String,
        val urlTransform: (String) -> String = { it },
        /** When true, base request must not apply `-S` format sorter (YouTube fallbacks). */
        val skipFormatSorter: Boolean = false,
        val forceFormat: String? = null,
        val apply: YoutubeDLRequest.(DownloadUtil.DownloadPreferences) -> Unit = {},
    )

    fun detectPlatform(url: String): Platform {
        val lower = url.lowercase()
        return when {
            "tiktok.com" in lower || "vt.tiktok.com" in lower -> Platform.TIKTOK
            "instagram.com" in lower -> Platform.INSTAGRAM
            "youtube.com" in lower || "youtu.be" in lower -> Platform.YOUTUBE
            else -> Platform.OTHER
        }
    }

    fun strategiesFor(platform: Platform, phase: Phase, preferences: DownloadUtil.DownloadPreferences): List<Strategy> =
        when (platform) {
            Platform.TIKTOK -> tiktokStrategies(preferences)
            Platform.INSTAGRAM -> instagramStrategies(preferences, phase)
            Platform.YOUTUBE -> youtubeStrategies(phase)
            Platform.OTHER -> listOf(Strategy("default"))
        }

    private fun tiktokStrategies(preferences: DownloadUtil.DownloadPreferences): List<Strategy> {
        val strategies = mutableListOf(
            Strategy("mobile-ua-sleep") { prefs ->
                applyUserAgent(this, MOBILE_UA, prefs)
                addOption("--sleep-requests", "1")
            },
            Strategy("tiktok-app-ua") { prefs ->
                applyUserAgent(this, TIKTOK_APP_UA, prefs)
                addOption("--sleep-requests", "1")
                addOption("--add-header", "Referer:https://www.tiktok.com/")
            },
            Strategy("tiktok-api-hostname") { prefs ->
                applyUserAgent(this, TIKTOK_APP_UA, prefs)
                addOption("--sleep-requests", "1")
                addOption("--extractor-args", "tiktok:api_hostname=api16-normal-c-useast1a.tiktokv.com")
            },
        )
        if (preferences.cookies || cookiesFileAvailable()) {
            strategies.add(
                Strategy("cookies") { prefs ->
                    applyUserAgent(this, TIKTOK_APP_UA, prefs)
                    addOption("--sleep-requests", "1")
                    enableCookiesForFallback(prefs)
                }
            )
        }
        strategies.add(
            Strategy("alt-mobile-ua") { prefs ->
                applyUserAgent(
                    this,
                    "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36",
                    prefs,
                )
                addOption("--sleep-requests", "2")
            }
        )
        strategies.add(
            Strategy("desktop-ua") { prefs ->
                applyUserAgent(this, DESKTOP_CHROME_UA, prefs)
                addOption("--sleep-requests", "1")
            }
        )
        return strategies
    }

    private fun instagramStrategies(
        preferences: DownloadUtil.DownloadPreferences,
        phase: Phase,
    ): List<Strategy> {
        val strategies = mutableListOf(
            Strategy("default"),
            Strategy("mobile-ua-sleep") { prefs ->
                applyUserAgent(this, MOBILE_UA, prefs)
                addOption("--sleep-requests", "1")
                addOption("--add-header", "Referer:https://www.instagram.com/")
            },
        )
        if (preferences.cookies || cookiesFileAvailable()) {
            strategies.add(
                Strategy("cookies") { prefs ->
                    applyUserAgent(this, MOBILE_UA, prefs)
                    enableCookiesForFallback(prefs)
                    addOption("--add-header", "Referer:https://www.instagram.com/")
                }
            )
        }
        strategies.add(
            Strategy("extractor-app") { prefs ->
                applyUserAgent(this, MOBILE_UA, prefs)
                addOption("--sleep-requests", "1")
                addOption("--extractor-args", "instagram:api=web")
            }
        )
        if (phase == Phase.FETCH_INFO || phase == Phase.DOWNLOAD) {
            strategies.add(
                Strategy("embed-url") { prefs ->
                    applyUserAgent(this, MOBILE_UA, prefs)
                    addOption("--sleep-requests", "1")
                }.let { base ->
                    base.copy(urlTransform = ::toInstagramEmbedUrl)
                }
            )
        }
        return strategies
    }

    private fun youtubeStrategies(phase: Phase): List<Strategy> = buildList {
        add(Strategy("default-sorter"))
        if (phase == Phase.DOWNLOAD) {
            add(
                Strategy(
                    name = "format-fallback",
                    skipFormatSorter = true,
                    forceFormat = YOUTUBE_FORMAT_FALLBACK,
                )
            )
        }
        add(
            Strategy(
                name = "android-client",
                skipFormatSorter = phase == Phase.DOWNLOAD,
                forceFormat = if (phase == Phase.DOWNLOAD) YOUTUBE_FORMAT_FALLBACK else null,
            ) {
                addOption("--extractor-args", "youtube:player_client=android")
            }
        )
        add(
            Strategy(
                name = "web-client",
                skipFormatSorter = phase == Phase.DOWNLOAD,
                forceFormat = if (phase == Phase.DOWNLOAD) YOUTUBE_FORMAT_FALLBACK else null,
            ) {
                addOption("--extractor-args", "youtube:player_client=web")
            }
        )
    }

    /**
     * Tries each [strategies] entry in order, rebuilding [buildRequest] every time.
     */
    fun executeWithFallbacks(
        url: String,
        platform: Platform,
        phase: Phase,
        preferences: DownloadUtil.DownloadPreferences,
        strategies: List<Strategy>,
        buildRequest: (effectiveUrl: String, strategy: Strategy) -> YoutubeDLRequest,
        processId: String? = null,
        progressCallback: ((Float, Long, String) -> Unit)? = null,
    ): Result<YoutubeDLResponse> {
        if (strategies.isEmpty() || platform == Platform.OTHER) {
            val defaultStrategy = Strategy("default")
            return runCatching {
                YoutubeDL.getInstance().execute(
                    buildRequest(url, defaultStrategy),
                    processId,
                    progressCallback,
                )
            }
        }

        var lastError: Throwable? = null
        for ((index, strategy) in strategies.withIndex()) {
            val effectiveUrl = strategy.urlTransform(url)
            val request = buildRequest(effectiveUrl, strategy).apply {
                strategy.apply(this, preferences)
                strategy.forceFormat?.let { addOption("-f", it) }
            }
            Log.d(TAG, "[$platform/$phase] strategy ${index + 1}/${strategies.size}: ${strategy.name} url=$effectiveUrl")
            request.buildCommand().forEach { Log.d(TAG, it) }
            val result = runCatching {
                YoutubeDL.getInstance().execute(request, processId, progressCallback)
            }
            result.onSuccess { response ->
                Log.d(TAG, "Strategy '${strategy.name}' succeeded")
                return Result.success(response)
            }.onFailure { error ->
                lastError = error
                Log.w(TAG, "Strategy '${strategy.name}' failed: ${error.message}")
            }
        }
        return Result.failure(
            enrichFailure(platform, phase, lastError ?: YoutubeDLException("All strategies failed")),
        )
    }

    fun enrichFailure(platform: Platform, phase: Phase, cause: Throwable): Throwable {
        val message = when (platform) {
            Platform.TIKTOK -> when (phase) {
                Phase.FETCH_INFO -> context.getString(R.string.fetch_info_error_tiktok)
                Phase.DOWNLOAD -> context.getString(R.string.download_error_tiktok)
            }
            Platform.INSTAGRAM -> when (phase) {
                Phase.FETCH_INFO -> context.getString(R.string.fetch_info_error_instagram)
                Phase.DOWNLOAD -> context.getString(R.string.download_error_instagram)
            }
            else -> cause.message ?: context.getString(
                if (phase == Phase.FETCH_INFO) R.string.fetch_info_error_msg
                else R.string.download_error_msg,
            )
        }
        return if (message == cause.message) cause
        else YoutubeDLException(message).apply { initCause(cause) }
    }

    private fun applyUserAgent(
        request: YoutubeDLRequest,
        ua: String,
        preferences: DownloadUtil.DownloadPreferences,
    ) {
        if (preferences.userAgentString.isEmpty()) {
            request.addOption("--add-header", "User-Agent:$ua")
        }
    }

    private fun YoutubeDLRequest.enableCookiesForFallback(
        preferences: DownloadUtil.DownloadPreferences,
    ) {
        if (cookiesFileAvailable()) {
            addOption("--cookies", context.getCookiesFile().absolutePath)
            if (preferences.userAgentString.isNotEmpty()) {
                addOption("--add-header", "User-Agent:${preferences.userAgentString}")
            }
        }
    }

    private fun cookiesFileAvailable(): Boolean {
        val file = context.getCookiesFile()
        return file.exists() && file.length() > 50
    }

    /** Convert /reel/ID or /p/ID URLs to embed endpoints. */
    fun toInstagramEmbedUrl(url: String): String {
        val trimmed = url.trimEnd('/')
        if (trimmed.endsWith("/embed")) return url
        val reelMatch = Regex("instagram\\.com/reel/([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
            .find(url)
        if (reelMatch != null) {
            return "https://www.instagram.com/reel/${reelMatch.groupValues[1]}/embed/"
        }
        val postMatch = Regex("instagram\\.com/p/([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
            .find(url)
        if (postMatch != null) {
            return "https://www.instagram.com/p/${postMatch.groupValues[1]}/embed/captioned/"
        }
        return url
    }
}
