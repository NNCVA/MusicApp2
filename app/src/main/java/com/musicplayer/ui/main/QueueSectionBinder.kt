package com.musicplayer.ui.main

import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicplayer.data.model.Song
import com.musicplayer.databinding.ContentPlayerDetailBinding
import com.musicplayer.service.PlayerManager
import com.musicplayer.ui.adapter.QueueAdapter

/**
 * 播放队列区域绑定器。
 *
 * 管理播放队列 RecyclerView 的 [QueueAdapter]，通过 LiveData 观察歌单和当前歌曲变化，
 * 自动刷新列表、更新队列位置文本并滚动到当前播放歌曲。
 *
 * @param binding 全屏播放器布局绑定
 * @param playerManager 播放服务代理，提供 playlist 和 currentSong 的 LiveData
 * @param lifecycleOwner 用于观察 LiveData 的生命周期宿主
 */
internal class QueueSectionBinder(
    private val binding: ContentPlayerDetailBinding,
    private val playerManager: PlayerManager,
    lifecycleOwner: LifecycleOwner
) {

    // 队列位置文本（如 "3/12"），位于工具栏
    private val queuePositionTextView: TextView = binding.toolbarQueuePosition

    // 队列适配器，点击歌曲时立即播放并切换歌单
    private val queueAdapter = QueueAdapter { song, position ->
        playerManager.playSong(song, playerManager.playlist.value ?: emptyList(), position)
    }

    // 歌单列表观察者：歌单变化时刷新列表和位置文本
    private val playlistObserver = Observer<List<Song>> { playlist ->
        queueAdapter.submitList(playlist)
        updateQueuePosition()
    }

    // 当前歌曲观察者：切歌时更新高亮、刷新位置文本并滚动到当前歌曲
    private val currentSongObserver = Observer<Song?> { song ->
        queueAdapter.currentPlayingSongId = song?.id
        updateQueuePosition()
        scrollToCurrentSong()
    }

    // ==================== 初始化 ====================
    // 绑定 RecyclerView 的适配器和布局管理器，并注册 LiveData 观察者
    init {
        binding.queueRecyclerView.apply {
            adapter = queueAdapter
            layoutManager = LinearLayoutManager(context)
        }

        playerManager.playlist.observe(lifecycleOwner, playlistObserver)
        playerManager.currentSong.observe(lifecycleOwner, currentSongObserver)
    }

    /**
     * 释放资源：移除 LiveData 观察者，解绑 RecyclerView 适配器。
     * 必须在宿主视图销毁时调用，避免内存泄漏。
     */
    fun release() {
        playerManager.playlist.removeObserver(playlistObserver)
        playerManager.currentSong.removeObserver(currentSongObserver)
        binding.queueRecyclerView.adapter = null
    }

    /**
     * 将队列 RecyclerView 滚动到当前播放歌曲位置。
     * 使用 post 确保在布局完成后执行，避免布局未完成时滚动无效。
     */
    fun scrollToCurrentSong() {
        val currentIndex = playerManager.getCurrentIndex()
        if (currentIndex >= 0) {
            binding.queueRecyclerView.post {
                val layoutManager = binding.queueRecyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(currentIndex, 0)
            }
        }
    }

    /**
     * 更新队列位置文本，格式为 "当前序号/总歌曲数"（从 1 开始计数）。
     * 无有效索引时显示 "0/总数"。
     */
    private fun updateQueuePosition() {
        val currentIndex = playerManager.getCurrentIndex()
        val totalCount = playerManager.playlist.value?.size ?: 0
        queuePositionTextView.text = if (currentIndex >= 0 && totalCount > 0) {
            "${currentIndex + 1}/$totalCount"
        } else {
            "0/$totalCount"
        }
    }
}
