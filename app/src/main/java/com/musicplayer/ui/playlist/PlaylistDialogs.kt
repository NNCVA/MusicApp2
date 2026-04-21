package com.musicplayer.ui.playlist

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.musicplayer.data.model.Playlist

internal fun showCreatePlaylistDialog(
    context: Context,
    onConfirmed: (String) -> Unit
) {
    val editText = EditText(context).apply {
        hint = "请输入歌单名称"
        setPadding(32, 16, 32, 16)
    }

    AlertDialog.Builder(context)
        .setTitle("新建歌单")
        .setView(editText)
        .setPositiveButton("确定") { dialog, _ ->
            val name = editText.text.toString().trim()
            if (name.isNotEmpty()) {
                onConfirmed(name)
            }
            dialog.dismiss()
        }
        .setNegativeButton("取消", null)
        .show()
}

internal fun showRenamePlaylistDialog(
    context: Context,
    playlist: Playlist,
    onConfirmed: (String) -> Unit
) {
    val editText = EditText(context).apply {
        setText(playlist.name)
        setSelection(playlist.name.length)
        setPadding(32, 16, 32, 16)
    }

    AlertDialog.Builder(context)
        .setTitle("重命名歌单")
        .setView(editText)
        .setPositiveButton("确定") { dialog, _ ->
            val newName = editText.text.toString().trim()
            if (newName.isNotEmpty()) {
                onConfirmed(newName)
            }
            dialog.dismiss()
        }
        .setNegativeButton("取消", null)
        .show()
}

internal fun showDeletePlaylistDialog(
    context: Context,
    playlist: Playlist,
    onConfirmed: () -> Unit
) {
    AlertDialog.Builder(context)
        .setTitle("删除歌单")
        .setMessage("确定要删除歌单\"${playlist.name}\"吗？")
        .setPositiveButton("删除") { dialog, _ ->
            onConfirmed()
            dialog.dismiss()
        }
        .setNegativeButton("取消", null)
        .show()
}
