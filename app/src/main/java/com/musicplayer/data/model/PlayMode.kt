package com.musicplayer.data.model

/**
 * 播放模式枚举
 */
enum class PlayMode {
    ORDER,      // 顺序播放
    SHUFFLE,    // 随机播放
    REPEAT_ONE; // 单曲循环
    
    /**
     * 获取下一个播放模式
     */
    fun next(): PlayMode {
        return when (this) {
            ORDER -> SHUFFLE
            SHUFFLE -> REPEAT_ONE
            REPEAT_ONE -> ORDER
        }
    }
    
    /**
     * 获取模式对应的图标资源ID
     */
    fun getIconResId(): Int {
        return when (this) {
            ORDER -> com.musicplayer.R.drawable.ic_repeat
            SHUFFLE -> com.musicplayer.R.drawable.ic_shuffle
            REPEAT_ONE -> com.musicplayer.R.drawable.ic_repeat_one
        }
    }
}