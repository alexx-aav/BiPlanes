package com.example.biplanes

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.biplanes.menu.MultiplayerSetupActivity
import com.example.biplanes.menu.TrainingActivity

class MainActivity : AppCompatActivity() {
    private lateinit var trainingButton: Button
    private lateinit var multiplayerButton: Button
    private lateinit var exitButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        setupListeners()
    }

    private fun initViews() {
        trainingButton = findViewById(R.id.trainingButton)
        multiplayerButton = findViewById(R.id.multiplayerButton)
        exitButton = findViewById(R.id.exitButton)
    }

    private fun setupListeners() {
        trainingButton.setOnClickListener {
            startActivity(Intent(this, TrainingActivity::class.java))
        }

        multiplayerButton.setOnClickListener {
            startActivity(Intent(this, MultiplayerSetupActivity::class.java))
        }

        exitButton.setOnClickListener {
            finish()
        }
    }
}
