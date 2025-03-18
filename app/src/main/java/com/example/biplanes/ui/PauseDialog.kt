package com.example.biplanes.ui

import android.content.Context
import android.view.View
import com.example.biplanes.R

class PauseDialog(context: Context) : BaseDialog(context) {
    private var onResumeListener: (() -> Unit)? = null
    private var onRestartListener: (() -> Unit)? = null
    private var onExitListener: (() -> Unit)? = null

    override fun getLayoutResId() = R.layout.dialog_pause

    override fun initViews() {
        dialogView.findViewById<View>(R.id.resumeButton)
        dialogView.findViewById<View>(R.id.restartButton)
        dialogView.findViewById<View>(R.id.exitButton)
    }

    override fun setupListeners() {
        dialogView.findViewById<View>(R.id.resumeButton).setOnClickListener {
            onResumeListener?.invoke()
            dismiss()
        }

        dialogView.findViewById<View>(R.id.restartButton).setOnClickListener {
            onRestartListener?.invoke()
            dismiss()
        }

        dialogView.findViewById<View>(R.id.exitButton).setOnClickListener {
            onExitListener?.invoke()
            dismiss()
        }
    }

    fun setOnResumeListener(listener: () -> Unit) {
        onResumeListener = listener
    }

    fun setOnRestartListener(listener: () -> Unit) {
        onRestartListener = listener
    }

    fun setOnExitListener(listener: () -> Unit) {
        onExitListener = listener
    }
} 