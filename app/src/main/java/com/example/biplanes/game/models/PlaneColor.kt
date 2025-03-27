package com.example.biplanes.game.models

import android.graphics.Color
import java.io.Serializable

/**
 * Перечисление цветов самолетов.
 * @property color Цвет в формате RGB.
 */
enum class PlaneColor(val color: Int) : Serializable {
    RED(Color.RED),
    BLUE(Color.BLUE),
    GREEN(Color.GREEN),
    YELLOW(Color.YELLOW),
    PURPLE(0xFF800080.toInt()),
    ORANGE(0xFFFFA500.toInt());
    
    // Добавляем serialVersionUID для совместимости сериализации
    companion object {
        private const val serialVersionUID = 987654321L
        
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