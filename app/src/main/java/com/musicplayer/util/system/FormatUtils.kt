package com.musicplayer.util.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 格式化工具类
 * 用于格式化文件大小、日期时间、文件扩展名等
 */
object FormatUtils {

    /**
     * 格式化文件大小
     * @param bytes 文件字节数
     * @return 格式化后的字符串（如 "3.5 MB"）
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()

        return String.format(
            Locale.getDefault(),
            "%.1f %s",
            bytes / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }

    /**
     * 格式化时间戳为日期字符串
     * @param timestamp 时间戳（毫秒）
     * @param format 输出格式（默认"yyyy-MM-dd HH:mm"）
     * @return 格式化后的日期字符串
     */
    fun formatDate(
        timestamp: Long,
        format: String = "yyyy-MM-dd HH:mm"
    ): String {
        if (timestamp == 0L) return "未知"

        return try {
            val sdf = SimpleDateFormat(format, Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "未知"
        }
    }

    /**
     * 从文件路径获取文件扩展名
     * @param path 文件路径
     * @return 扩展名（大写，如"MP3"）
     */
    fun getFileExtension(path: String): String {
        return path.substringAfterLast('.', "")
            .ifEmpty { "未知" }
            .uppercase(Locale.getDefault())
    }

    /**
     * 异步获取文件大小
     * @param path 文件路径
     * @return 格式化后的文件大小字符串
     */
    suspend fun getFileSizeAsync(path: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (file.exists()) {
                    formatFileSize(file.length())
                } else {
                    "未知"
                }
            } catch (e: Exception) {
                "未知"
            }
        }
    }
}
