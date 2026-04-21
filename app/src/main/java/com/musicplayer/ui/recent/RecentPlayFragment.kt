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
 */
class RecentPlayFragment : Fragment() {

    private var _binding: FragmentRecentBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: RecentPlayViewModel
    private lateinit var adapter: SongAdapter
    private val playerManager = PlayerManager.getInstance()

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
            onSongLongClick = { song, position -> enterMultiSelectMode(song, position) }
        )

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

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.contentRecentPlay.emptyView.isVisible = isEmpty
        binding.contentRecentPlay.recyclerView.isVisible = !isEmpty
        requireActivity().invalidateOptionsMenu()
    }

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
                viewModel.removeFromRecentPlay(song.id)
            }
            showSnackbar("已删除${songs.size}首歌曲")
        }
    }

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

    private fun enterMultiSelectMode(selectedSong: Song, position: Int) {
        isMultiSelectMode = true
        adapter.isMultiSelectMode = true
        adapter.selectedSongs.add(selectedSong)
        adapter.notifyDataSetChanged()

        showMultiSelectToolbar()
    }

    private fun showMultiSelectToolbar() {
        binding.actionBarMultiSelect.root.visibility = View.VISIBLE
        requireActivity().invalidateOptionsMenu()
        updateMultiSelectToolbar()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        adapter.resetMultiSelectMode()
        binding.actionBarMultiSelect.root.visibility = View.GONE
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
        val songs = viewModel.recentPlays.value ?: emptyList()
        val clearItem = menu.findItem(R.id.action_clear)
        clearItem?.isVisible = songs.isNotEmpty() && !isMultiSelectMode
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
