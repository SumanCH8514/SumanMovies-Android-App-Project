package com.sumanonline.sumanmovies.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val mediaType: String,
    val posterUrl: String?,
    val year: String?,
    val rating: Float?,
    val addedTimestamp: Long = System.currentTimeMillis()
)
