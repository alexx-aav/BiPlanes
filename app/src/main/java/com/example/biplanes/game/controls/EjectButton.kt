package com.example.biplanes.game.controls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class EjectButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val paint = Paint()
    private var isPressed = false
    private var isActive = true
    private var radius = 0f
    private var centerX = 0f
    private var centerY = 0f
    
    // Интерфейс для обработки нажатия
    private var onEjectListener: (() -> Unit)? = null
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        radius = Math.min(w, h) / 2f
        centerX = w / 2f
        centerY = h / 2f
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!isActive) return
        
        // Рисуем основу кнопки (зеленая)
        paint.color = if (isPressed) Color.rgb(0, 180, 0) else Color.rgb(0, 220, 0)
        canvas.drawCircle(centerX, centerY, radius, paint)
        
        // Рисуем обводку
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(centerX, centerY, radius, paint)
        
        // Рисуем символ катапультирования (стрелка вверх)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        
        // Стрелка вверх
        val arrowWidth = radius * 0.5f
        val arrowHeight = radius * 0.7f
        
        // Треугольник стрелки
        val trianglePath = Path()
        trianglePath.moveTo(centerX, centerY - arrowHeight / 2)
        trianglePath.lineTo(centerX - arrowWidth / 2, centerY - arrowHeight / 2 + arrowHeight / 3)
        trianglePath.lineTo(centerX + arrowWidth / 2, centerY - arrowHeight / 2 + arrowHeight / 3)
        trianglePath.close()
        canvas.drawPath(trianglePath, paint)
        
        // Линия стрелки
        canvas.drawRect(
            RectF(
                centerX - arrowWidth / 4,
                centerY - arrowHeight / 2 + arrowHeight / 3,
                centerX + arrowWidth / 4,
                centerY + arrowHeight / 2
            ),
            paint
        )
    }
    
    private fun isPointInButton(touchX: Float, touchY: Float): Boolean {
        if (!isActive) return false
        
        val distance = Math.sqrt(
            Math.pow((touchX - centerX).toDouble(), 2.0) +
            Math.pow((touchY - centerY).toDouble(), 2.0)
        )
        
        return distance <= radius
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isPointInButton(event.x, event.y)) {
                    isPressed = true
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isPressed) {
                    isPressed = false
                    invalidate()
                    // Вызываем обработчик при отпускании кнопки
                    onEjectListener?.invoke()
                }
            }
        }
        return super.onTouchEvent(event)
    }
    
    fun reset() {
        isPressed = false
        invalidate()
    }
    
    fun deactivate() {
        isActive = false
        invalidate()
    }
    
    fun activate() {
        isActive = true
        invalidate()
    }
    
    fun isActive(): Boolean {
        return isActive
    }
    
    fun getIsPressed(): Boolean {
        return isPressed
    }
    
    // Метод для установки обработчика нажатия
    fun setOnEjectListener(listener: () -> Unit) {
        onEjectListener = listener
    }
} 