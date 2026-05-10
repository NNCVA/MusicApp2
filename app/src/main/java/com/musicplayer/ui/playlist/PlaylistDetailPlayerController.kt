package com.musicplayer.ui.playlist

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.musicplayer.R
import com.musicplayer.data.model.PlayMode
import com.musicplayer.data.model.Song
import com.musicplayer.databinding.LayoutPlayerBottomSheetBinding
import com.musicplayer.service.PlayerManager
import com.musicplayer.ui.main.PlayerLyricsController
import com.musicplayer.ui.main.PlayerViewSwipeController
import com.musicplayer.ui.main.QueueSectionBinder
import com.musicplayer.util.media.AlbumArtModelLoader
import com.musicplayer.util.ui.BottomCropDrawable

/**
 * 歌单详情页的播放器控制器。
 *
 * 镜像 [ContainerActivity] 中的播放器逻辑，但作用域限定在歌单详情页上下文。
 * 内部持有三个委托组件：[PlayerLyricsController]（歌词）、[QueueSectionBinder]（播放队列）、
 * [PlayerViewSwipeController]（三页横滑），并将播放控制统一转发给 [PlayerManager]。
 *
 * @param activity 宿主 Activity，用于 Glide 生命周期和资源访问
 * @param binding 底部弹出栏绑定，包含迷你播放栏和全屏播放器布局
 * @param playerManager 播放服务代理单例
 */
internal class PlaylistDetailPlayerController(
    private val activity: AppCompatActivity,
    private val binding: LayoutPlayerBottomSheetBinding,
    private val playerManager: PlayerManager
) {
    // ==================== 委托组件 ====================
    private val miniPlayerBinding = binding.miniPlayerContainer
    private val fullPlayerBinding = binding.fullPlayerContent
    private val bottomSheetBehavior = BottomSheetBehavior.from(binding.root)
    // 歌词加载与同步，委托给 PlayerLyricsController
    private val lyricsController = PlayerLyricsController(activity, fullPlayerBinding, playerManager)
    // 播放队列 RecyclerView 绑定，委托给 QueueSectionBinder
    private lateinit var queueSectionBinder: QueueSectionBinder
    // 三页横滑切换（封面/歌词/队列），委托给 PlayerViewSwipeController
    private lateinit var playerViewSwipeController: PlayerViewSwipeController

    // ==================== 专辑旋转动画状态 ====================
    // 专辑封面旋转动画器，播放时持续旋转，暂停时暂停
    private lateinit var rotateAnimator: ObjectAnimator
    // 上一次的播放状态，用于避免重复触发动画 start/pause
    private var lastIsPlayingState: Boolean? = null
    // 当前歌曲 ID，用于检测切歌
    private var currentSongId: String = ""
    // 切歌进行中标记，切歌期间跳过动画状态更新避免冲突
    private var isDuringSongChange = false

    /**
     * 是否正在拖动进度条。
     * 拖动期间歌词同步应暂停，避免进度跳动导致歌词闪烁。
     */
    val isSeeking: Boolean
        get() = lyricsController.isSeeking

    /**
     * 初始化播放器全部组件。
     * 依次设置 BottomSheet、三个子控制器、播放控件和专辑旋转动画。
     */
    fun setup() {
        setupBottomSheet()
        setupPlayerSections()
        setupPlayerControls()
        setupAlbumRotation()
    }

    /** 展开全屏播放器，仅在有当前歌曲时生效。 */
    fun expand() {
        if (playerManager.currentSong.value != null) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    /** 折叠回迷你播放栏。 */
    fun collapse() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    fun isExpanded(): Boolean = bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED

    fun updateMiniPlayer(song: Song?) {
        if (song != null) {
            binding.root.visibility = View.VISIBLE
            miniPlayerBinding.root.visibility = View.VISIBLE
            miniPlayerBinding.tvSongTitle.text = song.title
            miniPlayerBinding.tvArtist.text = song.artist

            if (song.albumId > 0) {
                val albumArtUri = AlbumArtModelLoader.AlbumArtUri(song.albumId, song.path)
                Glide.with(activity)
                    .load(albumArtUri)
                    .placeholder(R.drawable.ic_play)
                    .error(R.drawable.ic_play)
                    .into(miniPlayerBinding.ivAlbumCover)
            } else {
                miniPlayerBinding.ivAlbumCover.setImageResource(R.drawable.ic_play)
            }

            miniPlayerBinding.root.background = BottomCropDrawable(activity, R.drawable.background)
        } else {
            binding.root.visibility = View.GONE
            miniPlayerBinding.root.visibility = View.GONE
        }
    }

    /**
     * 更新全屏播放器的歌曲信息。
     * 切歌时执行专辑封面淡入淡出：先逆向旋转归零，再加载新封面并恢复旋转动画。
     */
    fun updateCurrentSong(song: Song) {
        fullPlayerBinding.toolbarSongTitle.text = song.title
        fullPlayerBinding.toolbarArtistName.text = song.artist

        if (currentSongId != song.id) {
            currentSongId = song.id
            // 标记切歌进行中，防止 updateAlbumCoverAnimation 在动画中途干预
            isDuringSongChange = true

            if (::rotateAnimator.isInitialized && (rotateAnimator.isRunning || rotateAnimator.isPaused)) {
                rotateAnimator.cancel()
            }

            // 切歌时先将封面旋转角度逆向归零，实现淡出效果
            val currentRotation = fullPlayerBinding.ivAlbumCover.rotation
            val reverseRotateAnimator = ObjectAnimator.ofFloat(
                fullPlayerBinding.ivAlbumCover,
                "rotation",
                currentRotation,
                0f
            ).apply {
                duration = 500
            }

            // 逆向旋转结束后：加载新封面图片，若正在播放则创建并启动新的旋转动画
            reverseRotateAnimator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    loadAlbumCoverImage(song)

                    val isCurrentlyPlaying = playerManager.isPlaying.value == true
                    if (isCurrentlyPlaying) {
                        rotateAnimator = createAlbumRotationAnimator()
                        rotateAnimator.start()
                    }

                    lastIsPlayingState = isCurrentlyPlaying
                    isDuringSongChange = false
                }
            })

            reverseRotateAnimator.start()
        }

        lyricsController.loadLyrics()
    }

    fun updatePlaybackState(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        miniPlayerBinding.btnPlayPause.setImageResource(icon)
        fullPlayerBinding.btnPlayPause.setImageResource(icon)
        updateAlbumCoverAnimation(isPlaying)
    }

    fun updateCurrentPosition(position: Long) {
        val duration = playerManager.duration.value ?: 0
        if (duration > 0) {
            fullPlayerBinding.seekBar.progress = (position * 100 / duration).toInt()
        }
        fullPlayerBinding.tvCurrentTime.text = formatTime(position)
        lyricsController.updateLyrics(position)
    }

    fun updateDuration(duration: Long) {
        fullPlayerBinding.tvTotalTime.text = formatTime(duration)
    }

    fun updatePlayMode(mode: PlayMode) {
        fullPlayerBinding.btnShuffle.setImageResource(mode.getIconResId())
    }

    fun release() {
        if (::rotateAnimator.isInitialized) {
            if (rotateAnimator.isRunning) {
                rotateAnimator.cancel()
            }
        }
        lyricsController.release()
        queueSectionBinder.release()
        playerViewSwipeController.release()
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior.peekHeight = activity.resources.getDimensionPixelSize(R.dimen.mini_player_height)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val miniAlpha = (1 - slideOffset * 4).coerceIn(0f, 1f)
                miniPlayerBinding.root.alpha = miniAlpha

                val fullAlpha = ((slideOffset - 0.25f) / 0.75f).coerceIn(0f, 1f)
                fullPlayerBinding.root.alpha = fullAlpha
                binding.btnCollapsePlayer.alpha = fullAlpha
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED,
                    BottomSheetBehavior.STATE_COLLAPSED -> playerManager.resetExpandPlayerSheet()

                    BottomSheetBehavior.STATE_HIDDEN -> bottomSheetBehavior.state =
                        BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        })

        binding.btnCollapsePlayer.setOnClickListener {
            collapse()
        }

        miniPlayerBinding.root.setOnClickListener {
            expand()
        }
    }

    private fun setupPlayerControls() {
        miniPlayerBinding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }
        miniPlayerBinding.btnPrevious.setOnClickListener {
            playerManager.skipToPrevious()
        }
        miniPlayerBinding.btnNext.setOnClickListener {
            playerManager.skipToNext()
        }
        miniPlayerBinding.tvSongTitle.isSelected = true
        miniPlayerBinding.tvSongTitle.requestFocus()

        fullPlayerBinding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }
        fullPlayerBinding.btnPrevious.setOnClickListener {
            playerManager.skipToPrevious()
        }
        fullPlayerBinding.btnNext.setOnClickListener {
            playerManager.skipToNext()
        }
        fullPlayerBinding.btnShuffle.setOnClickListener {
            playerManager.togglePlayMode()
        }
        fullPlayerBinding.btnShowLyrics.setOnClickListener {
            toggleLyricsView()
        }
        // ==================== 进度条 SeekBar 交互 ====================
        // 用户拖动进度条时实时更新时间文本；松手后 seek 到目标位置，
        // 延迟 50ms 更新歌词并解除 seeking 标记，避免歌词同步与 seek 竞争
        fullPlayerBinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = playerManager.duration.value ?: 0
                    val position = (duration * progress / 100f).toLong()
                    fullPlayerBinding.tvCurrentTime.text = formatTime(position)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // 拖动开始，暂停歌词同步
                lyricsController.isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val duration = playerManager.duration.value ?: 0
                val progress = seekBar?.progress ?: 0
                val position = (duration * progress / 100f).toLong()
                playerManager.seekTo(position)

                // 50ms 延迟补偿：等待 ExoPlayer 内部 seek 完成后再刷新歌词
                fullPlayerBinding.seekBar.postDelayed({
                    lyricsController.updateLyrics(position)
                    lyricsController.isSeeking = false
                }, 50)
            }
        })
        playerViewSwipeController.bind()
    }

    // ==================== 专辑旋转动画 ====================
    private fun setupAlbumRotation() {
        rotateAnimator = createAlbumRotationAnimator()
    }

    /** 创建专辑封面无限旋转动画，40 秒转一圈，匀速线性插值。 */
    private fun createAlbumRotationAnimator(): ObjectAnimator {
        return ObjectAnimator.ofFloat(fullPlayerBinding.ivAlbumCover, "rotation", 0f, 360f).apply {
            duration = 40000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    private fun togglePlayPause() {
        if (playerManager.isPlaying.value == true) {
            playerManager.pause()
        } else {
            playerManager.play()
        }
    }

    private fun toggleLyricsView() {
        playerViewSwipeController.showNextView()
    }

    private fun setupPlayerSections() {
        queueSectionBinder = QueueSectionBinder(fullPlayerBinding, playerManager, activity)
        playerViewSwipeController = PlayerViewSwipeController(fullPlayerBinding) {
            queueSectionBinder.scrollToCurrentSong()
        }
    }

    /** 通过 Glide 加载专辑封面，优先使用 albumId 查询 MediaStore，失败时回退到默认图标。 */
    private fun loadAlbumCoverImage(song: Song) {
        if (song.albumId > 0) {
            val albumArtUri = AlbumArtModelLoader.AlbumArtUri(song.albumId, song.path)
            Glide.with(activity)
                .load(albumArtUri)
                .placeholder(R.drawable.ic_play)
                .error(R.drawable.ic_play)
                .into(fullPlayerBinding.ivAlbumCover)
        } else {
            fullPlayerBinding.ivAlbumCover.setImageResource(R.drawable.ic_play)
        }
    }

    /**
     * 根据播放/暂停状态控制专辑封面旋转动画。
     * 切歌期间和状态未变化时跳过，避免重复 start/pause 导致动画跳帧。
     */
    private fun updateAlbumCoverAnimation(isPlaying: Boolean) {
        if (isDuringSongChange || !::rotateAnimator.isInitialized) {
            return
        }

        if (lastIsPlayingState == isPlaying) {
            return
        }
        lastIsPlayingState = isPlaying

        if (isPlaying) {
            if (rotateAnimator.isPaused) {
                rotateAnimator.resume()
            } else if (!rotateAnimator.isRunning) {
                rotateAnimator.start()
            }
        } else if (rotateAnimator.isRunning) {
            rotateAnimator.pause()
        }
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }
}
