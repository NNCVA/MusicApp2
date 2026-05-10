package com.musicplayer.ui.playlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.musicplayer.R
import com.musicplayer.data.model.Playlist
import com.musicplayer.util.media.AlbumArtModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 歌单列表适配器，基于 [ListAdapter] 实现 DiffUtil 异步差异更新。
 * 每个 item 在绑定时通过协程异步加载歌单封面和歌曲数量，避免阻塞主线程。
 *
 * @param viewModel 提供 [PlaylistViewModel.viewModelScope] 和数据仓库访问
 * @param onPlaylistClick 点击歌单项时的回调
 * @param onPlaylistMenuClick 点击歌单项更多菜单时的回调
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
        holder.bind(getItem(position))
    }

    inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPlaylistIcon: ImageView = itemView.findViewById(R.id.iv_playlist_icon)
        private val tvPlaylistName: TextView = itemView.findViewById(R.id.tv_playlist_name)
        private val tvSongCount: TextView = itemView.findViewById(R.id.tv_song_count)
        private val btnMenu: View = itemView.findViewById(R.id.btn_menu)

        /**
         * 绑定歌单数据到视图。
         * 先同步设置文本和占位封面，再通过协程在 IO 线程异步加载歌单封面和歌曲数量，
         * 加载完成后切回主线程更新 UI。使用 ViewModel 的 viewModelScope 保证生命周期安全。
         */
        fun bind(playlist: Playlist) {
            tvPlaylistName.text = playlist.name

            // 先设置占位封面，避免异步加载期间显示旧图
            Glide.with(itemView.context)
                .load(R.drawable.ic_playlist_album)
                .placeholder(R.drawable.ic_playlist_album)
                .into(ivPlaylistIcon)

            // IO 线程异步查询歌单封面歌曲和歌曲数量，避免阻塞主线程
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                val coverSong = viewModel.musicRepository.getPlaylistCoverSong(playlist.id)
                val songCount = viewModel.musicRepository.getPlaylistSongCount(playlist.id)

                // 切回主线程更新歌曲数量文本和封面图
                withContext(Dispatchers.Main) {
                    tvSongCount.text = itemView.context.getString(R.string.playlist_songs_count, songCount)

                    // 仅当歌单内有歌曲时才加载封面
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

    /** 歌单 DiffUtil 回调，通过 id 判断是否同一项，通过 data class equals 判断内容是否变化 */
    class PlaylistDiffCallback : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist): Boolean {
            return oldItem == newItem
        }
    }
}
