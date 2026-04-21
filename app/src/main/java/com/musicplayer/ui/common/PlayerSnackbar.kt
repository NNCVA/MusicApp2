package com.musicplayer.ui.common

import android.app.Activity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.musicplayer.R

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

private fun showPlayerSnackbarInternal(
    activity: Activity,
    fallbackView: View,
    message: String,
    duration: Int
) {
    val anchorView = activity.findViewById<View>(R.id.coordinator_layout) ?: fallbackView
    val snackbar = Snackbar.make(anchorView, message, duration)
    val miniPlayer = activity.findViewById<View>(R.id.mini_player_container)

    if (miniPlayer != null && miniPlayer.visibility == View.VISIBLE) {
        val snackbarView = snackbar.view
        val layoutParams = snackbarView.layoutParams as? CoordinatorLayout.LayoutParams

        if (layoutParams != null) {
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
