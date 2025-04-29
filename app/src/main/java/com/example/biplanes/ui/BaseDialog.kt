package com.example.biplanes.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.example.biplanes.R

abstract class BaseDialog(context: Context) : Dialog(context) {
    protected lateinit var dialogView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCancelable(false)
        
        dialogView = View.inflate(context, getLayoutResId(), null)
        setContentView(dialogView)
        
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }

        initViews()
        setupListeners()
    }

    override fun show() {
        super.show()
        DialogAnimator.animateDialog(
            dialogView,
            duration = 200,
            startAlpha = 0f,
            endAlpha = 1f,
            startY = 50f,
            endY = 0f,
            startScale = 1f,
            endScale = 1f,
        )
    }

    override fun dismiss() {
        DialogAnimator.hideDialog(dialogView)
        super.dismiss()
    }

    protected abstract fun getLayoutResId(): Int
    protected abstract fun initViews()
    protected abstract fun setupListeners()
} 