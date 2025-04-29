package com.example.biplanes.game.controls

import android.graphics.Canvas
import android.view.View

abstract class BaseControl(
    private val view: View
) {
    protected var centerX: Float = 0f
    protected var centerY: Float = 0f
    protected var radius: Float = 0f

    abstract fun onDraw(canvas: Canvas)

    open fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        centerX = w / 2f
        centerY = h / 2f
        radius = Math.min(w, h) / 2f
    }
}