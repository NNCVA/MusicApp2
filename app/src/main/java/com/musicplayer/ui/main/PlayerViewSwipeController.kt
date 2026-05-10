package com.musicplayer.ui.main

import android.view.View
import androidx.core.view.ViewCompat
import com.musicplayer.databinding.ContentPlayerDetailBinding

/**
 * 全屏播放器的三个页面枚举。
 *
 * 页面顺序固定：[ALBUM_COVER] → [LYRICS] → [QUEUE]，不循环。
 * 到达边界时回弹，不会从 QUEUE 滑回 ALBUM_COVER。
 *
 * @property index 页面在横滑容器中的位置索引
 */
enum class PlayerDetailView(val index: Int) {
    ALBUM_COVER(0),
    LYRICS(1),
    QUEUE(2);

    /** 循环取下一个页面，QUEUE 的下一个是 ALBUM_COVER。仅用于 btnShowLyrics 顺序切页。 */
    fun next(): PlayerDetailView = values()[(index + 1) % values().size]

    /**
     * 向右（内容方向）取下一个页面；已在 QUEUE 则返回自身（不循环）。
     * 用于 [PageSettleCalculator] 判定拖拽距离或 fling 速度为负值时的目标页。
     */
    fun nextOrSelf(): PlayerDetailView = when (this) {
        ALBUM_COVER -> LYRICS
        LYRICS -> QUEUE
        QUEUE -> QUEUE
    }

    /**
     * 向左（返回方向）取上一个页面；已在 ALBUM_COVER 则返回自身（不循环）。
     * 用于 [PageSettleCalculator] 判定拖拽距离或 fling 速度为正值时的目标页。
     */
    fun previousOrSelf(): PlayerDetailView = when (this) {
        ALBUM_COVER -> ALBUM_COVER
        LYRICS -> ALBUM_COVER
        QUEUE -> LYRICS
    }
}

/**
 * 全屏播放器的页面切换协调器。
 *
 * 绑定 [PlayerPageSwipeLayout]，管理页面状态，并在切页时同步嵌套滚动目标。
 * 属于轻量控制器，不直接处理手势——手势逻辑由 [PlayerPageSwipeLayout] 和 [PageSettleCalculator] 承担。
 *
 * @param binding 播放器详情布局的 ViewBinding
 * @param onQueueViewShown 播放队列页显示时的回调，用于触发队列滚动到当前歌曲等操作
 */
internal class PlayerViewSwipeController(
    private val binding: ContentPlayerDetailBinding,
    private val onQueueViewShown: () -> Unit
) {

    /**
     * 绑定控制器到布局。
     *
     * 注册页面切换回调（切页时同步嵌套滚动目标），设置初始嵌套滚动状态为 [PlayerDetailView.ALBUM_COVER]，
     * 并同步各页面的 translationX 位置。
     */
    fun bind() {
        binding.pageSwipeContainer.onPageChanged = { page ->
            applyNestedScrollTarget(page)
            if (page == PlayerDetailView.QUEUE) {
                onQueueViewShown()
            }
        }
        applyNestedScrollTarget(PlayerDetailView.ALBUM_COVER)
        binding.pageSwipeContainer.syncPageTranslations()
    }

    /**
     * 立即设置当前页面，无动画。用于初始化或从外部状态恢复页面位置。
     *
     * @param page 目标页面
     */
    fun setCurrentPage(page: PlayerDetailView) {
        binding.pageSwipeContainer.setCurrentPage(page)
    }

    /**
     * 动画滚动到指定页面。用于 btnShowLyrics 顺序切页等场景。
     *
     * @param page 目标页面
     */
    fun animateToPage(page: PlayerDetailView) {
        binding.pageSwipeContainer.animateToPage(page)
    }

    /** 顺序切换到下一个页面（封面 → 歌词 → 播放队列 → 封面），封装 [PlayerPageSwipeLayout.showNextPage]。 */
    fun showNextView() {
        binding.pageSwipeContainer.showNextPage()
    }

    /**
     * 同步各页面的 translationX，确保视图位置与当前页面状态一致。
     * 通常在容器重建或外部状态变更后调用。
     */
    fun syncPageTranslations() {
        binding.pageSwipeContainer.syncPageTranslations()
    }

    /**
     * 释放内部资源，清理页面切换回调。
     * 在持有者（Activity/Fragment）销毁时调用，避免回调泄漏。
     */
    fun release() {
        binding.pageSwipeContainer.release()
    }

    /**
     * 根据当前页面切换嵌套滚动目标。
     *
     * [BottomSheetBehavior] 会缓存它认为的 nested scrolling child。
     * 如果不主动禁用非当前页的 nested scrolling，切页后 BottomSheet 仍可能把旧页面
     * （如 lyrics_view）当作纵向滚动宿主，导致新页面（如 queue_recycler_view）无法滚动。
     * 因此每页只启用对应 View 的 nested scrolling，并触发父容器 requestLayout 让 BottomSheet 刷新缓存。
     */
    private fun applyNestedScrollTarget(page: PlayerDetailView) {
        val target = PlayerNestedScrollTargetResolver.resolve(page)
        ViewCompat.setNestedScrollingEnabled(binding.lyricsView, target.lyricsEnabled)
        ViewCompat.setNestedScrollingEnabled(binding.queueRecyclerView, target.queueEnabled)
        // 触发父容器重新布局，使 BottomSheetBehavior 重新获取正确的 nested scrolling child
        (binding.root.parent as? View)?.requestLayout()
    }
}
