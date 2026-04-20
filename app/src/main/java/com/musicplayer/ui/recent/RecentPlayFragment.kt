package com.musicplayer.ui.recent

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
import com.musicplayer.data.repository.MusicRepository
import com.musicplayer.service.PlayerManager
import com.musicplayer.ui.adapter.SongAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 最近播放页面Fragment
 */
class RecentPlayFragment : Fragment() {

    private var _binding: FragmentRecentBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: RecentPlayViewModel
    private lateinit var adapter: SongAdapter
    private val playerManager = PlayerManager.getInstance()

    // 多选模式标志
    private var isMultiSelectMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化ViewModel
        val musicRepository = (requireActivity().application as MusicPlayerApplication).musicRepository
        viewModel = ViewModelProvider(
            this,
            RecentPlayViewModelFactory(musicRepository)
        )[RecentPlayViewModel::class.java]

        // 设置UI
        setupUI()

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
                enterMultiSelectMode(song, position)
            }
        )

        adapter.onSelectionChanged = {
            updateMultiSelectToolbar()
        }

        binding.contentRecentPlay.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RecentPlayFragment.adapter
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

        // 设置下拉刷新
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshRecentPlays()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun observeData() {
        viewModel.recentPlays.observe(viewLifecycleOwner) { songs ->
            adapter.submitList(songs)
            updateEmptyView(songs.isEmpty())
        }

        // 观察播放器状态
        playerManager.currentSong.observe(viewLifecycleOwner) { song ->
            adapter.updateCurrentPlayingSongId(song?.id)
        }
    }

    /**
     * 获取适合显示 Snackbar 的锚点视图
     * 优先使用 Activity 的 CoordinatorLayout，确保 Snackbar 正确显示
     */
    private fun getSnackbarAnchorView(): View {
        val coordinatorLayout = requireActivity().findViewById<View>(R.id.coordinator_layout)
        return coordinatorLayout ?: requireView()
    }

    /**
     * 显示 Snackbar，自动处理迷你播放栏的遮挡问题
     * 将 Snackbar 定位到迷你播放栏上方
     */
    private fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        val snackbar = Snackbar.make(getSnackbarAnchorView(), message, duration)

        // 获取迷你播放栏视图
        val miniPlayer = requireActivity().findViewById<View>(R.id.mini_player_container)
        if (miniPlayer != null && miniPlayer.visibility == View.VISIBLE) {
            val snackbarView = snackbar.view
            val params = snackbarView.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams

            // 设置底部边距为迷你播放栏的高度，使 Snackbar 显示在迷你播放栏上方
            miniPlayer.post {
                val miniPlayerHeight = miniPlayer.height
                if (miniPlayerHeight > 0) {
                    params.bottomMargin = miniPlayerHeight
                    snackbarView.layoutParams = params
                }
            }
        }

        snackbar.show()
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.contentRecentPlay.emptyView.isVisible = isEmpty
        binding.contentRecentPlay.recyclerView.isVisible = !isEmpty
        // 刷新菜单以显示/隐藏清空按钮
        requireActivity().invalidateOptionsMenu()
    }

    private fun playSong(song: Song, position: Int) {
        val songs = viewModel.recentPlays.value ?: return

        // 检查点击的歌曲是否是当前正在播放的歌曲
        val currentSong = playerManager.currentSong.value
        if (currentSong != null && currentSong.id == song.id) {
            // 如果是当前播放的歌曲，请求展开Bottom Sheet
            playerManager.requestExpandPlayerSheet()
        } else {
            // 如果不是当前播放的歌曲，播放该歌曲
            val playlistSnapshot = ArrayList(songs)
            val actualPosition = playlistSnapshot.indexOf(song)
            if (actualPosition >= 0) {
                playerManager.playSong(song, playlistSnapshot, actualPosition)
            }
        }
    }

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

    private fun showAddToPlaylistDialog(song: Song) {
        viewModel.viewModelScope.launch {
            val playlists = viewModel.getAllPlaylistsSync()

            if (playlists.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("提示")
                    .setMessage("您还没有创建任何歌单，请先创建歌单")
                    .setPositiveButton("确定") { dialog, _ ->
                        showCreatePlaylistDialog(song)
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                val playlistNames = playlists.map { it.name }.toTypedArray()
                val selectedIndices = BooleanArray(playlists.size) { false }

                AlertDialog.Builder(requireContext())
                    .setTitle("添加到歌单")
                    .setMultiChoiceItems(playlistNames, selectedIndices) { _, which, isChecked ->
                        selectedIndices[which] = isChecked
                    }
                    .setPositiveButton("确定") { dialog, _ ->
                        val selectedPlaylistIds = mutableListOf<Long>()
                        for (i in selectedIndices.indices) {
                            if (selectedIndices[i]) {
                                selectedPlaylistIds.add(playlists[i].id)
                            }
                        }
                        if (selectedPlaylistIds.isNotEmpty()) {
                            for (playlistId in selectedPlaylistIds) {
                                viewModel.addSongToPlaylist(playlistId, song)
                            }
                            showSnackbar("已添加到歌单")
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private fun showCreatePlaylistDialog(songToAdd: Song? = null) {
        val editText = EditText(requireContext()).apply {
            hint = "请输入歌单名称"
            setPadding(32, 16, 32, 16)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("新建歌单")
            .setView(editText)
            .setPositiveButton("确定") { dialog, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.createPlaylistAndAddSong(name, songToAdd)
                    showSnackbar("歌单创建成功")
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddToPlaylistDialogForMultipleSongs(songs: List<Song>) {
        viewModel.viewModelScope.launch {
            val playlists = viewModel.getAllPlaylistsSync()

            if (playlists.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("提示")
                    .setMessage("您还没有创建任何歌单，请先创建歌单")
                    .setPositiveButton("确定") { dialog, _ ->
                        showCreatePlaylistDialogForMultipleSongs(songs)
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                val playlistNames = playlists.map { it.name }.toTypedArray()
                val selectedIndices = BooleanArray(playlists.size) { false }

                AlertDialog.Builder(requireContext())
                    .setTitle("添加到歌单")
                    .setMultiChoiceItems(playlistNames, selectedIndices) { _, which, isChecked ->
                        selectedIndices[which] = isChecked
                    }
                    .setPositiveButton("确定") { dialog, _ ->
                        val selectedPlaylistIds = mutableListOf<Long>()
                        for (i in selectedIndices.indices) {
                            if (selectedIndices[i]) {
                                selectedPlaylistIds.add(playlists[i].id)
                            }
                        }
                        if (selectedPlaylistIds.isNotEmpty()) {
                            for (song in songs) {
                                for (playlistId in selectedPlaylistIds) {
                                    viewModel.addSongToPlaylist(playlistId, song)
                                }
                            }
                            showSnackbar("已添加到歌单")
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private fun showCreatePlaylistDialogForMultipleSongs(songs: List<Song>) {
        val editText = EditText(requireContext()).apply {
            hint = "请输入歌单名称"
            setPadding(32, 16, 32, 16)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("新建歌单")
            .setView(editText)
            .setPositiveButton("确定") { dialog, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.createPlaylistAndAddMultipleSongs(name, songs)
                    showSnackbar("歌单创建成功")
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteMultipleSongsDialog(songs: List<Song>) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除歌曲")
            .setMessage("确定要删除选中的${songs.size}首歌曲吗？")
            .setPositiveButton("删除") { dialog, _ ->
                for (song in songs) {
                    viewModel.removeFromRecentPlay(song.id)
                }
                showSnackbar("已删除${songs.size}首歌曲")
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showClearConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("清空记录")
            .setMessage("确定要清空最近播放记录吗？")
            .setPositiveButton("清空") { dialog, _ ->
                viewModel.clearRecentPlays()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun enterMultiSelectMode(selectedSong: Song, position: Int) {
        isMultiSelectMode = true
        adapter.isMultiSelectMode = true
        adapter.selectedSongs.add(selectedSong)
        adapter.notifyDataSetChanged()

        showMultiSelectToolbar()
    }

    private fun showMultiSelectToolbar() {
        binding.actionBarMultiSelect.root.visibility = View.VISIBLE
        // 刷新菜单以隐藏清空按钮
        requireActivity().invalidateOptionsMenu()
        updateMultiSelectToolbar()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        adapter.resetMultiSelectMode()
        binding.actionBarMultiSelect.root.visibility = View.GONE
        // 刷新菜单以恢复清空按钮显示状态
        requireActivity().invalidateOptionsMenu()
    }

    private fun updateMultiSelectToolbar() {
        binding.actionBarMultiSelect.tvSelectedCount.text =
            getString(R.string.selected_count, adapter.selectedSongs.size)
        val selectAllIcon = if (adapter.isAllSelected()) {
            R.drawable.ic_select_all_selected
        } else {
            R.drawable.ic_select_all
        }
        binding.actionBarMultiSelect.btnSelectAll.setImageResource(selectAllIcon)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_recent, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear -> {
                showClearConfirmDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        // 根据数据状态和多选模式显示/隐藏清空按钮
        val songs = viewModel.recentPlays.value ?: emptyList()
        val clearItem = menu.findItem(R.id.action_clear)
        clearItem?.isVisible = songs.isNotEmpty() && !isMultiSelectMode
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
