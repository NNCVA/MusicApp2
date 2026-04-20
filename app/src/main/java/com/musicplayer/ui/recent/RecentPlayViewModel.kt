package com.musicplayer.ui.recent

import androidx.lifecycle.*
import com.musicplayer.data.model.Playlist
import com.musicplayer.data.model.Song
import com.musicplayer.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 最近播放ViewModel
 */
class RecentPlayViewModel(private val musicRepository: MusicRepository) : ViewModel() {

    val recentPlays = musicRepository.getRecentPlaysLive()

    fun removeFromRecentPlay(songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.removeRecentPlay(songId)
        }
    }

    fun clearRecentPlays() {
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.clearRecentPlays()
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.createPlaylist(name)
        }
    }

    fun createPlaylistAndAddSong(name: String, song: Song?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playlistId = musicRepository.createPlaylist(name)
                song?.let {
                    musicRepository.addSongToPlaylist(playlistId, it.id)
                }
            } catch (e: Exception) {
                // 错误处理可以在Activity中通过Snackbar显示
            }
        }
    }

    fun createPlaylistAndAddMultipleSongs(name: String, songs: List<Song>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playlistId = musicRepository.createPlaylist(name)
                for (song in songs) {
                    musicRepository.addSongToPlaylist(playlistId, song.id)
                }
            } catch (e: Exception) {
                // 错误处理可以在Activity中通过Snackbar显示
            }
        }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                musicRepository.addSongToPlaylist(playlistId, song.id)
            } catch (e: Exception) {
                // 错误处理可以在Activity中通过Snackbar显示
            }
        }
    }

    suspend fun getAllPlaylistsSync(): List<Playlist> {
        return musicRepository.getAllPlaylistsSync()
    }

    fun refreshRecentPlays() {
        // 触发 LiveData 刷新
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.getRecentPlaysLive()
        }
    }
}

/**
 * ViewModel工厂
 */
class RecentPlayViewModelFactory(private val musicRepository: MusicRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecentPlayViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RecentPlayViewModel(musicRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
