package com.fitnessark.data.local.dao

import androidx.room.*
import com.fitnessark.data.local.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos ORDER BY date DESC")
    fun getAllPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos ORDER BY date DESC")
    suspend fun getAllPhotosList(): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: String): PhotoEntity?

    @Query("SELECT * FROM photos ORDER BY date DESC LIMIT 1")
    suspend fun getLatestPhoto(): PhotoEntity?

    @Query("SELECT * FROM photos WHERE localDate = :localDate LIMIT 1")
    suspend fun getPhotoByLocalDate(localDate: String): PhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhoto(id: String)

    @Query("DELETE FROM photos")
    suspend fun deleteAllPhotos()
}
