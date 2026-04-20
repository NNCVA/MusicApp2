package com.musicplayer.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.musicplayer.data.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * 歌曲数据访问对象
 */
@Dao
interface SongDao {
    
    /**
     * 插入歌曲
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song)
    
    /**
     * 批量插入歌曲
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)
    
    /**
     * 删除歌曲
     */
    @Delete
    suspend fun deleteSong(song: Song)
    
    /**
     * 删除所有歌曲
     */
    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()
    
    /**
     * 根据ID获取歌曲
     */
    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: String): Song?
    
    /**
     * 获取所有歌曲
     */
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>
    
    /**
     * 获取所有歌曲（LiveData）
     */
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongsLive(): LiveData<List<Song>>
    
    /**
     * 按名称排序
     */
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getSongsOrderByName(): Flow<List<Song>>
    
    /**
     * 按歌手排序
     */
    @Query("SELECT * FROM songs ORDER BY artist ASC, title ASC")
    fun getSongsOrderByArtist(): Flow<List<Song>>
    
    /**
     * 按时间排序
     */
    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun getSongsOrderByDate(): Flow<List<Song>>
    
    /**
     * 按时长排序
     */
    @Query("SELECT * FROM songs ORDER BY duration ASC")
    fun getSongsOrderByDuration(): Flow<List<Song>>
    
    /**
     * 默认排序
     */
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getSongsOrderByDefault(): Flow<List<Song>>
    
    /**
     * 搜索歌曲
     */
    @Query("""
        SELECT * FROM songs 
        WHERE title LIKE '%' || :query || '%' 
        OR artist LIKE '%' || :query || '%'
        ORDER BY title ASC
    """)
    fun searchSongs(query: String): Flow<List<Song>>
    
    /**
     * 获取歌曲数量
     */
    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCount(): Int
    
    /**
     * 检查歌曲是否存在
     */
    @Query("SELECT COUNT(*) FROM songs WHERE path = :path")
    suspend fun isSongExists(path: String): Boolean
    
    /**
     * 根据路径获取歌曲
     */
    @Query("SELECT * FROM songs WHERE path = :path")
    suspend fun getSongByPath(path: String): Song?
}