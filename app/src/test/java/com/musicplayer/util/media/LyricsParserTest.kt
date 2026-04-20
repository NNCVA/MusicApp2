package com.musicplayer.util.media

import org.junit.Assert.*
import org.junit.Test

class LyricsParserTest {

    @Test
    fun parseLrcContent_validLrc_returnsCorrectLyrics() {
        val content = """
            [00:00.00]标题: 测试歌曲
            [00:12.34]第一行歌词
            [00:15.67]第二行歌词
            [00:18.90]第三行歌词
        """.trimIndent()

        val lyrics = LyricsParser.parseLrcContent(content)

        assertEquals(3, lyrics.size)
        assertEquals("第一行歌词", lyrics[0].text)
        assertEquals(12340L, lyrics[0].time)
        assertEquals("第二行歌词", lyrics[1].text)
        assertEquals(15670L, lyrics[1].time)
    }

    @Test
    fun parseLrcContent_withSimpleTimeFormat_parsesCorrectly() {
        val content = """
            [03:45]第一行
            [04:10]第二行
        """.trimIndent()

        val lyrics = LyricsParser.parseLrcContent(content)

        assertEquals(2, lyrics.size)
        assertEquals(225000L, lyrics[0].time) // 3:45 = 3*60*1000 + 45*1000
        assertEquals(250000L, lyrics[1].time) // 4:10 = 4*60*1000 + 10*1000
    }

    @Test
    fun parseLrcContent_multipleTimestampsPerLine_createsMultipleEntries() {
        val content = """
            [00:10.00][00:20.00]重复歌词
        """.trimIndent()

        val lyrics = LyricsParser.parseLrcContent(content)

        assertEquals(2, lyrics.size)
        assertEquals("重复歌词", lyrics[0].text)
        assertEquals(10000L, lyrics[0].time)
        assertEquals(20000L, lyrics[1].time)
    }

    @Test
    fun parseLrcContent_withMetadata_skipsMetadata() {
        val content = """
            [ti:歌曲名]
            [ar:歌手名]
            [al:专辑名]
            [00:00.00]实际歌词
        """.trimIndent()

        val lyrics = LyricsParser.parseLrcContent(content)

        assertEquals(1, lyrics.size)
        assertEquals("实际歌词", lyrics[0].text)
    }

    @Test
    fun parseLrcContent_emptyContent_returnsEmptyList() {
        val lyrics = LyricsParser.parseLrcContent("")
        assertTrue(lyrics.isEmpty())
    }

    @Test
    fun findCurrentLineIndex_standardCase_returnsCorrectIndex() {
        val lyrics = listOf(
            LyricsParser.LyricsLine(0, "第一行"),
            LyricsParser.LyricsLine(10000, "第二行"),
            LyricsParser.LyricsLine(20000, "第三行"),
            LyricsParser.LyricsLine(30000, "第四行")
        )

        assertEquals(0, LyricsParser.findCurrentLineIndex(lyrics, 5000))
        assertEquals(1, LyricsParser.findCurrentLineIndex(lyrics, 15000))
        assertEquals(2, LyricsParser.findCurrentLineIndex(lyrics, 25000))
        assertEquals(3, LyricsParser.findCurrentLineIndex(lyrics, 35000))
    }

    @Test
    fun findCurrentLineIndex_emptyList_returnsNegativeOne() {
        val lyrics = emptyList<LyricsParser.LyricsLine>()
        assertEquals(-1, LyricsParser.findCurrentLineIndex(lyrics, 10000))
    }

    @Test
    fun findCurrentLineIndex_beforeFirstLine_returnsZero() {
        val lyrics = listOf(
            LyricsParser.LyricsLine(10000, "第一行"),
            LyricsParser.LyricsLine(20000, "第二行")
        )

        assertEquals(0, LyricsParser.findCurrentLineIndex(lyrics, 5000))
    }

    @Test
    fun findCurrentLine_standardCase_returnsCorrectLine() {
        val lyrics = listOf(
            LyricsParser.LyricsLine(10000, "第一行"),
            LyricsParser.LyricsLine(20000, "第二行")
        )

        val result = LyricsParser.findCurrentLine(lyrics, 15000)

        assertNotNull(result)
        assertEquals("第一行", result!!.text)
    }

    @Test
    fun formatTime_standardCase_formatsCorrectly() {
        assertEquals("00:00.00", LyricsParser.formatTime(0))
        assertEquals("01:23.45", LyricsParser.formatTime(83450))
        assertEquals("10:00.00", LyricsParser.formatTime(600000))
    }

    @Test
    fun hasTimestamps_withTimestamps_returnsTrue() {
        assertTrue(LyricsParser.hasTimestamps("[00:12.34]歌词"))
        assertTrue(LyricsParser.hasTimestamps("[03:45]歌词"))
    }

    @Test
    fun hasTimestamps_withoutTimestamps_returnsFalse() {
        assertFalse(LyricsParser.hasTimestamps("这只是普通文本"))
        assertFalse(LyricsParser.hasTimestamps(""))
    }

    @Test
    fun getLyricsContext_standardCase_returnsCorrectContext() {
        val lyrics = listOf(
            LyricsParser.LyricsLine(0, "第一行"),
            LyricsParser.LyricsLine(10000, "第二行"),
            LyricsParser.LyricsLine(20000, "第三行"),
            LyricsParser.LyricsLine(30000, "第四行"),
            LyricsParser.LyricsLine(40000, "第五行")
        )

        val context = LyricsParser.getLyricsContext(lyrics, 20000, 1, 1)

        assertEquals(3, context.size)
        assertEquals("第二行", context[0].text)
        assertEquals("第三行", context[1].text)
        assertEquals("第四行", context[2].text)
    }

    @Test
    fun parseLrcContent_withSpecialCharacters_removesSeparators() {
        val content = """
            [00:00.00]第一段/第二段
            [00:10.00]第二行|第三行
        """.trimIndent()

        val lyrics = LyricsParser.parseLrcContent(content)

        assertEquals(2, lyrics.size)
        assertEquals("第一段第二段", lyrics[0].text)
        assertEquals("第二行第三行", lyrics[1].text)
    }
}
