package com.musicplayer.ui.main

/**
 * 嵌套滚动目标配置，声明歌词 View 和播放队列 View 的 nested scrolling 开关状态。
 *
 * 每个页面只允许一个纵向可滚动 View 启用 nested scrolling（封面页两个都禁用），
 * 以避免 [BottomSheetBehavior] 缓存错误的滚动宿主。
 *
 * @param lyricsEnabled 是否启用歌词 View 的嵌套滚动
 * @param queueEnabled 是否启用播放队列 View 的嵌套滚动
 */
internal data class PlayerNestedScrollTarget(
    val lyricsEnabled: Boolean,
    val queueEnabled: Boolean
)

/**
 * 根据当前页面决定哪些 View 应启用嵌套滚动。
 *
 * 配合 [PlayerViewSwipeController.applyNestedScrollTarget] 使用，在切页时调用。
 * 规则：ALBUM_COVER 页禁用全部纵向滚动（封面无滚动内容），
 * LYRICS 页仅启用歌词 View，QUEUE 页仅启用队列 View。
 */
internal object PlayerNestedScrollTargetResolver {

    /**
     * 根据当前页面返回嵌套滚动配置。
     *
     * 每页最多只有一个 View 启用 nested scrolling，确保 [BottomSheetBehavior]
     * 在纵向滚动到边界后能正确接管手势，不会因为缓存旧的 nested scrolling child 导致新页面无法滚动。
     *
     * @param page 当前播放器页面
     * @return 对应页面的嵌套滚动配置
     */
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
