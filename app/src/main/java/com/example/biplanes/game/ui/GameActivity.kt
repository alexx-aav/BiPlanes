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
import com.example.biplanes.BiplanesApplication
import java.util.ArrayList
import java.util.UUID
import android.content.Context

/**
 * Управляет игровым процессом и пользовательским интерфейсом.
 */
class GameActivity : AppCompatActivity(), NetworkService.NetworkListener {
    private val TAG = "GameActivity"

    // Константы для запросов разрешений
    private val PERMISSION_REQUEST_WRITE_STORAGE = 101

    // ViewBinding
    private lateinit var binding: ActivityGameBinding
    
    private lateinit var gameManager: GameManager
    
    private var isMultiplayer = false
    private var isHost = false
    private var playerId = ""
    private var gameType = GameType.TRAINING
    private lateinit var playerColor: PlaneColor
    private var players = mutableListOf<Player>()

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var networkService: NetworkService
    
    private var playerReady = false
    private var enemyReady = false
    private var connectedServerAddress: String? = null

    
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
        
        // Получаем список игроков и обрабатываем возможные проблемы
        @Suppress("UNCHECKED_CAST")
        val receivedPlayers = intent.getSerializableExtra("players") as? ArrayList<Player>
        
        // Проверяем полученный список игроков
        if (receivedPlayers != null && receivedPlayers.isNotEmpty()) {
            players = receivedPlayers.toMutableList()
            Log.d(TAG, "Получен список игроков из Intent: ${players.size}")
        } else {
            // Пробуем восстановить список из SharedPreferences
            val loadedPlayers = loadPlayersFromPreferences()
            if (loadedPlayers.isNotEmpty()) {
                players = loadedPlayers.toMutableList()
                Log.d(TAG, "Восстановлен список игроков из SharedPreferences: ${players.size}")
            } else {
                Log.w(TAG, "Список игроков пуст или не получен! Создаем минимальный список.")
                
                // Создаем минимальный список игроков
                players = mutableListOf(
                    Player(
                        id = playerId.ifEmpty { UUID.randomUUID().toString().also { playerId = it } },
                        name = "Вы",
                        color = playerColor,
                        isReady = true,
                        isHost = isHost
                    )
                )
                
                // Если это мультиплеер, добавляем еще одного игрока для полноты
                if (gameType != GameType.TRAINING) {
                    val enemyColor = if (playerColor == PlaneColor.RED) PlaneColor.BLUE else PlaneColor.RED
                    players.add(
                        Player(
                            id = UUID.randomUUID().toString(),
                            name = "Противник",
                            color = enemyColor,
                            isReady = true,
                            isHost = !isHost
                        )
                    )
                }
                
                Log.d(TAG, "Создан минимальный список игроков: ${players.size}")
            }
        }
        
        // Сохраняем список игроков в SharedPreferences для возможного восстановления
        savePlayersToPreferences(players)
        players.forEachIndexed { index, player ->
            Log.d(TAG, "Игрок $index: id=${player.id}, имя=${player.name}, цвет=${player.color}, хост=${player.isHost}")
        }

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

        gameManager = GameManager(
            gameType = gameType,
            isHost = isHost,
            playerColor = playerColor,
            playerId = playerId,
            players = players,
            gameView = binding.gameView,
            fireButton = binding.fireButton,
            ejectButton = binding.ejectButton,
            pauseOverlay = binding.pauseOverlay,
            gameOverOverlay = binding.gameOverOverlay,
            joystick = binding.joystick,
            gameActivityCallback = object : GameManager.GameActivityCallback {
    }
            override fun sendMessage(message: Any) {
                networkService.sendMessage(message)
            }

            override fun runOnUi(action: () -> Unit) {
                runOnUiThread(action)
            }

            override fun onGameStarted(isMultiplayer: Boolean) {
                this@GameActivity.isMultiplayer = isMultiplayer
            }
        })
        
        // Инициализируем сетевой сервис, если это мультиплеерная игра
        if (isMultiplayer) {
            initNetworkService()
        } else {
            gameManager.startGame()
        }
    }
    
    private fun initNetworkService() {
        // Получаем NetworkService из приложения вместо создания нового
        val app = application as? BiplanesApplication
            ?: throw IllegalStateException("Application не является BiplanesApplication")
        
        networkService = app.getNetworkService()
            ?: NetworkService(this).also { app.setNetworkService(it) }
        
        // Устанавливаем новый слушатель
        networkService.setListener(this)
        
        // НЕ запускаем сервис заново, если он уже запущен
        if (!networkService.isRunning()) {
            networkService.start()
        }
        
        Log.d(TAG, "NetworkService инициализирован, isHost=$isHost")
        
        // Если это хост, создаем сервер
        if (isHost) {
            try {
                // Выводим список игроков для отладки
                Log.d(TAG, "Список игроков для создания сервера (${players.size}):")
                players.forEachIndexed { index, player ->
                    Log.d(TAG, "[$index] ID=${player.id}, Name=${player.name}, Color=${player.color}, IsHost=${player.isHost}")
                }
                
                // Проверяем, есть ли хост в списке игроков
                val hostPlayer = if (players.isNotEmpty()) {
                    players.find { it.isHost } ?: players.first()
                } else {
                    // Если список игроков пуст, создаем временного хоста на основе текущих данных
                    Log.d(TAG, "Список игроков пуст, создаем временного хоста с ID=$playerId")
                    Player(
                        id = playerId,
                        name = "Хост", 
                        color = playerColor,
                        isReady = true,
                        isHost = true
                    )
                }
                
                // Создаем игровой сервер с ID на основе ID хоста
                val gameId = "Game-${hostPlayer.id.substring(0, Math.min(8, hostPlayer.id.length))}"
                Log.d(TAG, "Создаем игровой сервер с ID: $gameId на основе хоста: ${hostPlayer.id}")
                
                // Переключаем режим сервиса на игровой без остановки существующих соединений
                networkService.switchToGameMode(gameId)
                
                Log.d(TAG, "Игровой режим активирован с ID: $gameId")


            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при создании сервера: ${e.message}", e)
                
                // Повторная попытка с фиксированным ID в случае ошибки
                try {
                    val fallbackGameId = "Game-Fallback-$playerId"
                    Log.d(TAG, "Повторная попытка создания сервера с резервным ID: $fallbackGameId")
                    networkService.switchToGameMode(fallbackGameId)
                    Log.d(TAG, "Создан резервный игровой сервер с ID: $fallbackGameId")
                    

                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при создании резервного сервера: ${e.message}", e)
                }
            }
        } else {
            // Для клиента ничего особого делать не нужно - соединение уже установлено
            // Отправляем сообщение JoinGame для уведомления сервера
            sendJoinGameMessage()

        }
        
        // Отправляем начальные данные о самолете, чтобы другие могли его видеть
        handler.postDelayed({
            sendInitialPlaneInfo()
        }, 1000) // Немного задержки, чтобы самолет успел инициализироваться
    }

    private fun sendJoinGameMessage() {
        val player = players.find { it.id == playerId } ?: return
        val joinMessage = GameMessage.JoinGame(player)
        
        try {
            networkService.sendMessage(joinMessage)
            Log.d(TAG, "Отправлено сообщение о присоединении к игре")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при отправке сообщения о присоединении к игре: ${e.message}")
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
        
        binding.restartButton.setOnClickListener { restartGame() }

        binding.saveLogsButton.setOnClickListener { saveGameLogs() }
    }

    private fun updateControls() = gameManager.updateControls()

    private fun updateGame() = gameManager.updateGame()

    private fun togglePause() {
        gameManager.togglePause()
    }

    private fun showGameOver() = gameManager.showGameOver()

    private fun restartGame() {
        gameManager.restartGame()
    }

    private fun stopJoystickUpdates() = gameManager.stopJoystickUpdates()

    private fun startJoystickUpdates() {
        gameManager.startJoystickUpdates()
    }

    // Реализация методов интерфейса NetworkListener
    
    override fun onServerDiscovered(serverInfo: NetworkService.ServerInfo) {
        Log.d(TAG, "Обнаружен сервер: ${serverInfo.name}")
        
        // Подключаемся только к игровым серверам
        if (serverInfo.name.startsWith(NetworkService.GAME_PREFIX) && !isHost) {
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
        gameManager.onResume()
    }

    override fun onPause() {
        super.onPause()
        gameManager.onPause()
    }
    
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
            gameManager.startGame()
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
                        // Отправляем сообщение каждые 100 мс в течение 2 секунд
                        for (i in 0..20) {
                            networkService.sendMessage(message)
                            Thread.sleep(100)
                        }
                        
                        Log.d(TAG, "Начальные данные о самолете отправлены (20 раз): позиция=(${playerPlane.position.x}, ${playerPlane.position.y})")
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка отправки начальных данных о самолете: ${e.message}")
                    }
                }.start()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка запуска потока для отправки данных о самолете: ${e.message}")
            }
        } else {
            Log.e(TAG, "Не удалось получить самолет игрока для отправки начальных данных")
        }}
    }

    /**
     * Обработка изменения статуса соединения
     */
    override fun onConnectionStatusChanged(isConnected: Boolean, serverAddress: String?) {
        Log.d(TAG, "Статус соединения изменился: isConnected=$isConnected, serverAddress=$serverAddress")
        
        // Обновляем интерфейс и управление
        runOnUiThread {
            if (isConnected) {
                // Соединение установлено, показываем сообщение
                Toast.makeText(
                    this,
                    "Соединение с сервером установлено",
                    Toast.LENGTH_SHORT
                ).show()
                
                if (!gameManager.isGameStarted) {
                  startGame()
                }
                else{
                    Log.d(TAG, "Игра уже запущена.")
                    gameManager.updateControls()    
                }
            } else {
                // Соединение потеряно, показываем сообщение
                Toast.makeText(
                    this,
                    "Соединение с сервером потеряно",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Сохраняет список игроков в SharedPreferences
     */
    private fun savePlayersToPreferences(players: List<Player>) {
        try {
            val prefs = getSharedPreferences("GameData", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Сохраняем количество игроков
            editor.putInt("players_count", players.size)
            
            // Сохраняем данные каждого игрока
            players.forEachIndexed { index, player ->
                editor.putString("player_${index}_id", player.id)
                editor.putString("player_${index}_name", player.name)
                editor.putString("player_${index}_color", player.color.name)
                editor.putBoolean("player_${index}_isReady", player.isReady)
                editor.putBoolean("player_${index}_isHost", player.isHost)
            }
            
            // Сохраняем тип игры, если он установлен
            if (gameType != null) {
                editor.putString("game_type", gameType.name)
            }
            
            // Применяем изменения
            editor.apply()
            
            Log.d(TAG, "Сохранено ${players.size} игроков в SharedPreferences")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении списка игроков: ${e.message}", e)
        }
    }
    
    /**
     * Загружает список игроков из SharedPreferences
     */
    private fun loadPlayersFromPreferences(): List<Player> {
        val players = mutableListOf<Player>()
        
        try {
            val prefs = getSharedPreferences("GameData", Context.MODE_PRIVATE)
            val playersCount = prefs.getInt("players_count", 0)
            
            if (playersCount > 0) {
                // Загружаем данные каждого игрока
                for (i in 0 until playersCount) {
                    val id = prefs.getString("player_${i}_id", "") ?: ""
                    val name = prefs.getString("player_${i}_name", "") ?: ""
                    val colorName = prefs.getString("player_${i}_color", "RED") ?: "RED"
                    val isReady = prefs.getBoolean("player_${i}_isReady", true)
                    val isHost = prefs.getBoolean("player_${i}_isHost", false)
                    
                    // Преобразуем строковое имя цвета в enum
                    val color = try {
                        PlaneColor.valueOf(colorName)
                    } catch (e: Exception) {
                        PlaneColor.RED
                    }
                    
                    // Создаем игрока и добавляем в список
                    val player = Player(
                        id = id,
                        name = name,
                        color = color,
                        isReady = isReady,
                        isHost = isHost
                    )
                    
                    players.add(player)
                }
                
                Log.d(TAG, "Загружено ${players.size} игроков из SharedPreferences")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при загрузке списка игроков: ${e.message}", e)
        }
        
        return players
    }
    
    /**
     * Сохраняет ID игрового сервера в SharedPreferences
     */
    private fun saveGameServerIdToPreferences(serverId: String) {
        try {
            val prefs = getSharedPreferences("GameData", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putString("game_server_id", serverId)
            editor.apply()
            Log.d(TAG, "Сохранен ID игрового сервера в SharedPreferences: $serverId")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении ID игрового сервера: ${e.message}", e)
        }
    }
    
    /**
     * Загружает ID игрового сервера из SharedPreferences
     */
    private fun loadGameServerIdFromPreferences(): String {
        try {
            val prefs = getSharedPreferences("GameData", Context.MODE_PRIVATE)
            val serverId = prefs.getString("game_server_id", "") ?: ""
            if (serverId.isNotEmpty()) {
                Log.d(TAG, "Загружен ID игрового сервера из SharedPreferences: $serverId")
            }
            return serverId
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при загрузке ID игрового сервера: ${e.message}", e)
            return ""
        }
    }
}