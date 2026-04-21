package com.musicplayer.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class PageSettleCalculatorTest {

    @Test
    fun determineTargetPage_coverDraggedLeftBeyondThreshold_movesToLyrics() {
        val target = PageSettleCalculator.determineTargetPage(
            currentPage = PlayerDetailView.ALBUM_COVER,
            dragOffset = -320f,
            velocityX = 0f,
            pageWidth = 1000f,
            minFlingVelocity = 500f
        )

        assertEquals(PlayerDetailView.LYRICS, target)
    }

    @Test
    fun determineTargetPage_lyricsDraggedRightBeyondThreshold_movesToCover() {
        val target = PageSettleCalculator.determineTargetPage(
            currentPage = PlayerDetailView.LYRICS,
            dragOffset = 320f,
            velocityX = 0f,
            pageWidth = 1000f,
            minFlingVelocity = 500f
        )

        assertEquals(PlayerDetailView.ALBUM_COVER, target)
    }

    @Test
    fun determineTargetPage_queueDraggedRightBeyondThreshold_movesToLyrics() {
        val target = PageSettleCalculator.determineTargetPage(
            currentPage = PlayerDetailView.QUEUE,
            dragOffset = 320f,
            velocityX = 0f,
            pageWidth = 1000f,
            minFlingVelocity = 500f
        )

        assertEquals(PlayerDetailView.LYRICS, target)
    }

    @Test
    fun determineTargetPage_coverDraggedRightBeyondThreshold_bouncesBackToCover() {
        val target = PageSettleCalculator.determineTargetPage(
            currentPage = PlayerDetailView.ALBUM_COVER,
            dragOffset = 320f,
            velocityX = 0f,
            pageWidth = 1000f,
            minFlingVelocity = 500f
        )

        assertEquals(PlayerDetailView.ALBUM_COVER, target)
    }

    @Test
    fun determineTargetPage_smallDragWithValidLeftFling_movesToNextPage() {
        val target = PageSettleCalculator.determineTargetPage(
            currentPage = PlayerDetailView.LYRICS,
            dragOffset = -80f,
            velocityX = -1200f,
            pageWidth = 1000f,
            minFlingVelocity = 500f
        )

        assertEquals(PlayerDetailView.QUEUE, target)
    }

    @Test
    fun determineTargetPage_smallDragWithInvalidEdgeFling_bouncesBack() {
        val target = PageSettleCalculator.determineTargetPage(
            currentPage = PlayerDetailView.QUEUE,
            dragOffset = -80f,
            velocityX = -1200f,
            pageWidth = 1000f,
            minFlingVelocity = 500f
        )

        assertEquals(PlayerDetailView.QUEUE, target)
    }
}
