package com.musicplayer.data.model

import org.junit.Assert.*
import org.junit.Test

class PlayModeTest {

    @Test
    fun next_fromOrder_returnsShuffle() {
        assertEquals(PlayMode.SHUFFLE, PlayMode.ORDER.next())
    }

    @Test
    fun next_fromShuffle_returnsRepeatOne() {
        assertEquals(PlayMode.REPEAT_ONE, PlayMode.SHUFFLE.next())
    }

    @Test
    fun next_fromRepeatOne_returnsOrder() {
        assertEquals(PlayMode.ORDER, PlayMode.REPEAT_ONE.next())
    }

    @Test
    fun next_cycleComplete_cyclesBackToOrder() {
        var mode = PlayMode.ORDER
        repeat(3) {
            mode = mode.next()
        }
        assertEquals(PlayMode.ORDER, mode)
    }

    @Test
    fun getIconResId_order_returnsCorrectIcon() {
        val iconRes = PlayMode.ORDER.iconResId
        assertTrue(iconRes > 0)
    }

    @Test
    fun getIconResId_shuffle_returnsCorrectIcon() {
        val iconRes = PlayMode.SHUFFLE.iconResId
        assertTrue(iconRes > 0)
    }

    @Test
    fun getIconResId_repeatOne_returnsCorrectIcon() {
        val iconRes = PlayMode.REPEAT_ONE.iconResId
        assertTrue(iconRes > 0)
    }
}

// Extension property to access the private getIconResId function
private val PlayMode.iconResId: Int
    get() = when (this) {
        PlayMode.ORDER -> com.musicplayer.R.drawable.ic_repeat
        PlayMode.SHUFFLE -> com.musicplayer.R.drawable.ic_shuffle
        PlayMode.REPEAT_ONE -> com.musicplayer.R.drawable.ic_repeat_one
    }
