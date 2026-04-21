package com.musicplayer.ui.main

import kotlin.math.abs

internal object PageSettleCalculator {
    private const val DEFAULT_DISTANCE_THRESHOLD_RATIO = 0.25f

    fun determineTargetPage(
        currentPage: PlayerDetailView,
        dragOffset: Float,
        velocityX: Float,
        pageWidth: Float,
        minFlingVelocity: Float,
        distanceThresholdRatio: Float = DEFAULT_DISTANCE_THRESHOLD_RATIO
    ): PlayerDetailView {
        if (pageWidth <= 0f) {
            return currentPage
        }

        val distanceThreshold = pageWidth * distanceThresholdRatio

        if (dragOffset <= -distanceThreshold) {
            return currentPage.nextOrSelf()
        }
        if (dragOffset >= distanceThreshold) {
            return currentPage.previousOrSelf()
        }

        if (abs(velocityX) >= minFlingVelocity) {
            return if (velocityX < 0f) {
                currentPage.nextOrSelf()
            } else {
                currentPage.previousOrSelf()
            }
        }

        return currentPage
    }
}
