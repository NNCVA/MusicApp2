package com.musicplayer.util.media

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.regex.Pattern

/**
 * Parses standard LRC lyrics content and files.
 */
object LyricsParser {

    private const val TAG = "LyricsParser"

    private val TIME_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\]")
    private val SIMPLE_TIME_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\]")
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

    data class LyricsLine(
        val time: Long,
        val text: String
    )

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

    private fun isMetadataText(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return METADATA_PREFIXES.any { normalized.startsWith(it) }
    }

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

        val timeMatches = TIME_PATTERN.matcher(text)
        while (timeMatches.find()) {
            val minutes = timeMatches.group(1)?.toIntOrNull() ?: 0
            val seconds = timeMatches.group(2)?.toIntOrNull() ?: 0
            val milliseconds = timeMatches.group(3)?.toIntOrNull() ?: 0
            times.add(minutes * 60 * 1000L + seconds * 1000L + milliseconds * 10L)
        }

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

    fun findCurrentLineIndex(lyrics: List<LyricsLine>, currentTime: Long): Int {
        if (lyrics.isEmpty()) return -1
        if (currentTime < lyrics.first().time) return 0

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

    fun findCurrentLine(lyrics: List<LyricsLine>, currentTime: Long): LyricsLine? {
        val index = findCurrentLineIndex(lyrics, currentTime)
        return if (index >= 0 && index < lyrics.size) lyrics[index] else null
    }

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

    fun formatTime(milliseconds: Long): String {
        val minutes = (milliseconds / 60000).toInt()
        val seconds = ((milliseconds % 60000) / 1000).toInt()
        val ms = ((milliseconds % 1000) / 10).toInt()
        return String.format("%02d:%02d.%02d", minutes, seconds, ms)
    }

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

    fun hasTimestamps(content: String): Boolean {
        return TIME_PATTERN.matcher(content).find() ||
            SIMPLE_TIME_PATTERN.matcher(content).find()
    }
}
