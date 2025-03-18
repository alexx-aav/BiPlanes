package com.example.biplanes.game.controls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.sqrt

class Joystick @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var baseRadius: Float = 0f
    private var handleRadius: Float = 0f
    
    private var handleX: Float = 0f
    private var handleY: Float = 0f
    private var isPressed: Boolean = false
    private var pointerId: Int = INVALID_POINTER_ID

    private val paint = Paint().apply {
        color = Color.GRAY
        alpha = 128
        style = Paint.Style.FILL
    }
    
    private val handlePaint = Paint().apply {
        color = Color.DKGRAY
        alpha = 255
        style = Paint.Style.FILL
    }

    companion object {
        private const val INVALID_POINTER_ID = -1
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = Math.min(w, h) / 3f
        handleRadius = baseRadius / 2f
        
        handleX = centerX
        handleY = centerY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isPointInJoystick(event.x, event.y)) {
                    isPressed = true
                    pointerId = event.getPointerId(0)
                    updatePosition(event.x, event.y)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPressed) {
                    val pointerIndex = event.findPointerIndex(pointerId)
                    if (pointerIndex != -1) {
                        updatePosition(event.getX(pointerIndex), event.getY(pointerIndex))
                        invalidate()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pointerId != INVALID_POINTER_ID) {
                    isPressed = false
                    pointerId = INVALID_POINTER_ID
                    resetPosition()
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isPointInJoystick(x: Float, y: Float): Boolean {
        val distance = sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY))
        return distance <= baseRadius
    }

    private fun updatePosition(x: Float, y: Float) {
        val distance = sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY))
        if (distance <= baseRadius) {
            handleX = x
            handleY = y
        } else {
            val ratio = baseRadius / distance
            handleX = centerX + (x - centerX) * ratio
            handleY = centerY + (y - centerY) * ratio
        }
    }

    private fun resetPosition() {
        handleX = centerX
        handleY = centerY
    }

    // Добавляем методы для получения угла и силы
    fun getAngle(): Float {
        return atan2(handleY - centerY, handleX - centerX)
    }

    fun getStrength(): Float {
        val distance = sqrt((handleX - centerX) * (handleX - centerX) + (handleY - centerY) * (handleY - centerY))
        return (distance / baseRadius).coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Рисуем внешнюю обводку основания
        paint.color = Color.rgb(50, 50, 200) // Синий цвет для обводки
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        canvas.drawCircle(centerX, centerY, baseRadius + 5f, paint)
        
        // Рисуем основание
        paint.color = Color.rgb(100, 100, 200) // Светло-синий цвет для основания
        paint.alpha = 180
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, baseRadius, paint)
        
        // Рисуем направляющие линии
        paint.color = Color.WHITE
        paint.alpha = 120
        paint.strokeWidth = 3f
        canvas.drawLine(centerX - baseRadius, centerY, centerX + baseRadius, centerY, paint)
        canvas.drawLine(centerX, centerY - baseRadius, centerX, centerY + baseRadius, paint)
        
        // Рисуем ручку с обводкой
        handlePaint.color = Color.BLACK
        handlePaint.style = Paint.Style.STROKE
        handlePaint.strokeWidth = 6f
        canvas.drawCircle(handleX, handleY, handleRadius + 3f, handlePaint)
        
        // Рисуем ручку
        handlePaint.color = Color.rgb(50, 50, 150) // Темно-синий для ручки
        handlePaint.style = Paint.Style.FILL
        canvas.drawCircle(handleX, handleY, handleRadius, handlePaint)
        
        // Рисуем блик на ручке для объемности
        handlePaint.color = Color.WHITE
        handlePaint.alpha = 150
        canvas.drawCircle(handleX - handleRadius * 0.3f, handleY - handleRadius * 0.3f, handleRadius * 0.2f, handlePaint)
    }

    // Геттеры для получения текущего положения джойстика
    fun getXPercent(): Float = (handleX - centerX) / baseRadius
    fun getYPercent(): Float = (handleY - centerY) / baseRadius
} 