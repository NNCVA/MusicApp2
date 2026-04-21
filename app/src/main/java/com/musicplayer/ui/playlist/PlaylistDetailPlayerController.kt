package com.musicplayer.ui.playlist

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.musicplayer.R
import com.musicplayer.data.model.PlayMode
import com.musicplayer.data.model.Song
import com.musicplayer.databinding.LayoutPlayerBottomSheetBinding
import com.musicplayer.service.PlayerManager
import com.musicplayer.ui.main.PlayerLyricsController
import com.musicplayer.ui.main.PlayerViewSwipeController
import com.musicplayer.ui.main.QueueSectionBinder
import com.musicplayer.util.media.AlbumArtModelLoader
import com.musicplayer.util.ui.BottomCropDrawable

internal class PlaylistDetailPlayerController(
    private val activity: AppCompatActivity,
    private val binding: LayoutPlayerBottomSheetBinding,
    private val playerManager: PlayerManager
) {
    private val miniPlayerBinding = binding.miniPlayerContainer
    private val fullPlayerBinding = binding.fullPlayerContent
    private val bottomSheetBehavior = BottomSheetBehavior.from(binding.root)
    private val lyricsController = PlayerLyricsController(activity, fullPlayerBinding, playerManager)
    private lateinit var queueSectionBinder: QueueSectionBinder
    private lateinit var playerViewSwipeController: PlayerViewSwipeController

    private lateinit var rotateAnimator: ObjectAnimator
    private var lastIsPlayingState: Boolean? = null
    private var currentSongId: String = ""
    private var isDuringSongChange = false

    val isSeeking: Boolean
        get() = lyricsController.isSeeking

    fun setup() {
        setupBottomSheet()
        setupPlayerSections()
        setupPlayerControls()
        setupAlbumRotation()
    }

    fun expand() {
        if (playerManager.currentSong.value != null) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    fun collapse() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    fun isExpanded(): Boolean = bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED

    fun updateMiniPlayer(song: Song?) {
        if (song != null) {
            binding.root.visibility = View.VISIBLE
            miniPlayerBinding.root.visibility = View.VISIBLE
            miniPlayerBinding.tvSongTitle.text = song.title
            miniPlayerBinding.tvArtist.text = song.artist

            if (song.albumId > 0) {
                val albumArtUri = AlbumArtModelLoader.AlbumArtUri(song.albumId, song.path)
                Glide.with(activity)
                    .load(albumArtUri)
                    .placeholder(R.drawable.ic_play)
                    .error(R.drawable.ic_play)
                    .into(miniPlayerBinding.ivAlbumCover)
            } else {
                miniPlayerBinding.ivAlbumCover.setImageResource(R.drawable.ic_play)
            }

            miniPlayerBinding.root.background = BottomCropDrawable(activity, R.drawable.background)
        } else {
            binding.root.visibility = View.GONE
            miniPlayerBinding.root.visibility = View.GONE
        }
    }

    fun updateCurrentSong(song: Song) {
        fullPlayerBinding.toolbarSongTitle.text = song.title
        fullPlayerBinding.toolbarArtistName.text = song.artist

        if (currentSongId != song.id) {
            currentSongId = song.id
            isDuringSongChange = true

            if (::rotateAnimator.isInitialized && (rotateAnimator.isRunning || rotateAnimator.isPaused)) {
                rotateAnimator.cancel()
            }

            val currentRotation = fullPlayerBinding.ivAlbumCover.rotation
            val reverseRotateAnimator = ObjectAnimator.ofFloat(
                fullPlayerBinding.ivAlbumCover,
                "rotation",
                currentRotation,
                0f
            ).apply {
                duration = 500
            }

            reverseRotateAnimator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    loadAlbumCoverImage(song)

                    val isCurrentlyPlaying = playerManager.isPlaying.value == true
                    if (isCurrentlyPlaying) {
                        rotateAnimator = createAlbumRotationAnimator()
                        rotateAnimator.start()
                    }

                    lastIsPlayingState = isCurrentlyPlaying
                    isDuringSongChange = false
                }
            })

            reverseRotateAnimator.start()
        }

        lyricsController.loadLyrics()
    }

    fun updatePlaybackState(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        miniPlayerBinding.btnPlayPause.setImageResource(icon)
        fullPlayerBinding.btnPlayPause.setImageResource(icon)
        updateAlbumCoverAnimation(isPlaying)
    }

    fun updateCurrentPosition(position: Long) {
        val duration = playerManager.duration.value ?: 0
        if (duration > 0) {
            fullPlayerBinding.seekBar.progress = (position * 100 / duration).toInt()
        }
        fullPlayerBinding.tvCurrentTime.text = formatTime(position)
        lyricsController.updateLyrics(position)
    }

    fun updateDuration(duration: Long) {
        fullPlayerBinding.tvTotalTime.text = formatTime(duration)
    }

    fun updatePlayMode(mode: PlayMode) {
        fullPlayerBinding.btnShuffle.setImageResource(mode.getIconResId())
    }

    fun release() {
        if (::rotateAnimator.isInitialized) {
            if (rotateAnimator.isRunning) {
                rotateAnimator.cancel()
            }
        }
        lyricsController.release()
        queueSectionBinder.release()
        playerViewSwipeController.release()
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior.peekHeight = activity.resources.getDimensionPixelSize(R.dimen.mini_player_height)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val miniAlpha = (1 - slideOffset * 4).coerceIn(0f, 1f)
                miniPlayerBinding.root.alpha = miniAlpha

                val fullAlpha = ((slideOffset - 0.25f) / 0.75f).coerceIn(0f, 1f)
                fullPlayerBinding.root.alpha = fullAlpha
                binding.btnCollapsePlayer.alpha = fullAlpha
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED,
                    BottomSheetBehavior.STATE_COLLAPSED -> playerManager.resetExpandPlayerSheet()

                    BottomSheetBehavior.STATE_HIDDEN -> bottomSheetBehavior.state =
                        BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        })

        binding.btnCollapsePlayer.setOnClickListener {
            collapse()
        }

        miniPlayerBinding.root.setOnClickListener {
            expand()
        }
    }

    private fun setupPlayerControls() {
        miniPlayerBinding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }
        miniPlayerBinding.btnPrevious.setOnClickListener {
            playerManager.skipToPrevious()
        }
        miniPlayerBinding.btnNext.setOnClickListener {
            playerManager.skipToNext()
        }
        miniPlayerBinding.tvSongTitle.isSelected = true
        miniPlayerBinding.tvSongTitle.requestFocus()

        fullPlayerBinding.btnPlayPause.setOnClickListener {
            togglePlayPause()
        }
        fullPlayerBinding.btnPrevious.setOnClickListener {
            playerManager.skipToPrevious()
        }
        fullPlayerBinding.btnNext.setOnClickListener {
            playerManager.skipToNext()
        }
        fullPlayerBinding.btnShuffle.setOnClickListener {
            playerManager.togglePlayMode()
        }
        fullPlayerBinding.btnShowLyrics.setOnClickListener {
            toggleLyricsView()
        }
        fullPlayerBinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = playerManager.duration.value ?: 0
                    val position = (duration * progress / 100f).toLong()
                    fullPlayerBinding.tvCurrentTime.text = formatTime(position)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                lyricsController.isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val duration = playerManager.duration.value ?: 0
                val progress = seekBar?.progress ?: 0
                val position = (duration * progress / 100f).toLong()
                playerManager.seekTo(position)

                fullPlayerBinding.seekBar.postDelayed({
                    lyricsController.updateLyrics(position)
                    lyricsController.isSeeking = false
                }, 50)
            }
        })
        playerViewSwipeController.bind()
    }

    private fun setupAlbumRotation() {
        rotateAnimator = createAlbumRotationAnimator()
    }

    private fun createAlbumRotationAnimator(): ObjectAnimator {
        return ObjectAnimator.ofFloat(fullPlayerBinding.ivAlbumCover, "rotation", 0f, 360f).apply {
            duration = 40000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    private fun togglePlayPause() {
        if (playerManager.isPlaying.value == true) {
            playerManager.pause()
        } else {
            playerManager.play()
        }
    }

    private fun toggleLyricsView() {
        playerViewSwipeController.showNextView()
    }

    private fun setupPlayerSections() {
        queueSectionBinder = QueueSectionBinder(fullPlayerBinding, playerManager, activity)
        playerViewSwipeController = PlayerViewSwipeController(fullPlayerBinding) {
            queueSectionBinder.scrollToCurrentSong()
        }
    }

    private fun loadAlbumCoverImage(song: Song) {
        if (song.albumId > 0) {
            val albumArtUri = AlbumArtModelLoader.AlbumArtUri(song.albumId, song.path)
            Glide.with(activity)
                .load(albumArtUri)
                .placeholder(R.drawable.ic_play)
                .error(R.drawable.ic_play)
                .into(fullPlayerBinding.ivAlbumCover)
        } else {
            fullPlayerBinding.ivAlbumCover.setImageResource(R.drawable.ic_play)
        }
    }

    private fun updateAlbumCoverAnimation(isPlaying: Boolean) {
        if (isDuringSongChange || !::rotateAnimator.isInitialized) {
            return
        }

        if (lastIsPlayingState == isPlaying) {
            return
        }
        lastIsPlayingState = isPlaying

        if (isPlaying) {
            if (rotateAnimator.isPaused) {
                rotateAnimator.resume()
            } else if (!rotateAnimator.isRunning) {
                rotateAnimator.start()
            }
        } else if (rotateAnimator.isRunning) {
            rotateAnimator.pause()
        }
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }
}
