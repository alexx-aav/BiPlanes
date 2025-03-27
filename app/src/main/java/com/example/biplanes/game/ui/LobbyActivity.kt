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
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.biplanes.BiplanesApplication
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
        private const val CONNECTION_RETRY_DELAY = 2000L // 2 секунды задержка для повторных попыток
        private const val CONNECTION_MAX_RETRIES = 3 // Максимальное число попыток переподключения
    }
    
    // UI компоненты
    private lateinit var lobbyTitleTextView: TextView
    private lateinit var gameTypeTextView: TextView
    private lateinit var roomCodeTextView: TextView
    private lateinit var waitingTextView: TextView
    private lateinit var playersRecyclerView: RecyclerView
    private lateinit var startGameButton: Button
    private lateinit var backButton: Button
    private lateinit var menuPlaneView: MenuPlaneView
    
    // Адаптер для списка игроков
    private lateinit var playerAdapter: PlayerAdapter
    private val players = mutableListOf<Player>()
    
    // Данные лобби
    private var isHost = false
    private var gameType = GameType.ONE_VS_ONE
    private var selectedColor = PlaneColor.BLUE
    private var roomCode = ""
    private var playerId = ""
    
    // Сетевой сервис
    private lateinit var networkService: NetworkService
    
    // Список обнаруженных серверов
    private val discoveredServers = mutableListOf<NetworkService.ServerInfo>()
    
    // Диалоги
    private var serverListDialog: AlertDialog? = null
    private var connectionProgressDialog: AlertDialog? = null
    
    // Счетчик попыток подключения
    private var connectionRetries = 0
    
    // Хэндлер для задержек
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lobby)
        
        // Генерируем уникальный ID для игрока, если его нет
        playerId = savedInstanceState?.getString("playerId") ?: UUID.randomUUID().toString()
        
        // Получаем параметры из Intent
        isHost = intent.getBooleanExtra("isHost", false)
        gameType = intent.getSerializableExtra("gameType") as GameType? ?: GameType.ONE_VS_ONE
        selectedColor = intent.getSerializableExtra("planeColor") as PlaneColor? ?: PlaneColor.BLUE
        
        // Если передан код комнаты, используем его
        roomCode = intent.getStringExtra("roomCode") ?: ""
        
        // Инициализируем UI и настраиваем обработчики
        initViews()
        setupUI()
        setupRecyclerView()
        
        // Добавляем текущего игрока
        addCurrentPlayer()
        
        // Если хост, генерируем код комнаты
        if (isHost && roomCode.isEmpty()) {
            generateRoomCode()
        }
        
        // Обновляем UI
        updateUI()
        
        // Проверяем разрешения и инициализируем сетевой сервис
        checkPermissionsAndInit()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("playerId", playerId)
        outState.putString("roomCode", roomCode)
        outState.putBoolean("isHost", isHost)
        outState.putSerializable("gameType", gameType)
        outState.putSerializable("selectedColor", selectedColor)
    }
    
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        playerId = savedInstanceState.getString("playerId") ?: playerId
        roomCode = savedInstanceState.getString("roomCode") ?: roomCode
        isHost = savedInstanceState.getBoolean("isHost", isHost)
        gameType = savedInstanceState.getSerializable("gameType") as GameType? ?: gameType
        selectedColor = savedInstanceState.getSerializable("selectedColor") as PlaneColor? ?: selectedColor
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
        lobbyTitleTextView.setTextColor(android.graphics.Color.parseColor("#002060"))
        
        // Скрываем лишние элементы
        gameTypeTextView.visibility = View.GONE
        
        // Кнопка начала игры доступна только хосту
        startGameButton.visibility = if (isHost) View.VISIBLE else View.GONE
        startGameButton.setOnClickListener {
            startGame()
        }
        
        // Кнопка "Назад"
        backButton.setOnClickListener {
            showExitConfirmDialog()
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
        // Генерируем случайный код комнаты из букв и цифр
        val allowedChars = ('A'..'Z') + ('0'..'9')
        roomCode = (1..6)
            .map { allowedChars.random() }
            .joinToString("")
        
        Log.d(TAG, "Сгенерирован код комнаты: $roomCode")
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
    
    private fun showExitConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Выход из лобби")
            .setMessage("Вы уверены, что хотите выйти из лобби?")
            .setPositiveButton("Да") { _, _ ->
                // Отправляем сообщение о выходе, если подключены к сети
                if (::networkService.isInitialized) {
                    sendLeaveMessage()
                    networkService.stop()
                }
                finish()
            }
            .setNegativeButton("Нет", null)
            .show()
    }
    
    private fun updateUI() {
        // Обновляем код комнаты с особым выделением для хоста
        if (isHost) {
            // Делаем код комнаты более заметным
            roomCodeTextView.text = "КОД КОМНАТЫ: $roomCode"
            roomCodeTextView.setTextColor(resources.getColor(android.R.color.white, null))
            roomCodeTextView.textSize = 20f
            
            // Скрываем текст ожидания для хоста
            waitingTextView.visibility = View.GONE
        } else {
            // Если не хост, то просто отображаем код комнаты
            roomCodeTextView.text = "Код комнаты: $roomCode"
            
            // Обновляем текст ожидания для клиента
            waitingTextView.text = "Ожидание начала игры..."
            waitingTextView.visibility = View.VISIBLE
        }
        
        // Обновляем кнопку начала игры - активна только если есть хотя бы 2 игрока
        if (isHost) {
            startGameButton.isEnabled = players.size >= 2
            startGameButton.alpha = if (players.size >= 2) 1.0f else 0.5f
        }
    }
    
    private fun checkPermissionsAndInit() {
        val permissionsNeeded = mutableListOf<String>()
        
        // Проверяем разрешения, необходимые для работы сети на Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        } else {
            initNetworkService()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initNetworkService()
            } else {
                Toast.makeText(this, "Для работы сетевой игры необходимы разрешения", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    
    private fun initNetworkService() {
        val app = application as? BiplanesApplication
            ?: throw IllegalStateException("Application не является BiplanesApplication")
        networkService = app.getNetworkService()
            ?: NetworkService(this).also { app.setNetworkService(it) }
        networkService.setListener(this)
        networkService.start()
        
        if (isHost) {
            // Хост создает сервер лобби
            networkService.createLobbyServer(roomCode)
            Log.d(TAG, "Создан сервер лобби с кодом: $roomCode")
        } else {
            // Клиент ищет серверы и подключается
            if (roomCode.isNotEmpty()) {
                showConnectingDialog()
                findAndConnectToLobby()
            } else {
                showServersListDialog()
            }
        }
    }
    
    private fun showConnectingDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Подключение")
        builder.setMessage("Поиск сервера с кодом комнаты: $roomCode...")
        builder.setCancelable(false)
        
        builder.setNegativeButton("Отмена") { dialog, _ ->
            dialog.dismiss()
            networkService.stopDiscovery()
            connectionRetries = 0
        }
        
        connectionProgressDialog = builder.create()
        connectionProgressDialog?.show()
    }
    
    private fun findAndConnectToLobby() {
        // Очищаем текущий список серверов
        discoveredServers.clear()
        
        // Запускаем поиск серверов
        networkService.discoverServers()
        
        // Используем хэндлер для проверки найденных серверов через некоторое время
        handler.postDelayed({
            checkDiscoveredServers()
        }, 3000) // Даем 3 секунды на поиск
    }
    
    private fun checkDiscoveredServers() {
        val servers = networkService.getDiscoveredServers()
        Log.d(TAG, "Найдено серверов: ${servers.size}")
        
        // Ищем сервер с нужным кодом комнаты
        val targetServerName = "${NetworkService.LOBBY_PREFIX}$roomCode"
        val server = servers.find { it.name.equals(targetServerName, ignoreCase = true) }
        
        if (server != null) {
            Log.d(TAG, "Найден целевой сервер: ${server.name} @ ${server.host}:${server.port}")
            connectionProgressDialog?.setMessage("Подключение к найденному серверу...")
            
            // Подключаемся к найденному серверу
            networkService.connectToServer(server)
        } else {
            connectionRetries++
            if (connectionRetries < CONNECTION_MAX_RETRIES) {
                // Пробуем еще раз
                Log.d(TAG, "Сервер не найден, повторная попытка $connectionRetries")
                connectionProgressDialog?.setMessage("Сервер не найден, повторная попытка $connectionRetries из $CONNECTION_MAX_RETRIES...")
                
                handler.postDelayed({
                    findAndConnectToLobby()
                }, CONNECTION_RETRY_DELAY)
            } else {
                // Превышено количество попыток
                connectionProgressDialog?.dismiss()
                showErrorDialog("Не удалось найти сервер с кодом комнаты: $roomCode")
                connectionRetries = 0
            }
        }
    }
    
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Ошибка")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showServersListDialog() {
        // Очищаем текущий список серверов
        discoveredServers.clear()
        
        // Создаем диалог
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Доступные игры")
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_server_list, null)
        val serversRecyclerView = dialogView.findViewById<RecyclerView>(R.id.serversRecyclerView)
        val refreshButton = dialogView.findViewById<Button>(R.id.refreshButton)
        
        builder.setView(dialogView)
        
        // Настраиваем RecyclerView
        val serverAdapter = ServerListAdapter(discoveredServers) { server ->
            // Обработчик нажатия на сервер
            serverListDialog?.dismiss()
            networkService.connectToServer(server)
            showConnectingProgressDialog()
        }
        
        serversRecyclerView.layoutManager = LinearLayoutManager(this)
        serversRecyclerView.adapter = serverAdapter
        
        // Кнопка обновления списка
        refreshButton.setOnClickListener {
            discoveredServers.clear()
            serverAdapter.notifyDataSetChanged()
            networkService.discoverServers()
        }
        
        builder.setOnDismissListener {
            serverListDialog = null
        }
        
        // Создаем и показываем диалог
        serverListDialog = builder.create()
        serverListDialog?.show()
        
        // Запускаем поиск серверов
        networkService.discoverServers()
    }
    
    private fun showManualConnectDialog() {
        // Удаляем этот метод, так как в данной реализации мы не используем ручной ввод кода
    }
    
    private fun showConnectingProgressDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Подключение")
        builder.setMessage("Подключение к выбранному серверу...")
        builder.setCancelable(false)
        
        builder.setNegativeButton("Отмена") { dialog, _ ->
            dialog.dismiss()
            // Закрываем соединение более чистым способом
            networkService.stop()
            networkService.start()
        }
        
        connectionProgressDialog = builder.create()
        connectionProgressDialog?.show()
    }
    
    override fun onServerDiscovered(serverInfo: NetworkService.ServerInfo) {
        // Новый сервер обнаружен
        Log.d(TAG, "Обнаружен сервер: ${serverInfo.name} @ ${serverInfo.host}:${serverInfo.port}")
        
        // Добавляем сервер в список, если его ещё нет
        val exists = discoveredServers.any { it.id == serverInfo.id }
        if (!exists) {
            // Проверяем, соответствует ли сервер текущему режиму (лобби)
            if (serverInfo.name.startsWith(NetworkService.LOBBY_PREFIX)) {
                discoveredServers.add(serverInfo)
                
                // Обновляем список серверов в диалоге, если он открыт
                if (serverListDialog != null && serverListDialog?.isShowing == true) {
                    runOnUiThread {
                        val serversRecyclerView = serverListDialog?.findViewById<RecyclerView>(R.id.serversRecyclerView)
                        serversRecyclerView?.adapter?.notifyDataSetChanged()
                    }
                }
            }
        }
    }
    
    override fun onConnectionChanged(isConnected: Boolean, serverAddress: String?) {
        Log.d(TAG, "Изменение статуса подключения: $isConnected, адрес: $serverAddress")
        
        // Закрываем диалог подключения
        connectionProgressDialog?.dismiss()
        
        if (isConnected) {
            // Успешно подключились к серверу
            Toast.makeText(this, "Подключено к серверу", Toast.LENGTH_SHORT).show()
            
            // Отправляем информацию о себе
            sendJoinLobbyMessage()
            
            // Обновляем UI
            runOnUiThread {
                updateUI()
            }
        } else {
            // Соединение разорвано
            if (isHost) {
                // Если мы хост, ничего не делаем
            } else {
                // Если мы клиент, показываем ошибку
                Toast.makeText(this, "Соединение с сервером потеряно", Toast.LENGTH_SHORT).show()
                
                // Показываем диалог переподключения
                showReconnectDialog()
            }
        }
    }
    
    private fun showReconnectDialog() {
        AlertDialog.Builder(this)
            .setTitle("Соединение потеряно")
            .setMessage("Соединение с сервером потеряно. Попробовать переподключиться?")
            .setPositiveButton("Да") { _, _ ->
                if (roomCode.isNotEmpty()) {
                    showConnectingDialog()
                    findAndConnectToLobby()
                } else {
                    showServersListDialog()
                }
            }
            .setNegativeButton("Нет") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    override fun onClientConnected(clientId: String) {
        Log.d(TAG, "Клиент подключен: $clientId")
        // Этот колбэк вызывается только у сервера
        
        // Отправляем новому клиенту текущий список игроков
        val playersList = players.toList()
        
        // Создаем сообщение с текущими игроками
        val updateMessage = GameMessage.UpdatePlayersList(playersList)
        
        // Отправляем сообщение всем клиентам
        networkService.sendMessage(updateMessage)
    }
    
    override fun onClientDisconnected(clientId: String) {
        Log.d(TAG, "Клиент отключен: $clientId")
        
        // Находим и удаляем отключившегося игрока
        val playerIndex = players.indexOfFirst { it.id == clientId }
        if (playerIndex != -1) {
            val player = players[playerIndex]
            
            runOnUiThread {
                players.removeAt(playerIndex)
                playerAdapter.notifyItemRemoved(playerIndex)
                updateUI()
                
                // Показываем уведомление
                Toast.makeText(this, "Игрок ${player.name} отключился", Toast.LENGTH_SHORT).show()
            }
            
            // Если мы сервер, уведомляем остальных клиентов
            if (networkService.isHost()) {
                val updateMessage = GameMessage.UpdatePlayersList(players.toList())
                networkService.sendMessage(updateMessage)
            }
        }
    }
    
    override fun onMessageReceived(message: Any) {
        if (message is GameMessage) {
            when (message) {
                is GameMessage.JoinLobby -> handleJoinLobby(message)
                is GameMessage.UpdatePlayersList -> handleUpdatePlayersList(message)
                is GameMessage.PlayerReady -> handlePlayerReady(message)
                is GameMessage.StartGame -> handleStartGame(message)
                is GameMessage.LeaveGame -> handleLeaveGame(message)
                is GameMessage.UpdateGameType -> handleUpdateGameType(message)
                else -> Log.d(TAG, "Получено неизвестное игровое сообщение: $message")
            }
        }
    }
    
    private fun handleJoinLobby(message: GameMessage.JoinLobby) {
        Log.d(TAG, "Получено сообщение о присоединении к лобби: ${message.player.name}")
        
        // Добавляем нового игрока, если его ещё нет
        if (!players.any { it.id == message.player.id }) {
            runOnUiThread {
                players.add(message.player)
                playerAdapter.notifyItemInserted(players.size - 1)
                updateUI()
                
                // Показываем уведомление
                Toast.makeText(this, "Игрок ${message.player.name} присоединился", Toast.LENGTH_SHORT).show()
            }
            
            // Если мы хост, отправляем обновленный список всем клиентам
            if (networkService.isHost()) {
                val updateMessage = GameMessage.UpdatePlayersList(players.toList())
                networkService.sendMessage(updateMessage)
            }
        }
    }
    
    private fun handleUpdatePlayersList(message: GameMessage.UpdatePlayersList) {
        Log.d(TAG, "Получено сообщение с обновленным списком игроков: ${message.players.size} игроков")
        
        // Обновляем список игроков полностью
        runOnUiThread {
            // Сохраняем текущего игрока
            val currentPlayer = players.find { it.id == playerId }
            
            // Очищаем список и заполняем его заново
            players.clear()
            
            // Добавляем всех игроков из сообщения
            players.addAll(message.players)
            
            // Если нашего игрока нет в списке, добавляем его
            if (currentPlayer != null && !players.any { it.id == playerId }) {
                players.add(currentPlayer)
            }
            
            playerAdapter.notifyDataSetChanged()
            updateUI()
        }
    }
    
    private fun handlePlayerReady(message: GameMessage.PlayerReady) {
        // Обновляем статус готовности игрока
        val playerIndex = players.indexOfFirst { it.id == message.playerId }
        if (playerIndex != -1) {
            runOnUiThread {
                players[playerIndex] = players[playerIndex].copy(isReady = message.isReady)
                playerAdapter.notifyItemChanged(playerIndex)
            }
            
            // Если мы хост, отправляем обновление всем клиентам
            if (networkService.isHost()) {
                networkService.sendMessage(message)
            }
        }
    }
    
    private fun handleStartGame(message: GameMessage.StartGame) {
        Log.d(TAG, "Получено сообщение о начале игры")
        
        // Переходим к игровой активности
        val gameIntent = Intent(this, GameActivity::class.java).apply {
            putExtra("gameType", message.gameType)
            putExtra("isHost", isHost)
            putExtra("playerId", playerId)
            putExtra("planeColor", selectedColor)
            putExtra("players", ArrayList(message.players))
        }
        
        // Сохраняем данные в приложении
        val app = application as BiplanesApplication
        app.setPlayers(message.players)
        
        startActivity(gameIntent)
        
        // Не закрываем текущую активность, чтобы можно было вернуться в лобби
    }
    
    private fun handleLeaveGame(message: GameMessage.LeaveGame) {
        // Игрок вышел из лобби
        val playerIndex = players.indexOfFirst { it.id == message.playerId }
        if (playerIndex != -1) {
            val player = players[playerIndex]
            
            runOnUiThread {
                players.removeAt(playerIndex)
                playerAdapter.notifyItemRemoved(playerIndex)
                updateUI()
                
                // Показываем уведомление
                Toast.makeText(this, "Игрок ${player.name} вышел из лобби", Toast.LENGTH_SHORT).show()
            }
            
            // Если мы хост, отправляем обновление всем клиентам
            if (networkService.isHost()) {
                val updateMessage = GameMessage.UpdatePlayersList(players.toList())
                networkService.sendMessage(updateMessage)
            }
        }
    }
    
    private fun handleUpdateGameType(message: GameMessage.UpdateGameType) {
        // Обновляем тип игры
        gameType = message.gameType
        
        runOnUiThread {
            updateUI()
            
            // Показываем уведомление об изменении типа игры
            val gameTypeStr = when (gameType) {
                GameType.ONE_VS_ONE -> "1 на 1"
                GameType.TWO_VS_TWO -> "2 на 2"
                GameType.FREE_FOR_ALL -> "Каждый за себя"
                GameType.TRAINING -> "Тренировка"
            }
            
            Toast.makeText(this, "Тип игры изменен на: $gameTypeStr", Toast.LENGTH_SHORT).show()
        }
        
        // Если мы хост, отправляем обновление всем клиентам
        if (networkService.isHost()) {
            val updateMessage = GameMessage.UpdatePlayersList(players.toList())
            networkService.sendMessage(updateMessage)
        }
    }
    
    override fun onNetworkError(errorMessage: String) {
        Log.e(TAG, "Сетевая ошибка: $errorMessage")
        
        runOnUiThread {
            // Закрываем диалог подключения, если он открыт
            connectionProgressDialog?.dismiss()
            
            // Показываем сообщение об ошибке
            Toast.makeText(this, "Ошибка: $errorMessage", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun sendJoinLobbyMessage() {
        // Создаем нового игрока
        val player = Player(
            id = playerId,
            name = "Игрок",  // Упрощаем имя для избежания проблем с сериализацией
            color = selectedColor,
            isReady = true,
            isHost = isHost
        )
        
        Log.d(TAG, "Отправляем сообщение JoinLobby с игроком: $player")
        
        // Создаем сообщение о присоединении
        val joinMessage = GameMessage.JoinLobby(player, gameType)
        
        // Отправляем сообщение
        try {
            networkService.sendMessage(joinMessage)
            Log.d(TAG, "Сообщение JoinLobby успешно отправлено")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при отправке сообщения JoinLobby: ${e.message}")
            val stackTrace = java.io.StringWriter().apply {
                e.printStackTrace(java.io.PrintWriter(this))
            }.toString()
            Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
            
            // Пробуем еще раз через небольшую задержку
            handler.postDelayed({
                try {
                    networkService.sendMessage(joinMessage)
                    Log.d(TAG, "Повторная отправка сообщения JoinLobby успешна")
                } catch (e: Exception) {
                    Log.e(TAG, "Повторная отправка сообщения JoinLobby не удалась: ${e.message}")
                }
            }, 1000)
        }
    }
    
    private fun sendLeaveMessage() {
        // Создаем сообщение о выходе
        val leaveMessage = GameMessage.LeaveGame(playerId)
        
        // Отправляем сообщение
        networkService.sendMessage(leaveMessage)
    }
    
    private fun startGame() {
        if (!isHost) return
        
        // Проверяем, достаточно ли игроков
        if (players.size < 2) {
            Toast.makeText(this, "Для начала игры нужно минимум 2 игрока", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Создаем сообщение о начале игры
        val startMessage = GameMessage.StartGame(gameType, players)
        
        // Отправляем сообщение всем клиентам
        networkService.sendMessage(startMessage)
        
        // Запускаем игру у себя
        handleStartGame(startMessage)
    }
    
    override fun onBackPressed() {
        showExitConfirmDialog()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Отправляем сообщение о выходе
        if (::networkService.isInitialized) {
            sendLeaveMessage()
            networkService.stop()
        }
        
        // Закрываем все диалоги
        serverListDialog?.dismiss()
        connectionProgressDialog?.dismiss()
    }
    
    // Дополнительные методы из NetworkListener, которые нам пока не нужны
    override fun onConnectionStatusChanged(isConnected: Boolean, serverAddress: String?) {
        Log.d(TAG, "Изменение статуса подключения: $isConnected, адрес: $serverAddress")
        
        // Закрываем диалог подключения
        connectionProgressDialog?.dismiss()
        
        if (isConnected) {
            // Успешно подключились к серверу
            Toast.makeText(this, "Подключено к серверу", Toast.LENGTH_SHORT).show()
            
            // Отправляем информацию о себе - ВАЖНО! Это необходимо для регистрации в лобби
            sendJoinLobbyMessage()
            
            // Обновляем UI
            runOnUiThread {
                updateUI()
            }
        } else {
            // Соединение разорвано
            if (isHost) {
                // Если мы хост, ничего не делаем
            } else {
                // Если мы клиент, показываем ошибку
                Toast.makeText(this, "Соединение с сервером потеряно", Toast.LENGTH_SHORT).show()
                
                // Показываем диалог переподключения
                showReconnectDialog()
            }
        }
    }
    
    /**
     * Адаптер для списка серверов
     */
    private inner class ServerListAdapter(
        private val servers: List<NetworkService.ServerInfo>,
        private val onServerClickListener: (NetworkService.ServerInfo) -> Unit
    ) : RecyclerView.Adapter<ServerListAdapter.ServerViewHolder>() {
        
        inner class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val serverNameTextView: TextView = itemView.findViewById(R.id.serverNameTextView)
            val serverAddressTextView: TextView = itemView.findViewById(R.id.serverAddressTextView)
            
            init {
                itemView.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onServerClickListener(servers[position])
                    }
                }
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_server, parent, false)
            return ServerViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
            val server = servers[position]
            
            // Извлекаем код комнаты из имени сервера (формат: BiplaneLobby-XXXX)
            val roomCode = if (server.name.startsWith(NetworkService.LOBBY_PREFIX)) {
                server.name.substring(NetworkService.LOBBY_PREFIX.length)
            } else {
                server.name
            }
            
            holder.serverNameTextView.text = "Комната: $roomCode"
            holder.serverAddressTextView.text = "${server.host}:${server.port}"
        }
        
        override fun getItemCount(): Int = servers.size
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