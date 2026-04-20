package com.musicplayer.ui.widget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * 圆形ImageView控件
 */
class CircularImageView : AppCompatImageView {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rectF = RectF()
    private var radius = 0f
    
    constructor(context: Context) : super(context)
    
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    
    override fun onDraw(canvas: Canvas) {
        if (drawable == null) {
            super.onDraw(canvas)
            return
        }
        
        // 计算半径
        radius = Math.min(width, height) / 2f
        
        // 创建圆形路径
        path.reset()
        path.addCircle(width / 2f, height / 2f, radius, Path.Direction.CW)
        
        // 保存画布状态
        canvas.save()
        
        // 裁剪画布为圆形
        canvas.clipPath(path)
        
        // 绘制图像
        super.onDraw(canvas)
        
        // 恢复画布状态
        canvas.restore()
    }
}