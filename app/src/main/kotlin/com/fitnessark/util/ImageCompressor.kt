package com.fitnessark.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ImageCompressor {

    fun compress(bitmap: Bitmap, maxDimension: Int = 1024, quality: Int = 80): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }

        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val bytes = stream.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun createThumbnail(bitmap: Bitmap, size: Int = 200): Bitmap {
        val minDim = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - minDim) / 2
        val yOffset = (bitmap.height - minDim) / 2
        val cropped = Bitmap.createBitmap(bitmap, xOffset, yOffset, minDim, minDim)
        return Bitmap.createScaledBitmap(cropped, size, size, true)
    }

    fun saveToInternalStorage(context: Context, bitmap: Bitmap, fileName: String): String {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }

    fun loadFromInternalStorage(path: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        }
    }
}
