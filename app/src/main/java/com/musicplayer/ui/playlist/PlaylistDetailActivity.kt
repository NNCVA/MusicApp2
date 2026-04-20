package com.musicplayer.ui.playlist

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.musicplayer.MusicPlayerApplication
import com.musicplayer.R
import com.musicplayer.data.model.Song
import com.musicplayer.data.repository.MusicRepository
import com.musicplayer.databinding.ActivityPlaylistDetailBinding
import com.musicplayer.databinding.ContentPlayerDetailBinding
import com.musicplayer.databinding.LayoutPlayerBottomSheetBinding
import com.musicplayer.databinding.MiniPlayerBinding
import com.musicplayer.service.PlayerManager
import com.musicplayer.ui.base.BaseActivity
import com.musicplayer.ui.main.ContainerActivity
import com.musicplayer.ui.adapter.SongAdapter
import com.musicplayer.util.media.AlbumArtModelLoader
import com.musicplayer.util.media.EmbeddedLyricsExtractor
import com.musicplayer.util.media.LyricsParser
import com.musicplayer.util.ui.BottomCropDrawable
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * 歌单详情页Activity
 */
class PlaylistDetailActivity : BaseActivity() {
    companion object {
        private const val PLAYLIST_CHANGED_ACTION = "com.musicplayer.PLAYLIST_CHANGED"
    }

    private lateinit var binding: ActivityPlaylistDetailBinding
    private lateinit var viewModel: PlaylistDetailViewModel
    private lateinit var adapter: SongAdapter

    // Bottom Sheet 播放器相关
    private lateinit var playerBottomSheetBinding: LayoutPlayerBottomSheetBinding
    private lateinit var miniPlayerBinding: MiniPlayerBinding
    private lateinit var fullPlayerBinding: ContentPlayerDetailBinding
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

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

    // 多选模式标志
    private var isMultiSelectMode = false
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (isMultiSelectMode) {
            // 多选模式下，显示多选操作菜单
            menuInflater.inflate(R.menu.multi_select_menu, menu)
            
            // 更新全选按钮的图标状态
            val selectAllItem = menu?.findItem(R.id.action_select_all)
            selectAllItem?.let {
                if (adapter.isAllSelected()) {
                    // 已全选，显示选中状态图标
                    it.setIcon(R.drawable.ic_select_all_selected)
                } else {
                    // 未全选，显示未选中状态图标
                    it.setIcon(R.drawable.ic_select_all)
                }
            }
        } else {
            // 普通模式下，显示原菜单
            menuInflater.inflate(R.menu.playlist_detail_menu, menu)
        }
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // 多选模式下的操作
            R.id.action_select_all -> {
                // 切换全选状态
                if (adapter.isAllSelected()) {
                    // 已全选，取消全选
                    adapter.deselectAll()
                } else {
                    // 未全选，执行全选
                    adapter.selectAll()
                }
                // 更新工具栏
                updateMultiSelectToolbar()
                true
            }
            R.id.action_delete -> {
                // 批量删除歌曲
                if (adapter.selectedSongs.isNotEmpty()) {
                    showDeleteMultipleSongsDialog(adapter.selectedSongs.toList())
                }
                exitMultiSelectMode()
                true
            }
            R.id.action_cancel -> {
                // 取消多选模式
                exitMultiSelectMode()
                true
            }
            // 普通模式下的操作
            R.id.menu_clear_playlist -> {
                showClearPlaylistDialog()
                // 发送广播通知歌单列表页面更新
                notifyPlaylistChanged()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private var playlistId: Long = -1
    private var playlistName: String = ""

    private fun notifyPlaylistChanged() {
        sendBroadcast(Intent(PLAYLIST_CHANGED_ACTION).setPackage(packageName))
    }
    
    /**
     * 获取适合显示 Snackbar 的锚点视图
     * 优先使用 Activity 的 CoordinatorLayout，确保 Snackbar 正确显示
     */
    private fun getSnackbarAnchorView(): View {
        // 优先使用 Activity 的 CoordinatorLayout
        val coordinatorLayout = findViewById<View>(R.id.coordinator_layout)
        return coordinatorLayout ?: binding.root
    }

    /**
     * 显示 Snackbar，自动处理迷你播放栏的遮挡问题
     * 将 Snackbar 定位到迷你播放栏上方
     */
    private fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        val snackbar = Snackbar.make(getSnackbarAnchorView(), message, duration)

        val miniPlayer = findViewById<View>(R.id.mini_player_container)
        if (miniPlayer != null && miniPlayer.visibility == View.VISIBLE) {
            val snackbarView = snackbar.view
            val params = snackbarView.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams

            // 在布局完成后获取迷你播放栏的实际高度
            miniPlayer.post {
                val miniPlayerHeight = miniPlayer.height
                if (miniPlayerHeight > 0) {
                    // 设置底部边距为迷你播放栏的高度，使 Snackbar 显示在迷你播放栏上方
                    params.bottomMargin = miniPlayerHeight
                    params.setMargins(params.leftMargin, params.topMargin, params.rightMargin, miniPlayerHeight)
                    snackbarView.layoutParams = params
                }
            }
        }

        snackbar.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityPlaylistDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 获取传递的参数
        playlistId = intent.getLongExtra("playlist_id", -1)
        playlistName = intent.getStringExtra("playlist_name") ?: ""
        
        if (playlistId == -1L) {
            Toast.makeText(this, "无效的歌单", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // 设置Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 清除默认标题，避免重复显示
        supportActionBar?.title = null
        
        // 设置居中标题
        val toolbarTitle = binding.toolbar.findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle.text = playlistName
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        // 初始化ViewModel
        val musicRepository = (application as MusicPlayerApplication).musicRepository
        viewModel = ViewModelProvider(
            this,
            PlaylistDetailViewModelFactory(musicRepository, playlistId)
        )[PlaylistDetailViewModel::class.java]
        
        // 设置UI
        setupUI()

        // 设置Bottom Sheet
        setupBottomSheet()

        // 设置播放器控制
        setupPlayerControls()

        // 设置专辑封面旋转动画
        setupAlbumRotation()

        // 观察数据
        observeData()
    }
    
    private fun setupUI() {
        // 设置RecyclerView
        adapter = SongAdapter(
            onSongClick = { song, position ->
                playSong(song, position)
            },
            onSongMenuClick = { song, view ->
                showSongMenu(song, view)
            },
            onSongLongClick = { song, position ->
                // 进入多选模式
                enterMultiSelectMode(song, position)
            }
        )
        
        // 设置选中状态改变的回调
        adapter.onSelectionChanged = {
            updateMultiSelectToolbar()
        }
        
        binding.contentMain.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlaylistDetailActivity)
            adapter = this@PlaylistDetailActivity.adapter
        }

        // 设置正常模式操作栏按钮
        binding.actionBarMain.btnShuffle.setOnClickListener {
            shufflePlay()
        }

        // 设置多选模式操作栏按钮
        binding.actionBarMultiSelect.btnSelectAll.setOnClickListener {
            if (adapter.isAllSelected()) {
                adapter.deselectAll()
            } else {
                adapter.selectAll()
            }
            updateMultiSelectToolbar()
        }

        binding.actionBarMultiSelect.btnAddToPlaylist.setOnClickListener {
            // 歌单页面不需要"添加到歌单"功能，因为已经在歌单中
            exitMultiSelectMode()
        }

        binding.actionBarMultiSelect.btnDelete.setOnClickListener {
            if (adapter.selectedSongs.isNotEmpty()) {
                showDeleteMultipleSongsDialog(adapter.selectedSongs.toList())
            }
            exitMultiSelectMode()
        }

        binding.actionBarMultiSelect.btnCancel.setOnClickListener {
            exitMultiSelectMode()
        }

        // 设置下拉刷新
        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.swipeRefreshLayout.isRefreshing = true
            viewModel.refreshSongs()
            // 延迟隐藏刷新指示器
            binding.root.postDelayed({
                binding.swipeRefreshLayout.isRefreshing = false
            }, 500)
        }
    }
    
    // 进入多选模式
    private fun enterMultiSelectMode(selectedSong: Song, position: Int) {
        isMultiSelectMode = true
        adapter.isMultiSelectMode = true
        adapter.selectedSongs.add(selectedSong)
        adapter.notifyDataSetChanged()

        // 显示多选操作栏
        showMultiSelectToolbar()
    }

    // 显示多选工具栏
    private fun showMultiSelectToolbar() {
        // 隐藏正常模式操作栏
        binding.actionBarMain.root.visibility = View.GONE
        // 显示多选模式操作栏
        binding.actionBarMultiSelect.root.visibility = View.VISIBLE
        // 更新选中数量
        updateMultiSelectToolbar()
    }

    // 退出多选模式
    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        adapter.resetMultiSelectMode()

        // 显示正常模式操作栏
        binding.actionBarMain.root.visibility = View.VISIBLE
        // 隐藏多选模式操作栏
        binding.actionBarMultiSelect.root.visibility = View.GONE
    }

    // 更新多选工具栏
    private fun updateMultiSelectToolbar() {
        binding.actionBarMultiSelect.tvSelectedCount.text =
            getString(R.string.selected_count, adapter.selectedSongs.size)
        // 更新全选按钮图标
        val selectAllIcon = if (adapter.isAllSelected()) {
            R.drawable.ic_select_all_selected
        } else {
            R.drawable.ic_select_all
        }
        binding.actionBarMultiSelect.btnSelectAll.setImageResource(selectAllIcon)
    }

    // Override parent's miniPlayerBinding methods to use Bottom Sheet bindings
    // This prevents BaseActivity from using its own miniPlayerBinding
    // and the navigation listener that would cause double navigation
    override fun updateMiniPlayer(song: Song?) {
        // Use Bottom Sheet's mini player for display
        updateMiniPlayerDisplay(song)
    }

    override fun updatePlayPauseButton(isPlaying: Boolean) {
        // Use Bottom Sheet's buttons
        updatePlayPauseButtonIcons(isPlaying)
    }

    // Override onResume to skip parent's observePlayerState
    // We handle our own observations in observeData()
    override fun onResume() {
        super.onResume()
        // Don't call parent's observePlayerState() - we handle our own observations in observeData()
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
                        playerManager.resetExpandPlayerSheet()
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        playerManager.resetExpandPlayerSheet()
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

    private fun setupPlayerControls() {
        // === 迷你播放栏控制 ===
        setupMiniPlayerControls()

        // === 全屏播放器控制 ===
        setupFullPlayerControls()
    }

    private fun setupAlbumRotation() {
        rotateAnimator = ObjectAnimator.ofFloat(fullPlayerBinding.ivAlbumCover, "rotation", 0f, 360f).apply {
            duration = 40000 // 40秒一圈
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
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
    }

    private fun togglePlayPause() {
        if (playerManager.isPlaying.value == true) {
            playerManager.pause()
        } else {
            playerManager.play()
        }
    }

    private fun toggleLyricsView() {
        if (fullPlayerBinding.lyricsView.visibility == View.VISIBLE) {
            fullPlayerBinding.lyricsView.visibility = View.GONE
            fullPlayerBinding.albumCoverView.visibility = View.VISIBLE
        } else {
            fullPlayerBinding.lyricsView.visibility = View.VISIBLE
            fullPlayerBinding.albumCoverView.visibility = View.GONE
        }
    }

    /**
     * 更新迷你播放栏的显示
     */
    private fun updateMiniPlayerDisplay(song: Song?) {
        if (song != null) {
            // 显示整个 Bottom Sheet
            playerBottomSheetBinding.root.visibility = View.VISIBLE

            miniPlayerBinding.root.visibility = View.VISIBLE
            miniPlayerBinding.tvSongTitle.text = song.title
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
        } else {
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

                    // 检查实时播放状态
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
    private fun updatePlayPauseButtonIcons(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        miniPlayerBinding.btnPlayPause.setImageResource(icon)
        fullPlayerBinding.btnPlayPause.setImageResource(icon)
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
        } else {
            // 使用 pause 而不是 cancel，保持旋转位置
            if (rotateAnimator.isRunning) {
                rotateAnimator.pause()
            }
        }
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
                        updateThreeLineLyrics()
                    }
                } else {
                    lyrics = emptyList()
                    fullPlayerBinding.tvLyrics.text = getString(R.string.lyrics_not_found)
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

        val layout = fullPlayerBinding.tvLyrics.layout ?: return

        val lineTop = layout.getLineTop(currentLyricsIndex)
        val viewHeight = fullPlayerBinding.lyricsView.height
        val lineHeight = layout.getLineDescent(currentLyricsIndex) - lineTop

        val scrollY = lineTop - (viewHeight - lineHeight) / 2

        fullPlayerBinding.lyricsView.smoothScrollTo(0, scrollY.coerceAtLeast(0))
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    private fun shufflePlay() {
        viewModel.songs.value?.let { songs ->
            if (songs.isNotEmpty()) {
                val randomIndex = (0 until songs.size).random()
                playerManager.playSong(songs[randomIndex], songs, randomIndex)
            }
        }
    }

    private fun observeData() {
        viewModel.songs.observe(this, Observer { songs ->
            adapter.submitList(songs)
            updateSongCount(songs.size)
        })

        // 观察播放器状态
        playerManager.currentSong.observe(this, Observer { song ->
            adapter.updateCurrentPlayingSongId(song?.id)
            updateMiniPlayerDisplay(song)
            if (song != null) {
                updateFullPlayerSongInfo(song)
            }
        })

        // 观察播放状态
        playerManager.isPlaying.observe(this, Observer {
            updatePlayPauseButtonIcons(it)
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
            }
        })
    }

    private fun updateSongCount(count: Int) {
        binding.actionBarMain.tvSongCount.text =
            getString(R.string.song_count, count)
    }

    private fun playSong(song: Song, position: Int) {
        val songs = viewModel.songs.value ?: return

        // 检查点击的歌曲是否是当前正在播放的歌曲
        val currentSong = playerManager.currentSong.value
        if (currentSong != null && currentSong.id == song.id) {
            // 如果是当前播放的歌曲，展开Bottom Sheet
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        } else {
            // 如果不是当前播放的歌曲，播放该歌曲
            playerManager.playSong(song, songs, position)
        }
    }
    
    private fun showSongMenu(song: Song, anchorView: android.view.View) {
        val popupMenu = androidx.appcompat.widget.PopupMenu(this, anchorView)
        popupMenu.menuInflater.inflate(R.menu.song_menu, popupMenu.menu)
        
        // 移除添加到歌单选项，因为已经在歌单中
        popupMenu.menu.removeItem(R.id.menu_add_to_playlist)
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_play -> {
                    playSong(song, 0)
                    true
                }
                R.id.menu_song_info -> {
                    showSongInfoBottomSheet(song)
                    true
                }
                R.id.menu_delete -> {
                    removeSongFromPlaylist(song)
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }
    
    private fun removeSongFromPlaylist(song: Song) {
        viewModel.removeSongFromPlaylist(song)
        // 发送广播通知歌单列表页面更新
        notifyPlaylistChanged()
    }

    private fun showClearPlaylistDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("清空歌单")
            .setMessage("确定要清空歌单中的所有歌曲吗？")
            .setPositiveButton("清空") {
                dialog, _ ->
                viewModel.clearPlaylist()
                // 发送广播通知歌单列表页面更新
                notifyPlaylistChanged()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // 批量删除歌曲确认对话框
    private fun showDeleteMultipleSongsDialog(songs: List<Song>) {
        AlertDialog.Builder(this)
            .setTitle("删除歌曲")
            .setMessage("确定要删除选中的${songs.size}首歌曲吗？")
            .setPositiveButton("删除") {
                dialog, _ ->
                // 删除所有选中的歌曲
                for (song in songs) {
                    viewModel.removeSongFromPlaylist(song)
                }
                // 发送广播通知歌单列表页面更新
                notifyPlaylistChanged()
                showSnackbar("已删除${songs.size}首歌曲")
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onBackPressed() {
        // 如果Bottom Sheet展开，先折叠
        if (::bottomSheetBehavior.isInitialized &&
            bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            return
        }
        if (isMultiSelectMode) {
            // 多选模式下，返回键退出多选模式
            exitMultiSelectMode()
        } else {
            super.onBackPressed()
        }
    }

    private fun showSongInfoBottomSheet(song: Song) {
        val bottomSheet = com.musicplayer.ui.dialog.SongInfoBottomSheet.newInstance(song)
        bottomSheet.show(supportFragmentManager, com.musicplayer.ui.dialog.SongInfoBottomSheet.TAG)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止动画以释放资源
        if (::rotateAnimator.isInitialized && rotateAnimator.isRunning) {
            rotateAnimator.cancel()
        }
    }
}
