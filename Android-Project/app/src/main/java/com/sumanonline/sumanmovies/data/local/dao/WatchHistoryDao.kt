package com.sumanonline.sumanmovies.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sumanonline.sumanmovies.data.local.entities.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC")
    fun getAllHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE isCompleted = 0 ORDER BY lastWatchedTimestamp DESC")
    fun getContinueWatching(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE isCompleted = 1 ORDER BY lastWatchedTimestamp DESC")
    fun getCompleted(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE id = :id LIMIT 1")
    suspend fun getProgress(id: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: WatchHistoryEntity)

    @Query("UPDATE watch_history SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun setCompleted(id: String, isCompleted: Boolean)

    @Query("UPDATE watch_history SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun markCompleted(id: String, isCompleted: Boolean)

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}
