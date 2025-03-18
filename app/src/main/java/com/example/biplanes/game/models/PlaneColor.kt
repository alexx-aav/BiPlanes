package com.example.biplanes.game.models

import android.graphics.Color

/**
 * Перечисление цветов самолетов
 */
enum class PlaneColor(val color: Int) {
    RED(Color.RED),
    BLUE(Color.BLUE),
    GREEN(Color.GREEN),
    YELLOW(Color.YELLOW),
    PURPLE(0xFF800080.toInt()),
    ORANGE(0xFFFFA500.toInt());
    
    companion object {
        /**
         * Получить цвет по индексу
         */
        fun fromIndex(index: Int): PlaneColor {
            return values()[index % values().size]
        }
        
        /**
         * Получить случайный цвет
         */
        fun random(): PlaneColor {
            return values().random()
        }
    }
} 