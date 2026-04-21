package com.musicplayer.ui.main

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.musicplayer.R
import com.musicplayer.data.model.Song
import com.musicplayer.databinding.ActivityContainerBinding
import com.musicplayer.databinding.ContentPlayerDetailBinding
import com.musicplayer.databinding.LayoutPlayerBottomSheetBinding
import com.musicplayer.databinding.MiniPlayerBinding
import com.musicplayer.service.PlayerManager
import com.musicplayer.util.media.AlbumArtModelLoader
import com.musicplayer.util.ui.BottomCropDrawable

/**
 * 容器 Activity，作为应用的主入口。
 */
class ContainerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContainerBinding
    private lateinit var playerBottomSheetBinding: LayoutPlayerBottomSheetBinding
    private lateinit var miniPlayerBinding: MiniPlayerBinding
    private lateinit var fullPlayerBinding: ContentPlayerDetailBinding

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val playerManager = PlayerManager.getInstance()

    private lateinit var lyricsController: PlayerLyricsController
    private lateinit var queueSectionBinder: QueueSectionBinder
    private lateinit var playerViewSwipeController: PlayerViewSwipeController

    private lateinit var rotateAnimator: ObjectAnimator
    private var shouldAnimate = false
    private var lastIsPlayingState: Boolean? = null
    private var currentSongId: String = ""
    private var isDuringSongChange = false
    private var lastUpdatedSongId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playerManager.initialize(this)

        setupBottomSheet()
        setupNavigation()
        setupPlayerSections()
        setupPlayerControls()
        setupAlbumRotation()
        observePlayerState()

        updateMiniPlayer(playerManager.currentSong.value)
        updatePlayPauseButton(playerManager.isPlaying.value ?: false)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.musicplayer.ACTION_EXPAND_PLAYER" &&
            playerManager.currentSong.value != null
        ) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun setupBottomSheet() {
        playerBottomSheetBinding = binding.playerBottomSheet
        miniPlayerBinding = playerBottomSheetBinding.miniPlayerContainer
        fullPlayerBinding = playerBottomSheetBinding.fullPlayerContent

        bottomSheetBehavior = BottomSheetBehavior.from(playerBottomSheetBinding.root)
        bottomSheetBehavior.peekHeight = resources.getDimensionPixelSize(R.dimen.mini_player_height)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val miniAlpha = (1 - slideOffset * 4).coerceIn(0f, 1f)
                miniPlayerBinding.root.alpha = miniAlpha

                val fullAlpha = ((slideOffset - 0.25f) / 0.75f).coerceIn(0f, 1f)
                fullPlayerBinding.root.alpha = fullAlpha
                playerBottomSheetBinding.btnCollapsePlayer.alpha = fullAlpha
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        playerManager.resetExpandPlayerSheet()
                        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    }

                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        playerManager.resetExpandPlayerSheet()
                        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    }

                    BottomSheetBehavior.STATE_HIDDEN -> {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    }
                }
            }
        })

        playerBottomSheetBinding.btnCollapsePlayer.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        miniPlayerBinding.root.setOnClickListener {
            if (playerManager.currentSong.value != null) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = null

        appBarConfiguration = AppBarConfiguration.Builder(
            R.id.nav_songs,
            R.id.nav_playlists,
            R.id.nav_recent,
            R.id.nav_scan
        ).setOpenableLayout(binding.drawerLayout).build()

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)
        NavigationUI.setupWithNavController(binding.navView, navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            supportActionBar?.title = ""

            val titleResId = when (destination.id) {
                R.id.nav_songs -> R.string.title_songs
                R.id.nav_playlists -> R.string.title_playlists
                R.id.nav_recent -> R.string.title_recent_play
                R.id.nav_scan -> R.string.title_scan_music
                else -> R.string.app_name
            }
            binding.toolbarTitle.text = getString(titleResId)
        }
    }

    private fun setupPlayerSections() {
        lyricsController = PlayerLyricsController(this, fullPlayerBinding, playerManager)
        queueSectionBinder = QueueSectionBinder(fullPlayerBinding, playerManager, this)
        playerViewSwipeController = PlayerViewSwipeController(fullPlayerBinding) {
            queueSectionBinder.scrollToCurrentSong()
        }
    }

    private fun setupPlayerControls() {
        setupMiniPlayerControls()
        setupFullPlayerControls()
        playerViewSwipeController.bind()
    }

    private fun setupAlbumRotation() {
        rotateAnimator = ObjectAnimator.ofFloat(fullPlayerBinding.ivAlbumCover, "rotation", 0f, 360f).apply {
            duration = 40000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    private fun updateAlbumCoverAnimation(isPlaying: Boolean) {
        if (isDuringSongChange) return

        shouldAnimate = isPlaying

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

    private fun setupMiniPlayerControls() {
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
    }

    private fun setupFullPlayerControls() {
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

        fullPlayerBinding.toolbarSongTitle.isSelected = true
        fullPlayerBinding.toolbarSongTitle.requestFocus()

        fullPlayerBinding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: android.widget.SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    val duration = playerManager.duration.value ?: 0
                    val position = (duration * progress / 100f).toLong()
                    fullPlayerBinding.tvCurrentTime.text = formatTime(position)
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                lyricsController.isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
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

    private fun updateMiniPlayer(song: Song?) {
        if (song != null) {
            playerBottomSheetBinding.root.visibility = View.VISIBLE
            miniPlayerBinding.root.visibility = View.VISIBLE

            if (lastUpdatedSongId != song.id) {
                lastUpdatedSongId = song.id

                miniPlayerBinding.tvSongTitle.text = song.title
                miniPlayerBinding.tvSongTitle.isSelected = true
                miniPlayerBinding.tvArtist.text = song.artist

                if (song.albumId > 0) {
                    val albumArtUri = AlbumArtModelLoader.AlbumArtUri(song.albumId, song.path)
                    Glide.with(this)
                        .load(albumArtUri)
                        .placeholder(R.drawable.ic_play)
                        .error(R.drawable.ic_play)
                        .into(miniPlayerBinding.ivAlbumCover)
                } else {
                    miniPlayerBinding.ivAlbumCover.setImageResource(R.drawable.ic_play)
                }

                val bottomCropDrawable = BottomCropDrawable(this, R.drawable.background)
                miniPlayerBinding.root.background = bottomCropDrawable
            }
        } else {
            lastUpdatedSongId = null
            playerBottomSheetBinding.root.visibility = View.GONE
            miniPlayerBinding.root.visibility = View.GONE
        }
    }

    private fun updateFullPlayerSongInfo(song: Song) {
        fullPlayerBinding.toolbarSongTitle.text = song.title
        fullPlayerBinding.toolbarSongTitle.isSelected = true
        fullPlayerBinding.toolbarSongTitle.requestFocus()
        fullPlayerBinding.toolbarArtistName.text = song.artist

        if (currentSongId != song.id) {
            currentSongId = song.id
            isDuringSongChange = true

            if (rotateAnimator.isRunning || rotateAnimator.isPaused) {
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

            reverseRotateAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    loadAlbumCoverImage(song)

                    val isCurrentlyPlaying = playerManager.isPlaying.value == true
                    if (isCurrentlyPlaying) {
                        rotateAnimator = ObjectAnimator.ofFloat(
                            fullPlayerBinding.ivAlbumCover,
                            "rotation",
                            0f,
                            360f
                        ).apply {
                            duration = 40000
                            repeatCount = ValueAnimator.INFINITE
                            interpolator = LinearInterpolator()
                        }
                        rotateAnimator.start()
                    }

                    shouldAnimate = isCurrentlyPlaying
                    lastIsPlayingState = isCurrentlyPlaying
                    isDuringSongChange = false
                }
            })

            reverseRotateAnimator.start()
        }

        lyricsController.loadLyrics()
    }

    private fun loadAlbumCoverImage(song: Song) {
        if (song.albumId > 0) {
            val albumArtUri = AlbumArtModelLoader.AlbumArtUri(song.albumId, song.path)
            Glide.with(this)
                .load(albumArtUri)
                .placeholder(R.drawable.ic_play)
                .error(R.drawable.ic_play)
                .into(fullPlayerBinding.ivAlbumCover)
        } else {
            fullPlayerBinding.ivAlbumCover.setImageResource(R.drawable.ic_play)
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        miniPlayerBinding.btnPlayPause.setImageResource(icon)
        fullPlayerBinding.btnPlayPause.setImageResource(icon)
    }

    private fun updateProgress(position: Long) {
        val duration = playerManager.duration.value ?: 0
        if (duration > 0) {
            val progress = (position * 100 / duration).toInt()
            fullPlayerBinding.seekBar.progress = progress
        }
        fullPlayerBinding.tvCurrentTime.text = formatTime(position)
    }

    private fun updateDuration(duration: Long) {
        fullPlayerBinding.tvTotalTime.text = formatTime(duration)
    }

    private fun updatePlayModeButton(mode: com.musicplayer.data.model.PlayMode) {
        fullPlayerBinding.btnShuffle.setImageResource(mode.getIconResId())
    }

    private fun observePlayerState() {
        playerManager.currentSong.observe(this, Observer { song ->
            updateMiniPlayer(song)
            if (song != null) {
                updateFullPlayerSongInfo(song)
            }
        })

        playerManager.isPlaying.observe(this, Observer {
            updatePlayPauseButton(it)
            updateAlbumCoverAnimation(it)
        })

        playerManager.currentPosition.observe(this, Observer { position ->
            if (!lyricsController.isSeeking) {
                updateProgress(position)
                lyricsController.updateLyrics(position)
            }
        })

        playerManager.duration.observe(this, Observer { duration ->
            updateDuration(duration)
        })

        playerManager.playMode.observe(this, Observer { mode ->
            updatePlayModeButton(mode)
        })

        playerManager.expandPlayerSheet.observe(this, Observer { shouldExpand ->
            if (shouldExpand && playerManager.currentSong.value != null) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                playerManager.resetExpandPlayerSheet()
            }
        })

        playerManager.isSwitching.observe(this, Observer { isSwitching ->
            if (isSwitching) {
                startMiniPlayerTransitionAnimation()
            } else {
                endMiniPlayerTransitionAnimation()
            }
        })
    }

    private fun startMiniPlayerTransitionAnimation() {
        miniPlayerBinding.ivAlbumCover.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .alpha(0.7f)
            .setDuration(250)
            .start()

        miniPlayerBinding.tvSongTitle.alpha = 0.5f
        miniPlayerBinding.tvArtist.alpha = 0.5f
    }

    private fun endMiniPlayerTransitionAnimation() {
        miniPlayerBinding.ivAlbumCover.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(250)
            .start()

        miniPlayerBinding.tvSongTitle.alpha = 1f
        miniPlayerBinding.tvArtist.alpha = 1f
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
    }

    override fun onBackPressed() {
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            return
        }

        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return
        }

        super.onBackPressed()
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::rotateAnimator.isInitialized && rotateAnimator.isRunning) {
            rotateAnimator.cancel()
        }
    }
}
