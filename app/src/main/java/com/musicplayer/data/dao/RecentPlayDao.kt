package com.musicplayer.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.musicplayer.data.model.RecentPlay
import com.musicplayer.data.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * 最近播放数据访问对象
 */
@Dao
interface RecentPlayDao {
    
    /**
     * 添加播放记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addRecentPlay(recentPlay: RecentPlay)
    
    /**
     * 获取最近播放的歌曲（最多20首）
     */
    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN (
            SELECT songId, MAX(playedAt) as maxPlayedAt 
            FROM recent_plays 
            GROUP BY songId 
            ORDER BY maxPlayedAt DESC 
            LIMIT 20
        ) rp ON s.id = rp.songId
        ORDER BY rp.maxPlayedAt DESC
    """)
    fun getRecentPlays(): Flow<List<Song>>
    
    /**
     * 获取最近播放的歌曲（LiveData）
     */
    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN (
            SELECT songId, MAX(playedAt) as maxPlayedAt 
            FROM recent_plays 
            GROUP BY songId 
            ORDER BY maxPlayedAt DESC 
            LIMIT 20
        ) rp ON s.id = rp.songId
        ORDER BY rp.maxPlayedAt DESC
    """)
    fun getRecentPlaysLive(): LiveData<List<Song>>
    
    /**
     * 清空最近播放记录
     */
    @Query("DELETE FROM recent_plays")
    suspend fun clearRecentPlays()
    
    /**
     * 获取歌曲的播放次数
     */
    @Query("SELECT IFNULL(SUM(playCount), 0) FROM recent_plays WHERE songId = :songId")
    suspend fun getPlayCount(songId: String): Int
    
    /**
     * 更新播放次数
     */
    @Query("UPDATE recent_plays SET playCount = playCount + 1 WHERE songId = :songId AND playedAt = :playedAt")
    suspend fun incrementPlayCount(songId: String, playedAt: Long)
    
    /**
     * 根据歌曲ID删除播放记录
     */
    @Query("DELETE FROM recent_plays WHERE songId = :songId")
    suspend fun deleteRecentPlayBySongId(songId: String)
}