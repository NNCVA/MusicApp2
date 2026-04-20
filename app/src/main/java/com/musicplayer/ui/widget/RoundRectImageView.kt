package com.musicplayer.ui.widget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/**
 * 圆角矩形ImageView控件
 */
class RoundRectImageView : AppCompatImageView {
    
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
        
        // 设置圆角半径
        radius = 10f
        
        // 创建圆角矩形路径
        rectF.set(0f, 0f, width.toFloat(), height.toFloat())
        path.reset()
        path.addRoundRect(rectF, radius, radius, Path.Direction.CW)
        
        // 保存画布状态
        canvas.save()
        
        // 裁剪画布为圆角矩形
        canvas.clipPath(path)
        
        // 绘制图像
        super.onDraw(canvas)
        
        // 恢复画布状态
        canvas.restore()
    }
}