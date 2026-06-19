package com.abobi.video.downloader.util

import android.net.Uri
import android.webkit.CookieManager
import com.abobi.video.downloader.App.Companion.context
import com.abobi.video.downloader.ui.page.settings.network.Cookie
import com.abobi.video.downloader.util.DownloadUtil.toCookiesFileContent
import com.abobi.video.downloader.util.FileUtil.getCookiesFile
import java.io.File
import java.net.URI

data class CookieSaveResult(
    val siteLabel: String,
    val cookieCount: Int,
    val siteFilePath: String,
    val masterFilePath: String,
)

object CookieHelper {

    const val TIKTOK_LOGIN_URL = "https://www.tiktok.com/login"
    const val INSTAGRAM_LOGIN_URL = "https://www.instagram.com/accounts/login/"

    fun getCookiesDirectory(): File =
        File(context.filesDir, "cookies/sites").also { it.mkdirs() }

    fun siteLabelFromUrl(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: Uri.parse(url).host?.takeIf { it.isNotBlank() }
            ?: return "unknown"
        return host.removePrefix("www.")
    }

    private fun siteLabelFromDomain(domain: String): String =
        domain.removePrefix(".").removePrefix("www.")

    fun cookieMatchesSiteLabel(cookie: Cookie, siteLabel: String): Boolean =
        cookieDomainMatchesSiteLabel(siteLabelFromDomain(cookie.domain), siteLabel)

    private fun cookieDomainMatchesSiteLabel(cookieDomain: String, siteLabel: String): Boolean {
        val domain = cookieDomain.removePrefix("www.")
        val label = siteLabel.removePrefix("www.")
        return domain.equals(label, ignoreCase = true) ||
            domain.endsWith(".$label", ignoreCase = true) ||
            label.endsWith(".$domain", ignoreCase = true) ||
            label.endsWith(domain, ignoreCase = true)
    }

    fun cookiesFileAvailable(): Boolean {
        val file = context.getCookiesFile()
        return file.exists() && file.length() > 50
    }

    fun cookiesAvailableForUrl(downloadUrl: String): Boolean {
        val siteLabel = siteLabelFromUrl(downloadUrl)
        val siteFile = File(getCookiesDirectory(), "$siteLabel.txt")
        if (siteFile.exists() && siteFile.length() > 50 && countCookiesInFile(siteFile) > 0) {
            return true
        }
        val master = context.getCookiesFile()
        if (!master.exists() || master.length() <= 50) return false
        return master.readLines().any { line ->
            if (line.startsWith("#") || line.isBlank()) return@any false
            val domain = line.substringBefore('\t')
            cookieDomainMatchesSiteLabel(siteLabelFromDomain(domain), siteLabel)
        }
    }

    fun shouldUseCookies(url: String = ""): Boolean =
        cookiesAvailableForUrl(url) || cookiesFileAvailable()

    fun refreshCookiesForSite(profileUrl: String): Result<CookieSaveResult> = runCatching {
        CookieManager.getInstance().flush()
        val allCookies = DownloadUtil.getCookieListFromDatabase().getOrThrow()
        val siteLabel = siteLabelFromUrl(profileUrl)
        val siteCookies = allCookies.filter { cookieMatchesSiteLabel(it, siteLabel) }
        val siteFile = File(getCookiesDirectory(), "$siteLabel.txt")
        FileUtil.writeContentToFile(siteCookies.toCookiesFileContent(), siteFile)
        val masterFile = context.getCookiesFile()
        FileUtil.writeContentToFile(allCookies.toCookiesFileContent(), masterFile)
        CookieSaveResult(
            siteLabel = siteLabel,
            cookieCount = siteCookies.size,
            siteFilePath = siteFile.absolutePath,
            masterFilePath = masterFile.absolutePath,
        )
    }

    fun refreshAllCookiesFiles(): Result<CookieSaveResult> = runCatching {
        CookieManager.getInstance().flush()
        val allCookies = DownloadUtil.getCookieListFromDatabase().getOrThrow()
        val siteDir = getCookiesDirectory()
        allCookies
            .map { siteLabelFromDomain(it.domain) }
            .distinct()
            .forEach { label ->
                val siteCookies = allCookies.filter { cookieMatchesSiteLabel(it, label) }
                FileUtil.writeContentToFile(
                    siteCookies.toCookiesFileContent(),
                    File(siteDir, "$label.txt"),
                )
            }
        val masterFile = context.getCookiesFile()
        FileUtil.writeContentToFile(allCookies.toCookiesFileContent(), masterFile)
        CookieSaveResult(
            siteLabel = "all",
            cookieCount = allCookies.size,
            siteFilePath = siteDir.absolutePath,
            masterFilePath = masterFile.absolutePath,
        )
    }

    /** @deprecated Use [refreshAllCookiesFiles] */
    fun refreshCookiesFile(): Result<Int> =
        refreshAllCookiesFiles().map { it.cookieCount }

    fun getSiteCookieStats(profileUrl: String): Pair<Int, String>? {
        val siteLabel = siteLabelFromUrl(profileUrl)
        val siteFile = File(getCookiesDirectory(), "$siteLabel.txt")
        if (!siteFile.exists() || siteFile.length() == 0L) return null
        val count = countCookiesInFile(siteFile)
        if (count == 0) return null
        return count to siteFile.absolutePath
    }

    fun getSiteCookieFile(profileUrl: String): File? {
        val siteLabel = siteLabelFromUrl(profileUrl)
        val siteFile = File(getCookiesDirectory(), "$siteLabel.txt")
        return siteFile.takeIf { it.exists() && it.length() > 0 }
    }

    /** Prefer per-site cookies file when present; otherwise fall back to the master file. */
    fun cookiesFileForUrl(downloadUrl: String): File? {
        getSiteCookieFile(downloadUrl)
            ?.takeIf { it.length() > 50 && countCookiesInFile(it) > 0 }
            ?.let { return it }
        val master = context.getCookiesFile()
        return master.takeIf { it.exists() && it.length() > 50 }
    }

    fun hasCookiesForDomain(domain: String): Boolean =
        DownloadUtil.getCookieListFromDatabase().getOrNull()
            ?.any { it.domain.contains(domain, ignoreCase = true) } == true

    fun buildCookieHeaderForUrl(url: String): String? {
        val host = runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: Uri.parse(url).host?.takeIf { it.isNotBlank() }
            ?: return null
        val cookies = DownloadUtil.getCookieListFromDatabase().getOrNull() ?: return null
        val matching = cookies.filter { cookie ->
            val domain = cookie.domain.removePrefix(".")
            host.equals(domain, ignoreCase = true) || host.endsWith(".$domain", ignoreCase = true)
        }
        if (matching.isEmpty()) return null
        return matching.joinToString("; ") { "${it.name}=${it.value}" }
    }

    private fun countCookiesInFile(file: File): Int =
        file.readLines().count { line -> line.isNotBlank() && !line.startsWith("#") }

    /** Strip tracking query params that can confuse yt-dlp on Instagram reel/post URLs. */
    fun normalizeDownloadUrl(url: String): String {
        if ("instagram.com" !in url.lowercase()) return url
        val base = url.substringBefore('?').trimEnd('/')
        return if (
            Regex("instagram\\.com/(reel|p|tv)/", RegexOption.IGNORE_CASE).containsMatchIn(base)
        ) {
            base
        } else {
            url.substringBefore('?')
        }
    }
}
