package com.example.biplanes.ui

import android.content.Context
import android.view.View
import android.widget.TextView
import com.example.biplanes.R

class GameOverDialog(context: Context) : BaseDialog(context) {
    private var onRematchListener: (() -> Unit)? = null
    private var onMainMenuListener: (() -> Unit)? = null

    override fun getLayoutResId() = R.layout.dialog_game_over

    override fun initViews() {
        dialogView.findViewById<View>(R.id.rematchButton)
        dialogView.findViewById<View>(R.id.mainMenuButton)
    }

    override fun setupListeners() {
        dialogView.findViewById<View>(R.id.rematchButton).setOnClickListener {
            onRematchListener?.invoke()
            dismiss()
        }

        dialogView.findViewById<View>(R.id.mainMenuButton).setOnClickListener {
            onMainMenuListener?.invoke()
            dismiss()
        }
    }

    fun setWinner(winner: String) {
        dialogView.findViewById<TextView>(R.id.winnerText).text = "Победитель: $winner"
    }

    fun setScore(score: String) {
        dialogView.findViewById<TextView>(R.id.scoreText).text = "Счет: $score"
    }

    fun setOnRematchListener(listener: () -> Unit) {
        onRematchListener = listener
    }

    fun setOnMainMenuListener(listener: () -> Unit) {
        onMainMenuListener = listener
    }
} 