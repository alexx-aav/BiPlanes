package com.example.biplanes.game.models.objects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.example.biplanes.game.models.Vector2D
import kotlin.math.sin

class House(
    val position: PointF,
    val width: Float = 200f,
    val height: Float = 150f
) {
    private val roofHeight = height / 2
    private val doorWidth = width / 5
    private val doorHeight = height * 0.6f
    private val windowSize = width / 6
    private var animationTime = 0f
    private var flagWavePhase = 0f
    private val flagPath = Path()
    
    private val rect = RectF(
        position.x - width / 2,
        position.y - height,
        position.x + width / 2,
        position.y
    )
    
    // Добавляем метод для получения уровня земли
    fun getGroundLevel(): Float {
        return position.y
    }
    
    // Добавляем метод для установки дома на уровень земли
    fun setToGroundLevel(groundLevel: Float) {
        position.y = groundLevel + 5f
        // Обновляем прямоугольник дома
        rect.set(
            position.x - width / 2,
            position.y - height,
            position.x + width / 2,
            position.y
        )
    }

    // Добавляем метод для обновления анимации
    fun update(deltaTime: Float) {
        animationTime += deltaTime
        flagWavePhase = (animationTime * 5f) % (2f * Math.PI.toFloat())
    }

    fun draw(canvas: Canvas, paint: Paint) {
        // Сохраняем оригинальные настройки Paint
        val originalColor = paint.color
        val originalStyle = paint.style
        val originalStrokeWidth = paint.strokeWidth

        // Рисуем стены дома
        paint.color = Color.rgb(210, 180, 140) // Бежевый цвет
        paint.style = Paint.Style.FILL
        canvas.drawRect(rect, paint)
        
        // Рисуем крышу
        paint.color = Color.rgb(165, 42, 42) // Коричневый цвет
        val roofPath = Path().apply {
            moveTo(rect.left - 20f, rect.top)
            lineTo(rect.centerX(), rect.top - roofHeight)
            lineTo(rect.right + 20f, rect.top)
            close()
        }
        canvas.drawPath(roofPath, paint)
        
        // Рисуем дверь
        paint.color = Color.rgb(101, 67, 33) // Темно-коричневый
        canvas.drawRect(
            rect.centerX() - doorWidth / 2,
            rect.bottom - doorHeight,
            rect.centerX() + doorWidth / 2,
            rect.bottom,
            paint
        )
        
        // Рисуем ручку двери
        paint.color = Color.YELLOW
        canvas.drawCircle(
            rect.centerX() + doorWidth / 4,
            rect.bottom - doorHeight / 2,
            5f,
            paint
        )
        
        // Рисуем окна
        paint.color = Color.CYAN
        // Левое окно
        canvas.drawRect(
            rect.left + width / 4 - windowSize / 2,
            rect.centerY() - windowSize / 2,
            rect.left + width / 4 + windowSize / 2,
            rect.centerY() + windowSize / 2,
            paint
        )
        // Правое окно
        canvas.drawRect(
            rect.right - width / 4 - windowSize / 2,
            rect.centerY() - windowSize / 2,
            rect.right - width / 4 + windowSize / 2,
            rect.centerY() + windowSize / 2,
            paint
        )
        
        // Рисуем рамы окон
        paint.color = Color.WHITE
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        
        // Левое окно - вертикальная линия
        canvas.drawLine(
            rect.left + width / 4,
            rect.centerY() - windowSize / 2,
            rect.left + width / 4,
            rect.centerY() + windowSize / 2,
            paint
        )
        // Левое окно - горизонтальная линия
        canvas.drawLine(
            rect.left + width / 4 - windowSize / 2,
            rect.centerY(),
            rect.left + width / 4 + windowSize / 2,
            rect.centerY(),
            paint
        )
        
        // Правое окно - вертикальная линия
        canvas.drawLine(
            rect.right - width / 4,
            rect.centerY() - windowSize / 2,
            rect.right - width / 4,
            rect.centerY() + windowSize / 2,
            paint
        )
        // Правое окно - горизонтальная линия
        canvas.drawLine(
            rect.right - width / 4 - windowSize / 2,
            rect.centerY(),
            rect.right - width / 4 + windowSize / 2,
            rect.centerY(),
            paint
        )
        
        // Рисуем флагшток
        paint.color = Color.rgb(139, 69, 19) // Коричневый
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 4f
        
        // Начинаем флагшток от крыши дома
        val flagpoleX = rect.right - width / 8
        val flagpoleStartY = rect.top // Начало на уровне крыши
        val flagpoleEndY = rect.top - height / 2 // Конец выше крыши
        
        canvas.drawLine(
            flagpoleX,
            flagpoleStartY,
            flagpoleX,
            flagpoleEndY,
            paint
        )
        
        // Рисуем развевающийся флаг
        paint.color = Color.RED
        flagPath.reset()
        flagPath.moveTo(flagpoleX, flagpoleEndY)
        
        // Добавляем волнистую форму флага с анимацией
        val flagWidth = width / 3
        val flagHeight = height / 4
        val waveAmplitude = flagHeight / 4
        
        for (i in 0..10) {
            val x = flagpoleX + (i * flagWidth / 10)
            val waveOffset = sin(flagWavePhase + i * 0.5f) * waveAmplitude
            flagPath.lineTo(x, flagpoleEndY + waveOffset)
        }
        
        // Завершаем контур флага
        flagPath.lineTo(flagpoleX + flagWidth, flagpoleEndY + flagHeight / 2)
        flagPath.lineTo(flagpoleX, flagpoleEndY + flagHeight / 2)
        flagPath.close()
        
        canvas.drawPath(flagPath, paint)
        
        // Добавляем обводку флага
        paint.style = Paint.Style.STROKE
        paint.color = Color.BLACK
        paint.strokeWidth = 1f
        canvas.drawPath(flagPath, paint)
        
        // Рисуем табличку "HANGAR" на доме
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        val signRect = RectF(
            rect.centerX() - width / 3,
            rect.top + height / 6,
            rect.centerX() + width / 3,
            rect.top + height / 3
        )
        canvas.drawRect(signRect, paint)
        
        paint.color = Color.BLACK
        paint.textSize = height / 6
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("HANGAR", rect.centerX(), rect.top + height / 4, paint)
        
        // Восстанавливаем оригинальные настройки Paint
        paint.color = originalColor
        paint.style = originalStyle
        paint.strokeWidth = originalStrokeWidth
        paint.alpha = 255
    }

    fun checkCollision(point: PointF): Boolean {
        return rect.contains(point.x, point.y)
    }
    
    fun checkPilotRescue(pilot: Pilot): Boolean {
        val pilotPos = pilot.position
        return pilotPos.x >= rect.left && pilotPos.x <= rect.right && 
               pilotPos.y >= rect.top && pilotPos.y <= rect.bottom
    }
} 