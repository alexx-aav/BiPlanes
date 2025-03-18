package com.example.biplanes.game.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.biplanes.databinding.ActivityGameBinding
import com.example.biplanes.game.models.GameType
import com.example.biplanes.game.models.PlaneColor
import com.example.biplanes.game.models.Player
import com.example.biplanes.game.models.Vector2D
import com.example.biplanes.network.GameMessage
import com.example.biplanes.network.WiFiDirectService
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.ArrayList
import android.app.AlertDialog
import android.view.MotionEvent

/**
 * Активность для игры Biplanes.
 * Управляет игровым процессом и пользовательским интерфейсом.
 */
class GameActivity : AppCompatActivity(), WiFiDirectService.WiFiDirectListener {
    private val TAG = "GameActivity"

    // Параметры игры
    private var gameType: GameType = GameType.TRAINING
    private var isHost: Boolean = false
    private var planeColor: PlaneColor = PlaneColor.RED
    private var playerId: String = ""
    private var players: ArrayList<Player> = ArrayList()

    // ViewBinding
    private lateinit var binding: ActivityGameBinding

    // Состояние игры
    private var isPaused: Boolean = false
    private var isGameStarted: Boolean = false
    
    // Переменные для управления стрельбой и катапультированием
    private var lastFireTime = 0L
    private val fireDelay = 300L // Задержка между выстрелами в миллисекундах
    private var lastEjectTime = 0L
    private val ejectDelay = 1000L // Задержка между катапультированиями в миллисекундах
    
    // Обработчик для проверки состояния кнопок
    private val handler = Handler(Looper.getMainLooper())
    private val controlsRunnable = object : Runnable {
        override fun run() {
            updateControls()
            handler.postDelayed(this, 16) // ~60 FPS
        }
    }
    
    // Сервис Wi-Fi Direct
    private lateinit var wifiDirectService: WiFiDirectService
    
    // Флаги для кнопок
    private var isFiringButtonPressed = false
    private var isEjectButtonPressed = false
    private var isFiring = false
    private var isEjecting = false
    
    // Переменные для мультиплеера
    private var playerReady = false
    private var enemyReady = false
    private var isMultiplayer = false

    // Константы для управления
    private val joystickUpdateInterval = 16L // ~60 FPS
    private var joystickUpdateRunnable: Runnable = object : Runnable {
        override fun run() {
            updateControls()
            handler.postDelayed(this, joystickUpdateInterval)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Устанавливаем полноэкранный режим
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получаем параметры игры из Intent
        gameType = intent.getSerializableExtra("gameType") as? GameType ?: GameType.TRAINING
        isHost = intent.getBooleanExtra("isHost", false)
        planeColor = intent.getSerializableExtra("planeColor") as? PlaneColor ?: PlaneColor.RED
        playerId = intent.getStringExtra("playerId") ?: ""
        
        // Получаем список игроков
        @Suppress("UNCHECKED_CAST")
        players = intent.getSerializableExtra("players") as? ArrayList<Player> ?: ArrayList()

        Log.d(TAG, "Game parameters: gameType=$gameType, isHost=$isHost, planeColor=$planeColor, playerId=$playerId, players=${players.size}")

        // Настраиваем игровое представление
        setupGameView()
        
        // Настраиваем обработчики кнопок
        setupButtonListeners()
        
        // Инициализируем игру
        startGame()
    }
    
    private fun initWiFiDirect() {
        // Инициализируем сервис Wi-Fi Direct
        wifiDirectService = WiFiDirectService(this)
        wifiDirectService.setListener(this)
        wifiDirectService.start()
    }

    private fun setupGameView() {
        // Настраиваем игровое представление с параметрами игры
        binding.gameView.initialize(gameType, isHost, planeColor)
        
        // Устанавливаем слушатель событий игры
        binding.gameView.setGameEventListener(object : GameView.GameEventListener {
            override fun onScoreChanged(newScore: Int) {
                // Не используется, так как мы удалили систему счета
            }
            
            override fun onGameOver() {
                showGameOver()
                
                // Отправляем сообщение о завершении игры
                if (gameType != GameType.TRAINING && isHost) {
                    val message = GameMessage.GameOver(null)
                    wifiDirectService.sendMessage(message)
                }
            }
        })
        
        // Устанавливаем флаг, что игра не окончена
        binding.gameView.setGameOver(false)
    }

    private fun setupButtonListeners() {
        // Кнопка паузы
        binding.pauseButton.setOnClickListener { togglePause() }
        
        // Кнопка перезапуска
        binding.restartButton.setOnClickListener { restartGame() }

        // Метод для обработки нажатий на кнопки
        setupButtons()
    }
    
    private fun updateControls() {
        if (isPaused || !isGameStarted) return
        
        // Получаем значения джойстика
        val joystickX = binding.joystick.getXPercent()
        val joystickY = binding.joystick.getYPercent()
        
        // Передаем управление в GameView
        updateGame()
        
        // Отправляем сообщение о движении самолета
        if (gameType != GameType.TRAINING) {
            val playerPlane = binding.gameView.getPlayerPlane()
            if (playerPlane != null) {
                val message = GameMessage.PlaneMovement(
                    playerId = playerId,
                    position = playerPlane.position,
                    rotation = playerPlane.rotation,
                    velocity = playerPlane.velocity
                )
                wifiDirectService.sendMessage(message)
            }
            
            // Отправляем сообщение о выстреле
            if (isFiring && System.currentTimeMillis() - lastFireTime > fireDelay) {
                lastFireTime = System.currentTimeMillis()
                
                val playerPlane = binding.gameView.getPlayerPlane()
                if (playerPlane != null) {
                    // Создаем вектор направления выстрела
                    val angle = Math.toRadians(playerPlane.rotation.toDouble())
                    val bulletVelocity = Vector2D(
                        Math.cos(angle).toFloat() * 15f,
                        Math.sin(angle).toFloat() * 15f
                    )
                    
                    // Создаем вектор позиции выстрела
                    val bulletOffset = Vector2D(
                        Math.cos(angle).toFloat() * playerPlane.width / 2,
                        Math.sin(angle).toFloat() * playerPlane.width / 2
                    )
                    val bulletPosition = Vector2D(
                        playerPlane.position.x + bulletOffset.x,
                        playerPlane.position.y + bulletOffset.y
                    )
                    
                    val message = GameMessage.Fire(
                        playerId = playerId,
                        position = bulletPosition,
                        velocity = bulletVelocity,
                        color = planeColor.color
                    )
                    wifiDirectService.sendMessage(message)
                }
            }
            
            // Отправляем сообщение о катапультировании
            if (isEjecting && System.currentTimeMillis() - lastEjectTime > ejectDelay) {
                lastEjectTime = System.currentTimeMillis()
                
                val playerPlane = binding.gameView.getPlayerPlane()
                if (playerPlane != null) {
                    val message = GameMessage.Eject(
                        playerId = playerId,
                        position = playerPlane.position
                    )
                    wifiDirectService.sendMessage(message)
                }
            }
        }
    }

    // Метод для обработки нажатий на кнопки
    private fun setupButtons() {
        // Кнопка стрельбы
        binding.fireButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isFiring = true
                    Log.d(TAG, "Fire button pressed")
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isFiring = false
                    Log.d(TAG, "Fire button released")
                    true
                }
                else -> false
            }
        }
        
        // Кнопка катапультирования
        binding.ejectButton.setOnEjectListener {
            Log.d(TAG, "Eject button pressed")
            
            // Устанавливаем флаг катапультирования
            isEjecting = true
            
            // Немедленно вызываем метод катапультирования напрямую
            val playerPlane = binding.gameView.getPlayerPlane()
            if (playerPlane != null) {
                Log.d(TAG, "Вызываем катапультирование напрямую для самолета на позиции (${playerPlane.position.x}, ${playerPlane.position.y})")
                binding.gameView.ejectPilotDirectly(playerPlane)
            } else {
                Log.e(TAG, "Не удалось получить самолет игрока для катапультирования")
            }
            
            // Обновляем игру для применения изменений
            updateGame()
            
            // Сбрасываем флаг после небольшой задержки
            Handler(Looper.getMainLooper()).postDelayed({
                isEjecting = false
                Log.d(TAG, "Eject flag reset after delay")
            }, 500) // Увеличенная задержка для надежности
        }
    }

    // Метод для обновления игры
    private fun updateGame() {
        // Получаем значения джойстика
        val joystickX = binding.joystick.getXPercent()
        val joystickY = binding.joystick.getYPercent()
        
        // Логируем значения для отладки
        if (isFiring || isEjecting) {
            Log.d(TAG, "updateGame: joystickX=$joystickX, joystickY=$joystickY, isFiring=$isFiring, isEjecting=$isEjecting")
        }
        
        // Обновляем состояние игры - передаем текущие значения флагов
        binding.gameView.controlPlayerPlane(joystickX, joystickY, isFiring, isEjecting)
        
        // НЕ сбрасываем флаги здесь, они будут сброшены в controlsRunnable
        // после обработки в GameView
    }

    private fun togglePause() {
        isPaused = !isPaused
        if (isPaused) {
            binding.gameView.pause()
            binding.pauseOverlay.visibility = View.VISIBLE
            handler.removeCallbacks(controlsRunnable)
        } else {
            binding.gameView.resume()
            binding.pauseOverlay.visibility = View.GONE
            handler.post(controlsRunnable)
        }
    }

    private fun showGameOver() {
        try {
            Log.d(TAG, "Показываем экран окончания игры")
            
            // Показываем оверлей окончания игры
            binding.gameOverOverlay.visibility = View.VISIBLE
            
            // Устанавливаем флаг окончания игры
            binding.gameView.setGameOver(true)
            
            // Останавливаем обновление джойстика
            stopJoystickUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при показе экрана окончания игры: ${e.message}")
        }
    }

    /**
     * Перезапускает игру
     */
    private fun restartGame() {
        try {
            Log.d(TAG, "Перезапуск игры")
            
            // Скрываем оверлей окончания игры
            binding.gameOverOverlay.visibility = View.GONE
            
            // Перезапускаем игровое представление
            binding.gameView.restart()
            
            // Возобновляем обновление джойстика
            startJoystickUpdates()
            
            // Сбрасываем флаги кнопок
            isFiringButtonPressed = false
            isEjectButtonPressed = false
            
            // Устанавливаем флаг, что игра запущена
            isGameStarted = true
            
            // Сбрасываем флаг паузы
            isPaused = false
            
            Log.d(TAG, "Игра перезапущена")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при перезапуске игры: ${e.message}")
        }
    }

    private fun startJoystickUpdates() {
        joystickUpdateRunnable = object : Runnable {
            override fun run() {
                updateControls()
                handler.postDelayed(this, joystickUpdateInterval)
            }
        }
        handler.post(joystickUpdateRunnable)
    }

    private fun stopJoystickUpdates() {
        handler.removeCallbacks(joystickUpdateRunnable)
    }

    // Реализация методов интерфейса WiFiDirectListener
    
    override fun onDeviceDiscovered(device: android.net.wifi.p2p.WifiP2pDevice) {
        // Не используется в игре
    }
    
    override fun onConnectionChanged(isConnected: Boolean, groupOwnerAddress: String?) {
        if (!isConnected) {
            // Если соединение разорвано, показываем сообщение и завершаем игру
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Соединение разорвано",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
    
    override fun onDeviceDisconnected() {
        // Если устройство отключено, показываем сообщение и завершаем игру
        runOnUiThread {
            Toast.makeText(
                this,
                "Устройство отключено",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }
    
    override fun onMessageReceived(message: Any) {
        if (message is GameMessage) {
            when (message) {
                is GameMessage.PlaneMovement -> {
                    // Получено сообщение о движении самолета
                    val planeId = message.playerId
                    val position = message.position
                    val rotation = message.rotation
                    val velocity = message.velocity
                    
                    // Обновляем позицию самолета
                    runOnUiThread {
                        binding.gameView.updateRemotePlane(planeId, position, rotation, velocity)
                    }
                }
                
                is GameMessage.Fire -> {
                    // Получено сообщение о выстреле
                    val planeId = message.playerId
                    val position = message.position
                    val velocity = message.velocity
                    val color = message.color
                    
                    // Создаем пулю
                    runOnUiThread {
                        binding.gameView.createRemoteBullet(planeId, position, velocity, color)
                    }
                }
                
                is GameMessage.Eject -> {
                    // Получено сообщение о катапультировании
                    val planeId = message.playerId
                    val position = message.position
                    
                    // Катапультируем пилота
                    runOnUiThread {
                        binding.gameView.ejectRemotePilot(planeId, position)
                    }
                }
                
                is GameMessage.Hit -> {
                    // Получено сообщение о попадании
                    val planeId = message.playerId
                    val damage = message.damage
                    
                    // Наносим урон самолету
                    runOnUiThread {
                        binding.gameView.damageRemotePlane(planeId, damage)
                    }
                }
                
                is GameMessage.PlaneDestroyed -> {
                    // Получено сообщение о уничтожении самолета
                    val planeId = message.playerId
                    
                    // Уничтожаем самолет
                    runOnUiThread {
                        binding.gameView.destroyRemotePlane(planeId)
                    }
                }
                
                is GameMessage.PilotRescued -> {
                    // Получено сообщение о спасении пилота
                    val planeId = message.playerId
                    
                    // Спасаем пилота
                    runOnUiThread {
                        binding.gameView.rescueRemotePilot(planeId)
                    }
                }
                
                is GameMessage.GameOver -> {
                    // Получено сообщение о завершении игры
                    runOnUiThread {
                        showGameOver()
                    }
                }
                
                else -> {
                    // Игнорируем другие типы сообщений
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isPaused && isGameStarted) {
            binding.gameView.resume()
            handler.post(controlsRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        binding.gameView.pause()
        handler.removeCallbacks(controlsRunnable)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(controlsRunnable)
        
        // Останавливаем сервис Wi-Fi Direct
        if (::wifiDirectService.isInitialized) {
            wifiDirectService.stop()
        }
    }

    /**
     * Обрабатывает сетевые сообщения от другого игрока
     */
    private fun handleMessage(message: String) {
        try {
            Log.d(TAG, "Received message: $message")
            
            // Разбиваем сообщение на части
            val parts = message.split(":")
            if (parts.isEmpty()) {
                Log.e(TAG, "Empty message received")
                return
            }
            
            // Обрабатываем сообщение в зависимости от типа
            when (parts[0]) {
                "READY" -> handleReadyMessage(parts)
                "MOVE" -> handleMoveMessage(parts)
                "FIRE" -> handleFireMessage(parts)
                "EJECT" -> handleEjectMessage(parts)
                "DESTROY" -> handleDestroyMessage(parts)
                "GAME_OVER" -> handleGameOverMessage(parts)
                else -> Log.e(TAG, "Unknown message type: ${parts[0]}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}")
        }
    }

    /**
     * Обрабатывает сообщение о готовности игрока
     */
    private fun handleReadyMessage(parts: List<String>) {
        runOnUiThread {
            enemyReady = true
            checkBothPlayersReady()
        }
    }

    /**
     * Проверяет, готовы ли оба игрока начать игру
     */
    private fun checkBothPlayersReady() {
        if (playerReady && enemyReady) {
            startGame()
        }
    }

    /**
     * Обрабатывает сообщение о движении самолета
     */
    private fun handleMoveMessage(parts: List<String>) {
        if (parts.size < 4) {
            Log.e(TAG, "Invalid MOVE message format")
            return
        }
        
        try {
            val x = parts[1].toFloat()
            val y = parts[2].toFloat()
            val angle = parts[3].toFloat()
            
            runOnUiThread {
                // Обновляем позицию вражеского самолета
                // Этот метод должен быть реализован в GameView
                // binding.gameView.updateEnemyPosition(x, y, angle)
            }
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Error parsing MOVE message values", e)
        }
    }

    /**
     * Обрабатывает сообщение о выстреле
     */
    private fun handleFireMessage(parts: List<String>) {
        if (parts.size < 4) {
            Log.e(TAG, "Invalid FIRE message format")
            return
        }
        
        try {
            val x = parts[1].toFloat()
            val y = parts[2].toFloat()
            val angle = parts[3].toFloat()
            
            runOnUiThread {
                // Создаем выстрел от вражеского самолета
                // Этот метод должен быть реализован в GameView
                // binding.gameView.enemyFire(x, y, angle)
            }
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Error parsing FIRE message values", e)
        }
    }

    /**
     * Обрабатывает сообщение о катапультировании
     */
    private fun handleEjectMessage(parts: List<String>) {
        if (parts.size < 3) {
            Log.e(TAG, "Invalid EJECT message format")
            return
        }
        
        try {
            val x = parts[1].toFloat()
            val y = parts[2].toFloat()
            
            runOnUiThread {
                // Создаем катапультирование вражеского пилота
                // Этот метод должен быть реализован в GameView
                // binding.gameView.enemyEject(x, y)
            }
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Error parsing EJECT message values", e)
        }
    }

    /**
     * Обрабатывает сообщение о уничтожении самолета
     */
    private fun handleDestroyMessage(parts: List<String>) {
        if (parts.size < 2) {
            Log.e(TAG, "Invalid DESTROY message format")
            return
        }
        
        try {
            val planeId = parts[1]
            
            runOnUiThread {
                // Уничтожаем самолет
                binding.gameView.destroyRemotePlane(planeId)
            }
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Error parsing DESTROY message values", e)
        }
    }

    /**
     * Обрабатывает сообщение о конце игры
     */
    private fun handleGameOverMessage(parts: List<String>) {
        runOnUiThread {
            showGameOverDialog(false)
        }
    }

    /**
     * Показывает диалог окончания игры
     * @param playerWon true, если игрок победил
     */
    private fun showGameOverDialog(playerWon: Boolean) {
        val message = if (playerWon) {
            "Вы победили!"
        } else {
            "Вы проиграли!"
        }
        
        AlertDialog.Builder(this)
            .setTitle("Игра окончена")
            .setMessage(message)
            .setPositiveButton("Играть снова") { _, _ ->
                if (isMultiplayer) {
                    sendMessage("RESTART")
                }
                restartGame()
            }
            .setNegativeButton("Выход") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Показывает диалог готовности только в мультиплеерном режиме
     */
    private fun showReadyDialog() {
        if (isMultiplayer) {
            AlertDialog.Builder(this)
                .setTitle("Готовы?")
                .setMessage("Вы готовы начать игру?")
                .setPositiveButton("Готов") { _, _ ->
                    playerReady = true
                    sendMessage("READY")
                    checkBothPlayersReady()
                }
                .setCancelable(false)
                .show()
        } else {
            // В режиме тренировки сразу начинаем игру
            startGame()
        }
    }

    /**
     * Отправляет сообщение другому игроку
     */
    private fun sendMessage(message: String) {
        if (isMultiplayer && ::wifiDirectService.isInitialized) {
            wifiDirectService.sendMessage(message)
        }
    }

    private fun startGame() {
        isGameStarted = true
        startJoystickUpdates()
    }
} 