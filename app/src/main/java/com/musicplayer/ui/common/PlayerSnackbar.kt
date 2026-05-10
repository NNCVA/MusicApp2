package com.musicplayer.ui.common

/**
 * 播放器 Snackbar 工具函数。
 * 在迷你播放栏上方显示 Snackbar，自动计算底部边距避免被迷你播放栏遮挡。
 * 提供 Fragment 和 AppCompatActivity 两个入口，内部统一走 [showPlayerSnackbarInternal] 处理。
 */

import android.app.Activity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.musicplayer.R

/**
 * Fragment 扩展函数，在迷你播放栏上方显示 Snackbar。
 * 使用 Fragment 的 Activity 查找锚点视图，Fragment 自身的 View 作为兜底锚点。
 *
 * @param message Snackbar 显示的文本内容
 * @param duration 显示时长，默认 [Snackbar.LENGTH_SHORT]
 */
fun Fragment.showPlayerSnackbar(
    message: String,
    duration: Int = Snackbar.LENGTH_SHORT
) {
    showPlayerSnackbarInternal(
        activity = requireActivity(),
        fallbackView = requireView(),
        message = message,
        duration = duration
    )
}

/**
 * AppCompatActivity 扩展函数，在迷你播放栏上方显示 Snackbar。
 * 调用方需提供一个兜底 View，当找不到 coordinator_layout 时用作锚点。
 *
 * @param message Snackbar 显示的文本内容
 * @param fallbackView 兜底锚点视图
 * @param duration 显示时长，默认 [Snackbar.LENGTH_SHORT]
 */
fun AppCompatActivity.showPlayerSnackbar(
    message: String,
    fallbackView: View,
    duration: Int = Snackbar.LENGTH_SHORT
) {
    showPlayerSnackbarInternal(
        activity = this,
        fallbackView = fallbackView,
        message = message,
        duration = duration
    )
}

/**
 * Snackbar 显示的内部实现。
 * 优先使用 [R.id.coordinator_layout] 作为锚点视图以获得 CoordinatorLayout 的滑动消除行为；
 * 找不到时降级到 [fallbackView]。当迷你播放栏可见时，通过 post 延迟计算其高度并设置为
 * Snackbar 的 bottomMargin，确保 Snackbar 显示在迷你播放栏正上方而不被遮挡。
 */
private fun showPlayerSnackbarInternal(
    activity: Activity,
    fallbackView: View,
    message: String,
    duration: Int
) {
    val anchorView = activity.findViewById<View>(R.id.coordinator_layout) ?: fallbackView
    val snackbar = Snackbar.make(anchorView, message, duration)
    val miniPlayer = activity.findViewById<View>(R.id.mini_player_container)

    // 迷你播放栏可见时，调整 Snackbar 底部边距使其不被遮挡
    if (miniPlayer != null && miniPlayer.visibility == View.VISIBLE) {
        val snackbarView = snackbar.view
        val layoutParams = snackbarView.layoutParams as? CoordinatorLayout.LayoutParams

        if (layoutParams != null) {
            // post 延迟到布局完成后获取迷你播放栏实际高度
            miniPlayer.post {
                val miniPlayerHeight = miniPlayer.height
                if (miniPlayerHeight > 0) {
                    layoutParams.bottomMargin = miniPlayerHeight
                    layoutParams.setMargins(
                        layoutParams.leftMargin,
                        layoutParams.topMargin,
                        layoutParams.rightMargin,
                        miniPlayerHeight
                    )
                    snackbarView.layoutParams = layoutParams
                }
            }
        }
    }

    snackbar.show()
}
