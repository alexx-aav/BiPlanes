package com.example.biplanes.ui

import android.content.Context
import android.util.Log
import android.view.View
import com.example.biplanes.R

class PauseDialog(context: Context) : BaseDialog(context) {
    private var onResumeListener: (() -> Unit)? = null
    private var onRestartListener: (() -> Unit)? = null
    private var onExitListener: (() -> Unit)? = null

    override fun getLayoutResId() = R.layout.dialog_pause

    override fun initViews() {}

    override fun setupListeners() {
        val resumeButton = dialogView.findViewById<View>(R.id.resumeButton)
        resumeButton?.setOnClickListener {
            onResumeListener?.invoke()
            dismiss()
        } ?: run {
            Log.e("PauseDialog", "Resume button not found")
        }

        val restartButton = dialogView.findViewById<View>(R.id.restartButton)
        restartButton?.setOnClickListener {
            onRestartListener?.invoke()
            dismiss()
        } ?: run {
            Log.e("PauseDialog", "Restart button not found")
        }

        val exitButton = dialogView.findViewById<View>(R.id.exitButton)
        exitButton?.setOnClickListener {
            onExitListener?.invoke()
            dismiss()
        } ?: run {
            Log.e("PauseDialog", "Exit button not found")
        }
    }

    /**
     * Метод для установки слушателя на кнопку возобновления игры
     * @param listener функция, которая будет вызвана при нажатии на кнопку
     */
    fun setOnResumeListener(listener: () -> Unit) {
        onResumeListener = listener
    }

    /**
     * Метод для установки слушателя на кнопку выхода из игры
     * @param listener функция, которая будет вызвана при нажатии на кнопку
     */
    fun setOnExitListener(listener: () -> Unit) {
        onExitListener = listener
    }

    fun setOnRestartListener(listener: () -> Unit) {
        onRestartListener = listener
    }
} 