package com.example.biplanes.game.models

import android.util.Log
import com.example.biplanes.game.models.objects.Bullet
import com.example.biplanes.game.models.objects.Explosion
import com.example.biplanes.game.models.objects.Plane
import com.example.biplanes.game.models.objects.Target
import com.example.biplanes.game.ui.GameView

/**
 * Класс для управления столкновениями в игре
 */
class CollisionManager(
    private val gameView: GameView,
    private val gameType: GameType
) {
    private val TAG = "CollisionManager"
    
    /**
     * Проверяет все возможные столкновения в игре
     * @param planes список самолетов
     * @param bullets список пуль
     * @param targets список мишеней
     * @param groundHeight высота земли
     * @return список пуль, которые нужно удалить
     */
    fun checkCollisions(
        planes: List<Plane>,
        bullets: List<Bullet>,
        targets: List<Target>,
        groundHeight: Float
    ): List<Bullet> {
        val bulletsToRemove = mutableListOf<Bullet>()
        
        try {
            // Проверяем столкновения пуль с самолетами
            for (bullet in bullets) {
                for (plane in planes) {
                    try {
                        // Пропускаем проверку столкновения пули с самолетом, который ее выпустил
                        if (bullet.color == plane.color) continue
                        
                        // Пропускаем уже уничтоженные самолеты
                        if (plane.isDestroyed) continue
                        
                        // Проверяем столкновение
                        if (plane.checkCollision(bullet)) {
                            bulletsToRemove.add(bullet)
                            
                            // Создаем взрыв
                            val explosion = Explosion.createExplosion(
                                bullet.position.x,
                                bullet.position.y,
                                Explosion.ExplosionSize.MEDIUM
                            )
                            gameView.addExplosion(explosion)
                            
                            // Наносим урон самолету
                            val damage = 50
                            plane.takeDamage(damage)
                            
                            // Если самолет уничтожен, пилот выпрыгивает
                            if (plane.isDestroyed && plane.pilot != null) {
                                try {
                                    gameView.ejectPilot(plane)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error ejecting pilot in checkCollisions: ${e.message}")
                                }
                            }
                            
                            // Обрабатываем логику в зависимости от режима игры
                            if (gameType == GameType.TRAINING) {
                                // В режиме тренировки просто уведомляем о уничтожении самолета
                                if (plane.isDestroyed && plane == planes.firstOrNull()) {
                                    gameView.setGameOver(true)
                                }
                            } else {
                                // В мультиплеере увеличиваем счетчик убийств, если уничтожен вражеский самолет
                                if (plane.isDestroyed && plane != planes.firstOrNull()) {
                                    gameView.increaseKillCount()
                                } else if (plane.isDestroyed && plane == planes.firstOrNull()) {
                                    // Если уничтожен самолет игрока, завершаем игру
                                    gameView.setGameOver(true)
                                }
                            }
                            
                            break
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking collision with plane: ${e.message}")
                    }
                }
                
                // Проверяем столкновения пуль с мишенями (только в режиме тренировки)
                if (!bulletsToRemove.contains(bullet) && gameType == GameType.TRAINING) {
                    for (target in targets) {
                        if (Target.checkCollision(bullet, target)) {
                            bulletsToRemove.add(bullet)
                            
                            // Создаем взрыв
                            val explosion = Explosion.createExplosion(
                                bullet.position.x,
                                bullet.position.y,
                                Explosion.ExplosionSize.SMALL
                            )
                            gameView.addExplosion(explosion)
                            
                            // Наносим урон мишени
                            target.hit()
                            
                            break
                        }
                    }
                }
                
                // Проверяем столкновение пули с землей
                if (bullet.position.y > groundHeight) {
                    bulletsToRemove.add(bullet)
                    
                    // Создаем маленький взрыв
                    val explosion = Explosion.createExplosion(
                        bullet.position.x,
                        groundHeight,
                        Explosion.ExplosionSize.SMALL
                    )
                    gameView.addExplosion(explosion)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkCollisions: ${e.message}")
        }
        
        return bulletsToRemove
    }
    
    /**
     * Проверяет столкновения самолетов с землей
     * @param planes список самолетов
     * @param groundHeight высота земли
     */
    fun checkPlanesGroundCollisions(planes: List<Plane>, groundHeight: Float) {
        for (plane in planes) {
            try {
                val position = plane.position
                
                // Проверяем столкновение с землей
                if (position.y + plane.height / 2 >= groundHeight) {
                    // Самолет столкнулся с землей
                    if (!plane.isDestroyed) {
                        // Создаем взрыв
                        val explosion = Explosion.createExplosion(
                            position.x,
                            groundHeight,
                            Explosion.ExplosionSize.LARGE
                        )
                        gameView.addExplosion(explosion)
                        
                        // Катапультируем пилота, если он еще в самолете
                        if (plane.pilot != null) {
                            try {
                                gameView.ejectPilot(plane)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error ejecting pilot on ground collision: ${e.message}")
                            }
                        }
                        
                        // Отмечаем самолет как уничтоженный
                        plane.isDestroyed = true
                        
                        // Уведомляем о потере жизни или завершаем игру
                        if (plane == planes.firstOrNull()) {
                            // Завершаем игру, если это самолет игрока
                            gameView.setGameOver(true)
                        }
                    } else {
                        // Если самолет уже уничтожен, просто удерживаем его на уровне земли
                        plane.position.y = groundHeight - plane.height / 2
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking plane ground collision: ${e.message}")
            }
        }
    }

    /**
     * Проверяет столкновения между пулями и самолетами
     * @param bullets список пуль
     * @param planes список самолетов
     * @param onCollision функция обратного вызова, вызываемая при столкновении
     */
    fun checkBulletPlaneCollisions(
        bullets: List<Bullet>,
        planes: List<Plane>,
        onCollision: (Bullet, Plane) -> Unit
    ) {
        try {
            for (bullet in bullets) {
                for (plane in planes) {
                    // Пропускаем проверку столкновения пули с самолетом, который ее выпустил
                    if (bullet.color == plane.color) continue
                    
                    // Проверяем столкновение
                    if (checkCollision(bullet, plane)) {
                        onCollision(bullet, plane)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkBulletPlaneCollisions: ${e.message}")
        }
    }
    
    /**
     * Проверяет столкновения между пулями и целями
     * @param bullets список пуль
     * @param targets список целей
     * @param onCollision функция обратного вызова, вызываемая при столкновении
     */
    fun checkBulletTargetCollisions(
        bullets: List<Bullet>,
        targets: List<Target>,
        onCollision: (Bullet, Target) -> Unit
    ) {
        try {
            for (bullet in bullets) {
                for (target in targets) {
                    // Проверяем столкновение
                    if (checkCollision(bullet, target)) {
                        onCollision(bullet, target)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkBulletTargetCollisions: ${e.message}")
        }
    }
    
    /**
     * Проверяет столкновение между двумя самолетами
     * @param plane1 первый самолет
     * @param plane2 второй самолет
     * @param onCollision функция обратного вызова, вызываемая при столкновении
     */
    fun checkPlanePlaneCollision(
        plane1: Plane,
        plane2: Plane,
        onCollision: () -> Unit
    ) {
        try {
            if (checkCollision(plane1, plane2)) {
                onCollision()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkPlanePlaneCollision: ${e.message}")
        }
    }
    
    /**
     * Проверяет столкновение между пулей и самолетом
     * @param bullet пуля
     * @param plane самолет
     * @return true, если произошло столкновение
     */
    private fun checkCollision(bullet: Bullet, plane: Plane): Boolean {
        val bulletX = bullet.position.x
        val bulletY = bullet.position.y
        val planeX = plane.position.x
        val planeY = plane.position.y
        
        // Простая проверка столкновения по прямоугольникам
        return bulletX >= planeX && 
               bulletX <= planeX + plane.width &&
               bulletY >= planeY && 
               bulletY <= planeY + plane.height
    }
    
    /**
     * Проверяет столкновение между пулей и целью
     * @param bullet пуля
     * @param target цель
     * @return true, если произошло столкновение
     */
    private fun checkCollision(bullet: Bullet, target: Target): Boolean {
        val bulletX = bullet.position.x
        val bulletY = bullet.position.y
        val targetX = target.position.x
        val targetY = target.position.y
        
        // Простая проверка столкновения по прямоугольникам
        return bulletX >= targetX && 
               bulletX <= targetX + target.width &&
               bulletY >= targetY && 
               bulletY <= targetY + target.height
    }
    
    /**
     * Проверяет столкновение между двумя самолетами
     * @param plane1 первый самолет
     * @param plane2 второй самолет
     * @return true, если произошло столкновение
     */
    private fun checkCollision(plane1: Plane, plane2: Plane): Boolean {
        val x1 = plane1.position.x
        val y1 = plane1.position.y
        val w1 = plane1.width
        val h1 = plane1.height
        
        val x2 = plane2.position.x
        val y2 = plane2.position.y
        val w2 = plane2.width
        val h2 = plane2.height
        
        // Проверка столкновения по прямоугольникам
        return x1 < x2 + w2 &&
               x1 + w1 > x2 &&
               y1 < y2 + h2 &&
               y1 + h1 > y2
    }

    /**
     * Проверяет и корректирует позицию самолета в режиме тренировки
     * @param plane самолет для проверки
     * @param screenWidth ширина экрана
     * @param screenHeight высота экрана
     * @param groundHeight высота земли
     * @param gameStartTime время начала игры
     */
    fun checkAndCorrectTrainingPlanePosition(
        plane: Plane,
        screenWidth: Float,
        screenHeight: Float,
        groundHeight: Float,
        gameStartTime: Long
    ) {
        try {
            if (!plane.isDestroyed) {
                // Безопасная высота - 30% от высоты экрана от земли
                val safeHeight = groundHeight - screenHeight * 0.3f
                
                // Если самолет ниже безопасной высоты и прошло менее 3 секунд с начала игры
                if (plane.position.y >= safeHeight && System.nanoTime() - gameStartTime < 3_000_000_000L) {
                    // Поднимаем самолет выше от земли
                    plane.position.y = screenHeight * 0.1f
                    
                    // Устанавливаем горизонтальную скорость с небольшим подъемом
                    plane.velocity.x = 8.0f
                    plane.velocity.y = -1.0f
                    
                    // Устанавливаем небольшой угол вверх
                    plane.rotation = -5f
                    
                    Log.d(TAG, "Emergency adjustment of plane position to prevent crash: (${plane.position.x}, ${plane.position.y})")
                }
                
                // Дополнительная защита - если самолет летит вниз слишком быстро, корректируем
                if (plane.velocity.y > 5.0f) {
                    plane.velocity.y = 5.0f
                    // Добавляем подъемную силу
                    plane.velocity.y -= 2.0f
                    Log.d(TAG, "Corrected excessive downward velocity")
                }
                
                // Если самолет слишком близко к земле, добавляем подъемную силу
                val distanceToGround = groundHeight - plane.position.y
                if (distanceToGround < screenHeight * 0.2f && plane.velocity.y > 0) {
                    // Чем ближе к земле, тем сильнее корректируем
                    val correctionFactor = 1.0f - (distanceToGround / (screenHeight * 0.2f))
                    plane.velocity.y -= 3.0f * correctionFactor
                    Log.d(TAG, "Added lift force near ground, correction factor: $correctionFactor")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkAndCorrectTrainingPlanePosition: ${e.message}")
        }
    }
} 