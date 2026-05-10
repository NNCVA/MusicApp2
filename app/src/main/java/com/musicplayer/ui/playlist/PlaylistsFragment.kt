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
 *
 * 展示用户创建的歌单列表，支持创建、重命名、删除歌单，
 * 以及点击进入歌单详情页。通过广播接收器监听歌单变更事件并自动刷新。
 */
class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PlaylistViewModel
    private lateinit var adapter: PlaylistAdapter

    /** 延迟停止下拉刷新动画的 Runnable，避免刷新指示器显示时间过短 */
    private val stopRefreshingRunnable = Runnable {
        _binding?.swipeRefreshLayout?.isRefreshing = false
    }

    /**
     * 延迟恢复歌单列表的 Runnable
     *
     * onResume 时先清空列表再延迟恢复，强制 RecyclerView 重新绑定数据，
     * 确保从歌单详情页返回后列表状态与数据库一致。
     */
    private val restorePlaylistListRunnable = Runnable {
        if (::adapter.isInitialized) {
            adapter.submitList(ArrayList(viewModel.playlists.value ?: emptyList()))
        }
    }

    /**
     * 歌单变更广播接收器
     *
     * 监听 PlaylistDetailActivity 发送的 PLAYLIST_CHANGED_ACTION 广播，
     * 收到后刷新歌单列表数据。使用 RECEIVER_NOT_EXPORTED 限制仅接收本应用内广播。
     */
    private val playlistChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.refreshPlaylists()
        }
    }

    /**
     * 注册歌单变更广播接收器
     *
     * 在 onCreate 中注册而非 onStart，确保即使页面不可见也能收到歌单变更通知，
     * 保证返回时数据已刷新。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireContext().registerReceiver(
            playlistChangeReceiver,
            IntentFilter("com.musicplayer.PLAYLIST_CHANGED"),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    /** 创建 Fragment 视图，使用 ViewBinding 绑定布局 */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 视图创建完成后初始化
     *
     * 通过 ViewModelProvider 获取 PlaylistViewModel，然后依次初始化 UI 组件和数据观察。
     */
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

    /**
     * 初始化 UI 组件
     *
     * 创建 PlaylistAdapter 并绑定到 RecyclerView，设置 FAB 点击创建新歌单，
     * 配置下拉刷新监听器（刷新后 1 秒自动停止刷新动画）。
     */
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

    /** 观察歌单列表变化，同步更新列表 UI 和空状态视图 */
    private fun observeData() {
        viewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            adapter.submitList(playlists)
            updateEmptyView(playlists.isEmpty())
        }
    }

    /** 根据列表是否为空切换空状态视图和列表的可见性 */
    private fun updateEmptyView(isEmpty: Boolean) {
        binding.contentPlaylist.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.contentPlaylist.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /**
     * 显示歌单操作弹出菜单
     *
     * 提供重命名和删除两个操作。
     * 重命名弹出输入对话框，删除弹出确认对话框，操作完成后通过 ViewModel 更新数据。
     */
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

    /** 跳转到歌单详情页，传递歌单 ID 和名称 */
    private fun openPlaylistDetail(playlist: Playlist) {
        val intent = Intent(requireContext(), PlaylistDetailActivity::class.java).apply {
            putExtra("playlist_id", playlist.id)
            putExtra("playlist_name", playlist.name)
        }
        startActivity(intent)
    }

    /**
     * 恢复可见时刷新列表
     *
     * 先清空列表再延迟 100ms 恢复，强制 RecyclerView 重新绑定数据，
     * 确保从歌单详情页返回后歌单名称等字段与数据库一致。
     */
    override fun onResume() {
        super.onResume()
        viewModel.playlists.value?.let {
            adapter.submitList(null)
            binding.contentPlaylist.recyclerView.postDelayed(restorePlaylistListRunnable, 100)
        }
    }

    /**
     * 视图销毁时释放资源
     *
     * 清理延迟回调、解绑 RecyclerView Adapter，置空 binding 防止内存泄漏。
     */
    override fun onDestroyView() {
        binding.root.removeCallbacks(stopRefreshingRunnable)
        binding.contentPlaylist.recyclerView.removeCallbacks(restorePlaylistListRunnable)
        binding.contentPlaylist.recyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }

    /**
     * 销毁时注销广播接收器
     *
     * 使用 try-catch 防止重复注销导致的 IllegalArgumentException。
     */
    override fun onDestroy() {
        super.onDestroy()
        try {
            requireContext().unregisterReceiver(playlistChangeReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }
}
