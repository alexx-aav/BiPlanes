package com.example.biplanes.game.models

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.io.Serializable

class Vector2D(var x: Float, var y: Float) : Serializable {
    
    constructor() : this(0f, 0f)
    
    fun set(x: Float, y: Float) {
        this.x = x
        this.y = y
    }
    
    fun add(other: Vector2D): Vector2D {
        return Vector2D(x + other.x, y + other.y)
    }
    
    fun subtract(other: Vector2D): Vector2D {
        return Vector2D(x - other.x, y - other.y)
    }
    
    fun multiply(scalar: Float): Vector2D {
        return Vector2D(x * scalar, y * scalar)
    }
    
    fun divide(scalar: Float) {
        if (scalar != 0f) {
            x /= scalar
            y /= scalar
        }
    }
    
    fun length(): Float {
        return sqrt(x * x + y * y)
    }
    
    fun normalize(): Vector2D {
        val len = length()
        return if (len > 0) Vector2D(x / len, y / len) else Vector2D(0f, 0f)
    }
    
    fun angle(): Float {
        return atan2(y, x)
    }
    
    fun rotate(angle: Float) {
        val cos = cos(angle)
        val sin = sin(angle)
        val newX = x * cos - y * sin
        val newY = x * sin + y * cos
        x = newX
        y = newY
    }
    
    fun copy(): Vector2D {
        return Vector2D(x, y)
    }
    
    fun dot(other: Vector2D): Float {
        return x * other.x + y * other.y
    }
    
    fun distanceTo(other: Vector2D): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }
    
    override fun toString(): String {
        return "($x, $y)"
    }
    
    // Операторы из utils.Vector2D
    operator fun plus(other: Vector2D): Vector2D {
        return Vector2D(x + other.x, y + other.y)
    }

    operator fun minus(other: Vector2D): Vector2D {
        return Vector2D(x - other.x, y - other.y)
    }

    operator fun times(scalar: Float): Vector2D {
        return Vector2D(x * scalar, y * scalar)
    }

    operator fun div(scalar: Float): Vector2D {
        return if (scalar != 0f) Vector2D(x / scalar, y / scalar) else Vector2D()
    }
    
    companion object {
        fun distance(v1: Vector2D, v2: Vector2D): Float {
            val dx = v1.x - v2.x
            val dy = v1.y - v2.y
            return sqrt(dx * dx + dy * dy)
        }
        
        fun angle(v1: Vector2D, v2: Vector2D): Float {
            return atan2(v2.y - v1.y, v2.x - v1.x)
        }
    }
} 