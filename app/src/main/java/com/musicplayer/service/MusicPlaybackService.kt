package com.musicplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession as Media3MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import com.musicplayer.MusicPlayerApplication
import com.musicplayer.R
import com.musicplayer.data.model.PlayMode
import com.musicplayer.data.model.Song
import com.musicplayer.data.repository.MusicRepository
import com.musicplayer.ui.main.ContainerActivity
import com.musicplayer.util.media.AlbumArtExtractor
import kotlinx.coroutines.*
import java.util.*
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

/**
 * 音乐播放前台服务
 * 使用 ExoPlayer + MediaSession 实现
 */
class MusicPlaybackService : MediaBrowserServiceCompat() {
    
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var media3MediaSession: Media3MediaSession
    private lateinit var notificationManager: NotificationManager
    
    // 创建一个专门用于此服务的 CoroutineScope
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    private val musicRepository: MusicRepository by lazy {
        (application as MusicPlayerApplication).musicRepository
    }
    
    // 当前播放列表
    private var currentPlaylist: List<Song> = emptyList()
    private var currentPlayMode: PlayMode = PlayMode.ORDER
    private var currentSongIndex: Int = -1

    // 音量淡入淡出相关
    private var volumeAnimator: ValueAnimator? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "music_playback_channel"
        private const val CHANNEL_NAME = "音乐播放"
        private const val MAX_VOLUME = 1.0f
        private const val MIN_VOLUME = 0.0f
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化 ExoPlayer
        exoPlayer = ExoPlayer.Builder(this).build()
        
        // 初始化 MediaSession
        mediaSession = MediaSessionCompat(this, "MusicPlaybackService").apply {
            isActive = true
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or 
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        }
        
        // 设置 Media3 MediaSession
        media3MediaSession = Media3MediaSession.Builder(this, exoPlayer)
            .setId("MusicPlaybackService")
            .build()
        
        // 设置 session token
        sessionToken = mediaSession.sessionToken
        
        // 初始化通知管理器
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        // 设置播放器监听器
        setupPlayerListener()
        
        // 设置媒体会话回调
        setupMediaSessionCallback()
    }
    
    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
                updateNotification()
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                currentSongIndex = exoPlayer.currentMediaItemIndex
                
                // 更新媒体元数据
                if (currentSongIndex >= 0 && currentSongIndex < currentPlaylist.size) {
                    val currentSong = currentPlaylist[currentSongIndex]
                    // 异步加载专辑封面并设置媒体元数据
                    loadAlbumArtAsync(currentSong)
                    
                    // 添加到最近播放
                    serviceScope.launch {
                        musicRepository.addRecentPlay(currentSong.id)
                    }
                }
                
                updatePlaybackState()
                updateNotification()
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                updatePlaybackState()
                updateNotification()
            }
        })
    }
    
    private fun setupMediaSessionCallback() {
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                play()
            }
            
            override fun onPause() {
                pause()
            }
            
            override fun onSkipToNext() {
                skipToNext()
            }
            
            override fun onSkipToPrevious() {
                skipToPrevious()
            }
            
            override fun onSeekTo(pos: Long) {
                seekTo(pos)
            }
            
            override fun onStop() {
                stop()
            }
            
            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                // 从媒体ID播放
                mediaId?.let {
                    val songId = it // 直接使用字符串类型，与Song.id字段类型匹配
                    val songIndex = currentPlaylist.indexOfFirst { song -> song.id == songId }
                    if (songIndex >= 0) {
                        exoPlayer.seekTo(songIndex, 0)
                        exoPlayer.play()
                        // 异步加载专辑封面并设置媒体元数据
                        loadAlbumArtAsync(currentPlaylist[songIndex])
                        updatePlaybackState()
                        updateNotification()
                    }
                }
            }
        })
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放控制通知"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun updateNotification() {
        if (currentSongIndex >= 0 && currentSongIndex < currentPlaylist.size) {
            val song = currentPlaylist[currentSongIndex]
            val notification = buildNotification(song)
            
            // 根据播放状态决定是否保持前台服务
            if (exoPlayer.isPlaying) {
                startForeground(NOTIFICATION_ID, notification)
            } else {
                // 暂停时移除前台服务状态，但保留通知
                notificationManager.notify(NOTIFICATION_ID, notification)
                stopForeground(false)
            }
        }
    }
    
    private fun buildNotification(song: Song): Notification {
        // 创建跳转到ContainerActivity的Intent (请求展开Bottom Sheet)
        val intent = Intent(this, ContainerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // 通过Action传递展开请求
            action = "com.musicplayer.ACTION_EXPAND_PLAYER"
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 创建取消按钮Intent
        val cancelIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_STOP
        )
        
        // 播放/暂停操作
        val playPauseAction = NotificationCompat.Action(
            if (exoPlayer.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            if (exoPlayer.isPlaying) getString(R.string.pause) else getString(R.string.play),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                if (exoPlayer.isPlaying) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY
            )
        )
        
        // 上一首操作
        val prevAction = NotificationCompat.Action(
            R.drawable.ic_skip_previous,
            getString(R.string.previous),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
        )
        
        // 下一首操作
        val nextAction = NotificationCompat.Action(
            R.drawable.ic_skip_next,
            getString(R.string.next),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            )
        )
        
        // 加载专辑封面
        val albumArt = loadAlbumArt(song)
        
        // 创建通知构建器
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSubText(song.album)
            .setSmallIcon(R.drawable.ic_play)
            .setLargeIcon(albumArt)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(exoPlayer.isPlaying)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(cancelIntent)
            )
            
        // 添加专辑封面作为大图标
        if (albumArt != null) {
            notificationBuilder.setLargeIcon(albumArt)
        }
        
        return notificationBuilder.build()
    }
    
    private fun updatePlaybackState() {
        val state = if (exoPlayer.isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, exoPlayer.currentPosition, 1.0f)
            .build()
        
        mediaSession.setPlaybackState(playbackState)
    }
    
    private fun createMediaDescription(song: Song): MediaDescriptionCompat {
        // 获取专辑封面URI
        val albumArtUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            song.albumId
        )
        
        return MediaDescriptionCompat.Builder()
            .setMediaId(song.id.toString())
            .setTitle(song.title)
            .setSubtitle(song.artist)
            .setDescription(song.album)
            .setIconUri(albumArtUri)
            .setMediaUri(Uri.parse(song.path))
            .build()
    }
    
    // ==================== 播放控制方法 ====================
    
    fun playSong(song: Song, playlist: List<Song>, startIndex: Int = 0) {
        currentPlaylist = playlist
        currentSongIndex = startIndex
        
        val mediaItems = playlist.map { s ->
            MediaItem.fromUri(s.path)
        }
        
        exoPlayer.setMediaItems(mediaItems)
        exoPlayer.prepare()
        exoPlayer.seekTo(startIndex, 0)
        
        // 根据当前的播放模式设置ExoPlayer的循环模式
        exoPlayer.repeatMode = when (currentPlayMode) {
            PlayMode.ORDER -> Player.REPEAT_MODE_OFF
            PlayMode.SHUFFLE -> Player.REPEAT_MODE_OFF
            PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
        }
        
        exoPlayer.play()
        
        // 异步加载专辑封面并设置媒体元数据
        loadAlbumArtAsync(song)
        
        updatePlaybackState()
        updateNotification()
    }
    
    private fun setMediaMetadata(song: Song, albumArt: Bitmap?) {
        val mediaDescription = createMediaDescription(song)
        
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, song.id.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, song.path)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, mediaDescription.iconUri.toString())
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, albumArt)
            .build()
        
        mediaSession.setMetadata(metadata)
    }
    
    private fun loadAlbumArtAsync(song: Song) {
        // 在后台线程加载专辑封面
        serviceScope.launch(Dispatchers.IO) { 
            val albumArt = loadAlbumArt(song)
            // 回到主线程更新媒体元数据和通知
            withContext(Dispatchers.Main) { 
                setMediaMetadata(song, albumArt)
                updateNotification()
            }
        }
    }
    
    private fun loadAlbumArt(song: Song): Bitmap? {
        try {
            // 首先尝试通过albumId获取专辑封面
            if (song.albumId > 0) {
                val albumArtByAlbumId = AlbumArtExtractor.getAlbumArtBitmap(contentResolver, song.albumId)
                if (albumArtByAlbumId != null) {
                    return albumArtByAlbumId
                }
            }
            
            // 如果失败，尝试从音频文件中提取内嵌封面
            val embeddedAlbumArt = AlbumArtExtractor.getEmbeddedAlbumArt(song.path)
            if (embeddedAlbumArt != null) {
                return embeddedAlbumArt
            }
            
            // 如果都失败，使用占位符图片
            return BitmapFactory.decodeResource(resources, R.drawable.ic_play)
        } catch (e: Exception) {
            // 处理异常，返回占位符图片
            e.printStackTrace()
            return BitmapFactory.decodeResource(resources, R.drawable.ic_play)
        }
    }
    
    fun play() {
        exoPlayer.play()
    }
    
    fun pause() {
        exoPlayer.pause()
    }
    
    fun stop() {
        exoPlayer.stop()
        stopForeground(true)
        stopSelf()
    }
    
    fun skipToNext() {
        when (currentPlayMode) {
            PlayMode.ORDER -> {
                if (currentSongIndex < currentPlaylist.size - 1) {
                    exoPlayer.seekToNextMediaItem()
                } else if (currentSongIndex == currentPlaylist.size - 1 && currentPlaylist.isNotEmpty()) {
                    // 如果是最后一首歌，切换到第一首
                    exoPlayer.seekTo(0, 0)
                }
                // 切换歌曲后自动播放
                exoPlayer.play()
            }
            PlayMode.SHUFFLE -> {
                if (currentPlaylist.isNotEmpty()) {
                    val randomIndex = Random().nextInt(currentPlaylist.size)
                    exoPlayer.seekTo(randomIndex, 0)
                    // 切换歌曲后自动播放
                    exoPlayer.play()
                }
            }
            PlayMode.REPEAT_ONE -> {
                // 单曲循环模式下，允许跳转到下一首歌曲
                if (currentSongIndex < currentPlaylist.size - 1) {
                    exoPlayer.seekToNextMediaItem()
                } else if (currentSongIndex == currentPlaylist.size - 1 && currentPlaylist.isNotEmpty()) {
                    // 如果是最后一首歌，切换到第一首
                    exoPlayer.seekTo(0, 0)
                }
                // 切换歌曲后自动播放
                exoPlayer.play()
            }
        }
    }
    
    fun skipToPrevious() {
        if (currentSongIndex > 0) {
            // 如果不是第一首歌，切换到上一首
            exoPlayer.seekToPreviousMediaItem()
        } else if (currentSongIndex == 0 && currentPlaylist.isNotEmpty()) {
            // 如果是第一首歌，切换到最后一首
            exoPlayer.seekTo(currentPlaylist.size - 1, 0)
        }
        // 切换歌曲后自动播放
        exoPlayer.play()
    }
    
    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
    }
    
    fun setPlayMode(mode: PlayMode) {
        currentPlayMode = mode
        
        // 根据播放模式设置ExoPlayer的循环模式
        exoPlayer.repeatMode = when (mode) {
            PlayMode.ORDER -> Player.REPEAT_MODE_OFF
            PlayMode.SHUFFLE -> Player.REPEAT_MODE_OFF
            PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
        }
    }
    
    fun getCurrentPlayMode(): PlayMode = currentPlayMode
    
    fun getCurrentSong(): Song? {
        return if (currentSongIndex >= 0 && currentSongIndex < currentPlaylist.size) {
            currentPlaylist[currentSongIndex]
        } else {
            null
        }
    }
    
    fun getCurrentPosition(): Long = exoPlayer.currentPosition
    
    fun getDuration(): Long = exoPlayer.duration
    
    fun isPlaying(): Boolean = exoPlayer.isPlaying

    /**
     * 开始淡出效果
     * @param duration 淡出时长（毫秒）
     */
    fun startFadeOut(duration: Long) {
        volumeAnimator?.cancel()
        volumeAnimator = ValueAnimator.ofFloat(exoPlayer.volume, MIN_VOLUME).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                exoPlayer.volume = animator.animatedValue as Float
            }
            start()
        }
    }

    /**
     * 开始淡入效果
     * @param duration 淡入时长（毫秒）
     */
    fun startFadeIn(duration: Long) {
        volumeAnimator?.cancel()
        volumeAnimator = ValueAnimator.ofFloat(exoPlayer.volume, MAX_VOLUME).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                exoPlayer.volume = animator.animatedValue as Float
            }
            start()
        }
    }

    // ==================== MediaBrowserServiceCompat 方法 ====================
    
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("root", null)
    }
    
    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(emptyList())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 处理媒体按钮事件
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        volumeAnimator?.cancel()
        mediaSession.isActive = false
        mediaSession.release()
        media3MediaSession.release()
        exoPlayer.release()
        // 取消所有在此作用域中启动的协程
        serviceScope.cancel()
    }
    
    // ==================== LocalBinder ====================
    
    inner class LocalBinder : android.os.Binder() {
        val service: MusicPlaybackService
            get() = this@MusicPlaybackService
    }
    
    override fun onBind(intent: Intent?): android.os.IBinder {
        return LocalBinder()
    }
}