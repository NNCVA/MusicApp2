package com.musicplayer.ui.recent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.musicplayer.MusicPlayerApplication
import com.musicplayer.R
import com.musicplayer.data.model.Song
import com.musicplayer.databinding.FragmentRecentBinding
import com.musicplayer.service.PlayerManager
import com.musicplayer.ui.adapter.SongAdapter
import com.musicplayer.ui.common.showCreatePlaylistNameDialog
import com.musicplayer.ui.common.showDeleteSongsConfirmDialog
import com.musicplayer.ui.common.showPlayerSnackbar
import com.musicplayer.ui.common.showPlaylistSelectionDialog
import kotlinx.coroutines.launch

/**
 * 最近播放页面 Fragment
 *
 * 展示用户最近播放的歌曲列表，支持点击播放、长按多选、
 * 添加到歌单、删除记录等操作。集成迷你播放栏状态同步。
 */
class RecentPlayFragment : Fragment() {

    private var _binding: FragmentRecentBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: RecentPlayViewModel
    private lateinit var adapter: SongAdapter
    private val playerManager = PlayerManager.getInstance()

    private var isMultiSelectMode = false

    /** 启用选项菜单，用于显示"清空记录"操作 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    /** 创建 Fragment 视图，使用 ViewBinding 绑定布局 */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecentBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 视图创建完成后初始化
     *
     * 通过 ViewModelProvider 获取 RecentPlayViewModel，然后依次初始化 UI 组件和数据观察。
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val musicRepository = (requireActivity().application as MusicPlayerApplication).musicRepository
        viewModel = ViewModelProvider(
            this,
            RecentPlayViewModelFactory(musicRepository)
        )[RecentPlayViewModel::class.java]

        setupUI()
        observeData()
    }

    private fun setupUI() {
        adapter = SongAdapter(
            onSongClick = { song, position -> playSong(song, position) },
            onSongMenuClick = { song, view -> showSongMenu(song, view) },
            // 长按歌曲进入多选模式，将长按项作为初始选中项
            onSongLongClick = { song, position -> enterMultiSelectMode(song, position) }
        )

        // 选中项变化时刷新多选工具栏的计数和图标
        adapter.onSelectionChanged = {
            updateMultiSelectToolbar()
        }

        binding.contentRecentPlay.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RecentPlayFragment.adapter
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
            if (adapter.selectedSongs.isNotEmpty()) {
                showAddToPlaylistDialogForMultipleSongs(adapter.selectedSongs.toList())
            }
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
            viewModel.refreshRecentPlays()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    /**
     * 观察数据变化
     *
     * 订阅最近播放歌曲列表和当前播放歌曲，将变化同步到列表 UI
     * 并更新当前播放歌曲的高亮状态。
     */
    private fun observeData() {
        viewModel.recentPlays.observe(viewLifecycleOwner) { songs ->
            adapter.submitList(songs)
            updateEmptyView(songs.isEmpty())
        }

        playerManager.currentSong.observe(viewLifecycleOwner) { song ->
            adapter.updateCurrentPlayingSongId(song?.id)
        }
    }

    private fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        showPlayerSnackbar(message, duration)
    }

    /** 根据列表是否为空切换空状态视图和列表的可见性，并刷新选项菜单 */
    private fun updateEmptyView(isEmpty: Boolean) {
        binding.contentRecentPlay.emptyView.isVisible = isEmpty
        binding.contentRecentPlay.recyclerView.isVisible = !isEmpty
        requireActivity().invalidateOptionsMenu()
    }

    /**
     * 播放指定歌曲
     *
     * 若点击的歌曲与当前正在播放的相同，则展开全屏播放器；
     * 否则以最近播放列表为播放队列，从歌曲实际位置开始播放。
     */
    private fun playSong(song: Song, position: Int) {
        val songs = viewModel.recentPlays.value ?: return

        val currentSong = playerManager.currentSong.value
        if (currentSong != null && currentSong.id == song.id) {
            playerManager.requestExpandPlayerSheet()
        } else {
            val playlistSnapshot = ArrayList(songs)
            val actualPosition = playlistSnapshot.indexOf(song)
            if (actualPosition >= 0) {
                playerManager.playSong(song, playlistSnapshot, actualPosition)
            }
        }
    }

    /**
     * 显示歌曲操作弹出菜单
     *
     * 提供播放、添加到歌单、进入多选模式、从最近播放中删除四项操作。
     * "添加到歌单"会弹出歌单选择对话框，支持创建新歌单。
     */
    private fun showSongMenu(song: Song, anchorView: View) {
        val popupMenu = androidx.appcompat.widget.PopupMenu(requireContext(), anchorView)
        popupMenu.menuInflater.inflate(R.menu.song_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_play -> {
                    playSong(song, 0)
                    true
                }

                R.id.menu_add_to_playlist -> {
                    showAddToPlaylistDialog(song)
                    true
                }

                R.id.menu_multi_select -> {
                    val position = adapter.getPositionForSong(song)
                    enterMultiSelectMode(song, position)
                    true
                }

                R.id.menu_delete -> {
                    viewModel.removeFromRecentPlay(song.id)
                    true
                }

                else -> false
            }
        }

        popupMenu.show()
    }

    /**
     * 显示单首歌曲添加到歌单的对话框
     *
     * 在协程中异步获取所有歌单列表，弹出歌单选择对话框；
     * 支持用户选择已有歌单或创建新歌单后添加。
     */
    private fun showAddToPlaylistDialog(song: Song) {
        viewModel.viewModelScope.launch {
            val playlists = viewModel.getAllPlaylistsSync()
            showPlaylistSelectionDialog(
                context = requireContext(),
                playlists = playlists,
                onCreateNewRequested = { showCreatePlaylistDialog(song) },
                onConfirmed = { selectedPlaylistIds ->
                    if (selectedPlaylistIds.isNotEmpty()) {
                        selectedPlaylistIds.forEach { playlistId ->
                            viewModel.addSongToPlaylist(playlistId, song)
                        }
                        showSnackbar("已添加到歌单")
                    }
                }
            )
        }
    }

    private fun showCreatePlaylistDialog(songToAdd: Song? = null) {
        showCreatePlaylistNameDialog(requireContext()) { name ->
            viewModel.createPlaylistAndAddSong(name, songToAdd)
            showSnackbar("歌单创建成功")
        }
    }

    private fun showAddToPlaylistDialogForMultipleSongs(songs: List<Song>) {
        viewModel.viewModelScope.launch {
            val playlists = viewModel.getAllPlaylistsSync()
            showPlaylistSelectionDialog(
                context = requireContext(),
                playlists = playlists,
                onCreateNewRequested = { showCreatePlaylistDialogForMultipleSongs(songs) },
                onConfirmed = { selectedPlaylistIds ->
                    if (selectedPlaylistIds.isNotEmpty()) {
                        songs.forEach { song ->
                            selectedPlaylistIds.forEach { playlistId ->
                                viewModel.addSongToPlaylist(playlistId, song)
                            }
                        }
                        showSnackbar("已添加到歌单")
                    }
                }
            )
        }
    }

    private fun showCreatePlaylistDialogForMultipleSongs(songs: List<Song>) {
        showCreatePlaylistNameDialog(requireContext()) { name ->
            viewModel.createPlaylistAndAddMultipleSongs(name, songs)
            showSnackbar("歌单创建成功")
        }
    }

    /** 显示批量删除确认对话框，确认后逐首从最近播放中移除 */
    private fun showDeleteMultipleSongsDialog(songs: List<Song>) {
        showDeleteSongsConfirmDialog(
            context = requireContext(),
            songCount = songs.size
        ) {
            songs.forEach { song ->
                viewModel.removeFromRecentPlay(song.id)
            }
            showSnackbar("已删除${songs.size}首歌曲")
        }
    }

    /** 显示清空最近播放记录确认对话框 */
    private fun showClearConfirmDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("清空记录")
            .setMessage("确定要清空最近播放记录吗？")
            .setPositiveButton("清空") { dialog, _ ->
                viewModel.clearRecentPlays()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 进入多选模式
     *
     * 设置标志位和 Adapter 多选状态，将长按的歌曲加入选中列表，
     * 刷新整个列表以显示复选框，然后显示多选工具栏。
     */
    private fun enterMultiSelectMode(selectedSong: Song, position: Int) {
        isMultiSelectMode = true
        adapter.isMultiSelectMode = true
        adapter.selectedSongs.add(selectedSong)
        adapter.notifyDataSetChanged()

        showMultiSelectToolbar()
    }

    /** 显示多选工具栏并刷新选项菜单（隐藏"清空记录"按钮） */
    private fun showMultiSelectToolbar() {
        binding.actionBarMultiSelect.root.visibility = View.VISIBLE
        requireActivity().invalidateOptionsMenu()
        updateMultiSelectToolbar()
    }

    /** 退出多选模式：重置 Adapter 选中状态，隐藏多选工具栏，恢复选项菜单 */
    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        adapter.resetMultiSelectMode()
        binding.actionBarMultiSelect.root.visibility = View.GONE
        requireActivity().invalidateOptionsMenu()
    }

    /** 刷新多选工具栏：更新选中数量文本和全选按钮图标 */
    private fun updateMultiSelectToolbar() {
        binding.actionBarMultiSelect.tvSelectedCount.text =
            getString(R.string.selected_count, adapter.selectedSongs.size)
        // 全选图标跟随选中状态切换
        val selectAllIcon = if (adapter.isAllSelected()) {
            R.drawable.ic_select_all_selected
        } else {
            R.drawable.ic_select_all
        }
        binding.actionBarMultiSelect.btnSelectAll.setImageResource(selectAllIcon)
    }

    // ==================== 选项菜单 ====================

    /** 创建最近播放页面的选项菜单，包含"清空记录"操作 */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_recent, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    /** 处理选项菜单点击：清空记录 */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear -> {
                showClearConfirmDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 准备选项菜单可见性
     *
     * "清空记录"按钮仅在列表非空且未处于多选模式时显示。
     */
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val songs = viewModel.recentPlays.value ?: emptyList()
        val clearItem = menu.findItem(R.id.action_clear)
        clearItem?.isVisible = songs.isNotEmpty() && !isMultiSelectMode
    }

    /**
     * 视图销毁时释放资源
     *
     * 解绑 RecyclerView Adapter 并释放 Adapter 内部状态，置空 binding 防止内存泄漏。
     */
    override fun onDestroyView() {
        binding.contentRecentPlay.recyclerView.adapter = null
        adapter.release()
        super.onDestroyView()
        _binding = null
    }
}
