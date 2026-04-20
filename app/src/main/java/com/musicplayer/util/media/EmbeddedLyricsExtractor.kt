package com.musicplayer.util.media

import android.util.Log
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag as JaudiotaggerTag
import java.io.File

/**
 * 内嵌歌词提取工具类
 * 使用 Jaudiotagger 库从音频文件中提取内嵌歌词
 *
 * 支持的音频格式：
 * - MP3 (ID3v1, ID3v2)
 * - FLAC (Vorbis Comments)
 * - OGG (Vorbis Comments)
 * - M4A/MP4 (iTunes Metadata)
 * - WMA (ASF Metadata)
 * - WAV (ID3v2)
 */
object EmbeddedLyricsExtractor {

    private const val TAG = "EmbeddedLyricsExtractor"

    // 内嵌歌词缓存，避免重复提取
    private val lyricsCache = mutableMapOf<String, String?>()

    /**
     * 从音频文件中提取内嵌歌词
     *
     * @param songPath 歌曲文件路径
     * @return 歌词文本，如果未找到或提取失败返回null
     */
    fun extractEmbeddedLyrics(songPath: String): String? {
        // 生成缓存键
        val cacheKey = "embedded:$songPath"

        // 检查缓存，如果存在直接返回
        if (lyricsCache.containsKey(cacheKey)) {
            return lyricsCache[cacheKey]
        }

        // 缓存中不存在，提取歌词
        val result = try {
            val audioFile = AudioFileIO.read(File(songPath))
            val tag = audioFile.tagOrCreateAndSetDefault

            // 尝试多种方式提取歌词
            val lyrics = extractLyricsFromTag(tag, audioFile)

            if (!lyrics.isNullOrBlank()) {
                Log.d(TAG, "Found embedded lyrics in: ${File(songPath).name}")
                cleanupLyricsText(lyrics)
            } else {
                Log.d(TAG, "No embedded lyrics found in: ${File(songPath).name}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract embedded lyrics from: $songPath", e)
            null
        }

        // 将结果存入缓存
        lyricsCache[cacheKey] = result
        return result
    }

    /**
     * 从标签中提取歌词
     * 尝试多种可能的歌词字段
     *
     * @param tag 音频文件标签
     * @param audioFile 音频文件对象
     * @return 歌词文本，如果未找到返回null
     */
    private fun extractLyricsFromTag(tag: JaudiotaggerTag, audioFile: AudioFile): String? {
        // 方式1: 尝试标准 LYRICS 字段
        var lyrics = tag.getFirst(FieldKey.LYRICS)
        if (!lyrics.isNullOrBlank()) {
            Log.d(TAG, "Found lyrics in LYRICS field")
            return lyrics
        }

        // 方式2: 尝试其他可能的歌词字段
        val alternativeKeys = listOf(
            FieldKey.COMMENT to "COMMENT",
            FieldKey.TITLE to "TITLE"
        )

        for ((key, name) in alternativeKeys) {
            try {
                val content = tag.getFirst(key)
                if (!content.isNullOrBlank() && content.length > 50) {
                    // 如果内容较长且包含时间戳标记，可能是歌词
                    if (containsLyricsIndicators(content)) {
                        Log.d(TAG, "Found lyrics in $name field")
                        return content
                    }
                }
            } catch (e: Exception) {
                // 忽略不支持的标签
            }
        }

        // 方式3: 尝试特定格式的自定义标签
        // 根据文件类型尝试特定的元数据键
        val extension = audioFile.file.extension.lowercase()
        when (extension) {
            "mp3" -> {
                // ID3v2 特定标签
                lyrics = extractId3v2Lyrics(tag)
            }
            "flac", "ogg" -> {
                // Vorbis Comments
                lyrics = extractVorbisComments(tag)
            }
            "m4a", "mp4" -> {
                // iTunes Metadata
                lyrics = extractItunesMetadata(tag)
            }
        }

        return lyrics
    }

    /**
     * 提取 ID3v2 标签中的歌词
     */
    private fun extractId3v2Lyrics(tag: JaudiotaggerTag): String? {
        // ID3v2 中的非同步歌词（USLT）
        try {
            // Jaudiotagger 将 USLT 映射到 LYRICS 字段（已在方式1中尝试）
            // 这里尝试其他可能的 ID3v2 歌词标签
            val customFields = listOf(
                "LYRIC",      // 一些变体
                "LYRICS-JA",  // 日语歌词
                "LYRICS-EN"   // 英文歌词
            )

            for (fieldName in customFields) {
                try {
                    val field = tag.getFirstField(fieldName)
                    if (field != null && !field.toString().isBlank()) {
                        Log.d(TAG, "Found ID3v2 lyrics in $fieldName")
                        return field.toString()
                    }
                } catch (e: Exception) {
                    // 忽略不存在的字段
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to extract ID3v2 lyrics: ${e.message}")
        }

        return null
    }

    /**
     * 提取 Vorbis Comments（FLAC/OGG）
     */
    private fun extractVorbisComments(tag: JaudiotaggerTag): String? {
        val vorbisKeys = listOf(
            "LYRICS",
            "LYRIC",
            "UNSYNCEDLYRICS",
            "SYNCEDLYRICS"
        )

        for (key in vorbisKeys) {
            try {
                val field = tag.getFirstField(key)
                if (field != null && !field.toString().isBlank()) {
                    Log.d(TAG, "Found Vorbis comment lyrics in $key")
                    return field.toString()
                }
            } catch (e: Exception) {
                // 忽略不存在的字段
            }
        }

        return null
    }

    /**
     * 提取 iTunes 元数据（M4A/MP4）
     */
    private fun extractItunesMetadata(tag: JaudiotaggerTag): String? {
        // iTunes 使用 ©lyr 标签存储歌词
        try {
            val field = tag.getFirstField("©lyr")
            if (field != null && !field.toString().isBlank()) {
                Log.d(TAG, "Found iTunes lyrics in ©lyr")
                return field.toString()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to extract iTunes lyrics: ${e.message}")
        }

        return null
    }

    /**
     * 检测文本是否包含歌词特征
     *
     * @param text 文本内容
     * @return true表示可能是歌词，false表示不是
     */
    private fun containsLyricsIndicators(text: String): Boolean {
        // 检查是否包含时间戳标记（LRC格式）
        val hasTimestamp = Regex("\\[\\d{2}:\\d{2}").containsMatchIn(text)

        // 检查是否包含多行文本
        val hasMultipleLines = text.lines().size > 3

        // 检查是否包含常见的歌词词汇
        val hasLyricsKeywords = Regex(
            "(verse|chorus|bridge|intro|outro|副歌|主歌|过门|间奏)",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)

        return hasTimestamp || (hasMultipleLines && (hasLyricsKeywords || text.length > 100))
    }

    /**
     * 清理和格式化歌词文本
     * 处理编码问题和多余的空白字符
     *
     * @param text 原始歌词文本
     * @return 清理后的歌词文本
     */
    private fun cleanupLyricsText(text: String): String {
        // 移除BOM标记
        var cleaned = removeBOM(text)

        // 移除多余的空白行
        cleaned = cleaned.lines()
            .filter { it.trim().isNotEmpty() }
            .joinToString("\n")

        return cleaned.trim()
    }

    /**
     * 移除BOM（字节顺序标记）
     */
    private fun removeBOM(text: String): String {
        return when {
            text.startsWith("\uFEFF") -> text.substring(1)  // UTF-8 BOM
            text.startsWith("\uFFFE") -> text.substring(1)  // UTF-16 BE BOM
            text.startsWith("\u0000") -> {                 // UTF-16 LE BOM处理
                val bytes = text.toByteArray(Charsets.UTF_16LE)
                String(bytes, Charsets.UTF_8)
            }
            else -> text
        }
    }

    /**
     * 清空缓存
     * 在应用退出或内存不足时调用
     */
    fun clearCache() {
        lyricsCache.clear()
        Log.d(TAG, "Lyrics cache cleared")
    }

    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Int {
        return lyricsCache.size
    }

    /**
     * 检查是否包含内嵌歌词
     *
     * @param songPath 歌曲文件路径
     * @return true表示包含内嵌歌词，false表示不包含
     */
    fun hasEmbeddedLyrics(songPath: String): Boolean {
        return !extractEmbeddedLyrics(songPath).isNullOrBlank()
    }

    /**
     * 获取支持的音频格式列表
     *
     * @return 支持的文件扩展名列表
     */
    fun getSupportedFormats(): List<String> {
        return listOf("mp3", "flac", "ogg", "m4a", "mp4", "wma", "wav")
    }

    /**
     * 检查文件格式是否支持
     *
     * @param songPath 歌曲文件路径
     * @return true表示支持，false表示不支持
     */
    fun isFormatSupported(songPath: String): Boolean {
        val extension = File(songPath).extension.lowercase()
        return getSupportedFormats().contains(extension)
    }
}
