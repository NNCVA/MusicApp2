package com.musicplayer.ui.adapter

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.musicplayer.R

class SelectedFolderAdapter(
    private val context: Context,
    private var folderList: List<String>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<SelectedFolderAdapter.FolderViewHolder>() {

    fun updateFolders(newFolders: List<String>) {
        folderList = newFolders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folderUri = folderList[position]
        val folderName = getFriendlyFolderName(folderUri)
        
        holder.bind(folderName, folderUri)
    }

    override fun getItemCount(): Int = folderList.size

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val folderIcon: ImageView = itemView.findViewById(R.id.iv_folder_icon)
        private val folderName: TextView = itemView.findViewById(R.id.tv_folder_name)
        private val deleteButton: ImageView = itemView.findViewById(R.id.iv_delete_folder)

        fun bind(folderNameText: String, folderUri: String) {
            folderName.text = folderNameText
            deleteButton.setOnClickListener {
                onDeleteClick(folderUri)
            }
        }
    }

    private fun getFriendlyFolderName(uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            // 尝试提取有意义的文件夹名称
            val path = uri.path ?: uriString
            if (path.contains(":")) {
                // 格式类似于 /tree/primary:Music 或 /tree/external:Music
                val folderName = path.substringAfterLast(":")
                when (folderName) {
                    "Music" -> "音乐"
                    "Download" -> "下载"
                    "Documents" -> "文档"
                    "Pictures" -> "图片"
                    "Movies" -> "视频"
                    else -> folderName
                }
            } else {
                // 如果无法解析，至少去掉一些常见的前缀
                path.replace("/tree/", "")
            }
        } catch (e: Exception) {
            // 如果解析失败，返回原始字符串
            uriString
        }
    }
}