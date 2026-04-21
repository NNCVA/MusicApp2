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
 */
class PlaylistDetailActivity : BaseActivity() {
    companion object {
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (isMultiSelectMode) {
            menuInflater.inflate(R.menu.multi_select_menu, menu)
            val selectAllItem = menu?.findItem(R.id.action_select_all)
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

    private fun enterMultiSelectMode(selectedSong: Song, position: Int) {
        isMultiSelectMode = true
        adapter.isMultiSelectMode = true
        adapter.selectedSongs.add(selectedSong)
        adapter.notifyDataSetChanged()
        showMultiSelectToolbar()
    }

    private fun showMultiSelectToolbar() {
        binding.actionBarMain.root.visibility = View.GONE
        binding.actionBarMultiSelect.root.visibility = View.VISIBLE
        updateMultiSelectToolbar()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        adapter.resetMultiSelectMode()
        binding.actionBarMain.root.visibility = View.VISIBLE
        binding.actionBarMultiSelect.root.visibility = View.GONE
    }

    private fun updateMultiSelectToolbar() {
        binding.actionBarMultiSelect.tvSelectedCount.text =
            getString(R.string.selected_count, adapter.selectedSongs.size)
        binding.actionBarMultiSelect.btnSelectAll.setImageResource(
            if (adapter.isAllSelected()) {
                R.drawable.ic_select_all_selected
            } else {
                R.drawable.ic_select_all
            }
        )
    }

    override fun updateMiniPlayer(song: Song?) {
        playerController.updateMiniPlayer(song)
    }

    override fun updatePlayPauseButton(isPlaying: Boolean) {
        playerController.updatePlaybackState(isPlaying)
    }

    private fun setupPlayerController() {
        playerController = PlaylistDetailPlayerController(
            activity = this,
            binding = binding.playerBottomSheet,
            playerManager = playerManager
        )
        playerController.setup()
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

    private fun updateSongCount(count: Int) {
        binding.actionBarMain.tvSongCount.text = getString(R.string.song_count, count)
    }

    private fun playSong(song: Song, position: Int) {
        val songs = viewModel.songs.value ?: return

        val currentSong = playerManager.currentSong.value
        if (currentSong != null && currentSong.id == song.id) {
            playerController.expand()
        } else {
            playerManager.playSong(song, songs, position)
        }
    }

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

    private fun removeSongFromPlaylist(song: Song) {
        viewModel.removeSongFromPlaylist(song)
        notifyPlaylistChanged()
    }

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

    private fun showDeleteMultipleSongsDialog(songs: List<Song>) {
        showDeleteSongsConfirmDialog(this, songs.size) {
            songs.forEach { song ->
                viewModel.removeSongFromPlaylist(song)
            }
            notifyPlaylistChanged()
            showSnackbar("已删除${songs.size}首歌曲")
        }
    }

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

    private fun showSongInfoBottomSheet(song: Song) {
        val bottomSheet = com.musicplayer.ui.dialog.SongInfoBottomSheet.newInstance(song)
        bottomSheet.show(supportFragmentManager, com.musicplayer.ui.dialog.SongInfoBottomSheet.TAG)
    }

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
