package com.example.biplanes.game.models

import java.io.Serializable

/**
 * Класс, представляющий игрока в лобби
 */
data class Player(
    val id: String,
    val name: String,
    val color: PlaneColor,
    val isReady: Boolean = false,
    val isHost: Boolean = false
) : Serializable 