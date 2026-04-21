package com.musicplayer.ui.main

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import com.musicplayer.R
import com.musicplayer.data.model.Song
import com.musicplayer.databinding.ContentPlayerDetailBinding
import com.musicplayer.service.PlayerManager
import com.musicplayer.util.media.EmbeddedLyricsExtractor
import com.musicplayer.util.media.LyricsParser
import java.io.File

internal class PlayerLyricsController(
    private val context: Context,
    private val binding: ContentPlayerDetailBinding,
    private val playerManager: PlayerManager
) {

    private var lyrics: List<LyricsParser.LyricsLine> = emptyList()
    private var currentLyricsIndex = -1
    var isSeeking: Boolean = false

    fun loadLyrics() {
        playerManager.currentSong.value?.let { song ->
            val lyricsFile = findLyricsFile(song)

            if (lyricsFile != null && lyricsFile.exists()) {
                lyrics = LyricsParser.parseLrcFile(lyricsFile)
                updateLyricsList()
            } else {
                val embeddedLyrics = EmbeddedLyricsExtractor.extractEmbeddedLyrics(song.path)
                if (!embeddedLyrics.isNullOrBlank()) {
                    if (LyricsParser.hasTimestamps(embeddedLyrics)) {
                        lyrics = LyricsParser.parseEmbeddedLyrics(embeddedLyrics)
                        updateLyricsList()
                    } else {
                        lyrics = emptyList()
                        binding.tvLyrics.text = embeddedLyrics
                        updateThreeLineLyrics()
                    }
                } else {
                    lyrics = emptyList()
                    binding.tvLyrics.text = context.getString(R.string.lyrics_not_found)
                    updateThreeLineLyrics()
                }
            }
        }
    }

    fun updateLyrics(currentPosition: Long) {
        if (lyrics.isEmpty()) return

        val index = LyricsParser.findCurrentLineIndex(lyrics, currentPosition)
        if (index != currentLyricsIndex) {
            currentLyricsIndex = index
            updateLyricsHighlight()
            updateThreeLineLyrics()
            if (index >= 0) {
                scrollToCurrentLyrics()
            }
        }
    }

    private fun findLyricsFile(song: Song): File? {
        val lrcFilePath = song.path.replaceAfterLast('.', "lrc")
        var lyricsFile = File(lrcFilePath)
        if (lyricsFile.exists()) return lyricsFile

        val possibleExtensions = arrayOf(".lrc", ".LRC")
        for (ext in possibleExtensions) {
            lyricsFile = File(song.path.substringBeforeLast('.') + ext)
            if (lyricsFile.exists()) return lyricsFile
        }

        val songTitle = song.title.replace("[\\/:*?\"<>|]", "")
        val parentDir = File(song.path).parentFile
        if (parentDir != null) {
            for (ext in possibleExtensions) {
                lyricsFile = File(parentDir, songTitle + ext)
                if (lyricsFile.exists()) return lyricsFile
            }
        }

        return null
    }

    private fun updateLyricsList() {
        if (lyrics.isEmpty()) {
            binding.tvLyrics.text = context.getString(R.string.lyrics_not_found)
            return
        }
        updateLyricsHighlight()
        updateThreeLineLyrics()
    }

    private fun updateThreeLineLyrics() {
        if (lyrics.isEmpty()) {
            binding.tvPreviousLyric.text = ""
            binding.tvCurrentLyric.text = ""
            binding.tvNextLyric.text = ""
            return
        }

        val currentIndex = currentLyricsIndex

        binding.tvPreviousLyric.text = if (currentIndex > 0) {
            lyrics[currentIndex - 1].text
        } else {
            ""
        }

        binding.tvCurrentLyric.text = if (currentIndex >= 0 && currentIndex < lyrics.size) {
            lyrics[currentIndex].text
        } else {
            ""
        }

        binding.tvNextLyric.text = if (currentIndex < lyrics.size - 1) {
            lyrics[currentIndex + 1].text
        } else {
            ""
        }
    }

    private fun updateLyricsHighlight() {
        if (lyrics.isEmpty()) {
            binding.tvLyrics.text = context.getString(R.string.lyrics_not_found)
            return
        }

        val allLyrics = lyrics.joinToString("\n") { it.text }
        val spannableString = SpannableStringBuilder(allLyrics)

        if (currentLyricsIndex >= 0 && currentLyricsIndex < lyrics.size) {
            var startIndex = 0
            for (i in 0 until currentLyricsIndex) {
                startIndex += lyrics[i].text.length + 1
            }

            val endIndex = startIndex + lyrics[currentLyricsIndex].text.length
            val highlightColor = context.resources.getColor(R.color.black, context.theme)
            val normalColor = context.resources.getColor(R.color.text_secondary, context.theme)

            spannableString.setSpan(
                ForegroundColorSpan(normalColor),
                0,
                allLyrics.length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )

            spannableString.setSpan(
                ForegroundColorSpan(highlightColor),
                startIndex,
                endIndex,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )

            spannableString.setSpan(
                RelativeSizeSpan(1.1f),
                startIndex,
                endIndex,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )

            binding.tvLyrics.text = spannableString
        } else {
            binding.tvLyrics.text = allLyrics
        }
    }

    private fun scrollToCurrentLyrics() {
        if (lyrics.isEmpty() || currentLyricsIndex < 0) return

        val lineHeight = binding.tvLyrics.lineHeight
        val scrollY = currentLyricsIndex * lineHeight - binding.lyricsView.height / 2 + lineHeight / 2

        binding.lyricsView.smoothScrollTo(0, scrollY)
    }

    fun release() {
        lyrics = emptyList()
        currentLyricsIndex = -1
    }
}
