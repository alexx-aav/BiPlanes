package com.example.biplanes.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Path
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.biplanes.game.models.GameType
import com.example.biplanes.game.models.Vector2D
import com.example.biplanes.game.models.objects.Bullet
import com.example.biplanes.game.models.objects.Pilot
import com.example.biplanes.game.models.objects.Plane
import com.example.biplanes.game.models.objects.Target
import com.example.biplanes.game.models.objects.House
import com.example.biplanes.game.models.objects.Explosion
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.Log
import android.util.AttributeSet
import com.example.biplanes.game.models.PlaneColor
import kotlin.random.Random
import kotlin.math.pow
import android.os.Handler
import android.os.Looper
import com.example.biplanes.game.models.Background
import com.example.biplanes.game.models.CollisionManager
import com.example.biplanes.game.models.Player

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {
    private var gameThread: Thread? = null
    private val paint = Paint()
    private var gameType: GameType = GameType.TRAINING
    private var isHost: Boolean = false
    private val planes = mutableListOf<Plane>()
    private val bullets = mutableListOf<Bullet>()
    private val pilots = mutableListOf<Pilot>()
    private val targets = mutableListOf<Target>()
    private val explosions = mutableListOf<Explosion>()
    private var house: House? = null
    private var groundHeight = 0f
    private var isRunning = false
    
    // Размеры самолета
    private var planeWidth: Float = 84f
    private var planeHeight: Float = 42f
    
    // Новые классы для управления игрой
    private var background: Background? = null
    private var collisionManager: CollisionManager? = null
    
    private var pendingInitGame = false
    private var pendingGameType: GameType? = null
    private var pendingIsHost: Boolean = false
    private var pendingPlaneColor: PlaneColor? = null
    private var isGameOver: Boolean = false
    
    // Переменные для игровой статистики
    private var killCount: Int = 0
    
    // Переменные для управления игрой
    private var planeColor: PlaneColor = PlaneColor.RED
    private var isGameStarted: Boolean = false
    private var isGamePaused: Boolean = false
    
    // Переменные для отслеживания времени
    private var lastFrameTime: Long = 0
    private var deltaTime: Float = 0f
    
    // Переменные для стрельбы и катапультирования
    private var lastFireTime: Long = 0
    private val fireDelay: Long = 300
    private var lastEjectTime: Long = 0
    private val ejectDelay: Long = 1000
    
    // Переменные для размеров самолета
    private var initialPlaneWidth: Float = 0f
    private var initialPlaneHeight: Float = 0f
    
    // Константа для логирования
    private val TAG = "GameView"
    
    // Идентификатор игрока
    private var playerId: String = ""
    
    // Дополнительные параметры
    private var planesToRemove = mutableListOf<Plane>()
    private var planesToEject = mutableListOf<Plane>()
    private var bulletCount = 0
    private var gameState = GameState.WAITING
    private var players = mutableListOf<Player>()
    
    // Параметры отображения
    private val screenWidth: Float
        get() = width.toFloat()
    private val screenHeight: Float
        get() = height.toFloat()
    
    // Состояния игры
    enum class GameState {
        WAITING,    // Ожидание начала игры
        RUNNING,    // Игра запущена
        PAUSED,     // Игра на паузе
        GAME_OVER   // Игра окончена
    }
    
    // Интерфейс для обратной связи с GameActivity
    interface GameEventListener {
        fun onScoreChanged(newScore: Int)
        fun onGameOver()
    }
    
    private var gameEventListener: GameEventListener? = null

    // Константы для режимов игры
    companion object {
        const val MULTIPLAYER = "MULTIPLAYER"
    }
    
    init {
        Log.d(TAG, "GameView initialized")
        holder.addCallback(this)
        paint.isAntiAlias = true
    }
    
    // Метод для установки слушателя событий
    fun setGameEventListener(listener: GameEventListener) {
        gameEventListener = listener
    }
    
    /**
     * Инициализирует игру с заданными параметрами
     */
    fun initialize(gameType: GameType, isHost: Boolean, planeColor: PlaneColor, playerId: String = "") {
        try {
            // Сохраняем параметры
            this.gameType = gameType
            this.isHost = isHost
            this.planeColor = planeColor
            this.playerId = playerId
            
            Log.d(TAG, "Инициализация игры: gameType=$gameType, isHost=$isHost, planeColor=$planeColor, playerId=$playerId")
            
            // Очищаем все списки
            planes.clear()
            pilots.clear()
            targets.clear()
            Log.d(TAG, "Списки очищены: planes=${planes.size}, pilots=${pilots.size}, targets=${targets.size}")
            
            // Проверяем, что у нас есть размеры экрана и инициализирован фон
            if (width == 0 || height == 0 || background == null) {
                Log.d(TAG, "Размеры экрана или фон не установлены, откладываем создание самолета")
                return
            }
            
            // Создаем самолет игрока
            initPlayerPlane()
            
            // Создаем самолеты противников
            if (gameType != GameType.TRAINING) {
                createEnemyPlanes()
            }
            
            // Создаем цели
            createTargets()
            
            // Запускаем игровой цикл
            startGameLoop()
            
            Log.d(TAG, "GameView инициализирован")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при инициализации GameView: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Создает цели для режима тренировки
     */
    private fun createTargets() {
        try {
            // Очищаем существующие мишени
            targets.clear()
            
            // Создаем мишени используя существующий метод из класса Target
            targets.addAll(Target.createTargets(5, width.toFloat(), height.toFloat(), groundHeight))
            
            Log.d(TAG, "Создано мишеней: ${targets.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при создании мишеней: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Создает самолет игрока при инициализации игры
     */
    private fun initPlayerPlane() {
        createPlayerPlane(
            relativeHeight = 1/3f,
            initialVelocity = Vector2D(5.0f, 0.0f),
            addToStart = false
        )
    }
    
    /**
     * Создает самолет игрока с заданными параметрами
     * @param relativeHeight Относительная высота самолета (от высоты экрана)
     * @param initialVelocity Начальная скорость самолета
     * @param addToStart Добавлять самолет в начало списка (иначе в конец)
     */
    private fun createPlayerPlane(
        relativeHeight: Float = 0.3f,
        initialVelocity: Vector2D = Vector2D(5.0f, 0.0f),
        addToStart: Boolean = false
    ) {
        try {
            Log.d(TAG, "Создание самолёта игрока: relativeHeight=$relativeHeight, initialVelocity=$initialVelocity, addToStart=$addToStart")
            
            // Проверяем, что размеры экрана установлены
            if (width == 0 || height == 0) {
                Log.d(TAG, "Размеры экрана не установлены, откладываем создание самолета")
                return
            }
            
            // Вычисляем позицию
            val screenWidth = width.toFloat()
            val screenHeight = height.toFloat()
            val startX = screenWidth / 2f
            val startY = screenHeight * relativeHeight
            
            // Создаем самолет
            val plane = Plane(
                position = Vector2D(startX, startY),
                width = planeWidth,
                height = planeHeight,
                color = planeColor.color,
                isPlayer = true,
                maxSpeed = 12f
            )
            
            // Устанавливаем начальную скорость и направление
            plane.velocity = initialVelocity
            plane.rotation = 0f
            
            // Устанавливаем режим тренировки для самолета
            if (gameType == GameType.TRAINING) {
                plane.setTrainingMode(true)
            }
            
            // Создаем пилота с тем же цветом, что и самолет
            val pilot = Pilot(planeColor.color, planeWidth / 4)
            plane.assignPilot(pilot)
            
            // Добавляем самолет в список
            if (addToStart) {
                planes.add(0, plane)
            } else {
                planes.add(plane)
            }
            
            Log.d(TAG, "Самолёт игрока создан на позиции (${startX}, ${startY}) с размерами ${planeWidth}x${planeHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при создании самолёта игрока: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Создает новый самолет игрока во время игры
     */
    fun createNewPlayerPlane() {
        createPlayerPlane(
            relativeHeight = 0.3f,
            initialVelocity = Vector2D(10.0f, -2.0f),
            addToStart = true
        )
    }

    private fun createEnemyPlane() {
        val position = Vector2D(Random.nextFloat() * width, 100f)
        val plane = Plane(
            position = position,
            width = width / 21f,
            height = width / 21f,
            color = Color.RED,
            isPlayer = false,
            maxSpeed = 8f
        )
        planes.add(plane)
        Log.d(TAG, "Создан вражеский самолет: $plane")
    }

    private fun createRemotePlane(id: String, position: Vector2D) {
        // Определяем цвет для удаленного самолета
        val color = when {
            playerId == id -> planeColor.color  // Если это самолет игрока
            else -> Color.RED  // Для других игроков пока используем красный
        }
        
        // Создаем самолет с помощью универсального метода
        val plane = createPlane(position.x, position.y, color, id)
        
        // Добавляем самолет в список
        planes.add(plane)
        
        Log.d(TAG, "Создан удаленный самолет с ID $id на позиции (${position.x}, ${position.y})")
    }
    
    // Метод для управления самолетом игрока с помощью джойстика и кнопок
    fun controlPlayerPlane(joystickX: Float, joystickY: Float, isFiring: Boolean, isEjecting: Boolean) {
        try {
            if (planes.isEmpty()) {
                Log.w(TAG, "controlPlayerPlane called but planes list is empty")
                return
            }
            
            val playerPlane = planes[0]
            
            if (playerPlane.isDestroyed) {
                Log.w(TAG, "Player plane is destroyed, cannot control")
                return
            }
            
            // Применяем мертвую зону для джойстика
            val deadzone = 0.15f
            
            // Обрабатываем ввод джойстика
            var adjustedX = if (Math.abs(joystickX) < deadzone) 0f else {
                val sign = if (joystickX >= 0) 1f else -1f
                sign * ((Math.abs(joystickX) - deadzone) / (1f - deadzone)).pow(1.5f).toFloat()
            }
            
            var adjustedY = if (Math.abs(joystickY) < deadzone) 0f else {
                val sign = if (joystickY >= 0) 1f else -1f
                sign * ((Math.abs(joystickY) - deadzone) / (1f - deadzone)).pow(1.5f).toFloat()
            }
            
            // Ограничиваем значения
            adjustedX = adjustedX.coerceIn(-1f, 1f)
            adjustedY = adjustedY.coerceIn(-1f, 1f)
            
            // Используем новый метод steer для реалистичного управления самолетом
            playerPlane.steer(adjustedX, adjustedY)
            
            // Обрабатываем стрельбу
            if (isFiring) {
                fireBullet(playerPlane)
            }
            
            // Обрабатываем катапультирование
            if (isEjecting) {
                ejectPilot(playerPlane)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в методе controlPlayerPlane: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // Метод для стрельбы из самолета
    fun fireBullet(plane: Plane) {
        // Проверяем, прошло ли достаточно времени с последнего выстрела
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFireTime < fireDelay) {
            return
        }
        
        // Обновляем время последнего выстрела
        lastFireTime = currentTime
        
        try {
            // Вычисляем позицию и направление пули
            val planeDirection = Math.toRadians(plane.rotation.toDouble())
            val bulletX = plane.position.x + Math.cos(planeDirection) * plane.width / 2
            val bulletY = plane.position.y + Math.sin(planeDirection) * plane.height / 2
            
            // Создаем пулю с увеличенной скоростью
            val bullet = Bullet(
                Vector2D(bulletX.toFloat(), bulletY.toFloat()),
                Vector2D(
                    Math.cos(planeDirection).toFloat() * 25f,  // Увеличиваем скорость с 15f до 25f
                    Math.sin(planeDirection).toFloat() * 25f   // Увеличиваем скорость с 15f до 25f
                ),
                plane.color,
                5f
            )
            
            // Добавляем пулю в список
            bullets.add(bullet)
            
            // Логируем информацию о выстреле
            Log.d(TAG, "Bullet fired from plane at (${plane.position.x}, ${plane.position.y}) with rotation ${plane.rotation}")
        } catch (e: Exception) {
            Log.e(TAG, "Error firing bullet: ${e.message}")
        }
    }
    
    /**
     * Катапультирует пилота из самолета
     */
    fun ejectPilot(plane: Plane) {
        try {
            Log.d(TAG, "Вызван метод ejectPilot для самолета на позиции (${plane.position.x}, ${plane.position.y})")
            
            // Проверяем, не уничтожен ли самолет и есть ли в нем пилот
            if (plane.isDestroyed || !plane.hasPilot) {
                Log.d(TAG, "Нельзя катапультировать пилота: самолет уничтожен=${plane.isDestroyed}, есть пилот=${plane.hasPilot}")
                return
            }
            
            // Получаем пилота из самолета (используем pilot или getPilotObject, что доступно)
            val pilot = plane.pilot ?: plane.getPilotObject() ?: return
            
            // Получаем текущую позицию самолета
            val planePosition = plane.position.copy()

            // Устанавливаем начальную позицию пилота точно в позицию самолета
            val pilotX = planePosition.x + plane.width / 2
            val pilotY = planePosition.y + plane.height / 2
            
            // Устанавливаем параметры окружения для пилота
            pilot.screenWidth = width.toFloat()
            pilot.screenHeight = height.toFloat()
            pilot.groundLevel = groundHeight
            
            // Устанавливаем начальную скорость пилота
            pilot.velocity = Vector2D(plane.velocity.x * 0.5f, plane.velocity.y * 0.5f)
            
            // Удаляем пилота из самолета - это активирует внутреннюю логику падения в классе Plane
            plane.ejectPilot() // Это изменит физику самолета внутри класса Plane
            
            // Добавляем пилота в список пилотов
            pilots.add(pilot)
            
            // Устанавливаем ссылку на дом, если еще не установлена
            if (pilot.house == null && house != null) {
                pilot.assignHouse(house!!)
            }
            
            // Катапультируем пилота с правильной позицией
            pilot.eject(pilotX, pilotY)
            
            Log.d(TAG, "Пилот катапультирован на позиции (${pilotX}, ${pilotY})")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при катапультировании пилота: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // Методы для управления игровым потоком
    fun pause() {
        isRunning = false
        var retry = true
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (e: InterruptedException) {
                Log.e(TAG, "Error pausing game thread: ${e.message}", e)
            }
        }
        gameThread = null
    }
    
    fun resume() {
        if (gameThread == null) {
            isRunning = true
            gameThread = Thread {
                while (isRunning) {
                    update()
                    try {
                        holder.lockCanvas()?.let { canvas ->
                            draw(canvas)
                            holder.unlockCanvasAndPost(canvas)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in game loop: ${e.message}")
                    }
                    Thread.sleep(16) // ~60 FPS
                }
            }
            gameThread?.start()
        }
    }
    
    fun restart() {
        // Вызываем метод перезапуска игры
        restartGame()
    }
    
    // Метод для обновления состояния игры (вызывается из GameThread)
    fun update() {
        try {
            // Вычисляем deltaTime для плавного движения
            val currentTime = System.nanoTime()
            deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f
            lastFrameTime = currentTime
            
            // Ограничиваем deltaTime, чтобы избежать скачков при низком FPS
            if (deltaTime > 0.1f) deltaTime = 0.1f
            
            // Если игра окончена, обновляем только фон и не трогаем игровые объекты
            if (isGameOver) {
                background?.update(deltaTime)
                return
            }
            
            // Обновляем фон
            background?.update(deltaTime)
            
            // Обновляем дом
            house?.update(deltaTime)
            
            // Проверяем, нужно ли пересоздать самолет игрока
            respawnPlayerIfNeeded()
            
            // Проверяем, что у нас есть самолеты для обновления
            if (planes.isEmpty() && gameType == GameType.TRAINING) {
                Log.d(TAG, "Нет самолетов для обновления, создаем новый самолет")
                createNewPlayerPlane()
                return
            }
            
            // Обновляем самолеты
            updatePlanes()
            
            // Обновляем пули
            updateBullets()
            
            // Обновляем пилотов
            updatePilots()
            
            // Обновляем мишени
            updateTargets()
            
            // Обновляем взрывы
            updateExplosions()
            
            // Проверяем коллизии
            checkCollisions()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в методе update: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // Метод для обновления самолетов
    private fun updatePlanes() {
        try {
            val planesToRemove = mutableListOf<Plane>()
            val planesToEject = mutableListOf<Plane>()
            
            for (plane in planes) {
                try {
                    // Обновляем самолет
                    updatePlane(plane, deltaTime)
                    
                    // Проверяем, не вышел ли самолет за пределы экрана
                    if (!plane.isDestroyed) {
                        val position = plane.position
                        
                        // Обрабатываем перемещение самолета с одной стороны экрана на другую
                        // Если самолет вылетел за левую границу, перемещаем его на правую
                        if (position.x < -plane.width) {
                            plane.position.x = width + plane.width / 2
                            Log.d(TAG, "Самолет переместился с левой границы на правую: (${plane.position.x}, ${plane.position.y})")
                        } 
                        // Если самолет вылетел за правую границу, перемещаем его на левую
                        else if (position.x > width + plane.width) {
                            plane.position.x = -plane.width / 2
                            Log.d(TAG, "Самолет переместился с правой границы на левую: (${plane.position.x}, ${plane.position.y})")
                        }
                        
                        // Проверяем верхнюю и нижнюю границы
                        val isOutOfVerticalBounds = position.y < -plane.height || position.y > height + plane.height
                        
                        // Проверяем столкновение с землей
                        if (position.y + plane.height / 2 >= groundHeight) {
                            // Самолет столкнулся с землей
                            if (!plane.isDestroyed) {
                                // Создаем взрыв
                                val explosion = Explosion(position.x, groundHeight, 150f)
                                explosions.add(explosion)
                                
                                // ВАЖНО: Если пилот в самолете - НЕ катапультируем его, а завершаем игру
                                if (plane.hasPilot) {
                                    // Отмечаем самолет как уничтоженный с пилотом внутри
                                    plane.isDestroyed = true
                                    
                                    // Завершаем игру, так как пилот разбился вместе с самолетом
                                    if (plane.isPlayer) {
                                        Log.d(TAG, "Самолет игрока разбился с пилотом внутри! Игра окончена.")
                                        isGameOver = true
                                        gameEventListener?.onGameOver()
                                    }
                                } else {
                                    // Пилота нет в самолете, просто разбиваем пустой самолет
                                    plane.isDestroyed = true
                                }
                                
                                // Уменьшаем скорость падения для более реалистичного поведения
                                plane.velocity.multiply(0.5f)
                                
                                // Добавляем случайное вращение для эффекта крушения
                                plane.rotation += Random.nextFloat() * 30f - 15f
                                
                                Log.d(TAG, "Plane crashed into ground at position (${position.x}, ${position.y})")
                            } else {
                                // Если самолет уже уничтожен, просто удерживаем его на уровне земли
                                plane.position.y = groundHeight - plane.height / 2
                            }
                        }
                        
                        // Обрабатываем выход за вертикальные пределы экрана
                        if (isOutOfVerticalBounds) {
                            if (!plane.isOutOfScreen) {
                                plane.isOutOfScreen = true
                                plane.outOfScreenTime = 0f
                            } else {
                                plane.outOfScreenTime += deltaTime
                                
                                // Если самолет слишком долго за пределами экрана, катапультируем пилота
                                if (plane.outOfScreenTime > plane.maxOutOfScreenTime) {
                                    planesToEject.add(plane)
                                }
                            }
                        } else {
                            plane.isOutOfScreen = false
                            plane.outOfScreenTime = 0f
                        }
                    } else {
                        // Если самолет уже уничтожен, проверяем, не достиг ли он земли
                        if (plane.position.y + plane.height / 2 >= groundHeight) {
                            // Удерживаем уничтоженный самолет на уровне земли
                            plane.position.y = groundHeight - plane.height / 2
                            
                            // Останавливаем движение по вертикали
                            plane.velocity.y = 0f
                            
                            // Замедляем движение по горизонтали для имитации трения о землю
                            plane.velocity.x *= 0.95f
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating plane: ${e.message}")
                    plane.isDestroyed = true
                    planesToRemove.add(plane)
                }
            }
            
            // Удаляем разрушенные самолеты
            planes.removeAll(planesToRemove)
            
            // Катапультируем пилотов из самолетов, которые слишком долго были за пределами экрана
            for (plane in planesToEject) {
                if (!plane.isDestroyed && plane.getPilotObject() != null) {
                    ejectPilot(plane)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating planes: ${e.message}")
        }
    }
    
    private fun updatePlane(plane: Plane, deltaTime: Float) {
        // Обновляем самолет с помощью его собственного метода update
        plane.update(deltaTime)
        
        // Проверяем границы экрана
        handleScreenBoundaries(plane)
    }
    
    private fun handleScreenBoundaries(plane: Plane) {
        // Обрабатываем перемещение самолета с одной стороны экрана на другую
        // Если самолет вылетел за левую границу, перемещаем его на правую
        if (plane.position.x < -plane.width) {
            plane.position.x = width + plane.width / 2
            Log.d(TAG, "Самолет переместился с левой границы на правую: (${plane.position.x}, ${plane.position.y})")
        } 
        // Если самолет вылетел за правую границу, перемещаем его на левую
        else if (plane.position.x > width + plane.width) {
            plane.position.x = -plane.width / 2
            Log.d(TAG, "Самолет переместился с правой границы на левую: (${plane.position.x}, ${plane.position.y})")
        }
        
        // Проверяем верхнюю и нижнюю границы
        val isOutOfVerticalBounds = plane.position.y < -plane.height || plane.position.y > height + plane.height
        
        // Обрабатываем выход за вертикальные пределы экрана
        if (isOutOfVerticalBounds) {
            if (!plane.isOutOfScreen) {
                plane.isOutOfScreen = true
                plane.outOfScreenTime = 0f
            }
        } else {
            plane.isOutOfScreen = false
            plane.outOfScreenTime = 0f
        }
    }
    
    // Метод для обновления пуль
    private fun updateBullets() {
        try {
            val bulletsToRemove = mutableListOf<Bullet>()
            for (bullet in bullets) {
                bullet.update(deltaTime)
                
                // Проверяем, не вышла ли пуля за пределы экрана
                if (bullet.position.x < 0 || bullet.position.x > width) {
                    bulletsToRemove.add(bullet)
                } else if (bullet.position.y < 0) {
                    bulletsToRemove.add(bullet)
                } else if (bullet.position.y > groundHeight) {
                    // Пуля попала в землю - создаем маленький взрыв
                    explosions.add(Explosion(bullet.position.x, groundHeight, 50f))
                    bulletsToRemove.add(bullet)
                }
            }
            bullets.removeAll(bulletsToRemove)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating bullets: ${e.message}")
        }
    }
    
    // Метод для обновления пилотов
    private fun updatePilots() {
        try {
            // В режиме тренировки удаляем пилотов, которые улетели за пределы экрана
            if (gameType == GameType.TRAINING) {
                pilots.removeAll { pilot ->
                    val isOutOfBounds = pilot.position.y < -500 || 
                                       pilot.position.y > height + 500 ||
                                       pilot.position.x < -500 || 
                                       pilot.position.x > width + 500
                    if (isOutOfBounds) {
                        Log.d(TAG, "Removing pilot that flew out of bounds at (${pilot.position.x}, ${pilot.position.y})")
                    }
                    isOutOfBounds
                }
            }
            
            // Обновляем каждого катапультированного пилота из списка pilots
            for (pilot in pilots) {
                // Если пилот катапультирован и не спасен
                if (pilot.isEjected && !pilot.isRescued) {
                    // Устанавливаем ссылку на дом, если еще не установлена
                    if (pilot.house == null && house != null) {
                        pilot.assignHouse(house!!)
                    }
                    
                    // Обновляем пилота
                    pilot.update(deltaTime)
                    Log.d(TAG, "Обновлен пилот: позиция=(${pilot.position.x}, ${pilot.position.y}), " +
                              "скорость=(${pilot.velocity.x}, ${pilot.velocity.y}), " +
                              "состояние=${pilot.state}, onGround=${pilot.isOnGround}")
                    
                    // ВАЖНО: Проверяем, не вылетел ли пилот за границы экрана
                    // Ограничиваем положение пилота экраном + небольшой запас
                    if (pilot.position.y > height - 50) {
                        pilot.position.y = height.toFloat() - 50f
                        pilot.velocity.y = 0f  // Останавливаем движение вниз
                        
                        // Если пилот достиг нижней границы экрана, принудительно приземляем его
                        if (!pilot.isOnGround) {
                            pilot.onGroundContact(height.toFloat() - 50f)
                            Log.d(TAG, "Пилот принудительно приземлен на границе экрана")
                        }
                    }
                    
                    // Проверяем, достиг ли пилот земли
                    if (pilot.position.y >= groundHeight - 20f && !pilot.isOnGround) {
                        // Вызываем метод обработки контакта с землей
                        pilot.onGroundContact(groundHeight - 20f)
                    }
                    
                    // Если пилот на земле, проверяем, не достиг ли он дома
                    if (pilot.isOnGround || pilot.state == Pilot.State.RUNNING) {
                        // Если пилот достиг дома, он спасен
                        house?.let { safeHouse ->
                            if (safeHouse.checkPilotRescue(pilot)) {
                                pilot.rescue()
                                
                                // Удаляем код создания самолёта, так как этим занимается метод respawnPlayerIfNeeded()
                                Log.d(TAG, "Пилот спасён! Самолёт будет создан в методе respawnPlayerIfNeeded")
                            }
                        }
                        
                        // Если пилот на земле, полностью гасим вертикальную скорость
                        if (pilot.isOnGround) {
                            pilot.velocity.y = 0f
                            pilot.position.y = groundHeight - 20f
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating pilots: ${e.message}")
        }
    }
    
    // Метод для обновления мишеней
    private fun updateTargets() {
        try {
            // В мультиплеере нет мишеней
            if (gameType != GameType.TRAINING) {
                return
            }
            
            // Используем статический метод Target.updateTargets для обновления мишеней
            val destroyedTargets = Target.updateTargets(targets)
            
            // Создаем взрывы для уничтоженных мишеней
            for (target in destroyedTargets) {
                explosions.add(Explosion(target.position.x, target.position.y, 100f))
            }
            
            // Удаляем уничтоженные мишени
            targets.removeAll(destroyedTargets)
            
            // Если все мишени уничтожены в режиме тренировки, создаем новые
            if (targets.isEmpty()) {
                createTargets()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating targets: ${e.message}")
        }
    }
    
    // Метод для обновления взрывов
    private fun updateExplosions() {
        try {
            val explosionsToRemove = mutableListOf<Explosion>()
            for (explosion in explosions) {
                explosion.update()
                if (explosion.isFinished()) {
                    explosionsToRemove.add(explosion)
                }
            }
            explosions.removeAll(explosionsToRemove)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating explosions: ${e.message}")
        }
    }
    
    // Метод для пересоздания самолета игрока в режиме тренировки
    private fun respawnPlayerIfNeeded() {
        try {
            // Проверяем, есть ли катапультированные пилоты, которые достигли дома
            val pilotRescued = pilots.any { pilot -> 
                pilot.isEjected && pilot.isRescued && pilot.state == Pilot.State.RESCUED
            }
            
            // Если пилот спасен, создаем новый самолет игрока
            if (pilotRescued) {
                // Находим спасенного пилота
                val rescuedPilot = pilots.find { it.isEjected && it.isRescued }
                
                if (rescuedPilot != null) {
                    Log.d(TAG, "Пилот спасен, создаем новый самолет игрока")
                    
                    // Удаляем все самолеты игрока, которые могли остаться
                    planes.removeAll { it.isPlayer }
                    
                    // Отсоединяем пилота от всех самолетов
                    planes.forEach { plane ->
                        if (plane.getPilotObject() == rescuedPilot) {
                            plane.ejectPilot()
                        }
                    }
                    
                    // Создаем новый самолет с помощью универсального метода
                    // Сохраняем ссылку на созданный самолет
                    var playerPlane: Plane? = null
                    val originalColor = planeColor
                    
                    try {
                        // Временно изменяем цвет на цвет спасенного пилота
                        // Это нужно, так как createPlayerPlane использует planeColor для задания цвета
                        val pilotColorInt = rescuedPilot.color
                        // Находим соответствующий PlaneColor по значению цвета
                        val pilotPlaneColor = PlaneColor.values().find { it.color == pilotColorInt } ?: planeColor
                        
                        // Временно устанавливаем цвет самолета
                        this.planeColor = pilotPlaneColor
                        
                        // Создаем самолет и получаем ссылку на него (последний добавленный в список)
                        createPlayerPlane(relativeHeight = 0.4f, initialVelocity = Vector2D(5.0f, 0.0f), addToStart = true)
                        playerPlane = planes.firstOrNull()
                    } finally {
                        // Восстанавливаем оригинальный цвет
                        this.planeColor = originalColor
                    }
                    
                    // Если самолет создан успешно, назначаем пилота
                    if (playerPlane != null) {
                        // Устанавливаем пилота в самолет
                        playerPlane.assignPilot(rescuedPilot)
                        
                        // Устанавливаем режим тренировки для самолета
                        if (gameType == GameType.TRAINING) {
                            playerPlane.setTrainingMode(true)
                        }
                        
                        // Сбрасываем флаг катапультирования и спасения
                        rescuedPilot.isEjected = false
                        rescuedPilot.isRescued = false
                        rescuedPilot.state = Pilot.State.IN_PLANE
                        
                        // Удаляем пилота из списка отдельных пилотов
                        pilots.remove(rescuedPilot)
                        
                        Log.d(TAG, "Новый самолет игрока создан на позиции (${playerPlane.position.x}, ${playerPlane.position.y})")
                    }
                }
            }
            
            // Проверяем, что игра в режиме тренировки и самолет игрока уничтожен
            if (gameType == GameType.TRAINING && (planes.isEmpty() || !planes.any { it.isPlayer })) {
                Log.d(TAG, "Самолёт игрока отсутствует, проверяем возможность респауна")
                
                // Если все пилоты погибли (нет активных пилотов) И размеры экрана установлены, создаем новый самолет
                if ((pilots.isEmpty() || !pilots.any { !it.isRescued }) && width > 0 && height > 0 && planes.isEmpty()) {
                    createNewPlayerPlane()
                    
                    // Сбрасываем флаг окончания игры, если он был установлен
                    isGameOver = false
                    
                    Log.d(TAG, "Новый самолет создан")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error respawning player: ${e.message}")
        }
    }
    
    // Метод для отрисовки (вызывается из GameThread)
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        
        try {
            // Проверяем размеры экрана
            if (width <= 0 || height <= 0) {
                Log.e(TAG, "Ошибка: Недопустимые размеры экрана: width=$width, height=$height")
                return
            }
            
            // Очищаем экран
            canvas.drawColor(Color.BLACK)
            
            // Проверяем, что фон инициализирован
            if (background == null) {
                Log.d(TAG, "Background is null, initializing with width=$width, height=$height")
                background = Background(width.toFloat(), height.toFloat())
                groundHeight = background?.getGroundHeight() ?: (height * 0.85f)
                Log.d(TAG, "Background initialized, groundHeight=$groundHeight")
            }
            
            // Рисуем фон
            background?.draw(canvas)
            
            // Рисуем дом
            house?.draw(canvas, paint)
            
            // Рисуем мишени
            for (target in targets) {
                target.draw(canvas, paint)
            }
            
            // Рисуем самолеты
            for (plane in planes) {
                plane.draw(canvas, paint)
            }
            
            // Рисуем пули
            for (bullet in bullets) {
                bullet.draw(canvas, paint)
            }
            
            // Рисуем пилотов
            for (pilot in pilots) {
                pilot.draw(canvas, paint)
            }
            
            // Рисуем взрывы
            for (explosion in explosions) {
                explosion.draw(canvas, paint)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в draw: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // Метод для обработки крушения самолета игрока
    private fun handlePlayerCrash() {
        // Создаем взрыв
        val playerPlane = planes.find { it == planes.firstOrNull() } ?: return
        explosions.add(Explosion(playerPlane.position.x, playerPlane.position.y, 80f))
        
        if (gameType == GameType.TRAINING) {
            // В режиме тренировки не завершаем игру, а пересоздаем самолет
            playerPlane.isDestroyed = true
            Log.d(TAG, "Самолет игрока уничтожен, будет создан новый")
        } else {
            // В мультиплеере завершаем игру
            isGameOver = true
            gameEventListener?.onGameOver()
        }
    }
    
    // Методы SurfaceHolder.Callback
    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated called, width=$width, height=$height")
        
        // Инициализируем фон
        initBackground()
        
        // Запускаем игровой поток
        if (!isRunning) {
            isRunning = true
            gameThread = Thread {
                while (isRunning) {
                    update()
                    try {
                        holder.lockCanvas()?.let { canvas ->
                            draw(canvas)
                            holder.unlockCanvasAndPost(canvas)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in game loop: ${e.message}")
                    }
                    Thread.sleep(16) // ~60 FPS
                }
            }
            gameThread?.start()
            Log.d(TAG, "Игровой поток запущен")
        } else {
            Log.d(TAG, "Игровой поток уже запущен")
        }
        
        // Избегаем дублирования самолетов
        // Создадим самолет игрока только если список пуст
        if (width > 0 && height > 0 && planes.isEmpty()) {
            createNewPlayerPlane()
        }
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged: width=$width, height=$height")
        
        // Если была отложенная инициализация, выполняем ее
        if (pendingInitGame && pendingGameType != null && pendingPlaneColor != null) {
            Log.d(TAG, "Executing pending initialization")
            try {
                initialize(pendingGameType!!, pendingIsHost, pendingPlaneColor!!)
                pendingInitGame = false
            } catch (e: Exception) {
                Log.e(TAG, "Error in pending initialization: ${e.message}")
                // Если произошла ошибка, пытаемся инициализировать с безопасными параметрами
                try {
                    initialize(GameType.TRAINING, false, PlaneColor.RED)
                    pendingInitGame = false
                } catch (e2: Exception) {
                    Log.e(TAG, "Critical error in safe initialization: ${e2.message}")
                }
            }
        }
    }
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed")
        pause()
    }

    /**
     * Проверяет коллизии между игровыми объектами
     */
    private fun checkCollisions() {
        try {
            // Используем CollisionManager для проверки всех коллизий
            if (collisionManager == null) {
                collisionManager = CollisionManager(this, gameType)
            }
            
            // Проверяем коллизии между пулями, самолетами и мишенями
            val bulletsToRemove = collisionManager!!.checkCollisions(
                planes,
                bullets,
                targets,
                groundHeight
            )
            
            // Удаляем пули, которые столкнулись с объектами
            bullets.removeAll(bulletsToRemove)
            
            // Проверяем коллизии самолетов с землей
            collisionManager!!.checkPlanesGroundCollisions(planes, groundHeight)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при проверке коллизий: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Обновляет позицию удаленного самолета на основе сетевых данных
     */
    fun updateRemotePlane(planeId: String, position: Vector2D, rotation: Float, velocity: Vector2D) {
        try {
            Log.d(TAG, "Получены данные о самолете $planeId: позиция=(${position.x}, ${position.y}), поворот=$rotation")
            
            // Проверяем, является ли этот самолет самолетом игрока
            if (planeId == playerId) {
                Log.d(TAG, "Игнорируем обновление собственного самолета (playerId=$playerId)")
                return
            }
            
            // Проверяем, существует ли самолет с указанным ID
            val plane = planes.find { it.id == planeId }
            
            if (plane != null) {
                // Если самолет существует, обновляем его позицию и ориентацию
                plane.position = position
                plane.rotation = rotation
                plane.velocity = velocity
                Log.d(TAG, "Обновлен удаленный самолет $planeId: позиция=(${position.x}, ${position.y}), поворот=$rotation")
            } else {
                // Если самолет не существует, создаем новый
                Log.d(TAG, "Создаем удаленный самолет для $planeId. Наш playerId=$playerId")
                
                // Определяем цвет для удаленного самолета на основе информации о других игроках
                var planeColor = Color.RED  // Цвет по умолчанию
                
                // Ищем цвет из списка игроков
                val player = players.find { it.id == planeId }
                if (player != null) {
                    // Используем цвет из настроек игрока
                    planeColor = player.color.color
                    Log.d(TAG, "Найден игрок в списке, используем его цвет: ${player.color}")
                } else {
                    Log.d(TAG, "Игрок не найден в списке, используем цвет по умолчанию")
                }
                
                // Создаем новый самолет с помощью универсального метода
                val newPlane = createPlane(position.x, position.y, planeColor, planeId)
                newPlane.rotation = rotation
                newPlane.velocity = velocity
                
                // Добавляем самолет в список
                planes.add(newPlane)
                
                // Выводим список всех самолетов для отладки
                logAllPlanes()
                
                Log.d(TAG, "Создан новый удаленный самолет $planeId: позиция=(${position.x}, ${position.y}), поворот=$rotation")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при обновлении удаленного самолета: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Выводит в лог список всех самолетов для отладки
     */
    private fun logAllPlanes() {
        Log.d(TAG, "Список всех самолетов (${planes.size}):")
        for (plane in planes) {
            Log.d(TAG, "- Самолет ID=${plane.id}, позиция=(${plane.position.x}, ${plane.position.y}), поворот=${plane.rotation}, цвет=${plane.color}")
        }
    }
    
    /**
     * Возвращает самолет игрока (первый самолет в списке)
     * @return самолет игрока или null, если список пуст
     */
    fun getPlayerPlane(): Plane? {
        val playerPlane = if (planes.isNotEmpty()) planes.find { it.id == playerId } ?: planes[0] else null
        
        // Для отладки иногда выводим информацию о самолете
        if (System.currentTimeMillis() % 2000 < 16) { // Примерно раз в 2 секунды
            if (playerPlane != null) {
                Log.d(TAG, "getPlayerPlane: ID=${playerPlane.id}, позиция=(${playerPlane.position.x}, ${playerPlane.position.y}), isPlayer=${playerPlane.isPlayer}")
            } else {
                Log.d(TAG, "getPlayerPlane: самолет не найден")
            }
        }
        
        return playerPlane
    }

    // Методы для мультиплеера
    fun ejectRemotePilot(planeId: String, position: Vector2D) {
        val remotePlane = planes.find { it.id == planeId }
        if (remotePlane != null) {
            // Создаем пилота с цветом самолета
            val pilot = Pilot(remotePlane.color, remotePlane.width / 4)
            pilot.position = position
            pilot.screenWidth = width.toFloat()
            pilot.screenHeight = height.toFloat()
            pilot.groundLevel = groundHeight
            pilots.add(pilot)
            remotePlane.hasPilot = false
        }
    }

    fun damageRemotePlane(planeId: String, damage: Int) {
        val remotePlane = planes.find { it.id == planeId }
        if (remotePlane != null) {
            remotePlane.damage(damage)
        }
    }

    fun rescueRemotePilot(planeId: String) {
        val remotePlane = planes.find { it.id == planeId }
        if (remotePlane != null) {
            remotePlane.hasPilot = true
            pilots.removeAll { it.isRescued }
        }
    }

    /**
     * Возвращает список всех пилотов в игре
     * @return список пилотов
     */
    fun getPilots(): List<Pilot> {
        return pilots
    }

    private fun createEnemyPlanes() {
        val enemyCount = if (gameType == GameType.ONE_VS_ONE) 1 else 3
        for (i in 0 until enemyCount) {
            createEnemyPlane()
        }
    }

    private fun startGameLoop() {
        isRunning = true
        gameThread = Thread {
            while (isRunning) {
                update()
                draw()
                Thread.sleep(16) // примерно 60 FPS
            }
        }.apply { start() }
    }

    private fun draw() {
        if (!holder.surface.isValid) return

        val canvas = holder.lockCanvas()
        try {
            // Очищаем экран
            canvas.drawColor(Color.WHITE)

            // Рисуем фон
            background?.draw(canvas)

            // Рисуем все объекты
            planes.forEach { it.draw(canvas, paint) }
            bullets.forEach { it.draw(canvas, paint) }
            pilots.forEach { it.draw(canvas, paint) }
            targets.forEach { it.draw(canvas, paint) }
            explosions.forEach { it.draw(canvas, paint) }
            house?.draw(canvas, paint)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun initBackground() {
        try {
            // Инициализируем фон при создании поверхности
            if (background == null) {
                Log.d(TAG, "Инициализация фона в surfaceCreated, width=$width, height=$height")
                background = Background(width.toFloat(), height.toFloat())
                groundHeight = background?.getGroundHeight() ?: (height * 0.85f)
                Log.d(TAG, "Фон инициализирован, groundHeight=$groundHeight")
                
                // Создаем дом на земле
                if (house == null) {
                    house = House(PointF(width * 0.8f, groundHeight))
                    house?.setToGroundLevel(groundHeight)
                    Log.d(TAG, "Дом создан на позиции (${width * 0.8f}, $groundHeight)")
                }
            }
            
            // Если есть отложенная инициализация, выполняем ее
            if (pendingInitGame && pendingGameType != null && pendingPlaneColor != null) {
                Log.d(TAG, "Выполняем отложенную инициализацию")
                initialize(pendingGameType!!, pendingIsHost, pendingPlaneColor!!)
                pendingInitGame = false
            }
            
            // Сбрасываем флаг окончания игры
            isGameOver = false
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при инициализации фона: ${e.message}")
            e.printStackTrace()
        }
    }

    // Универсальный метод для создания самолета с заданными параметрами
    private fun createPlane(x: Float, y: Float, color: Int, planeId: String? = null): Plane {
        // Определяем размеры самолета относительно экрана
        val planeWidth = width * 0.04f
        val planeHeight = planeWidth / 2
        
        // Создаем самолет
        val plane = Plane(
            position = Vector2D(x, y),
            width = planeWidth,
            height = planeHeight,
            color = color,
            isPlayer = planeId == playerId,
            maxSpeed = 12f
        )
        
        // Устанавливаем ID если предоставлен
        if (planeId != null) {
            plane.id = planeId
        }
        
        // Инициализируем начальные параметры
        plane.velocity = Vector2D(5f, 0f)
        plane.rotation = 0f
        
        // Устанавливаем границы экрана
        plane.screenWidth = width.toFloat()
        plane.screenHeight = height.toFloat()
        plane.groundLevel = groundHeight
        
        Log.d(TAG, "Создан самолет с ID $planeId, позиция=(${x}, ${y}), цвет=$color")
        
        return plane
    }

    /**
     * Добавляет взрыв в список взрывов
     */
    fun addExplosion(explosion: Explosion) {
        explosions.add(explosion)
    }
    
    /**
     * Устанавливает флаг окончания игры
     */
    fun setGameOver(isGameOver: Boolean) {
        this.isGameOver = isGameOver
    }
    
    /**
     * Увеличивает счетчик убийств
     */
    fun increaseKillCount() {
        killCount++
        
        // Уведомляем слушателя об изменении счета
        gameEventListener?.onScoreChanged(killCount)
        
        // Проверяем условие победы
        if ((gameType == GameType.TRAINING && killCount >= 5) || 
            (gameType == GameType.ONE_VS_ONE && killCount >= 10)) {
            victory()
        }
    }
    
    /**
     * Создает пулю от удаленного самолета
     */
    fun createRemoteBullet(planeId: String, position: Vector2D, velocity: Vector2D, color: Int) {
        val bullet = Bullet(position, velocity, color)
        bullets.add(bullet)
    }

    /**
     * Уничтожает удаленный самолет
     */
    fun destroyRemotePlane(planeId: String) {
        val remotePlane = planes.find { it.id == planeId }
        if (remotePlane != null) {
            remotePlane.destroy()
        }
    }
    
    /**
     * Обрабатывает столкновение самолета с землей
     */
    fun onPlaneGroundCollision(plane: Plane) {
        if (plane.isPlayer && !isGameOver) {
            // Создаем взрыв
            val explosion = Explosion.createExplosion(
                plane.position.x,
                plane.position.y,
                Explosion.ExplosionSize.LARGE
            )
            addExplosion(explosion)
            
            // Уничтожаем самолет
            plane.isDestroyed = true
            
            // В режиме тренировки не завершаем игру
            if (gameType == GameType.TRAINING) {
                Log.d(TAG, "Самолет игрока столкнулся с землей, будет создан новый")
            } else {
                // В мультиплеере завершаем игру
                gameOver()
            }
        }
    }
    
    /**
     * Обрабатывает столкновение самолета с пулей
     */
    fun onPlaneBulletCollision(plane: Plane, bullet: Bullet) {
        if (!plane.isDestroyed) {
            // Создаем взрыв
            val explosion = Explosion.createExplosion(
                bullet.position.x,
                bullet.position.y,
                Explosion.ExplosionSize.MEDIUM
            )
            addExplosion(explosion)
            
            // Уничтожаем самолет
            plane.isDestroyed = true
            
            // Если это самолет игрока, обрабатываем по-разному в зависимости от режима
            if (plane.isPlayer && !isGameOver) {
                if (gameType == GameType.TRAINING) {
                    // В режиме тренировки не завершаем игру
                    Log.d(TAG, "Самолет игрока уничтожен пулей, будет создан новый")
                } else {
                    // В мультиплеере завершаем игру
                    gameOver()
                }
            }
            
            // Увеличиваем счет, если это не самолет игрока
            if (!plane.isPlayer) {
                killCount++
                
                // Проверяем условие победы в режиме тренировки
                if (gameType == GameType.TRAINING && killCount >= 5) {
                    victory()
                }
                
                // Проверяем условие победы в режиме мультиплеера
                if (gameType == GameType.ONE_VS_ONE && killCount >= 10) {
                    victory()
                }
            }
        }
    }
    
    /**
     * Обрабатывает столкновение цели с пулей
     */
    fun onTargetBulletCollision(target: Target, bullet: Bullet) {
        // Создаем взрыв
        val explosion = Explosion.createExplosion(
            bullet.position.x,
            bullet.position.y,
            Explosion.ExplosionSize.SMALL
        )
        addExplosion(explosion)
        
        // Уничтожаем цель
        target.isDestroyed = true
        
        // Увеличиваем счет
        killCount++
        
        // Проверяем условие победы в режиме тренировки
        if (gameType == GameType.TRAINING && killCount >= 5) {
            victory()
        }
    }
    
    /**
     * Завершает игру
     */
    private fun gameOver() {
        isGameOver = true
        gameEventListener?.onGameOver()
    }
    
    /**
     * Обрабатывает победу в игре
     */
    private fun victory() {
        isGameOver = true
        gameEventListener?.onGameOver()
    }
    
    /**
     * Перезапускает игру
     */
    fun restartGame() {
        try {
            Log.d(TAG, "Перезапуск игры")
            
            // Сбрасываем игровые переменные
            isGameOver = false
            isGameStarted = true
            isGamePaused = false
            killCount = 0
            
            // Очищаем списки объектов
            planes.clear()
            bullets.clear()
            pilots.clear()
            explosions.clear()
            targets.clear()
            
            // Создаем самолет игрока
            initPlayerPlane()
            
            // Создаем дом на земле
            val groundLevel = background?.getGroundHeight() ?: (height * 0.85f)
            house = House(PointF(width * 0.8f, groundLevel))
            house?.setToGroundLevel(groundLevel)
            
            // Создаем цели в режиме тренировки
            if (gameType == GameType.TRAINING) {
                createTargets()
            }
            
            // Обновляем интерфейс
            gameEventListener?.onScoreChanged(killCount)
            
            Log.d(TAG, "Игра перезапущена")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при перезапуске игры: ${e.message}")
            e.printStackTrace()
        }
    }

    // Метод для передачи списка игроков в GameView
    fun setPlayers(playersList: List<Player>) {
        players.clear()
        players.addAll(playersList)
        Log.d(TAG, "Установлен список игроков (${players.size})")
        
        // Выводим список игроков для отладки
        for (player in players) {
            Log.d(TAG, "Игрок: id=${player.id}, name=${player.name}, color=${player.color}, isHost=${player.isHost}")
        }
    }
    
    // Метод для создания тестового самолета противника (для отладки)
    fun createTestEnemyPlane() {
        // Получаем противоположный цвет от цвета игрока
        val enemyColor = when (planeColor) {
            PlaneColor.RED -> PlaneColor.BLUE
            PlaneColor.BLUE -> PlaneColor.RED
            PlaneColor.GREEN -> PlaneColor.YELLOW
            PlaneColor.YELLOW -> PlaneColor.GREEN
            PlaneColor.ORANGE -> PlaneColor.PURPLE
            PlaneColor.PURPLE -> PlaneColor.ORANGE
        }
        
        // Создаем фиктивный ID для противника
        val enemyId = "test-enemy-" + System.currentTimeMillis()
        
        // Создаем позицию для противника (на противоположной стороне экрана)
        val enemyX = width * 0.7f
        val enemyY = height * 0.3f
        
        // Создаем самолет противника
        val enemyPlane = createPlane(enemyX, enemyY, enemyColor.color, enemyId)
        planes.add(enemyPlane)
        
        Log.d(TAG, "Создан тестовый самолет противника: id=$enemyId, position=($enemyX, $enemyY), color=${enemyColor.name}")
    }

    /**
     * Останавливает игровой поток.
     * Используется при завершении активности.
     */
    fun stopGame() {
        isRunning = false
        try {
            gameThread?.join(1000) // Ждем завершения потока максимум 1 секунду
            gameThread = null
            Log.d(TAG, "Игровой поток остановлен")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при остановке игрового потока: ${e.message}")
        }
    }
} 