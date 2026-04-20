package com.musicplayer.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.musicplayer.data.model.Playlist
import com.musicplayer.data.model.PlaylistSong
import com.musicplayer.data.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * 歌单数据访问对象
 */
@Dao
interface PlaylistDao {
    
    /**
     * 插入歌单
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long
    
    /**
     * 更新歌单
     */
    @Update
    suspend fun updatePlaylist(playlist: Playlist)
    
    /**
     * 删除歌单
     */
    @Delete
    suspend fun deletePlaylist(playlist: Playlist)
    
    /**
     * 获取所有歌单
     */
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>
    
    /**
     * 获取所有歌单（LiveData）
     */
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylistsLive(): LiveData<List<Playlist>>

    /**
     * 同步获取所有歌单
     */
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getAllPlaylistsSync(): List<Playlist>
    
    /**
     * 根据ID获取歌单
     */
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): Playlist?
    
    /**
     * 添加歌曲到歌单
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(playlistSong: PlaylistSong)
    
    /**
     * 批量添加歌曲到歌单
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongsToPlaylist(playlistSongs: List<PlaylistSong>)
    
    /**
     * 从歌单中移除歌曲
     */
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)
    
    /**
     * 获取歌单中的歌曲
     */
    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN playlist_songs ps ON s.id = ps.songId 
        WHERE ps.playlistId = :playlistId 
        ORDER BY ps.position ASC
    """)
    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>>
    
    /**
     * 获取歌单中的歌曲（同步）
     */
    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN playlist_songs ps ON s.id = ps.songId 
        WHERE ps.playlistId = :playlistId 
        ORDER BY ps.position ASC
    """)
    suspend fun getSongsInPlaylistSync(playlistId: Long): List<Song>
    
    /**
     * 获取歌单歌曲数量
     */
    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getPlaylistSongCount(playlistId: Long): Int
    
    /**
     * 检查歌曲是否在歌单中
     */
    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun isSongInPlaylist(playlistId: Long, songId: String): Boolean
    
    /**
     * 清空歌单
     */
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    /**
     * 获取歌单中最近添加的歌曲
     */
    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.addedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestSongInPlaylist(playlistId: Long): Song?
}