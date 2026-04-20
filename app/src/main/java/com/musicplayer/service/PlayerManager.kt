package com.musicplayer.service

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.musicplayer.data.model.PlayMode
import com.musicplayer.data.model.Song
import kotlinx.coroutines.*

/**
 * 播放器管理器
 * 统一管理播放状态和与Service的通信
 */
class PlayerManager private constructor() {
    
    // 播放状态
    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying
    
    private val _currentSong = MutableLiveData<Song?>(null)
    val currentSong: LiveData<Song?> = _currentSong
    
    private val _currentPosition = MutableLiveData(0L)
    val currentPosition: LiveData<Long> = _currentPosition
    
    private val _duration = MutableLiveData(0L)
    val duration: LiveData<Long> = _duration
    
    private val _playMode = MutableLiveData(PlayMode.ORDER)
    val playMode: LiveData<PlayMode> = _playMode
    
    private val _playlist = MutableLiveData<List<Song>>(emptyList())
    val playlist: LiveData<List<Song>> = _playlist

    // Bottom Sheet 展开请求
    private val _expandPlayerSheet = MutableLiveData(false)
    val expandPlayerSheet: LiveData<Boolean> = _expandPlayerSheet

    // 歌曲切换状态
    private val _isSwitching = MutableLiveData(false)
    val isSwitching: LiveData<Boolean> = _isSwitching

    private var updateJob: Job? = null
    private var serviceConnection: PlayerServiceConnection? = null
    private var lastKnownSongId: String? = null
    
    companion object {
        @Volatile
        private var instance: PlayerManager? = null
        
        fun getInstance(): PlayerManager {
            return instance ?: synchronized(this) {
                instance ?: PlayerManager().also { instance = it }
            }
        }
    }
    
    /**
     * 初始化播放器管理器
     */
    fun initialize(context: Context) {
        serviceConnection = PlayerServiceConnection(context) { service ->
            // 连接成功后同步状态
            _isPlaying.postValue(service.isPlaying())
            _currentSong.postValue(service.getCurrentSong())
            lastKnownSongId = service.getCurrentSong()?.id
            _currentPosition.postValue(service.getCurrentPosition())
            _duration.postValue(service.getDuration())
            _playMode.postValue(service.getCurrentPlayMode())

            // 开始更新位置
            startPositionUpdates()
        }

        serviceConnection?.bindService()
    }
    
    /**
     * 播放歌曲
     */
    fun playSong(song: Song, playlist: List<Song>, startIndex: Int = 0) {
        // 如果正在切换，忽略新请求
        if (_isSwitching.value == true) return

        CoroutineScope(Dispatchers.Main).launch {
            _isSwitching.postValue(true)

            // 触发淡出效果
            serviceConnection?.service?.startFadeOut(500)

            delay(200) // 500ms 延迟

            serviceConnection?.service?.playSong(song, playlist, startIndex)
            _playlist.postValue(playlist)
            _currentSong.postValue(song)

            // 淡入新歌曲
            serviceConnection?.service?.startFadeIn(500)

            _isSwitching.postValue(false)
        }
    }
    
    /**
     * 播放
     */
    fun play() {
        serviceConnection?.service?.play()
        // 立即同步播放状态
        syncPlayingState()
    }
    
    /**
     * 暂停
     */
    fun pause() {
        serviceConnection?.service?.pause()
        // 等待 ExoPlayer 完全过渡到暂停状态
        CoroutineScope(Dispatchers.Main).launch {
            delay(50) // 50ms延迟确保状态转换完成
            syncPlayingState()
        }
    }
    
    /**
     * 同步播放状态
     */
    private fun syncPlayingState() {
        serviceConnection?.service?.let { service ->
            _isPlaying.postValue(service.isPlaying())
        }
    }
    
    /**
     * 上一首
     */
    fun skipToPrevious() {
        if (_isSwitching.value == true) return

        CoroutineScope(Dispatchers.Main).launch {
            _isSwitching.postValue(true)
            serviceConnection?.service?.startFadeOut(500)
            delay(500)

            serviceConnection?.service?.skipToPrevious()
            delay(200) // 等待切换完成

            serviceConnection?.service?.startFadeIn(500)
            syncCurrentState()

            _isSwitching.postValue(false)
        }
    }
    
    /**
     * 下一首
     */
    fun skipToNext() {
        if (_isSwitching.value == true) return

        CoroutineScope(Dispatchers.Main).launch {
            _isSwitching.postValue(true)
            serviceConnection?.service?.startFadeOut(500)
            delay(500)

            serviceConnection?.service?.skipToNext()
            delay(200) // 等待切换完成

            serviceConnection?.service?.startFadeIn(500)
            syncCurrentState()

            _isSwitching.postValue(false)
        }
    }
    
    /**
     * 跳转到指定位置
     */
    fun seekTo(position: Long) {
        serviceConnection?.service?.seekTo(position)
    }
    
    /**
     * 切换播放模式
     */
    fun togglePlayMode() {
        val currentMode = _playMode.value ?: PlayMode.ORDER
        val nextMode = currentMode.next()
        serviceConnection?.service?.setPlayMode(nextMode)
        _playMode.postValue(nextMode)
    }
    
    /**
     * 设置播放模式
     */
    fun setPlayMode(mode: PlayMode) {
        serviceConnection?.service?.setPlayMode(mode)
        _playMode.postValue(mode)
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        serviceConnection?.service?.stop()
        updateJob?.cancel()
    }
    
    /**
     * 开始位置更新
     */
    private fun startPositionUpdates() {
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                delay(1000) // 每秒更新一次
                serviceConnection?.service?.let { service ->
                    _currentPosition.postValue(service.getCurrentPosition())
                    _duration.postValue(service.getDuration())
                    _isPlaying.postValue(service.isPlaying())

                    // 智能更新 currentSong：只在 songId 变化时更新
                    val currentSong = service.getCurrentSong()
                    if (currentSong?.id != lastKnownSongId) {
                        lastKnownSongId = currentSong?.id
                        _currentSong.postValue(currentSong)
                    }
                }
            }
        }
    }
    
    /**
     * 获取当前播放进度百分比
     */
    fun getProgress(): Float {
        val duration = _duration.value ?: 0L
        val position = _currentPosition.value ?: 0L
        
        return if (duration > 0) {
            position.toFloat() / duration.toFloat()
        } else {
            0f
        }
    }
    
    /**
     * 获取当前歌曲在播放列表中的索引
     */
    fun getCurrentIndex(): Int {
        val current = _currentSong.value ?: return -1
        val playlist = _playlist.value ?: return -1
        
        return playlist.indexOfFirst { it.id == current.id }
    }
    
    /**
     * 同步当前状态
     */
    private fun syncCurrentState() {
        serviceConnection?.service?.let { service ->
            _isPlaying.postValue(service.isPlaying())
            _currentSong.postValue(service.getCurrentSong())
            _currentPosition.postValue(service.getCurrentPosition())
            _duration.postValue(service.getDuration())
            _playMode.postValue(service.getCurrentPlayMode())
        }
    }

    /**
     * 请求展开播放器 Bottom Sheet
     */
    fun requestExpandPlayerSheet() {
        _expandPlayerSheet.postValue(true)
    }

    /**
     * 重置展开请求
     */
    fun resetExpandPlayerSheet() {
        _expandPlayerSheet.postValue(false)
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        updateJob?.cancel()
        serviceConnection?.unbindService()
        serviceConnection = null
    }
}

/**
 * 播放器服务连接
 */
class PlayerServiceConnection(
    private val context: Context,
    private val onConnected: (MusicPlaybackService) -> Unit
) {
    
    var service: MusicPlaybackService? = null
        private set
    
    private var isBound = false
    
    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
            if (binder is MusicPlaybackService.LocalBinder) {
                service = binder.service
                isBound = true
                onConnected(binder.service)
            }
        }
        
        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            service = null
            isBound = false
        }
    }
    
    fun bindService() {
        if (!isBound) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }
    
    fun unbindService() {
        if (isBound) {
            context.unbindService(connection)
            isBound = false
            service = null
        }
    }
}    

