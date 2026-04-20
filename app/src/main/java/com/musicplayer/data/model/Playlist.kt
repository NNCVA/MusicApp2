package com.musicplayer.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * 歌单数据模型
 */
@Parcelize
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                  // 歌单名称
    val createdAt: Long,              // 创建时间
    val updatedAt: Long,              // 更新时间
    val coverSongId: String? = null   // 封面歌曲ID（最近添加的歌曲）
) : Parcelable {
    
    companion object {
        /**
         * 创建默认歌单（我的最爱）
         */
        fun createDefault(): Playlist {
            val now = System.currentTimeMillis()
            return Playlist(
                name = "我的最爱",
                createdAt = now,
                updatedAt = now
            )
        }
    }
}