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
    fun setToGroundLevel(groundHeight: Float) {
        // Устанавливаем позицию дома так, чтобы он стоял на земле
        // Опускаем дом ниже на 20% высоты, чтобы он был более глубоко погружен в землю
        position.y = groundHeight + height * 0.2f
        
        // Обновляем прямоугольник дома
        rect.set(
            position.x - width / 2f,
            position.y - height,
            position.x + width / 2f,
            position.y
        )
    }

    /**
     * Обновляет состояние дома (анимация флага)
     * @param deltaTime время, прошедшее с последнего обновления (в секундах)
     */
    fun update(deltaTime: Float) {
        // Увеличиваем счетчик анимации для волнообразного движения флага
        animationTime += deltaTime * 5f  // Увеличиваем скорость анимации
        
        // Увеличиваем фазу волны флага для движения
        flagWavePhase += deltaTime * 10f  // Значительно увеличиваем скорость движения флага
        
        // Обновляем путь для флага - создаем волнообразное движение
        updateFlagPath()
    }
    
    /**
     * Обновляет путь для флага, создавая волнообразное движение
     */
    private fun updateFlagPath() {
        // Сбрасываем путь
        flagPath.reset()
        
        // Начальные координаты флага - используем ту же X-координату, что и для флагштока
        val flagPoleX = rect.right - width / 8
        val flagPoleY = rect.top - height / 2 // Верхняя точка флагштока
        
        // Ширина и высота флага
        val flagWidth = width * 0.4f
        val flagHeight = height * 0.25f
        
        // Начинаем путь от верхней части флагштока
        flagPath.moveTo(flagPoleX, flagPoleY)
        
        // Амплитуда волны флага (увеличена для более заметного эффекта)
        val waveAmplitude = flagHeight * 0.2f
        
        // Частота волны
        val waveFrequency = 0.1f
        
        // Рисуем верхнюю часть флага с волнообразным движением
        for (i in 0..20) {
            val normalizedX = i / 20f
            val x = flagPoleX + normalizedX * flagWidth
            
            // Создаем волнообразное движение с использованием синусоиды
            // Амплитуда увеличивается по мере удаления от флагштока
            val amplitude = waveAmplitude * normalizedX * 2.5f
            
            // Добавляем фазу для движения волны со временем
            val waveY = amplitude * Math.sin((normalizedX * 10 + flagWavePhase) * waveFrequency * Math.PI * 2).toFloat()
            
            // Верхняя часть флага
            val y = flagPoleY + waveY
            
            flagPath.lineTo(x, y)
        }
        
        // Добавляем правую вертикальную сторону флага
        flagPath.lineTo(flagPoleX + flagWidth, flagPoleY + flagHeight)
        
        // Рисуем нижнюю часть флага с волнообразным движением в обратном направлении
        for (i in 20 downTo 0) {
            val normalizedX = i / 20f
            val x = flagPoleX + normalizedX * flagWidth
            
            // Создаем волнообразное движение с использованием синусоиды
            // Амплитуда увеличивается по мере удаления от флагштока
            val amplitude = waveAmplitude * normalizedX * 2.5f
            
            // Добавляем фазу для движения волны со временем
            // Для нижней части используем другой паттерн волны
            val waveY = amplitude * Math.sin((normalizedX * 10 + flagWavePhase + 1) * waveFrequency * Math.PI * 2).toFloat()
            
            // Нижняя часть флага
            val y = flagPoleY + flagHeight + waveY
            
            flagPath.lineTo(x, y)
        }
        
        // Замыкаем путь
        flagPath.close()
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
        canvas.drawPath(flagPath, paint)
        
        // Добавляем обводку флага
        paint.style = Paint.Style.STROKE
        paint.color = Color.BLACK
        paint.strokeWidth = 1f
        canvas.drawPath(flagPath, paint)
        
        // Рисуем табличку "respawn" на доме
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
        canvas.drawText("RESPAWN", rect.centerX(), rect.top + height / 4, paint)
        
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