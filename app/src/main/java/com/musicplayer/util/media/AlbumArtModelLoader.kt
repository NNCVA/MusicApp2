package com.musicplayer.util.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * 自定义ModelLoader用于加载专辑封面
 * 避免使用Content URI导致的ETXTBSY错误
 */
class AlbumArtModelLoader(private val context: Context) : ModelLoader<AlbumArtModelLoader.AlbumArtUri, InputStream> {
    
    data class AlbumArtUri(val albumId: Long, val songPath: String)
    
    override fun buildLoadData(
        model: AlbumArtUri,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        return ModelLoader.LoadData(
            ObjectKey("${model.albumId}_${model.songPath}"),
            AlbumArtDataFetcher(context, model)
        )
    }
    
    override fun handles(model: AlbumArtUri): Boolean {
        return true
    }
    
    /**
     * 数据获取器
     */
    class AlbumArtDataFetcher(
        private val context: Context,
        private val model: AlbumArtUri
    ) : DataFetcher<InputStream> {
        
        private var inputStream: InputStream? = null
        
        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
            try {
                // 首先尝试通过albumId获取专辑封面
                var bitmap: Bitmap? = null
                
                if (model.albumId > 0) {
                    bitmap = AlbumArtExtractor.getAlbumArtBitmap(context.contentResolver, model.albumId)
                }
                
                // 如果通过albumId获取失败，则尝试从音频文件中提取内嵌封面
                if (bitmap == null && model.songPath.isNotEmpty()) {
                    bitmap = AlbumArtExtractor.getEmbeddedAlbumArt(model.songPath)
                }
                
                // 如果成功获取到Bitmap，则转换为InputStream
                if (bitmap != null) {
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    val data = outputStream.toByteArray()
                    inputStream = ByteArrayInputStream(data)
                    callback.onDataReady(inputStream)
                } else {
                    callback.onLoadFailed(Exception("Failed to load album art"))
                }
            } catch (e: Exception) {
                Log.e("AlbumArtModelLoader", "Error loading album art", e)
                callback.onLoadFailed(e)
            }
        }
        
        override fun cleanup() {
            try {
                inputStream?.close()
            } catch (e: Exception) {
                // 忽略关闭异常
            }
        }
        
        override fun cancel() {
            // 取消操作（如果需要的话）
        }
        
        override fun getDataClass(): Class<InputStream> {
            return InputStream::class.java
        }
        
        override fun getDataSource(): DataSource {
            return DataSource.LOCAL
        }
    }
    
    /**
     * Factory类用于创建AlbumArtModelLoader实例
     */
    class Factory(private val context: Context) : ModelLoaderFactory<AlbumArtUri, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<AlbumArtUri, InputStream> {
            return AlbumArtModelLoader(context)
        }
        
        override fun teardown() {
            // 清理资源（如果需要的话）
        }
    }
}