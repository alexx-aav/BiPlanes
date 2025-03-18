package com.example.biplanes.game.controls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

class FireButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var buttonPressed: Boolean = false
    
    private var isEjectButton: Boolean = false
    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var radius: Float = 0f
        
    private val paint = Paint().apply {
        style = Paint.Style.FILL
        strokeWidth = 5f
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = Math.min(w, h) / 2f
    }

    // Метод для проверки нажатия в определенной точке
    private fun isPointInButton(touchX: Float, touchY: Float): Boolean {
        val distance = sqrt((touchX - centerX) * (touchX - centerX) + (touchY - centerY) * (touchY - centerY))
        return distance <= radius
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isPointInButton(event.x, event.y)) {
                    buttonPressed = true
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                buttonPressed = false
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Рисуем внешний круг кнопки (обводка)
        paint.color = if (isEjectButton) Color.YELLOW else Color.RED
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, radius + 5f, paint)
        
        // Рисуем основной круг кнопки
        paint.color = if (buttonPressed) {
            if (isEjectButton) Color.rgb(255, 165, 0) // Оранжевый при нажатии для кнопки катапультирования
            else Color.rgb(200, 0, 0) // Темно-красный при нажатии для кнопки стрельбы
        } else {
            if (isEjectButton) Color.rgb(255, 215, 0) // Золотой для кнопки катапультирования
            else Color.rgb(220, 50, 50) // Ярко-красный для кнопки стрельбы
        }
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, radius, paint)
        
        // Рисуем значок
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.1f // Более толстые линии для лучшей видимости
        
        if (isEjectButton) {
            // Рисуем значок катапультирования (парашютист)
            val size = radius * 0.6f
            // Голова
            canvas.drawCircle(centerX, centerY - size * 0.3f, size * 0.15f, paint)
            // Тело
            canvas.drawLine(centerX, centerY - size * 0.15f, centerX, centerY + size * 0.2f, paint)
            // Руки
            canvas.drawLine(centerX - size * 0.2f, centerY, centerX + size * 0.2f, centerY, paint)
            // Ноги
            canvas.drawLine(centerX, centerY + size * 0.2f, centerX - size * 0.15f, centerY + size * 0.4f, paint)
            canvas.drawLine(centerX, centerY + size * 0.2f, centerX + size * 0.15f, centerY + size * 0.4f, paint)
            // Парашют
            val path = Path()
            path.moveTo(centerX - size * 0.4f, centerY - size * 0.4f)
            path.quadTo(centerX, centerY - size * 0.7f, centerX + size * 0.4f, centerY - size * 0.4f)
            canvas.drawPath(path, paint)
            // Стропы
            canvas.drawLine(centerX - size * 0.4f, centerY - size * 0.4f, centerX, centerY - size * 0.15f, paint)
            canvas.drawLine(centerX + size * 0.4f, centerY - size * 0.4f, centerX, centerY - size * 0.15f, paint)
        } else {
            // Рисуем значок стрельбы (прицел)
            val size = radius * 0.5f
            canvas.drawCircle(centerX, centerY, size, paint)
            // Перекрестие
            canvas.drawLine(centerX - size, centerY, centerX + size, centerY, paint)
            canvas.drawLine(centerX, centerY - size, centerX, centerY + size, paint)
            // Внешний круг
            canvas.drawCircle(centerX, centerY, size * 1.5f, paint)
        }
    }
    
    fun setEjectButton(isEject: Boolean) {
        isEjectButton = isEject
        invalidate()
    }
    
    fun getButtonPressed(): Boolean {
        return buttonPressed
    }
} 