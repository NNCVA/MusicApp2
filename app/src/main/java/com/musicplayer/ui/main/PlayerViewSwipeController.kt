package com.musicplayer.ui.main

import android.annotation.SuppressLint
import android.content.res.Resources
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.musicplayer.databinding.ContentPlayerDetailBinding
import kotlin.math.abs

internal enum class PlayerDetailView(val index: Int) {
    ALBUM_COVER(0),
    LYRICS(1),
    QUEUE(2);

    fun next(): PlayerDetailView = values()[(index + 1) % values().size]
}

internal class PlayerViewSwipeController(
    private val binding: ContentPlayerDetailBinding,
    private val resources: Resources,
    private val onQueueViewShown: () -> Unit
) {

    private var currentView = PlayerDetailView.ALBUM_COVER
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private val minVelocity = 500f

    private val gestureDetector by lazy {
        GestureDetector(binding.root.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                swipeStartX = e.rawX
                swipeStartY = e.rawY
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (abs(distanceX) > abs(distanceY)) {
                    updateViewTranslation(e2.rawX - swipeStartX)
                    return true
                }
                return false
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (abs(velocityX) > abs(velocityY) && abs(velocityX) > minVelocity) {
                    if (velocityX < 0) {
                        when (currentView) {
                            PlayerDetailView.ALBUM_COVER -> switchToView(PlayerDetailView.LYRICS)
                            PlayerDetailView.LYRICS -> switchToView(PlayerDetailView.QUEUE)
                            PlayerDetailView.QUEUE -> switchToView(PlayerDetailView.ALBUM_COVER)
                        }
                    } else {
                        when (currentView) {
                            PlayerDetailView.LYRICS -> switchToView(PlayerDetailView.ALBUM_COVER)
                            PlayerDetailView.QUEUE -> switchToView(PlayerDetailView.LYRICS)
                            PlayerDetailView.ALBUM_COVER -> Unit
                        }
                    }
                    return true
                }
                return false
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    fun bind() {
        bindNestedScrollableTouch(binding.lyricsView)
        bindNestedScrollableTouch(binding.queueRecyclerView)

        binding.root.post {
            val screenWidth = screenWidth()
            binding.albumCoverView.translationX = 0f
            binding.lyricsView.translationX = screenWidth
            binding.queueView.translationX = screenWidth * 2
        }

        binding.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.rawX
                    swipeStartY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> true

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val deltaX = event.rawX - swipeStartX
                    val threshold = screenWidth() * 0.3f

                    if (abs(deltaX) > threshold) {
                        if (deltaX < 0) {
                            when (currentView) {
                                PlayerDetailView.ALBUM_COVER -> switchToView(PlayerDetailView.LYRICS)
                                PlayerDetailView.LYRICS -> switchToView(PlayerDetailView.QUEUE)
                                PlayerDetailView.QUEUE -> switchToView(PlayerDetailView.ALBUM_COVER)
                            }
                        } else {
                            when (currentView) {
                                PlayerDetailView.LYRICS -> switchToView(PlayerDetailView.ALBUM_COVER)
                                PlayerDetailView.QUEUE -> switchToView(PlayerDetailView.LYRICS)
                                PlayerDetailView.ALBUM_COVER -> Unit
                            }
                        }
                    } else {
                        resetViewTranslation()
                    }
                    true
                }

                else -> true
            }
        }
    }

    fun showNextView() {
        switchToView(currentView.next())
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindNestedScrollableTouch(view: View) {
        view.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            view.parent?.requestDisallowInterceptTouchEvent(true)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.rawX
                    swipeStartY = event.rawY
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = abs(event.rawX - swipeStartX)
                    val deltaY = abs(event.rawY - swipeStartY)
                    deltaX > deltaY && deltaX > 30
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun updateViewTranslation(deltaX: Float) {
        val screenWidth = screenWidth()

        when (currentView) {
            PlayerDetailView.ALBUM_COVER -> {
                val clampedTranslation = deltaX.coerceIn(-screenWidth, 0f)
                binding.albumCoverView.translationX = clampedTranslation
                binding.lyricsView.translationX = clampedTranslation + screenWidth
                binding.queueView.translationX = clampedTranslation + screenWidth * 2
            }

            PlayerDetailView.LYRICS -> {
                val clampedTranslation = deltaX.coerceIn(-screenWidth, screenWidth)
                binding.albumCoverView.translationX = clampedTranslation - screenWidth
                binding.lyricsView.translationX = clampedTranslation
                binding.queueView.translationX = clampedTranslation + screenWidth
            }

            PlayerDetailView.QUEUE -> {
                val clampedTranslation = deltaX.coerceIn(0f, screenWidth)
                binding.albumCoverView.translationX = clampedTranslation - screenWidth * 2
                binding.lyricsView.translationX = clampedTranslation - screenWidth
                binding.queueView.translationX = clampedTranslation
            }
        }
    }

    private fun resetViewTranslation() {
        val screenWidth = screenWidth()
        val animationDuration = 200L

        when (currentView) {
            PlayerDetailView.ALBUM_COVER -> {
                binding.albumCoverView.animate()
                    .translationX(0f)
                    .setDuration(animationDuration)
                    .start()
                binding.lyricsView.translationX = screenWidth
                binding.queueView.translationX = screenWidth * 2
            }

            PlayerDetailView.LYRICS -> {
                binding.albumCoverView.translationX = -screenWidth
                binding.lyricsView.animate()
                    .translationX(0f)
                    .setDuration(animationDuration)
                    .start()
                binding.queueView.translationX = screenWidth
            }

            PlayerDetailView.QUEUE -> {
                binding.albumCoverView.translationX = -screenWidth * 2
                binding.lyricsView.translationX = -screenWidth
                binding.queueView.animate()
                    .translationX(0f)
                    .setDuration(animationDuration)
                    .start()
            }
        }
    }

    private fun switchToView(targetView: PlayerDetailView) {
        if (targetView == currentView) return

        binding.albumCoverView.visibility = View.VISIBLE
        binding.albumCoverView.alpha = 1f
        binding.lyricsView.visibility = View.VISIBLE
        binding.lyricsView.alpha = 1f
        binding.queueView.visibility = View.VISIBLE
        binding.queueView.alpha = 1f

        val screenWidth = screenWidth()

        when (targetView) {
            PlayerDetailView.ALBUM_COVER -> {
                binding.albumCoverView.animate().translationX(0f).setDuration(300).start()
                binding.lyricsView.animate().translationX(screenWidth).setDuration(300).start()
                binding.queueView.animate().translationX(screenWidth * 2).setDuration(300).start()
            }

            PlayerDetailView.LYRICS -> {
                binding.albumCoverView.animate().translationX(-screenWidth).setDuration(300).start()
                binding.lyricsView.animate().translationX(0f).setDuration(300).start()
                binding.queueView.animate().translationX(screenWidth).setDuration(300).start()
            }

            PlayerDetailView.QUEUE -> {
                binding.albumCoverView.animate().translationX(-screenWidth * 2).setDuration(300).start()
                binding.lyricsView.animate().translationX(-screenWidth).setDuration(300).start()
                binding.queueView.animate().translationX(0f).setDuration(300).start()
            }
        }

        currentView = targetView

        if (targetView == PlayerDetailView.QUEUE) {
            onQueueViewShown()
        }
    }

    private fun screenWidth(): Float = resources.displayMetrics.widthPixels.toFloat()
}
