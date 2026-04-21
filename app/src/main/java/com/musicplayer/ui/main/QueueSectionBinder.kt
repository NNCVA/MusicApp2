package com.musicplayer.ui.main

import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicplayer.data.model.Song
import com.musicplayer.databinding.ContentPlayerDetailBinding
import com.musicplayer.service.PlayerManager
import com.musicplayer.ui.adapter.QueueAdapter

internal class QueueSectionBinder(
    private val binding: ContentPlayerDetailBinding,
    private val playerManager: PlayerManager,
    lifecycleOwner: LifecycleOwner
) {

    private val queuePositionTextView: TextView = binding.toolbarQueuePosition

    private val queueAdapter = QueueAdapter { song, position ->
        playerManager.playSong(song, playerManager.playlist.value ?: emptyList(), position)
    }

    private val playlistObserver = Observer<List<Song>> { playlist ->
        queueAdapter.submitList(playlist)
        updateQueuePosition()
    }

    private val currentSongObserver = Observer<Song?> { song ->
        queueAdapter.currentPlayingSongId = song?.id
        updateQueuePosition()
        scrollToCurrentSong()
    }

    init {
        binding.queueRecyclerView.apply {
            adapter = queueAdapter
            layoutManager = LinearLayoutManager(context)
        }

        playerManager.playlist.observe(lifecycleOwner, playlistObserver)
        playerManager.currentSong.observe(lifecycleOwner, currentSongObserver)
    }

    fun release() {
        playerManager.playlist.removeObserver(playlistObserver)
        playerManager.currentSong.removeObserver(currentSongObserver)
        binding.queueRecyclerView.adapter = null
    }

    fun scrollToCurrentSong() {
        val currentIndex = playerManager.getCurrentIndex()
        if (currentIndex >= 0) {
            binding.queueRecyclerView.post {
                val layoutManager = binding.queueRecyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(currentIndex, 0)
            }
        }
    }

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
