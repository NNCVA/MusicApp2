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

class PlayerPageSwipeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private enum class TouchMode {
        IDLE,
        HORIZONTAL_PAGING,
        VERTICAL_PASS_THROUGH
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()

    private var touchMode = TouchMode.IDLE
    private var currentPage = PlayerDetailView.ALBUM_COVER
    private var dragOffset = 0f
    private var downX = 0f
    private var downY = 0f
    private var velocityTracker: VelocityTracker? = null
    private var settleAnimator: ValueAnimator? = null

    private lateinit var albumCoverView: View
    private lateinit var lyricsView: View
    private lateinit var queueView: View

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

    fun animateToPage(targetPage: PlayerDetailView) {
        if (width == 0) {
            post { setCurrentPage(targetPage) }
            return
        }

        val targetOffset = (currentPage.index - targetPage.index) * width.toFloat()
        startSettleAnimation(targetPage, targetOffset)
    }

    fun showNextPage() {
        animateToPage(currentPage.next())
    }

    fun syncPageTranslations() {
        if (!::albumCoverView.isInitialized || width == 0) {
            return
        }

        val pageWidth = width.toFloat()
        albumCoverView.translationX = pageTranslation(PlayerDetailView.ALBUM_COVER, pageWidth)
        lyricsView.translationX = pageTranslation(PlayerDetailView.LYRICS, pageWidth)
        queueView.translationX = pageTranslation(PlayerDetailView.QUEUE, pageWidth)
    }

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

    private fun shouldStartHorizontalPaging(event: MotionEvent): Boolean {
        val deltaX = event.x - downX
        val deltaY = event.y - downY
        return abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY)
    }

    private fun shouldPassThroughVertically(event: MotionEvent): Boolean {
        val deltaX = event.x - downX
        val deltaY = event.y - downY
        return abs(deltaY) > touchSlop && abs(deltaY) >= abs(deltaX)
    }

    private fun startHorizontalPaging() {
        cancelSettleAnimation()
        touchMode = TouchMode.HORIZONTAL_PAGING
        parent?.requestDisallowInterceptTouchEvent(true)
    }

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

    private fun finishGesture() {
        touchMode = TouchMode.IDLE
        parent?.requestDisallowInterceptTouchEvent(false)
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun trackMotion(event: MotionEvent) {
        velocityTracker?.addMovement(event)
    }

    private fun adjustedDragOffset(rawOffset: Float): Float {
        val pageWidth = width.toFloat()
        val resistedOffset = when {
            currentPage == PlayerDetailView.ALBUM_COVER && rawOffset > 0f -> rawOffset * EDGE_RESISTANCE
            currentPage == PlayerDetailView.QUEUE && rawOffset < 0f -> rawOffset * EDGE_RESISTANCE
            else -> rawOffset
        }
        return resistedOffset.coerceIn(-pageWidth, pageWidth)
    }

    private fun pageTranslation(page: PlayerDetailView, pageWidth: Float): Float {
        return (page.index - currentPage.index) * pageWidth + dragOffset
    }

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

    private fun cancelSettleAnimation() {
        settleAnimator?.cancel()
        settleAnimator = null
    }

    fun release() {
        cancelSettleAnimation()
        velocityTracker?.recycle()
        velocityTracker = null
        onPageChanged = null
    }

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
        const val EDGE_RESISTANCE = 0.35f
        const val MIN_SETTLE_DURATION_MS = 180L
        const val MAX_SETTLE_DURATION_MS = 280L
        const val DEFAULT_SETTLE_DURATION_MS = 220L
    }
}
