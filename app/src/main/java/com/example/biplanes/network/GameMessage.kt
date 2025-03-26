package com.example.biplanes.network

import com.example.biplanes.game.models.GameType
import com.example.biplanes.game.models.PlaneColor
import com.example.biplanes.game.models.Player
import com.example.biplanes.game.models.Vector2D
import java.io.Serializable

/**
 * Базовый класс для всех сообщений в игре
 */
sealed class GameMessage : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
    
    /**
     * Сообщение о присоединении игрока к лобби
     */
    data class JoinLobby(
        val player: Player,
        val gameType: GameType
    ) : GameMessage() {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
    
    /**
     * Сообщение о готовности игрока к игре
     */
    data class PlayerReady(
        val playerId: String,
        val isReady: Boolean
    ) : GameMessage() {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
    
    /**
     * Сообщение о начале игры
     */
    data class StartGame(
        val gameType: GameType,
        val players: List<Player>
    ) : GameMessage() {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
    
    /**
     * Сообщение о движении самолета
     */
    data class PlaneMovement(
        val playerId: String,
        val position: Vector2D,
        val rotation: Float,
        val velocity: Vector2D
    ) : GameMessage() {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
    
    /**
     * Сообщение о выстреле
     */
    data class Fire(
        val playerId: String,
        val position: Vector2D,
        val velocity: Vector2D,
        val color: Int
    ) : GameMessage() {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
    
    /**
     * Сообщение о катапультировании пилота
     */
    data class Eject(
        val playerId: String,
        val position: Vector2D
    ) : GameMessage() {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
    
    /**
     * Сообщение о попадании в самолет
     */
    data class Hit(
        val playerId: String,
        val damage: Int
    ) : GameMessage() {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
    
    /**
     * Сообщение о уничтожении самолета
     */
    data class PlaneDestroyed(
        val playerId: String
    ) : GameMessage() {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
    
    /**
     * Сообщение о спасении пилота
     */
    data class PilotRescued(
        val playerId: String
    ) : GameMessage() {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
    
    /**
     * Сообщение о выходе игрока из игры
     */
    data class LeaveGame(
        val playerId: String
    ) : GameMessage() {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
    
    /**
     * Сообщение о завершении игры
     */
    data class GameOver(
        val winnerId: String?
    ) : GameMessage() {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
    
    /**
     * Сообщение об обновлении типа игры
     */
    data class UpdateGameType(val gameType: GameType) : GameMessage() {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
} 