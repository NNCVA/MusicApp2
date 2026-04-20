package com.musicplayer.util.system

import org.junit.Assert.*
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun formatFileSize_bytes_returnsCorrectFormat() {
        assertEquals("0 B", FormatUtils.formatFileSize(0))
    }

    @Test
    fun formatFileSize_negative_returnsZero() {
        assertEquals("0 B", FormatUtils.formatFileSize(-1))
    }

    @Test
    fun formatFileSize_bytesOnly_returnsBytes() {
        assertEquals("512.0 B", FormatUtils.formatFileSize(512))
    }

    @Test
    fun formatFileSize_kilobytes_returnsKB() {
        assertEquals("1.0 KB", FormatUtils.formatFileSize(1024))
        assertEquals("1.5 KB", FormatUtils.formatFileSize(1536))
    }

    @Test
    fun formatFileSize_megabytes_returnsMB() {
        assertEquals("1.0 MB", FormatUtils.formatFileSize(1048576))
        assertEquals("3.5 MB", FormatUtils.formatFileSize(3670016))
    }

    @Test
    fun formatFileSize_gigabytes_returnsGB() {
        assertEquals("1.0 GB", FormatUtils.formatFileSize(1073741824))
    }

    @Test
    fun formatDate_zeroTimestamp_returnsUnknown() {
        assertEquals("未知", FormatUtils.formatDate(0))
    }

    @Test
    fun formatDate_validTimestamp_returnsFormattedDate() {
        // 2024-01-01 00:00:00 in milliseconds
        val timestamp = 1704067200000L
        val result = FormatUtils.formatDate(timestamp)
        assertTrue(result.contains("2024"))
    }

    @Test
    fun formatDate_customFormat_usesProvidedFormat() {
        val timestamp = 1704067200000L
        val result = FormatUtils.formatDate(timestamp, "yyyy-MM-dd")
        assertEquals("2024-01-01", result)
    }

    @Test
    fun getFileExtension_standardPath_returnsExtension() {
        assertEquals("MP3", FormatUtils.getFileExtension("/music/song.mp3"))
        assertEquals("FLAC", FormatUtils.getFileExtension("/music/song.flac"))
    }

    @Test
    fun getFileExtension_noExtension_returnsUnknown() {
        assertEquals("未知", FormatUtils.getFileExtension("/music/noextension"))
    }

    @Test
    fun getFileExtension_emptyPath_returnsUnknown() {
        assertEquals("未知", FormatUtils.getFileExtension(""))
    }

    @Test
    fun getFileExtension_extensionCase_normalizesToUppercase() {
        assertEquals("MP3", FormatUtils.getFileExtension("/music/song.Mp3"))
        assertEquals("FLAC", FormatUtils.getFileExtension("/music/song.flaC"))
    }
}
