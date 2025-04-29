package com.example.biplanes.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import com.example.biplanes.R

class ConnectingDialog(context: Context) : BaseDialog(context) {
    private var onCancelListener: (() -> Unit)? = null

    override fun getLayoutResId() = R.layout.dialog_connecting
    override fun initViews() {}

    override fun setupListeners() {
        val cancelButton = dialogView.findViewById<View>(R.id.cancelButton)
        cancelButton?.setOnClickListener {
            onCancelListener?.invoke() // Вызываем слушателя
            dismiss() // Закрываем диалог
        } ?: run {
            Log.e("ConnectingDialog", "Cancel button not found")
        }
    }

    fun setStatus(status: String) {
        dialogView.findViewById<TextView>(R.id.statusText)?.text = status ?: run {
            Log.e("ConnectingDialog", "Status text not found")
        }
    }

    fun setOnCancelListener(listener: () -> Unit) {
        onCancelListener = listener
    }
} 