package com.example.biplanes

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.biplanes.game.models.GameType
import com.example.biplanes.game.models.Player
import com.example.biplanes.network.NetworkService
import java.util.UUID

/**
 * Основной класс приложения для хранения глобальных данных
 */
class BiplanesApplication : Application() {
    
    companion object {
        private const val TAG = "BiplanesApplication"
        
        // Ключи для SharedPreferences
        private const val PREFS_NAME = "BiplanesPrefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CURRENT_GAME_ID = "current_game_id"
    }
    
    // Список игроков, доступный для всех активностей
    private var playersList: MutableList<Player> = mutableListOf()
    
    // ID текущего сервера для игры
    private var currentGameServerId: String = ""
    
    // Тип текущей игры
    private var currentGameType: GameType = GameType.TRAINING
    
    // Сервис сети, который будет поддерживаться на уровне приложения
    private var networkService: NetworkService? = null
    
    // Уникальный ID устройства
    private var deviceId: String = ""
    
    override fun onCreate() {
        super.onCreate()
        
        // Инициализируем ID устройства или загружаем сохраненный
        initDeviceId()
        
        Log.d(TAG, "BiplanesApplication создано, ID устройства: $deviceId")
    }
    
    /**
     * Инициализация ID устройства
     */
    private fun initDeviceId() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Пытаемся загрузить сохраненный ID
        deviceId = prefs.getString(KEY_DEVICE_ID, "") ?: ""
        
        // Если ID пустой, генерируем новый
        if (deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString()
            
            // Сохраняем новый ID
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
            Log.d(TAG, "Сгенерирован новый ID устройства: $deviceId")
        } else {
            Log.d(TAG, "Загружен сохраненный ID устройства: $deviceId")
        }
    }
    
    /**
     * Получение ID устройства
     */
    fun getUniqueDeviceId(): String {
        return deviceId
    }
    
    /**
     * Получить список игроков
     */
    fun getPlayers(): List<Player> {
        return playersList.toList()
    }
    
    /**
     * Установить список игроков
     */
    fun setPlayers(players: List<Player>) {
        playersList.clear()
        playersList.addAll(players)
        Log.d(TAG, "Обновлен список игроков: ${playersList.size} игроков")
    }
    
    /**
     * Добавить игрока в список
     */
    fun addPlayer(player: Player) {
        if (!playersList.any { it.id == player.id }) {
            playersList.add(player)
            Log.d(TAG, "Добавлен игрок: ${player.name}, ID: ${player.id}")
        } else {
            // Обновляем данные игрока
            val index = playersList.indexOfFirst { it.id == player.id }
            if (index != -1) {
                playersList[index] = player
                Log.d(TAG, "Обновлен игрок: ${player.name}, ID: ${player.id}")
            }
        }
    }
    
    /**
     * Удалить игрока из списка
     */
    fun removePlayer(playerId: String) {
        val removed = playersList.removeIf { it.id == playerId }
        if (removed) {
            Log.d(TAG, "Удален игрок с ID: $playerId")
        }
    }
    
    /**
     * Получить текущий ID игрового сервера
     */
    fun getCurrentGameServerId(): String {
        return currentGameServerId
    }
    
    /**
     * Установить текущий ID игрового сервера
     */
    fun setCurrentGameServerId(serverId: String) {
        currentGameServerId = serverId
        
        // Сохраняем ID сервера
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT_GAME_ID, serverId)
            .apply()
        
        Log.d(TAG, "Установлен ID игрового сервера: $serverId")
    }
    
    /**
     * Получить текущий тип игры
     */
    fun getCurrentGameType(): GameType {
        return currentGameType
    }
    
    /**
     * Установить текущий тип игры
     */
    fun setCurrentGameType(gameType: GameType) {
        currentGameType = gameType
        Log.d(TAG, "Установлен тип игры: $gameType")
    }
    
    /**
     * Установить сервис сети
     */
    fun setNetworkService(service: NetworkService) {
        networkService = service
        Log.d(TAG, "Установлен NetworkService")
    }
    
    /**
     * Получить сервис сети
     */
    fun getNetworkService(): NetworkService? {
        return networkService
    }
    
    /**
     * Очистить все данные приложения
     */
    fun clearAllData() {
        playersList.clear()
        currentGameServerId = ""
        currentGameType = GameType.TRAINING
        
        // Останавливаем сетевой сервис, если он активен
        networkService?.stop()
        networkService = null
        
        Log.d(TAG, "Очищены все данные приложения")
    }
    
    /**
     * Сохранить данные игроков в SharedPreferences
     */
    fun savePlayersToPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Сохраняем количество игроков
        editor.putInt("players_count", playersList.size)
        
        // Сохраняем данные каждого игрока
        playersList.forEachIndexed { index, player ->
            editor.putString("player_${index}_id", player.id)
            editor.putString("player_${index}_name", player.name)
            editor.putString("player_${index}_color", player.color.name)
            editor.putBoolean("player_${index}_isReady", player.isReady)
            editor.putBoolean("player_${index}_isHost", player.isHost)
        }
        
        // Сохраняем тип игры
        editor.putString("game_type", currentGameType.name)
        
        // Применяем изменения
        editor.apply()
        
        Log.d(TAG, "Сохранены данные игроков в SharedPreferences: ${playersList.size} игроков")
    }
    
    /**
     * Загрузить данные игроков из SharedPreferences
     */
    fun loadPlayersFromPreferences(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val playersCount = prefs.getInt("players_count", 0)
        
        if (playersCount > 0) {
            // Очищаем текущий список
            playersList.clear()
            
            // Загружаем данные каждого игрока
            for (i in 0 until playersCount) {
                val id = prefs.getString("player_${i}_id", "") ?: ""
                val name = prefs.getString("player_${i}_name", "") ?: ""
                val colorName = prefs.getString("player_${i}_color", "RED") ?: "RED"
                val isReady = prefs.getBoolean("player_${i}_isReady", true)
                val isHost = prefs.getBoolean("player_${i}_isHost", false)
                
                // Преобразуем строковое имя цвета в enum
                val color = try {
                    com.example.biplanes.game.models.PlaneColor.valueOf(colorName)
                } catch (e: Exception) {
                    com.example.biplanes.game.models.PlaneColor.RED
                }
                
                // Создаем игрока и добавляем в список
                val player = Player(
                    id = id,
                    name = name,
                    color = color,
                    isReady = isReady,
                    isHost = isHost
                )
                
                playersList.add(player)
            }
            
            // Загружаем тип игры
            val gameTypeName = prefs.getString("game_type", GameType.TRAINING.name) ?: GameType.TRAINING.name
            currentGameType = try {
                GameType.valueOf(gameTypeName)
            } catch (e: Exception) {
                GameType.TRAINING
            }
            
            Log.d(TAG, "Загружены данные игроков из SharedPreferences: ${playersList.size} игроков")
            return true
        }
        
        Log.d(TAG, "Нет сохраненных данных игроков в SharedPreferences")
        return false
    }
} 