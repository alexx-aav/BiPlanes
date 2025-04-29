package com.example.biplanes.game.models

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin
import kotlin.random.Random

class CloudManager(
    private val screenWidth: Float,
    private val screenHeight: Float
) {
    private val cloudPaint = Paint().apply {
        color = Color.WHITE
        alpha = 200
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val clouds = mutableListOf<Cloud>()

    init {
        createClouds()
    }

    private fun createClouds() {
        val numClouds = 5
        for (i in 0 until numClouds) {
            val baseSize = Random.nextFloat() * 60f + 40f
            val width = baseSize * (1.5f + Random.nextFloat())
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

    private fun generateCloudBubbles(baseSize: Float): List<CloudBubble> {
        val bubbles = mutableListOf<CloudBubble>()
        val bubblesCount = (3 + Random.nextInt(4))

        bubbles.add(CloudBubble(0f, 0f, baseSize, 255))

        for (i in 1 until bubblesCount) {
            val angle = Random.nextFloat() * 360f
            val distance = baseSize * 0.6f * Random.nextFloat()
            val offsetX = Math.cos(Math.toRadians(angle.toDouble())).toFloat() * distance
            val offsetY = Math.sin(Math.toRadians(angle.toDouble())).toFloat() * distance * 0.5f
            val size = baseSize * (0.5f + Random.nextFloat() * 0.5f)
            val alpha = 180 + Random.nextInt(75)

            bubbles.add(CloudBubble(offsetX, offsetY, size, alpha))
        }

        return bubbles
    }

    fun update(deltaTime: Float) {
        clouds.forEach { cloud ->
            val speed = cloud.speed * (1f - cloud.scale * 0.5f)
            cloud.x -= speed * deltaTime * 60f
            cloud.y += sin(cloud.x * 0.01f) * 0.2f * deltaTime * 60f

            if (cloud.x + cloud.width < 0) {
                cloud.x = screenWidth + Random.nextFloat() * 100f
                cloud.y = Random.nextFloat() * screenHeight * 0.3f
                cloud.scale = 0.5f + Random.nextFloat() * 0.5f
            }
        }
    }

    fun draw(canvas: Canvas) {
        clouds.forEach { cloud ->
            drawCloud(canvas, cloud)
        }
    }

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

    private data class Cloud(
        var x: Float,
        var y: Float,
        val baseSize: Float,
        val width: Float,
        val speed: Float,
        var bubbles: List<CloudBubble>,
        var scale: Float
    )

    private data class CloudBubble(
        val offsetX: Float,
        val offsetY: Float,
        val size: Float,
        val alpha: Int
    )
}