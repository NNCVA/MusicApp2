package com.musicplayer.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * 歌曲数据模型
 */
@Parcelize
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey
    val id: String,                    // 唯一标识
    val title: String,                 // 歌曲名称
    val artist: String,                // 歌手名称
    val album: String,                 // 专辑名称
    val duration: Long,               // 时长（毫秒）
    val path: String,                  // 文件路径
    val albumId: Long,                 // 专辑ID（用于获取封面）
    val dateAdded: Long,              // 添加时间
    val dateModified: Long            // 修改时间
) : Parcelable {
    
    /**
     * 获取格式化的时长字符串
     */
    fun getDurationString(): String {
        val seconds = duration / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }
    
    /**
     * 获取文件名（用于显示）
     */
    fun getFileName(): String {
        return path.substringAfterLast('/')
    }
    
    companion object {
        /**
         * 创建空歌曲对象
         */
        fun empty(): Song {
            return Song(
                id = "",
                title = "未知歌曲",
                artist = "未知歌手",
                album = "未知专辑",
                duration = 0,
                path = "",
                albumId = 0,
                dateAdded = 0,
                dateModified = 0
            )
        }
    }
}