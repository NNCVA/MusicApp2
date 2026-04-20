package com.musicplayer.util.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources

/**
 * 底部裁剪Drawable
 * 用于将图片的底部部分精确显示在指定区域内
 */
class BottomCropDrawable(
    private val context: Context,
    @DrawableRes private val drawableResId: Int
) : Drawable() {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    
    init {
        loadBitmap()
    }
    
    private fun loadBitmap() {
        try {
            val drawable = AppCompatResources.getDrawable(context, drawableResId)
            if (drawable is BitmapDrawable) {
                bitmap = drawable.bitmap
            } else if (drawable != null) {
                bitmap = drawableToBitmap(drawable)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        
        return bitmap
    }
    
    override fun draw(canvas: Canvas) {
        val bitmap = this.bitmap ?: return
        val bounds = bounds
        
        // 计算缩放比例，保持宽度填满
        val scale = bounds.width().toFloat() / bitmap.width
        
        // 计算源矩形（取底部部分）
        val srcRect = Rect(
            0,
            Math.max(0, (bitmap.height - (bounds.height() / scale)).toInt()),
            bitmap.width,
            bitmap.height
        )
        
        // 目标矩形
        val dstRect = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
    }
    
    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }
    
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }
    
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }
}