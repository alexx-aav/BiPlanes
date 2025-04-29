package com.example.biplanes.game.models.objects

import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Color
import android.graphics.Paint
import com.example.biplanes.game.models.Collidable
import com.example.biplanes.game.models.Vector2D
import kotlin.random.Random
import android.util.Log

class Target(
    override var position: Vector2D,
    override var width: Float,
    override var height: Float,
    val type: Type
) : Collidable {
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
        
        // Сохраняем оригинальные настройки
        val originalColor = paint.color
        val originalStyle = paint.style
        val originalStrokeWidth = paint.strokeWidth
        val originalAlpha = paint.alpha
        
        // Внешний круг (красный)
        paint.color = Color.RED
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        canvas.drawCircle(position.x, position.y, width / 2, paint)
        
        // Средний круг (белый)
        paint.color = Color.WHITE
        canvas.drawCircle(position.x, position.y, width * 0.7f / 2, paint)
        
        // Внутренний круг (красный)
        paint.color = Color.RED
        canvas.drawCircle(position.x, position.y, width * 0.4f / 2, paint)
        
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
        
        // Восстанавливаем оригинальные настройки
        paint.color = originalColor
        paint.style = originalStyle
        paint.strokeWidth = originalStrokeWidth
        paint.alpha = originalAlpha
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
            // Увеличиваем размер мишени для лучшей видимости и игрового процесса
            val targetWidth = screenWidth / 20f  // Было / 30f
            
            // Проверяем входные параметры
            if (count <= 0) {
                Log.e("Target", "Запрошено создание $count мишеней, возвращаем пустой список")
                return targets
            }
            
            Log.d("Target", "Создаем $count мишеней с размером $targetWidth")
            
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
                
                Log.d("Target", "Создана мишень #${i+1} на позиции ($x, $y) с размером $targetWidth")
            }
            
            Log.d("Target", "Создано ${targets.size} мишеней")
            return targets
        }
        
        /**
         * Обновляет состояние всех мишеней и возвращает список уничтоженных
         * @param targets Список мишеней для обновления
         * @return Список уничтоженных мишеней, которые нужно удалить
         */
        fun updateTargets(targets: List<Target>): List<Target> {
            val destroyedTargets = mutableListOf<Target>()
            
            try {
                // Обновляем каждую мишень и проверяем, не уничтожена ли она
                for (target in targets) {
                    target.update()
                    if (target.isDestroyed) {
                        destroyedTargets.add(target)
                        Log.d("Target", "Мишень уничтожена на позиции (${target.position.x}, ${target.position.y})")
                    }
                }
            } catch (e: Exception) {
                Log.e("Target", "Ошибка при обновлении мишеней: ${e.message}")
            }
            
            return destroyedTargets
        }
        
       
    }

    override fun checkCollision(other: Collidable): Boolean = other.checkCollision(position.x, position.y)
    }
} 