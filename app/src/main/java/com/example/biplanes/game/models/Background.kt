package com.example.biplanes.game.models

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import kotlin.random.Random

/**
 * Класс для управления фоном игры
 */
class Background(
    private val screenWidth: Float,
    private val screenHeight: Float
) {
    private val cloudManager = CloudManager(screenWidth, screenHeight)

    private val groundPaint = Paint().apply {
        shader = LinearGradient(
            0f, screenHeight * 0.85f,
            0f, screenHeight,
            Color.parseColor("#228B22"), // Темно-зеленый
            Color.parseColor("#006400"), // Темно-зеленый
            Shader.TileMode.CLAMP
        )
    }

    private val groundPath = Path()
    private var groundHeight = screenHeight * 0.85f
    init {
        createGroundPath()
    }

    private fun createGroundPath() {
        groundPath.reset()
        groundPath.moveTo(0f, screenHeight)

        // Создаем неровности земли, синхронизированные с облаками
        val segments = 20
        val segmentWidth = screenWidth / segments
        val maxHeight = screenHeight * 0.02f

        // Используем синусоидальную функцию для создания плавных неровностей
        for (i in 0..segments) {
            val x = i * segmentWidth
            val baseY = groundHeight
            val noise = Math.sin(i * 0.5) * maxHeight
            val y = baseY + noise.toFloat()
            
            if (i == 0) {
                groundPath.moveTo(x, y)
            } else {
                groundPath.lineTo(x, y)
            }
        }

        // Замыкаем путь
        groundPath.lineTo(screenWidth, screenHeight)
        groundPath.lineTo(0f, screenHeight)
        groundPath.close()
    }

    /**
     * Обновляет состояние облаков
     * @param deltaTime время, прошедшее с последнего обновления (в секундах)
     */
    fun update(deltaTime: Float) {
        cloudManager.update(deltaTime)
    }

    fun draw(canvas: Canvas) {        
        cloudManager.draw(canvas)
        canvas.drawPath(groundPath, groundPaint)
    }

    /**
     * Возвращает высоту земли
     * @return высота земли
     */
    fun getGroundHeight(): Float {
        return groundHeight
    }
} 