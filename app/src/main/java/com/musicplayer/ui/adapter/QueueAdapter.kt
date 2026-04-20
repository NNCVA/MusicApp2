package com.musicplayer.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musicplayer.R
import com.musicplayer.data.model.Song

/**
 * 播放队列适配器
 * 只显示歌曲名和歌手名，不显示专辑封面
 */
class QueueAdapter(
    private val onSongClick: (Song, Int) -> Unit
) : ListAdapter<Song, QueueAdapter.QueueViewHolder>(QueueDiffCallback()) {

    // 当前播放的歌曲ID
    var currentPlayingSongId: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue_song, parent, false)
        return QueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val song = getItem(position)
        holder.bind(song, position)
    }

    inner class QueueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSongTitle: TextView = itemView.findViewById(R.id.tv_queue_song_title)
        private val tvArtistName: TextView = itemView.findViewById(R.id.tv_queue_artist_name)

        fun bind(song: Song, position: Int) {
            tvSongTitle.text = song.title
            tvArtistName.text = song.artist

            // 高亮当前播放的歌曲
            val highlightColor = itemView.context.resources.getColor(R.color.light_blue, null)
            val defaultTitleColor = itemView.context.resources.getColor(R.color.text_primary, null)

            tvSongTitle.setTextColor(
                if (song.id == currentPlayingSongId) highlightColor else defaultTitleColor
            )

            itemView.setOnClickListener {
                onSongClick(song, position)
            }
        }
    }

    class QueueDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem == newItem
        }
    }
}

