package com.musicplayer.ui.main

import com.musicplayer.databinding.ContentPlayerDetailBinding

enum class PlayerDetailView(val index: Int) {
    ALBUM_COVER(0),
    LYRICS(1),
    QUEUE(2);

    fun next(): PlayerDetailView = values()[(index + 1) % values().size]

    fun nextOrSelf(): PlayerDetailView = when (this) {
        ALBUM_COVER -> LYRICS
        LYRICS -> QUEUE
        QUEUE -> QUEUE
    }

    fun previousOrSelf(): PlayerDetailView = when (this) {
        ALBUM_COVER -> ALBUM_COVER
        LYRICS -> ALBUM_COVER
        QUEUE -> LYRICS
    }
}

internal class PlayerViewSwipeController(
    private val binding: ContentPlayerDetailBinding,
    private val onQueueViewShown: () -> Unit
) {

    fun bind() {
        binding.pageSwipeContainer.onPageChanged = { page ->
            if (page == PlayerDetailView.QUEUE) {
                onQueueViewShown()
            }
        }
        binding.pageSwipeContainer.syncPageTranslations()
    }

    fun setCurrentPage(page: PlayerDetailView) {
        binding.pageSwipeContainer.setCurrentPage(page)
    }

    fun animateToPage(page: PlayerDetailView) {
        binding.pageSwipeContainer.animateToPage(page)
    }

    fun showNextView() {
        binding.pageSwipeContainer.showNextPage()
    }

    fun syncPageTranslations() {
        binding.pageSwipeContainer.syncPageTranslations()
    }

    fun release() {
        binding.pageSwipeContainer.release()
    }
}
