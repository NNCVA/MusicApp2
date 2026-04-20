package com.musicplayer.ui.playlist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.musicplayer.MusicPlayerApplication
import com.musicplayer.R
import com.musicplayer.data.model.Playlist
import com.musicplayer.databinding.FragmentPlaylistsBinding
import com.musicplayer.data.repository.MusicRepository
import com.musicplayer.util.media.AlbumArtModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 歌单管理页面Fragment
 */
class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PlaylistViewModel
    private lateinit var adapter: PlaylistAdapter

    // 广播接收器用于监听歌单变化
    private val playlistChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.refreshPlaylists()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 注册广播接收器
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

        // 初始化ViewModel
        val musicRepository = (requireActivity().application as MusicPlayerApplication).musicRepository
        viewModel = ViewModelProvider(
            this,
            PlaylistViewModelFactory(musicRepository)
        )[PlaylistViewModel::class.java]

        // 设置UI
        setupUI()

        // 观察数据
        observeData()
    }

    private fun setupUI() {
        // 设置RecyclerView
        adapter = PlaylistAdapter(
            viewModel = viewModel,
            onPlaylistClick = { playlist ->
                openPlaylistDetail(playlist)
            },
            onPlaylistMenuClick = { playlist, view ->
                showPlaylistMenu(playlist, view)
            }
        )

        binding.contentPlaylist.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PlaylistsFragment.adapter
        }

        // 新建歌单按钮
        binding.fabAdd.setOnClickListener {
            showCreatePlaylistDialog()
        }

        // 设置下拉刷新
        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.swipeRefreshLayout.isRefreshing = true
            viewModel.refreshPlaylists()
            view?.postDelayed({
                binding.swipeRefreshLayout.isRefreshing = false
            }, 1000)
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

    private fun showCreatePlaylistDialog() {
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
                    viewModel.createPlaylist(name)
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRenamePlaylistDialog(playlist: Playlist) {
        val editText = EditText(requireContext()).apply {
            setText(playlist.name)
            setSelection(playlist.name.length)
            setPadding(32, 16, 32, 16)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("重命名歌单")
            .setView(editText)
            .setPositiveButton("确定") { dialog, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    viewModel.renamePlaylist(playlist, newName)
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeletePlaylistDialog(playlist: Playlist) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除歌单")
            .setMessage("确定要删除歌单\"${playlist.name}\"吗？")
            .setPositiveButton("删除") { dialog, _ ->
                viewModel.deletePlaylist(playlist)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPlaylistMenu(playlist: Playlist, anchorView: View) {
        val popupMenu = androidx.appcompat.widget.PopupMenu(requireContext(), anchorView)
        popupMenu.menuInflater.inflate(R.menu.playlist_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_rename -> {
                    showRenamePlaylistDialog(playlist)
                    true
                }
                R.id.menu_delete -> {
                    showDeletePlaylistDialog(playlist)
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun openPlaylistDetail(playlist: Playlist) {
        val intent = android.content.Intent(requireContext(), PlaylistDetailActivity::class.java).apply {
            putExtra("playlist_id", playlist.id)
            putExtra("playlist_name", playlist.name)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // 刷新歌单列表数据
        viewModel.playlists.value?.let {
            adapter.submitList(null)
            binding.contentPlaylist.recyclerView.postDelayed({
                adapter.submitList(ArrayList(it))
            }, 100)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消注册广播接收器
        try {
            requireContext().unregisterReceiver(playlistChangeReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered, ignore
        }
    }
}

/**
 * 歌单适配器
 */
class PlaylistAdapter(
    private val viewModel: PlaylistViewModel,
    private val onPlaylistClick: (Playlist) -> Unit,
    private val onPlaylistMenuClick: (Playlist, View) -> Unit
) : ListAdapter<Playlist, PlaylistAdapter.PlaylistViewHolder>(PlaylistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val playlist = getItem(position)
        holder.bind(playlist, viewModel)
    }

    inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPlaylistIcon: ImageView = itemView.findViewById(R.id.iv_playlist_icon)
        private val tvPlaylistName: TextView = itemView.findViewById(R.id.tv_playlist_name)
        private val tvSongCount: TextView = itemView.findViewById(R.id.tv_song_count)
        private val btnMenu: View = itemView.findViewById(R.id.btn_menu)

        fun bind(playlist: Playlist, viewModel: PlaylistViewModel) {
            tvPlaylistName.text = playlist.name

            // 加载歌单封面（默认图标）
            Glide.with(itemView.context)
                .load(R.drawable.ic_playlist_album)
                .placeholder(R.drawable.ic_playlist_album)
                .into(ivPlaylistIcon)

            // 在后台线程获取封面歌曲和歌曲数量
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                val coverSong = viewModel.musicRepository.getPlaylistCoverSong(playlist.id)
                val songCount = viewModel.musicRepository.getPlaylistSongCount(playlist.id)

                // 切换到主线程更新UI
                withContext(Dispatchers.Main) {
                    tvSongCount.text = itemView.context.getString(R.string.playlist_songs_count, songCount)

                    // 如果有封面歌曲，加载封面
                    coverSong?.let { song ->
                        Glide.with(itemView.context)
                            .load(AlbumArtModelLoader.AlbumArtUri(song.albumId, song.path))
                            .placeholder(R.drawable.ic_playlist_album)
                            .error(R.drawable.ic_playlist_album)
                            .into(ivPlaylistIcon)
                    }
                }
            }

            itemView.setOnClickListener {
                onPlaylistClick(playlist)
            }

            btnMenu.setOnClickListener { view ->
                onPlaylistMenuClick(playlist, view)
            }
        }
    }

    class PlaylistDiffCallback : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist): Boolean {
            return oldItem == newItem
        }
    }
}
