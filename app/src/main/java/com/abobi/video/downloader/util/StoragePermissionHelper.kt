package com.abobi.video.downloader.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException

object StoragePermissionHelper {

    const val ROOT_FOLDER_NAME = "AbobiVideoDownloader"
    const val VIDEO_FOLDER_NAME = "video"
    const val AUDIO_FOLDER_NAME = "audio"

    /** Room for extension (e.g. `.mp4`) within Android's 255-byte filename limit. */
    const val MAX_TITLE_FILENAME_BYTES = 245

    fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
        )
        else -> arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    fun hasStorageAccess(context: Context): Boolean {
        val permissions = requiredPermissions()
        if (permissions.isNotEmpty()) {
            val allGranted = permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (!allGranted) return false
        }
        return canWriteToDirectory(defaultVideoDir(context))
    }

    fun canWriteToDirectory(dir: File): Boolean {
        dir.mkdirs()
        if (!dir.isDirectory) return false
        val testFile = File(dir, ".write_test_${System.nanoTime()}")
        return try {
            testFile.writeText("ok")
            val ok = testFile.exists() && testFile.length() > 0L
            testFile.delete()
            ok
        } catch (_: IOException) {
            testFile.delete()
            false
        }
    }

    fun defaultDownloadRoot(context: Context): File {
        val publicRoot = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            ROOT_FOLDER_NAME,
        )
        if (canWriteToDirectory(publicRoot)) {
            publicRoot.mkdirs()
            File(publicRoot, VIDEO_FOLDER_NAME).mkdirs()
            File(publicRoot, AUDIO_FOLDER_NAME).mkdirs()
            return publicRoot
        }

        val appRoot = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            ROOT_FOLDER_NAME,
        )
        appRoot.mkdirs()
        File(appRoot, VIDEO_FOLDER_NAME).mkdirs()
        File(appRoot, AUDIO_FOLDER_NAME).mkdirs()
        return appRoot
    }

    fun defaultVideoDir(context: Context): File =
        File(defaultDownloadRoot(context), VIDEO_FOLDER_NAME).apply { mkdirs() }

    fun defaultAudioDir(context: Context): File =
        File(defaultDownloadRoot(context), AUDIO_FOLDER_NAME).apply { mkdirs() }

    fun ensureDefaultDownloadDir(context: Context): File = defaultVideoDir(context)
}
