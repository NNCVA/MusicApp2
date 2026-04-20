package com.musicplayer.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.musicplayer.R
import com.musicplayer.data.model.Song
import com.musicplayer.util.media.AlbumArtModelLoader

/**
 * 歌曲列表适配器
 */
class SongAdapter(
    private val onSongClick: (Song, Int) -> Unit,
    private val onSongMenuClick: (Song, View) -> Unit,
    private val onSongLongClick: (Song, Int) -> Unit
) : ListAdapter<Song, SongAdapter.SongViewHolder>(SongDiffCallback()) {
    
    // 多选模式标志
    var isMultiSelectMode = false
    
    // 选中的歌曲集合
    val selectedSongs = mutableSetOf<Song>()
    
    // 当前播放的歌曲ID
    var currentPlayingSongId: String? = null

    // 获取歌曲在当前列表中的位置
    fun getPositionForSong(song: Song): Int {
        return currentList.indexOf(song)
    }

    // 更新当前播放的歌曲ID
    fun updateCurrentPlayingSongId(songId: String?) {
        val oldSongId = currentPlayingSongId
        currentPlayingSongId = songId
        
        // 如果歌曲ID发生变化，更新受影响的项
        if (oldSongId != songId) {
            // 使用更高效的方式查找索引，避免多次遍历
            val currentSongs = currentList // 获取当前列表，避免多次调用getItem(i)
            val itemsToUpdate = mutableListOf<Int>()
            
            // 找到旧的当前播放歌曲位置
            if (oldSongId != null) {
                val oldIndex = currentSongs.indexOfFirst { it.id == oldSongId }
                if (oldIndex != -1) {
                    itemsToUpdate.add(oldIndex)
                }
            }
            
            // 找到新的当前播放歌曲位置
            if (songId != null) {
                val newIndex = currentSongs.indexOfFirst { it.id == songId }
                if (newIndex != -1) {
                    itemsToUpdate.add(newIndex)
                }
            }
            
            // 批量更新，减少UI刷新次数
            itemsToUpdate.forEach { index ->
                notifyItemChanged(index)
            }
        }
    }
    
    // 刷新适配器，重置多选状态
    fun resetMultiSelectMode() {
        isMultiSelectMode = false
        selectedSongs.clear()
        notifyDataSetChanged()
    }
    
    // 全选所有歌曲
    fun selectAll() {
        selectedSongs.clear()
        for (i in 0 until itemCount) {
            selectedSongs.add(getItem(i))
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }
    
    // 取消全选所有歌曲
    fun deselectAll() {
        selectedSongs.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }
    
    // 检查是否所有歌曲都已选中
    fun isAllSelected(): Boolean {
        return selectedSongs.size == itemCount && itemCount > 0
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = getItem(position)
        holder.bind(song, position)
    }
    
    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cbSelect: CheckBox = itemView.findViewById(R.id.cb_select)
        private val ivAlbumCover: ImageView = itemView.findViewById(R.id.iv_album_cover)
        private val tvSongTitle: TextView = itemView.findViewById(R.id.tv_song_title)
        private val tvArtistName: TextView = itemView.findViewById(R.id.tv_artist_name)
        private val tvDuration: TextView = itemView.findViewById(R.id.tv_duration)
        private val btnMenu: ImageView = itemView.findViewById(R.id.btn_menu)
        
        fun bind(song: Song, position: Int) {
            // 设置歌曲信息
            tvSongTitle.text = song.title
            tvArtistName.text = song.artist
            tvDuration.text = song.getDurationString()
            
            // 设置高亮颜色：如果是当前播放的歌曲，使用亮蓝色，否则使用默认颜色
            val highlightColor = itemView.context.resources.getColor(R.color.light_blue, null)
            val defaultTitleColor = itemView.context.resources.getColor(android.R.color.white, null)
            val defaultArtistColor = itemView.context.resources.getColor(R.color.text_artist, null)
            
            if (song.id == currentPlayingSongId) {
                // 当前播放的歌曲，高亮显示
                tvSongTitle.setTextColor(highlightColor)
                tvArtistName.setTextColor(highlightColor)
            } else {
                // 非当前播放的歌曲，使用默认颜色
                tvSongTitle.setTextColor(defaultTitleColor)
                tvArtistName.setTextColor(defaultArtistColor)
            }
            
            // 加载专辑封面
            loadAlbumCover(song)
            
            // 根据多选模式显示/隐藏复选框和more按钮
            if (isMultiSelectMode) {
                cbSelect.visibility = View.VISIBLE
                btnMenu.visibility = View.GONE
            } else {
                cbSelect.visibility = View.GONE
                btnMenu.visibility = View.VISIBLE
            }
            
            // 设置复选框状态
            cbSelect.isChecked = selectedSongs.contains(song)
            
            // 设置点击事件
            itemView.setOnClickListener {
                if (isMultiSelectMode) {
                    // 多选模式下，点击切换选中状态
                    toggleSelection(song, position)
                } else {
                    // 普通模式下，直接播放歌曲
                    onSongClick(song, position)
                }
            }
            
            // 设置长按事件
            itemView.setOnLongClickListener {
                if (!isMultiSelectMode) {
                    onSongLongClick(song, position)
                    true
                } else {
                    false
                }
            }
            
            // 设置菜单点击事件
            btnMenu.setOnClickListener { view ->
                onSongMenuClick(song, view)
            }
            
            // 设置复选框点击事件
            cbSelect.setOnClickListener {
                toggleSelection(song, position)
            }
        }
        
        // 切换歌曲选中状态
        private fun toggleSelection(song: Song, position: Int) {
            if (selectedSongs.contains(song)) {
                selectedSongs.remove(song)
            } else {
                selectedSongs.add(song)
            }
            // 通知Activity更新选中计数
            onSelectionChanged?.invoke()
            notifyItemChanged(position)
        }

        private fun loadAlbumCover(song: Song) {
            // 只有当 albumId 不为 0 时才尝试加载专辑封面
            if (song.albumId > 0) {
                val albumArtUri = AlbumArtModelLoader.AlbumArtUri(song.albumId, song.path)
                Glide.with(itemView.context)
                    .load(albumArtUri)
                    .placeholder(R.drawable.ic_play)
                    .error(R.drawable.ic_play)
                    .into(ivAlbumCover)
            } else {
                // 没有专辑封面时直接使用占位符
                ivAlbumCover.setImageResource(R.drawable.ic_play)
            }
        }

    }
    
    // 添加一个回调函数，用于通知选中状态改变
    var onSelectionChanged: (() -> Unit)? = null
    
    /**
     * DiffUtil回调
     */
    class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean {
            return oldItem == newItem
        }
    }
}