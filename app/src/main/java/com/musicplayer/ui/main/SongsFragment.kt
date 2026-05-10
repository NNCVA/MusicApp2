package com.musicplayer.ui.main

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.musicplayer.MusicPlayerApplication
import com.musicplayer.R
import com.musicplayer.data.model.PlayMode
import com.musicplayer.data.model.Song
import com.musicplayer.data.model.SortType
import com.musicplayer.databinding.FragmentSongsBinding
import com.musicplayer.service.PlayerManager
import com.musicplayer.ui.adapter.SongAdapter
import com.musicplayer.ui.common.showCreatePlaylistNameDialog
import com.musicplayer.ui.common.showDeleteSongsConfirmDialog
import com.musicplayer.ui.common.showPlayerSnackbar
import com.musicplayer.ui.common.showPlaylistSelectionDialog
import com.musicplayer.util.system.PermissionManager
import kotlinx.coroutines.launch

/**
 * 歌曲列表 Fragment
 */
class SongsFragment : Fragment() {

    private var _binding: FragmentSongsBinding? = null
    /** 非空断言访问 _binding，仅在 onCreateView 到 onDestroyView 生命周期内有效 */
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel
    private lateinit var songAdapter: SongAdapter
    /** 播放管理器单例，用于播放歌曲和获取当前播放状态 */
    private val playerManager = PlayerManager.getInstance()

    private var isMultiSelectMode = false

    // ==================== 权限处理 ====================

    /**
     * 运行时权限请求启动器。
     * Android 13+ 使用 READ_MEDIA_AUDIO，低版本使用 READ_EXTERNAL_STORAGE。
     * 授权后刷新歌曲列表，拒绝后提示用户。
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.refreshSongs()
        } else {
            showPermissionDeniedMessage()
        }
    }

    /** 启用选项菜单，使 onCreateOptionsMenu 被调用以创建搜索功能。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    /** 创建 Fragment 视图，使用 ViewBinding 绑定布局。 */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 视图创建完成后初始化 ViewModel（通过工厂注入 MusicRepository），
     * 然后设置 UI 控件和数据观察者。
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val musicRepository = (requireActivity().application as MusicPlayerApplication).musicRepository
        viewModel = ViewModelProvider(this, MainViewModelFactory(musicRepository))[MainViewModel::class.java]

        setupUI()
        observeData()
    }

    /** 检查音频存储权限，未授权时通过 requestPermissionLauncher 发起请求。 */
    private fun checkPermissions() {
        if (!PermissionManager.hasAudioPermission(requireContext())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        showPlayerSnackbar(message, duration)
    }

    private fun showPermissionDeniedMessage() {
        showSnackbar("需要存储权限来访问音乐文件", Snackbar.LENGTH_LONG)
    }

    // ==================== 适配器设置 ====================

    /**
     * 初始化整个 UI：权限检查、歌曲适配器（含点击/菜单/长按回调）、
     * RecyclerView 布局管理器、随机播放按钮、排序按钮、多选工具栏按钮、
     * 下拉刷新监听器。
     */
    private fun setupUI() {
        checkPermissions()

        // 创建歌曲适配器：单击播放、弹出菜单、长按进入多选模式
        songAdapter = SongAdapter(
            onSongClick = { song, position -> playSong(song, position) },
            onSongMenuClick = { song, view -> showSongMenu(song, view) },
            onSongLongClick = { song, position -> enterMultiSelectMode(song, position) }
        )

        // 多选状态变化时刷新工具栏（已选数量、全选图标）
        songAdapter.onSelectionChanged = {
            updateMultiSelectToolbar()
        }

        binding.contentMain.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
        }

        binding.actionBarMain.btnShuffle.setOnClickListener {
            shufflePlay()
        }

        binding.actionBarMain.btnSort.setOnClickListener {
            toggleSort()
        }

        binding.actionBarMultiSelect.btnSelectAll.setOnClickListener {
            if (songAdapter.isAllSelected()) {
                songAdapter.deselectAll()
            } else {
                songAdapter.selectAll()
            }
            updateMultiSelectToolbar()
        }

        binding.actionBarMultiSelect.btnAddToPlaylist.setOnClickListener {
            if (songAdapter.selectedSongs.isNotEmpty()) {
                showAddToPlaylistDialogForMultipleSongs(songAdapter.selectedSongs.toList())
            }
            exitMultiSelectMode()
        }

        binding.actionBarMultiSelect.btnDelete.setOnClickListener {
            if (songAdapter.selectedSongs.isNotEmpty()) {
                showDeleteMultipleSongsDialog(songAdapter.selectedSongs.toList())
            }
            exitMultiSelectMode()
        }

        binding.actionBarMultiSelect.btnCancel.setOnClickListener {
            exitMultiSelectMode()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshSongs()
        }
    }

    /**
     * 注册数据观察者：filteredSongs 更新列表和空视图、isLoading 控制进度条、
     * errorMessage 显示错误提示、currentSong 同步当前播放歌曲高亮。
     */
    private fun observeData() {
        viewModel.filteredSongs.observe(viewLifecycleOwner) { songs ->
            songAdapter.submitList(songs)
            updateEmptyView(songs.isEmpty())
            updateSongCount(songs.size)
            binding.swipeRefreshLayout.isRefreshing = false
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.contentMain.progressBar.isVisible = isLoading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                showSnackbar(it, Snackbar.LENGTH_LONG)
                viewModel.clearError()
            }
        }

        playerManager.currentSong.observe(viewLifecycleOwner) { song ->
            songAdapter.updateCurrentPlayingSongId(song?.id)
        }
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.contentMain.emptyView?.isVisible = isEmpty
        binding.contentMain.recyclerView.isVisible = !isEmpty
    }

    private fun updateSongCount(count: Int) {
        binding.actionBarMain.tvSongCount.text = getString(R.string.song_count, count)
    }

    // ==================== 多选模式 ====================

    /**
     * 长按歌曲进入多选模式：标记状态、将首首歌加入选中集合、
     * 刷新列表显示复选框、切换到多选工具栏。
     */
    private fun enterMultiSelectMode(selectedSong: Song, position: Int) {
        isMultiSelectMode = true
        songAdapter.isMultiSelectMode = true
        songAdapter.selectedSongs.add(selectedSong)
        songAdapter.notifyDataSetChanged()

        showMultiSelectToolbar()
    }

    /** 隐藏普通 ActionBar，显示多选工具栏，并刷新选中计数。 */
    private fun showMultiSelectToolbar() {
        binding.actionBarMain.root.visibility = View.GONE
        binding.actionBarMultiSelect.root.visibility = View.VISIBLE
        updateMultiSelectToolbar()
    }

    /** 退出多选模式：重置适配器选中状态、恢复普通 ActionBar 显示。 */
    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        songAdapter.resetMultiSelectMode()

        binding.actionBarMain.root.visibility = View.VISIBLE
        binding.actionBarMultiSelect.root.visibility = View.GONE
    }

    /**
     * 刷新多选工具栏：更新已选数量文字，根据是否全选切换全选按钮图标。
     * 全选按钮是切换式——已全选时点击取消全选，未全选时点击全选。
     */
    private fun updateMultiSelectToolbar() {
        binding.actionBarMultiSelect.tvSelectedCount.text =
            getString(R.string.selected_count, songAdapter.selectedSongs.size)
        val selectAllIcon = if (songAdapter.isAllSelected()) {
            R.drawable.ic_select_all_selected
        } else {
            R.drawable.ic_select_all
        }
        binding.actionBarMultiSelect.btnSelectAll.setImageResource(selectAllIcon)
    }

    // ==================== 排序/搜索 ====================

    /**
     * 创建选项菜单，加载搜索控件并设置搜索监听：
     * 提交和输入变化时实时过滤歌曲列表，关闭搜索时清空过滤。
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? androidx.appcompat.widget.SearchView

        searchView?.let {
            it.queryHint = getString(R.string.search_hint)

            it.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    it.clearFocus()
                    query?.let { searchQuery -> viewModel.searchSongs(searchQuery) }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    newText?.let { searchQuery -> viewModel.searchSongs(searchQuery) }
                    return true
                }
            })

            it.setOnCloseListener {
                viewModel.searchSongs("")
                false
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 播放歌曲：若点击的是当前正在播放的歌曲则展开全屏播放器，
     * 否则通过 PlayerManager 开始播放（传入当前过滤后的列表和位置）。
     */
    private fun playSong(song: Song, position: Int) {
        val songs = viewModel.filteredSongs.value ?: return

        val currentSong = playerManager.currentSong.value
        if (currentSong != null && currentSong.id == song.id) {
            playerManager.requestExpandPlayerSheet()
        } else {
            playerManager.playSong(song, songs, position)
        }
    }

    /** 随机播放：从当前过滤列表中随机选一首开始播放，并将播放模式设为随机。 */
    private fun shufflePlay() {
        val songs = viewModel.filteredSongs.value ?: return
        if (songs.isNotEmpty()) {
            val randomIndex = (0 until songs.size).random()
            playerManager.playSong(songs[randomIndex], songs, randomIndex)
            playerManager.setPlayMode(PlayMode.SHUFFLE)
        }
    }

    /** 弹出排序方式选择对话框，用户选择后通知 ViewModel 更新排序类型并显示提示。 */
    private fun toggleSort() {
        val sortOptions = arrayOf(
            "默认排序",
            "按歌曲名排序",
            "按歌手名排序",
            "按添加时间排序",
            "按时长排序"
        )

        val sortTypeValues = SortType.values()

        AlertDialog.Builder(requireContext())
            .setTitle("选择排序方式")
            .setItems(sortOptions) { _, which ->
                viewModel.setSortType(sortTypeValues[which])
                val sortName = when (sortTypeValues[which]) {
                    SortType.DEFAULT -> "默认"
                    SortType.NAME -> "歌曲名"
                    SortType.ARTIST -> "歌手名"
                    SortType.DATE -> "添加时间"
                    SortType.DURATION -> "时长"
                }
                showSnackbar("按$sortName 排序")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示歌曲的弹出菜单，包含：播放、添加到歌单、多选、歌曲信息、删除。
     * 菜单项点击后分发到对应处理方法。
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
                    val position = songAdapter.getPositionForSong(song)
                    enterMultiSelectMode(song, position)
                    true
                }

                R.id.menu_song_info -> {
                    showSongInfoBottomSheet(song)
                    true
                }

                R.id.menu_delete -> {
                    viewModel.deleteSong(song)
                    true
                }

                else -> false
            }
        }

        popupMenu.show()
    }

    /** 单首歌曲添加到歌单：异步获取歌单列表，弹出选择对话框。 */
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

    /** 弹出创建歌单对话框，创建成功后可选地将指定歌曲添加到新歌单。 */
    private fun showCreatePlaylistDialog(songToAdd: Song? = null) {
        showCreatePlaylistNameDialog(requireContext()) { name ->
            viewModel.createPlaylistAndAddSong(name, songToAdd)
            showSnackbar("歌单创建成功")
        }
    }

    /** 多首歌曲批量添加到歌单：异步获取歌单列表，选中后逐一添加。 */
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

    /** 创建新歌单并批量添加多首歌曲。 */
    private fun showCreatePlaylistDialogForMultipleSongs(songs: List<Song>) {
        showCreatePlaylistNameDialog(requireContext()) { name ->
            viewModel.createPlaylistAndAddMultipleSongs(name, songs)
            showSnackbar("歌单创建成功")
        }
    }

    /** 弹出删除确认对话框，确认后逐一删除选中的歌曲。 */
    private fun showDeleteMultipleSongsDialog(songs: List<Song>) {
        showDeleteSongsConfirmDialog(
            context = requireContext(),
            songCount = songs.size
        ) {
            songs.forEach { song ->
                viewModel.deleteSong(song)
            }
            showSnackbar("已删除${songs.size}首歌曲")
        }
    }

    /** 显示歌曲详情底部弹窗（标题、歌手、专辑、时长、路径等）。 */
    private fun showSongInfoBottomSheet(song: Song) {
        val bottomSheet = com.musicplayer.ui.dialog.SongInfoBottomSheet.newInstance(song)
        bottomSheet.show(childFragmentManager, com.musicplayer.ui.dialog.SongInfoBottomSheet.TAG)
    }

    /**
     * 视图销毁时清理：解绑 RecyclerView Adapter、释放适配器内部状态（选择态等）、
     * 置空 binding 引用防止内存泄漏。
     */
    override fun onDestroyView() {
        super.onDestroyView()
        binding.contentMain.recyclerView.adapter = null
        songAdapter.release()
        _binding = null
    }
}
