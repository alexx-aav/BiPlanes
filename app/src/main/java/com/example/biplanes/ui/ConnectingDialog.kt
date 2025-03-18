package com.example.biplanes.ui

import android.content.Context
import android.view.View
import android.widget.TextView
import com.example.biplanes.R

class ConnectingDialog(context: Context) : BaseDialog(context) {
    private var onCancelListener: (() -> Unit)? = null

    override fun getLayoutResId() = R.layout.dialog_connecting

    override fun initViews() {
        dialogView.findViewById<View>(R.id.cancelButton)
    }

    override fun setupListeners() {
        dialogView.findViewById<View>(R.id.cancelButton).setOnClickListener {
            onCancelListener?.invoke()
            dismiss()
        }
    }

    fun setStatus(status: String) {
        dialogView.findViewById<TextView>(R.id.statusText).text = status
    }

    fun setOnCancelListener(listener: () -> Unit) {
        onCancelListener = listener
    }
} 