package com.musicplayer.util.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.musicplayer.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 音乐文件扫描器
 * 负责扫描设备中的音频文件
 */
object MusicScanner {
    
    // 支持的音频格式
    private val SUPPORTED_FORMATS = setOf(
        "mp3", "m4a", "ogg", "wav", "flac", "aac", "wma"
    )
    
    /**
     * 扫描指定文件夹中的音乐文件
     * 使用MediaStore API,支持通过URI或路径扫描
     */
    suspend fun scanFolder(context: Context, folderPath: String): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()

        // 判断folderPath是URI还是文件路径
        if (folderPath.startsWith("content://")) {
            // 使用MediaStore扫描,通过路径过滤
            scanFolderWithMediaStore(context, folderPath, songs)
        } else {
            // 使用File API直接扫描
            val folder = File(folderPath)
            if (folder.exists() && folder.isDirectory) {
                scanFilesRecursive(folder, songs)
            }
        }

        songs
    }

    /**
     * 使用MediaStore扫描指定文件夹
     * 从Document URI中提取路径,然后过滤MediaStore结果
     */
    private suspend fun scanFolderWithMediaStore(
        context: Context,
        folderUri: String,
        songs: MutableList<Song>
    ) = withContext(Dispatchers.IO) {
        try {
            // 从Document URI提取实际路径
            val folderPath = extractPathFromDocumentUri(folderUri)

            if (folderPath != null) {
                val contentResolver = context.contentResolver

                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DATE_MODIFIED,
                    MediaStore.Audio.Media.MIME_TYPE
                )

                // 使用LIKE查询匹配文件夹路径
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?"
                val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    arrayOf("$folderPath%"),
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn).toString()
                        val title = cursor.getString(titleColumn) ?: "Unknown"
                        val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                        val album = cursor.getString(albumColumn) ?: "Unknown Album"
                        val duration = cursor.getLong(durationColumn)
                        val path = cursor.getString(dataColumn) ?: continue
                        val albumId = cursor.getLong(albumIdColumn)
                        val dateAdded = cursor.getLong(dateAddedColumn) * 1000
                        val dateModified = cursor.getLong(dateModifiedColumn) * 1000

                        // 再次确认文件路径匹配
                        if (!path.startsWith(folderPath)) continue

                        // 检查文件是否存在
                        if (!File(path).exists()) continue

                        // 检查文件格式
                        if (!isSupportedFormat(path)) continue

                        val song = Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            path = path,
                            albumId = albumId,
                            dateAdded = dateAdded,
                            dateModified = dateModified
                        )

                        songs.add(song)
                    }
                }
            } else {
                android.util.Log.e("MusicScanner", "Failed to extract path from URI: $folderUri")
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicScanner", "Error scanning folder with MediaStore", e)
        }
    }

    /**
     * 从Document URI提取文件路径
     */
    private fun extractPathFromDocumentUri(uriString: String): String? {
        return try {
            val uri = android.net.Uri.parse(uriString)
            val path = uri.path ?: return null

            when {
                // 处理 /tree/primary:Music 或 /tree/primary%3AMusic
                path.startsWith("/tree/primary:") -> {
                    val relativePath = path.substringAfter("/tree/primary:")
                        .replace(":", "/")
                        .replace("%3A", "/")
                        .replace("%2F", "/")
                    android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
                }
                // 处理 /tree/external:Music
                path.startsWith("/tree/external:") -> {
                    val relativePath = path.substringAfter("/tree/external:")
                        .replace(":", "/")
                        .replace("%3A", "/")
                        .replace("%2F", "/")
                    android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
                }
                else -> null
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicScanner", "Error extracting path from URI: $uriString", e)
            null
        }
    }

    /**
     * 扫描指定文件夹中的音乐文件 (已弃用,保留用于兼容非URI路径)
     */
    suspend fun scanFolderLegacy(context: Context, folderPath: String): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val folder = File(folderPath)

        if (folder.exists() && folder.isDirectory) {
            scanFilesRecursive(folder, songs)
        }

        songs
    }
    
    /**
     * 递归扫描文件
     */
    private fun scanFilesRecursive(folder: File, songs: MutableList<Song>) {
        folder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                scanFilesRecursive(file, songs)
            } else if (isAudioFile(file)) {
                val song = createSongFromFile(file)
                if (song != null) {
                    songs.add(song)
                }
            }
        }
    }
    
    /**
     * 使用MediaStore扫描所有音乐
     */
    suspend fun scanAllMusic(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val contentResolver = context.contentResolver
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE
        )
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn).toString()
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)
                val path = cursor.getString(dataColumn) ?: continue
                val albumId = cursor.getLong(albumIdColumn)
                val dateAdded = cursor.getLong(dateAddedColumn) * 1000
                val dateModified = cursor.getLong(dateModifiedColumn) * 1000
                
                // 检查文件是否存在
                if (!File(path).exists()) continue
                
                // 检查文件格式
                if (!isSupportedFormat(path)) continue
                
                val song = Song(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    path = path,
                    albumId = albumId,
                    dateAdded = dateAdded,
                    dateModified = dateModified
                )
                
                songs.add(song)
            }
        }
        
        songs
    }
    
    /**
     * 从文件创建Song对象
     */
    private fun createSongFromFile(file: File): Song? {
        if (!file.exists() || !isSupportedFormat(file.name)) {
            return null
        }
        
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            
            val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "Unknown Artist"
            val album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "Unknown Album"
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0
            
            retriever.release()
            
            Song(
                id = file.absolutePath.hashCode().toString(),
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                path = file.absolutePath,
                albumId = 0,
                dateAdded = file.lastModified(),
                dateModified = file.lastModified()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 检查是否为音频文件
     */
    private fun isAudioFile(file: File): Boolean {
        if (!file.isFile) return false
        
        val extension = file.extension.lowercase()
        return SUPPORTED_FORMATS.contains(extension)
    }
    
    /**
     * 检查是否为支持的格式
     */
    private fun isSupportedFormat(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return SUPPORTED_FORMATS.contains(extension)
    }
    
    /**
     * 获取专辑封面URI
     */
    fun getAlbumArtUri(albumId: Long): Uri {
        return ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
    }
    
    /**
     * 获取音频文件的Content URI
     */
    fun getSongUri(songId: Long): Uri {
        return ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            songId
        )
    }
}