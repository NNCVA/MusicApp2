package com.musicplayer.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.musicplayer.data.model.Song
import com.musicplayer.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 歌单详情ViewModel
 */
class PlaylistDetailViewModel(
    private val musicRepository: MusicRepository,
    private val playlistId: Long
) : ViewModel() {

    val songs = musicRepository.getSongsInPlaylist(playlistId).asLiveData()

    fun removeSongFromPlaylist(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.removeSongFromPlaylist(playlistId, song.id)
        }
    }

    fun clearPlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            musicRepository.clearPlaylist(playlistId)
        }
    }

    fun refreshSongs() {
        // 触发 LiveData 刷新
        viewModelScope.launch(Dispatchers.IO) {
            // 重新查询数据库以触发 LiveData 更新
            val currentSongs = musicRepository.getSongsInPlaylist(playlistId)
            // LiveData 会自动更新
        }
    }
}

/**
 * ViewModel工厂
 */
class PlaylistDetailViewModelFactory(
    private val musicRepository: MusicRepository,
    private val playlistId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlaylistDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlaylistDetailViewModel(musicRepository, playlistId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
