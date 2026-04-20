package com.musicplayer.data.model

import org.junit.Assert.*
import org.junit.Test

class SongTest {

    private val testSong = Song(
        id = "1",
        title = "Test Song",
        artist = "Test Artist",
        album = "Test Album",
        duration = 185000, // 3:05
        path = "/storage/music/test_song.mp3",
        albumId = 100,
        dateAdded = 1700000000000,
        dateModified = 1700000000000
    )

    @Test
    fun getDurationString_standardCase_formatsCorrectly() {
        assertEquals("3:05", testSong.durationString)
    }

    @Test
    fun getDurationString_zeroDuration_returnsZero() {
        val zeroSong = testSong.copy(duration = 0)
        assertEquals("0:00", zeroSong.durationString)
    }

    @Test
    fun getDurationString_longDuration_formatsCorrectly() {
        val longSong = testSong.copy(duration = 3661000) // 1:01:01
        assertEquals("61:01", longSong.durationString)
    }

    @Test
    fun getFileName_standardCase_extractsFileName() {
        assertEquals("test_song.mp3", testSong.fileName)
    }

    @Test
    fun getFileName_pathWithoutSlash_returnsFullPath() {
        val song = testSong.copy(path = "no_slash.mp3")
        assertEquals("no_slash.mp3", song.fileName)
    }

    @Test
    fun empty_createsDefaultSong() {
        val emptySong = Song.empty()

        assertEquals("", emptySong.id)
        assertEquals("未知歌曲", emptySong.title)
        assertEquals("未知歌手", emptySong.artist)
        assertEquals("未知专辑", emptySong.album)
        assertEquals(0L, emptySong.duration)
        assertEquals("", emptySong.path)
        assertEquals(0L, emptySong.albumId)
    }

    @Test
    fun copy_preservesFields() {
        val copied = testSong.copy(id = "2")

        assertEquals("2", copied.id)
        assertEquals(testSong.title, copied.title)
        assertEquals(testSong.artist, copied.artist)
        assertEquals(testSong.album, copied.album)
        assertEquals(testSong.duration, copied.duration)
        assertEquals(testSong.path, copied.path)
    }
}

// Extension properties to access the private getDurationString and getFileName functions
private val Song.durationString: String
    get() {
        val seconds = duration / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

private val Song.fileName: String
    get() = path.substringAfterLast('/')
