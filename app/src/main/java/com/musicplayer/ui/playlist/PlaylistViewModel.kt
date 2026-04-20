package com.musicplayer.ui.playlist

import androidx.lifecycle.*
import com.musicplayer.data.model.Playlist
import com.musicplayer.data.model.Song
import com.musicplayer.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 歌单ViewModel
 */
class PlaylistViewModel(private val repository: MusicRepository) : ViewModel() {

    val playlists = repository.getAllPlaylists().asLiveData()
    val musicRepository = repository

    fun createPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.createPlaylist(name)
        }
    }

    fun renamePlaylist(playlist: Playlist, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedPlaylist = playlist.copy(name = newName)
            repository.updatePlaylist(updatedPlaylist)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePlaylist(playlist)
        }
    }

    fun refreshPlaylists() {
        // 触发 LiveData 刷新
        playlists.value?.let {
            val newList = ArrayList(it)
        }
    }
}

/**
 * ViewModel工厂
 */
class PlaylistViewModelFactory(private val musicRepository: MusicRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlaylistViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlaylistViewModel(musicRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
