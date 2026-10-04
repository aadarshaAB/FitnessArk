package com.fitnessark.data.model

import com.fitnessark.data.local.entity.PhotoEntity

/**
 * The three poses a day's photo entry can hold. Declaration order is the thumbnail priority:
 * the thumbnail comes from the first angle that has a photo.
 */
enum class PhotoAngle(val label: String) {
    FRONT("Front"),
    SIDE("Side"),
    BACK("Back");

    /** Lower-case name used in image file names, e.g. `front_<id>.jpg`. */
    val fileKey: String get() = name.lowercase()
}

fun PhotoEntity.pathFor(angle: PhotoAngle): String? = when (angle) {
    PhotoAngle.FRONT -> frontPhotoPath
    PhotoAngle.SIDE  -> sidePhotoPath
    PhotoAngle.BACK  -> backPhotoPath
}

fun PhotoEntity.withPath(angle: PhotoAngle, path: String?): PhotoEntity = when (angle) {
    PhotoAngle.FRONT -> copy(frontPhotoPath = path)
    PhotoAngle.SIDE  -> copy(sidePhotoPath = path)
    PhotoAngle.BACK  -> copy(backPhotoPath = path)
}

/** The first angle (front, then side, then back) that has a photo; the thumbnail is taken from it. */
fun PhotoEntity.thumbnailAngle(): PhotoAngle? = PhotoAngle.entries.firstOrNull { pathFor(it) != null }

/** Every image file this entry points at: the angles plus the thumbnail. */
fun PhotoEntity.filePaths(): List<String> =
    listOfNotNull(frontPhotoPath, sidePhotoPath, backPhotoPath, thumbnailPath)
