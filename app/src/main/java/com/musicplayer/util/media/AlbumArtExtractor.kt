package com.musicplayer.util.media

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * 专辑封面提取工具类
 * 通过直接读取文件而非Content URI来避免ETXTBSY错误
 */
object AlbumArtExtractor {
    
    // 专辑封面缓存，避免重复读取同一专辑封面
    private val albumArtCache = mutableMapOf<String, Bitmap?>()
    
    /**
     * 通过albumId获取专辑封面Bitmap
     * 直接查询MediaStore获取专辑封面文件路径，然后读取Bitmap
     *
     * @param contentResolver ContentResolver实例
     * @param albumId 专辑ID
     * @return 专辑封面Bitmap，如果获取失败返回null
     */
    fun getAlbumArtBitmap(contentResolver: ContentResolver, albumId: Long): Bitmap? {
        // 生成缓存键
        val cacheKey = "albumId:$albumId"
        
        // 检查缓存，如果存在直接返回
        if (albumArtCache.containsKey(cacheKey)) {
            return albumArtCache[cacheKey]
        }
        
        // 缓存中不存在，读取专辑封面
        val result = try {
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
            
            // 先尝试直接打开输入流
            contentResolver.openInputStream(albumArtUri)?.use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            // 优化异常处理，只在必要时打印日志
            // 专门处理ETXTBSY异常，避免打印完整堆栈跟踪
            if (e.message?.contains("ETXTBSY") == true) {
                // 文件忙异常，无需打印完整堆栈
                null
            } else {
                // 其他异常，只打印简单日志，不打印完整堆栈
                e.printStackTrace()
                null
            }
        }
        
        // 将结果存入缓存
        albumArtCache[cacheKey] = result
        return result
    }
    
    /**
     * 通过歌曲路径获取内嵌专辑封面
     * 从音频文件中直接提取内嵌的专辑封面
     *
     * @param songPath 歌曲文件路径
     * @return 专辑封面Bitmap，如果获取失败返回null
     */
    fun getEmbeddedAlbumArt(songPath: String): Bitmap? {
        // 生成缓存键
        val cacheKey = "embedded:$songPath"
        
        // 检查缓存，如果存在直接返回
        if (albumArtCache.containsKey(cacheKey)) {
            return albumArtCache[cacheKey]
        }
        
        // 缓存中不存在，读取专辑封面
        val result = try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(songPath)
            val art = retriever.embeddedPicture
            retriever.release()
            
            if (art != null) {
                android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
            } else {
                null
            }
        } catch (e: Exception) {
            // 优化异常处理，只在必要时打印日志
            // 专门处理ETXTBSY异常，避免打印完整堆栈跟踪
            if (e.message?.contains("ETXTBSY") == true) {
                // 文件忙异常，无需打印完整堆栈
                null
            } else {
                // 其他异常，只打印简单日志，不打印完整堆栈
                e.printStackTrace()
                null
            }
        }
        
        // 将结果存入缓存
        albumArtCache[cacheKey] = result
        return result
    }
}