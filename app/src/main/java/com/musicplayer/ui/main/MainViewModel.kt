package com.musicplayer.ui.main

import android.app.Application
import androidx.lifecycle.*
import com.musicplayer.data.model.Song
import com.musicplayer.data.model.SortType
import com.musicplayer.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.musicplayer.MusicPlayerApplication
import com.musicplayer.data.model.Playlist

/**
 * 主界面ViewModel
 */
class MainViewModel(private val musicRepository: MusicRepository) : ViewModel() {
    
    private val _filteredSongs = MutableLiveData<List<Song>>()
    val filteredSongs: LiveData<List<Song>> = _filteredSongs
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _sortType = MutableLiveData(SortType.DEFAULT)
    val sortType: LiveData<SortType> = _sortType
    
    init {
        loadSongs()
    }

    // 加载歌曲
    private fun loadSongs() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                musicRepository.getSongsSorted(_sortType.value ?: SortType.DEFAULT).collect { songs ->
                    _filteredSongs.value = songs
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = "加载歌曲失败: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    /**
     * 刷新歌曲列表
     */
    fun refreshSongs() {
        loadSongs()
    }

    /**
     * 删除歌曲
     */
    fun deleteSong(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                musicRepository.deleteSong(song)
            } catch (e: Exception) {
                _errorMessage.postValue("删除歌曲失败: ${e.message}")
            }
        }
    }

    /**
     * 搜索歌曲
     */
    fun searchSongs(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                musicRepository.getAllSongs().collect { songs ->
                    _filteredSongs.value = songs
                }
            } else {
                musicRepository.searchSongs(query).collect { songs ->
                    _filteredSongs.value = songs
                }
            }
        }
    }

    /**
     * 设置排序类型
     */
    fun setSortType(sortType: SortType) {
        _sortType.value = sortType
        applySort()
    }

    /**
     * 切换排序类型
     */
    fun toggleSortType(): SortType {
        val currentType = _sortType.value ?: SortType.DEFAULT
        val nextType = when (currentType) {
            SortType.DEFAULT -> SortType.NAME
            SortType.NAME -> SortType.ARTIST
            SortType.ARTIST -> SortType.DATE
            SortType.DATE -> SortType.DURATION
            SortType.DURATION -> SortType.DEFAULT
        }
        _sortType.value = nextType
        
        // 应用新的排序
        applySort()
        
        return nextType
    }
    
    private fun applySort() {
        val sortType = _sortType.value ?: SortType.DEFAULT
        viewModelScope.launch {
            musicRepository.getSongsSorted(sortType).collect { songs ->
                _filteredSongs.value = songs
            }
        }
    }
    
    // 获取所有歌单
    fun getAllPlaylists(): LiveData<List<Playlist>> {
        return musicRepository.getAllPlaylistsLive()
    }

    // 同步获取所有歌单（用于一次性获取）
    suspend fun getAllPlaylistsSync(): List<Playlist> {
        return musicRepository.getAllPlaylistsSync()
    }
    
    // 创建歌单
    fun createPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                musicRepository.createPlaylist(name)
            } catch (e: Exception) {
                _errorMessage.postValue("创建歌单失败: ${e.message}")
            }
        }
    }
    
    // 创建歌单并添加歌曲
    fun createPlaylistAndAddSong(name: String, song: Song?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playlistId = musicRepository.createPlaylist(name)
                song?.let {
                    musicRepository.addSongToPlaylist(playlistId, it.id)
                }
            } catch (e: Exception) {
                _errorMessage.postValue("创建歌单失败: ${e.message}")
            }
        }
    }
    
    // 创建歌单并添加多首歌曲
    fun createPlaylistAndAddMultipleSongs(name: String, songs: List<Song>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playlistId = musicRepository.createPlaylist(name)
                for (song in songs) {
                    musicRepository.addSongToPlaylist(playlistId, song.id)
                }
            } catch (e: Exception) {
                _errorMessage.postValue("创建歌单失败: ${e.message}")
            }
        }
    }
    
    // 添加歌曲到歌单
    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                musicRepository.addSongToPlaylist(playlistId, song.id)
            } catch (e: Exception) {
                _errorMessage.postValue("添加歌曲到歌单失败: ${e.message}")
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}

/**
 * MainViewModel工厂
 */
class MainViewModelFactory(private val musicRepository: MusicRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(musicRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}