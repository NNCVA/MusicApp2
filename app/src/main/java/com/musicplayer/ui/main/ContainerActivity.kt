package com.musicplayer.ui.main

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.musicplayer.MusicPlayerApplication
import com.musicplayer.R
import com.musicplayer.databinding.ActivityContainerBinding
import com.musicplayer.databinding.ContentPlayerDetailBinding
import com.musicplayer.databinding.LayoutPlayerBottomSheetBinding
import com.musicplayer.databinding.MiniPlayerBinding
import com.musicplayer.data.model.Song
import com.musicplayer.service.PlayerManager
import com.musicplayer.util.media.AlbumArtModelLoader
import com.musicplayer.util.media.EmbeddedLyricsExtractor
import com.musicplayer.util.ui.BottomCropDrawable
import com.musicplayer.util.media.LyricsParser
import com.musicplayer.ui.adapter.QueueAdapter
import kotlin.math.abs
import java.io.File

/**
 * 容器Activity，作为应用的入口点
 * 包含侧边导航栏、所有主页面Fragment和播放器Bottom Sheet
 */
class ContainerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContainerBinding
    private lateinit var playerBottomSheetBinding: LayoutPlayerBottomSheetBinding
    private lateinit var miniPlayerBinding: MiniPlayerBinding
    private lateinit var fullPlayerBinding: ContentPlayerDetailBinding

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    protected val playerManager = PlayerManager.getInstance()

    // 播放详情页相关
    private var lyrics: List<LyricsParser.LyricsLine> = emptyList()
    private var currentLyricsIndex = -1
    private var isSeeking = false

    // 专辑封面旋转动画相关
    private lateinit var rotateAnimator: ObjectAnimator
    private var shouldAnimate = false
    private var lastIsPlayingState: Boolean? = null
    private var currentSongId: String = ""
    private var isDuringSongChange = false
    private var lastUpdatedSongId: String? = null

    // 播放器视图状态
    private enum class PlayerView(val index: Int) {
        ALBUM_COVER(0),
        LYRICS(1),
        QUEUE(2);

        fun next(): PlayerView = values()[(index + 1) % 3]
    }

    // 滑动切换相关状态
    private var currentView = PlayerView.ALBUM_COVER
    private lateinit var queueAdapter: com.musicplayer.ui.adapter.QueueAdapter
    private lateinit var queuePositionTextView: TextView
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private val swipeThreshold by lazy {
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50f, resources.displayMetrics)
    }
    private val minVelocity = 500f // 降低最小滑动速度，使快速滑动更容易触发

    // 手势检测器
    private val gestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean {
                swipeStartX = e.rawX
                swipeStartY = e.rawY
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                // 只处理水平滑动
                if (abs(distanceX) > abs(distanceY)) {
                    updateViewTranslation(e2.rawX - swipeStartX)
                    return true
                }
                return false
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                // 只处理水平方向的快速滑动
                if (abs(velocityX) > abs(velocityY) && abs(velocityX) > minVelocity) {
                    if (velocityX < 0) {
                        // 左滑
                        when (currentView) {
                            PlayerView.ALBUM_COVER -> switchToView(PlayerView.LYRICS)
                            PlayerView.LYRICS -> switchToView(PlayerView.QUEUE)
                            PlayerView.QUEUE -> switchToView(PlayerView.ALBUM_COVER)
                        }
                    } else {
                        // 右滑
                        when (currentView) {
                            PlayerView.LYRICS -> switchToView(PlayerView.ALBUM_COVER)
                            PlayerView.QUEUE -> switchToView(PlayerView.LYRICS)
                            PlayerView.ALBUM_COVER -> {
                                // 专辑封面右滑无效果
                            }
                        }
                    }
                    return true
                }
                return false
            }

            // 移除 onSingleTapUp，不在每次点击时都回弹
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化播放器
        playerManager.initialize(this)

        // 设置Bottom Sheet
        setupBottomSheet()

        // 设置导航
        setupNavigation()

        // 设置迷你播放器和全屏播放器
        setupPlayerControls()

        // 设置专辑封面旋转动画
        setupAlbumRotation()

        // 注册播放器状态观察（只在 onCreate 中注册一次，防止重复注册）
        observePlayerState()

        // 更新初始状态
        updateMiniPlayer(playerManager.currentSong.value)
        updatePlayPauseButton(playerManager.isPlaying.value ?: false)

        // 处理从通知栏展开的请求
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.musicplayer.ACTION_EXPAND_PLAYER") {
            if (playerManager.currentSong.value != null) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun setupBottomSheet() {
        playerBottomSheetBinding = binding.playerBottomSheet
        miniPlayerBinding = playerBottomSheetBinding.miniPlayerContainer
        fullPlayerBinding = playerBottomSheetBinding.fullPlayerContent

        // 获取BottomSheetBehavior
        bottomSheetBehavior = BottomSheetBehavior.from(playerBottomSheetBinding.root)

        // 设置peekHeight为72dp (迷你播放栏高度)
        bottomSheetBehavior.peekHeight = resources.getDimensionPixelSize(R.dimen.mini_player_height)

        // 设置初始状态为折叠
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        // 设置BottomSheet回调
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // slideOffset: 0 (折叠) -> 1 (展开)
                // 当 slideOffset 达到 0.25 时，迷你播放栏完全透明

                // 迷你播放栏：slideOffset = 0.25 时 alpha = 0
                val miniAlpha = (1 - slideOffset * 4).coerceIn(0f, 1f)
                miniPlayerBinding.root.alpha = miniAlpha

                // 全屏播放内容：slideOffset = 0.25 时开始显示
                val fullAlpha = ((slideOffset - 0.25f) / 0.75f).coerceIn(0f, 1f)
                fullPlayerBinding.root.alpha = fullAlpha

                // 下滑箭头：随全屏内容一起
                playerBottomSheetBinding.btnCollapsePlayer.alpha = fullAlpha
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        // 展开时，重置展开请求
                        playerManager.resetExpandPlayerSheet()
                        // 禁用侧栏手势滑动
                        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        // 折叠时，重置展开请求
                        playerManager.resetExpandPlayerSheet()
                        // 恢复侧栏手势滑动
                        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    }
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        // 防止隐藏
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    }
                }
            }
        })

        // 下滑箭头点击
        playerBottomSheetBinding.btnCollapsePlayer.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        // 迷你播放栏点击 - 展开Bottom Sheet
        miniPlayerBinding.root.setOnClickListener {
            if (playerManager.currentSong.value != null) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 设置Toolbar为ActionBar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = null

        // 配置顶级目的地
        appBarConfiguration = AppBarConfiguration.Builder(
            R.id.nav_songs,
            R.id.nav_playlists,
            R.id.nav_recent,
            R.id.nav_scan
        ).setOpenableLayout(binding.drawerLayout)
            .build()

        // 将Toolbar与NavController关联
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)

        // 将NavigationView与NavController关联
        NavigationUI.setupWithNavController(binding.navView, navController)

        // 监听导航变化，更新标题
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

    private fun setupPlayerControls() {
        // === 迷你播放栏控制 ===
        setupMiniPlayerControls()

        // === 全屏播放器控制 ===
        setupFullPlayerControls()

        // === 播放队列视图 ===
        setupQueueView()
    }

    private fun setupAlbumRotation() {
        rotateAnimator = ObjectAnimator.ofFloat(fullPlayerBinding.ivAlbumCover, "rotation", 0f, 360f).apply {
            duration = 40000 // 40秒一圈
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    private fun updateAlbumCoverAnimation(isPlaying: Boolean) {
        // 如果正在切换歌曲，跳过此次更新，避免状态冲突
        if (isDuringSongChange) {
            return
        }

        shouldAnimate = isPlaying

        // 只有状态真正改变时才操作动画
        if (lastIsPlayingState == isPlaying) {
            return  // 状态没变，不做任何操作
        }
        lastIsPlayingState = isPlaying

        if (isPlaying) {
            // 如果动画处于暂停状态，恢复它（保持当前位置）
            if (rotateAnimator.isPaused) {
                rotateAnimator.resume()
            } else if (!rotateAnimator.isRunning) {
                // 如果动画没在运行，启动它
                rotateAnimator.start()
            }
            // 如果动画已经在运行，不需要做任何事
        } else {
            // 使用 pause 而不是 cancel，保持旋转位置
            if (rotateAnimator.isRunning) {
                rotateAnimator.pause()
            }
        }
    }

    private fun setupMiniPlayerControls() {
        // 播放/暂停按钮
        miniPlayerBinding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        // 上一首按钮
        miniPlayerBinding.btnPrevious.setOnClickListener {
            playerManager.skipToPrevious()
        }

        // 下一首按钮
        miniPlayerBinding.btnNext.setOnClickListener {
            playerManager.skipToNext()
        }

        // 设置歌曲标题的选中状态以启用跑马灯效果
        miniPlayerBinding.tvSongTitle.isSelected = true
        miniPlayerBinding.tvSongTitle.requestFocus()
    }

    private fun setupFullPlayerControls() {
        // 播放/暂停按钮
        fullPlayerBinding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        // 上一首按钮
        fullPlayerBinding.btnPrevious.setOnClickListener {
            playerManager.skipToPrevious()
        }

        // 下一首按钮
        fullPlayerBinding.btnNext.setOnClickListener {
            playerManager.skipToNext()
        }

        // 播放模式按钮
        fullPlayerBinding.btnShuffle.setOnClickListener {
            playerManager.togglePlayMode()
        }

        // 显示歌词按钮
        fullPlayerBinding.btnShowLyrics.setOnClickListener {
            toggleLyricsView()
        }

        // 设置歌曲标题的选中状态以启用跑马灯效果
        fullPlayerBinding.toolbarSongTitle.isSelected = true
        fullPlayerBinding.toolbarSongTitle.requestFocus()

        // 进度条
        fullPlayerBinding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = playerManager.duration.value ?: 0
                    val position = (duration * progress / 100f).toLong()
                    fullPlayerBinding.tvCurrentTime.text = formatTime(position)
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                val duration = playerManager.duration.value ?: 0
                val progress = seekBar?.progress ?: 0
                val position = (duration * progress / 100f).toLong()
                playerManager.seekTo(position)

                // 短暂延迟后手动更新歌词，确保 seek 完成后歌词焦点正确
                fullPlayerBinding.seekBar.postDelayed({
                    updateLyrics(position)
                    isSeeking = false
                }, 50)
            }
        })

        // 设置歌词视图的触摸事件处理，同时支持垂直滚动和水平滑动切换
        fullPlayerBinding.lyricsView.setOnTouchListener { _, event ->
            // 先让手势检测器处理事件
            gestureDetector.onTouchEvent(event)

            // 当歌词视图被触摸时，请求父视图不要拦截触摸事件
            fullPlayerBinding.lyricsView.parent?.requestDisallowInterceptTouchEvent(true)

            // 根据滑动方向决定是否消费事件
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.rawX
                    swipeStartY = event.rawY
                    false // 让 NestedScrollView 处理
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = abs(event.rawX - swipeStartX)
                    val deltaY = abs(event.rawY - swipeStartY)
                    // 如果水平滑动距离大于垂直滑动距离，可能是想要切换视图
                    if (deltaX > deltaY && deltaX > 30) {
                        true // 消费事件，阻止 NestedScrollView 滚动
                    } else {
                        false // 让 NestedScrollView 处理垂直滚动
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    true // 消费事件
                }
                else -> false
            }
        }

        // 设置播放队列视图的触摸事件处理，同时支持垂直滚动和水平滑动切换
        fullPlayerBinding.queueRecyclerView.setOnTouchListener { _, event ->
            // 先让手势检测器处理事件
            gestureDetector.onTouchEvent(event)

            // 当队列视图被触摸时，请求父视图不要拦截触摸事件
            fullPlayerBinding.queueRecyclerView.parent?.requestDisallowInterceptTouchEvent(true)

            // 根据滑动方向决定是否消费事件
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.rawX
                    swipeStartY = event.rawY
                    false // 让 RecyclerView 处理
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = abs(event.rawX - swipeStartX)
                    val deltaY = abs(event.rawY - swipeStartY)
                    // 如果水平滑动距离大于垂直滑动距离，可能是想要切换视图
                    if (deltaX > deltaY && deltaX > 30) {
                        true // 消费事件，阻止 RecyclerView 滚动
                    } else {
                        false // 让 RecyclerView 处理垂直滚动
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    true // 消费事件
                }
                else -> false
            }
        }

        // 初始化三视图位置
        fullPlayerBinding.root.post {
            val screenWidth = resources.displayMetrics.widthPixels.toFloat()
            fullPlayerBinding.albumCoverView.translationX = 0f
            fullPlayerBinding.lyricsView.translationX = screenWidth
            fullPlayerBinding.queueView.translationX = screenWidth * 2
        }

        // 为根视图设置手势监听（拦截整个页面的滑动手势）
        fullPlayerBinding.root.setOnTouchListener { _, event ->
            // 始终让手势检测器处理事件
            gestureDetector.onTouchEvent(event)

            // 根据事件类型决定是否消费
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.rawX
                    swipeStartY = event.rawY
                    true // 消费事件，让后续事件能传递到这里
                }
                MotionEvent.ACTION_MOVE -> {
                    true // 消费事件
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 基于滑动距离决定是否切换视图
                    val deltaX = event.rawX - swipeStartX
                    val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                    val threshold = screenWidth * 0.3f // 30% 屏幕宽度作为切换阈值

                    if (abs(deltaX) > threshold) {
                        // 达到阈值，执行切换
                        if (deltaX < 0) {
                            // 向左滑
                            when (currentView) {
                                PlayerView.ALBUM_COVER -> switchToView(PlayerView.LYRICS)
                                PlayerView.LYRICS -> switchToView(PlayerView.QUEUE)
                                PlayerView.QUEUE -> switchToView(PlayerView.ALBUM_COVER)
                            }
                        } else {
                            // 向右滑
                            when (currentView) {
                                PlayerView.LYRICS -> switchToView(PlayerView.ALBUM_COVER)
                                PlayerView.QUEUE -> switchToView(PlayerView.LYRICS)
                                PlayerView.ALBUM_COVER -> {
                                    // 专辑封面右滑无效果
                                }
                            }
                        }
                    } else {
                        // 未达到阈值，回弹到当前位置
                        resetViewTranslation()
                    }
                    true // 消费事件
                }
                else -> true // 消费所有其他事件
            }
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
        // 循环切换视图：专辑封面 → 歌词 → 播放队列 → 专辑封面
        switchToView(currentView.next())
    }

    /**
     * 初始化播放队列视图
     */
    private fun setupQueueView() {
        // 初始化位置显示 TextView
        queuePositionTextView = fullPlayerBinding.toolbarQueuePosition

        queueAdapter = com.musicplayer.ui.adapter.QueueAdapter(
            onSongClick = { song, position ->
                playerManager.playSong(song, playerManager.playlist.value ?: emptyList(), position)
            }
        )

        fullPlayerBinding.queueRecyclerView.apply {
            adapter = queueAdapter
            layoutManager = LinearLayoutManager(context)
        }

        // 观察播放队列变化
        playerManager.playlist.observe(this) { playlist ->
            queueAdapter.submitList(playlist)
            updateQueuePosition()
        }

        // 观察当前播放歌曲变化，高亮显示
        playerManager.currentSong.observe(this) { song ->
            queueAdapter.currentPlayingSongId = song?.id
            updateQueuePosition()
            scrollToCurrentSong()  // 切换歌曲时滚动到当前歌曲
        }
    }

    /**
     * 滚动到当前播放歌曲
     */
    private fun scrollToCurrentSong() {
        val currentIndex = playerManager.getCurrentIndex()
        if (currentIndex >= 0) {
            fullPlayerBinding.queueRecyclerView.post {
                val layoutManager = fullPlayerBinding.queueRecyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(currentIndex, 0)
            }
        }
    }

    /**
     * 更新队列位置显示
     */
    private fun updateQueuePosition() {
        val currentIndex = playerManager.getCurrentIndex()
        val totalCount = playerManager.playlist.value?.size ?: 0
        if (currentIndex >= 0 && totalCount > 0) {
            // 索引从0开始，显示时+1
            queuePositionTextView.text = "${currentIndex + 1}/$totalCount"
        } else {
            queuePositionTextView.text = "0/$totalCount"
        }
    }

    /**
     * 更新迷你播放栏的显示
     */
    private fun updateMiniPlayer(song: Song?) {
        if (song != null) {
            // 显示整个 Bottom Sheet
            playerBottomSheetBinding.root.visibility = View.VISIBLE
            miniPlayerBinding.root.visibility = View.VISIBLE

            // 只在歌曲真正变化时更新文本和跑马灯
            if (lastUpdatedSongId != song.id) {
                lastUpdatedSongId = song.id

                miniPlayerBinding.tvSongTitle.text = song.title
                miniPlayerBinding.tvSongTitle.isSelected = true
                miniPlayerBinding.tvArtist.text = song.artist

                // 加载专辑封面
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

                // 设置迷你播放栏背景
                val bottomCropDrawable = BottomCropDrawable(this, R.drawable.background)
                miniPlayerBinding.root.background = bottomCropDrawable
            }
        } else {
            lastUpdatedSongId = null
            // 隐藏整个 Bottom Sheet
            playerBottomSheetBinding.root.visibility = View.GONE
            miniPlayerBinding.root.visibility = View.GONE
        }
    }

    /**
     * 更新全屏播放器的歌曲信息
     */
    private fun updateFullPlayerSongInfo(song: Song) {
        fullPlayerBinding.toolbarSongTitle.text = song.title
        fullPlayerBinding.toolbarSongTitle.isSelected = true
        fullPlayerBinding.toolbarArtistName.text = song.artist

        // 只有歌曲ID真正改变时才重新加载封面
        if (currentSongId != song.id) {
            currentSongId = song.id

            // 标记开始切换歌曲，阻止动画状态更新
            isDuringSongChange = true

            // 取消当前的无限旋转动画
            if (rotateAnimator.isRunning || rotateAnimator.isPaused) {
                rotateAnimator.cancel()
            }

            // 获取当前旋转角度
            val currentRotation = fullPlayerBinding.ivAlbumCover.rotation

            // 创建反向旋转动画，将封面旋转回正（0度）
            val reverseRotateAnimator = ObjectAnimator.ofFloat(
                fullPlayerBinding.ivAlbumCover,
                "rotation",
                currentRotation,  // 从当前角度
                0f               // 旋转到0度（正向）
            ).apply {
                duration = 500  // 快速旋转 - 500ms
            }

            // 添加动画结束监听器
            reverseRotateAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // 反向旋转完成后，加载新的专辑封面
                    loadAlbumCoverImage(song)

                    // 修复：检查实时播放状态，而不是使用缓存的标志
                    val isCurrentlyPlaying = playerManager.isPlaying.value == true

                    // 只有音乐真正在播放时才启动无限旋转
                    if (isCurrentlyPlaying) {
                        // 重新创建主动画器，从0度开始
                        rotateAnimator = ObjectAnimator.ofFloat(
                            fullPlayerBinding.ivAlbumCover,
                            "rotation",
                            0f,      // 从0度开始
                            360f     // 旋转到360度
                        ).apply {
                            duration = 40000 // 40秒一圈
                            repeatCount = ValueAnimator.INFINITE
                            interpolator = LinearInterpolator()
                        }
                        // 启动无限旋转
                        rotateAnimator.start()
                    }

                    // 同步更新状态标志，确保与实际播放状态一致
                    shouldAnimate = isCurrentlyPlaying
                    lastIsPlayingState = isCurrentlyPlaying

                    // 歌曲切换完成，允许动画状态更新
                    isDuringSongChange = false
                }
            })

            // 启动反向旋转动画
            reverseRotateAnimator.start()
        }

        // 加载歌词
        loadLyrics()
    }

    /**
     * 加载专辑封面图片
     */
    private fun loadAlbumCoverImage(song: Song) {
        // 只有当 albumId 不为 0 时才尝试加载专辑封面
        if (song.albumId > 0) {
            val albumArtUri = AlbumArtModelLoader.AlbumArtUri(song.albumId, song.path)
            Glide.with(this)
                .load(albumArtUri)
                .placeholder(R.drawable.ic_play)
                .error(R.drawable.ic_play)
                .into(fullPlayerBinding.ivAlbumCover)
        } else {
            // 没有专辑封面时直接使用占位符
            fullPlayerBinding.ivAlbumCover.setImageResource(R.drawable.ic_play)
        }
    }

    /**
     * 更新播放/暂停按钮的状态
     */
    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        miniPlayerBinding.btnPlayPause.setImageResource(icon)
        fullPlayerBinding.btnPlayPause.setImageResource(icon)
    }

    /**
     * 更新进度
     */
    private fun updateProgress(position: Long) {
        val duration = playerManager.duration.value ?: 0
        if (duration > 0) {
            val progress = (position * 100 / duration).toInt()
            fullPlayerBinding.seekBar.progress = progress
        }
        fullPlayerBinding.tvCurrentTime.text = formatTime(position)
    }

    /**
     * 更新总时长
     */
    private fun updateDuration(duration: Long) {
        fullPlayerBinding.tvTotalTime.text = formatTime(duration)
    }

    /**
     * 更新播放模式按钮
     */
    private fun updatePlayModeButton(mode: com.musicplayer.data.model.PlayMode) {
        fullPlayerBinding.btnShuffle.setImageResource(mode.getIconResId())
    }

    /**
     * 加载歌词
     */
    private fun loadLyrics() {
        playerManager.currentSong.value?.let { song ->
            val lyricsFile = findLyricsFile(song)

            if (lyricsFile != null && lyricsFile.exists()) {
                lyrics = LyricsParser.parseLrcFile(lyricsFile)
                updateLyricsList()
            } else {
                val embeddedLyrics = EmbeddedLyricsExtractor.extractEmbeddedLyrics(song.path)
                if (!embeddedLyrics.isNullOrBlank()) {
                    if (LyricsParser.hasTimestamps(embeddedLyrics)) {
                        lyrics = LyricsParser.parseEmbeddedLyrics(embeddedLyrics)
                        updateLyricsList()
                    } else {
                        lyrics = emptyList()
                        fullPlayerBinding.tvLyrics.text = embeddedLyrics
                        // 清空三行歌词显示
                        updateThreeLineLyrics()
                    }
                } else {
                    lyrics = emptyList()
                    fullPlayerBinding.tvLyrics.text = getString(R.string.lyrics_not_found)
                    // 清空三行歌词显示
                    updateThreeLineLyrics()
                }
            }
        }
    }

    private fun findLyricsFile(song: Song): File? {
        val lrcFilePath = song.path.replaceAfterLast('.', "lrc")
        var lyricsFile = File(lrcFilePath)
        if (lyricsFile.exists()) return lyricsFile

        val possibleExtensions = arrayOf(".lrc", ".LRC")
        for (ext in possibleExtensions) {
            lyricsFile = File(song.path.substringBeforeLast('.') + ext)
            if (lyricsFile.exists()) return lyricsFile
        }

        val songTitle = song.title.replace("[\\/:*?\"<>|]", "")
        val parentDir = File(song.path).parentFile
        if (parentDir != null) {
            for (ext in possibleExtensions) {
                lyricsFile = File(parentDir, songTitle + ext)
                if (lyricsFile.exists()) return lyricsFile
            }
        }

        return null
    }

    private fun updateLyricsList() {
        if (lyrics.isEmpty()) {
            fullPlayerBinding.tvLyrics.text = getString(R.string.lyrics_not_found)
            return
        }
        updateLyricsHighlight()
        updateThreeLineLyrics()
    }

    private fun updateThreeLineLyrics() {
        if (lyrics.isEmpty()) {
            fullPlayerBinding.tvPreviousLyric.text = ""
            fullPlayerBinding.tvCurrentLyric.text = ""
            fullPlayerBinding.tvNextLyric.text = ""
            return
        }

        val currentIndex = currentLyricsIndex

        if (currentIndex > 0) {
            fullPlayerBinding.tvPreviousLyric.text = lyrics[currentIndex - 1].text
        } else {
            fullPlayerBinding.tvPreviousLyric.text = ""
        }

        if (currentIndex >= 0 && currentIndex < lyrics.size) {
            fullPlayerBinding.tvCurrentLyric.text = lyrics[currentIndex].text
        } else {
            fullPlayerBinding.tvCurrentLyric.text = ""
        }

        if (currentIndex < lyrics.size - 1) {
            fullPlayerBinding.tvNextLyric.text = lyrics[currentIndex + 1].text
        } else {
            fullPlayerBinding.tvNextLyric.text = ""
        }
    }

    private fun updateLyrics(currentPosition: Long) {
        if (lyrics.isEmpty()) return

        val index = LyricsParser.findCurrentLineIndex(lyrics, currentPosition)
        if (index != currentLyricsIndex) {
            currentLyricsIndex = index
            updateLyricsHighlight()
            updateThreeLineLyrics()
            if (index >= 0) {
                scrollToCurrentLyrics()
            }
        }
    }

    private fun updateLyricsHighlight() {
        if (lyrics.isEmpty()) {
            fullPlayerBinding.tvLyrics.text = getString(R.string.lyrics_not_found)
            return
        }

        val allLyrics = lyrics.joinToString("\n") { it.text }
        val spannableString = android.text.SpannableStringBuilder(allLyrics)

        if (currentLyricsIndex >= 0 && currentLyricsIndex < lyrics.size) {
            var startIndex = 0
            for (i in 0 until currentLyricsIndex) {
                startIndex += lyrics[i].text.length + 1
            }

            val endIndex = startIndex + lyrics[currentLyricsIndex].text.length

            val highlightColor = resources.getColor(R.color.black, theme)
            val normalColor = resources.getColor(R.color.text_secondary, theme)

            spannableString.setSpan(
                android.text.style.ForegroundColorSpan(normalColor),
                0,
                allLyrics.length,
                android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )

            spannableString.setSpan(
                android.text.style.ForegroundColorSpan(highlightColor),
                startIndex,
                endIndex,
                android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )

            spannableString.setSpan(
                android.text.style.RelativeSizeSpan(1.1f),
                startIndex,
                endIndex,
                android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )

            fullPlayerBinding.tvLyrics.text = spannableString
        } else {
            fullPlayerBinding.tvLyrics.text = allLyrics
        }
    }

    private fun scrollToCurrentLyrics() {
        if (lyrics.isEmpty() || currentLyricsIndex < 0) return

        val lineHeight = fullPlayerBinding.tvLyrics.lineHeight
        val scrollY = currentLyricsIndex * lineHeight - fullPlayerBinding.lyricsView.height / 2 + lineHeight / 2

        fullPlayerBinding.lyricsView.smoothScrollTo(0, scrollY)
    }

    override fun onResume() {
        super.onResume()
        // 观察者已在 onCreate 中注册，无需重复注册
    }

    /**
     * 观察播放器状态
     */
    private fun observePlayerState() {
        // 观察当前播放歌曲
        playerManager.currentSong.observe(this, Observer { song ->
            updateMiniPlayer(song)
            if (song != null) {
                updateFullPlayerSongInfo(song)
            }
        })

        // 观察播放状态
        playerManager.isPlaying.observe(this, Observer {
            updatePlayPauseButton(it)
            updateAlbumCoverAnimation(it)
        })

        // 观察播放位置
        playerManager.currentPosition.observe(this, Observer { position ->
            if (!isSeeking) {
                updateProgress(position)
                updateLyrics(position)
            }
        })

        // 观察总时长
        playerManager.duration.observe(this, Observer { duration ->
            updateDuration(duration)
        })

        // 观察播放模式
        playerManager.playMode.observe(this, Observer { mode ->
            updatePlayModeButton(mode)
        })

        // 观察展开请求
        playerManager.expandPlayerSheet.observe(this, Observer { shouldExpand ->
            if (shouldExpand && playerManager.currentSong.value != null) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                playerManager.resetExpandPlayerSheet()
            }
        })

        // 观察歌曲切换状态
        playerManager.isSwitching.observe(this, Observer { isSwitching ->
            if (isSwitching) {
                startMiniPlayerTransitionAnimation()
            } else {
                endMiniPlayerTransitionAnimation()
            }
        })
    }

    /**
     * 开始迷你播放器切换过渡动画
     */
    private fun startMiniPlayerTransitionAnimation() {
        // 迷你播放器封面缩放和淡出效果
        miniPlayerBinding.ivAlbumCover.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .alpha(0.7f)
            .setDuration(250)
            .start()

        // 歌曲信息淡出
        miniPlayerBinding.tvSongTitle.alpha = 0.5f
        miniPlayerBinding.tvArtist.alpha = 0.5f
    }

    /**
     * 结束迷你播放器切换过渡动画
     */
    private fun endMiniPlayerTransitionAnimation() {
        // 恢复迷你播放器封面
        miniPlayerBinding.ivAlbumCover.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(250)
            .start()

        // 歌曲信息恢复
        miniPlayerBinding.tvSongTitle.alpha = 1f
        miniPlayerBinding.tvArtist.alpha = 1f
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
    }

    override fun onBackPressed() {
        // 如果Bottom Sheet展开，先折叠
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            return
        }

        // 如果侧边栏打开，关闭侧边栏
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return
        }

        super.onBackPressed()
    }

    /**
     * 更新视图的 translationX 实现滑动预览效果
     */
    private fun updateViewTranslation(deltaX: Float) {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()

        when (currentView) {
            PlayerView.ALBUM_COVER -> {
                // 封面在中间，可以向左滑到队列
                val clampedTranslation = deltaX.coerceIn(-screenWidth, 0f)
                fullPlayerBinding.albumCoverView.translationX = clampedTranslation
                fullPlayerBinding.lyricsView.translationX = clampedTranslation + screenWidth
                fullPlayerBinding.queueView.translationX = clampedTranslation + screenWidth * 2
            }
            PlayerView.LYRICS -> {
                // 歌词在中间，可以向左右滑
                val clampedTranslation = deltaX.coerceIn(-screenWidth, screenWidth)
                fullPlayerBinding.albumCoverView.translationX = clampedTranslation - screenWidth
                fullPlayerBinding.lyricsView.translationX = clampedTranslation
                fullPlayerBinding.queueView.translationX = clampedTranslation + screenWidth
            }
            PlayerView.QUEUE -> {
                // 队列在中间，可以向右滑到歌词，向左滑到封面
                val clampedTranslation = deltaX.coerceIn(0f, screenWidth)
                fullPlayerBinding.albumCoverView.translationX = clampedTranslation - screenWidth * 2
                fullPlayerBinding.lyricsView.translationX = clampedTranslation - screenWidth
                fullPlayerBinding.queueView.translationX = clampedTranslation
            }
        }
    }

    /**
     * 重置视图位置（取消滑动时回弹）
     */
    private fun resetViewTranslation() {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val animationDuration = 200L

        when (currentView) {
            PlayerView.ALBUM_COVER -> {
                fullPlayerBinding.albumCoverView.animate()
                    .translationX(0f)
                    .setDuration(animationDuration)
                    .start()
                fullPlayerBinding.lyricsView.translationX = screenWidth
                fullPlayerBinding.queueView.translationX = screenWidth * 2
            }
            PlayerView.LYRICS -> {
                fullPlayerBinding.albumCoverView.translationX = -screenWidth
                fullPlayerBinding.lyricsView.animate()
                    .translationX(0f)
                    .setDuration(animationDuration)
                    .start()
                fullPlayerBinding.queueView.translationX = screenWidth
            }
            PlayerView.QUEUE -> {
                fullPlayerBinding.albumCoverView.translationX = -screenWidth * 2
                fullPlayerBinding.lyricsView.translationX = -screenWidth
                fullPlayerBinding.queueView.animate()
                    .translationX(0f)
                    .setDuration(animationDuration)
                    .start()
            }
        }
    }

    /**
     * 统一视图切换函数
     * 支持专辑封面、歌词、播放队列三视图切换
     */
    private fun switchToView(targetView: PlayerView) {
        if (targetView == currentView) return

        // 确保所有视图可见，实现 ViewPager 那样的拼接效果
        fullPlayerBinding.albumCoverView.visibility = View.VISIBLE
        fullPlayerBinding.albumCoverView.alpha = 1f
        fullPlayerBinding.lyricsView.visibility = View.VISIBLE
        fullPlayerBinding.lyricsView.alpha = 1f
        fullPlayerBinding.queueView.visibility = View.VISIBLE
        fullPlayerBinding.queueView.alpha = 1f

        val screenWidth = resources.displayMetrics.widthPixels.toFloat()

        when (targetView) {
            PlayerView.ALBUM_COVER -> {
                fullPlayerBinding.albumCoverView.animate()
                    .translationX(0f)
                    .setDuration(300)
                    .start()
                fullPlayerBinding.lyricsView.animate()
                    .translationX(screenWidth)
                    .setDuration(300)
                    .start()
                fullPlayerBinding.queueView.animate()
                    .translationX(screenWidth * 2)
                    .setDuration(300)
                    .start()
            }
            PlayerView.LYRICS -> {
                fullPlayerBinding.albumCoverView.animate()
                    .translationX(-screenWidth)
                    .setDuration(300)
                    .start()
                fullPlayerBinding.lyricsView.animate()
                    .translationX(0f)
                    .setDuration(300)
                    .start()
                fullPlayerBinding.queueView.animate()
                    .translationX(screenWidth)
                    .setDuration(300)
                    .start()
            }
            PlayerView.QUEUE -> {
                fullPlayerBinding.albumCoverView.animate()
                    .translationX(-screenWidth * 2)
                    .setDuration(300)
                    .start()
                fullPlayerBinding.lyricsView.animate()
                    .translationX(-screenWidth)
                    .setDuration(300)
                    .start()
                fullPlayerBinding.queueView.animate()
                    .translationX(0f)
                    .setDuration(300)
                    .start()
            }
        }

        currentView = targetView

        // 切换到队列视图时，滚动到当前歌曲
        if (targetView == PlayerView.QUEUE) {
            scrollToCurrentSong()
        }
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止动画以释放资源
        if (this::rotateAnimator.isInitialized && rotateAnimator.isRunning) {
            rotateAnimator.cancel()
        }
    }
}
