package com.example.biplanes.game.models

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.sin
import kotlin.random.Random

/**
 * Класс для управления фоном игры
 */
class Background(
    private val screenWidth: Float,
    private val screenHeight: Float
) {
    private val skyPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f,
            0f, screenHeight,
            Color.parseColor("#87CEEB"), // Светло-голубой
            Color.parseColor("#1E90FF"), // Темно-голубой
            Shader.TileMode.CLAMP
        )
    }

    private val groundPaint = Paint().apply {
        shader = LinearGradient(
            0f, screenHeight * 0.85f,
            0f, screenHeight,
            Color.parseColor("#228B22"), // Темно-зеленый
            Color.parseColor("#006400"), // Темно-зеленый
            Shader.TileMode.CLAMP
        )
    }

    private val cloudPaint = Paint().apply {
        color = Color.WHITE
        alpha = 200  // Немного увеличиваем непрозрачность
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val groundPath = Path()
    private val clouds = mutableListOf<Cloud>()
    private var groundHeight = screenHeight * 0.85f

    init {
        createClouds()
        createGroundPath()
    }

    /**
     * Создает облака на небе
     */
    private fun createClouds() {
        val numClouds = 5
        for (i in 0 until numClouds) {
            val baseSize = Random.nextFloat() * 60f + 40f
            val width = baseSize * (1.5f + Random.nextFloat())  // Ширина облака
            clouds.add(
                Cloud(
                    x = Random.nextFloat() * screenWidth,
                    y = Random.nextFloat() * (screenHeight * 0.4f),
                    baseSize = baseSize,
                    width = width,
                    speed = Random.nextFloat() * 0.5f + 0.2f,
                    bubbles = generateCloudBubbles(baseSize),
                    scale = 0.5f + Random.nextFloat() * 0.5f
                )
            )
        }
    }

    /**
     * Генерирует "пузыри" для облака
     */
    private fun generateCloudBubbles(baseSize: Float): List<CloudBubble> {
        val bubbles = mutableListOf<CloudBubble>()
        val bubblesCount = (3 + Random.nextInt(4)) // 3-6 "пузырей" для одного облака
        
        // Центральный большой пузырь
        bubbles.add(CloudBubble(0f, 0f, baseSize, 255))
        
        // Дополнительные пузыри вокруг центрального
        for (i in 1 until bubblesCount) {
            val angle = Random.nextFloat() * 360f
            val distance = baseSize * 0.6f * Random.nextFloat()
            val offsetX = Math.cos(Math.toRadians(angle.toDouble())).toFloat() * distance
            val offsetY = Math.sin(Math.toRadians(angle.toDouble())).toFloat() * distance * 0.5f // Сжимаем по вертикали
            val size = baseSize * (0.5f + Random.nextFloat() * 0.5f) // 50-100% от базового размера
            val alpha = 180 + Random.nextInt(75) // Разная прозрачность для объемного эффекта
            
            bubbles.add(CloudBubble(offsetX, offsetY, size, alpha))
        }
        
        return bubbles
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
     * Обновляет состояние фона
     * @param deltaTime время, прошедшее с последнего обновления (в секундах)
     */
    fun update(deltaTime: Float) {
        // Обновляем положение каждого облака
        clouds.forEach { cloud ->
            // Скорость движения облака пропорциональна его размеру
            // (более крупные облака двигаются медленнее)
            val speed = cloud.speed * (1f - cloud.scale * 0.5f)
            
            // Перемещаем облако влево
            cloud.x -= speed * deltaTime * 60f  // Масштабируем скорость с учетом deltaTime
            
            // Добавляем небольшой вертикальный дрейф
            cloud.y += sin(cloud.x * 0.01f) * 0.2f * deltaTime * 60f
            
            // Если облако вышло за левую границу экрана, перемещаем его обратно вправо
            if (cloud.x + cloud.width < 0) {
                cloud.x = screenWidth + Random.nextFloat() * 100f
                
                // Немного изменяем высоту облака для разнообразия
                cloud.y = Random.nextFloat() * screenHeight * 0.3f
                
                // Также случайно изменяем размер облака
                cloud.scale = 0.5f + Random.nextFloat() * 0.5f
            }
        }
    }

    /**
     * Рисует фон на канвасе
     * @param canvas канвас для рисования
     */
    fun draw(canvas: Canvas) {
        // Рисуем небо
        canvas.drawRect(0f, 0f, screenWidth, screenHeight, skyPaint)

        // Рисуем облака
        clouds.forEach { cloud ->
            drawCloud(canvas, cloud)
        }

        // Рисуем землю
        canvas.drawPath(groundPath, groundPaint)
    }

    /**
     * Рисует одно облако на канвасе
     */
    private fun drawCloud(canvas: Canvas, cloud: Cloud) {
        for (bubble in cloud.bubbles) {
            cloudPaint.alpha = bubble.alpha
            canvas.drawCircle(
                cloud.x + bubble.offsetX,
                cloud.y + bubble.offsetY,
                bubble.size,
                cloudPaint
            )
        }
    }

    /**
     * Возвращает высоту земли
     * @return высота земли
     */
    fun getGroundHeight(): Float {
        return groundHeight
    }

    /**
     * Внутренний класс для представления облака
     */
    private data class Cloud(
        var x: Float,
        var y: Float,
        val baseSize: Float,
        val width: Float,
        val speed: Float,
        var bubbles: List<CloudBubble>,
        var scale: Float
    )
    
    /**
     * Внутренний класс для представления "пузыря" облака
     */
    private data class CloudBubble(
        val offsetX: Float,
        val offsetY: Float,
        val size: Float,
        val alpha: Int
    )
} 