package com.abobi.video.downloader.util

import android.util.Log
import com.abobi.video.downloader.App.Companion.context
import com.abobi.video.downloader.R
import com.abobi.video.downloader.database.objects.DownloadedVideoInfo
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Last-resort mirror APIs + direct OkHttp download when yt-dlp fails for TikTok/Instagram.
 */
object PlatformAlternativeDownloader {

    private const val TAG = "PlatformAltDownloader"

    const val ALT_EXTRACTOR_KEY = "AbobiMirror"

    // 64 KB reduz o nº de syscalls de I/O vs. 8 KB → download direto mais eficiente.
    private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class MirrorPayload(
        val videoUrl: String,
        val audioUrl: String? = null,
        val title: String,
        val id: String,
        val thumbnail: String? = null,
        val duration: Double? = null,
        val uploader: String? = null,
        val webpageUrl: String,
        val extractor: String,
        val extractorKey: String = ALT_EXTRACTOR_KEY,
    )

    @Serializable
    private data class TikWmResponse(
        val code: Int = -1,
        val data: TikWmData? = null,
        val msg: String? = null,
    )

    @Serializable
    private data class TikWmData(
        val id: String = "",
        val title: String = "",
        val cover: String? = null,
        val duration: Double? = null,
        val play: String? = null,
        val hdplay: String? = null,
        val wmplay: String? = null,
        val music: String? = null,
        val author: TikWmAuthor? = null,
    )

    @Serializable
    private data class TikWmAuthor(
        val nickname: String? = null,
    )

    fun fetchTikTok(url: String, extractAudio: Boolean): Result<MirrorPayload> {
        val hosts = listOf("https://www.tikwm.com", "https://tikwm.com")
        var lastError: Throwable? = null
        for (host in hosts) {
            val result = fetchTikTokFromTikWm(url, extractAudio, host)
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()
            Log.w(TAG, "TikTok mirror $host failed: ${lastError?.message}")
        }
        return Result.failure(lastError ?: IOException("All TikTok mirrors failed"))
    }

    private fun fetchTikTokFromTikWm(
        url: String,
        extractAudio: Boolean,
        apiHost: String,
    ): Result<MirrorPayload> = runCatching {
        val apiUrl = "$apiHost/api/?url=${URLEncoder.encode(url, Charsets.UTF_8.name())}"
        Log.d(TAG, "fetchTikTok: $apiUrl")
        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("User-Agent", DownloadFallback.MOBILE_UA)
            .get()
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("tikwm HTTP ${response.code}")
            }
            response.body?.string() ?: throw IOException("tikwm empty body")
        }
        val parsed = jsonFormat.decodeFromString<TikWmResponse>(body)
        if (parsed.code != 0 || parsed.data == null) {
            throw IOException(parsed.msg ?: "tikwm API error (code=${parsed.code})")
        }
        val data = parsed.data
        val videoUrl = if (extractAudio) {
            data.music ?: throw IOException("No audio URL in tikwm response")
        } else {
            data.hdplay?.takeIf { it.isNotBlank() }
                ?: data.play?.takeIf { it.isNotBlank() }
                ?: data.wmplay?.takeIf { it.isNotBlank() }
                ?: throw IOException("No video URL in tikwm response")
        }
        MirrorPayload(
            videoUrl = videoUrl,
            audioUrl = data.music,
            title = data.title.ifBlank { "TikTok ${data.id}" },
            id = data.id,
            thumbnail = data.cover,
            duration = data.duration,
            uploader = data.author?.nickname,
            webpageUrl = url,
            extractor = "TikTok",
        )
    }.onFailure { Log.w(TAG, "fetchTikTok failed: ${it.message}") }

    fun fetchInstagram(url: String, extractAudio: Boolean): Result<MirrorPayload> {
        if (extractAudio) {
            return Result.failure(IOException("Instagram audio-only not supported via mirror"))
        }
        val normalizedUrl = CookieHelper.normalizeDownloadUrl(url)
        val mirrors = listOf(
            "https://v3.saveig.app/api/ajaxSearch",
            "https://api.saveig.app/api/ajaxSearch",
            "https://v3.igdownloader.app/api/ajaxSearch",
            "https://snapinsta.app/api/ajaxSearch",
        )
        var lastError: Throwable? = null
        for (mirrorUrl in mirrors) {
            val result = fetchInstagramFromMirror(normalizedUrl, mirrorUrl)
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()
            Log.w(TAG, "Instagram mirror $mirrorUrl failed: ${lastError?.message}")
        }
        return Result.failure(lastError ?: IOException("All Instagram mirrors failed"))
    }

    private fun fetchInstagramFromMirror(url: String, apiUrl: String): Result<MirrorPayload> = runCatching {
        val formBody = "q=${URLEncoder.encode(url, Charsets.UTF_8.name())}&t=media&lang=en"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val referer = apiUrl.substringBefore("/api/")
        val request = Request.Builder()
            .url(apiUrl)
            .post(formBody)
            .addHeader("User-Agent", DownloadFallback.MOBILE_UA)
            .addHeader("Referer", "$referer/")
            .addHeader("Origin", referer)
            .build()
        Log.d(TAG, "fetchInstagram: POST $apiUrl")
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Instagram mirror HTTP ${response.code}")
            }
            response.body?.string() ?: throw IOException("Instagram mirror empty body")
        }
        val videoUrl = extractMp4Url(body)
        val id = Regex("instagram\\.com/(?:reel|p|tv)/([A-Za-z0-9_-]+)")
            .find(url)?.groupValues?.get(1) ?: videoUrl.hashCode().toString()
        MirrorPayload(
            videoUrl = videoUrl,
            title = "Instagram $id",
            id = id,
            webpageUrl = url,
            extractor = "Instagram",
        )
    }

    private fun extractMp4Url(text: String): String {
        val patterns = listOf(
            Regex(""""url"\s*:\s*"(https://[^"\\]+\.mp4[^"\\]*)""""),
            Regex(""""downloadUrl"\s*:\s*"(https://[^"\\]+)""""),
            Regex(""""videoUrl"\s*:\s*"(https://[^"\\]+)""""),
            Regex("""href="(https://[^"]+\.mp4[^"]*)""""),
            Regex("""(https://[^\s"<>\\]+\.mp4[^\s"<>\\]*)"""),
        )
        for (pattern in patterns) {
            pattern.find(text)?.groupValues?.getOrNull(1)?.let { raw ->
                return raw.replace("\\/", "/").replace("\\u0026", "&")
            }
        }
        throw IOException("No mp4 URL found in Instagram mirror response")
    }

    fun toVideoInfo(payload: MirrorPayload, extractAudio: Boolean): VideoInfo {
        val ext = if (extractAudio) "m4a" else "mp4"
        val downloadUrl = if (extractAudio) {
            payload.audioUrl ?: payload.videoUrl
        } else {
            payload.videoUrl
        }
        val safeTitle = FileUtil.sanitizeDownloadTitle(payload.title)
        return VideoInfo(
            id = payload.id,
            title = payload.title,
            thumbnail = payload.thumbnail,
            duration = payload.duration,
            uploader = payload.uploader,
            webpageUrl = payload.webpageUrl,
            extractor = payload.extractor,
            extractorKey = ALT_EXTRACTOR_KEY,
            ext = ext,
            vcodec = if (extractAudio) "none" else "h264",
            acodec = if (extractAudio) "aac" else null,
            requestedDownloads = listOf(
                RequestedDownload(
                    url = downloadUrl,
                    ext = ext,
                    vcodec = if (extractAudio) "none" else "h264",
                    acodec = if (extractAudio) "aac" else null,
                    filename = "$safeTitle.$ext",
                ),
            ),
        )
    }

    fun isAlternativeVideoInfo(info: VideoInfo): Boolean =
        info.extractorKey == ALT_EXTRACTOR_KEY

    fun downloadFromVideoInfo(
        videoInfo: VideoInfo,
        downloadPath: String,
        preferences: DownloadUtil.DownloadPreferences,
        progressCallback: ((Float, Long, String) -> Unit)?,
    ): Result<List<String>> = runCatching {
        val download = videoInfo.requestedDownloads?.firstOrNull()
            ?: throw YoutubeDLException("No direct download URL in mirror VideoInfo")
        val directUrl = download.url?.takeIf { it.isNotBlank() }
            ?: throw YoutubeDLException("Empty mirror download URL")
        val ext = download.ext ?: videoInfo.ext.ifBlank { "mp4" }
        val title = preferences.newTitle.ifEmpty {
            download.filename?.substringBeforeLast('.') ?: videoInfo.title
        }
        val safeTitle = FileUtil.sanitizeDownloadTitle(title)
        val outputFile = File(downloadPath, "$safeTitle.$ext")
        outputFile.parentFile?.mkdirs()
        Log.d(TAG, "Direct download: $directUrl -> ${outputFile.absolutePath}")
        downloadFile(directUrl, outputFile, progressCallback)
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            throw YoutubeDLException(context.getString(R.string.download_file_not_found))
        }
        if (preferences.privateMode) {
            return@runCatching emptyList<String>()
        }
        val paths = FileUtil.scanFileToMediaLibraryPostDownload(safeTitle, downloadPath)
        paths.forEach { path ->
            DatabaseUtil.insertInfo(videoInfo.toDownloadedVideoInfo(path))
        }
        paths.ifEmpty { listOf(outputFile.absolutePath) }
    }.onFailure { Log.e(TAG, "downloadFromVideoInfo failed: ${it.message}") }

    private fun VideoInfo.toDownloadedVideoInfo(videoPath: String): DownloadedVideoInfo =
        DownloadedVideoInfo(
            id = 0,
            videoTitle = title,
            videoAuthor = uploader ?: channel ?: uploaderId.orEmpty(),
            videoUrl = webpageUrl ?: originalUrl.orEmpty(),
            thumbnailUrl = thumbnail.toHttpsUrl(),
            videoPath = videoPath,
            extractor = extractorKey,
        )

    private fun downloadFile(
        url: String,
        outputFile: File,
        progressCallback: ((Float, Long, String) -> Unit)?,
    ) {
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("User-Agent", DownloadFallback.MOBILE_UA)
        val lowerUrl = url.lowercase()
        if ("tiktokcdn" in lowerUrl || "tiktok.com" in lowerUrl) {
            CookieHelper.buildCookieHeaderForUrl(url)?.let { cookieHeader ->
                requestBuilder.addHeader("Cookie", cookieHeader)
            }
        }
        val request = requestBuilder.get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty download body")
            val totalBytes = body.contentLength()
            try {
                body.byteStream().use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                        var downloaded = 0L
                        var read: Int
                        // Só reporta quando o % inteiro muda — evita milhares de
                        // updates de UI por download (jank no progresso).
                        var lastPercent = -1
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0 && progressCallback != null) {
                                val percent = (downloaded * 100 / totalBytes).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    progressCallback.invoke(percent.toFloat(), 0L, outputFile.name)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            } catch (e: Throwable) {
                // Remove arquivo parcial/corrompido para não vazar p/ a galeria.
                runCatching { if (outputFile.exists()) outputFile.delete() }
                throw e
            }
            progressCallback?.invoke(100f, 0L, outputFile.name)
        }
    }
}
