package com.abobi.video.downloader.util

import android.content.ClipData
import com.abobi.video.downloader.App
import com.abobi.video.downloader.App.Companion.clipboard
import com.abobi.video.downloader.App.Companion.context
import com.abobi.video.downloader.R
import java.io.PrintWriter
import java.io.StringWriter

object ErrorReportUtil {

    fun buildFullReport(
        url: String?,
        th: Throwable,
        title: String? = null,
        extraLog: String? = null,
    ): String = buildString {
        append(App.getVersionReport())
        append('\n')
        if (!title.isNullOrBlank()) {
            append("Title: ").append(title).append('\n')
        }
        append("URL: ").append(url.orEmpty()).append('\n')
        val cookieUrl = url.orEmpty()
        if (cookieUrl.isNotBlank()) {
            append("Cookies for URL: ")
                .append(if (CookieHelper.cookiesAvailableForUrl(cookieUrl)) "yes" else "no")
                .append('\n')
        }
        append("Cookies file: ")
            .append(if (CookieHelper.cookiesFileAvailable()) "yes" else "no")
            .append("\n\n")

        val strategyLog = extractDebugDetails(th) ?: extraLog
        if (!strategyLog.isNullOrBlank()) {
            append("Strategy log:\n").append(strategyLog).append("\n\n")
        }

        append("Error: ").append(th.message.orEmpty()).append("\n\n")
        append(formatThrowableChain(th))
    }

    fun extractDebugDetails(th: Throwable): String? {
        var current: Throwable? = th
        while (current != null) {
            if (current is DownloadDebugException && current.debugDetails.isNotBlank()) {
                return current.debugDetails
            }
            current = current.cause
        }
        return null
    }

    private fun formatThrowableChain(th: Throwable): String = buildString {
        var current: Throwable? = th
        var depth = 0
        while (current != null) {
            if (depth > 0) {
                append("\n--- Caused by: ")
            } else {
                append("--- ")
            }
            append(current.javaClass.name)
            current.message?.let { append(": ").append(it) }
            append(" ---\n")
            append(stackTraceToString(current))
            current = current.cause
            depth++
        }
    }

    private fun stackTraceToString(th: Throwable): String {
        val sw = StringWriter()
        th.printStackTrace(PrintWriter(sw))
        return sw.toString().trimEnd()
    }

    fun copyToClipboard(report: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(null, report))
    }

    fun copyAndNotify(report: String) {
        copyToClipboard(report)
        ToastUtil.makeToastSuspend(context.getString(R.string.error_copied))
    }
}
