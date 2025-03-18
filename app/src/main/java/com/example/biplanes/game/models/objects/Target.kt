package com.example.biplanes.game.models.objects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import com.example.biplanes.game.models.Vector2D
import kotlin.random.Random
import android.util.Log

class Target(
    var position: Vector2D,
    val width: Float,
    val height: Float,
    val type: Type
) {
    enum class Type {
        STATIC,
        MOVING
    }
    
    var isDestroyed: Boolean = false
    var health = 3 // Для уничтожения мишени нужно 3 попадания
    private var hitAnimationTime = 0f
    private var oscillationAngle = 0f
    
    fun update() {
        // Анимация при попадании
        if (hitAnimationTime > 0) {
            hitAnimationTime -= 0.05f
        }
        
        // Плавное колебание мишени
        oscillationAngle += 0.05f
        position.y += Math.sin(oscillationAngle.toDouble()).toFloat() * 0.5f
    }
    
    fun hit() {
        health--
        hitAnimationTime = 1f
        
        if (health <= 0) {
            isDestroyed = true
        }
    }
    
    fun draw(canvas: Canvas, paint: Paint) {
        if (isDestroyed) return
        
        // Внешний круг (красный)
        paint.color = Color.RED
        paint.style = Paint.Style.FILL
        canvas.drawCircle(position.x, position.y, width / 2, paint)
        
        // Средний круг (белый)
        paint.color = Color.WHITE
        canvas.drawCircle(position.x, position.y, width * 0.7f / 2, paint)
        
        // Внутренний круг (красный)
        paint.color = Color.RED
        canvas.drawCircle(position.x, position.y, width * 0.4f / 2, paint)
        
        // Центральная точка (черная)
        paint.color = Color.BLACK
        canvas.drawCircle(position.x, position.y, width * 0.1f / 2, paint)
        
        // Если мишень была поражена, рисуем эффект попадания
        if (hitAnimationTime > 0) {
            paint.color = Color.YELLOW
            paint.alpha = (hitAnimationTime * 255).toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            canvas.drawCircle(position.x, position.y, width * (1f + hitAnimationTime * 0.5f) / 2, paint)
            paint.alpha = 255
        }
        
        // Отображаем оставшееся здоровье
        paint.color = Color.BLACK
        paint.textSize = width * 0.8f / 2
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(health.toString(), position.x, position.y + width * 0.3f / 2, paint)
    }
    
    fun destroy() {
        isDestroyed = true
    }
    
    companion object {
        /**
         * Создает группу мишеней для тренировочного режима
         * @param count количество мишеней
         * @param screenWidth ширина экрана
         * @param screenHeight высота экрана
         * @param groundHeight высота земли
         * @return список созданных мишеней
         */
        fun createTargets(count: Int, screenWidth: Float, screenHeight: Float, groundHeight: Float): List<Target> {
            val targets = mutableListOf<Target>()
            val targetWidth = screenWidth / 30f
            
            // Проверяем входные параметры
            if (count <= 0) {
                Log.e("Target", "Запрошено создание $count мишеней, возвращаем пустой список")
                return targets
            }
            
            Log.d("Target", "Создаем $count мишеней")
            
            // Вычисляем безопасную зону для размещения мишеней
            val safeMargin = targetWidth * 2
            val minX = safeMargin
            val maxX = screenWidth - safeMargin
            val minY = safeMargin
            val maxY = groundHeight - safeMargin * 2
            
            // Делим экран на секции для равномерного распределения мишеней
            val sectionWidth = (maxX - minX) / count
            
            // Создаем мишени в случайных позициях, но в разных секциях экрана
            for (i in 0 until count) {
                // Вычисляем границы секции
                val sectionMinX = minX + i * sectionWidth
                val sectionMaxX = sectionMinX + sectionWidth
                
                // Генерируем случайную позицию в пределах секции
                val x = Random.nextFloat() * (sectionMaxX - sectionMinX) + sectionMinX
                val y = Random.nextFloat() * (maxY - minY) + minY
                
                // Создаем мишень
                val target = Target(Vector2D(x, y), targetWidth, targetWidth, Type.STATIC)
                targets.add(target)
                
                Log.d("Target", "Создана мишень #${i+1} на позиции ($x, $y)")
            }
            
            Log.d("Target", "Создано ${targets.size} мишеней")
            return targets
        }
        
        /**
         * Проверяет столкновение пули с мишенью
         * @param bullet пуля
         * @param target мишень
         * @return true, если произошло столкновение
         */
        fun checkCollision(bullet: Bullet, target: Target): Boolean {
            if (bullet.isDestroyed || target.isDestroyed) return false
            
            // Простая проверка столкновения на основе расстояния
            val distance = Vector2D.distance(bullet.position, target.position)
            return distance < (bullet.radius + target.width / 2)
        }
    }
} 