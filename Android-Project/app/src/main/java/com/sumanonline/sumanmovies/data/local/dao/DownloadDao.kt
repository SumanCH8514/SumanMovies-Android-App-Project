package com.sumanonline.sumanmovies.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sumanonline.sumanmovies.data.local.entities.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE downloadId = :downloadId LIMIT 1")
    suspend fun getByDownloadId(downloadId: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, downloadedBytes = :downloadedBytes, localFilePath = :localPath WHERE downloadId = :downloadId")
    suspend fun updateProgress(downloadId: Long, status: String, downloadedBytes: Long, localPath: String?)

    @Query("UPDATE downloads SET status = :status, downloadedBytes = :downloadedBytes, fileSizeBytes = CASE WHEN :fileSizeBytes > 0 THEN :fileSizeBytes ELSE fileSizeBytes END, localFilePath = COALESCE(:localPath, localFilePath) WHERE id = :id")
    suspend fun updateProgressById(id: String, status: String, downloadedBytes: Long, fileSizeBytes: Long, localPath: String?)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatusById(id: String, status: String)

    @Query("SELECT COUNT(*) FROM downloads")
    fun getTotalDownloadsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    fun getCompletedDownloadsCount(): Flow<Int>

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM downloads WHERE downloadId = :downloadId")
    suspend fun deleteByDownloadId(downloadId: Long)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}
