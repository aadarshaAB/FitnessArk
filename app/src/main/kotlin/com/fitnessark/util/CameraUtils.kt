package com.fitnessark.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object CameraUtils {

    /**
     * Creates a temp file and returns its FileProvider URI for use with
     * ActivityResultContracts.TakePicture(). The URI is authority-scoped so
     * the camera app can write to it without needing WRITE_EXTERNAL_STORAGE.
     */
    fun createTempCameraUri(context: Context, prefix: String = "camera_temp"): Uri {
        val dir = context.externalCacheDir ?: context.cacheDir
        deleteStaleTempFiles(dir)
        val tempFile = File.createTempFile(prefix, ".jpg", dir)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    /**
     * Camera captures are copied into app storage once saved, so the temp originals are
     * garbage. Only files older than a day go, so a capture still waiting to be saved survives.
     */
    private fun deleteStaleTempFiles(dir: File) {
        val cutoff = System.currentTimeMillis() - STALE_AFTER_MS
        dir.listFiles { f -> f.isFile && f.extension == "jpg" && f.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    private const val STALE_AFTER_MS = 24L * 60 * 60 * 1000
}
