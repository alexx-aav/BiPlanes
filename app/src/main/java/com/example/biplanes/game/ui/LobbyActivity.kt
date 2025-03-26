package com.example.biplanes.game.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biplanes.R
import com.example.biplanes.game.models.GameType
import com.example.biplanes.game.models.PlaneColor
import com.example.biplanes.game.models.Player
import com.example.biplanes.network.GameMessage
import com.example.biplanes.network.NetworkService
import com.example.biplanes.network.NetworkService.ServiceMode
import java.util.UUID

class LobbyActivity : AppCompatActivity(), NetworkService.NetworkListener {
    
    companion object {
        private const val TAG = "LobbyActivity"
        private const val REQUEST_PERMISSIONS = 1001
    }
    
    private lateinit var lobbyTitleTextView: TextView
    private lateinit var gameTypeTextView: TextView
    private lateinit var roomCodeTextView: TextView
    private lateinit var waitingTextView: TextView
    private lateinit var playersRecyclerView: RecyclerView
    private lateinit var startGameButton: Button
    private lateinit var backButton: Button
    private lateinit var menuPlaneView: MenuPlaneView
    
    private lateinit var playerAdapter: PlayerAdapter
    private val players = mutableListOf<Player>()
    
    private var isHost = false
    private var gameType = GameType.ONE_VS_ONE
    private var selectedColor = PlaneColor.BLUE
    private var roomCode = ""
    private var playerId = ""
    
    // Сетевой сервис для работы с локальной сетью
    private lateinit var networkService: NetworkService
    
    // Список обнаруженных серверов
    private val discoveredServers = mutableListOf<NetworkService.ServerInfo>()
    
    // Диалоги
    private var serverListDialog: AlertDialog? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lobby)
        
        // Генерируем уникальный ID для игрока
        playerId = UUID.randomUUID().toString()
        
        // Получаем параметры из Intent
        isHost = intent.getBooleanExtra("isHost", false)
        gameType = intent.getSerializableExtra("gameType") as GameType? ?: GameType.ONE_VS_ONE
        selectedColor = intent.getSerializableExtra("planeColor") as PlaneColor? ?: PlaneColor.BLUE
        
        // Инициализируем UI
        initViews()
        setupUI()
        
        // Настраиваем RecyclerView
        setupRecyclerView()
        
        // Добавляем текущего игрока
        addCurrentPlayer()
        
        // Если хост, генерируем код комнаты
        if (isHost) {
            generateRoomCode()
            
            // Дополнительная проверка для отображения кода комнаты
            Handler(Looper.getMainLooper()).postDelayed({
                if (roomCodeTextView.text.isNullOrEmpty() || !roomCodeTextView.text.contains(roomCode)) {
                    Log.d(TAG, "Room code not displayed correctly, updating UI again")
                    updateUI()
                }
            }, 500) // Проверяем через 500 мс
        } else {
            // Если не хост, то код комнаты должен быть передан
            roomCode = intent.getStringExtra("roomCode") ?: "UNKNOWN"
        }
        
        // Обновляем UI
        updateUI()
        
        // Инициализируем сетевой сервис
        initNetworkService()
    }
    
    private fun initViews() {
        lobbyTitleTextView = findViewById(R.id.lobbyTitleTextView)
        gameTypeTextView = findViewById(R.id.gameTypeTextView)
        roomCodeTextView = findViewById(R.id.roomCodeTextView)
        waitingTextView = findViewById(R.id.waitingTextView)
        playersRecyclerView = findViewById(R.id.playersRecyclerView)
        startGameButton = findViewById(R.id.startGameButton)
        backButton = findViewById(R.id.backButton)
        menuPlaneView = findViewById(R.id.menuPlaneView)
    }
    
    private fun setupUI() {
        // Настраиваем заголовок в зависимости от роли
        val gameTypeShortText = when (gameType) {
            GameType.ONE_VS_ONE -> "1 на 1"
            GameType.TWO_VS_TWO -> "2 на 2"
            GameType.FREE_FOR_ALL -> "Каждый за себя"
            GameType.TRAINING -> "Тренировка"
        }
        
        // Добавляем тип игры в заголовок
        lobbyTitleTextView.text = if (isHost) "Ваша игра ($gameTypeShortText)" else "Подключение к игре ($gameTypeShortText)"
        
        // Устанавливаем темно-синий цвет для заголовка
        lobbyTitleTextView.setTextColor(android.graphics.Color.parseColor("#002060"))
        
        // Настраиваем тип игры
        gameTypeTextView.visibility = View.GONE
        
        // Настраиваем кнопку начала игры
        startGameButton.visibility = if (isHost) View.VISIBLE else View.GONE
        startGameButton.setOnClickListener {
            startGame()
        }
        
        // Настраиваем кнопку назад
        backButton.setOnClickListener {
            finish()
        }
        
        // Настраиваем MenuPlaneView
        menuPlaneView.setPlaneColor(selectedColor.color)
        
        // Добавляем обработчик нажатия на текст кода комнаты для хоста
        if (isHost) {
            roomCodeTextView.setOnClickListener {
                showRoomCodeDialog()
            }
        }
    }
    
    private fun setupRecyclerView() {
        playerAdapter = PlayerAdapter(players)
        playersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@LobbyActivity)
            adapter = playerAdapter
        }
    }
    
    private fun addCurrentPlayer() {
        val currentPlayer = Player(
            id = playerId,
            name = "Вы",
            color = selectedColor,
            isReady = true,
            isHost = isHost
        )
        players.add(currentPlayer)
        playerAdapter.notifyItemInserted(players.size - 1)
    }
    
    private fun generateRoomCode() {
        // Генерируем случайный код комнаты
        val allowedChars = ('A'..'Z') + ('0'..'9')
        roomCode = (1..6)
            .map { allowedChars.random() }
            .joinToString("")
        
        Log.d(TAG, "Generated room code: $roomCode")
        updateUI()
    }
    
    private fun showRoomCodeDialog() {
        if (!isHost) return
        
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Код комнаты")
        
        // Создаем TextView для отображения кода с большим шрифтом
        val textView = TextView(this)
        textView.text = roomCode
        textView.textSize = 40f
        textView.setTextColor(resources.getColor(android.R.color.holo_red_light, null))
        textView.gravity = android.view.Gravity.CENTER
        textView.setPadding(20, 40, 20, 40)
        
        builder.setView(textView)
        
        builder.setMessage("Сообщите этот код другим игрокам для подключения")
        
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        
        builder.setNeutralButton("Скопировать") { _, _ ->
            // Копируем код в буфер обмена
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Room Code", roomCode)
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(this, "Код скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
        }
        
        builder.show()
    }
    
    private fun updateUI() {
        // Обновляем код комнаты с особым выделением для хоста
        if (isHost) {
            // Делаем код комнаты более заметным
            roomCodeTextView.text = "КОД КОМНАТЫ: $roomCode"
            roomCodeTextView.setTextColor(resources.getColor(android.R.color.white, null))
            roomCodeTextView.textSize = 20f // Уменьшаем размер шрифта для лучшего вида
            
            // Скрываем текст ожидания для хоста, чтобы увеличить место для списка игроков
            waitingTextView.visibility = View.GONE
            
            // Логируем код комнаты для отладки
            Log.d(TAG, "Displaying room code: $roomCode")
        } else {
            // Если не хост, то просто отображаем код комнаты
            roomCodeTextView.text = "Код комнаты: $roomCode"
            
            // Обновляем текст ожидания для клиента
            waitingTextView.text = "Ожидание начала игры..."
            waitingTextView.visibility = View.VISIBLE
        }
        
        // Обновляем видимость элементов для не-хоста
        if (!isHost) {
            waitingTextView.visibility = if (players.size < getRequiredPlayerCount()) View.VISIBLE else View.GONE
        }
        
        // Обновляем доступность кнопки начала игры
        startGameButton.isEnabled = isHost && players.size >= getRequiredPlayerCount() && 
                                    players.all { it.isReady }
    }
    
    private fun getRequiredPlayerCount(): Int {
        return when (gameType) {
            GameType.ONE_VS_ONE -> 2
            GameType.TWO_VS_TWO -> 4
            GameType.FREE_FOR_ALL -> 3
            GameType.TRAINING -> 1
        }
    }
    
    private fun initNetworkService() {
        // Проверяем разрешения
        if (checkAndRequestPermissions()) {
            // Инициализируем сетевой сервис
            networkService = NetworkService(this)
            networkService.setListener(this)
            networkService.start()
            
            // Добавляем задержку перед началом работы с сетью
            Handler(Looper.getMainLooper()).postDelayed({
                if (isHost) {
                    // Если хост, создаем сервер лобби с кодом комнаты
                    networkService.createLobbyServer(roomCode)
                    
                    Toast.makeText(
                        this,
                        "Лобби создано. Ожидание подключений...",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // Если не хост, показываем диалог выбора сервера
                    showServerListDialog()
                }
            }, 1000) // Задержка 1 секунда
        }
    }
    
    private fun checkAndRequestPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        
        // Для Android 10+ нужно разрешение ACCESS_FINE_LOCATION для сетевых операций
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        // Запрашиваем разрешения, если они не предоставлены
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                REQUEST_PERMISSIONS
            )
            return false
        }
        
        return true
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Все разрешения предоставлены, инициализируем сетевой сервис
                initNetworkService()
            } else {
                // Разрешения не предоставлены, показываем сообщение
                Toast.makeText(
                    this,
                    "Для работы мультиплеера необходимы разрешения",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
    
    private fun showServerListDialog() {
        // Проверяем, не уничтожена ли активность перед показом диалога
        if (isFinishing || isDestroyed) {
            Log.d(TAG, "Активность завершена, отмена показа диалога")
            return
        }
        
        // Создаем диалог выбора сервера
        val builder = AlertDialog.Builder(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_server_list, null)
        
        val titleTextView = dialogView.findViewById<TextView>(R.id.titleTextView)
        val serversRecyclerView = dialogView.findViewById<RecyclerView>(R.id.serversRecyclerView)
        val emptyTextView = dialogView.findViewById<TextView>(R.id.emptyTextView)
        val refreshButton = dialogView.findViewById<Button>(R.id.refreshButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        
        // Настраиваем RecyclerView
        val serverAdapter = ServerAdapter(discoveredServers) { serverInfo ->
            // Обработка нажатия на сервер
            serverListDialog?.dismiss()
            connectToServer(serverInfo)
        }
        
        serversRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@LobbyActivity)
            adapter = serverAdapter
        }
        
        // Настраиваем кнопки
        refreshButton.setOnClickListener {
            // Обновляем список серверов
            discoveredServers.clear()
            serverAdapter.notifyDataSetChanged()
            emptyTextView.visibility = View.VISIBLE
            
            networkService.stopDiscovery()
            networkService.discoverServers()
            
            Toast.makeText(
                this,
                "Поиск серверов...",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        cancelButton.setOnClickListener {
            serverListDialog?.dismiss()
            finish()
        }
        
        // Обновляем видимость элементов
        emptyTextView.visibility = if (discoveredServers.isEmpty()) View.VISIBLE else View.GONE
        
        builder.setView(dialogView)
        builder.setCancelable(false)
        
        try {
            serverListDialog = builder.create()
            serverListDialog?.show()
            
            // Начинаем поиск серверов
            networkService.discoverServers()
            
            Toast.makeText(
                this,
                "Поиск серверов...",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при показе диалога: ${e.message}", e)
        }
    }
    
    private fun connectToServer(serverInfo: NetworkService.ServerInfo) {
        Toast.makeText(
            this,
            "Подключение к серверу ${serverInfo.name}...",
            Toast.LENGTH_SHORT
        ).show()
        
        networkService.connectToServer(serverInfo)
    }
    
    private fun startGame() {
        // Отправляем сообщение о начале игры всем игрокам
        val startGameMessage = GameMessage.StartGame(
            gameType = gameType,
            players = players
        )
        networkService.sendMessage(startGameMessage)
        
        // Запускаем игру
        startGameActivity()
    }
    
    private fun startGameActivity() {
        Log.d(TAG, "Подготовка к запуску GameActivity...")
        
        try {
            // Подготавливаем NetworkService к переходу
            networkService.prepareTransferToNextActivity()
            
            // Создаем Intent для запуска GameActivity
            val intent = Intent(this, GameActivity::class.java).apply {
                putExtra("gameType", gameType)
                putExtra("isHost", isHost)
                putExtra("planeColor", players.find { it.id == playerId }?.color ?: PlaneColor.RED)
                putExtra("playerId", playerId)
                putExtra("players", ArrayList(players))
            }
            
            // Запускаем Activity
            startActivity(intent)
            Log.d(TAG, "GameActivity запущена")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при запуске GameActivity: ${e.message}", e)
            Toast.makeText(
                this,
                "Ошибка при запуске игры: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // Реализация методов интерфейса NetworkListener
    
    override fun onServerDiscovered(serverInfo: NetworkService.ServerInfo) {
        Log.d(TAG, "Обнаружен сервер: ${serverInfo.name}")
        
        // Интересуют только серверы лобби
        if (serverInfo.name.startsWith(NetworkService.LOBBY_PREFIX)) {
            runOnUiThread {
                // Проверяем наличие этого сервера в списке
                val serverExists = discoveredServers.any { it.id == serverInfo.id }
                
                if (!serverExists) {
                    // Если сервер новый, добавляем его
                    discoveredServers.add(serverInfo)
                    
                    // Обновляем UI
                    serverListDialog?.let { dialog ->
                        val recyclerView = dialog.findViewById<RecyclerView>(R.id.serversRecyclerView)
                        val emptyTextView = dialog.findViewById<TextView>(R.id.emptyTextView)
                        
                        recyclerView?.adapter?.notifyItemInserted(discoveredServers.size - 1)
                        emptyTextView?.visibility = if (discoveredServers.isEmpty()) View.VISIBLE else View.GONE
                    }
                    
                    // Если клиент и есть roomCode, проверяем соответствие
                    if (!isHost && roomCode.isNotEmpty()) {
                        if (serverInfo.name == "${NetworkService.LOBBY_PREFIX}$roomCode") {
                            // Найден искомый сервер, подключаемся автоматически
                            Log.d(TAG, "Найден сервер с нужным кодом комнаты: $roomCode")
                            serverListDialog?.dismiss()
                            connectToServer(serverInfo)
                        }
                    }
                }
            }
        }
    }
    
    private fun sendLobbyData() {
        try {
            if (isHost) {
                // Если мы хост, отправляем данные о всех игроках всем клиентам
                players.forEach { player ->
                    val joinMessage = GameMessage.JoinLobby(player = player, gameType = gameType)
                    networkService.sendMessage(joinMessage)
                    Log.d(TAG, "Хост отправил информацию о игроке: ID=${player.id}, Name=${player.name}")
                }
                
                // Также отправляем тип игры
                val gameTypeMessage = GameMessage.UpdateGameType(gameType = gameType)
                networkService.sendMessage(gameTypeMessage)
                Log.d(TAG, "Хост отправил тип игры: $gameType")
            } else {
                // Если мы клиент, отправляем только данные о себе
                val currentPlayer = players.first() // Мы всегда первый в своем списке
                val joinMessage = GameMessage.JoinLobby(player = currentPlayer, gameType = gameType)
                networkService.sendMessage(joinMessage)
                Log.d(TAG, "Клиент отправил информацию о себе: ID=${currentPlayer.id}, Name=${currentPlayer.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при отправке данных лобби: ${e.message}", e)
        }
    }
    
    override fun onClientConnected(clientId: String) {
        Log.d(TAG, "Клиент подключился: $clientId")
        
        // Если мы хост, отправляем информацию о лобби
        if (isHost) {
            runOnUiThread {
                Toast.makeText(this, "Клиент подключился", Toast.LENGTH_SHORT).show()
                
                // Не создаем игрока здесь, ждем информацию от клиента
                Log.d(TAG, "Ожидаем информацию о игроке от клиента: $clientId")
                
                // Отправляем информацию о лобби новому клиенту
                // Используем небольшую задержку для надежности
                Handler(Looper.getMainLooper()).postDelayed({
                    sendLobbyData()
                }, 300)
            }
        }
    }
    
    override fun onClientDisconnected(clientId: String) {
        Log.d(TAG, "Клиент отключился: $clientId")
        
        // Удаляем игрока из списка
        val playerIndex = players.indexOfFirst { it.id == clientId }
        if (playerIndex != -1) {
            runOnUiThread {
                players.removeAt(playerIndex)
                playerAdapter.notifyItemRemoved(playerIndex)
                
                // Обновляем UI
                updateUI()
            }
        }
    }
    
    override fun onMessageReceived(message: Any) {
        Log.d(TAG, "Получено сообщение: ${message.javaClass.simpleName}, Содержимое: $message")
        
        // Обрабатываем разные типы сообщений
        when (message) {
            is GameMessage.JoinLobby -> {
                // Получено сообщение о присоединении игрока
                val player = message.player
                Log.d(TAG, "Обрабатываем JoinLobby для игрока: ID=${player.id}, Name=${player.name}, Color=${player.color}, IsHost=${player.isHost}")
                
                runOnUiThread {
                    // Проверяем, есть ли уже такой игрок
                    val existingPlayerIndex = players.indexOfFirst { it.id == player.id }
                    
                    if (existingPlayerIndex == -1) {
                        // Если игрока нет в списке, добавляем его
                        Log.d(TAG, "Добавляем нового игрока: $player")
                        players.add(player)
                        playerAdapter.notifyItemInserted(players.size - 1)
                        
                        // Показываем Toast для отладки
                        Toast.makeText(this, 
                            "Добавлен игрок: ${player.name}", 
                            Toast.LENGTH_SHORT).show()
                    } else {
                        // Если игрок уже в списке, обновляем его данные
                        Log.d(TAG, "Обновляем данные игрока: $player")
                        players[existingPlayerIndex] = player
                        playerAdapter.notifyItemChanged(existingPlayerIndex)
                    }
                    
                    // Выводим текущий список игроков
                    Log.d(TAG, "Текущий список игроков (${players.size}):")
                    players.forEachIndexed { index, p ->
                        Log.d(TAG, "[$index] ID=${p.id}, Name=${p.name}, Color=${p.color}, IsHost=${p.isHost}")
                    }
                    
                    // Обновляем UI
                    updateUI()
                }
            }
            
            is GameMessage.UpdateGameType -> {
                // Получено сообщение об обновлении типа игры
                val updatedGameType = message.gameType
                Log.d(TAG, "Обрабатываем UpdateGameType: $updatedGameType")
                
                runOnUiThread {
                    // Обновляем тип игры
                    gameType = updatedGameType
                    
                    // Обновляем заголовок
                    val gameTypeShortText = when (gameType) {
                        GameType.ONE_VS_ONE -> "1 на 1"
                        GameType.TWO_VS_TWO -> "2 на 2"
                        GameType.FREE_FOR_ALL -> "Каждый за себя"
                        GameType.TRAINING -> "Тренировка"
                    }
                    lobbyTitleTextView.text = if (isHost) 
                        "Ваша игра ($gameTypeShortText)" 
                    else 
                        "Подключение к игре ($gameTypeShortText)"
                    
                    // Обновляем UI
                    updateUI()
                    
                    // Показываем Toast для отладки
                    Toast.makeText(this, 
                        "Получен тип игры: $gameTypeShortText", 
                        Toast.LENGTH_SHORT).show()
                }
            }
            
            is GameMessage.PlayerReady -> {
                // Обновляем статус готовности игрока
                val playerId = message.playerId
                val isReady = message.isReady
                Log.d(TAG, "Обрабатываем PlayerReady: ID=$playerId, IsReady=$isReady")
                
                runOnUiThread {
                    // Обновляем статус игрока
                    val playerIndex = players.indexOfFirst { it.id == playerId }
                    if (playerIndex != -1) {
                        val player = players[playerIndex]
                        players[playerIndex] = player.copy(isReady = isReady)
                        playerAdapter.notifyItemChanged(playerIndex)
                        
                        // Обновляем UI
                        updateUI()
                    } else {
                        Log.e(TAG, "Не найден игрок с ID=$playerId для обновления статуса готовности")
                    }
                }
            }
            
            is GameMessage.StartGame -> {
                // Запускаем игру
                Log.d(TAG, "Получено сообщение о начале игры")
                runOnUiThread {
                    startGameActivity()
                }
            }
            
            else -> {
                Log.d(TAG, "Получено неизвестное сообщение типа: ${message.javaClass.name}")
            }
        }
    }
    
    override fun onNetworkError(errorMessage: String) {
        runOnUiThread {
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            Log.e(TAG, "Network error: $errorMessage")
            // Обновляем UI
            waitingTextView.text = "Ошибка: $errorMessage"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        try {
            // Останавливаем сетевой сервис
            if (::networkService.isInitialized) {
                networkService.stop()
                Log.d(TAG, "NetworkService остановлен в onDestroy")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при остановке NetworkService в onDestroy: ${e.message}")
        }
        
        // Закрываем диалог выбора сервера
        serverListDialog?.dismiss()
    }
    
    override fun onConnectionChanged(isConnected: Boolean, serverAddress: String?) {
        Log.d(TAG, "Статус соединения изменился: isConnected=$isConnected, serverAddress=$serverAddress")
        
        runOnUiThread {
            if (isConnected) {
                waitingTextView.text = "Подключено к серверу"
                
                // Если мы клиент и подключились, отправляем свою информацию
                if (!isHost) {
                    // Добавляем задержку перед отправкой, чтобы дать время серверу подготовиться
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            sendLobbyData()
                            
                            // Дополнительно показываем Toast для отладки
                            Toast.makeText(this@LobbyActivity, 
                                "Отправлены данные на сервер", 
                                Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e(TAG, "Ошибка при отправке данных о себе: ${e.message}", e)
                            Toast.makeText(this@LobbyActivity, 
                                "Ошибка: ${e.message}", 
                                Toast.LENGTH_SHORT).show()
                        }
                    }, 500) // Задержка 500 мс
                }
            } else {
                waitingTextView.text = "Ожидание подключения..."
            }
        }
    }
}

// Адаптер для списка игроков
class PlayerAdapter(private val players: List<Player>) : 
    RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PlayerViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_player, parent, false)
        return PlayerViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(players[position])
    }
    
    override fun getItemCount() = players.size
    
    class PlayerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val playerColorView: View = itemView.findViewById(R.id.playerColorView)
        private val playerNameTextView: TextView = itemView.findViewById(R.id.playerNameTextView)
        private val playerStatusTextView: TextView = itemView.findViewById(R.id.playerStatusTextView)
        
        fun bind(player: Player) {
            // Устанавливаем цвет самолета игрока
            playerColorView.setBackgroundColor(player.color.color)
            
            // Добавляем скругление для цветового индикатора
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.OVAL
            shape.setColor(player.color.color)
            playerColorView.background = shape
            
            // Устанавливаем имя игрока с более заметным выделением для хоста
            val displayName = if (player.isHost) "${player.name} (Хост)" else player.name
            playerNameTextView.text = displayName
            
            // Если игрок - хост, делаем его имя более заметным
            if (player.isHost) {
                playerNameTextView.setTypeface(null, Typeface.BOLD)
            } else {
                playerNameTextView.setTypeface(null, Typeface.NORMAL)
            }
            
            // Устанавливаем статус с более яркими цветами
            val statusText = if (player.isReady) "Готов" else "Не готов"
            val statusColor = if (player.isReady) 
                android.graphics.Color.parseColor("#4CAF50") // Яркий зеленый
            else 
                android.graphics.Color.parseColor("#F44336") // Яркий красный
            
            playerStatusTextView.text = statusText
            playerStatusTextView.setTextColor(statusColor)
        }
    }
}

// Адаптер для списка серверов
class ServerAdapter(
    private val servers: List<NetworkService.ServerInfo>,
    private val onServerSelected: (NetworkService.ServerInfo) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ServerViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ServerViewHolder(view, onServerSelected)
    }
    
    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(servers[position])
    }
    
    override fun getItemCount() = servers.size
    
    class ServerViewHolder(
        itemView: View,
        private val onServerSelected: (NetworkService.ServerInfo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val serverNameTextView: TextView = itemView.findViewById(R.id.serverNameTextView)
        private val serverAddressTextView: TextView = itemView.findViewById(R.id.serverAddressTextView)
        
        fun bind(serverInfo: NetworkService.ServerInfo) {
            // Устанавливаем имя сервера
            serverNameTextView.text = serverInfo.name
            
            // Устанавливаем адрес сервера
            serverAddressTextView.text = "${serverInfo.host}:${serverInfo.port}"
            
            // Добавляем обработчик нажатия
            itemView.setOnClickListener {
                onServerSelected(serverInfo)
            }
        }
    }
} 