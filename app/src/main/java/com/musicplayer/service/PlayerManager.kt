package com.musicplayer.service

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.musicplayer.data.model.PlayMode
import com.musicplayer.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 播放管理器单例 — UI 与 [MusicPlaybackService] 之间的代理层。
 *
 * 所有播放操作（播放、暂停、切歌、拖进度等）统一经由此类转发到服务，
 * 同时通过 LiveData 对外暴露播放状态，供 UI 层观察。
 */
class PlayerManager private constructor() {

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

    private val _expandPlayerSheet = MutableLiveData(false)
    val expandPlayerSheet: LiveData<Boolean> = _expandPlayerSheet

    private val _isSwitching = MutableLiveData(false)
    val isSwitching: LiveData<Boolean> = _isSwitching

    /** 进度轮询协程句柄，用于取消定时更新 */
    private var updateJob: Job? = null
    /** 服务连接包装，持有 MusicPlaybackService 引用 */
    private var serviceConnection: PlayerServiceConnection? = null
    /** 上次已知的歌曲 ID，用于检测轮询期间歌曲是否变化 */
    private var lastKnownSongId: String? = null

    companion object {
        @Volatile
        private var instance: PlayerManager? = null

        /** 获取单例实例，双重检查锁定 */
        fun getInstance(): PlayerManager {
            return instance ?: synchronized(this) {
                instance ?: PlayerManager().also { instance = it }
            }
        }
    }

    /**
     * 初始化并绑定播放服务。
     *
     * 首次调用时创建 [PlayerServiceConnection]（使用 applicationContext 避免 Activity 泄漏），
     * 后续调用仅确保服务已绑定；若服务已可用则立即同步状态并启动进度轮询。
     */
    fun initialize(context: Context) {
        if (serviceConnection == null) {
            serviceConnection = PlayerServiceConnection(context.applicationContext) { service ->
                syncStateFromService(service)
                startPositionUpdates()
            }
        }

        serviceConnection?.bindService()
        serviceConnection?.service?.let { service ->
            syncStateFromService(service)
            startPositionUpdates()
        }
    }

    /**
     * 播放指定歌曲。
     *
     * 采用淡入淡出序列避免切歌时的突兀感：
     * 1. 设置切换守卫（isSwitching），防止重入
     * 2. 淡出当前音频（500ms）
     * 3. 等待 200ms 后切换到新歌曲
     * 4. 淡入新音频（500ms）
     * 5. 解除切换守卫
     */
    fun playSong(song: Song, playlist: List<Song>, startIndex: Int = 0) {
        if (_isSwitching.value == true) return

        CoroutineScope(Dispatchers.Main).launch {
            _isSwitching.postValue(true)
            serviceConnection?.service?.startFadeOut(500)
            delay(200)

            serviceConnection?.service?.playSong(song, playlist, startIndex)
            _playlist.postValue(playlist)
            _currentSong.postValue(song)

            serviceConnection?.service?.startFadeIn(500)
            _isSwitching.postValue(false)
        }
    }

    /** 恢复播放，并立即同步播放状态 */
    fun play() {
        serviceConnection?.service?.play()
        syncPlayingState()
    }

    /** 暂停播放，延迟 50ms 后同步状态以等待服务端完成暂停操作 */
    fun pause() {
        serviceConnection?.service?.pause()
        CoroutineScope(Dispatchers.Main).launch {
            delay(50)
            syncPlayingState()
        }
    }

    private fun syncPlayingState() {
        serviceConnection?.service?.let { service ->
            _isPlaying.postValue(service.isPlaying())
        }
    }

    /**
     * 切换到上一首歌曲。
     *
     * 与 [playSong] 类似的淡入淡出守卫模式：
     * isSwitching=true → 淡出 → 等待淡出完成 → 执行切歌 → 淡入 → 同步全部状态 → isSwitching=false。
     * 若 isSwitching 为 true 则直接返回，防止快速连切导致的状态混乱。
     */
    fun skipToPrevious() {
        if (_isSwitching.value == true) return

        CoroutineScope(Dispatchers.Main).launch {
            _isSwitching.postValue(true)
            serviceConnection?.service?.startFadeOut(500)
            delay(500)

            serviceConnection?.service?.skipToPrevious()
            delay(200)

            serviceConnection?.service?.startFadeIn(500)
            syncCurrentState()
            _isSwitching.postValue(false)
        }
    }

    /**
     * 切换到下一首歌曲。
     *
     * 执行流程与 [skipToPrevious] 相同，区别仅在于调用服务的 skipToNext。
     */
    fun skipToNext() {
        if (_isSwitching.value == true) return

        CoroutineScope(Dispatchers.Main).launch {
            _isSwitching.postValue(true)
            serviceConnection?.service?.startFadeOut(500)
            delay(500)

            serviceConnection?.service?.skipToNext()
            delay(200)

            serviceConnection?.service?.startFadeIn(500)
            syncCurrentState()
            _isSwitching.postValue(false)
        }
    }

    /** 拖动进度条到指定毫秒位置 */
    fun seekTo(position: Long) {
        serviceConnection?.service?.seekTo(position)
    }

    /** 循环切换播放模式：顺序 → 随机 → 单曲循环 → 顺序 */
    fun togglePlayMode() {
        val currentMode = _playMode.value ?: PlayMode.ORDER
        val nextMode = currentMode.next()
        serviceConnection?.service?.setPlayMode(nextMode)
        _playMode.postValue(nextMode)
    }

    /** 设置指定的播放模式 */
    fun setPlayMode(mode: PlayMode) {
        serviceConnection?.service?.setPlayMode(mode)
        _playMode.postValue(mode)
    }

    /** 停止播放并取消进度轮询 */
    fun stop() {
        serviceConnection?.service?.stop()
        updateJob?.cancel()
    }

    /**
     * 启动进度轮询循环。
     *
     * 每 1000ms 从服务端同步一次当前位置、总时长和播放状态。
     * 同时通过 lastKnownSongId 做变化检测——仅当歌曲 ID 发生变化时才更新 _currentSong，
     * 避免不必要的 LiveData 通知。
     * 若已有活跃的轮询任务则直接返回，不会重复启动。
     */
    private fun startPositionUpdates() {
        if (updateJob?.isActive == true) {
            return
        }

        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                delay(1000)
                serviceConnection?.service?.let { service ->
                    _currentPosition.postValue(service.getCurrentPosition())
                    _duration.postValue(service.getDuration())
                    _isPlaying.postValue(service.isPlaying())

                    // 仅当歌曲 ID 变化时才通知 UI，减少不必要的重组
                    val currentSong = service.getCurrentSong()
                    if (currentSong?.id != lastKnownSongId) {
                        lastKnownSongId = currentSong?.id
                        _currentSong.postValue(currentSong)
                    }
                }
            }
        }
    }

    /** 获取当前播放进度，返回 0f~1f 的比例值；时长为 0 时返回 0f */
    fun getProgress(): Float {
        val duration = _duration.value ?: 0L
        val position = _currentPosition.value ?: 0L
        return if (duration > 0) position.toFloat() / duration.toFloat() else 0f
    }

    /** 获取当前歌曲在播放列表中的索引，未找到则返回 -1 */
    fun getCurrentIndex(): Int {
        val current = _currentSong.value ?: return -1
        val playlist = _playlist.value ?: return -1
        return playlist.indexOfFirst { it.id == current.id }
    }

    /** 从服务端同步全部播放状态（切歌后调用） */
    private fun syncCurrentState() {
        serviceConnection?.service?.let { service ->
            syncStateFromService(service)
        }
    }

    /** 将服务端的播放状态一次性同步到所有 LiveData 字段 */
    private fun syncStateFromService(service: MusicPlaybackService) {
        _isPlaying.postValue(service.isPlaying())
        _currentSong.postValue(service.getCurrentSong())
        lastKnownSongId = service.getCurrentSong()?.id
        _currentPosition.postValue(service.getCurrentPosition())
        _duration.postValue(service.getDuration())
        _playMode.postValue(service.getCurrentPlayMode())
    }

    /** 请求 UI 展开迷你播放栏到全屏播放器（BottomSheet 展开） */
    fun requestExpandPlayerSheet() {
        _expandPlayerSheet.postValue(true)
    }

    /** 重置展开标记，UI 消费后调用 */
    fun resetExpandPlayerSheet() {
        _expandPlayerSheet.postValue(false)
    }

    /** 释放所有资源：取消轮询、解绑服务、清空引用 */
    fun cleanup() {
        updateJob?.cancel()
        updateJob = null
        serviceConnection?.unbindService()
        serviceConnection = null
        lastKnownSongId = null
    }
}

/**
 * 播放服务连接包装。
 *
 * 封装 [MusicPlaybackService] 的 bind/unbind 生命周期，
 * 通过 [onConnected] 回调通知调用方服务已就绪。
 * 使用 applicationContext 绑定，避免 Activity 被 ServiceConnection 引用链持有导致泄漏。
 */
class PlayerServiceConnection(
    private val context: Context,
    private val onConnected: (MusicPlaybackService) -> Unit
) {

    /** 已连接的服务实例，未连接时为 null */
    var service: MusicPlaybackService? = null
        private set

    /** 服务是否已绑定完成 */
    private var isBound = false
    /** 服务是否正在绑定中（防止重复调用 bindService） */
    private var isBinding = false

    /** 底层 ServiceConnection 实现，连接成功后提取 LocalBinder 并触发 onConnected 回调 */
    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(
            name: android.content.ComponentName?,
            binder: android.os.IBinder?
        ) {
            if (binder is MusicPlaybackService.LocalBinder) {
                service = binder.service
                isBound = true
                isBinding = false
                onConnected(binder.service)
            }
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            service = null
            isBound = false
            isBinding = false
        }
    }

    /**
     * 绑定播放服务。
     *
     * 仅在未绑定且未处于绑定中状态时发起绑定，
     * bindService 返回 false 时重置 isBinding 标记。
     */
    fun bindService() {
        if (!isBound && !isBinding) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            isBinding = true
            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                isBinding = false
            }
        }
    }

    /** 解绑播放服务并清空所有引用 */
    fun unbindService() {
        if (isBound) {
            context.unbindService(connection)
        }
        isBound = false
        isBinding = false
        service = null
    }
}
