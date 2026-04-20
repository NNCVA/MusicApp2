package com.musicplayer

import android.app.Application
import com.musicplayer.data.database.MusicDatabase
import com.musicplayer.data.repository.MusicRepository

class MusicPlayerApplication : Application() {
    
    // 数据库实例
    val database: MusicDatabase by lazy { MusicDatabase.getDatabase(this) }
    
    // 仓库实例
    val musicRepository: MusicRepository by lazy { 
        MusicRepository(database.songDao(), database.playlistDao(), database.recentPlayDao()) 
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: MusicPlayerApplication
            private set
    }
}