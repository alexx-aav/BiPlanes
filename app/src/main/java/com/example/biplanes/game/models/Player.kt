package com.example.biplanes.game.models

import java.io.Serializable

/**
 * Класс, представляющий игрока.
 * @property id Уникальный идентификатор игрока.
 * @property name Имя игрока.
 * @property color Цвет самолета игрока.
 * @property isReady Флаг готовности игрока к началу игры.
 * @property isHost Флаг, указывающий, является ли игрок хостом.
 */
data class Player(
    val id: String,
    val name: String,
    val color: PlaneColor,
    val isReady: Boolean = false,
    val isHost: Boolean = false
) : Serializable {
    // Добавляем serialVersionUID для совместимости сериализации
    companion object {
        private const val serialVersionUID = 123456789L
    }
    
    // Переопределяем методы для надежности сериализации
    private fun writeObject(out: java.io.ObjectOutputStream) {
        out.defaultWriteObject()
    }
    
    private fun readObject(input: java.io.ObjectInputStream) {
        input.defaultReadObject()
    }
    
    override fun toString(): String {
        return "Player(id=$id, name=$name, color=$color, isReady=$isReady, isHost=$isHost)"
    }
} 