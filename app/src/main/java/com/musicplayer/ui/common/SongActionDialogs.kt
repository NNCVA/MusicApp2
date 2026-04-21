package com.musicplayer.ui.common

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.musicplayer.data.model.Playlist

fun showPlaylistSelectionDialog(
    context: Context,
    playlists: List<Playlist>,
    onCreateNewRequested: () -> Unit,
    onConfirmed: (List<Long>) -> Unit
) {
    if (playlists.isEmpty()) {
        AlertDialog.Builder(context)
            .setTitle("提示")
            .setMessage("您还没有创建任何歌单，请先创建歌单")
            .setPositiveButton("确定") { dialog, _ ->
                onCreateNewRequested()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
        return
    }

    val playlistNames = playlists.map { it.name }.toTypedArray()
    val selectedIndices = BooleanArray(playlists.size)

    AlertDialog.Builder(context)
        .setTitle("添加到歌单")
        .setMultiChoiceItems(playlistNames, selectedIndices) { _, which, isChecked ->
            selectedIndices[which] = isChecked
        }
        .setPositiveButton("确定") { dialog, _ ->
            val selectedPlaylistIds = buildList {
                selectedIndices.forEachIndexed { index, isSelected ->
                    if (isSelected) {
                        add(playlists[index].id)
                    }
                }
            }
            onConfirmed(selectedPlaylistIds)
            dialog.dismiss()
        }
        .setNegativeButton("取消", null)
        .show()
}

fun showCreatePlaylistNameDialog(
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

fun showDeleteSongsConfirmDialog(
    context: Context,
    songCount: Int,
    onConfirmed: () -> Unit
) {
    AlertDialog.Builder(context)
        .setTitle("删除歌曲")
        .setMessage("确定要删除选中的${songCount}首歌曲吗？")
        .setPositiveButton("删除") { dialog, _ ->
            onConfirmed()
            dialog.dismiss()
        }
        .setNegativeButton("取消", null)
        .show()
}
