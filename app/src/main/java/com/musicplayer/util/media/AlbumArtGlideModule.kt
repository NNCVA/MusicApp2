package com.musicplayer.util.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.musicplayer.util.media.AlbumArtModelLoader.AlbumArtUri
import java.io.InputStream

/**
 * Glide模块，注册自定义的专辑封面加载器
 */
@GlideModule
class AlbumArtGlideModule : AppGlideModule() {
    
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        super.registerComponents(context, glide, registry)
        registry.prepend(
            AlbumArtUri::class.java,
            InputStream::class.java,
            AlbumArtModelLoader.Factory(context)
        )
    }
}