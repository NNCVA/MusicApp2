package com.musicplayer.data.repository

import androidx.lifecycle.LiveData
import com.musicplayer.data.dao.PlaylistDao
import com.musicplayer.data.dao.RecentPlayDao
import com.musicplayer.data.dao.SongDao
import com.musicplayer.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.*

/**
 * 音乐数据仓库（Repository）
 * 统一管理所有数据源
 */
class MusicRepository(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val recentPlayDao: RecentPlayDao
) {
    
    // ==================== 歌曲相关 ====================
    
    /**
     * 获取所有歌曲
     */
    fun getAllSongs(): Flow<List<Song>> = songDao.getAllSongs()
    
    /**
     * 获取所有歌曲（LiveData）
     */
    fun getAllSongsLive(): LiveData<List<Song>> = songDao.getAllSongsLive()
    
    /**
     * 根据ID获取歌曲
     */
    suspend fun getSongById(songId: String): Song? = withContext(Dispatchers.IO) {
        songDao.getSongById(songId)
    }
    
    /**
     * 插入歌曲
     */
    suspend fun insertSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.insertSong(song)
    }
    
    /**
     * 批量插入歌曲
     */
    suspend fun insertSongs(songs: List<Song>) = withContext(Dispatchers.IO) {
        songDao.insertSongs(songs)
    }
    
    /**
     * 删除歌曲
     */
    suspend fun deleteSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.deleteSong(song)
    }
    
    /**
     * 删除所有歌曲
     */
    suspend fun deleteAllSongs() = withContext(Dispatchers.IO) {
        songDao.deleteAllSongs()
    }
    
    /**
     * 搜索歌曲
     */
    fun searchSongs(query: String): Flow<List<Song>> = songDao.searchSongs(query)
    
    /**
     * 按不同方式排序歌曲
     */
    fun getSongsSorted(sortType: SortType): Flow<List<Song>> {
        return when (sortType) {
            SortType.DEFAULT -> songDao.getSongsOrderByDefault()
            SortType.NAME -> songDao.getSongsOrderByName()
            SortType.ARTIST -> songDao.getSongsOrderByArtist()
            SortType.DATE -> songDao.getSongsOrderByDate()
            SortType.DURATION -> songDao.getSongsOrderByDuration()
        }
    }
    
    /**
     * 检查歌曲是否存在
     */
    suspend fun isSongExists(path: String): Boolean = withContext(Dispatchers.IO) {
        songDao.isSongExists(path)
    }
    
    // ==================== 歌单相关 ====================
    
    /**
     * 获取所有歌单
     */
    fun getAllPlaylists(): Flow<List<Playlist>> = playlistDao.getAllPlaylists()
    
    /**
     * 获取所有歌单（LiveData）
     */
    fun getAllPlaylistsLive(): LiveData<List<Playlist>> = playlistDao.getAllPlaylistsLive()

    /**
     * 同步获取所有歌单
     */
    suspend fun getAllPlaylistsSync(): List<Playlist> = withContext(Dispatchers.IO) {
        playlistDao.getAllPlaylistsSync()
    }
    
    /**
     * 根据ID获取歌单
     */
    suspend fun getPlaylistById(playlistId: Long): Playlist? = withContext(Dispatchers.IO) {
        playlistDao.getPlaylistById(playlistId)
    }
    
    /**
     * 创建歌单
     */
    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val playlist = Playlist(
            name = name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        playlistDao.insertPlaylist(playlist)
    }
    
    /**
     * 更新歌单
     */
    suspend fun updatePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        val updatedPlaylist = playlist.copy(updatedAt = System.currentTimeMillis())
        playlistDao.updatePlaylist(updatedPlaylist)
    }
    
    /**
     * 删除歌单
     */
    suspend fun deletePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist)
    }
    
    /**
     * 添加歌曲到歌单
     */
    suspend fun addSongToPlaylist(playlistId: Long, songId: String) = withContext(Dispatchers.IO) {
        val count = playlistDao.getPlaylistSongCount(playlistId)
        val playlistSong = PlaylistSong(
            playlistId = playlistId,
            songId = songId,
            addedAt = System.currentTimeMillis(),
            position = count
        )
        playlistDao.addSongToPlaylist(playlistSong)

        // 自动更新歌单封面为最近添加的歌曲
        updatePlaylistCoverToLatest(playlistId)
    }
    
    /**
     * 批量添加歌曲到歌单
     */
    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<String>) = withContext(Dispatchers.IO) {
        val count = playlistDao.getPlaylistSongCount(playlistId)
        val playlistSongs = songIds.mapIndexed { index, songId ->
            PlaylistSong(
                playlistId = playlistId,
                songId = songId,
                addedAt = System.currentTimeMillis(),
                position = count + index
            )
        }
        playlistDao.addSongsToPlaylist(playlistSongs)

        // 自动更新歌单封面为最近添加的歌曲
        updatePlaylistCoverToLatest(playlistId)
    }
    
    /**
     * 从歌单中移除歌曲
     */
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) = withContext(Dispatchers.IO) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }
    
    /**
     * 获取歌单中的歌曲
     */
    fun getSongsInPlaylist(playlistId: Long): Flow<List<Song>> = 
        playlistDao.getSongsInPlaylist(playlistId)
    
    /**
     * 获取歌单中的歌曲（同步）
     */
    suspend fun getSongsInPlaylistSync(playlistId: Long): List<Song> = withContext(Dispatchers.IO) {
        playlistDao.getSongsInPlaylistSync(playlistId)
    }
    
    /**
     * 获取歌单歌曲数量
     */
    suspend fun getPlaylistSongCount(playlistId: Long): Int = withContext(Dispatchers.IO) {
        playlistDao.getPlaylistSongCount(playlistId)
    }
    
    /**
     * 检查歌曲是否在歌单中
     */
    suspend fun isSongInPlaylist(playlistId: Long, songId: String): Boolean = withContext(Dispatchers.IO) {
        playlistDao.isSongInPlaylist(playlistId, songId)
    }
    
    /**
     * 清空歌单
     */
    suspend fun clearPlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        playlistDao.clearPlaylist(playlistId)
    }

    /**
     * 更新歌单封面为最近添加的歌曲
     */
    suspend fun updatePlaylistCoverToLatest(playlistId: Long) = withContext(Dispatchers.IO) {
        val latestSong = playlistDao.getLatestSongInPlaylist(playlistId)
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withContext

        // 更新歌单的封面歌曲ID
        playlistDao.updatePlaylist(playlist.copy(coverSongId = latestSong?.id))
    }

    /**
     * 根据歌单封面歌曲ID获取封面歌曲
     */
    suspend fun getPlaylistCoverSong(playlistId: Long): Song? = withContext(Dispatchers.IO) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withContext null
        playlist.coverSongId?.let { songDao.getSongById(it) }
    }
    
    // ==================== 最近播放相关 ====================
    
    /**
     * 获取最近播放的歌曲
     */
    fun getRecentPlays(): Flow<List<Song>> = recentPlayDao.getRecentPlays()
    
    /**
     * 获取最近播放的歌曲（LiveData）
     */
    fun getRecentPlaysLive(): LiveData<List<Song>> = recentPlayDao.getRecentPlaysLive()
    
    /**
     * 添加播放记录
     */
    suspend fun addRecentPlay(songId: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // 每次播放都添加新的记录，而不是更新现有记录
        val recentPlay = RecentPlay(
            songId = songId,
            playedAt = now,
            playCount = 1
        )
        recentPlayDao.addRecentPlay(recentPlay)
    }
    
    /**
     * 清空最近播放记录
     */
    suspend fun clearRecentPlays() = withContext(Dispatchers.IO) {
        recentPlayDao.clearRecentPlays()
    }
    
    /**
     * 删除单个最近播放记录
     */
    suspend fun removeRecentPlay(songId: String) = withContext(Dispatchers.IO) {
        recentPlayDao.deleteRecentPlayBySongId(songId)
    }
    
    /**
     * 获取歌曲播放次数
     */
    suspend fun getPlayCount(songId: String): Int = withContext(Dispatchers.IO) {
        recentPlayDao.getPlayCount(songId)
    }
}
