package com.example.biplanes.ui

import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.DecelerateInterpolator

object DialogAnimator {
    fun animateDialog(
        view: View,
        duration: Long = 300,
        interpolator: Interpolator = DecelerateInterpolator(),
        startAlpha: Float = 0f,
        endAlpha: Float = 1f,
        startY: Float = 0f,
        endY: Float = 0f,
        startScale: Float = 0f,
        endScale: Float = 1f,
    ) {
        view.alpha = startAlpha
        view.translationY = startY
        view.scaleX = startScale
        view.scaleY = startScale
        view.visibility = View.VISIBLE

        view.animate()
            .alpha(endAlpha)
            .translationY(endY)
            .scaleX(endScale)
            .scaleY(endScale)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .start()
    }

    fun hideDialog(view: View, onEnd: () -> Unit = {}) {
        view.animate()
            .alpha(0f)
            .scaleX(0f)
            .scaleY(0f)
            .setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                onEnd()
            }
            .start()
    }
}