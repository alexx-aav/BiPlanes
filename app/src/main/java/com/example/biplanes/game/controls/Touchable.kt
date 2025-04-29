package com.example.biplanes.game.controls

interface Touchable {
    fun isPointInBounds(x: Float, y: Float): Boolean
}