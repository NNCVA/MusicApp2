package com.musicplayer.ui.playlist

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.musicplayer.MusicPlayerApplication
import com.musicplayer.R
import com.musicplayer.data.model.Song
import com.musicplayer.databinding.ActivityPlaylistDetailBinding
import com.musicplayer.ui.adapter.SongAdapter
import com.musicplayer.ui.base.BaseActivity
import com.musicplayer.ui.common.showDeleteSongsConfirmDialog
import com.musicplayer.ui.common.showPlayerSnackbar

/**
 * 歌单详情页 Activity
 *
 * 展示歌单内歌曲列表，支持播放、多选删除、清空歌单等操作。
 * 集成 [PlaylistDetailPlayerController] 管理迷你播放栏与全屏播放器。
 * 通过 [PLAYLIST_CHANGED_ACTION] 广播通知其他页面歌单数据已变更。
 */
class PlaylistDetailActivity : BaseActivity() {
    companion object {
        /** 歌单变更广播 Action，通知 PlaylistsFragment 等页面刷新 */
        private const val PLAYLIST_CHANGED_ACTION = "com.musicplayer.PLAYLIST_CHANGED"
    }

    private lateinit var binding: ActivityPlaylistDetailBinding
    private lateinit var viewModel: PlaylistDetailViewModel
    private lateinit var adapter: SongAdapter
    private lateinit var playerController: PlaylistDetailPlayerController
    private val stopRefreshingRunnable = Runnable {
        binding.swipeRefreshLayout.isRefreshing = false
    }

    private var isMultiSelectMode = false
    private var playlistId: Long = -1
    private var playlistName: String = ""

    // ==================== 菜单处理 ====================

    /** 根据多选模式状态动态切换菜单：多选时显示全选/删除/取消，否则显示歌单详情菜单 */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (isMultiSelectMode) {
            menuInflater.inflate(R.menu.multi_select_menu, menu)
            val selectAllItem = menu?.findItem(R.id.action_select_all)
            // 根据当前是否全选切换图标：已全选时显示取消全选图标，否则显示全选图标
            selectAllItem?.setIcon(
                if (adapter.isAllSelected()) {
                    R.drawable.ic_select_all_selected
                } else {
                    R.drawable.ic_select_all
                }
            )
        } else {
            menuInflater.inflate(R.menu.playlist_detail_menu, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_select_all -> {
                if (adapter.isAllSelected()) {
                    adapter.deselectAll()
                } else {
                    adapter.selectAll()
                }
                updateMultiSelectToolbar()
                true
            }

            R.id.action_delete -> {
                if (adapter.selectedSongs.isNotEmpty()) {
                    showDeleteMultipleSongsDialog(adapter.selectedSongs.toList())
                }
                exitMultiSelectMode()
                true
            }

            R.id.action_cancel -> {
                exitMultiSelectMode()
                true
            }

            R.id.menu_clear_playlist -> {
                showClearPlaylistDialog()
                notifyPlaylistChanged()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 初始化歌单详情页
     *
     * 从 Intent 中读取歌单 ID 和名称，校验有效性后初始化 Toolbar、ViewModel、
     * UI 组件、播放器控制器并开始观察数据变化。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPlaylistDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistId = intent.getLongExtra("playlist_id", -1)
        playlistName = intent.getStringExtra("playlist_name") ?: ""

        if (playlistId == -1L) {
            Toast.makeText(this, "无效的歌单", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = null

        val toolbarTitle = binding.toolbar.findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle.text = playlistName
        binding.toolbar.setNavigationOnClickListener { finish() }

        val musicRepository = (application as MusicPlayerApplication).musicRepository
        viewModel = ViewModelProvider(
            this,
            PlaylistDetailViewModelFactory(musicRepository, playlistId)
        )[PlaylistDetailViewModel::class.java]

        setupUI()
        setupPlayerController()
        observeData()
    }

    // ==================== 歌单变化广播 ====================

    /**
     * 发送歌单变更广播
     *
     * 使用 [PLAYLIST_CHANGED_ACTION] 配合 setPackage 限制为本应用内广播，
     * 通知 PlaylistsFragment 等页面刷新歌单列表数据。
     */
    private fun notifyPlaylistChanged() {
        sendBroadcast(Intent(PLAYLIST_CHANGED_ACTION).setPackage(packageName))
    }

    private fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        showPlayerSnackbar(message, binding.root, duration)
    }

    private fun setupUI() {
        adapter = SongAdapter(
            onSongClick = { song, position -> playSong(song, position) },
            onSongMenuClick = { song, view -> showSongMenu(song, view) },
            onSongLongClick = { song, position -> enterMultiSelectMode(song, position) }
        )

        adapter.onSelectionChanged = {
            updateMultiSelectToolbar()
        }

        binding.contentMain.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlaylistDetailActivity)
            adapter = this@PlaylistDetailActivity.adapter
        }

        binding.actionBarMain.btnShuffle.setOnClickListener {
            shufflePlay()
        }

        binding.actionBarMultiSelect.btnSelectAll.setOnClickListener {
            if (adapter.isAllSelected()) {
                adapter.deselectAll()
            } else {
                adapter.selectAll()
            }
            updateMultiSelectToolbar()
        }

        binding.actionBarMultiSelect.btnAddToPlaylist.setOnClickListener {
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

        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.swipeRefreshLayout.isRefreshing = true
            viewModel.refreshSongs()
            binding.root.postDelayed(stopRefreshingRunnable, 500)
        }
    }

    // ==================== 多选模式 ====================

    /** 进入多选模式：设置标志位，将长按歌曲加入选中列表，切换到多选工具栏 */
    private fun enterMultiSelectMode(selectedSong: Song, position: Int) {
        isMultiSelectMode = true
        adapter.isMultiSelectMode = true
        adapter.selectedSongs.add(selectedSong)
        adapter.notifyDataSetChanged()
        showMultiSelectToolbar()
    }

    /** 显示多选工具栏，隐藏默认 ActionBar，并刷新选中计数 */
    private fun showMultiSelectToolbar() {
        binding.actionBarMain.root.visibility = View.GONE
        binding.actionBarMultiSelect.root.visibility = View.VISIBLE
        updateMultiSelectToolbar()
    }

    /** 退出多选模式：重置 Adapter 选中状态，恢复默认 ActionBar 显示 */
    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        adapter.resetMultiSelectMode()
        binding.actionBarMain.root.visibility = View.VISIBLE
        binding.actionBarMultiSelect.root.visibility = View.GONE
    }

    /** 刷新多选工具栏：更新选中数量文本和全选按钮图标状态 */
    private fun updateMultiSelectToolbar() {
        binding.actionBarMultiSelect.tvSelectedCount.text =
            getString(R.string.selected_count, adapter.selectedSongs.size)
        // 全选图标跟随选中状态切换：全部选中时显示取消全选图标
        binding.actionBarMultiSelect.btnSelectAll.setImageResource(
            if (adapter.isAllSelected()) {
                R.drawable.ic_select_all_selected
            } else {
                R.drawable.ic_select_all
            }
        )
    }

    // ==================== 播放器控制器集成 ====================

    /** 由 BaseActivity 回调，将当前歌曲信息同步到迷你播放栏 */
    override fun updateMiniPlayer(song: Song?) {
        playerController.updateMiniPlayer(song)
    }

    /** 由 BaseActivity 回调，将播放/暂停状态同步到播放器控制器 */
    override fun updatePlayPauseButton(isPlaying: Boolean) {
        playerController.updatePlaybackState(isPlaying)
    }

    /**
     * 初始化播放器控制器
     *
     * 创建 [PlaylistDetailPlayerController] 并绑定到 Activity 的 BottomSheet 布局，
     * 负责迷你播放栏与全屏播放器之间的展开/折叠及播放状态管理。
     */
    private fun setupPlayerController() {
        playerController = PlaylistDetailPlayerController(
            activity = this,
            binding = binding.playerBottomSheet,
            playerManager = playerManager
        )
        playerController.setup()
    }

    /** 随机播放：从歌单歌曲中随机选取一首开始播放 */
    private fun shufflePlay() {
        viewModel.songs.value?.let { songs ->
            if (songs.isNotEmpty()) {
                val randomIndex = (0 until songs.size).random()
                playerManager.playSong(songs[randomIndex], songs, randomIndex)
            }
        }
    }

    /**
     * 观察数据变化
     *
     * 订阅歌单歌曲列表、当前播放歌曲、播放状态、进度、时长、播放模式等 LiveData，
     * 将变化同步到 UI 列表和播放器控制器。
     */
    private fun observeData() {
        viewModel.songs.observe(this, Observer { songs ->
            adapter.submitList(songs)
            updateSongCount(songs.size)
        })

        playerManager.currentSong.observe(this, Observer { song ->
            adapter.updateCurrentPlayingSongId(song?.id)
            playerController.updateMiniPlayer(song)
            if (song != null) {
                playerController.updateCurrentSong(song)
            }
        })

        playerManager.isPlaying.observe(this, Observer {
            playerController.updatePlaybackState(it)
        })

        playerManager.currentPosition.observe(this, Observer { position ->
            if (!playerController.isSeeking) {
                playerController.updateCurrentPosition(position)
            }
        })

        playerManager.duration.observe(this, Observer { duration ->
            playerController.updateDuration(duration)
        })

        playerManager.playMode.observe(this, Observer { mode ->
            playerController.updatePlayMode(mode)
        })

        playerManager.expandPlayerSheet.observe(this, Observer { shouldExpand ->
            if (shouldExpand) {
                playerController.expand()
            }
        })
    }

    /** 更新工具栏中的歌曲数量显示 */
    private fun updateSongCount(count: Int) {
        binding.actionBarMain.tvSongCount.text = getString(R.string.song_count, count)
    }

    /**
     * 播放指定歌曲
     *
     * 若点击的歌曲与当前正在播放的歌曲相同，则展开全屏播放器；
     * 否则从歌单歌曲列表中以指定位置开始播放。
     */
    private fun playSong(song: Song, position: Int) {
        val songs = viewModel.songs.value ?: return

        val currentSong = playerManager.currentSong.value
        if (currentSong != null && currentSong.id == song.id) {
            playerController.expand()
        } else {
            playerManager.playSong(song, songs, position)
        }
    }

    /**
     * 显示歌曲操作弹出菜单
     *
     * 在歌单详情页中移除"添加到歌单"选项（因为已在歌单内），
     * 保留播放、歌曲信息、从歌单中删除三个操作。
     */
    private fun showSongMenu(song: Song, anchorView: View) {
        val popupMenu = androidx.appcompat.widget.PopupMenu(this, anchorView)
        popupMenu.menuInflater.inflate(R.menu.song_menu, popupMenu.menu)
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

    /** 从歌单中移除单首歌曲并发送变更广播 */
    private fun removeSongFromPlaylist(song: Song) {
        viewModel.removeSongFromPlaylist(song)
        notifyPlaylistChanged()
    }

    /** 显示清空歌单确认对话框，确认后清空所有歌曲并发送变更广播 */
    private fun showClearPlaylistDialog() {
        AlertDialog.Builder(this)
            .setTitle("清空歌单")
            .setMessage("确定要清空歌单中的所有歌曲吗？")
            .setPositiveButton("清空") { dialog, _ ->
                viewModel.clearPlaylist()
                notifyPlaylistChanged()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 显示批量删除歌曲确认对话框，确认后逐首从歌单中移除并发送变更广播 */
    private fun showDeleteMultipleSongsDialog(songs: List<Song>) {
        showDeleteSongsConfirmDialog(this, songs.size) {
            songs.forEach { song ->
                viewModel.removeSongFromPlaylist(song)
            }
            notifyPlaylistChanged()
            showSnackbar("已删除${songs.size}首歌曲")
        }
    }

    /**
     * 返回键处理
     *
     * 优先级：全屏播放器展开时先折叠 → 多选模式时退出多选 → 默认返回行为。
     */
    override fun onBackPressed() {
        if (::playerController.isInitialized && playerController.isExpanded()) {
            playerController.collapse()
            return
        }
        if (isMultiSelectMode) {
            exitMultiSelectMode()
        } else {
            super.onBackPressed()
        }
    }

    /** 显示歌曲详情底部弹窗 */
    private fun showSongInfoBottomSheet(song: Song) {
        val bottomSheet = com.musicplayer.ui.dialog.SongInfoBottomSheet.newInstance(song)
        bottomSheet.show(supportFragmentManager, com.musicplayer.ui.dialog.SongInfoBottomSheet.TAG)
    }

    /**
     * 销毁时释放资源
     *
     * 清理延迟回调、解绑 RecyclerView Adapter 并释放 Adapter 状态、
     * 释放播放器控制器资源，防止内存泄漏。
     */
    override fun onDestroy() {
        binding.root.removeCallbacks(stopRefreshingRunnable)
        if (::adapter.isInitialized) {
            binding.contentMain.recyclerView.adapter = null
            adapter.release()
        }
        if (::playerController.isInitialized) {
            playerController.release()
        }
        super.onDestroy()
    }
}
