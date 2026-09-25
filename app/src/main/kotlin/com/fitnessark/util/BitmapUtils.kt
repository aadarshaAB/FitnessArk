package com.fitnessark.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object BitmapUtils {

    private const val MAX_DIMENSION = 2048

    /**
     * Loads a Bitmap from a Uri, respecting its EXIF orientation.
     * Downscales large source images (e.g. 12-50MP camera photos) via inSampleSize
     * to avoid OutOfMemoryError, since three photos may be decoded at once.
     */
    fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            } ?: return null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, MAX_DIMENSION)
            }

            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            // Now check orientation using ExifInterface
            val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
                val exifInterface = ExifInterface(input)
                exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    matrix.postScale(1f, -1f)
                    matrix.postRotate(180f)
                }
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
                else -> return bitmap
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            rotatedBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        var w = width
        var h = height
        while (w / 2 >= maxDimension || h / 2 >= maxDimension) {
            w /= 2
            h /= 2
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
