package com.example.biplanes.game.models

import android.graphics.RectF

interface Collidable {
    var position: Vector2D
    var width: Float
    var height: Float
    val bounds: RectF
        get() = RectF(position.x, position.y, position.x + width, position.y + height)
    fun checkCollision(other: Collidable): Boolean
    fun checkCollision(x: Float, y: Float): Boolean {
        return x >= position.x &&
                x <= position.x + width &&
                y >= position.y &&
                y <= position.y + height
    }
}