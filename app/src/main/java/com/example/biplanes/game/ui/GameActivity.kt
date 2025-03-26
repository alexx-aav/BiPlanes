package com.example.biplanes.game.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.biplanes.R
import com.example.biplanes.databinding.ActivityGameBinding
import com.example.biplanes.game.models.GameType
import com.example.biplanes.game.models.PlaneColor
import com.example.biplanes.game.models.Player
import com.example.biplanes.game.models.Vector2D
import com.example.biplanes.network.GameMessage
import com.example.biplanes.network.NetworkService
import com.example.biplanes.network.NetworkService.ServiceMode
import java.lang.System.currentTimeMillis
import java.util.ArrayList

/**
 * Активность для игры Biplanes.
 * Управляет игровым процессом и пользовательским интерфейсом.
 */
class GameActivity : AppCompatActivity(), NetworkService.NetworkListener {
    private val TAG = "GameActivity"

    // Константы для запросов разрешений
    private val PERMISSION_REQUEST_WRITE_STORAGE = 101

    // ViewBinding
    private lateinit var binding: ActivityGameBinding
    
    // Состояние игры
    private var isPaused = false
    private var isMultiplayer = false
    private var isHost = false
    private var playerId = ""
    private var gameType = GameType.TRAINING
    private lateinit var playerColor: PlaneColor
    private var players = mutableListOf<Player>()
    
    private var lastUpdateTime = 0L // Время последней отправки данных о самолете
    private var isGameStarted = false // Флаг, указывающий, что игра запущена

    // Параметры игры
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
    
    // Сервис сетевого взаимодействия
    private lateinit var networkService: NetworkService
    
    // Флаги для кнопок
    private var isFiringButtonPressed = false
    private var isEjectButtonPressed = false
    private var isFiring = false
    private var isEjecting = false
    
    // Переменные для мультиплеера
    private var playerReady = false
    private var enemyReady = false
    private var connectedServerAddress: String? = null

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
        playerColor = intent.getSerializableExtra("planeColor") as? PlaneColor ?: PlaneColor.RED
        playerId = intent.getStringExtra("playerId") ?: ""
        
        // Получаем список игроков
        @Suppress("UNCHECKED_CAST")
        players = intent.getSerializableExtra("players") as? ArrayList<Player> ?: ArrayList()

        // Устанавливаем флаг мультиплеера
        isMultiplayer = gameType != GameType.TRAINING

        Log.d(TAG, "Game parameters: gameType=$gameType, isHost=$isHost, planeColor=$playerColor, playerId=$playerId, players=${players.size}")
        
        // Показываем сообщение пользователю о режиме игры
        val gameTypeStr = when(gameType) {
            GameType.ONE_VS_ONE -> "1 на 1"
            GameType.TWO_VS_TWO -> "2 на 2"
            GameType.FREE_FOR_ALL -> "Каждый за себя"
            GameType.TRAINING -> "Тренировка"
        }
        
        Toast.makeText(
            this,
            "Режим: $gameTypeStr, Хост: $isHost, Цвет: ${playerColor.name}",
            Toast.LENGTH_SHORT
        ).show()

        // Настраиваем игровое представление
        setupGameView()
        
        // Настраиваем обработчики кнопок
        setupButtonListeners()
        
        // Инициализируем сетевой сервис, если это мультиплеерная игра
        if (isMultiplayer) {
            initNetworkService()
        } else {
            // Инициализируем игру сразу для одиночной игры
            startGame()
        }
    }
    
    private fun initNetworkService() {
        // Инициализируем сетевой сервис
        networkService = NetworkService(this)
        networkService.setListener(this)
        
        // Запускаем сетевой сервис
        networkService.start()
        
        Log.d(TAG, "NetworkService инициализирован, isHost=$isHost")
        
        // Если это хост, создаем сервер
        if (isHost) {
            try {
                // Создаем игровой сервер с уникальным ID игры
                val gameId = "Game-${players.first { it.isHost }.id.substring(0, 8)}"
                networkService.createGameServer(gameId)
                Log.d(TAG, "Создан игровой сервер с ID: $gameId")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при создании сервера: ${e.message}")
            }
        } else {
            // Если мы клиент (не хост), начинаем поиск серверов
            try {
                Log.d(TAG, "Начинаем поиск серверов (клиент)")
                networkService.discoverServers()
                
                // Ищем хоста в списке игроков
                val hostPlayer = players.find { it.isHost }
                if (hostPlayer != null) {
                    Log.d(TAG, "Найден хост в списке игроков: ${hostPlayer.id}")
                    
                    // Ожидаем обнаружения игровых серверов и подключаемся к первому найденному
                    // В реальной реализации можно добавить логику для выбора нужного сервера
                    Handler(Looper.getMainLooper()).postDelayed({
                        val discoveredServers = networkService.getFilteredServers(ServiceMode.GAME)
                        Log.d(TAG, "Найдено ${discoveredServers.size} игровых серверов")
                        
                        if (discoveredServers.isNotEmpty()) {
                            // Подключаемся к первому найденному серверу
                            val server = discoveredServers.first()
                            Log.d(TAG, "Подключаемся к игровому серверу: ${server.name}")
                            networkService.connectToServer(server)
                        } else {
                            // Если серверы не найдены, показываем сообщение
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    "Не удалось найти игровой сервер. Переподключитесь.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }, 1500) // Даем время на обнаружение серверов
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при поиске серверов: ${e.message}")
            }
        }
    }

    private fun setupGameView() {
        // Настраиваем игровое представление с параметрами игры
        binding.gameView.initialize(gameType, isHost, playerColor, playerId)
        
        // Передаем список игроков в GameView
        binding.gameView.setPlayers(players)
        
        // Для отладки создаем тестовый самолет противника
        if (isMultiplayer && players.size >= 2) {
            Log.d(TAG, "Для отладки создаем тестовый самолет противника")
            Handler(Looper.getMainLooper()).postDelayed({
                binding.gameView.createTestEnemyPlane()
            }, 2000) // Задержка для того, чтобы убедиться, что самолет игрока уже создан
        }
        
        // Устанавливаем слушатель событий игры
        binding.gameView.setGameEventListener(object : GameView.GameEventListener {
            override fun onScoreChanged(newScore: Int) {
                // Не используется, так как мы удалили систему счета
            }
            
            override fun onGameOver() {
                showGameOver()
                
                // Отправляем сообщение о завершении игры
                if (isMultiplayer && isHost) {
                    val message = GameMessage.GameOver(null)
                    networkService.sendMessage(message)
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

        // Кнопка сохранения логов
        binding.saveLogsButton.setOnClickListener { saveGameLogs() }

        // Метод для обработки нажатий на кнопки
        setupButtons()
    }
    
    private fun updateControls() {
        if (isPaused) return
        
        // Обновляем игру в любом случае, даже если мультиплеер еще не готов
        updateGame()
        
        // Отправляем сообщение о движении самолета
        if (isMultiplayer) {
            val playerPlane = binding.gameView.getPlayerPlane()
            if (playerPlane != null) {
                val message = GameMessage.PlaneMovement(
                    playerId = playerId,
                    position = playerPlane.position,
                    rotation = playerPlane.rotation,
                    velocity = playerPlane.velocity
                )
                try {
                    // Отправляем каждые 50 мс
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime > 50) {
                        networkService.sendMessage(message)
                        lastUpdateTime = currentTime
                        //Log.d(TAG, "Отправлены данные о движении самолета: pos=(${playerPlane.position.x}, ${playerPlane.position.y})")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка отправки сообщения о движении: ${e.message}")
                }
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
                        color = playerColor.color
                    )
                    try {
                        networkService.sendMessage(message)
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка отправки сообщения о выстреле: ${e.message}")
                    }
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
                    try {
                        networkService.sendMessage(message)
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка отправки сообщения о катапультировании: ${e.message}")
                    }
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
                    
                    // Запускаем игру при первом нажатии, если она еще не запущена
                    if (!isGameStarted) {
                        startGame()
                    }
                    
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
            
            // Запускаем игру при первом нажатии, если она еще не запущена
            if (!isGameStarted) {
                startGame()
            }
            
            // Устанавливаем флаг катапультирования
            isEjecting = true
            
            // Немедленно вызываем метод катапультирования напрямую
            val playerPlane = binding.gameView.getPlayerPlane()
            if (playerPlane != null) {
                Log.d(TAG, "Вызываем катапультирование напрямую для самолета на позиции (${playerPlane.position.x}, ${playerPlane.position.y})")
                binding.gameView.ejectPilot(playerPlane)
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
        try {
            // Получаем значения джойстика
            val joystickX = binding.joystick.getXPercent()
            val joystickY = binding.joystick.getYPercent()
            
            // Обновляем состояние игры - передаем текущие значения флагов
            binding.gameView.controlPlayerPlane(joystickX, joystickY, isFiring, isEjecting)
            
            // Периодически выводим отладочную информацию
            if (System.currentTimeMillis() % 1000 < 16) { // Примерно раз в секунду
                Log.d(TAG, "Джойстик: X=$joystickX, Y=$joystickY, Стрельба=$isFiring, Катапульта=$isEjecting")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в updateGame: ${e.message}")
        }
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
            
            // Используем runOnUiThread для обновления UI из основного потока
            runOnUiThread {
                // Показываем оверлей окончания игры
                binding.gameOverOverlay.visibility = View.VISIBLE
                
                // Устанавливаем флаг окончания игры
                binding.gameView.setGameOver(true)
                
                // Останавливаем обновление джойстика
                stopJoystickUpdates()
            }
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
        // Удаляем предыдущий обработчик, если он существует
        handler.removeCallbacks(joystickUpdateRunnable)
        
        // Создаем новый обработчик
        joystickUpdateRunnable = object : Runnable {
            override fun run() {
                if (isGameStarted && !isPaused) {
                    updateControls()
                }
                handler.postDelayed(this, joystickUpdateInterval)
            }
        }
        
        // Запускаем обновление джойстика
        handler.post(joystickUpdateRunnable)
        Log.d(TAG, "Джойстик запущен")
    }

    private fun stopJoystickUpdates() {
        handler.removeCallbacks(joystickUpdateRunnable)
    }

    // Реализация методов интерфейса NetworkListener
    
    override fun onServerDiscovered(serverInfo: NetworkService.ServerInfo) {
        Log.d(TAG, "Обнаружен сервер: ${serverInfo.name}")
        
        // Подключаемся только к игровым серверам
        if (serverInfo.name.startsWith(NetworkService.GAME_PREFIX)) {
            // Можно добавить дополнительную логику для выбора нужного сервера
        }
    }
    
    override fun onConnectionChanged(isConnected: Boolean, serverAddress: String?) {
        Log.d(TAG, "Статус соединения изменился: isConnected=$isConnected, serverAddress=$serverAddress")
        
        if (isConnected) {
            runOnUiThread {
                Toast.makeText(this, "Подключено к игровому серверу", Toast.LENGTH_SHORT).show()
            }
            
            // Отправляем информацию о своем самолете сразу после подключения
            Thread {
                // Небольшая задержка, чтобы убедиться, что другая сторона готова принимать сообщения
                Thread.sleep(1000)
                sendInitialPlaneInfo()
            }.start()
        } else {
            runOnUiThread {
                Toast.makeText(this, "Отключено от сервера", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onClientConnected(clientId: String) {
        Log.d(TAG, "Клиент подключился: $clientId")
    }
    
    override fun onClientDisconnected(clientId: String) {
        Log.d(TAG, "Клиент отключился: $clientId")
    }
    
    override fun onMessageReceived(message: Any) {
        // Обработка полученных сообщений
        when (message) {
            is GameMessage.PlaneMovement -> {
                // Обновляем позицию самолета от другого игрока
                binding.gameView.updateRemotePlane(
                    message.playerId,
                    message.position,
                    message.rotation,
                    message.velocity
                )
            }
            
            is GameMessage.Fire -> {
                // Другой игрок выстрелил
                binding.gameView.createRemoteBullet(
                    message.playerId,
                    message.position,
                    message.velocity,
                    message.color
                )
            }
            
            is GameMessage.Eject -> {
                // Другой игрок катапультировался
                binding.gameView.ejectRemotePilot(
                    message.playerId,
                    message.position
                )
            }
            
            is GameMessage.GameOver -> {
                // Получено сообщение о завершении игры
                runOnUiThread {
                    showGameOver()
                }
            }
            
            else -> {
                // Игнорируем другие типы сообщений
                Log.d(TAG, "Получено неизвестное сообщение: ${message.javaClass.simpleName}")
            }
        }
    }
    
    override fun onNetworkError(errorMessage: String) {
        runOnUiThread {
            Toast.makeText(this, "Ошибка сети: $errorMessage", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Network error: $errorMessage")
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
        // Останавливаем обработчики и освобождаем ресурсы
        handler.removeCallbacks(controlsRunnable)
        handler.removeCallbacks(joystickUpdateRunnable)
        
        // Останавливаем сетевой сервис
        if (::networkService.isInitialized) {
            try {
                networkService.stop()
                Log.d(TAG, "NetworkService остановлен в onDestroy")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при остановке NetworkService: ${e.message}")
            }
        }
        
        // Останавливаем игровой поток
        binding.gameView.stopGame()
        
        super.onDestroy()
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
     * Показывает диалог готовности только в мультиплеерном режиме
     */
    private fun showReadyDialog() {
        if (isMultiplayer) {
            AlertDialog.Builder(this)
                .setTitle("Готовы?")
                .setMessage("Вы готовы начать игру?")
                .setPositiveButton("Готов") { _, _ ->
                    playerReady = true
                    networkService.sendMessage("READY")
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
                    networkService.sendMessage("RESTART")
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
     * Метод для сохранения игровых логов
     */
    private fun saveGameLogs() {
        try {
            // Проверяем разрешения для Android 6.0-9.0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val hasWritePermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                    PackageManager.PERMISSION_GRANTED
                
                if (!hasWritePermission) {
                    // Запрашиваем разрешение
                    requestPermissions(
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 
                        PERMISSION_REQUEST_WRITE_STORAGE
                    )
                    return
                }
            }
            
            // Используем LogManager для сохранения логов
            val result = com.example.biplanes.game.utils.LogManager.saveLogsToFile(this)
            
            // Показываем результат пользователю
            val message = if (result) {
                "Логи успешно сохранены в папку Downloads"
            } else {
                "Не удалось сохранить логи"
            }
            
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Попытка сохранения логов: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении логов: ${e.message}", e)
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startGame() {
        isGameStarted = true
        startJoystickUpdates()
        
        // Запускаем в любом случае основной цикл обновления
        handler.post(controlsRunnable)
        
        Log.d(TAG, "Игра запущена: gameType=$gameType, isHost=$isHost, planeColor=$playerColor, playerId=$playerId, число игроков=${players.size}")
        
        // Добавляем Toast с информацией о режиме игры для отладки
        runOnUiThread {
            Toast.makeText(
                this,
                "Режим: ${if (isMultiplayer) "Мультиплеер" else "Тренировка"}, Хост: $isHost, Цвет: $playerColor",
                Toast.LENGTH_LONG
            ).show()
        }
        
        // Если это мультиплеер, начинаем отправлять данные о положении самолета сразу
        if (isMultiplayer) {
            // Отправляем данные о положении самолета сразу после старта игры
            Handler(Looper.getMainLooper()).postDelayed({
                sendInitialPlaneInfo()
            }, 500)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PERMISSION_REQUEST_WRITE_STORAGE -> {
                // Если запрос был отменен, то массив результатов будет пустым
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    // Разрешение получено, продолжаем сохранение логов
                    saveGameLogs()
                } else {
                    // Разрешение не получено, показываем сообщение
                    Toast.makeText(
                        this, 
                        "Для сохранения логов необходимо разрешение на запись файлов", 
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }
        }
    }

    private fun sendInitialPlaneInfo() {
        // Принудительно отправляем начальные данные о самолете
        val playerPlane = binding.gameView.getPlayerPlane()
        if (playerPlane != null && isMultiplayer) {
            Log.d(TAG, "Отправляем данные о самолете при старте: ID=$playerId")
            
            val message = GameMessage.PlaneMovement(
                playerId = playerId,
                position = playerPlane.position,
                rotation = playerPlane.rotation,
                velocity = playerPlane.velocity
            )
            
            try {
                // Отправляем несколько раз для надежности с короткими интервалами
                Thread {
                    try {
                        // Отправляем сообщение каждые 100 мс в течение 1 секунды
                        for (i in 0..10) {
                            networkService.sendMessage(message)
                            Thread.sleep(100)
                        }
                        
                        Log.d(TAG, "Начальные данные о самолете отправлены: позиция=(${playerPlane.position.x}, ${playerPlane.position.y})")
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка отправки начальных данных о самолете: ${e.message}")
                    }
                }.start()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка запуска потока для отправки данных о самолете: ${e.message}")
            }
        } else {
            Log.e(TAG, "Не удалось получить самолет игрока для отправки начальных данных")
        }
    }
} 