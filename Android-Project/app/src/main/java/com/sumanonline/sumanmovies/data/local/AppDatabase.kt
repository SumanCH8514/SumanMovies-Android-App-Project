package com.sumanonline.sumanmovies.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sumanonline.sumanmovies.data.local.dao.DownloadDao
import com.sumanonline.sumanmovies.data.local.dao.FavoriteDao
import com.sumanonline.sumanmovies.data.local.dao.WatchHistoryDao
import com.sumanonline.sumanmovies.data.local.entities.DownloadEntity
import com.sumanonline.sumanmovies.data.local.entities.FavoriteEntity
import com.sumanonline.sumanmovies.data.local.entities.WatchHistoryEntity

@Database(
    entities = [WatchHistoryEntity::class, FavoriteEntity::class, DownloadEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sumanmovies.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
