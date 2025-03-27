package com.example.biplanes.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.biplanes.network.GameMessage
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Сервис для работы с локальной Wi-Fi сетью
 */
class NetworkService(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkService"
        private const val DEFAULT_PORT = 8888
        private const val SERVICE_TYPE = "_biplane._tcp."
        private const val CONNECTION_TIMEOUT = 5000 // Уменьшаем таймаут с 10000 до 5000 мс
        private const val SOCKET_READ_TIMEOUT = 3000 // Добавляем отдельный таймаут для чтения из сокета
        
        // Добавляем динамический порт, который будет изменяться при необходимости
        private var CURRENT_PORT = DEFAULT_PORT
        
        // Добавляем значения портов для последовательного перебора
        private val PORT_RANGE = intArrayOf(8888, 8889, 8890, 8891, 8892)
        
        // Список всех запущенных экземпляров NetworkService
        private val activeInstances = mutableListOf<NetworkService>()
        
        // Префиксы для имен сервисов, чтобы различать лобби и игру
        const val LOBBY_PREFIX = "BiplaneLobby-"
        const val GAME_PREFIX = "BiplaneGame-"
    }
    
    // Интерфейс для обратной связи
    interface NetworkListener {
        fun onServerDiscovered(serverInfo: ServerInfo)
        fun onConnectionChanged(isConnected: Boolean, serverAddress: String?)
        fun onClientConnected(clientId: String)
        fun onClientDisconnected(clientId: String)
        fun onMessageReceived(message: Any)
        fun onNetworkError(errorMessage: String)
        fun onConnectionStatusChanged(isConnected: Boolean, serverAddress: String?)
    }
    
    // Информация о найденном сервере
    data class ServerInfo(
        val id: String,
        val name: String,
        val host: String,
        val port: Int
    )
    
    // NSD менеджер для обнаружения сервисов
    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    
    // Слушатель событий
    private var listener: NetworkListener? = null
    
    // Имя сервиса
    private var serviceName = "Biplane-${UUID.randomUUID().toString().substring(0, 8)}"
    
    // Идентификатор текущего устройства
    private val deviceId: String
    
    // Инициализация для идентификатора устройства
    init {
        // Получаем ID из приложения или генерируем, если не удалось
        deviceId = try {
            (context.applicationContext as? com.example.biplanes.BiplanesApplication)?.getUniqueDeviceId() 
                ?: UUID.randomUUID().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Не удалось получить ID устройства из приложения: ${e.message}")
            UUID.randomUUID().toString()
        }
        Log.d(TAG, "NetworkService инициализирован с ID устройства: $deviceId")
    }
    
    // Сокеты для обмена данными
    private var serverSocket: ServerSocket? = null
    private val clientSockets = ConcurrentHashMap<String, Socket>()
    private val clientStreams = ConcurrentHashMap<String, Pair<ObjectInputStream, ObjectOutputStream>>()
    
    // Клиентское подключение к серверу
    private var serverConnection: Socket? = null
    private var serverInputStream: ObjectInputStream? = null
    private var serverOutputStream: ObjectOutputStream? = null
    private var isConnected = false
    // Добавляем serverAddress для хранения адреса сервера
    private var serverAddress: String? = null
    
    // Флаг, указывающий, является ли устройство сервером
    private var isServer = false
    
    // Флаг, указывающий, запущен ли сервис
    private var isRunning = false
    private var isDiscovering = false
    private var isRegistered = false
    
    // Обнаруженные серверы
    private val discoveredServers = mutableListOf<ServerInfo>()
    
    // Регистрация сервиса NSD
    private var registrationListener: NsdManager.RegistrationListener? = null
    
    // Обнаружение сервисов NSD
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    
    // Разрешение сервиса для получения IP и порта
    private var resolveListener: NsdManager.ResolveListener? = null
    
    // Хэндлер для выполнения задач в основном потоке
    private val handler = Handler(Looper.getMainLooper())
    
    // Флаги для отслеживания состояния сервиса
    private var serviceMode = ServiceMode.NONE
    
    // Перечисление для режимов сервиса
    enum class ServiceMode {
        NONE, LOBBY, GAME
    }
    
    /**
     * Установка слушателя событий
     */
    fun setListener(listener: NetworkListener) {
        this.listener = listener
    }
    
    /**
     * Запуск сервиса
     */
    fun start() {
        if (isRunning) {
            Log.d(TAG, "NetworkService already running")
            return
        }
        
        try {
            isRunning = true
            
            // Добавляем этот экземпляр в список активных
            synchronized(activeInstances) {
                activeInstances.add(this)
            }
            
            Log.d(TAG, "NetworkService started")
            
            // Если мы не хост (клиент), начинаем обнаружение серверов
            if (!isServer) {
                discoverServers()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting NetworkService", e)
        }
    }
    
    /**
     * Остановка сервиса
     * @param preserveServer если true, сохраняет состояние сервера
     */
    fun stop(preserveServer: Boolean = false) {
        if (!isRunning) return
        
        try {
            // Останавливаем обнаружение и регистрацию сервисов
            stopDiscovery()
            
            if (!preserveServer) {
                unregisterService()
                
                // Закрываем соединения
                closeAllConnections(false)
                
                // Полностью освобождаем ресурсы сервера
                try {
                    val socket = serverSocket
                    serverSocket = null // Сначала обнуляем ссылку
                    if (socket != null && !socket.isClosed) {
                        socket.close()    // Потом закрываем сокет
                        Log.d(TAG, "Successfully closed server socket")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing server socket: ${e.message}")
                }
                
                // Сбрасываем все флаги
                isRunning = false
                isServer = false
                isConnected = false
                
                // Удаляем этот экземпляр из списка активных
                synchronized(activeInstances) {
                    activeInstances.remove(this)
                }
                
                Log.d(TAG, "NetworkService stopped and all resources released")
            } else {
                // Если нужно сохранить сервер, просто отмечаем, что текущий экземпляр сервиса остановлен
                isRunning = false
                Log.d(TAG, "NetworkService stopped but server preserved")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping NetworkService", e)
        }
    }
    
    /**
     * Создание сервера для лобби
     * @param roomCode код комнаты для идентификации лобби
     */
    fun createLobbyServer(roomCode: String) {
        serviceMode = ServiceMode.LOBBY
        serviceName = "${LOBBY_PREFIX}${roomCode}"
        createServer(serviceName)
    }
    
    /**
     * Создание сервера для игры
     * @param gameId идентификатор игры
     */
    fun createGameServer(gameId: String) {
        Log.d(TAG, "Создание игрового сервера с ID: $gameId")
        
        // Принудительно завершаем все активные соединения и закрываем сокеты
        try {
            // Останавливаем текущие операции
            stopDiscovery()
            unregisterService()
            
            // Закрываем все клиентские соединения
            closeAllConnections(false)
            
            // Закрываем серверный сокет
            try {
                val socket = serverSocket
                serverSocket = null // Сначала обнуляем ссылку
                if (socket != null && !socket.isClosed) {
                    socket.close()
                    Log.d(TAG, "Закрыт старый серверный сокет перед созданием игрового сервера")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при закрытии серверного сокета: ${e.message}")
            }
            
            // Добавляем задержку для полного освобождения ресурсов
            Thread.sleep(500)
            
            // Сообщаем GC о желательности сборки мусора
            System.gc()
            Thread.sleep(100)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при очистке ресурсов перед созданием игрового сервера: ${e.message}")
        }
        
        // Устанавливаем режим сервиса на GAME
        serviceMode = ServiceMode.GAME
        
        // НЕ добавляем случайный суффикс, чтобы имя было предсказуемым для клиентов
        // Используем более простое имя на основе gameId
        serviceName = "${GAME_PREFIX}${gameId}"
        
        Log.d(TAG, "Установлено имя игрового сервера: $serviceName")
        
        // Пробуем создать сервер несколько раз в случае ошибки
        var success = false
        var attempts = 0
        val maxAttempts = 3
        
        while (!success && attempts < maxAttempts) {
            attempts++
            try {
                Log.d(TAG, "Попытка $attempts создания игрового сервера...")
                createServer(serviceName)
                success = true
                Log.d(TAG, "Игровой сервер успешно создан с попытки $attempts")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при создании игрового сервера (попытка $attempts): ${e.message}")
                
                // Если это не последняя попытка, ждем и пробуем снова
                if (attempts < maxAttempts) {
                    try {
                        // Закрываем соединения и сокеты
                        closeAllConnections(false)
                        try {
                            val socket = serverSocket
                            serverSocket = null
                            if (socket != null && !socket.isClosed) {
                                socket.close()
                            }
                        } catch (e2: Exception) {
                            Log.e(TAG, "Ошибка при закрытии сокета между попытками: ${e2.message}")
                        }
                        
                        // Ждем перед повторной попыткой
                        Thread.sleep(1000)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Ошибка при очистке между попытками: ${e2.message}")
                    }
                }
            }
        }
        
        // Если создать сервер не удалось после всех попыток
        if (!success) {
            Log.e(TAG, "Не удалось создать игровой сервер после $maxAttempts попыток")
            listener?.onNetworkError("Не удалось создать игровой сервер после $maxAttempts попыток")
        }
    }
    
    /**
     * Создание сервера
     * @param customName необязательное имя сервера
     */
    fun createServer(customName: String? = null) {
        // Проверка на существующий сервер или сокет
        if (serverSocket != null && !serverSocket!!.isClosed) {
            Log.d(TAG, "Server socket already exists on port ${serverSocket!!.localPort}")
            return
        }
        
        if (isServer) {
            Log.d(TAG, "Server flag is already set, but no server socket found. Creating new server.")
        }
        
        if (customName != null) {
            serviceName = customName
        }
        
        isServer = true
        
        // Принудительно закрываем все существующие экземпляры сетевого сервиса
        // чтобы освободить порты
        synchronized(activeInstances) {
            Log.d(TAG, "Обнаружено ${activeInstances.size} активных экземпляров NetworkService")
            for (instance in activeInstances.toList()) {
                if (instance != this) {
                    try {
                        Log.d(TAG, "Останавливаем другой экземпляр NetworkService перед созданием сервера")
                        // Полностью останавливаем экземпляр
                        instance.stop(false)
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка при остановке другого экземпляра NetworkService: ${e.message}")
                    }
                }
            }
        }
        
        // Дополнительная задержка для полного освобождения ресурсов
        Thread.sleep(500)
        
        // Запускаем сервер в отдельном потоке
        thread {
            var portIndex = 0
            var maxPortAttempts = PORT_RANGE.size
            
            while (portIndex < maxPortAttempts) {
                val currentPort = PORT_RANGE[portIndex]
                try {
                    // Принудительно закрываем сокет, если он был создан ранее
                    try {
                        val oldSocket = serverSocket
                        if (oldSocket != null && !oldSocket.isClosed) {
                            Log.d(TAG, "Закрываем старый серверный сокет перед созданием нового")
                            serverSocket = null
                            oldSocket.close()
                            Thread.sleep(100) // Даем время для освобождения порта
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка при закрытии старого серверного сокета: ${e.message}")
                        val stackTrace = java.io.StringWriter().apply {
                            e.printStackTrace(java.io.PrintWriter(this))
                        }.toString()
                        Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                    }
                    
                    // Дополнительная проверка доступности порта
                    val testSocket = ServerSocket()
                    testSocket.reuseAddress = true // Разрешаем повторное использование адреса
                    testSocket.bind(java.net.InetSocketAddress(currentPort))
                    testSocket.close()
                    Thread.sleep(100) // Даем время для освобождения порта
                    
                    // Если удалось создать тестовый сокет, создаем основной сервер
                    val newSocket = ServerSocket()
                    newSocket.reuseAddress = true // Важно для быстрого переиспользования порта
                    newSocket.bind(java.net.InetSocketAddress(currentPort))
                    serverSocket = newSocket
                    
                    Log.d(TAG, "Server socket created on port $currentPort")
                    
                    // Регистрируем сервис в NSD
                    registerService(currentPort)
                    
                    // Начинаем принимать клиентов
                    acceptClients()
                    
                    // Выходим из цикла, так как сервер успешно создан
                    break
                    
                } catch (e: IOException) {
                    val stackTrace = java.io.StringWriter().apply {
                        e.printStackTrace(java.io.PrintWriter(this))
                    }.toString()
                    Log.e(TAG, "Не удалось создать сервер на порту $currentPort: ${e.message}")
                    Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                    
                    portIndex++
                    
                    if (portIndex < maxPortAttempts) {
                        // Если порт занят, пробуем следующий порт из списка
                        Log.w(TAG, "Порт $currentPort занят, пробуем следующий порт: ${PORT_RANGE[portIndex]}")
                        
                        // Делаем небольшую паузу перед следующей попыткой
                        Thread.sleep(300)
                    } else {
                        Log.e(TAG, "Не удалось найти свободный порт после $maxPortAttempts попыток")
                        isServer = false
                        handler.post {
                            listener?.onConnectionChanged(false, null)
                            listener?.onNetworkError("Не удалось создать сервер: все порты заняты")
                        }
                        return@thread
                    }
                }
            }
        }
    }
    
    /**
     * Начать обнаружение серверов
     */
    fun discoverServers() {
        if (isDiscovering) return
        
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Service discovery started: $serviceType")
                isDiscovering = true
            }
            
            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Service discovery stopped: $serviceType")
                isDiscovering = false
            }
            
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                
                // Разрешаем сервис для получения его IP и порта
                resolveService(serviceInfo)
            }
            
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                
                // Удаляем потерянный сервер из списка
                synchronized(discoveredServers) {
                    val index = discoveredServers.indexOfFirst { it.name == serviceInfo.serviceName }
                    if (index != -1) {
                        discoveredServers.removeAt(index)
                    }
                }
            }
            
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: $errorCode")
                isDiscovering = false
            }
            
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $errorCode")
                isDiscovering = false
            }
        }
        
        try {
            nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering services", e)
        }
    }
    
    /**
     * Остановить обнаружение серверов
     */
    fun stopDiscovery() {
        if (!isDiscovering || discoveryListener == null) return
        
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
            isDiscovering = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery", e)
        }
    }
    
    /**
     * Получить список обнаруженных серверов
     */
    fun getDiscoveredServers(): List<ServerInfo> {
        synchronized(discoveredServers) {
            return discoveredServers.toList()
        }
    }
    
    /**
     * Фильтрация серверов по их типу (лобби или игра)
     * @param mode режим сервиса для фильтрации
     * @return список серверов соответствующего типа
     */
    fun getFilteredServers(mode: ServiceMode): List<ServerInfo> {
        synchronized(discoveredServers) {
            return when (mode) {
                ServiceMode.LOBBY -> discoveredServers.filter { it.name.startsWith(LOBBY_PREFIX) }
                ServiceMode.GAME -> discoveredServers.filter { it.name.startsWith(GAME_PREFIX) }
                else -> emptyList()
            }
        }
    }
    
    /**
     * Поиск сервера по коду комнаты
     * @param roomCode код комнаты для поиска
     * @return информация о сервере или null, если сервер не найден
     */
    fun findServerByRoomCode(roomCode: String): ServerInfo? {
        synchronized(discoveredServers) {
            return discoveredServers.find { it.name == "${LOBBY_PREFIX}${roomCode}" }
        }
    }
    
    /**
     * Поиск сервера по ID игры
     * @param gameId идентификатор игры для поиска
     * @return информация о сервере или null, если сервер не найден
     */
    fun findServerByGameId(gameId: String): ServerInfo? {
        synchronized(discoveredServers) {
            return discoveredServers.find { it.name == "${GAME_PREFIX}${gameId}" }
        }
    }
    
    /**
     * Подключение к серверу.
     * @param serverInfo информация о сервере
     * @return true, если подключение успешно
     */
    fun connectToServer(serverInfo: ServerInfo): Boolean {
        Log.d(TAG, "Попытка подключения к серверу: ${serverInfo.name} (${serverInfo.host}:${serverInfo.port})")
        
        if (serverConnection != null && !serverConnection!!.isClosed) {
            Log.d(TAG, "Закрываем старое клиентское соединение перед подключением к новому серверу")
            try {
                serverConnection?.close()
                serverConnection = null
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при закрытии старого соединения: ${e.message}")
            }
        }
        
        // Запускаем подключение в отдельном потоке
        thread {
            try {
                val socket = Socket()
                
                // Устанавливаем таймаут подключения
                socket.connect(InetSocketAddress(serverInfo.host, serverInfo.port), CONNECTION_TIMEOUT)
                
                // Устанавливаем таймауты чтения и записи
                socket.soTimeout = SOCKET_READ_TIMEOUT
                
                serverConnection = socket
                
                val outputStream = DataOutputStream(socket.getOutputStream())
                val inputStream = DataInputStream(socket.getInputStream())
                
                serverOutputStream = ObjectOutputStream(outputStream)
                serverInputStream = ObjectInputStream(inputStream)
                
                // ВАЖНО: отправляем свой ID сразу после подключения
                serverOutputStream?.writeObject(deviceId)
                serverOutputStream?.flush()
                Log.d(TAG, "Отправлен ID устройства серверу: $deviceId")
                
                isConnected = true
                serverAddress = serverInfo.host
                
                // Запускаем поток для чтения данных с сервера
                startClientMessageListener()
                
                // Уведомляем о подключении к серверу через главный поток
                handler.post {
                    listener?.onConnectionStatusChanged(isConnected, serverAddress)
                }
                
                Log.d(TAG, "Подключение к серверу успешно: ${serverInfo.name} (${serverInfo.host}:${serverInfo.port})")
            } catch (e: Exception) {
                // Подробное логирование исключения
                val stackTrace = java.io.StringWriter().apply {
                    e.printStackTrace(java.io.PrintWriter(this))
                }.toString()
                
                Log.e(TAG, "Ошибка подключения к серверу ${serverInfo.name} (${serverInfo.host}:${serverInfo.port}): ${e.message}")
                Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                
                // Закрываем сокет в случае неудачи
                try {
                    serverConnection?.close()
                    serverConnection = null
                } catch (e2: Exception) {
                    Log.e(TAG, "Ошибка при закрытии сокета после неудачного подключения: ${e2.message}")
                }
                
                isConnected = false
                serverAddress = null
                
                // Сообщаем об ошибке через главный поток
                handler.post {
                    listener?.onConnectionStatusChanged(isConnected, serverAddress)
                    listener?.onNetworkError("Ошибка подключения к серверу: ${e.message ?: "Неизвестная ошибка"}")
                }
            }
        }
        
        // Возвращаем true, так как подключение стартовало (результат будет известен в колбэке)
        return true
    }
    
    /**
     * Отправка сообщения
     */
    fun sendMessage(message: Any) {
        if (message !is Serializable) {
            Log.e(TAG, "Cannot send non-serializable message: $message")
            return
        }
        
        try {
            if (isServer) {
                // Если мы сервер, отправляем сообщение всем клиентам
                for (clientId in clientStreams.keys) {
                    sendMessageToClient(clientId, message)
                }
                
                // И обрабатываем сообщение сами
                handler.post {
                    listener?.onMessageReceived(message)
                }
            } else if (isConnected) {
                // Если мы клиент, отправляем сообщение серверу
                val serverOutputStream = serverOutputStream ?: return
                
                thread {
                    try {
                        synchronized(serverOutputStream) {
                            // Заменяем reset() на flush, чтобы избежать сброса кэша объектов
                            serverOutputStream.flush()
                            serverOutputStream.writeObject(message)
                            serverOutputStream.flush()
                            Log.d(TAG, "Message sent to server: $message")
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error sending message to server: ${e.message}")
                        disconnectFromServer()
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error sending message to server: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message: ${e.message}")
        }
    }
    
    /**
     * Отправка сообщения конкретному клиенту
     */
    fun sendMessageToClient(clientId: String, message: Any) {
        if (message !is Serializable) {
            Log.e(TAG, "Cannot send non-serializable message: $message")
            return
        }
        
        val streams = clientStreams[clientId] ?: return
        val outputStream = streams.second
        
        thread {
            try {
                synchronized(outputStream) {
                    // Заменяем reset() на flush, чтобы избежать сброса кэша объектов
                    outputStream.flush()
                    outputStream.writeObject(message)
                    outputStream.flush()
                    Log.d(TAG, "Message sent to client $clientId: $message")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error sending message to client $clientId: ${e.message}")
                closeConnection(clientId)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error sending message to client $clientId: ${e.message}")
                // Не закрываем соединение при неожиданных ошибках, чтобы дать шанс восстановиться
            }
        }
    }
    
    /**
     * Регистрация сервиса в NSD
     */
    private fun registerService(port: Int) {
        // Создаем информацию о сервисе
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = this@NetworkService.serviceName
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        
        // Создаем слушатель регистрации
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service registered: ${serviceInfo.serviceName}")
                this@NetworkService.serviceName = serviceInfo.serviceName
                isRegistered = true
            }
            
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Registration failed: $errorCode")
                isRegistered = false
            }
            
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered: ${serviceInfo.serviceName}")
                isRegistered = false
            }
            
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Unregistration failed: $errorCode")
            }
        }
        
        try {
            // Регистрируем сервис
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error registering service", e)
        }
    }
    
    /**
     * Отмена регистрации сервиса в NSD
     */
    private fun unregisterService() {
        if (!isRegistered || registrationListener == null) return
        
        try {
            nsdManager.unregisterService(registrationListener)
            isRegistered = false
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering service", e)
        }
    }
    
    /**
     * Разрешение сервиса для получения IP и порта
     */
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        // Создаем новый resolveListener для каждого запроса, чтобы избежать конфликтов
        val localResolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: code $errorCode")
                
                // Особые случаи для разных кодов ошибок
                when (errorCode) {
                    NsdManager.FAILURE_ALREADY_ACTIVE -> {
                        Log.w(TAG, "Resolve already active, will retry after delay")
                        // Повторная попытка с задержкой
                        handler.postDelayed({
                            try {
                                nsdManager.resolveService(serviceInfo, this)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in delayed resolve: ${e.message}")
                            }
                        }, 300)
                    }
                    NsdManager.FAILURE_INTERNAL_ERROR -> {
                        Log.e(TAG, "Internal error during resolve, will not retry")
                    }
                    else -> {
                        Log.w(TAG, "Generic resolve failure, will retry once")
                        // Одна повторная попытка с большей задержкой
                        handler.postDelayed({
                            try {
                                nsdManager.resolveService(serviceInfo, this)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in retry resolve: ${e.message}")
                            }
                        }, 500)
                    }
                }
            }
            
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service resolved: ${serviceInfo.serviceName}")
                
                // Проверяем, не наш ли это сервис
                if (serviceInfo.serviceName == this@NetworkService.serviceName) {
                    Log.d(TAG, "Ignoring our own service")
                    return
                }
                
                // Получаем IP и порт сервера
                val host = serviceInfo.host.hostAddress ?: return
                val port = serviceInfo.port
                val serverId = "${host}:${port}"
                
                // Создаем объект ServerInfo
                val serverInfo = ServerInfo(
                    id = serverId,
                    name = serviceInfo.serviceName,
                    host = host,
                    port = port
                )
                
                // Добавляем сервер в список
                synchronized(discoveredServers) {
                    val index = discoveredServers.indexOfFirst { it.id == serverId }
                    if (index == -1) {
                        discoveredServers.add(serverInfo)
                        
                        // Уведомляем о новом сервере
                        handler.post {
                            listener?.onServerDiscovered(serverInfo)
                        }
                    }
                }
            }
        }
        
        try {
            nsdManager.resolveService(serviceInfo, localResolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving service: ${e.message}")
        }
    }
    
    /**
     * Прием клиентов
     */
    private fun acceptClients() {
        thread {
            Log.d(TAG, "Запущен поток прослушивания подключений клиентов")
            
            while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                try {
                    Log.d(TAG, "Ожидание подключения клиента на порту ${serverSocket?.localPort}")
                    val clientSocket = serverSocket!!.accept()
                    clientSocket.soTimeout = SOCKET_READ_TIMEOUT
                    val clientAddress = clientSocket.inetAddress.hostAddress
                    Log.d(TAG, "Клиент подключен: $clientAddress")
                    
                    // Создаем потоки для обмена данными в отдельном потоке для каждого клиента
                    thread {
                        try {
                            // ВАЖНО: сначала создаем outputStream, потом inputStream
                            val outputStream = ObjectOutputStream(clientSocket.getOutputStream())
                            outputStream.flush() // Важно сделать flush перед созданием inputStream
                            
                            // Используем небольшую задержку между созданием потоков
                            Thread.sleep(50)
                            
                            val inputStream = ObjectInputStream(clientSocket.getInputStream())
                            
                            try {
                                // Получаем ID клиента
                                val clientId = inputStream.readObject() as String
                                
                                // Сохраняем соединение
                                clientSockets[clientId] = clientSocket
                                clientStreams[clientId] = Pair(inputStream, outputStream)
                                
                                // Уведомляем о подключении клиента
                                handler.post {
                                    listener?.onClientConnected(clientId)
                                }
                                
                                // Запускаем поток для чтения сообщений
                                startReadingMessages(clientId)
                            } catch (e: ClassNotFoundException) {
                                Log.e(TAG, "Ошибка чтения ID клиента: ${e.message}")
                                val stackTrace = java.io.StringWriter().apply {
                                    e.printStackTrace(java.io.PrintWriter(this))
                                }.toString()
                                Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                                
                                try { outputStream.close() } catch (e: Exception) { }
                                try { inputStream.close() } catch (e: Exception) { }
                                try { clientSocket.close() } catch (e: Exception) { }
                            } catch (e: Exception) {
                                Log.e(TAG, "Непредвиденная ошибка при обработке клиента: ${e.message}")
                                val stackTrace = java.io.StringWriter().apply {
                                    e.printStackTrace(java.io.PrintWriter(this))
                                }.toString()
                                Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                                
                                try { outputStream.close() } catch (e: Exception) { }
                                try { inputStream.close() } catch (e: Exception) { }
                                try { clientSocket.close() } catch (e: Exception) { }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Ошибка создания потоков для клиента $clientAddress: ${e.message}")
                            val stackTrace = java.io.StringWriter().apply {
                                e.printStackTrace(java.io.PrintWriter(this))
                            }.toString()
                            Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                            
                            try { clientSocket.close() } catch (e: Exception) { }
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    // Тайм-аут сокета - это нормально в некоторых случаях
                    Log.w(TAG, "Тайм-аут при ожидании подключения клиента: ${e.message}")
                } catch (e: IOException) {
                    if (isRunning) {
                        Log.e(TAG, "Ошибка при приеме клиента: ${e.message}")
                        val stackTrace = java.io.StringWriter().apply {
                            e.printStackTrace(java.io.PrintWriter(this))
                        }.toString()
                        Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                        
                        // Небольшая пауза перед следующей попыткой
                        Thread.sleep(100)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Непредвиденная ошибка при приеме клиента: ${e.message}")
                    val stackTrace = java.io.StringWriter().apply {
                        e.printStackTrace(java.io.PrintWriter(this))
                    }.toString()
                    Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                    
                    // Небольшая пауза перед следующей попыткой
                    Thread.sleep(100)
                }
            }
            
            Log.d(TAG, "Завершен поток прослушивания подключений клиентов")
        }
    }
    
    /**
     * Запуск потока для чтения сообщений от клиента
     */
    private fun startReadingMessages(clientId: String) {
        val streams = clientStreams[clientId] ?: return
        val inputStream = streams.first
        
        thread {
            try {
                Log.d(TAG, "Запущен поток чтения сообщений от клиента $clientId")
                
                while (isRunning && clientSockets.containsKey(clientId)) {
                    try {
                        // Используем более надежный способ чтения объектов
                        val message = try {
                            val obj = inputStream.readObject()
                            Log.d(TAG, "Прочитан объект от клиента $clientId: $obj")
                            obj
                        } catch (e: ClassNotFoundException) {
                            Log.e(TAG, "Ошибка десериализации сообщения от клиента $clientId: ${e.message}")
                            val stackTrace = java.io.StringWriter().apply {
                                e.printStackTrace(java.io.PrintWriter(this))
                            }.toString()
                            Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                            null
                        } catch (e: java.io.EOFException) {
                            Log.e(TAG, "EOF ошибка при десериализации от клиента $clientId: ${e.message}")
                            closeConnection(clientId)
                            break
                        } catch (e: java.net.SocketTimeoutException) {
                            // Тайм-аут это нормально, просто продолжаем цикл
                            continue
                        } catch (e: java.lang.InternalError) {
                            Log.e(TAG, "Внутренняя ошибка при десериализации: ${e.message}")
                            val stackTrace = java.io.StringWriter().apply {
                                e.printStackTrace(java.io.PrintWriter(this))
                            }.toString()
                            Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                            null
                        } catch (e: Exception) {
                            Log.e(TAG, "Непредвиденная ошибка при десериализации: ${e.message}")
                            val stackTrace = java.io.StringWriter().apply {
                                e.printStackTrace(java.io.PrintWriter(this))
                            }.toString()
                            Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                            null
                        }
                        
                        if (message == null) {
                            // Пропускаем обработку неудачно десериализованного сообщения
                            Log.w(TAG, "Пропускаем null-сообщение от клиента $clientId")
                            continue
                        }
                        
                        Log.d(TAG, "Сообщение получено от клиента $clientId: $message")
                        
                        // Уведомляем о получении сообщения
                        handler.post {
                            listener?.onMessageReceived(message)
                        }
                        
                        // Если сервер, пересылаем сообщение всем остальным клиентам
                        if (isServer) {
                            Log.d(TAG, "Пересылаем сообщение от клиента $clientId другим клиентам")
                            for (otherId in clientStreams.keys) {
                                if (otherId != clientId) {
                                    sendMessageToClient(otherId, message)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "IO ошибка при чтении сообщения от клиента $clientId: ${e.message}")
                        val stackTrace = java.io.StringWriter().apply {
                            e.printStackTrace(java.io.PrintWriter(this))
                        }.toString()
                        Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                        closeConnection(clientId)
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Непредвиденная ошибка в цикле чтения сообщений: ${e.message}")
                        val stackTrace = java.io.StringWriter().apply {
                            e.printStackTrace(java.io.PrintWriter(this))
                        }.toString()
                        Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                        // Продолжаем цикл, не закрывая соединение при неожиданных ошибках
                    }
                }
                
                Log.d(TAG, "Завершен поток чтения сообщений от клиента $clientId")
            } catch (e: Exception) {
                Log.e(TAG, "Фатальная ошибка при чтении сообщений: ${e.message}")
                val stackTrace = java.io.StringWriter().apply {
                    e.printStackTrace(java.io.PrintWriter(this))
                }.toString()
                Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                closeConnection(clientId)
            }
        }
    }
    
    /**
     * Закрытие соединения с клиентом
     */
    private fun closeConnection(clientId: String) {
        try {
            val streams = clientStreams.remove(clientId)
            
            try {
                streams?.first?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing input stream for client $clientId: ${e.message}")
            }
            
            try {
                streams?.second?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing output stream for client $clientId: ${e.message}")
            }
            
            try {
                val socket = clientSockets.remove(clientId)
                socket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing socket for client $clientId: ${e.message}")
            }
            
            // Уведомляем об отключении клиента
            handler.post {
                if (isServer) {
                    listener?.onClientDisconnected(clientId)
                } else {
                    listener?.onConnectionChanged(false, null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in closeConnection for client $clientId: ${e.message}")
        }
    }
    
    /**
     * Закрытие всех соединений
     * @param keepServerSocket если true, не закрывает серверный сокет
     */
    private fun closeAllConnections(keepServerSocket: Boolean = false) {
        // Закрываем сервер
        if (!keepServerSocket) {
            try {
                val socket = serverSocket
                serverSocket = null // Сначала обнуляем ссылку
                socket?.close()    // Потом закрываем сокет
            } catch (e: IOException) {
                Log.e(TAG, "Error closing server socket", e)
            }
        }
        
        // Закрываем все клиентские соединения
        for (clientId in clientSockets.keys.toList()) {
            closeConnection(clientId)
        }
        
        // Очищаем список клиентов
        clientSockets.clear()
        clientStreams.clear()
    }
    
    /**
     * Закрытие подключения к серверу
     */
    private fun disconnectFromServer() {
        try {
            try {
                serverInputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing server input stream: ${e.message}")
            }
            
            try {
                serverOutputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing server output stream: ${e.message}")
            }
            
            try {
                serverConnection?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing server connection: ${e.message}")
            }
            
            serverInputStream = null
            serverOutputStream = null
            serverConnection = null
            isConnected = false
            
            // Уведомляем об отключении от сервера
            handler.post {
                listener?.onConnectionChanged(false, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in disconnectFromServer: ${e.message}")
        }
    }
    
    /**
     * Сохраняет текущее состояние сервера между активностями
     * Вызывается перед переходом из одной активности (например, LobbyActivity) 
     * к другой (например, GameActivity)
     */
    fun prepareTransferToNextActivity() {
        Log.d(TAG, "Подготовка к переходу в следующую активность...")
        
        // Запускаем закрытие соединений в отдельном потоке
        thread {
            try {
                // Останавливаем обнаружение сервисов
                stopDiscovery()
                
                // Отменяем регистрацию любого сервиса
                unregisterService()
                
                if (isServer) {
                    // Закрываем все клиентские соединения
                    closeAllConnections(false) // Не сохраняем серверный сокет
                    
                    // Явно закрываем серверный сокет с обработкой ошибок
                    try {
                        val socket = serverSocket
                        serverSocket = null
                        socket?.close()
                        Log.d(TAG, "Серверный сокет успешно закрыт")
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка при закрытии серверного сокета: ${e.message}")
                    }
                    
                    // Добавляем задержку для гарантированного освобождения порта
                    Thread.sleep(300)
                    
                    Log.d(TAG, "Сервер полностью закрыт для перехода к следующей активности")
                } else if (isConnected) {
                    // Если мы клиент, закрываем соединение с сервером
                    disconnectFromServer()
                    Log.d(TAG, "Клиентское соединение закрыто для перехода к следующей активности")
                }
                
                // Удаляем из списка активных инстансов
                synchronized(activeInstances) {
                    activeInstances.remove(this)
                    Log.d(TAG, "Удалено из активных экземпляров, осталось: ${activeInstances.size}")
                }
                
                // Сбрасываем порт
                CURRENT_PORT = DEFAULT_PORT
                
                // Сбрасываем все флаги состояния
                isRunning = false
                isServer = false
                isConnected = false
                isDiscovering = false
                isRegistered = false
                
                // Очищаем все коллекции
                clientSockets.clear()
                clientStreams.clear()
                discoveredServers.clear()
                
                Log.d(TAG, "Подготовка к переходу завершена, ресурсы освобождены")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при подготовке к переходу: ${e.message}")
            }
        }
    }

    /**
     * Запуск потока для чтения сообщений от сервера
     */
    private fun startClientMessageListener() {
        // Запускаем поток для чтения данных с сервера
        thread {
            Log.d(TAG, "Запущен поток для чтения сообщений от сервера")
            handleClientMessages()
        }
    }

    /**
     * Проверяет, является ли устройство хостом (сервером)
     */
    fun isHost(): Boolean {
        return isServer
    }

    /**
     * Обработка входящих сообщений для клиента
     */
    private fun handleClientMessages() {
        try {
            while (isConnected && serverConnection != null && !serverConnection!!.isClosed) {
                try {
                    val message = serverInputStream?.readObject() as? GameMessage
                    
                    if (message != null) {
                        // Сообщаем о полученном сообщении
                        when (message) {
                            is GameMessage.JoinGame -> {
                                Log.d(TAG, "Получено игровое сообщение JoinGame от сервера: ${message.player}")
                            }
                            is GameMessage.UpdateGamePlayer -> {
                                Log.d(TAG, "Получено игровое сообщение UpdateGamePlayer от сервера")
                            }
                            is GameMessage.PlayerShot -> {
                                Log.d(TAG, "Получено игровое сообщение PlayerShot от сервера: playerId=${message.playerId}")
                            }
                            is GameMessage.PlaneHit -> {
                                Log.d(TAG, "Получено игровое сообщение PlaneHit от сервера: playerId=${message.playerId}")
                            }
                            else -> {
                                Log.d(TAG, "Получено сообщение от сервера: $message")
                            }
                        }
                        
                        onMessageReceived(message)
                    } else {
                        Log.w(TAG, "Skipping null message")
                    }
                } catch (e: SocketTimeoutException) {
                    // Таймаут - это нормально, продолжаем слушать
                    Log.d(TAG, "Socket timeout during read from server: ${e.message}")
                    continue
                } catch (e: EOFException) {
                    // Соединение закрыто сервером
                    Log.e(TAG, "EOF error during deserialization from server: ${e.message}")
                    break
                } catch (e: ClassNotFoundException) {
                    Log.e(TAG, "ClassNotFound error during deserialization from server: ${e.message}")
                    val stackTrace = java.io.StringWriter().apply {
                        e.printStackTrace(java.io.PrintWriter(this))
                    }.toString()
                    Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error during deserialization: ${e.message}")
                    val stackTrace = java.io.StringWriter().apply {
                        e.printStackTrace(java.io.PrintWriter(this))
                    }.toString()
                    Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
                    
                    if (e is SocketException && e.message?.contains("Socket closed") == true) {
                        // Сокет закрыт, завершаем цикл
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в цикле чтения сообщений: ${e.message}")
            val stackTrace = java.io.StringWriter().apply {
                e.printStackTrace(java.io.PrintWriter(this))
            }.toString()
            Log.e(TAG, "Подробная информация об ошибке: $stackTrace")
        } finally {
            // В случае выхода из цикла закрываем соединение
            disconnectFromServer()
            Log.d(TAG, "Завершен поток чтения сообщений от сервера")
        }
    }

    /**
     * Обработка входящих сообщений для сервера
     */
    private fun handleServerMessages(clientId: String) {
        val clientInfo = clientStreams[clientId] ?: return
        val clientSocket = clientSockets[clientId] ?: return
        
        try {
            while (isServer && clientSockets.containsKey(clientId) && !clientSocket.isClosed) {
                try {
                    val message = clientInfo.first?.readObject() as? GameMessage
                    
                    if (message != null) {
                        // Сообщаем о полученном сообщении
                        when (message) {
                            is GameMessage.JoinGame -> {
                                Log.d(TAG, "Получено игровое сообщение JoinGame от клиента $clientId: ${message.player}")
                                
                                // Отправляем текущим клиентам информацию о новом игроке
                                broadcastMessageToClients(message, excludeClientId = clientId)
                            }
                            is GameMessage.UpdateGamePlayer -> {
                                Log.d(TAG, "Получено игровое сообщение UpdateGamePlayer от клиента $clientId")
                                
                                // Отправляем всем остальным клиентам
                                broadcastMessageToClients(message, excludeClientId = clientId)
                            }
                            is GameMessage.PlayerShot -> {
                                Log.d(TAG, "Получено игровое сообщение PlayerShot от клиента $clientId: playerId=${message.playerId}")
                                
                                // Отправляем всем клиентам, включая отправителя
                                broadcastMessageToClients(message)
                            }
                            is GameMessage.PlaneHit -> {
                                Log.d(TAG, "Получено игровое сообщение PlaneHit от клиента $clientId: playerId=${message.playerId}")
                                
                                // Отправляем всем клиентам, включая отправителя
                                broadcastMessageToClients(message)
                            }
                            else -> {
                                Log.d(TAG, "Получено сообщение от клиента $clientId: $message")
                                
                                // Передаем сообщение слушателю сервиса
                                onMessageReceived(message, clientId)
                            }
                        }
                        
                    } else {
                        Log.w(TAG, "Skipping null message")
                    }
                } catch (e: SocketTimeoutException) {
                    // Таймаут чтения - это нормально, продолжаем слушать
                    Log.e(TAG, "Socket timeout during read from client $clientId: ${e.message}")
                } catch (e: EOFException) {
                    // Клиент отключился
                    Log.e(TAG, "Client $clientId disconnected (EOF): ${e.message}")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error during deserialization from client $clientId: ${e.message}")
                    if (e is SocketException && e.message?.contains("Socket closed") == true) {
                        // Сокет закрыт, завершаем цикл
                        break
                    }
                }
            }
        } finally {
            // Клиент отключился или произошла ошибка, закрываем соединение
            closeConnection(clientId)
        }
    }

    /**
     * Отправка сообщения всем клиентам кроме указанного
     */
    private fun broadcastMessageToClients(message: Any, excludeClientId: String? = null) {
        if (message !is Serializable) {
            Log.e(TAG, "Cannot broadcast non-serializable message: $message")
            return
        }
        
        for (clientId in clientStreams.keys) {
            if (excludeClientId == null || clientId != excludeClientId) {
                // Проверяем, что соединение активно
                val socket = clientSockets[clientId]
                if (socket != null && !socket.isClosed && socket.isConnected) {
                    sendMessageToClient(clientId, message)
                }
            }
        }
    }

    /**
     * Обработка входящих сообщений
     */
    private fun onMessageReceived(message: Any, clientId: String? = null) {
        handler.post {
            listener?.onMessageReceived(message)
        }
    }

    /**
     * Начать обнаружение серверов (альтернативное название для discoverServers)
     */
    fun startDiscovery() {
        discoverServers()
    }

    /**
     * Переключение на игровой режим без разрыва соединения
     * @param gameId идентификатор игры
     */
    fun switchToGameMode(gameId: String) {
        Log.d(TAG, "Переключение на игровой режим с ID: $gameId")
        
        // Переключаем режим сервиса на GAME
        serviceMode = ServiceMode.GAME
        
        // Изменяем название сервиса
        val oldName = serviceName
        serviceName = "${GAME_PREFIX}${gameId}"
        
        // Останавливаем текущую регистрацию сервиса
        unregisterService()
        
        // Регистрируем сервис с новым именем
        if (serverSocket != null && !serverSocket!!.isClosed) {
            val port = serverSocket!!.localPort
            registerService(port)
            Log.d(TAG, "Сервис переключен с $oldName на $serviceName")
        } else {
            Log.e(TAG, "Ошибка при переключении сервиса: серверный сокет закрыт или null")
        }
    }
    
    /**
     * Проверка активного состояния сервиса
     * @return true, если сервис запущен
     */
    fun isRunning(): Boolean {
        return isRunning
    }
} 