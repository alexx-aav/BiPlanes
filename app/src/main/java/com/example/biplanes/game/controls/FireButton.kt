package com.example.biplanes.game.controls

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class FireButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), BaseControl.Drawable {

    private var isEjectButton: Boolean = false    
    private val paint = Paint().apply {        
        style = Paint.Style.FILL
        strokeWidth = 5f
    }
    private val buttonControl = object: BaseButton(this){
        override fun onDraw(canvas: Canvas) {
            draw(canvas)
        }
    }

    init {
        buttonControl.setOnTouchListener {
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buttonControl.onSizeChanged(w,h,oldw,oldh)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean{
        return buttonControl.onTouchEvent(event)
    }

    override fun draw(canvas: Canvas) {
        val centerX = buttonControl.centerX
        val centerY = buttonControl.centerY
        val radius = buttonControl.radius

        // Рисуем внешний круг кнопки (обводка)
        paint.color = if (isEjectButton) Color.YELLOW else Color.RED
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, radius + 5f, paint)

        // Рисуем основной круг кнопки
        paint.color = if (buttonControl.isPressed) {
            if (isEjectButton)
                Color.rgb(255, 165, 0)
             else 
                Color.rgb(200, 0, 0)
        } else {
            if (isEjectButton) 
                Color.rgb(255, 215, 0)
            else
                Color.rgb(220, 50, 50)
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
    
    override fun onDraw(canvas: Canvas) {
        buttonControl.onDraw(canvas)
    }
    
    interface Drawable {
        fun draw(canvas: Canvas)
    }
}