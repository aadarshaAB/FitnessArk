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
        val tempFile = File.createTempFile(
            prefix,
            ".jpg",
            context.externalCacheDir ?: context.cacheDir
        )
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }
}
