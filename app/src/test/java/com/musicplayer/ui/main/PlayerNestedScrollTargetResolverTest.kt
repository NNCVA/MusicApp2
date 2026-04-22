package com.musicplayer.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNestedScrollTargetResolverTest {

    @Test
    fun resolve_albumCoverDisablesBothVerticalHosts() {
        val target = PlayerNestedScrollTargetResolver.resolve(PlayerDetailView.ALBUM_COVER)

        assertFalse(target.lyricsEnabled)
        assertFalse(target.queueEnabled)
    }

    @Test
    fun resolve_lyricsEnablesOnlyLyricsHost() {
        val target = PlayerNestedScrollTargetResolver.resolve(PlayerDetailView.LYRICS)

        assertTrue(target.lyricsEnabled)
        assertFalse(target.queueEnabled)
    }

    @Test
    fun resolve_queueEnablesOnlyQueueHost() {
        val target = PlayerNestedScrollTargetResolver.resolve(PlayerDetailView.QUEUE)

        assertFalse(target.lyricsEnabled)
        assertTrue(target.queueEnabled)
    }
}
