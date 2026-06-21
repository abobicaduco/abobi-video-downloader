package com.abobi.video.downloader.util

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.media.MediaScannerConnection
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.CheckResult
import com.abobi.video.downloader.App
import com.abobi.video.downloader.App.Companion.audioDownloadDir
import com.abobi.video.downloader.App.Companion.context
import com.abobi.video.downloader.App.Companion.videoDownloadDir
import com.abobi.video.downloader.Downloader
import com.abobi.video.downloader.Downloader.onProcessEnded
import com.abobi.video.downloader.Downloader.onProcessStarted
import com.abobi.video.downloader.Downloader.onTaskEnded
import com.abobi.video.downloader.Downloader.onTaskError
import com.abobi.video.downloader.Downloader.onTaskStarted
import com.abobi.video.downloader.Downloader.toNotificationId
import com.abobi.video.downloader.R
import com.abobi.video.downloader.database.objects.CommandTemplate
import com.abobi.video.downloader.database.objects.DownloadedVideoInfo
import com.abobi.video.downloader.ui.page.settings.network.Cookie
import com.abobi.video.downloader.util.FileUtil.getArchiveFile
import com.abobi.video.downloader.util.FileUtil.getConfigFile
import com.abobi.video.downloader.util.FileUtil.getCookiesFile
import com.abobi.video.downloader.util.FileUtil.getExternalTempDir
import com.abobi.video.downloader.util.FileUtil.getFileName
import com.abobi.video.downloader.util.FileUtil.getSdcardTempDir
import com.abobi.video.downloader.util.FileUtil.moveFilesToSdcard
import com.abobi.video.downloader.util.PreferenceUtil.COOKIE_HEADER
import com.abobi.video.downloader.util.PreferenceUtil.getBoolean
import com.abobi.video.downloader.util.PreferenceUtil.getInt
import com.abobi.video.downloader.util.PreferenceUtil.getString
import com.abobi.video.downloader.util.PreferenceUtil.updateBoolean
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale


object DownloadUtil {

    object CookieScheme {
        const val NAME = "name"
        const val VALUE = "value"
        const val SECURE = "is_secure"
        const val EXPIRY = "expires_utc"
        const val HOST = "host_key"
        const val PATH = "path"
    }

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
    }

    private const val TAG = "DownloadUtil"

  /** Best available quality, never exceeding 4K @ 60fps (yt-dlp `-S` fields). */
    const val MAX_VIDEO_QUALITY_SORTER = "res:2160,fps:60"

    /** Matches [StoragePermissionHelper.MAX_TITLE_FILENAME_BYTES] for yt-dlp output template. */
    const val BASENAME = "%(title).245B"

    const val EXTENSION = ".%(ext)s"

    private const val ID = "[%(id)s]"

    private const val CLIP_TIMESTAMP = "%(section_start)d-%(section_end)d"

    const val OUTPUT_TEMPLATE_DEFAULT = BASENAME + EXTENSION

    /** Max parallel fragments when aria2c is off — no user-facing cap. */
    private const val MAX_CONCURRENT_FRAGMENTS = 24

    const val OUTPUT_TEMPLATE_ID = "$BASENAME $ID$EXTENSION"

    private const val OUTPUT_TEMPLATE_CLIPS = "$BASENAME [$CLIP_TIMESTAMP]$EXTENSION"

    private const val OUTPUT_TEMPLATE_CHAPTERS =
        "chapter:$BASENAME/%(section_number)d - %(section_title).200B$EXTENSION"

    private const val OUTPUT_TEMPLATE_SPLIT = "$BASENAME/$OUTPUT_TEMPLATE_DEFAULT"

    private const val PLAYLIST_TITLE_SUBDIRECTORY_PREFIX = "%(playlist)s/"

    private const val CROP_ARTWORK_COMMAND =
        """--ppa "ffmpeg: -c:v mjpeg -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"""""


    @CheckResult
    fun getPlaylistOrVideoInfo(
        playlistURL: String, downloadPreferences: DownloadPreferences = DownloadPreferences()
    ): Result<YoutubeDLInfo> = YoutubeDL.runCatching {
        val preferences = downloadPreferences.withAbobiFixedFormats()
        ToastUtil.makeToastSuspend(context.getString(R.string.fetching_playlist_info))
        val platform = DownloadFallback.detectPlatform(playlistURL)
        val strategies = DownloadFallback.strategiesFor(
            platform, DownloadFallback.Phase.FETCH_INFO, preferences, playlistURL,
        )
        DownloadFallback.executeWithFallbacks(
            url = playlistURL,
            platform = platform,
            phase = DownloadFallback.Phase.FETCH_INFO,
            preferences = preferences,
            strategies = strategies,
            buildRequest = { effectiveUrl, strategy ->
                buildPlaylistFetchRequest(
                    url = effectiveUrl,
                    preferences = preferences,
                    skipFormatSorter = strategy.skipFormatSorter,
                )
            },
        ).getOrThrow().out.run {
            val playlistInfo = jsonFormat.decodeFromString<PlaylistResult>(this)
            if (playlistInfo.type != "playlist") {
                jsonFormat.decodeFromString<VideoInfo>(this)
            } else playlistInfo
        }
    }


    @CheckResult
    suspend fun investigateCollection(
        url: String,
        downloadPreferences: DownloadPreferences = DownloadPreferences(),
    ): Result<CollectionInvestigationResult> = withContext(Dispatchers.IO) {
        val classification = UrlLinkDetector.classify(url)
        val toastRes = when (classification.kind) {
            UrlLinkDetector.LinkKind.PROFILE -> R.string.fetching_profile_info
            UrlLinkDetector.LinkKind.PLAYLIST -> R.string.fetching_playlist_info
            UrlLinkDetector.LinkKind.SINGLE_VIDEO -> R.string.fetching_playlist_info
        }
        ToastUtil.makeToastSuspend(context.getString(toastRes))
        getPlaylistOrVideoInfo(url, downloadPreferences).mapCatching { info ->
            when (info) {
                is PlaylistResult -> {
                    val entries = info.entries.orEmpty()
                    if (entries.isEmpty()) {
                        throw YoutubeDLException(
                            context.getString(R.string.collection_investigation_empty),
                        )
                    }
                    info.toInvestigationResult(
                        sourceUrl = url,
                        kind = classification.kind,
                        platform = classification.platform,
                    )
                }

                is VideoInfo -> throw YoutubeDLException(
                    context.getString(R.string.collection_investigation_not_collection),
                )
            }
        }
    }


    @CheckResult
    private fun buildPlaylistFetchRequest(
        url: String,
        preferences: DownloadPreferences,
        skipFormatSorter: Boolean = false,
    ): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        addOption("--flat-playlist")
        addOption("--dump-single-json")
        addOption("-o", BASENAME)
        addOption("-R", "1")
        addOption("--socket-timeout", "5")
        if (preferences.extractAudio) {
            addOption("-x")
        }
        if (!skipFormatSorter) {
            applyFormatSorter(preferences, preferences.toFormatSorter())
        }
        if (preferences.proxy) {
            enableProxy(preferences.proxyUrl)
        }
    }


    @CheckResult
    private fun buildFetchInfoRequest(
        url: String,
        preferences: DownloadPreferences,
        playlistItem: Int = 0,
        skipFormatSorter: Boolean = false,
    ): YoutubeDLRequest = YoutubeDLRequest(url).apply {
        addOption("-o", BASENAME)
        if (preferences.extractAudio) {
            addOption("-x")
        }
        if (!skipFormatSorter) {
            applyFormatSorter(preferences, preferences.toFormatSorter())
        }
        if (preferences.proxy) {
            enableProxy(preferences.proxyUrl)
        }
        if (preferences.autoSubtitle) {
            addOption("--write-auto-subs")
            if (!preferences.autoTranslatedSubtitles) {
                addOption("--extractor-args", "youtube:skip=translated_subs")
            }
        }
        if (playlistItem != 0) {
            addOption("--playlist-items", playlistItem)
            addOption("--dump-json")
        } else {
            addOption("--dump-single-json")
        }
        addOption("-R", "1")
        addOption("--no-playlist")
        addOption("--socket-timeout", "5")
    }


    @CheckResult
    private fun executeFetchInfo(
        url: String,
        preferences: DownloadPreferences,
        playlistItem: Int = 0,
    ): Result<VideoInfo> {
        val normalizedUrl = CookieHelper.normalizeDownloadUrl(url)
        if (DownloadFallback.detectPlatform(normalizedUrl) == DownloadFallback.Platform.INSTAGRAM) {
            CookieHelper.refreshCookiesForSite(normalizedUrl)
                .onFailure { Log.w(TAG, "Instagram cookie refresh failed: ${it.message}") }
        }
        val platform = DownloadFallback.detectPlatform(normalizedUrl)
        val strategies = DownloadFallback.strategiesFor(
            platform, DownloadFallback.Phase.FETCH_INFO, preferences, normalizedUrl,
        )
        val ytdlpResult = DownloadFallback.executeWithFallbacks(
            url = normalizedUrl,
            platform = platform,
            phase = DownloadFallback.Phase.FETCH_INFO,
            preferences = preferences,
            strategies = strategies,
            buildRequest = { effectiveUrl, strategy ->
                buildFetchInfoRequest(
                    url = effectiveUrl,
                    preferences = preferences,
                    playlistItem = playlistItem,
                    skipFormatSorter = strategy.skipFormatSorter,
                )
            },
        ).mapCatching { response ->
            jsonFormat.decodeFromString<VideoInfo>(response.out)
        }
        if (ytdlpResult.isSuccess) return ytdlpResult
        return when (platform) {
            DownloadFallback.Platform.TIKTOK ->
                PlatformAlternativeDownloader.fetchTikTok(url, preferences.extractAudio)
                    .map { PlatformAlternativeDownloader.toVideoInfo(it, preferences.extractAudio) }
                    .onFailure { Log.w(TAG, "TikTok mirror fallback failed: ${it.message}") }
                    .recoverCatching { ytdlpResult.getOrThrow() }
            DownloadFallback.Platform.INSTAGRAM ->
                PlatformAlternativeDownloader.fetchInstagram(normalizedUrl, preferences.extractAudio)
                    .map { PlatformAlternativeDownloader.toVideoInfo(it, preferences.extractAudio) }
                    .onFailure { Log.w(TAG, "Instagram mirror fallback failed: ${it.message}") }
                    .recoverCatching { ytdlpResult.getOrThrow() }
            else -> ytdlpResult
        }
    }


    @CheckResult
    fun fetchVideoInfoFromUrl(
        url: String, playlistItem: Int = 0,
        preferences: DownloadPreferences = DownloadPreferences()
    ): Result<VideoInfo> = with(preferences.withAbobiFixedFormats()) {
        executeFetchInfo(url = url, preferences = this, playlistItem = playlistItem)
    }

    data class DownloadPreferences(
        val extractAudio: Boolean = PreferenceUtil.getValue(EXTRACT_AUDIO),
        val createThumbnail: Boolean = PreferenceUtil.getValue(THUMBNAIL),
        val downloadPlaylist: Boolean = PreferenceUtil.getValue(PLAYLIST),
        val subdirectoryExtractor: Boolean = false,
        val subdirectoryPlaylistTitle: Boolean = false,
        val commandDirectory: String = COMMAND_DIRECTORY.getString(),
        val downloadSubtitle: Boolean = PreferenceUtil.getValue(SUBTITLE),
        val embedSubtitle: Boolean = EMBED_SUBTITLE.getBoolean(),
        val keepSubtitle: Boolean = KEEP_SUBTITLE_FILES.getBoolean(),
        val subtitleLanguage: String = SUBTITLE_LANGUAGE.getString(),
        val autoSubtitle: Boolean = PreferenceUtil.getValue(AUTO_SUBTITLE),
        val autoTranslatedSubtitles: Boolean = AUTO_TRANSLATED_SUBTITLES.getBoolean(),
        val convertSubtitle: Int = CONVERT_SUBTITLE.getInt(),
        val concurrentFragments: Int = MAX_CONCURRENT_FRAGMENTS,
        val sponsorBlock: Boolean = PreferenceUtil.getValue(SPONSORBLOCK),
        val sponsorBlockCategory: String = PreferenceUtil.getSponsorBlockCategories(),
        val cookies: Boolean = CookieHelper.cookiesFileAvailable(),
        val aria2c: Boolean = PreferenceUtil.getValue(ARIA2C),
        val audioFormat: Int = M4A,
        val audioQuality: Int = NOT_SPECIFIED,
        val convertAudio: Boolean = false,
        val formatSorting: Boolean = false,
        val sortingFields: String = "",
        val audioConvertFormat: Int = CONVERT_M4A,
        val videoFormat: Int = FORMAT_QUALITY,
        val formatIdString: String = "",
        val videoResolution: Int = NOT_SPECIFIED,
        val privateMode: Boolean = PreferenceUtil.getValue(PRIVATE_MODE),
        val rateLimit: Boolean = false,
        val maxDownloadRate: String = "",
        val privateDirectory: Boolean = false,
        val cropArtwork: Boolean = PreferenceUtil.getValue(CROP_ARTWORK),
        val sdcard: Boolean = false,
        val sdcardUri: String = SDCARD_URI.getString(),
        val embedThumbnail: Boolean = EMBED_THUMBNAIL.getBoolean(),
        val videoClips: List<VideoClip> = emptyList(),
        val splitByChapter: Boolean = false,
        val debug: Boolean = DEBUG.getBoolean(),
        val proxy: Boolean = false,
        val proxyUrl: String = "",
        val newTitle: String = "",
        val userAgentString: String = USER_AGENT_STRING.run {
            if (USER_AGENT.getBoolean()) getString() else ""
        },
        val outputTemplate: String = OUTPUT_TEMPLATE_DEFAULT,
        val useDownloadArchive: Boolean = false,
        val embedMetadata: Boolean = EMBED_METADATA.getBoolean(),
        val supportAv1HardwareDecoding: Boolean = checkIfAv1HardwareAccelerated(),
        val mergeAudioStream: Boolean = false,
        val mergeToMkv: Boolean = (downloadSubtitle && embedSubtitle) || MERGE_OUTPUT_MKV.getBoolean(),
    ) {
        fun withAbobiFixedFormats(): DownloadPreferences = copy(
            audioFormat = M4A,
            audioConvertFormat = CONVERT_M4A,
            convertAudio = extractAudio,
            videoFormat = FORMAT_QUALITY,
            formatSorting = false,
            sortingFields = "",
            audioQuality = NOT_SPECIFIED,
            videoResolution = NOT_SPECIFIED,
            rateLimit = false,
            maxDownloadRate = "",
            concurrentFragments = MAX_CONCURRENT_FRAGMENTS,
            proxy = false,
            proxyUrl = "",
        )

        companion object {
            fun forQuickDownload(extractAudio: Boolean): DownloadPreferences =
                DownloadPreferences(extractAudio = extractAudio).withAbobiFixedFormats().copy(
                    createThumbnail = false,
                    debug = false,
                    mergeToMkv = false,
                )
        }
    }

    private fun YoutubeDLRequest.enableCookies(
        url: String,
        userAgentString: String,
    ): YoutubeDLRequest {
        val cookiesFile = CookieHelper.cookiesFileForUrl(url) ?: context.getCookiesFile()
        return this.addOption("--cookies", cookiesFile.absolutePath).apply {
            val ua = userAgentString.ifEmpty { DownloadFallback.MOBILE_UA }
            addOption("--add-header", "User-Agent:$ua")
        }
    }

    private fun YoutubeDLRequest.enableProxy(proxyUrl: String): YoutubeDLRequest =
        this.addOption("--proxy", proxyUrl)

    private fun YoutubeDLRequest.useDownloadArchive(): YoutubeDLRequest =
        this.addOption("--download-archive", context.getArchiveFile().absolutePath)


    @CheckResult
    fun getCookieListFromDatabase(): Result<List<Cookie>> = runCatching {
        CookieManager.getInstance().run {
            if (!hasCookies()) throw Exception("There is no cookies in the database!")
            flush()
        }
        SQLiteDatabase.openDatabase(
            "/data/data/${context.packageName}/app_webview/Default/Cookies", null, OPEN_READONLY
        ).run {
            val projection = arrayOf(
                CookieScheme.HOST,
                CookieScheme.EXPIRY,
                CookieScheme.PATH,
                CookieScheme.NAME,
                CookieScheme.VALUE,
                CookieScheme.SECURE
            )
            val cookieList = mutableListOf<Cookie>()
            query(
                "cookies", projection, null, null, null, null, null
            ).run {
                while (moveToNext()) {
                    val expiry = getLong(getColumnIndexOrThrow(CookieScheme.EXPIRY))
                    val name = getString(getColumnIndexOrThrow(CookieScheme.NAME))
                    val value = getString(getColumnIndexOrThrow(CookieScheme.VALUE))
                    val path = getString(getColumnIndexOrThrow(CookieScheme.PATH))
                    val secure = getLong(getColumnIndexOrThrow(CookieScheme.SECURE)) == 1L
                    val hostKey = getString(getColumnIndexOrThrow(CookieScheme.HOST))

                    val host = if (hostKey[0] != '.') ".$hostKey" else hostKey
                    cookieList.add(
                        Cookie(
                            domain = host,
                            name = name,
                            value = value,
                            path = path,
                            secure = secure,
                            expiry = expiry
                        )
                    )
                }
                close()
            }
            close()
            cookieList
        }
    }

    fun List<Cookie>.toCookiesFileContent(): String =
        this.fold(StringBuilder(COOKIE_HEADER)) { acc, cookie ->
            acc.append(cookie.toNetscapeCookieString()).append("\n")
        }.toString()

    fun getCookiesContentFromDatabase(): Result<String> = getCookieListFromDatabase().mapCatching {
        it.toCookiesFileContent()
    }

    private fun YoutubeDLRequest.enableAria2c(): YoutubeDLRequest =
        this.addOption("--downloader", "libaria2c.so")
            .addOption("--external-downloader-args", "aria2c:\"--summary-interval=1\"")

    private fun executeDownloadWithAria2cFallback(
        url: String,
        platform: DownloadFallback.Platform,
        downloadPreferences: DownloadPreferences,
        strategies: List<DownloadFallback.Strategy>,
        buildRequest: (effectiveUrl: String, strategy: DownloadFallback.Strategy) -> YoutubeDLRequest,
        processId: String,
        progressCallback: ((Float, Long, String) -> Unit)?,
    ): Result<YoutubeDLResponse> {
        val firstAttempt = DownloadFallback.executeWithFallbacks(
            url = url,
            platform = platform,
            phase = DownloadFallback.Phase.DOWNLOAD,
            preferences = downloadPreferences,
            strategies = strategies,
            buildRequest = buildRequest,
            processId = processId,
            progressCallback = progressCallback,
        )
        if (firstAttempt.isSuccess || !downloadPreferences.aria2c) {
            return firstAttempt
        }
        Log.w(
            TAG,
            "aria2c download failed, retrying with native yt-dlp downloader: ${firstAttempt.exceptionOrNull()?.message}",
        )
        return DownloadFallback.executeWithFallbacks(
            url = url,
            platform = platform,
            phase = DownloadFallback.Phase.DOWNLOAD,
            preferences = downloadPreferences.copy(aria2c = false),
            strategies = strategies,
            buildRequest = buildRequest,
            processId = processId,
            progressCallback = progressCallback,
        )
    }

    private fun YoutubeDLRequest.addOptionsForVideoDownloads(
        downloadPreferences: DownloadPreferences,
        skipFormatSorter: Boolean = false,
    ): YoutubeDLRequest = this.apply {
        downloadPreferences.run {
            addOption("--add-metadata")
            addOption("--no-embed-info-json")
            if (formatIdString.isNotEmpty()) {
                addOption("-f", formatIdString)
                if (mergeAudioStream) {
                    addOption("--audio-multistreams")
                }
            } else if (!skipFormatSorter) {
                applyFormatSorter(this, toFormatSorter())
            }
            if (downloadSubtitle) {
                if (autoSubtitle) {
                    addOption("--write-auto-subs")
                    if (!autoTranslatedSubtitles) {
                        addOption("--extractor-args", "youtube:skip=translated_subs")
                    }
                }
                subtitleLanguage.takeIf { it.isNotEmpty() }?.let { addOption("--sub-langs", it) }
                if (embedSubtitle) {
                    addOption("--embed-subs")
                    if (keepSubtitle) {
                        addOption("--write-subs")
                    }
                } else {
                    addOption("--write-subs")
                }
                when (convertSubtitle) {
                    CONVERT_ASS -> addOption("--convert-subs", "ass")
                    CONVERT_SRT -> addOption("--convert-subs", "srt")
                    CONVERT_VTT -> addOption("--convert-subs", "vtt")
                    CONVERT_LRC -> addOption("--convert-subs", "lrc")
                    else -> {}
                }
            }
            if (mergeToMkv) {
                addOption("--remux-video", "mkv")
                addOption("--merge-output-format", "mkv")
            } else {
                addOption("--remux-video", "mp4")
                addOption("--merge-output-format", "mp4")
            }
            if (embedThumbnail) {
                addOption("--embed-thumbnail")
            }
            if (videoClips.isEmpty()) addOption("--embed-chapters")
        }
    }


    @CheckResult
    private fun DownloadPreferences.toAudioFormatSorter(): String = this.run {
        val format = when (audioFormat) {
            M4A -> "acodec:aac"
            OPUS -> "acodec:opus"
            else -> ""
        }
        // Always pick the best available audio — no manual bitrate cap.
        return@run connectWithDelimiter(format, "", delimiter = ",")
    }

    @CheckResult
    private fun DownloadPreferences.toVideoFormatSorter(): String = this.run {
        val format = when (videoFormat) {
            FORMAT_COMPATIBILITY -> "proto,vcodec:h264,ext"
            FORMAT_QUALITY -> if (supportAv1HardwareDecoding) {
                "vcodec:av01"
            } else {
                "vcodec:vp9.2"
            }

            else -> ""
        }
        // Always pick the best available stream within the 2160p / 60fps ceiling.
        return@run connectWithDelimiter(format, MAX_VIDEO_QUALITY_SORTER, delimiter = ",")
    }

    private fun YoutubeDLRequest.applyFormatSorter(
        preferences: DownloadPreferences, sorter: String
    ) = preferences.run {
        if (formatSorting && sortingFields.isNotEmpty()) addOption("-S", sortingFields)
        else if (sorter.isNotEmpty()) addOption("-S", sorter) else {
        }
    }

    @CheckResult
    fun DownloadPreferences.toFormatSorter(): String = connectWithDelimiter(
        this.toVideoFormatSorter(), this.toAudioFormatSorter(), delimiter = ","
    )

    private fun YoutubeDLRequest.addOptionsForAudioDownloads(
        id: String, preferences: DownloadPreferences, playlistUrl: String
    ): YoutubeDLRequest = this.apply {
        with(preferences) {
            addOption("-x")
            if (downloadSubtitle) {
                addOption("--write-subs")

                if (autoSubtitle) {
                    addOption("--write-auto-subs")
                    if (!autoTranslatedSubtitles) {
                        addOption("--extractor-args", "youtube:skip=translated_subs")
                    }
                }
                subtitleLanguage.takeIf { it.isNotEmpty() }?.let { addOption("--sub-langs", it) }
                when (convertSubtitle) {
                    CONVERT_ASS -> addOption("--convert-subs", "ass")
                    CONVERT_SRT -> addOption("--convert-subs", "srt")
                    CONVERT_VTT -> addOption("--convert-subs", "vtt")
                    CONVERT_LRC -> addOption("--convert-subs", "lrc")
                    else -> {}
                }
            }
            if (formatIdString.isNotEmpty()) {
                addOption("-f", formatIdString)
                if (mergeAudioStream) {
                    addOption("--audio-multistreams")
                }
            } else if (convertAudio) {
                when (audioConvertFormat) {
                    CONVERT_MP3 -> {
                        addOption("--audio-format", "mp3")
                    }

                    CONVERT_M4A -> {
                        addOption("--audio-format", "m4a")
                    }
                }
            } else {
                applyFormatSorter(preferences, toAudioFormatSorter())
            }

            if (embedMetadata) {
                addOption("--embed-metadata")
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")

                if (cropArtwork) {
                    val configFile = context.getConfigFile(id)
                    FileUtil.writeContentToFile(CROP_ARTWORK_COMMAND, configFile)
                    addOption("--config", configFile.absolutePath)
                }
            }
            addOption("--parse-metadata", "%(release_year,upload_date)s:%(meta_date)s")

            if (playlistUrl.isNotEmpty()) {
                addOption("--parse-metadata", "%(album,playlist,title)s:%(meta_album)s")
                addOption(
                    "--parse-metadata", "%(track_number,playlist_index)d:%(meta_track)s"
                )
            } else {
                addOption("--parse-metadata", "%(album,title)s:%(meta_album)s")
            }
        }


    }

    private fun insertInfoIntoDownloadHistory(
        videoInfo: VideoInfo, filePaths: List<String>
    ): List<String> = filePaths.onEach {
        DatabaseUtil.insertInfo(videoInfo.toDownloadedVideoInfo(videoPath = it))
    }

    private fun VideoInfo.toDownloadedVideoInfo(
        id: Int = 0, videoPath: String
    ): DownloadedVideoInfo = this.run {
        DownloadedVideoInfo(
            id = id,
            videoTitle = title,
            videoAuthor = uploader ?: channel ?: uploaderId.toString(),
            videoUrl = webpageUrl ?: originalUrl.toString(),
            thumbnailUrl = thumbnail.toHttpsUrl(),
            videoPath = videoPath,
            extractor = extractorKey
        )
    }

    private fun insertSplitChapterIntoHistory(videoInfo: VideoInfo, filePaths: List<String>) =
        filePaths.onEach {
            DatabaseUtil.insertInfo(
                videoInfo.toDownloadedVideoInfo(videoPath = it).copy(videoTitle = it.getFileName())
            )
        }

    @CheckResult
    fun downloadVideo(
        videoInfo: VideoInfo? = null,
        playlistUrl: String = "",
        playlistItem: Int = 0,
        taskId: String,
        downloadPreferences: DownloadPreferences,
        progressCallback: ((Float, Long, String) -> Unit)?
    ): Result<List<String>> {
        if (videoInfo == null) return Result.failure(Throwable(context.getString(R.string.fetch_info_error_msg)))

        val downloadPreferences = downloadPreferences.withAbobiFixedFormats()
        with(downloadPreferences) {
            if (PlatformAlternativeDownloader.isAlternativeVideoInfo(videoInfo)) {
                val mirrorDownloadPath = buildString {
                    if (extractAudio || (videoInfo.vcodec == "none")) {
                        append(if (privateDirectory) App.privateDownloadDir else audioDownloadDir)
                    } else {
                        append(if (privateDirectory) App.privateDownloadDir else videoDownloadDir)
                    }
                    if (subdirectoryExtractor) append("/${videoInfo.extractorKey}")
                }
                return PlatformAlternativeDownloader.downloadFromVideoInfo(
                    videoInfo = videoInfo,
                    downloadPath = mirrorDownloadPath,
                    preferences = downloadPreferences,
                    progressCallback = progressCallback,
                )
            }

            val url = CookieHelper.normalizeDownloadUrl(
                playlistUrl.ifEmpty {
                    videoInfo.originalUrl ?: videoInfo.webpageUrl ?: return Result.failure(
                        Throwable(
                            context.getString(R.string.fetch_info_error_msg)
                        )
                    )
                },
            )
            if (useDownloadArchive) {
                val archiveFile = context.getArchiveFile()
                val archiveFileContent = archiveFile.readText()
                if (archiveFileContent.contains("${videoInfo.extractor} ${videoInfo.id}")) {
                    return Result.failure(YoutubeDLException(context.getString(R.string.download_archive_error)))
                }
            }

            val downloadPath = buildString {
                if (extractAudio || (videoInfo.vcodec == "none")) {
                    append(if (privateDirectory) App.privateDownloadDir else audioDownloadDir)
                } else {
                    append(if (privateDirectory) App.privateDownloadDir else videoDownloadDir)
                }
                if (subdirectoryExtractor) append("/${videoInfo.extractorKey}")
            }

            // Android 16 (API 36)+: FUSE blocks rename() for FFmpeg merge output files in shared
            // storage. Route yt-dlp output to app-private cache where all file ops work freely,
            // then copy finished files to the public Downloads destination.
            val privateTmpDir: File? = if (Build.VERSION.SDK_INT >= 36 && !sdcard && !privateDirectory) {
                (context.getExternalCacheDir() ?: context.cacheDir)
                    .resolve("ytdlp/${videoInfo.id}")
                    .apply { mkdirs() }
            } else null

            val outputPathPrefix = buildString {
                if (playlistItem != 0 && downloadPlaylist && subdirectoryPlaylistTitle && !videoInfo.playlist.isNullOrEmpty()) {
                    append(PLAYLIST_TITLE_SUBDIRECTORY_PREFIX)
                }
            }

            val outputTemplateResolved =
                if (splitByChapter) {
                    OUTPUT_TEMPLATE_SPLIT
                } else if (videoClips.isEmpty()) {
                    outputTemplate
                } else {
                    OUTPUT_TEMPLATE_CLIPS
                }

            fun buildDownloadRequest(
                effectiveUrl: String,
                strategy: DownloadFallback.Strategy,
            ): YoutubeDLRequest = YoutubeDLRequest(effectiveUrl).apply {
                addOption("--no-mtime")
                if (proxy) {
                    enableProxy(proxyUrl)
                }
                if (debug) {
                    addOption("-v")
                }
                if (useDownloadArchive) {
                    useDownloadArchive()
                }
                if (rateLimit && maxDownloadRate.isNumberInRange(1, 1000000)) {
                    addOption("-r", "${maxDownloadRate}K")
                }
                if (playlistItem != 0 && downloadPlaylist) {
                    addOption("--playlist-items", playlistItem)
                } else {
                    addOption("--no-playlist")
                }
                if (aria2c) {
                    enableAria2c()
                } else if (concurrentFragments > 1) {
                    addOption("--concurrent-fragments", concurrentFragments)
                }
                if (extractAudio || (videoInfo.vcodec == "none")) {
                    addOptionsForAudioDownloads(
                        id = videoInfo.id,
                        preferences = downloadPreferences,
                        playlistUrl = playlistUrl,
                    )
                } else {
                    addOptionsForVideoDownloads(
                        downloadPreferences,
                        skipFormatSorter = strategy.skipFormatSorter,
                    )
                }
                if (sponsorBlock) {
                    addOption("--sponsorblock-remove", sponsorBlockCategory)
                }
                if (createThumbnail) {
                    addOption("--write-thumbnail")
                    addOption("--convert-thumbnails", "png")
                }
                if (sdcard) {
                    addOption("-P", context.getSdcardTempDir(videoInfo.id).absolutePath)
                } else {
                    addOption("-P", privateTmpDir?.absolutePath ?: downloadPath)
                }
                videoClips.forEach {
                    addOption(
                        "--download-sections",
                        "*%d-%d".format(locale = Locale.US, it.start, it.end),
                    )
                }
                if (newTitle.isNotEmpty()) {
                    addCommands(listOf("--replace-in-metadata", "title", ".+", newTitle))
                }
                if (Build.VERSION.SDK_INT > 23 && !sdcard && privateTmpDir == null) {
                    addOption("-P", "temp:" + getExternalTempDir())
                }
                if (splitByChapter) {
                    addOption("-o", OUTPUT_TEMPLATE_CHAPTERS)
                    addOption("--split-chapters")
                }
                addOption("-o", outputPathPrefix + outputTemplateResolved)
            }

            val platform = DownloadFallback.detectPlatform(url)
            if (platform == DownloadFallback.Platform.INSTAGRAM) {
                CookieHelper.refreshCookiesForSite(url)
                    .onFailure { Log.w(TAG, "Instagram cookie refresh failed: ${it.message}") }
            }
            val strategies = DownloadFallback.strategiesFor(
                platform, DownloadFallback.Phase.DOWNLOAD, downloadPreferences, url,
            )

            val ytdlpResult = executeDownloadWithAria2cFallback(
                url = url,
                platform = platform,
                downloadPreferences = downloadPreferences,
                strategies = strategies,
                buildRequest = ::buildDownloadRequest,
                processId = taskId,
                progressCallback = progressCallback,
            )
            if (ytdlpResult.isFailure && platform == DownloadFallback.Platform.INSTAGRAM && !extractAudio) {
                Log.w(
                    TAG,
                    "Instagram yt-dlp download failed, trying mirror: ${ytdlpResult.exceptionOrNull()?.message}",
                )
                val mirrorPath = buildString {
                    append(if (privateDirectory) App.privateDownloadDir else videoDownloadDir)
                    if (subdirectoryExtractor) append("/${videoInfo.extractorKey}")
                }
                PlatformAlternativeDownloader.fetchInstagram(url, extractAudio = false)
                    .fold(
                        onSuccess = { payload ->
                            privateTmpDir?.deleteRecursively()
                            return PlatformAlternativeDownloader.downloadFromVideoInfo(
                                videoInfo = PlatformAlternativeDownloader.toVideoInfo(
                                    payload,
                                    extractAudio = false,
                                ),
                                downloadPath = mirrorPath,
                                preferences = downloadPreferences,
                                progressCallback = progressCallback,
                            )
                        },
                        onFailure = {
                            Log.w(TAG, "Instagram mirror download failed: ${it.message}")
                        },
                    )
            }

            // Android 16+: copy completed files from private cache to the public download dir,
            // then delete the cache regardless of outcome to avoid stale storage.
            if (privateTmpDir != null) {
                if (ytdlpResult.isSuccess) {
                    val destDir = File(downloadPath).also { it.mkdirs() }
                    privateTmpDir.walkTopDown()
                        .filter { it.isFile && !it.name.startsWith(".") }
                        .forEach { f ->
                            val dest = destDir.resolve(f.relativeTo(privateTmpDir).path)
                            dest.parentFile?.mkdirs()
                            runCatching { f.copyTo(dest, overwrite = true) }
                                .onFailure { e ->
                                    Log.w(TAG, "Cache→Downloads copy failed ${f.name}: ${e.message}")
                                }
                        }
                }
                privateTmpDir.deleteRecursively()
            }

            ytdlpResult.onFailure { th ->
                return if (sponsorBlock && th.message?.contains("Unable to communicate with SponsorBlock API") == true) {
                    th.printStackTrace()
                    onFinishDownloading(
                        preferences = this,
                        videoInfo = videoInfo,
                        downloadPath = downloadPath,
                        sdcardUri = sdcardUri,
                    )
                } else Result.failure(th)
            }.getOrThrow()

            return onFinishDownloading(
                preferences = this,
                videoInfo = videoInfo,
                downloadPath = downloadPath,
                sdcardUri = sdcardUri,
            )
        }
    }

    private fun onFinishDownloading(
        preferences: DownloadPreferences,
        videoInfo: VideoInfo,
        downloadPath: String,
        sdcardUri: String
    ): Result<List<String>> = preferences.run {
        val fileName = preferences.newTitle.ifEmpty {
            videoInfo.filename ?: videoInfo.requestedDownloads?.firstOrNull()?.filename
            ?: videoInfo.title
        }

        Log.d(TAG, "onFinishDownloading: $fileName")

        fun finishWithPaths(paths: List<String>): Result<List<String>> {
            if (privateMode) return Result.success(emptyList())
            if (!paths.any { path -> File(path).exists() && File(path).length() > 0L }) {
                return Result.failure(
                    YoutubeDLException(context.getString(R.string.download_file_not_found))
                )
            }
            return Result.success(
                if (splitByChapter) {
                    insertSplitChapterIntoHistory(videoInfo, paths)
                } else {
                    insertInfoIntoDownloadHistory(videoInfo, paths)
                }
            )
        }

        if (sdcard) {
            return moveFilesToSdcard(
                sdcardUri = sdcardUri, tempPath = context.getSdcardTempDir(videoInfo.id)
            ).fold(
                onSuccess = { finishWithPaths(it) },
                onFailure = { Result.failure(it) },
            )
        }

        val paths = FileUtil.finalizeDownloadFilenames(
            directory = downloadPath,
            videoTitle = videoInfo.title.ifBlank { fileName },
        ).ifEmpty {
            FileUtil.scanFileToMediaLibraryPostDownload(
                title = FileUtil.sanitizeDownloadTitle(videoInfo.title.ifBlank { fileName }),
                downloadDir = downloadPath,
            )
        }.also { scanned ->
            if (scanned.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    context,
                    scanned.toTypedArray(),
                    null,
                    null,
                )
            }
        }
        return finishWithPaths(paths)
    }

    suspend fun executeCommandInBackground(
        url: String,
        template: CommandTemplate = PreferenceUtil.getTemplate(),
        downloadPreferences: DownloadPreferences = DownloadPreferences(),
    ) {
        downloadPreferences.withAbobiFixedFormats().run {
            val taskId = Downloader.makeKey(url = url, templateName = template.name)
            val notificationId = taskId.toNotificationId()
            val urlList = url.split(Regex("[\n ]")).filter { it.isNotBlank() }

            ToastUtil.makeToastSuspend(context.getString(R.string.start_execute))
            onProcessStarted()
            withContext(Dispatchers.Main) {
                onTaskStarted(template, url)
            }

            fun buildRequest(useAria2c: Boolean): YoutubeDLRequest =
                YoutubeDLRequest(urlList).apply {
                    commandDirectory.takeIf { it.isNotEmpty() }?.let {
                        addOption("-P", it)
                    }
                    addOption("--newline")
                    if (useAria2c) {
                        enableAria2c()
                    }
                    if (useDownloadArchive) {
                        useDownloadArchive()
                    }
                    addOption(
                        "--config-locations", FileUtil.writeContentToFile(
                            template.template, context.getConfigFile()
                        ).absolutePath
                    )
                    if (CookieHelper.shouldUseCookies(url)) {
                        enableCookies(url, userAgentString)
                    }
                }

            fun executeRequest(request: YoutubeDLRequest): YoutubeDLResponse =
                YoutubeDL.getInstance().execute(
                    request = request, processId = taskId
                ) { progress, _, text ->
                    NotificationUtil.makeNotificationForCustomCommand(
                        notificationId = notificationId,
                        taskId = taskId,
                        progress = progress.toInt(),
                        templateName = template.name,
                        taskUrl = url,
                        text = text
                    )
                    Downloader.updateTaskOutput(
                        template = template, url = url, line = text, progress = progress
                    )
                }

            val aria2cEnabled = aria2c
            val commandResult = runCatching {
                executeRequest(buildRequest(aria2cEnabled))
            }
            val responseResult = if (commandResult.isFailure && aria2cEnabled) {
                Log.w(
                    TAG,
                    "aria2c command failed, retrying with native yt-dlp downloader: ${commandResult.exceptionOrNull()?.message}",
                )
                runCatching { executeRequest(buildRequest(useAria2c = false)) }
            } else {
                commandResult
            }

            responseResult.onSuccess { response ->
                onTaskEnded(template, url, response.out + "\n" + response.err)
            }.onFailure {
                it.printStackTrace()
                if (it is YoutubeDL.CanceledException) return@onFailure
                it.message.run {
                    if (isNullOrEmpty()) onTaskEnded(template, url)
                    else onTaskError(this, template, url)
                }
            }
            onProcessEnded()
        }
    }

    private fun checkIfAv1HardwareAccelerated(): Boolean {
        if (PreferenceUtil.containsKey(AV1_HARDWARE_ACCELERATED)) {
            return AV1_HARDWARE_ACCELERATED.getBoolean()
        } else {
            val res = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                false
            } else {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                    info.supportedTypes.any {
                        it.equals(
                            "video/av01",
                            ignoreCase = true
                        )
                    } && info.isHardwareAccelerated
                }
            }
            AV1_HARDWARE_ACCELERATED.updateBoolean(res)
            return res
        }
    }
}