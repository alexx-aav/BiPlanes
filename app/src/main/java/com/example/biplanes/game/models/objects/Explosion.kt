package com.example.biplanes.game.models.objects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin

class Explosion(
    private val x: Float,
    private val y: Float,
    private val maxRadius: Float
) {
    private var currentRadius = 0f
    private var alpha = 255
    private val duration = 1000L // Длительность взрыва в миллисекундах
    private val startTime = System.currentTimeMillis()
    private val paint = Paint()
    
    fun update() {
        val elapsedTime = System.currentTimeMillis() - startTime
        val progress = (elapsedTime.toFloat() / duration).coerceIn(0f, 1f)
        
        currentRadius = maxRadius * progress
        alpha = (255 * (1f - progress)).toInt()
    }
    
    /**
     * Рисует взрыв на канвасе с использованием внутреннего Paint
     * @param canvas канвас для рисования
     */
    fun draw(canvas: Canvas) {
        // Рисуем основной круг взрыва
        paint.color = Color.rgb(255, 100, 0) // Оранжевый цвет
        paint.alpha = alpha
        canvas.drawCircle(x, y, currentRadius, paint)
        
        // Рисуем внутренний круг (более яркий)
        paint.color = Color.rgb(255, 200, 0) // Желтый цвет
        paint.alpha = alpha
        canvas.drawCircle(x, y, currentRadius * 0.7f, paint)
        
        // Рисуем искры
        paint.color = Color.rgb(255, 255, 0) // Ярко-желтый
        paint.alpha = alpha
        paint.strokeWidth = 3f
        
        val sparkCount = 8
        for (i in 0 until sparkCount) {
            val angle = (i * 360f / sparkCount) + (System.currentTimeMillis() % 360)
            val radians = Math.toRadians(angle.toDouble())
            val endX = x + (currentRadius * 1.2f * cos(radians)).toFloat()
            val endY = y + (currentRadius * 1.2f * sin(radians)).toFloat()
            
            canvas.drawLine(x, y, endX, endY, paint)
        }
    }
    
    /**
     * Рисует взрыв на канвасе с использованием внешнего Paint
     * @param canvas канвас для рисования
     * @param externalPaint внешний Paint для рисования
     */
    fun draw(canvas: Canvas, externalPaint: Paint) {
        // Сохраняем настройки внешнего Paint
        val originalColor = externalPaint.color
        val originalAlpha = externalPaint.alpha
        val originalStrokeWidth = externalPaint.strokeWidth
        
        // Рисуем основной круг взрыва
        externalPaint.color = Color.rgb(255, 100, 0) // Оранжевый цвет
        externalPaint.alpha = alpha
        canvas.drawCircle(x, y, currentRadius, externalPaint)
        
        // Рисуем внутренний круг (более яркий)
        externalPaint.color = Color.rgb(255, 200, 0) // Желтый цвет
        externalPaint.alpha = alpha
        canvas.drawCircle(x, y, currentRadius * 0.7f, externalPaint)
        
        // Рисуем искры
        externalPaint.color = Color.rgb(255, 255, 0) // Ярко-желтый
        externalPaint.alpha = alpha
        externalPaint.strokeWidth = 3f
        
        val sparkCount = 8
        for (i in 0 until sparkCount) {
            val angle = (i * 360f / sparkCount) + (System.currentTimeMillis() % 360)
            val radians = Math.toRadians(angle.toDouble())
            val endX = x + (currentRadius * 1.2f * cos(radians)).toFloat()
            val endY = y + (currentRadius * 1.2f * sin(radians)).toFloat()
            
            canvas.drawLine(x, y, endX, endY, externalPaint)
        }
        
        // Восстанавливаем настройки внешнего Paint
        externalPaint.color = originalColor
        externalPaint.alpha = originalAlpha
        externalPaint.strokeWidth = originalStrokeWidth
    }
    
    fun isFinished(): Boolean {
        return System.currentTimeMillis() - startTime >= duration
    }
    
    companion object {
        /**
         * Создает взрыв
         * @param x координата X взрыва
         * @param y координата Y взрыва
         * @param size размер взрыва (маленький, средний, большой)
         * @return созданный объект взрыва
         */
        fun createExplosion(
            x: Float, 
            y: Float, 
            size: ExplosionSize
        ): Explosion {
            // Определяем размер взрыва
            val radius = when (size) {
                ExplosionSize.SMALL -> 50f
                ExplosionSize.MEDIUM -> 100f
                ExplosionSize.LARGE -> 150f
            }
            
            // Создаем и возвращаем объект взрыва
            return Explosion(x, y, radius)
        }
    }
    
    /**
     * Перечисление для размеров взрыва
     */
    enum class ExplosionSize {
        SMALL,   // Маленький взрыв (пуля в землю)
        MEDIUM,  // Средний взрыв (попадание в мишень)
        LARGE    // Большой взрыв (уничтожение самолета)
    }
} 