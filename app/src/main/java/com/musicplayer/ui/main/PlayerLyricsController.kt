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

/**
 * 歌词控制器，负责歌词加载、播放位置同步和三行歌词渲染。
 *
 * 歌词加载采用多级回退链：外部 .lrc 文件 -> 内嵌带时间戳歌词 -> 内嵌纯文本歌词 -> "未找到"提示。
 * 位置同步使用 [LyricsParser.findCurrentLineIndex] 二分查找，通过 [isSeeking] 标志
 * 在用户拖动进度条时暂停同步，避免歌词闪烁。
 *
 * @param context 用于资源访问和字符串获取
 * @param binding 全屏播放器布局绑定，操作歌词相关 View
 * @param playerManager 播放服务代理，提供当前歌曲信息
 */
internal class PlayerLyricsController(
    private val context: Context,
    private val binding: ContentPlayerDetailBinding,
    private val playerManager: PlayerManager
) {

    // 解析后的带时间戳歌词行列表
    private var lyrics: List<LyricsParser.LyricsLine> = emptyList()
    // 当前高亮歌词行索引，-1 表示尚未定位
    private var currentLyricsIndex = -1
    /**
     * 用户是否正在拖动进度条。
     * 拖动期间 [updateLyrics] 不应更新高亮，避免进度跳动导致歌词闪烁。
     * 由外部 SeekBar 监听器的 onStartTrackingTouch/onStopTrackingTouch 控制。
     */
    var isSeeking: Boolean = false

    /**
     * 加载当前歌曲的歌词。
     *
     * 回退链：外部 .lrc 文件 -> 内嵌带时间戳歌词（可同步） -> 内嵌纯文本歌词（仅展示） -> "未找到"提示。
     */
    fun loadLyrics() {
        playerManager.currentSong.value?.let { song ->
            // 第一优先：查找同名 .lrc 外部文件
            val lyricsFile = findLyricsFile(song)

            if (lyricsFile != null && lyricsFile.exists()) {
                lyrics = LyricsParser.parseLrcFile(lyricsFile)
                updateLyricsList()
            } else {
                // 第二优先：提取音频文件内嵌歌词
                val embeddedLyrics = EmbeddedLyricsExtractor.extractEmbeddedLyrics(song.path)
                if (!embeddedLyrics.isNullOrBlank()) {
                    if (LyricsParser.hasTimestamps(embeddedLyrics)) {
                        // 内嵌歌词带时间戳，可用于同步高亮
                        lyrics = LyricsParser.parseEmbeddedLyrics(embeddedLyrics)
                        updateLyricsList()
                    } else {
                        // 内嵌歌词为纯文本，仅展示不支持同步
                        lyrics = emptyList()
                        binding.tvLyrics.text = embeddedLyrics
                        updateThreeLineLyrics()
                    }
                } else {
                    // 无任何歌词来源，显示"未找到"提示
                    lyrics = emptyList()
                    binding.tvLyrics.text = context.getString(R.string.lyrics_not_found)
                    updateThreeLineLyrics()
                }
            }
        }
    }

    /**
     * 根据当前播放位置更新歌词高亮和三行歌词显示。
     * 通过二分查找定位当前行，仅在行索引变化时刷新 UI。
     *
     * @param currentPosition 当前播放位置（毫秒）
     */
    fun updateLyrics(currentPosition: Long) {
        if (lyrics.isEmpty()) return

        // 二分查找：在已排序的时间戳列表中定位当前播放位置对应的歌词行
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

    /**
     * 查找歌曲对应的 .lrc 歌词文件。
     * 查找顺序：同路径替换扩展名 -> 同目录大小写变体 -> 父目录按歌名查找。
     */
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

    /**
     * 更新三行歌词显示：上一行、当前行、下一行。
     * 无歌词时清空三个 TextView；边界情况下对应位置显示空字符串。
     */
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

    /**
     * 更新完整歌词列表的高亮样式。
     * 当前行设为黑色 + 1.1 倍字号，其余行为次要颜色。
     */
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

    /** 将歌词滚动视图平滑滚动到当前高亮行，使其居中显示。 */
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
