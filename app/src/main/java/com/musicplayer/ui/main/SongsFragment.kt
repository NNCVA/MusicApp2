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
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel
    private lateinit var songAdapter: SongAdapter
    private val playerManager = PlayerManager.getInstance()

    private var isMultiSelectMode = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.refreshSongs()
        } else {
            showPermissionDeniedMessage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val musicRepository = (requireActivity().application as MusicPlayerApplication).musicRepository
        viewModel = ViewModelProvider(this, MainViewModelFactory(musicRepository))[MainViewModel::class.java]

        setupUI()
        observeData()
    }

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

    private fun setupUI() {
        checkPermissions()

        songAdapter = SongAdapter(
            onSongClick = { song, position -> playSong(song, position) },
            onSongMenuClick = { song, view -> showSongMenu(song, view) },
            onSongLongClick = { song, position -> enterMultiSelectMode(song, position) }
        )

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

    private fun enterMultiSelectMode(selectedSong: Song, position: Int) {
        isMultiSelectMode = true
        songAdapter.isMultiSelectMode = true
        songAdapter.selectedSongs.add(selectedSong)
        songAdapter.notifyDataSetChanged()

        showMultiSelectToolbar()
    }

    private fun showMultiSelectToolbar() {
        binding.actionBarMain.root.visibility = View.GONE
        binding.actionBarMultiSelect.root.visibility = View.VISIBLE
        updateMultiSelectToolbar()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        songAdapter.resetMultiSelectMode()

        binding.actionBarMain.root.visibility = View.VISIBLE
        binding.actionBarMultiSelect.root.visibility = View.GONE
    }

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

    private fun playSong(song: Song, position: Int) {
        val songs = viewModel.filteredSongs.value ?: return

        val currentSong = playerManager.currentSong.value
        if (currentSong != null && currentSong.id == song.id) {
            playerManager.requestExpandPlayerSheet()
        } else {
            playerManager.playSong(song, songs, position)
        }
    }

    private fun shufflePlay() {
        val songs = viewModel.filteredSongs.value ?: return
        if (songs.isNotEmpty()) {
            val randomIndex = (0 until songs.size).random()
            playerManager.playSong(songs[randomIndex], songs, randomIndex)
            playerManager.setPlayMode(PlayMode.SHUFFLE)
        }
    }

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

    private fun showSongInfoBottomSheet(song: Song) {
        val bottomSheet = com.musicplayer.ui.dialog.SongInfoBottomSheet.newInstance(song)
        bottomSheet.show(childFragmentManager, com.musicplayer.ui.dialog.SongInfoBottomSheet.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.contentMain.recyclerView.adapter = null
        songAdapter.release()
        _binding = null
    }
}
