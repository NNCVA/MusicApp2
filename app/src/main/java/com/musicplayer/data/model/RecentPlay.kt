package com.musicplayer.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 最近播放记录数据模型
 */
@Entity(
    tableName = "recent_plays",
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["songId"])]
)
data class RecentPlay(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: String,                // 歌曲ID
    val playedAt: Long,               // 播放时间
    val playCount: Int = 1            // 播放次数
)