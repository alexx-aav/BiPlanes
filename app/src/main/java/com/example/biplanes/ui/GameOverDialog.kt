package com.example.biplanes.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import com.example.biplanes.R

class GameOverDialog(context: Context) : BaseDialog(context) {
    private var onRematchListener: (() -> Unit)? = null
    private var onMainMenuListener: (() -> Unit)? = null

    override fun getLayoutResId() = R.layout.dialog_game_over

    override fun initViews() {
        // No need to do anything here, merged with setupListeners
    }

    override fun setupListeners() {
        val rematchButton = dialogView.findViewById<View>(R.id.rematchButton)
        rematchButton?.setOnClickListener {
            onRematchListener?.invoke()
            dismiss()
        } ?: run {
            Log.e("GameOverDialog", "Rematch button not found")
        }

        val mainMenuButton = dialogView.findViewById<View>(R.id.mainMenuButton)
        mainMenuButton?.setOnClickListener {
            onMainMenuListener?.invoke()
            dismiss()
        } ?: run {
            Log.e("GameOverDialog", "Main menu button not found")
        }
    }

    fun setWinner(winner: String) {
        dialogView.findViewById<TextView>(R.id.winnerText)?.text = "Победитель: $winner" ?: run {
            Log.e("GameOverDialog", "WinnerText not found")
        }
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