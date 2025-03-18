package com.example.biplanes.game.models

import java.io.Serializable

enum class GameType : Serializable {
    ONE_VS_ONE,
    TWO_VS_TWO,
    FREE_FOR_ALL,
    TRAINING;

    val minPlayers: Int
        get() = when (this) {
            ONE_VS_ONE -> 2
            TWO_VS_TWO -> 4
            FREE_FOR_ALL -> 3
            TRAINING -> 1
        }

    val maxPlayers: Int
        get() = when (this) {
            ONE_VS_ONE -> 2
            TWO_VS_TWO -> 4
            FREE_FOR_ALL -> 6
            TRAINING -> 1
        }
} 