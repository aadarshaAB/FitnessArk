package com.fitnessark.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface

object BitmapUtils {

    private const val TAG = "BitmapUtils"

    private const val MAX_DIMENSION = 2048

    /**
     * Loads a Bitmap from a Uri, respecting its EXIF orientation.
     * Downscales large source images (e.g. 12-50MP camera photos) via inSampleSize
     * to avoid OutOfMemoryError, since three photos may be decoded at once.
     */
    fun decodeUriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = context.contentResolver.openInputStream(uri)
            if (boundsStream == null) {
                Log.e(TAG, "openInputStream returned null for $uri (bounds pass)")
                return null
            }
            boundsStream.use { BitmapFactory.decodeStream(it, null, boundsOptions) }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                Log.e(TAG, "Could not read image bounds for $uri " +
                    "(outWidth=${boundsOptions.outWidth}, outHeight=${boundsOptions.outHeight}); " +
                    "the file may not be fully written yet or isn't a valid image")
                return null
            }

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, MAX_DIMENSION)
            }

            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (bitmap == null) {
                Log.e(TAG, "decodeStream returned null for $uri on the full decode pass")
                return null
            }

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
            Log.e(TAG, "Failed to decode photo from $uri", e)
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
