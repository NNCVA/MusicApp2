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

    fun play() {
        serviceConnection?.service?.play()
        syncPlayingState()
    }

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

    fun seekTo(position: Long) {
        serviceConnection?.service?.seekTo(position)
    }

    fun togglePlayMode() {
        val currentMode = _playMode.value ?: PlayMode.ORDER
        val nextMode = currentMode.next()
        serviceConnection?.service?.setPlayMode(nextMode)
        _playMode.postValue(nextMode)
    }

    fun setPlayMode(mode: PlayMode) {
        serviceConnection?.service?.setPlayMode(mode)
        _playMode.postValue(mode)
    }

    fun stop() {
        serviceConnection?.service?.stop()
        updateJob?.cancel()
    }

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

                    val currentSong = service.getCurrentSong()
                    if (currentSong?.id != lastKnownSongId) {
                        lastKnownSongId = currentSong?.id
                        _currentSong.postValue(currentSong)
                    }
                }
            }
        }
    }

    fun getProgress(): Float {
        val duration = _duration.value ?: 0L
        val position = _currentPosition.value ?: 0L
        return if (duration > 0) position.toFloat() / duration.toFloat() else 0f
    }

    fun getCurrentIndex(): Int {
        val current = _currentSong.value ?: return -1
        val playlist = _playlist.value ?: return -1
        return playlist.indexOfFirst { it.id == current.id }
    }

    private fun syncCurrentState() {
        serviceConnection?.service?.let { service ->
            syncStateFromService(service)
        }
    }

    private fun syncStateFromService(service: MusicPlaybackService) {
        _isPlaying.postValue(service.isPlaying())
        _currentSong.postValue(service.getCurrentSong())
        lastKnownSongId = service.getCurrentSong()?.id
        _currentPosition.postValue(service.getCurrentPosition())
        _duration.postValue(service.getDuration())
        _playMode.postValue(service.getCurrentPlayMode())
    }

    fun requestExpandPlayerSheet() {
        _expandPlayerSheet.postValue(true)
    }

    fun resetExpandPlayerSheet() {
        _expandPlayerSheet.postValue(false)
    }

    fun cleanup() {
        updateJob?.cancel()
        updateJob = null
        serviceConnection?.unbindService()
        serviceConnection = null
        lastKnownSongId = null
    }
}

class PlayerServiceConnection(
    private val context: Context,
    private val onConnected: (MusicPlaybackService) -> Unit
) {

    var service: MusicPlaybackService? = null
        private set

    private var isBound = false
    private var isBinding = false

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

    fun unbindService() {
        if (isBound) {
            context.unbindService(connection)
        }
        isBound = false
        isBinding = false
        service = null
    }
}
