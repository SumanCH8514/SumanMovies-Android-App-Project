package com.sumanonline.sumanmovies.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val id: String,
    val mediaId: String,
    val title: String,
    val mediaType: String,
    val posterUrl: String?,
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isCompleted: Boolean = false,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
)
