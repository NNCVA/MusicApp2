package com.musicplayer.ui.main

import kotlin.math.abs

/**
 * 无状态的页面结算计算器。
 *
 * 根据拖拽位移和 fling 速度，按优先级判定手势结束后应切换到哪个页面。
 * 两个决策路径：先看距离是否超过阈值，再看速度是否足够触发 fling。
 * 边界行为由 [PlayerDetailView.nextOrSelf] 和 [previousOrSelf] 保证不循环。
 */
internal object PageSettleCalculator {
    private const val DEFAULT_DISTANCE_THRESHOLD_RATIO = 0.25f

    /**
     * 根据拖拽位移和 fling 速度判定目标页面。
     *
     * 决策优先级：
     * 1. 距离阈值：拖拽超过页面宽度的 [distanceThresholdRatio] 时切换到对应方向的页面
     * 2. 速度阈值：fling 速度超过 [minFlingVelocity] 时切换到对应方向的页面
     * 3. 两者都不满足：回弹到 [currentPage]
     *
     * @param currentPage 当前所在的页面
     * @param dragOffset 水平拖拽偏移量（负值 = 向左滑，正值 = 向右滑）
     * @param velocityX 水平 fling 速度（负值 = 向左，正值 = 向右）
     * @param pageWidth 单页宽度，用于计算距离阈值
     * @param minFlingVelocity 最小 fling 速度，低于此值忽略速度判定
     * @param distanceThresholdRatio 距离阈值比例，默认 0.25（页面宽度的 1/4）
     * @return 目标页面
     */
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

        // 路径 1：距离阈值判定——拖拽位移超过页面宽度的一定比例时切换
        if (dragOffset <= -distanceThreshold) {
            return currentPage.nextOrSelf()
        }
        if (dragOffset >= distanceThreshold) {
            return currentPage.previousOrSelf()
        }

        // 路径 2：速度阈值判定——fling 速度足够快时切换（即使距离未达阈值）
        if (abs(velocityX) >= minFlingVelocity) {
            return if (velocityX < 0f) {
                currentPage.nextOrSelf()
            } else {
                currentPage.previousOrSelf()
            }
        }

        // 两条路径都不满足，回弹到当前页面
        return currentPage
    }
}
