package com.musicplayer.ui.playlist

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.musicplayer.data.model.Playlist

/**
 * 显示新建歌单对话框。
 * 包含一个输入框，用户输入非空名称后通过 [onConfirmed] 回调返回歌单名。
 *
 * @param context 上下文，用于创建对话框
 * @param onConfirmed 用户确认后的回调，参数为去掉首尾空格的歌单名称
 */
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

/**
 * 显示重命名歌单对话框。
 * 输入框预填充当前歌单名称并将光标置于末尾，方便用户直接修改。
 *
 * @param context 上下文，用于创建对话框
 * @param playlist 需要重命名的歌单对象
 * @param onConfirmed 用户确认后的回调，参数为去掉首尾空格的新名称
 */
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

/**
 * 显示删除歌单确认对话框。
 * 弹出二次确认提示，包含歌单名称，用户点击"删除"后执行 [onConfirmed] 回调。
 *
 * @param context 上下文，用于创建对话框
 * @param playlist 需要删除的歌单对象，用于在提示中显示歌单名称
 * @param onConfirmed 用户确认删除后的回调
 */
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
