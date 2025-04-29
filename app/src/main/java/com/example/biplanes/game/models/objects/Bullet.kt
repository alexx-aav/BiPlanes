package com.example.biplanes.game.models.objects

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color
import com.example.biplanes.game.models.Collidable
import com.example.biplanes.game.models.Vector2D
import android.util.Log

class Bullet(
    override val color: Int,
    override var damage: Int = 25,
    override var radius: Float = 8f
) : BaseObject(Vector2D()), Collidable {
    override var position: Vector2D = Vector2D()
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
    init{
        width = radius * 2
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

    override var width: Float = radius * 2
    override var height: Float = radius * 2
    override val bounds: RectF
        get() = RectF(position.x - radius, position.y - radius, position.x + radius, position.y + radius)

    override fun checkCollision(other: Collidable): Boolean {
        return when (other) {
            is Plane -> checkCollisionWithPlane(other)
            is Target -> checkCollisionWithTarget(other)
            else -> false
        }
    }

    private fun checkCollisionWithPlane(other: Plane): Boolean {
        return RectF.intersects(bounds, other.bounds)
    }

    private fun checkCollisionWithTarget(other: Target): Boolean {
        return RectF.intersects(bounds, other.bounds)
    }

    fun hit(collidable: Collidable){
        when (collidable) {
            is Plane -> {
                collidable.takeDamage(damage)
            }
            is Target -> {
                collidable.hit()
            }
        }
        isDestroyed = true
    }
} 