package com.example.biplanes.game.models.objects

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import com.example.biplanes.game.models.Vector2D
import android.util.Log

class Bullet(
    val color: Int,
    val damage: Int = 25,
    val radius: Float = 8f
) {
    var position = Vector2D()
    var velocity = Vector2D()
    var isDestroyed = false
    var lifeTime = 0f
    val maxLifeTime = 3f // Время жизни пули в секундах
    
    // Конструктор с начальной позицией и скоростью
    constructor(
        position: Vector2D,
        velocity: Vector2D,
        color: Int,
        radius: Float = 8f,
        damage: Int = 25
    ) : this(color, damage, radius) {
        this.position = position
        this.velocity = velocity
    }
    
    fun update(deltaTime: Float) {
        if (isDestroyed) return
        
        // Исправляем метод обновления позиции для более плавного движения
        val scaledDeltaTime = deltaTime * 60f  // Нормализация для стабильного перемещения
        
        // Сохраняем предыдущую позицию для отладки
        val prevPosition = position.copy()
        
        // Обновляем позицию с учетом дельты времени
        position = position.add(velocity.multiply(scaledDeltaTime))
        
        // Логируем движение пули для отладки
        if (lifeTime == 0f) {  // Только для новых пуль
            Log.d("Bullet", "Пуля начала движение: позиция=$position, скорость=$velocity")
        }
        
        // Обновляем время жизни
        lifeTime += deltaTime
        if (lifeTime >= maxLifeTime) {
            isDestroyed = true
            Log.d("Bullet", "Пуля уничтожена по истечении времени жизни")
        }
    }
    
    fun draw(canvas: Canvas, paint: Paint) {
        if (isDestroyed) return
        
        // Сохраняем оригинальные настройки краски
        val originalColor = paint.color
        val originalStyle = paint.style
        val originalAlpha = paint.alpha
        
        // Настраиваем краску для основной пули
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        
        // Рисуем основную пулю
        canvas.drawCircle(position.x, position.y, radius, paint)
        
        // Рисуем светлое пятно внутри пули для визуального эффекта
        paint.color = Color.WHITE
        paint.alpha = 150
        canvas.drawCircle(position.x - radius/3, position.y - radius/3, radius/3, paint)
        
        // Восстанавливаем настройки краски
        paint.color = originalColor
        paint.style = originalStyle
        paint.alpha = originalAlpha
    }

    fun hit(plane: Plane) {
        if (!isDestroyed) {
            plane.takeDamage(damage)
            isDestroyed = true
            Log.d("Bullet", "Пуля попала в самолет и нанесла $damage урона")
        }
    }
} 