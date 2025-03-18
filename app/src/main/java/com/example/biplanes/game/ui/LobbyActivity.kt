package com.example.biplanes.game.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.style.StyleSpan
import android.util.Log
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
import com.example.biplanes.network.WiFiDirectService
import java.util.UUID
import android.graphics.Typeface

class LobbyActivity : AppCompatActivity(), WiFiDirectService.WiFiDirectListener {
    
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
    
    // Сервис Wi-Fi Direct
    private lateinit var wifiDirectService: WiFiDirectService
    
    // Список обнаруженных устройств
    private val discoveredDevices = mutableListOf<WifiP2pDevice>()
    
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
        
        // Инициализируем Wi-Fi Direct
        initWiFiDirect()
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
        
        // Логируем код комнаты для отладки
        Log.d(TAG, "Generated room code: $roomCode")
        
        // Обновляем UI с новым кодом комнаты
        runOnUiThread {
            updateUI()
            
            // Не показываем диалог автоматически при открытии
            // showRoomCodeDialog()
        }
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
    
    private fun initWiFiDirect() {
        // Проверяем разрешения
        if (checkAndRequestPermissions()) {
            // Инициализируем сервис Wi-Fi Direct
            wifiDirectService = WiFiDirectService(this)
            wifiDirectService.setListener(this)
            wifiDirectService.start()
            
            // Добавляем задержку перед началом обнаружения устройств
            Handler(Looper.getMainLooper()).postDelayed({
                // Если хост, начинаем обнаружение устройств
                if (isHost) {
                    wifiDirectService.discoverPeers()
                    
                    // Показываем сообщение о том, что ищем устройства
                    Toast.makeText(
                        this,
                        "Поиск устройств...",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // Если не хост, показываем диалог выбора устройства для подключения
                    showDeviceListDialog()
                }
            }, 1000) // Задержка 1 секунда
        }
    }
    
    private fun checkAndRequestPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        
        // Проверяем разрешения для Wi-Fi Direct
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        // Для Android 10+ нужно разрешение ACCESS_FINE_LOCATION
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
                // Все разрешения предоставлены, инициализируем Wi-Fi Direct
                initWiFiDirect()
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
    
    private fun showDeviceListDialog() {
        // Показываем диалог выбора устройства для подключения
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Выберите устройство для подключения")
        
        if (discoveredDevices.isEmpty()) {
            builder.setMessage("Устройства не найдены. Нажмите 'Обновить' для повторного поиска.")
            builder.setPositiveButton("Обновить") { _, _ ->
                // Показываем сообщение о том, что ищем устройства
                Toast.makeText(
                    this,
                    "Поиск устройств...",
                    Toast.LENGTH_SHORT
                ).show()
                
                wifiDirectService.discoverPeers()
                
                // Повторно показываем диалог через 3 секунды
                Handler(Looper.getMainLooper()).postDelayed({
                    showDeviceListDialog()
                }, 3000)
            }
        } else {
            val deviceNames = discoveredDevices.map { it.deviceName }.toTypedArray()
            builder.setItems(deviceNames) { _, which ->
                // Подключаемся к выбранному устройству
                Toast.makeText(
                    this,
                    "Подключение к ${deviceNames[which]}...",
                    Toast.LENGTH_SHORT
                ).show()
                
                wifiDirectService.connect(discoveredDevices[which])
            }
        }
        
        builder.setNegativeButton("Отмена") { _, _ ->
            finish()
        }
        
        builder.show()
    }
    
    private fun startGame() {
        // Отправляем сообщение о начале игры всем игрокам
        val startGameMessage = GameMessage.StartGame(
            gameType = gameType,
            players = players
        )
        wifiDirectService.sendMessage(startGameMessage)
        
        // Запускаем игру
        startGameActivity()
    }
    
    private fun startGameActivity() {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("gameType", gameType)
        intent.putExtra("isHost", isHost)
        intent.putExtra("planeColor", selectedColor)
        intent.putExtra("playerId", playerId)
        intent.putExtra("players", ArrayList(players))
        startActivity(intent)
        finish()
    }
    
    // Реализация методов интерфейса WiFiDirectListener
    
    override fun onDeviceDiscovered(device: WifiP2pDevice) {
        Log.d(TAG, "Device discovered: ${device.deviceName}")
        
        // Добавляем устройство в список
        if (!discoveredDevices.contains(device)) {
            discoveredDevices.add(device)
            
            // Если не хост, обновляем диалог выбора устройства
            if (!isHost) {
                showDeviceListDialog()
            }
        }
    }
    
    override fun onConnectionChanged(isConnected: Boolean, groupOwnerAddress: String?) {
        Log.d(TAG, "Connection changed: $isConnected, address: $groupOwnerAddress")
        
        if (isConnected) {
            // Если подключение установлено
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Подключение установлено",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Добавляем задержку перед отправкой сообщений
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isHost) {
                        // Если хост, отправляем информацию о лобби
                        val currentPlayer = players.first()
                        val joinMessage = GameMessage.JoinLobby(
                            player = currentPlayer,
                            gameType = gameType
                        )
                        wifiDirectService.sendMessage(joinMessage)
                    } else {
                        // Если не хост, отправляем запрос на присоединение
                        val currentPlayer = players.first()
                        val joinMessage = GameMessage.JoinLobby(
                            player = currentPlayer,
                            gameType = gameType
                        )
                        wifiDirectService.sendMessage(joinMessage)
                    }
                }, 1000) // Задержка 1 секунда
            }
        } else {
            // Если подключение разорвано
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Подключение разорвано. Повторная попытка...",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Удаляем всех игроков, кроме текущего
                val currentPlayer = players.first()
                players.clear()
                players.add(currentPlayer)
                playerAdapter.notifyDataSetChanged()
                
                // Обновляем UI
                updateUI()
                
                // Если не хост, пытаемся переподключиться
                if (!isHost) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        showDeviceListDialog()
                    }, 2000) // Задержка 2 секунды
                } else {
                    // Если хост, начинаем обнаружение устройств заново
                    Handler(Looper.getMainLooper()).postDelayed({
                        wifiDirectService.discoverPeers()
                    }, 2000) // Задержка 2 секунды
                }
            }
        }
    }
    
    override fun onDeviceDisconnected() {
        Log.d(TAG, "Device disconnected")
        
        runOnUiThread {
            Toast.makeText(
                this,
                "Устройство отключено",
                Toast.LENGTH_SHORT
            ).show()
            
            // Удаляем всех игроков, кроме текущего
            val currentPlayer = players.first()
            players.clear()
            players.add(currentPlayer)
            playerAdapter.notifyDataSetChanged()
            
            // Обновляем UI
            updateUI()
        }
    }
    
    override fun onMessageReceived(message: Any) {
        Log.d(TAG, "Message received: $message")
        
        if (message is GameMessage) {
            when (message) {
                is GameMessage.JoinLobby -> {
                    // Получено сообщение о присоединении игрока
                    val player = message.player
                    
                    runOnUiThread {
                        // Проверяем, есть ли уже такой игрок
                        if (players.none { it.id == player.id }) {
                            players.add(player)
                            playerAdapter.notifyItemInserted(players.size - 1)
                            
                            // Обновляем UI
                            updateUI()
                            
                            // Если хост, отправляем информацию о всех игроках
                            if (isHost) {
                                for (p in players) {
                                    val joinMessage = GameMessage.JoinLobby(
                                        player = p,
                                        gameType = gameType
                                    )
                                    wifiDirectService.sendMessage(joinMessage)
                                }
                            }
                        }
                    }
                }
                
                is GameMessage.PlayerReady -> {
                    // Получено сообщение о готовности игрока
                    val playerId = message.playerId
                    val isReady = message.isReady
                    
                    runOnUiThread {
                        // Обновляем статус игрока
                        val playerIndex = players.indexOfFirst { it.id == playerId }
                        if (playerIndex != -1) {
                            val player = players[playerIndex]
                            players[playerIndex] = player.copy(isReady = isReady)
                            playerAdapter.notifyItemChanged(playerIndex)
                            
                            // Обновляем UI
                            updateUI()
                        }
                    }
                }
                
                is GameMessage.StartGame -> {
                    // Получено сообщение о начале игры
                    runOnUiThread {
                        // Запускаем игру
                        startGameActivity()
                    }
                }
                
                else -> {
                    // Игнорируем другие типы сообщений
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Останавливаем сервис Wi-Fi Direct
        if (::wifiDirectService.isInitialized) {
            wifiDirectService.stop()
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