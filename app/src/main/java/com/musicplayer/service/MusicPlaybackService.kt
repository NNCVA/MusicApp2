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
    
    /**
     * 设置 ExoPlayer 事件监听器。
     * 监听播放状态变化、曲目切换和播放/暂停状态，自动同步更新播放状态、通知栏和媒体元数据。
     * 曲目切换时异步加载专辑封面并将当前歌曲加入最近播放记录。
     */
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
    
    /**
     * 设置 MediaSession 回调，响应外部媒体控制事件（通知栏按钮、耳机按键、系统媒体控制等）。
     * 将所有控制操作委托给本服务对应的播放方法。
     */
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
    
    // ==================== 通知栏 ====================

    /**
     * 创建通知渠道（Android 8.0+）。
     * 使用低重要性级别以避免在状态栏弹出横幅，同时关闭角标显示。
     */
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
    
    /**
     * 根据当前播放状态更新通知栏。
     * 播放时调用 [startForeground] 保持前台服务；暂停时仅更新通知但停止前台状态，
     * 使系统可以按需回收服务。
     */
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
    
    /**
     * 构建通知栏 [Notification]。
     * 包含：点击跳转到主页面展开播放器、上一首/播放暂停/下一首三个控制按钮、
     * MediaStyle 样式以及歌曲标题、艺术家、专辑信息和封面大图标。
     *
     * @param song 当前正在播放的歌曲
     */
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
    
    // ==================== 播放状态与媒体元数据 ====================

    /**
     * 同步当前播放状态到 MediaSession。
     * 将 ExoPlayer 的播放/暂停状态、当前位置和可用操作（播放、暂停、上下首、拖动、停止）
     * 写入 [PlaybackStateCompat]，供通知栏和系统媒体控制使用。
     */
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
    
    /**
     * 为指定歌曲创建 MediaDescription。
     * 包含媒体 ID、标题、艺术家、专辑名、专辑封面 URI 和音频文件路径，
     * 用于构建 MediaMetadata 和 MediaBrowser 媒体项。
     *
     * @param song 歌曲对象
     * @return 包含完整媒体描述信息的 [MediaDescriptionCompat]
     */
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
    
    /**
     * 播放指定歌曲列表，从 [startIndex] 位置开始。
     * 将整个播放列表加载到 ExoPlayer，设置循环模式匹配当前播放模式，
     * 异步加载封面并更新通知栏和播放状态。
     *
     * @param song 用于获取媒体元数据的目标歌曲
     * @param playlist 完整播放列表
     * @param startIndex 起始播放位置索引，默认为 0
     */
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
    
    /**
     * 设置当前歌曲的媒体元数据到 MediaSession。
     * 写入标题、艺术家、专辑、时长、文件路径、封面 URI 和封面 Bitmap，
     * 供系统媒体控制和通知栏显示使用。
     *
     * @param song 歌曲对象
     * @param albumArt 专辑封面 Bitmap，可为 null
     */
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
    
    // ==================== 专辑封面加载 ====================

    /**
     * 异步加载专辑封面并更新媒体元数据和通知栏。
     * 在 IO 线程执行封面加载，完成后切回主线程设置元数据和刷新通知。
     */
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
    
    /**
     * 加载专辑封面 Bitmap，采用三级回退策略：
     * 1. 通过 MediaStore albumId 查询系统专辑封面
     * 2. 从音频文件中提取内嵌封面（Jaudiotagger）
     * 3. 使用默认占位图标
     *
     * @param song 歌曲对象
     * @return 专辑封面 Bitmap，任何异常情况下返回占位图
     */
    private fun loadAlbumArt(song: Song): Bitmap? {
        try {
            // 第一级回退：通过 MediaStore albumId 查询系统专辑封面数据库
            if (song.albumId > 0) {
                val albumArtByAlbumId = AlbumArtExtractor.getAlbumArtBitmap(contentResolver, song.albumId)
                if (albumArtByAlbumId != null) {
                    return albumArtByAlbumId
                }
            }

            // 第二级回退：使用 Jaudiotagger 从音频文件中提取内嵌封面
            val embeddedAlbumArt = AlbumArtExtractor.getEmbeddedAlbumArt(song.path)
            if (embeddedAlbumArt != null) {
                return embeddedAlbumArt
            }

            // 第三级回退：所有方式均失败，使用默认播放图标作为占位图
            return BitmapFactory.decodeResource(resources, R.drawable.ic_play)
        } catch (e: Exception) {
            e.printStackTrace()
            // 异常兜底：返回占位图确保不返回 null
            return BitmapFactory.decodeResource(resources, R.drawable.ic_play)
        }
    }
    
    /** 恢复播放，委托给 ExoPlayer */
    fun play() {
        exoPlayer.play()
    }
    
    /** 暂停播放，委托给 ExoPlayer */
    fun pause() {
        exoPlayer.pause()
    }
    
    /** 停止播放、释放前台服务状态并销毁服务 */
    fun stop() {
        exoPlayer.stop()
        stopForeground(true)
        stopSelf()
    }
    
    /**
     * 跳转到下一首歌曲，行为因播放模式而异：
     * - [PlayMode.ORDER]：顺序播放，到最后一首后回到第一首
     * - [PlayMode.SHUFFLE]：随机选择一首播放
     * - [PlayMode.REPEAT_ONE]：单曲循环下仍允许手动跳转下一首（同顺序逻辑）
     * 切歌后自动恢复播放。
     */
    fun skipToNext() {
        when (currentPlayMode) {
            // 顺序模式：前进到下一首，末尾循环回到开头
            PlayMode.ORDER -> {
                if (currentSongIndex < currentPlaylist.size - 1) {
                    exoPlayer.seekToNextMediaItem()
                } else if (currentSongIndex == currentPlaylist.size - 1 && currentPlaylist.isNotEmpty()) {
                    // 最后一首歌，回到列表开头
                    exoPlayer.seekTo(0, 0)
                }
                exoPlayer.play()
            }
            // 随机模式：从播放列表中随机选择一首
            PlayMode.SHUFFLE -> {
                if (currentPlaylist.isNotEmpty()) {
                    val randomIndex = Random().nextInt(currentPlaylist.size)
                    exoPlayer.seekTo(randomIndex, 0)
                    exoPlayer.play()
                }
            }
            // 单曲循环模式：手动切歌时仍按顺序逻辑跳转（ExoPlayer 的 repeatMode 在正常播放时会循环当前曲目）
            PlayMode.REPEAT_ONE -> {
                if (currentSongIndex < currentPlaylist.size - 1) {
                    exoPlayer.seekToNextMediaItem()
                } else if (currentSongIndex == currentPlaylist.size - 1 && currentPlaylist.isNotEmpty()) {
                    exoPlayer.seekTo(0, 0)
                }
                exoPlayer.play()
            }
        }
    }
    
    /**
     * 跳转到上一首歌曲。
     * 不是第一首时切换到上一首；是第一首时跳转到列表末尾（循环）。
     * 切歌后自动恢复播放。
     */
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
    
    /** 拖动进度到指定位置（毫秒），委托给 ExoPlayer */
    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
    }
    
    /**
     * 设置播放模式并同步 ExoPlayer 循环模式。
     * - [PlayMode.ORDER] / [PlayMode.SHUFFLE]：ExoPlayer 关闭循环（由服务自行管理边界）
     * - [PlayMode.REPEAT_ONE]：ExoPlayer 设为单曲循环
     */
    fun setPlayMode(mode: PlayMode) {
        currentPlayMode = mode
        
        // 根据播放模式设置ExoPlayer的循环模式
        exoPlayer.repeatMode = when (mode) {
            PlayMode.ORDER -> Player.REPEAT_MODE_OFF
            PlayMode.SHUFFLE -> Player.REPEAT_MODE_OFF
            PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
        }
    }
    
    /** 返回当前播放模式 */
    fun getCurrentPlayMode(): PlayMode = currentPlayMode

    /** 返回当前正在播放的歌曲，索引无效时返回 null */
    fun getCurrentSong(): Song? {
        return if (currentSongIndex >= 0 && currentSongIndex < currentPlaylist.size) {
            currentPlaylist[currentSongIndex]
        } else {
            null
        }
    }
    
    /** 返回当前播放位置（毫秒） */
    fun getCurrentPosition(): Long = exoPlayer.currentPosition

    /** 返回当前曲目总时长（毫秒） */
    fun getDuration(): Long = exoPlayer.duration

    /** 返回 ExoPlayer 是否正在播放 */
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
    
    /**
     * 返回媒体浏览根节点。
     * 当前实现返回固定的 "root" 根节点，不限制客户端访问。
     */
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("root", null)
    }
    
    /**
     * 加载指定父节点下的媒体子项列表。
     * 当前实现不暴露媒体库内容，直接返回空列表（播放列表由客户端自行管理）。
     */
    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(emptyList())
    }
    
    /**
     * 处理启动命令，将媒体按钮事件委托给 MediaSession。
     * 耳机按键、通知栏按钮等通过 Intent 传递的控制事件在此分发。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 处理媒体按钮事件
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }
    
    /**
     * 服务销毁时释放所有资源。
     * 依次取消音量动画、停用并释放 MediaSession、释放 ExoPlayer、取消协程作用域。
     */
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