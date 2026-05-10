package com.musicplayer.ui.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.musicplayer.R
import com.musicplayer.ui.main.PageSettleCalculator
import com.musicplayer.ui.main.PlayerDetailView
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 全屏播放器三页横滑容器。
 *
 * 继承自 FrameLayout，负责处理封面/歌词/播放队列三个子页面的左右切换手势。
 * 支持边界阻力（首页右滑、末页左滑时有阻尼感）和 fling 快速翻页。
 * 内部通过 [touchMode] 状态机区分水平翻页与垂直穿透（交给子 View 纵向滚动）。
 */
class PlayerPageSwipeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * 触摸模式状态机。
     *
     * IDLE：手势尚未判定方向
     * HORIZONTAL_PAGING：已判定为水平翻页，本 View 消费事件
     * VERTICAL_PASS_THROUGH：已判定为垂直滚动，事件交给子 View
     */
    private enum class TouchMode {
        IDLE,
        HORIZONTAL_PAGING,
        VERTICAL_PASS_THROUGH
    }

    /** 系统触摸阈值，滑动距离超过此值才开始判定方向 */
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    /** 最小 fling 速度阈值（像素/秒），低于此值视为普通拖动 */
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()

    /** 当前触摸模式 */
    private var touchMode = TouchMode.IDLE
    /** 当前显示的页面 */
    private var currentPage = PlayerDetailView.ALBUM_COVER
    /** 当前拖动偏移量（像素），正值表示右滑，负值表示左滑 */
    private var dragOffset = 0f
    /** 手势起始 X 坐标 */
    private var downX = 0f
    /** 手势起始 Y 坐标 */
    private var downY = 0f
    /** 速度追踪器，用于计算 fling 速度 */
    private var velocityTracker: VelocityTracker? = null
    /** 页面归位动画实例 */
    private var settleAnimator: ValueAnimator? = null

    private lateinit var albumCoverView: View
    private lateinit var lyricsView: View
    private lateinit var queueView: View

    /** 页面切换回调，由外部（PlayerViewSwipeController）设置 */
    var onPageChanged: ((PlayerDetailView) -> Unit)? = null

    init {
        isClickable = true
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        albumCoverView = findViewById(R.id.album_cover_view)
        lyricsView = findViewById(R.id.lyrics_view)
        queueView = findViewById(R.id.queue_view)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        syncPageTranslations()
    }

    /**
     * 拦截决策树：
     * - ACTION_DOWN → 记录起点，不拦截（让子 View 有机会处理）
     * - ACTION_MOVE → 三个分支：
     *   1. 已处于水平翻页模式 → 拦截
     *   2. 水平位移超过 touchSlop 且大于垂直位移 → 判定为水平翻页，拦截
     *   3. 垂直位移超过 touchSlop 且大于等于水平位移 → 判定为垂直滚动，不拦截
     * - ACTION_UP / ACTION_CANCEL → 重置手势状态
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        trackMotion(ev)

        return when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                beginGesture(ev)
                false
            }

            MotionEvent.ACTION_MOVE -> {
                when {
                    touchMode == TouchMode.HORIZONTAL_PAGING -> true
                    shouldStartHorizontalPaging(ev) -> {
                        startHorizontalPaging()
                        true
                    }
                    shouldPassThroughVertically(ev) -> {
                        touchMode = TouchMode.VERTICAL_PASS_THROUGH
                        false
                    }
                    else -> false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                finishGesture()
                false
            }

            else -> false
        }
    }

    /**
     * 触摸状态机：
     *
     * ACTION_DOWN  → 记录起点，标记 IDLE
     * ACTION_MOVE  → 若尚未判定方向，尝试判定水平/垂直；
     *                 若已判定水平，则计算带边界阻力的偏移量并更新页面位置
     * ACTION_UP    → 水平模式下调用 settleToTargetPage 结算目标页并播放归位动画；
     *                 非水平模式直接结束手势
     * ACTION_CANCEL → 水平模式下回弹到当前页；非水平模式直接结束手势
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        trackMotion(event)

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                beginGesture(event)
                true
            }

            MotionEvent.ACTION_MOVE -> {
                if (touchMode != TouchMode.HORIZONTAL_PAGING) {
                    if (shouldStartHorizontalPaging(event)) {
                        startHorizontalPaging()
                    } else if (shouldPassThroughVertically(event)) {
                        touchMode = TouchMode.VERTICAL_PASS_THROUGH
                        return false
                    }
                }

                if (touchMode == TouchMode.HORIZONTAL_PAGING) {
                    dragOffset = adjustedDragOffset(event.x - downX)
                    syncPageTranslations()
                    true
                } else {
                    false
                }
            }

            MotionEvent.ACTION_UP -> {
                if (touchMode == TouchMode.HORIZONTAL_PAGING) {
                    settleToTargetPage()
                    finishGesture()
                    true
                } else {
                    finishGesture()
                    false
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (touchMode == TouchMode.HORIZONTAL_PAGING) {
                    animateToPage(currentPage)
                    finishGesture()
                    true
                } else {
                    finishGesture()
                    false
                }
            }

            else -> super.onTouchEvent(event)
        }
    }

    /**
     * 直接设置当前页面（无动画）。
     *
     * 取消正在进行的归位动画，重置拖动偏移，同步三个子 View 的 translationX。
     * 页面发生变化时触发 [onPageChanged] 回调。
     */
    fun setCurrentPage(page: PlayerDetailView) {
        val pageChanged = currentPage != page
        cancelSettleAnimation()
        currentPage = page
        dragOffset = 0f
        syncPageTranslations()
        if (pageChanged) {
            onPageChanged?.invoke(page)
        }
    }

    /**
     * 动画切换到目标页面。
     *
     * 计算当前页与目标页的偏移差，启动归位动画平滑过渡。
     * 若布局尚未完成（width==0），则延迟到下一帧直接设置。
     */
    fun animateToPage(targetPage: PlayerDetailView) {
        if (width == 0) {
            post { setCurrentPage(targetPage) }
            return
        }

        val targetOffset = (currentPage.index - targetPage.index) * width.toFloat()
        startSettleAnimation(targetPage, targetOffset)
    }

    /** 顺序切换到下一页（封面→歌词→队列→封面），由 [animateToPage] 执行动画 */
    fun showNextPage() {
        animateToPage(currentPage.next())
    }

    /**
     * 同步三个子页面的水平位移。
     *
     * 根据各页与当前页的索引差加上拖动偏移量计算 translationX，
     * 实现三页并排随手指滑动的效果。
     */
    fun syncPageTranslations() {
        if (!::albumCoverView.isInitialized || width == 0) {
            return
        }

        val pageWidth = width.toFloat()
        albumCoverView.translationX = pageTranslation(PlayerDetailView.ALBUM_COVER, pageWidth)
        lyricsView.translationX = pageTranslation(PlayerDetailView.LYRICS, pageWidth)
        queueView.translationX = pageTranslation(PlayerDetailView.QUEUE, pageWidth)
    }

    /**
     * 手指抬起后结算目标页面。
     *
     * 计算当前 fling 速度，委托 [PageSettleCalculator] 根据位移和速度判定目标页，
     * 然后启动归位动画。
     */
    private fun settleToTargetPage() {
        velocityTracker?.computeCurrentVelocity(1000)
        val velocityX = velocityTracker?.xVelocity ?: 0f
        val targetPage = PageSettleCalculator.determineTargetPage(
            currentPage = currentPage,
            dragOffset = dragOffset,
            velocityX = velocityX,
            pageWidth = width.toFloat(),
            minFlingVelocity = minFlingVelocity
        )
        animateToPage(targetPage)
    }

    /** 判断是否应启动水平翻页：水平位移超过 touchSlop 且大于垂直位移 */
    private fun shouldStartHorizontalPaging(event: MotionEvent): Boolean {
        val deltaX = event.x - downX
        val deltaY = event.y - downY
        return abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY)
    }

    /** 判断是否应穿透给子 View 处理垂直滚动：垂直位移超过 touchSlop 且大于等于水平位移 */
    private fun shouldPassThroughVertically(event: MotionEvent): Boolean {
        val deltaX = event.x - downX
        val deltaY = event.y - downY
        return abs(deltaY) > touchSlop && abs(deltaY) >= abs(deltaX)
    }

    /** 进入水平翻页模式：取消已有动画，禁止父容器拦截事件 */
    private fun startHorizontalPaging() {
        cancelSettleAnimation()
        touchMode = TouchMode.HORIZONTAL_PAGING
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    /** 手势开始：取消动画、记录起始坐标、重置触摸模式、初始化速度追踪器 */
    private fun beginGesture(event: MotionEvent) {
        cancelSettleAnimation()
        downX = event.x
        downY = event.y
        touchMode = TouchMode.IDLE
        velocityTracker?.recycle()
        velocityTracker = VelocityTracker.obtain().apply {
            addMovement(event)
        }
    }

    /** 手势结束：重置触摸模式、恢复父容器拦截、回收速度追踪器 */
    private fun finishGesture() {
        touchMode = TouchMode.IDLE
        parent?.requestDisallowInterceptTouchEvent(false)
        velocityTracker?.recycle()
        velocityTracker = null
    }

    /** 将事件传递给速度追踪器 */
    private fun trackMotion(event: MotionEvent) {
        velocityTracker?.addMovement(event)
    }

    /**
     * 对拖动偏移量施加边界阻力。
     *
     * 在首页右滑（rawOffset > 0）或末页左滑（rawOffset < 0）时，
     * 偏移量乘以 [EDGE_RESISTANCE]（0.35）产生阻尼感，防止用户滑出边界。
     * 最终结果钳制在一个页面宽度范围内。
     */
    private fun adjustedDragOffset(rawOffset: Float): Float {
        val pageWidth = width.toFloat()
        val resistedOffset = when {
            currentPage == PlayerDetailView.ALBUM_COVER && rawOffset > 0f -> rawOffset * EDGE_RESISTANCE
            currentPage == PlayerDetailView.QUEUE && rawOffset < 0f -> rawOffset * EDGE_RESISTANCE
            else -> rawOffset
        }
        return resistedOffset.coerceIn(-pageWidth, pageWidth)
    }

    /** 计算指定页面的 translationX：页面索引差 × 页面宽度 + 拖动偏移 */
    private fun pageTranslation(page: PlayerDetailView, pageWidth: Float): Float {
        return (page.index - currentPage.index) * pageWidth + dragOffset
    }

    /**
     * 启动页面归位动画。
     *
     * 动画生命周期：
     * 1. 取消已有动画，若起止偏移相同则直接设置页面
     * 2. ValueAnimator 从 startOffset 插值到 targetOffset
     * 3. 每帧更新 dragOffset 并同步三个子 View 的 translationX
     * 4. 动画结束时更新 currentPage、重置 dragOffset、触发 onPageChanged 回调
     * 5. 动画被取消时清空 animator 引用
     */
    private fun startSettleAnimation(targetPage: PlayerDetailView, targetOffset: Float) {
        cancelSettleAnimation()

        val startOffset = dragOffset
        if (startOffset == targetOffset) {
            setCurrentPage(targetPage)
            return
        }

        settleAnimator = ValueAnimator.ofFloat(startOffset, targetOffset).apply {
            duration = animationDuration(startOffset, targetOffset, width.toFloat())
            addUpdateListener { animator ->
                dragOffset = animator.animatedValue as Float
                syncPageTranslations()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    settleAnimator = null
                    val pageChanged = currentPage != targetPage
                    currentPage = targetPage
                    dragOffset = 0f
                    syncPageTranslations()
                    if (pageChanged) {
                        onPageChanged?.invoke(targetPage)
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    settleAnimator = null
                }
            })
            start()
        }
    }

    /** 取消正在进行的归位动画 */
    private fun cancelSettleAnimation() {
        settleAnimator?.cancel()
        settleAnimator = null
    }

    /** 释放所有资源：取消动画、回收速度追踪器、清空回调 */
    fun release() {
        cancelSettleAnimation()
        velocityTracker?.recycle()
        velocityTracker = null
        onPageChanged = null
    }

    /**
     * 根据拖动距离比例计算动画时长。
     *
     * 距离越短动画越快：在 [MIN_SETTLE_DURATION_MS]（180ms）和
     * [MAX_SETTLE_DURATION_MS]（280ms）之间线性插值。
     * pageWidth <= 0 时返回默认值 [DEFAULT_SETTLE_DURATION_MS]（220ms）。
     */
    private fun animationDuration(startOffset: Float, targetOffset: Float, pageWidth: Float): Long {
        if (pageWidth <= 0f) {
            return DEFAULT_SETTLE_DURATION_MS
        }
        val distanceRatio = abs(targetOffset - startOffset) / pageWidth
        return (MIN_SETTLE_DURATION_MS + (MAX_SETTLE_DURATION_MS - MIN_SETTLE_DURATION_MS) * distanceRatio)
            .roundToLong()
            .coerceIn(MIN_SETTLE_DURATION_MS, MAX_SETTLE_DURATION_MS)
    }

    private companion object {
        /** 边界阻力系数，越小阻力越大（0.35 表示边界处拖动速度降为 35%） */
        const val EDGE_RESISTANCE = 0.35f
        /** 归位动画最短时长（ms） */
        const val MIN_SETTLE_DURATION_MS = 180L
        /** 归位动画最长时长（ms） */
        const val MAX_SETTLE_DURATION_MS = 280L
        /** 归位动画默认时长（ms），用于 pageWidth <= 0 的降级场景 */
        const val DEFAULT_SETTLE_DURATION_MS = 220L
    }
}
