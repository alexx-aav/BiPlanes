package com.example.biplanes.game.controls

import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

abstract class BaseButton(
    view: View
) : BaseControl(view), Touchable {

    protected var isPressed: Boolean = false
    protected var isActive: Boolean = true
    private var onTouchListener: ((isPressed: Boolean) -> Unit)? = null

    override fun isPointInBounds(x: Float, y: Float): Boolean {
        if (!isActive) return false
        val distance = sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY))
        return distance <= radius
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isPointInBounds(event.x, event.y)) {
                    isPressed = true
                    onTouchListener?.invoke(isPressed)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isPressed) {
                    isPressed = false
                    onTouchListener?.invoke(isPressed)
                    reset()
                }
            }
        }
        return false
    }

    fun reset() {
        isPressed = false
    }
    
    fun activate() {
        isActive = true
    }

    fun deactivate() {
        isActive = false
    }
    
    fun setOnTouchListener(listener: (isPressed: Boolean) -> Unit) {
        onTouchListener = listener
    }
}