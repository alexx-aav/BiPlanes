package com.example.biplanes.game.models.objects

import android.graphics.Canvas
import android.graphics.Paint
import com.example.biplanes.game.models.Vector2D

class Bullet(
    val color: Int,
    val damage: Int = 25,
    val radius: Float = 5f
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
        radius: Float = 5f,
        damage: Int = 25
    ) : this(color, damage, radius) {
        this.position = position
        this.velocity = velocity
    }
    
    fun update(deltaTime: Float) {
        if (isDestroyed) return
        
        // Исправляем метод обновления позиции
        // Вместо position.add, которое модифицирует текущую позицию не переприсваивая результат
        position = position.add(Vector2D(velocity.x * deltaTime * 60f, velocity.y * deltaTime * 60f))
        
        // Обновляем время жизни
        lifeTime += deltaTime
        if (lifeTime >= maxLifeTime) {
            isDestroyed = true
        }
    }
    
    fun draw(canvas: Canvas, paint: Paint) {
        if (isDestroyed) return
        
        // Сохраняем оригинальные настройки краски
        val originalColor = paint.color
        val originalStyle = paint.style
        
        // Настраиваем краску для пули
        paint.color = color
        paint.style = Paint.Style.FILL
        
        // Рисуем пулю
        canvas.drawCircle(position.x, position.y, radius, paint)
        
        // Восстанавливаем настройки краски
        paint.color = originalColor
        paint.style = originalStyle
    }

    fun hit(plane: Plane) {
        if (!isDestroyed) {
            plane.takeDamage(damage)
            isDestroyed = true
        }
    }
} 