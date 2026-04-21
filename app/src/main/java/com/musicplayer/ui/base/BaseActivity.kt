package com.musicplayer.ui.base

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.musicplayer.R
import com.musicplayer.data.model.Song
import com.musicplayer.databinding.MiniPlayerBinding
import com.musicplayer.service.PlayerManager
import com.musicplayer.ui.main.ContainerActivity
import com.musicplayer.util.media.AlbumArtModelLoader
import com.musicplayer.util.ui.BottomCropDrawable

/**
 * 基础Activity类，包含共享的迷你播放栏实现
 */
abstract class BaseActivity : AppCompatActivity() {

    private lateinit var miniPlayerBinding: MiniPlayerBinding
    protected val playerManager = PlayerManager.getInstance()
    private var isObserverRegistered = false
    private var lastUpdatedSongId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化播放器
        playerManager.initialize(this)
    }
    
    /**
     * 设置迷你播放栏的绑定
     */
    protected fun setupMiniPlayerBinding(binding: MiniPlayerBinding) {
        miniPlayerBinding = binding
        setupMiniPlayer()

        // 注册播放器状态观察（只注册一次）
        if (!isObserverRegistered) {
            observePlayerState()
            isObserverRegistered = true
        }

        // 更新初始状态
        updateMiniPlayer(playerManager.currentSong.value)
        updatePlayPauseButton(playerManager.isPlaying.value ?: false)
    }
    
    /**
     * 设置迷你播放栏的事件监听
     */
    private fun setupMiniPlayer() {
        // 播放/暂停按钮
        miniPlayerBinding.btnPlayPause.setOnClickListener {
            if (playerManager.isPlaying.value == true) {
                playerManager.pause()
            } else {
                playerManager.play()
            }
        }
        
        // 上一首按钮
        miniPlayerBinding.btnPrevious.setOnClickListener {
            playerManager.skipToPrevious()
        }
        
        // 下一首按钮
        miniPlayerBinding.btnNext.setOnClickListener {
            playerManager.skipToNext()
        }
        
        // 设置歌曲标题的选中状态以启用跑马灯效果
        miniPlayerBinding.tvSongTitle.isSelected = true
        
        // 强制请求焦点以确保跑马灯效果启动
        miniPlayerBinding.tvSongTitle.requestFocus();
        
        // 点击迷你播放器 - 请求展开Bottom Sheet并返回主界面
        miniPlayerBinding.root.setOnClickListener {
            playerManager.currentSong.value?.let { _ ->
                // 请求展开Bottom Sheet
                playerManager.requestExpandPlayerSheet()
                // 返回到ContainerActivity
                val intent = Intent(this, ContainerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            }
        }
    }
    
    /**
     * 更新迷你播放栏的显示
     */
    protected open fun updateMiniPlayer(song: Song?) {
        if (song != null) {
            miniPlayerBinding.root.visibility = View.VISIBLE

            // 只在歌曲真正变化时更新文本和跑马灯
            if (lastUpdatedSongId != song.id) {
                lastUpdatedSongId = song.id

                miniPlayerBinding.tvSongTitle.text = song.title
                miniPlayerBinding.tvSongTitle.isSelected = true
                miniPlayerBinding.tvArtist.text = song.artist

                // 加载专辑封面
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

                // 设置迷你播放栏背景
                val bottomCropDrawable = BottomCropDrawable(
                    this,  // 传入Context而不是Resources
                    R.drawable.background
                )
                miniPlayerBinding.root.background = bottomCropDrawable
            }
        } else {
            lastUpdatedSongId = null
            miniPlayerBinding.root.visibility = View.GONE
        }
    }
    
    /**
     * 更新播放/暂停按钮的状态
     */
    protected open fun updatePlayPauseButton(isPlaying: Boolean) {
        val icon = if (isPlaying) {
            R.drawable.ic_pause
        } else {
            R.drawable.ic_play
        }
        miniPlayerBinding.btnPlayPause.setImageResource(icon)
    }
    
    override fun onResume() {
        super.onResume()
        // 观察者已在 setupMiniPlayerBinding 中注册，无需重复注册
    }
    
    /**
     * 观察播放器状态
     */
    private fun observePlayerState() {
        playerManager.currentSong.observe(this) {
            updateMiniPlayer(it)
        }
        playerManager.isPlaying.observe(this) {
            updatePlayPauseButton(it)
        }
    }
}
