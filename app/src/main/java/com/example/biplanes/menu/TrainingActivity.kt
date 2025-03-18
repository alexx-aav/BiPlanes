package com.example.biplanes.menu

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.biplanes.R
import com.example.biplanes.game.ui.GameActivity
import com.example.biplanes.game.models.GameType

class TrainingActivity : AppCompatActivity() {
    private lateinit var startButton: Button
    private lateinit var backButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_training)
        initViews()
        setupListeners()
    }

    private fun initViews() {
        startButton = findViewById(R.id.startButton)
        backButton = findViewById(R.id.backButton)
    }

    private fun setupListeners() {
        startButton.setOnClickListener {
            startGame()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun startGame() {
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra("gameType", GameType.TRAINING)
            putExtra("isHost", true)
        }
        startActivity(intent)
        finish()
    }
} 