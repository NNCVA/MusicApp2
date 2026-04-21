package com.musicplayer.ui.playlist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicplayer.MusicPlayerApplication
import com.musicplayer.data.model.Playlist
import com.musicplayer.databinding.FragmentPlaylistsBinding

/**
 * 歌单管理页面 Fragment
 */
class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PlaylistViewModel
    private lateinit var adapter: PlaylistAdapter
    private val stopRefreshingRunnable = Runnable {
        _binding?.swipeRefreshLayout?.isRefreshing = false
    }
    private val restorePlaylistListRunnable = Runnable {
        if (::adapter.isInitialized) {
            adapter.submitList(ArrayList(viewModel.playlists.value ?: emptyList()))
        }
    }

    private val playlistChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.refreshPlaylists()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireContext().registerReceiver(
            playlistChangeReceiver,
            IntentFilter("com.musicplayer.PLAYLIST_CHANGED"),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val musicRepository = (requireActivity().application as MusicPlayerApplication).musicRepository
        viewModel = ViewModelProvider(
            this,
            PlaylistViewModelFactory(musicRepository)
        )[PlaylistViewModel::class.java]

        setupUI()
        observeData()
    }

    private fun setupUI() {
        adapter = PlaylistAdapter(
            viewModel = viewModel,
            onPlaylistClick = { playlist -> openPlaylistDetail(playlist) },
            onPlaylistMenuClick = { playlist, view -> showPlaylistMenu(playlist, view) }
        )

        binding.contentPlaylist.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PlaylistsFragment.adapter
        }

        binding.fabAdd.setOnClickListener {
            showCreatePlaylistDialog(requireContext()) { name ->
                viewModel.createPlaylist(name)
            }
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.swipeRefreshLayout.isRefreshing = true
            viewModel.refreshPlaylists()
            binding.root.postDelayed(stopRefreshingRunnable, 1000)
        }
    }

    private fun observeData() {
        viewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            adapter.submitList(playlists)
            updateEmptyView(playlists.isEmpty())
        }
    }

    private fun updateEmptyView(isEmpty: Boolean) {
        binding.contentPlaylist.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.contentPlaylist.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showPlaylistMenu(playlist: Playlist, anchorView: View) {
        val popupMenu = androidx.appcompat.widget.PopupMenu(requireContext(), anchorView)
        popupMenu.menuInflater.inflate(com.musicplayer.R.menu.playlist_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                com.musicplayer.R.id.menu_rename -> {
                    showRenamePlaylistDialog(requireContext(), playlist) { newName ->
                        viewModel.renamePlaylist(playlist, newName)
                    }
                    true
                }

                com.musicplayer.R.id.menu_delete -> {
                    showDeletePlaylistDialog(requireContext(), playlist) {
                        viewModel.deletePlaylist(playlist)
                    }
                    true
                }

                else -> false
            }
        }

        popupMenu.show()
    }

    private fun openPlaylistDetail(playlist: Playlist) {
        val intent = Intent(requireContext(), PlaylistDetailActivity::class.java).apply {
            putExtra("playlist_id", playlist.id)
            putExtra("playlist_name", playlist.name)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.playlists.value?.let {
            adapter.submitList(null)
            binding.contentPlaylist.recyclerView.postDelayed(restorePlaylistListRunnable, 100)
        }
    }

    override fun onDestroyView() {
        binding.root.removeCallbacks(stopRefreshingRunnable)
        binding.contentPlaylist.recyclerView.removeCallbacks(restorePlaylistListRunnable)
        binding.contentPlaylist.recyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            requireContext().unregisterReceiver(playlistChangeReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }
}
