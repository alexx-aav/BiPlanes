package com.example.biplanes.ui

import android.view.View
import android.view.animation.DecelerateInterpolator

object DialogAnimator {
    fun showDialog(view: View) {
        view.alpha = 0f
        view.scaleX = 0f
        view.scaleY = 0f
        view.visibility = View.VISIBLE
        
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .start()
    }

    fun hideDialog(view: View) {
        view.animate()
            .alpha(0f)
            .scaleX(0f)
            .scaleY(0f)
            .setDuration(300)
            .start()
    }

    fun animateDialogItems(dialogView: View) {
        dialogView.alpha = 0f
        dialogView.translationY = 50f
        dialogView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
} 