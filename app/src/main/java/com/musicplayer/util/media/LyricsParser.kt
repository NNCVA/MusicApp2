package com.musicplayer.util.media

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.regex.Pattern

/**
 * LRC 歌词解析器。
 *
 * 支持标准 LRC 文件和内嵌歌词的解析，提供时间戳定位和上下文获取功能。
 * 时间格式支持 [mm:ss.xx]（精确到厘秒）和 [mm:ss]（秒级）两种。
 */
object LyricsParser {

    private const val TAG = "LyricsParser"

    // [mm:ss.xx] 格式，捕获分、秒、厘秒
    private val TIME_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\]")
    // [mm:ss] 格式，捕获分、秒
    private val SIMPLE_TIME_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\]")
    // 元数据行前缀，解析时跳过
    private val METADATA_PREFIXES = listOf(
        "title:",
        "artist:",
        "album:",
        "by:",
        "offset:",
        "\u6807\u9898:",
        "\u6b4c\u624b:",
        "\u4e13\u8f91:"
    )

    /**
     * 歌词行，包含时间戳（毫秒）和歌词文本。
     */
    data class LyricsLine(
        val time: Long,
        val text: String
    )

    /**
     * 清理歌词文本中的特殊符号和乱码。
     */
    private fun cleanLyricsText(text: String): String {
        return text
            .replace("/", "")
            .replace("|", "")
            .replace("\\", "")
            .replace("銆?", "")
            .replace("鈥?", "")
            .replace("芦", "")
            .replace("禄", "")
            .replace("搂", "")
            .replace("露", "")
            .trim()
    }

    /**
     * 判断文本是否为元数据行（标题、歌手、专辑等），解析时应跳过。
     */
    private fun isMetadataText(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return METADATA_PREFIXES.any { normalized.startsWith(it) }
    }

    /**
     * 解析 LRC 文件，返回按时间排序的歌词行列表。
     * 解析失败时返回空列表。
     */
    fun parseLrcFile(file: File): List<LyricsLine> {
        val lyrics = mutableListOf<LyricsLine>()

        try {
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { parseLine(it) }?.let { lyrics.addAll(it) }
                }
            }
            lyrics.sortBy { it.time }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse LRC file: ${file.path}", e)
        }

        return lyrics
    }

    /**
     * 解析 LRC 格式的歌词文本内容，返回按时间排序的歌词行列表。
     */
    fun parseLrcContent(content: String): List<LyricsLine> {
        val lyrics = mutableListOf<LyricsLine>()

        try {
            content.lines().forEach { line ->
                parseLine(line)?.let { lyrics.addAll(it) }
            }
            lyrics.sortBy { it.time }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse LRC content", e)
        }

        return lyrics
    }

    /**
     * 解析单行 LRC 歌词。
     * 一行可能包含多个时间标签（如 [01:00.00][02:00.00]副歌），返回多个 LyricsLine。
     * 空行、元数据行、无时间标签的行返回 null。
     */
    private fun parseLine(line: String): List<LyricsLine>? {
        if (
            line.isBlank() ||
            line.startsWith("[ti:") ||
            line.startsWith("[ar:") ||
            line.startsWith("[al:") ||
            line.startsWith("[by:") ||
            line.startsWith("[offset:")
        ) {
            return null
        }

        val lyrics = mutableListOf<LyricsLine>()
        var text = line
        val times = mutableListOf<Long>()

        // 优先匹配精确格式 [mm:ss.xx]
        val timeMatches = TIME_PATTERN.matcher(text)
        while (timeMatches.find()) {
            val minutes = timeMatches.group(1)?.toIntOrNull() ?: 0
            val seconds = timeMatches.group(2)?.toIntOrNull() ?: 0
            val milliseconds = timeMatches.group(3)?.toIntOrNull() ?: 0
            times.add(minutes * 60 * 1000L + seconds * 1000L + milliseconds * 10L)
        }

        // 精确格式未匹配到，回退到简单格式 [mm:ss]
        if (times.isEmpty()) {
            val simpleTimeMatches = SIMPLE_TIME_PATTERN.matcher(text)
            while (simpleTimeMatches.find()) {
                val minutes = simpleTimeMatches.group(1)?.toIntOrNull() ?: 0
                val seconds = simpleTimeMatches.group(2)?.toIntOrNull() ?: 0
                times.add(minutes * 60 * 1000L + seconds * 1000L)
            }
        }

        text = TIME_PATTERN.matcher(text).replaceAll("")
        text = SIMPLE_TIME_PATTERN.matcher(text).replaceAll("")
        text = cleanLyricsText(text)

        if (text.isEmpty() || isMetadataText(text)) {
            return null
        }

        times.forEach { time ->
            lyrics.add(LyricsLine(time, text))
        }

        return lyrics.ifEmpty { null }
    }

    /**
     * 二分查找当前时间对应的歌词行索引。
     * 返回不大于 currentTime 的最大时间戳所在行的索引；歌词为空返回 -1。
     */
    fun findCurrentLineIndex(lyrics: List<LyricsLine>, currentTime: Long): Int {
        if (lyrics.isEmpty()) return -1
        if (currentTime < lyrics.first().time) return 0

        // 二分查找：找到最后一个 time <= currentTime 的位置
        var left = 0
        var right = lyrics.size - 1
        var result = -1

        while (left <= right) {
            val mid = (left + right) / 2
            val line = lyrics[mid]

            when {
                currentTime >= line.time -> {
                    result = mid
                    left = mid + 1
                }
                else -> right = mid - 1
            }
        }

        return result
    }

    /**
     * 查找当前时间对应的歌词行，未找到返回 null。
     */
    fun findCurrentLine(lyrics: List<LyricsLine>, currentTime: Long): LyricsLine? {
        val index = findCurrentLineIndex(lyrics, currentTime)
        return if (index >= 0 && index < lyrics.size) lyrics[index] else null
    }

    /**
     * 获取当前歌词行及其前后若干行，用于三行歌词显示。
     * 默认返回当前行前 2 行和后 2 行（共 5 行）。
     */
    fun getLyricsContext(
        lyrics: List<LyricsLine>,
        currentTime: Long,
        linesBefore: Int = 2,
        linesAfter: Int = 2
    ): List<LyricsLine> {
        val currentIndex = findCurrentLineIndex(lyrics, currentTime)
        if (currentIndex == -1) return emptyList()

        val startIndex = maxOf(0, currentIndex - linesBefore)
        val endIndex = minOf(lyrics.size - 1, currentIndex + linesAfter)
        return lyrics.subList(startIndex, endIndex + 1)
    }

    /**
     * 将毫秒时间格式化为 LRC 时间格式 [mm:ss.xx]。
     */
    fun formatTime(milliseconds: Long): String {
        val minutes = (milliseconds / 60000).toInt()
        val seconds = ((milliseconds % 60000) / 1000).toInt()
        val ms = ((milliseconds % 1000) / 10).toInt()
        return String.format("%02d:%02d.%02d", minutes, seconds, ms)
    }

    /**
     * 查找歌曲对应的外部歌词文件。
     * 优先查找同名 .lrc 文件，其次尝试 .txt 和通用 lyrics.lrc。
     */
    fun findLyricsFile(songFile: File): File? {
        val songName = songFile.nameWithoutExtension
        val parentDir = songFile.parentFile

        val lrcFile = File(parentDir, "$songName.lrc")
        if (lrcFile.exists()) {
            return lrcFile
        }

        val possibleNames = listOf(
            "$songName.txt",
            "${songName}.lrc",
            "lyrics.lrc"
        )

        possibleNames.forEach { name ->
            val file = File(parentDir, name)
            if (file.exists()) {
                return file
            }
        }

        return null
    }

    /**
     * 解析内嵌歌词内容（来自音频文件标签），逻辑与 parseLrcContent 相同。
     */
    fun parseEmbeddedLyrics(content: String): List<LyricsLine> {
        val lyrics = mutableListOf<LyricsLine>()

        try {
            content.lines().forEach { line ->
                parseLine(line)?.let { lyrics.addAll(it) }
            }
            lyrics.sortBy { it.time }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse embedded lyrics content", e)
        }

        return lyrics
    }

    /**
     * 判断歌词内容是否包含时间戳，用于区分有时间戳的歌词和纯文本歌词。
     */
    fun hasTimestamps(content: String): Boolean {
        return TIME_PATTERN.matcher(content).find() ||
            SIMPLE_TIME_PATTERN.matcher(content).find()
    }
}
