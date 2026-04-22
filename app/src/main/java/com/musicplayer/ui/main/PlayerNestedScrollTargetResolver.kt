package com.musicplayer.ui.main

internal data class PlayerNestedScrollTarget(
    val lyricsEnabled: Boolean,
    val queueEnabled: Boolean
)

internal object PlayerNestedScrollTargetResolver {

    fun resolve(page: PlayerDetailView): PlayerNestedScrollTarget {
        return when (page) {
            PlayerDetailView.ALBUM_COVER -> PlayerNestedScrollTarget(
                lyricsEnabled = false,
                queueEnabled = false
            )

            PlayerDetailView.LYRICS -> PlayerNestedScrollTarget(
                lyricsEnabled = true,
                queueEnabled = false
            )

            PlayerDetailView.QUEUE -> PlayerNestedScrollTarget(
                lyricsEnabled = false,
                queueEnabled = true
            )
        }
    }
}
