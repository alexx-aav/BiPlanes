package com.example.biplanes.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Сервис для работы с Wi-Fi Direct
 */
class WiFiDirectService(private val context: Context) {
    
    companion object {
        private const val TAG = "WiFiDirectService"
        private const val PORT = 8888
    }
    
    // Интерфейс для обратной связи
    interface WiFiDirectListener {
        fun onDeviceDiscovered(device: WifiP2pDevice)
        fun onConnectionChanged(isConnected: Boolean, groupOwnerAddress: String?)
        fun onDeviceDisconnected()
        fun onMessageReceived(message: Any)
    }
    
    // Менеджер Wi-Fi Direct
    private val manager: WifiP2pManager by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    }
    
    // Канал для связи с менеджером
    private val channel: WifiP2pManager.Channel by lazy {
        manager.initialize(context, Looper.getMainLooper(), null)
    }
    
    // Приемник широковещательных сообщений
    private val receiver = WiFiDirectBroadcastReceiver()
    
    // Список обнаруженных устройств
    private val devices = mutableListOf<WifiP2pDevice>()
    
    // Слушатель событий
    private var listener: WiFiDirectListener? = null
    
    // Флаг, указывающий, является ли устройство владельцем группы
    private var isGroupOwner = false
    
    // Адрес владельца группы
    private var groupOwnerAddress: String? = null
    
    // Сокеты для обмена данными
    private var clientSocket: Socket? = null
    private var serverSocket: ServerSocket? = null
    
    // Потоки для обмена данными
    private var inputStream: ObjectInputStream? = null
    private var outputStream: ObjectOutputStream? = null
    
    // Флаг, указывающий, запущен ли сервис
    private var isRunning = false
    
    // Хэндлер для выполнения задач в основном потоке
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * Установка слушателя событий
     */
    fun setListener(listener: WiFiDirectListener) {
        this.listener = listener
    }
    
    /**
     * Запуск сервиса
     */
    fun start() {
        if (isRunning) return
        
        try {
            // Регистрируем приемник широковещательных сообщений
            val intentFilter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            context.registerReceiver(receiver, intentFilter)
            
            isRunning = true
            Log.d(TAG, "WiFiDirectService started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting WiFiDirectService", e)
        }
    }
    
    /**
     * Остановка сервиса
     */
    fun stop() {
        if (!isRunning) return
        
        try {
            // Отменяем обнаружение устройств
            manager.stopPeerDiscovery(channel, null)
            
            // Отключаемся от группы
            manager.removeGroup(channel, null)
            
            // Закрываем сокеты
            closeConnection()
            
            // Отменяем регистрацию приемника
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Receiver not registered", e)
            }
            
            isRunning = false
            Log.d(TAG, "WiFiDirectService stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WiFiDirectService", e)
        }
    }
    
    /**
     * Начало обнаружения устройств
     */
    fun discoverPeers() {
        try {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Discovery initiated")
                }
                
                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Discovery failed: $reason")
                    // Уведомляем об ошибке
                    handler.post {
                        listener?.onDeviceDisconnected()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering peers", e)
        }
    }
    
    /**
     * Подключение к устройству
     */
    fun connect(device: WifiP2pDevice) {
        try {
            val config = WifiP2pConfig().apply {
                deviceAddress = device.deviceAddress
                // Увеличиваем таймаут подключения
                groupOwnerIntent = 0 // Предпочитаем быть клиентом
            }
            
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Connection initiated")
                }
                
                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Connection failed: $reason")
                    // Уведомляем об ошибке
                    handler.post {
                        listener?.onDeviceDisconnected()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device", e)
        }
    }
    
    /**
     * Отправка сообщения
     */
    fun sendMessage(message: Any) {
        if (outputStream == null) {
            Log.e(TAG, "Cannot send message, outputStream is null")
            return
        }
        
        thread {
            try {
                // Добавляем небольшую задержку перед отправкой сообщения
                Thread.sleep(500)
                
                outputStream?.writeObject(message)
                outputStream?.flush()
                Log.d(TAG, "Message sent: $message")
            } catch (e: IOException) {
                Log.e(TAG, "Error sending message", e)
                closeConnection()
                handler.post {
                    listener?.onDeviceDisconnected()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error sending message", e)
                closeConnection()
                handler.post {
                    listener?.onDeviceDisconnected()
                }
            }
        }
    }
    
    /**
     * Закрытие соединения
     */
    private fun closeConnection() {
        try {
            inputStream?.close()
            outputStream?.close()
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing connection", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error closing connection", e)
        } finally {
            inputStream = null
            outputStream = null
            clientSocket = null
            serverSocket = null
        }
    }
    
    /**
     * Приемник широковещательных сообщений для Wi-Fi Direct
     */
    private inner class WiFiDirectBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    // Проверяем, включен ли Wi-Fi Direct
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    Log.d(TAG, "Wi-Fi Direct is ${if (isEnabled) "enabled" else "disabled"}")
                }
                
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    // Получаем список обнаруженных устройств
                    manager.requestPeers(channel) { peerList: WifiP2pDeviceList ->
                        val refreshedDevices = peerList.deviceList.toList()
                        Log.d(TAG, "Discovered devices: ${refreshedDevices.size}")
                        
                        // Уведомляем о новых устройствах
                        for (device in refreshedDevices) {
                            if (!devices.contains(device)) {
                                devices.add(device)
                                handler.post {
                                    listener?.onDeviceDiscovered(device)
                                }
                            }
                        }
                    }
                }
                
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    // Проверяем, изменилось ли состояние подключения
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    
                    if (networkInfo?.isConnected == true) {
                        // Подключение установлено, запрашиваем информацию о группе
                        manager.requestConnectionInfo(channel) { info: WifiP2pInfo ->
                            isGroupOwner = info.isGroupOwner
                            groupOwnerAddress = info.groupOwnerAddress?.hostAddress
                            
                            Log.d(TAG, "Connected to group. isGroupOwner: $isGroupOwner, address: $groupOwnerAddress")
                            
                            // Уведомляем о подключении
                            handler.post {
                                listener?.onConnectionChanged(true, groupOwnerAddress)
                            }
                            
                            // Запускаем соответствующий поток для обмена данными
                            if (isGroupOwner) {
                                startServerThread()
                            } else {
                                startClientThread()
                            }
                        }
                    } else {
                        // Подключение разорвано
                        Log.d(TAG, "Disconnected from group")
                        closeConnection()
                        handler.post {
                            listener?.onConnectionChanged(false, null)
                        }
                    }
                }
                
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Информация о текущем устройстве изменилась
                    val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    Log.d(TAG, "This device changed: ${device?.deviceName}")
                }
            }
        }
    }
    
    /**
     * Запуск серверного потока для приема подключений
     */
    private fun startServerThread() {
        thread {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "Server socket created")
                
                // Устанавливаем таймаут для ожидания подключения
                serverSocket?.soTimeout = 30000 // 30 секунд
                
                // Ожидаем подключения клиента
                val client = serverSocket?.accept()
                Log.d(TAG, "Client connected: ${client?.inetAddress?.hostAddress}")
                
                if (client != null) {
                    clientSocket = client
                    
                    // Создаем потоки для обмена данными
                    try {
                        outputStream = ObjectOutputStream(client.getOutputStream())
                        inputStream = ObjectInputStream(client.getInputStream())
                        
                        // Запускаем поток для чтения сообщений
                        startReadingMessages()
                    } catch (e: IOException) {
                        Log.e(TAG, "Error creating streams", e)
                        closeConnection()
                        handler.post {
                            listener?.onDeviceDisconnected()
                        }
                    }
                } else {
                    Log.e(TAG, "Client is null")
                    closeConnection()
                    handler.post {
                        listener?.onDeviceDisconnected()
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error creating server socket or accepting client", e)
                closeConnection()
                handler.post {
                    listener?.onDeviceDisconnected()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in server thread", e)
                closeConnection()
                handler.post {
                    listener?.onDeviceDisconnected()
                }
            }
        }
    }
    
    /**
     * Запуск клиентского потока для подключения к серверу
     */
    private fun startClientThread() {
        thread {
            try {
                // Ждем немного, чтобы сервер успел запуститься
                Thread.sleep(2000)
                
                // Проверяем, что адрес сервера не пустой
                if (groupOwnerAddress.isNullOrEmpty()) {
                    Log.e(TAG, "Group owner address is null or empty")
                    handler.post {
                        listener?.onDeviceDisconnected()
                    }
                    return@thread
                }
                
                // Максимальное количество попыток подключения
                val maxRetries = 3
                var retryCount = 0
                var connected = false
                
                while (retryCount < maxRetries && !connected) {
                    try {
                        // Подключаемся к серверу
                        val socket = Socket()
                        socket.connect(InetSocketAddress(groupOwnerAddress, PORT), 10000) // Увеличиваем таймаут до 10 секунд
                        Log.d(TAG, "Connected to server: $groupOwnerAddress")
                        
                        clientSocket = socket
                        
                        // Создаем потоки для обмена данными
                        outputStream = ObjectOutputStream(socket.getOutputStream())
                        inputStream = ObjectInputStream(socket.getInputStream())
                        
                        connected = true
                        
                        // Запускаем поток для чтения сообщений
                        startReadingMessages()
                    } catch (e: IOException) {
                        Log.e(TAG, "Error connecting to server (attempt ${retryCount + 1}/$maxRetries)", e)
                        retryCount++
                        
                        if (retryCount < maxRetries) {
                            // Ждем перед следующей попыткой
                            Thread.sleep(1000)
                        } else {
                            closeConnection()
                            handler.post {
                                listener?.onDeviceDisconnected()
                            }
                        }
                    }
                }
            } catch (e: InterruptedException) {
                Log.e(TAG, "Thread interrupted", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in client thread", e)
                closeConnection()
                handler.post {
                    listener?.onDeviceDisconnected()
                }
            }
        }
    }
    
    /**
     * Запуск потока для чтения сообщений
     */
    private fun startReadingMessages() {
        thread {
            try {
                while (true) {
                    val message = inputStream?.readObject()
                    Log.d(TAG, "Message received: $message")
                    
                    // Уведомляем о получении сообщения
                    handler.post {
                        message?.let { listener?.onMessageReceived(it) }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error reading message", e)
                closeConnection()
                handler.post {
                    listener?.onDeviceDisconnected()
                }
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "Error deserializing message", e)
            }
        }
    }
} 