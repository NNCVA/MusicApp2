package com.musicplayer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.musicplayer.data.dao.PlaylistDao
import com.musicplayer.data.dao.RecentPlayDao
import com.musicplayer.data.dao.SongDao
import com.musicplayer.data.model.Playlist
import com.musicplayer.data.model.PlaylistSong
import com.musicplayer.data.model.RecentPlay
import com.musicplayer.data.model.Song

/**
 * 音乐播放器数据库
 */
@Database(
    entities = [
        Song::class,
        Playlist::class,
        PlaylistSong::class,
        RecentPlay::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun recentPlayDao(): RecentPlayDao
    
    companion object {

        @Volatile
        private var INSTANCE: MusicDatabase? = null

        /**
         * 数据库版本 1 -> 2 迁移
         * 添加歌单封面歌曲ID字段
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加 coverSongId 列
                database.execSQL(
                    "ALTER TABLE playlists ADD COLUMN coverSongId TEXT DEFAULT NULL"
                )
            }
        }

        /**
         * 获取数据库实例
         */
        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "music_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}