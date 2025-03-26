package com.example.biplanes.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
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
    private val deviceId = UUID.randomUUID().toString()
    
    // Сокеты для обмена данными
    private var serverSocket: ServerSocket? = null
    private val clientSockets = ConcurrentHashMap<String, Socket>()
    private val clientStreams = ConcurrentHashMap<String, Pair<ObjectInputStream, ObjectOutputStream>>()
    
    // Клиентское подключение к серверу
    private var serverConnection: Socket? = null
    private var serverInputStream: ObjectInputStream? = null
    private var serverOutputStream: ObjectOutputStream? = null
    private var isConnected = false
    
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
        serviceMode = ServiceMode.GAME
        serviceName = "${GAME_PREFIX}${gameId}"
        createServer(serviceName)
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
            for (instance in activeInstances.toList()) {
                if (instance != this) {
                    try {
                        Log.d(TAG, "Stopping another NetworkService instance before creating server")
                        instance.stop(false)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping another NetworkService instance: ${e.message}")
                    }
                }
            }
        }
        
        // Дополнительная задержка для полного освобождения ресурсов
        Thread.sleep(200)
        
        // Запускаем сервер в отдельном потоке
        thread {
            var portIndex = 0
            var maxPortAttempts = PORT_RANGE.size
            
            while (portIndex < maxPortAttempts) {
                val currentPort = PORT_RANGE[portIndex]
                try {
                    // Пытаемся создать сервер на текущем порту
                    val testSocket = ServerSocket(currentPort)
                    testSocket.close()
                    
                    // Если удалось, создаем основной сервер
                    serverSocket = ServerSocket(currentPort)
                    Log.d(TAG, "Server socket created on port $currentPort")
                    
                    // Регистрируем сервис в NSD
                    registerService(currentPort)
                    
                    // Начинаем принимать клиентов
                    acceptClients()
                    
                    // Выходим из цикла, так как сервер успешно создан
                    break
                    
                } catch (e: IOException) {
                    portIndex++
                    
                    if (portIndex < maxPortAttempts) {
                        // Если порт занят, пробуем следующий порт из списка
                        Log.w(TAG, "Port $currentPort is busy, trying next port: ${PORT_RANGE[portIndex]}", e)
                        
                        // Делаем небольшую паузу перед следующей попыткой
                        Thread.sleep(100)
                    } else {
                        Log.e(TAG, "Failed to find available port after $maxPortAttempts attempts", e)
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
     * Подключение к серверу
     */
    fun connectToServer(serverInfo: ServerInfo) {
        if (isConnected) {
            Log.d(TAG, "Уже подключены к серверу")
            return
        }
        
        thread {
            try {
                // Сначала проверяем доступность сервера с меньшим таймаутом
                val pingSocket = Socket()
                try {
                    // Пробуем подключиться с коротким таймаутом для проверки доступности
                    pingSocket.connect(InetSocketAddress(serverInfo.host, serverInfo.port), 2000)
                    pingSocket.close()
                    Log.d(TAG, "Сервер ${serverInfo.host}:${serverInfo.port} доступен")
                } catch (e: IOException) {
                    Log.e(TAG, "Сервер ${serverInfo.host}:${serverInfo.port} недоступен: ${e.message}")
                    
                    // Сообщаем об ошибке
                    handler.post {
                        listener?.onConnectionChanged(false, null)
                    }
                    
                    // Проверяем, является ли причина EHOSTUNREACH или ENETUNREACH
                    if (e.message?.contains("EHOSTUNREACH") == true || 
                        e.message?.contains("ENETUNREACH") == true ||
                        e.message?.contains("No route to host") == true) {
                        // Это сетевая проблема - устройства могут быть в разных подсетях
                        Log.e(TAG, "Проблема с сетью: устройства могут быть в разных подсетях или Wi-Fi настройки не позволяют прямое соединение")
                        
                        // Сообщаем о специфической ошибке
                        handler.post {
                            listener?.onNetworkError("Не удалось подключиться: устройства могут быть в разных сетях")
                        }
                    }
                    
                    return@thread
                }
                
                // Создаем основное соединение
                val socket = Socket()
                socket.connect(InetSocketAddress(serverInfo.host, serverInfo.port), CONNECTION_TIMEOUT)
                socket.soTimeout = SOCKET_READ_TIMEOUT // Устанавливаем меньший таймаут для операций чтения
                Log.d(TAG, "Connected to server: ${serverInfo.host}:${serverInfo.port}")
                
                // Выполняем соединение и обмен данными в потоке
                try {
                    // Создаем потоки для обмена данными в правильном порядке
                    // ВАЖНО: сначала создаем outputStream, потом inputStream
                    val outputStream = ObjectOutputStream(socket.getOutputStream())
                    outputStream.flush() // Важно сделать flush перед созданием inputStream
                    
                    // Используем небольшую задержку между созданием потоков
                    Thread.sleep(50)
                    
                    val inputStream = ObjectInputStream(socket.getInputStream())
                    
                    // Отправляем информацию о клиенте
                    outputStream.writeObject(deviceId)
                    outputStream.flush()
                    
                    // Сохраняем соединение для клиента
                    serverConnection = socket
                    serverOutputStream = outputStream
                    serverInputStream = inputStream
                    isConnected = true
                    
                    // Уведомляем о подключении
                    handler.post {
                        listener?.onConnectionChanged(true, serverInfo.host)
                    }
                    
                    // Запускаем чтение сообщений от сервера
                    thread {
                        try {
                            while (isRunning && isConnected && serverInputStream != null) {
                                try {
                                    // Читаем сообщение от сервера
                                    val message = try {
                                        serverInputStream?.readObject()
                                    } catch (e: ClassNotFoundException) {
                                        Log.e(TAG, "Error deserializing message from server: ${e.message}")
                                        null
                                    } catch (e: java.io.EOFException) {
                                        Log.e(TAG, "EOF error during deserialization from server: ${e.message}")
                                        closeServerConnection()
                                        break
                                    } catch (e: java.net.SocketTimeoutException) {
                                        Log.e(TAG, "Socket timeout during read from server: ${e.message}")
                                        // Просто продолжаем цикл при таймауте чтения
                                        continue
                                    } catch (e: java.lang.InternalError) {
                                        Log.e(TAG, "Internal error during deserialization from server: ${e.message}")
                                        null
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Unexpected error during deserialization from server: ${e.message}")
                                        null
                                    }
                                    
                                    if (message == null) {
                                        Log.w(TAG, "Skipping null message from server")
                                        continue
                                    }
                                    
                                    Log.d(TAG, "Message received from server: $message")
                                    
                                    // Уведомляем о получении сообщения
                                    handler.post {
                                        listener?.onMessageReceived(message)
                                    }
                                    
                                } catch (e: IOException) {
                                    Log.e(TAG, "IO error reading message from server: ${e.message}")
                                    closeServerConnection()
                                    break
                                } catch (e: Exception) {
                                    Log.e(TAG, "Unexpected error in server message loop: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Fatal error in reading messages from server: ${e.message}")
                            closeServerConnection()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up client connection: ${e.message}")
                    try { socket.close() } catch (e2: Exception) { }
                    
                    handler.post {
                        listener?.onConnectionChanged(false, null)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error connecting to server", e)
                handler.post {
                    listener?.onConnectionChanged(false, null)
                }
            }
        }
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
                        closeServerConnection()
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
        while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
            try {
                val clientSocket = serverSocket!!.accept()
                clientSocket.soTimeout = SOCKET_READ_TIMEOUT // Устанавливаем меньший таймаут для операций чтения
                val clientAddress = clientSocket.inetAddress.hostAddress
                Log.d(TAG, "Client connected: $clientAddress")
                
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
                            Log.e(TAG, "Error reading client ID: ${e.message}")
                            try { outputStream.close() } catch (e: Exception) { }
                            try { inputStream.close() } catch (e: Exception) { }
                            try { clientSocket.close() } catch (e: Exception) { }
                        } catch (e: Exception) {
                            Log.e(TAG, "Unexpected error during client handshake: ${e.message}")
                            try { outputStream.close() } catch (e: Exception) { }
                            try { inputStream.close() } catch (e: Exception) { }
                            try { clientSocket.close() } catch (e: Exception) { }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating streams for client $clientAddress: ${e.message}")
                        try { clientSocket.close() } catch (e: Exception) { }
                    }
                }
            } catch (e: IOException) {
                if (isRunning) {
                    Log.e(TAG, "Error accepting client", e)
                    // Небольшая пауза перед следующей попыткой
                    Thread.sleep(100)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error accepting client", e)
                // Небольшая пауза перед следующей попыткой
                Thread.sleep(100)
            }
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
                while (isRunning && clientSockets.containsKey(clientId)) {
                    try {
                        // Используем более надежный способ чтения объектов
                        val message = try {
                            inputStream.readObject()
                        } catch (e: ClassNotFoundException) {
                            Log.e(TAG, "Error deserializing message: ${e.message}")
                            null
                        } catch (e: java.io.EOFException) {
                            Log.e(TAG, "EOF error during deserialization from client $clientId: ${e.message}")
                            closeConnection(clientId)
                            break
                        } catch (e: java.net.SocketTimeoutException) {
                            Log.e(TAG, "Socket timeout during read from client $clientId: ${e.message}")
                            // Просто продолжаем цикл при таймауте чтения
                            continue
                        } catch (e: java.lang.InternalError) {
                            Log.e(TAG, "Internal error during deserialization: ${e.message}")
                            null
                        } catch (e: Exception) {
                            Log.e(TAG, "Unexpected error during deserialization: ${e.message}")
                            null
                        }
                        
                        if (message == null) {
                            // Пропускаем обработку неудачно десериализованного сообщения
                            Log.w(TAG, "Skipping null message")
                            continue
                        }
                        
                        Log.d(TAG, "Message received from client $clientId: $message")
                        
                        // Уведомляем о получении сообщения
                        handler.post {
                            listener?.onMessageReceived(message)
                        }
                        
                        // Если сервер, пересылаем сообщение всем остальным клиентам
                        if (isServer) {
                            for (otherId in clientStreams.keys) {
                                if (otherId != clientId) {
                                    sendMessageToClient(otherId, message)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "IO error reading message from client $clientId: ${e.message}")
                        closeConnection(clientId)
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error in message loop: ${e.message}")
                        // Продолжаем цикл, не закрывая соединение при неожиданных ошибках
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in reading messages: ${e.message}")
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
    private fun closeServerConnection() {
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
            Log.e(TAG, "Error in closeServerConnection: ${e.message}")
        }
    }
    
    /**
     * Сохраняет текущее состояние сервера между активностями
     * Вызывается перед переходом из одной активности (например, LobbyActivity) 
     * к другой (например, GameActivity)
     */
    fun prepareTransferToNextActivity() {
        // Запускаем закрытие соединений в отдельном потоке
        thread {
            try {
                if (isServer) {
                    // При переходе от лобби к игре меняем режим сервиса и имя
                    if (serviceMode == ServiceMode.LOBBY) {
                        Log.d(TAG, "Transitioning from lobby to game server")
                        
                        // Отменяем регистрацию сервиса лобби
                        unregisterService()
                        
                        // Закрываем все соединения клиентов
                        closeAllConnections(true) // Сохраняем серверный сокет
                        
                        // Немного ждем, чтобы убедиться, что операции завершены
                        Thread.sleep(200)
                        
                        // В GameActivity нужно будет вызвать createGameServer() с ID игры
                    } else {
                        // Полностью закрываем все соединения перед переходом
                        unregisterService()
                        closeAllConnections(false)
                        
                        // Явно закрываем серверный сокет
                        try {
                            val socket = serverSocket
                            serverSocket = null
                            socket?.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error closing server socket during transfer: ${e.message}")
                        }
                        
                        Log.d(TAG, "Server completely closed for clean transition to next activity")
                    }
                } else if (isConnected) {
                    // Если мы клиент, также закрываем соединение для чистого перехода
                    closeServerConnection()
                    Log.d(TAG, "Client connection closed for clean transition to next activity")
                }
                
                // Удаляем из списка активных инстансов
                synchronized(activeInstances) {
                    activeInstances.remove(this)
                }
                
                // Сбрасываем порт
                CURRENT_PORT = DEFAULT_PORT
            } catch (e: Exception) {
                Log.e(TAG, "Error during prepareTransferToNextActivity: ${e.message}")
            }
        }
        
        // Сразу устанавливаем флаги, чтобы предотвратить дальнейшее использование сервиса
        isRunning = false
        isServer = false
        isConnected = false
    }
} 