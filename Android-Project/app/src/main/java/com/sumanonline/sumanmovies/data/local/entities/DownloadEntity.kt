package com.sumanonline.sumanmovies.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val downloadId: Long,
    val mediaId: String,
    val title: String,
    val mediaType: String,
    val posterUrl: String?,
    val streamUrl: String,
    val localFilePath: String? = null,
    val quality: String = "HD",
    val fileSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = "DOWNLOADING",
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)
