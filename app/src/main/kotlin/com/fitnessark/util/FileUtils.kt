package com.fitnessark.util

import android.content.Context
import java.io.File

object FileUtils {

    fun getInternalStorageDir(context: Context): File =
        File(context.filesDir, "photos").apply { mkdirs() }

    fun calculateAppSize(context: Context): Long {
        val filesDir = context.filesDir
        val cacheDir = context.cacheDir
        return (dirSize(filesDir) + dirSize(cacheDir))
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun deleteDirectoryRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteDirectoryRecursively(it) }
        }
        return file.delete()
    }
}
