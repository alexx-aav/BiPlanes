package com.example.biplanes.game.controls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class EjectButton @JvmOverloads constructor(
    context: Context,
    view: View
) : BaseButton(view) {

    private val paint = Paint().apply {
        isAntiAlias = true
    }

    init {
        // Устанавливаем слушатель на нажатие
        setOnTouchListener { isPressed ->
            view.invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val result = super.onTouchEvent(event)
        view.invalidate()
        return result
    }

    override fun onDraw(canvas: Canvas) {
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
}