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

        fun bind(playlist: Playlist) {
            tvPlaylistName.text = playlist.name

            Glide.with(itemView.context)
                .load(R.drawable.ic_playlist_album)
                .placeholder(R.drawable.ic_playlist_album)
                .into(ivPlaylistIcon)

            viewModel.viewModelScope.launch(Dispatchers.IO) {
                val coverSong = viewModel.musicRepository.getPlaylistCoverSong(playlist.id)
                val songCount = viewModel.musicRepository.getPlaylistSongCount(playlist.id)

                withContext(Dispatchers.Main) {
                    tvSongCount.text = itemView.context.getString(R.string.playlist_songs_count, songCount)

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
