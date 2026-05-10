package com.musicplayer.ui.main

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.musicplayer.R
import com.musicplayer.data.model.Song
import com.musicplayer.databinding.ActivityContainerBinding
import com.musicplayer.databinding.ContentPlayerDetailBinding
import com.musicplayer.databinding.LayoutPlayerBottomSheetBinding
import com.musicplayer.databinding.MiniPlayerBinding
import com.musicplayer.service.PlayerManager
import com.musicplayer.util.media.AlbumArtModelLoader
import com.musicplayer.util.ui.BottomCropDrawable

/**
 * 容器 Activity，作为应用的主入口。
 */
class ContainerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContainerBinding
    private lateinit var playerBottomSheetBinding: LayoutPlayerBottomSheetBinding
    private lateinit var miniPlayerBinding: MiniPlayerBinding
    private lateinit var fullPlayerBinding: ContentPlayerDetailBinding

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    /** 播放管理器单例，UI 层通过它控制播放，不直接接触 ExoPlayer */
    private val playerManager = PlayerManager.getInstance()

    private lateinit var lyricsController: PlayerLyricsController
    private lateinit var queueSectionBinder: QueueSectionBinder
    private lateinit var playerViewSwipeController: PlayerViewSwipeController

    private lateinit var rotateAnimator: ObjectAnimator
    /** 记录动画是否应该在播放状态下运行，用于歌曲切换后恢复动画 */
    private var shouldAnimate = false
    /** 缓存上一次播放状态，避免相同状态重复触发动画 start/pause */
    private var lastIsPlayingState: Boolean? = null
    private var currentSongId: String = ""
    /**
     * 歌曲切换进行中标记。
     * 切换期间跳过 updateAlbumCoverAnimation()，避免反向旋转动画和播放状态动画互相干扰。
     * 在反向旋转动画结束后重置为 false。
     */
    private var isDuringSongChange = false
    /** 用于迷你播放栏歌曲信息去重，同一首歌不重复加载封面和文本 */
    private var lastUpdatedSongId: String? = null

    /**
     * 初始化 Activity：绑定视图、初始化播放管理器、设置各子模块、
     * 同步当前播放状态到 UI，并处理可能携带展开播放器指令的 Intent。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playerManager.initialize(this)

        setupBottomSheet()
        setupNavigation()
        setupPlayerSections()
        setupPlayerControls()
        setupAlbumRotation()
        observePlayerState()

        // 恢复可能已存在的播放状态到 UI（冷启动或进程恢复场景）
        updateMiniPlayer(playerManager.currentSong.value)
        updatePlayPauseButton(playerManager.isPlaying.value ?: false)
        handleIntent(intent)
    }

    /**
     * 单实例模式下 Activity 已存在时收到新 Intent 的回调，
     * 委托给 handleIntent 处理展开播放器等指令。
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** 处理外部 Intent 携带的指令，当前仅支持 ACTION_EXPAND_PLAYER 展开全屏播放器。 */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.musicplayer.ACTION_EXPAND_PLAYER" &&
            playerManager.currentSong.value != null
        ) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    // ==================== BottomSheet 设置 ====================

    /**
     * 初始化 BottomSheetBehavior：绑定迷你播放栏和全屏播放器视图，
     * 设置 peekHeight（折叠态高度）和初始状态为折叠，
     * 注册滑动回调处理透明度渐变和状态切换时的抽屉锁定。
     */
    private fun setupBottomSheet() {
        playerBottomSheetBinding = binding.playerBottomSheet
        miniPlayerBinding = playerBottomSheetBinding.miniPlayerContainer
        fullPlayerBinding = playerBottomSheetBinding.fullPlayerContent

        bottomSheetBehavior = BottomSheetBehavior.from(playerBottomSheetBinding.root)
        bottomSheetBehavior.peekHeight = resources.getDimensionPixelSize(R.dimen.mini_player_height)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            /**
             * 滑动过程中实时调整迷你播放栏和全屏播放器的透明度，
             * 实现平滑的交叉淡入淡出效果：
             * slideOffset=0（折叠）→ 迷你栏完全可见；slideOffset=1（展开）→ 全屏完全可见。
             */
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // 迷你播放栏在滑动前 25% 区间内快速淡出
                val miniAlpha = (1 - slideOffset * 4).coerceIn(0f, 1f)
                miniPlayerBinding.root.alpha = miniAlpha

                // 全屏播放器在滑动 25%~100% 区间内淡入
                val fullAlpha = ((slideOffset - 0.25f) / 0.75f).coerceIn(0f, 1f)
                fullPlayerBinding.root.alpha = fullAlpha
                playerBottomSheetBinding.btnCollapsePlayer.alpha = fullAlpha
            }

            /**
             * 状态切换处理：
             * - EXPANDED：锁定导航抽屉防止滑动冲突，重置展开标记
             * - COLLAPSED：解锁导航抽屉，重置展开标记
             * - HIDDEN：禁止隐藏，强制回到折叠态（保证迷你播放栏始终可见）
             */
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        playerManager.resetExpandPlayerSheet()
                        // 全屏播放器展开时锁定侧边栏，避免手势冲突
                        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    }

                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        playerManager.resetExpandPlayerSheet()
                        // 播放器折叠后恢复侧边栏的正常滑动
                        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    }

                    // 强制不允许 HIDDEN 状态，保证迷你播放栏始终可见
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    }
                }
            }
        })

        playerBottomSheetBinding.btnCollapsePlayer.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        miniPlayerBinding.root.setOnClickListener {
            if (playerManager.currentSong.value != null) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    // ==================== 导航抽屉 ====================

    /**
     * 设置 Navigation 组件：绑定 NavHostFragment、Toolbar、ActionBar 配置和侧边栏导航，
     * 并注册目的地变化监听器以更新 Toolbar 标题。
     */
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = null

        appBarConfiguration = AppBarConfiguration.Builder(
            R.id.nav_songs,
            R.id.nav_playlists,
            R.id.nav_recent,
            R.id.nav_scan
        ).setOpenableLayout(binding.drawerLayout).build()

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)
        NavigationUI.setupWithNavController(binding.navView, navController)

        // 监听导航目的地变化，根据当前 Fragment 更新 Toolbar 标题文字
        navController.addOnDestinationChangedListener { _, destination, _ ->
            supportActionBar?.title = ""

            val titleResId = when (destination.id) {
                R.id.nav_songs -> R.string.title_songs
                R.id.nav_playlists -> R.string.title_playlists
                R.id.nav_recent -> R.string.title_recent_play
                R.id.nav_scan -> R.string.title_scan_music
                else -> R.string.app_name
            }
            binding.toolbarTitle.text = getString(titleResId)
        }
    }

    /**
     * 初始化全屏播放器的三个子模块：歌词控制器、播放队列绑定器、横向滑动控制器。
     * 滑动控制器的回调在切到队列页时触发滚动到当前播放歌曲。
     */
    private fun setupPlayerSections() {
        lyricsController = PlayerLyricsController(this, fullPlayerBinding, playerManager)
        queueSectionBinder = QueueSectionBinder(fullPlayerBinding, playerManager, this)
        playerViewSwipeController = PlayerViewSwipeController(fullPlayerBinding) {
            queueSectionBinder.scrollToCurrentSong()
        }
    }

    // ==================== 播放控制 ====================

    /** 组装迷你播放栏和全屏播放器的所有按钮事件，并绑定横向滑动控制器。 */
    private fun setupPlayerControls() {
        setupMiniPlayerControls()
        setupFullPlayerControls()
        playerViewSwipeController.bind()
    }

    // ==================== 专辑旋转 ====================

    /**
     * 创建专辑封面旋转动画器：无限循环、40 秒一圈、匀速线性插值器。
     * 实际启动/暂停由 updateAlbumCoverAnimation() 控制。
     */
    private fun setupAlbumRotation() {
        rotateAnimator = ObjectAnimator.ofFloat(fullPlayerBinding.ivAlbumCover, "rotation", 0f, 360f).apply {
            duration = 40000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    /**
     * 根据播放状态控制专辑封面旋转动画的启动/暂停。
     * 歌曲切换期间（isDuringSongChange=true）直接跳过，
     * 由 updateFullPlayerSongInfo() 中的反向旋转动画结束后统一处理。
     */
    private fun updateAlbumCoverAnimation(isPlaying: Boolean) {
        if (isDuringSongChange) return

        shouldAnimate = isPlaying

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

    /** 绑定迷你播放栏的播放/暂停、上一首、下一首按钮事件。 */
    private fun setupMiniPlayerControls() {
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
    }

    /** 绑定全屏播放器的所有控件：播放/暂停、上下首、播放模式、切页按钮、SeekBar 进度条。 */
    private fun setupFullPlayerControls() {
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

        fullPlayerBinding.toolbarSongTitle.isSelected = true
        fullPlayerBinding.toolbarSongTitle.requestFocus()

        fullPlayerBinding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            /** 用户拖动进度条时实时更新时间文字（不触发 seekTo） */
            override fun onProgressChanged(
                seekBar: android.widget.SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    val duration = playerManager.duration.value ?: 0
                    val position = (duration * progress / 100f).toLong()
                    fullPlayerBinding.tvCurrentTime.text = formatTime(position)
                }
            }

            /** 用户开始拖动时标记正在 seeking，暂停歌词自动滚动 */
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                lyricsController.isSeeking = true
            }

            /**
             * 用户松手后执行 seekTo，并在 50ms 延迟后：
             * 1. 强制刷新歌词到目标位置
             * 2. 解除 seeking 标记，恢复歌词自动滚动
             * 延迟是为了等 ExoPlayer 内部 position 更新完成再同步歌词。
             */
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                val duration = playerManager.duration.value ?: 0
                val progress = seekBar?.progress ?: 0
                val position = (duration * progress / 100f).toLong()
                playerManager.seekTo(position)

                fullPlayerBinding.seekBar.postDelayed({
                    lyricsController.updateLyrics(position)
                    lyricsController.isSeeking = false
                }, 50)
            }
        })
    }

    /** 切换播放/暂停状态，迷你播放栏和全屏播放器共用此方法。 */
    private fun togglePlayPause() {
        if (playerManager.isPlaying.value == true) {
            playerManager.pause()
        } else {
            playerManager.play()
        }
    }

    /** 顺序切换播放器页面：封面 -> 歌词 -> 播放队列 -> 封面。 */
    private fun toggleLyricsView() {
        playerViewSwipeController.showNextView()
    }

    /**
     * 更新迷你播放栏的歌曲信息和封面。
     * 通过 lastUpdatedSongId 去重，同一首歌不重复加载 Glide 封面和设置文本。
     * song 为 null 时隐藏整个 BottomSheet。
     */
    private fun updateMiniPlayer(song: Song?) {
        if (song != null) {
            playerBottomSheetBinding.root.visibility = View.VISIBLE
            miniPlayerBinding.root.visibility = View.VISIBLE

            if (lastUpdatedSongId != song.id) {
                lastUpdatedSongId = song.id

                miniPlayerBinding.tvSongTitle.text = song.title
                miniPlayerBinding.tvSongTitle.isSelected = true
                miniPlayerBinding.tvArtist.text = song.artist

                if (song.albumId > 0) {
                    val albumArtUri = AlbumArtModelLoader.AlbumArtUri(song.albumId, song.path)
                    Glide.with(this)
                        .load(albumArtUri)
                        .placeholder(R.drawable.ic_play)
                        .error(R.drawable.ic_play)
                        .into(miniPlayerBinding.ivAlbumCover)
                } else {
                    miniPlayerBinding.ivAlbumCover.setImageResource(R.drawable.ic_play)
                }

                val bottomCropDrawable = BottomCropDrawable(this, R.drawable.background)
                miniPlayerBinding.root.background = bottomCropDrawable
            }
        } else {
            lastUpdatedSongId = null
            playerBottomSheetBinding.root.visibility = View.GONE
            miniPlayerBinding.root.visibility = View.GONE
        }
    }

    // ==================== 歌曲切换处理 ====================

    /**
     * 更新全屏播放器的歌曲信息（标题、歌手、封面）。
     * 当歌曲 ID 发生变化时，执行切换动画序列：
     * 1. 设置 isDuringSongChange=true，暂停状态动画
     * 2. 取消当前旋转动画
     * 3. 启动反向旋转动画将封面归零
     * 4. 反向动画结束后加载新封面，根据播放状态决定是否重新启动旋转
     * 5. 重置 isDuringSongChange=false
     *
     * 每次调用都会触发歌词重新加载。
     */
    private fun updateFullPlayerSongInfo(song: Song) {
        fullPlayerBinding.toolbarSongTitle.text = song.title
        fullPlayerBinding.toolbarSongTitle.isSelected = true
        fullPlayerBinding.toolbarSongTitle.requestFocus()
        fullPlayerBinding.toolbarArtistName.text = song.artist

        if (currentSongId != song.id) {
            currentSongId = song.id
            isDuringSongChange = true

            if (rotateAnimator.isRunning || rotateAnimator.isPaused) {
                rotateAnimator.cancel()
            }

            val currentRotation = fullPlayerBinding.ivAlbumCover.rotation

            val reverseRotateAnimator = ObjectAnimator.ofFloat(
                fullPlayerBinding.ivAlbumCover,
                "rotation",
                currentRotation,
                0f
            ).apply {
                duration = 500
            }

            reverseRotateAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    loadAlbumCoverImage(song)

                    val isCurrentlyPlaying = playerManager.isPlaying.value == true
                    if (isCurrentlyPlaying) {
                        rotateAnimator = ObjectAnimator.ofFloat(
                            fullPlayerBinding.ivAlbumCover,
                            "rotation",
                            0f,
                            360f
                        ).apply {
                            duration = 40000
                            repeatCount = ValueAnimator.INFINITE
                            interpolator = LinearInterpolator()
                        }
                        rotateAnimator.start()
                    }

                    shouldAnimate = isCurrentlyPlaying
                    lastIsPlayingState = isCurrentlyPlaying
                    isDuringSongChange = false
                }
            })

            reverseRotateAnimator.start()
        }

        lyricsController.loadLyrics()
    }

    /** 通过 AlbumArtModelLoader 加载全屏播放器的专辑封面图片。 */
    private fun loadAlbumCoverImage(song: Song) {
        if (song.albumId > 0) {
            val albumArtUri = AlbumArtModelLoader.AlbumArtUri(song.albumId, song.path)
            Glide.with(this)
                .load(albumArtUri)
                .placeholder(R.drawable.ic_play)
                .error(R.drawable.ic_play)
                .into(fullPlayerBinding.ivAlbumCover)
        } else {
            fullPlayerBinding.ivAlbumCover.setImageResource(R.drawable.ic_play)
        }
    }

    /** 同步迷你播放栏和全屏播放器的播放/暂停按钮图标。 */
    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        miniPlayerBinding.btnPlayPause.setImageResource(icon)
        fullPlayerBinding.btnPlayPause.setImageResource(icon)
    }

    /** 更新全屏播放器的 SeekBar 进度和当前时间文字。 */
    private fun updateProgress(position: Long) {
        val duration = playerManager.duration.value ?: 0
        if (duration > 0) {
            val progress = (position * 100 / duration).toInt()
            fullPlayerBinding.seekBar.progress = progress
        }
        fullPlayerBinding.tvCurrentTime.text = formatTime(position)
    }

    /** 更新全屏播放器的总时长文字。 */
    private fun updateDuration(duration: Long) {
        fullPlayerBinding.tvTotalTime.text = formatTime(duration)
    }

    /** 更新全屏播放器的播放模式按钮图标（顺序/随机/单曲循环）。 */
    private fun updatePlayModeButton(mode: com.musicplayer.data.model.PlayMode) {
        fullPlayerBinding.btnShuffle.setImageResource(mode.getIconResId())
    }

    /**
     * 注册所有 PlayerManager LiveData 的观察者：
     * - currentSong：更新迷你栏和全屏歌曲信息
     * - isPlaying：更新按钮图标和专辑旋转动画
     * - currentPosition：更新进度条和歌词同步（seeking 期间暂停）
     * - duration：更新总时长
     * - playMode：更新播放模式图标
     * - expandPlayerSheet：响应外部请求展开播放器
     * - isSwitching：触发迷你栏切歌过渡动画
     */
    private fun observePlayerState() {
        playerManager.currentSong.observe(this, Observer { song ->
            updateMiniPlayer(song)
            if (song != null) {
                updateFullPlayerSongInfo(song)
            }
        })

        playerManager.isPlaying.observe(this, Observer {
            updatePlayPauseButton(it)
            updateAlbumCoverAnimation(it)
        })

        playerManager.currentPosition.observe(this, Observer { position ->
            if (!lyricsController.isSeeking) {
                updateProgress(position)
                lyricsController.updateLyrics(position)
            }
        })

        playerManager.duration.observe(this, Observer { duration ->
            updateDuration(duration)
        })

        playerManager.playMode.observe(this, Observer { mode ->
            updatePlayModeButton(mode)
        })

        playerManager.expandPlayerSheet.observe(this, Observer { shouldExpand ->
            if (shouldExpand && playerManager.currentSong.value != null) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                playerManager.resetExpandPlayerSheet()
            }
        })

        playerManager.isSwitching.observe(this, Observer { isSwitching ->
            if (isSwitching) {
                startMiniPlayerTransitionAnimation()
            } else {
                endMiniPlayerTransitionAnimation()
            }
        })
    }

    /** 切歌时迷你栏过渡动画：封面缩小+淡出，标题和歌手淡出。 */
    private fun startMiniPlayerTransitionAnimation() {
        miniPlayerBinding.ivAlbumCover.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .alpha(0.7f)
            .setDuration(250)
            .start()

        miniPlayerBinding.tvSongTitle.alpha = 0.5f
        miniPlayerBinding.tvArtist.alpha = 0.5f
    }

    /** 切歌完成后恢复迷你栏：封面恢复原始大小和不透明度，标题和歌手恢复可见。 */
    private fun endMiniPlayerTransitionAnimation() {
        miniPlayerBinding.ivAlbumCover.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(250)
            .start()

        miniPlayerBinding.tvSongTitle.alpha = 1f
        miniPlayerBinding.tvArtist.alpha = 1f
    }

    /** 处理 Toolbar 返回按钮的导航，委托给 NavigationUI。 */
    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
    }

    /**
     * 返回键处理优先级：全屏播放器展开 -> 折叠播放器；导航抽屉打开 -> 关闭抽屉；否则 -> 默认返回。
     */
    override fun onBackPressed() {
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            return
        }

        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return
        }

        super.onBackPressed()
    }

    /** 将毫秒转换为 "m:ss" 格式的时间字符串。 */
    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    /** 销毁时取消专辑旋转动画，防止泄漏。 */
    override fun onDestroy() {
        super.onDestroy()
        if (this::rotateAnimator.isInitialized && rotateAnimator.isRunning) {
            rotateAnimator.cancel()
        }
    }
}
