package com.example.biplanes.menu

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.biplanes.R
import com.example.biplanes.game.models.GameType
import com.example.biplanes.game.models.PlaneColor
import com.example.biplanes.game.ui.LobbyActivity

class MultiplayerSetupActivity : AppCompatActivity() {
    private lateinit var gameTypeRadioGroup: RadioGroup
    private lateinit var createGameButton: Button
    private lateinit var joinGameButton: Button
    private lateinit var backButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multiplayer_menu)
        initViews()
        setupListeners()
    }

    private fun initViews() {
        gameTypeRadioGroup = findViewById(R.id.gameTypeRadioGroup)
        createGameButton = findViewById(R.id.createGameButton)
        joinGameButton = findViewById(R.id.joinGameButton)
        backButton = findViewById(R.id.backButton)
    }

    private fun setupListeners() {
        createGameButton.setOnClickListener {
            val gameType = getSelectedGameType()
            startLobby(gameType, true)
        }

        joinGameButton.setOnClickListener {
            val gameType = getSelectedGameType()
            showJoinDialog(gameType)
        }

        backButton.setOnClickListener {
            finish()
        }
    }
    
    private fun getSelectedGameType(): GameType {
        return when (gameTypeRadioGroup.checkedRadioButtonId) {
            R.id.oneVsOneRadio -> GameType.ONE_VS_ONE
            R.id.twoVsTwoRadio -> GameType.TWO_VS_TWO
            R.id.freeForAllRadio -> GameType.FREE_FOR_ALL
            else -> GameType.ONE_VS_ONE // По умолчанию 1 на 1
        }
    }
    
    private fun showJoinDialog(gameType: GameType) {
        // Создаем диалог для ввода кода комнаты
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Присоединиться к игре")
        
        // Создаем поле для ввода кода
        val input = EditText(this)
        input.hint = "Введите код комнаты"
        builder.setView(input)
        
        // Добавляем кнопки
        builder.setPositiveButton("Присоединиться") { _, _ ->
            val roomCode = input.text.toString().trim()
            
            if (roomCode.isEmpty()) {
                Toast.makeText(this, "Введите код комнаты", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            
            startLobby(gameType, false, roomCode)
        }
        
        builder.setNegativeButton("Отмена") { dialog, _ ->
            dialog.cancel()
        }
        
        builder.show()
    }

    private fun startLobby(gameType: GameType, isHost: Boolean, roomCode: String = "") {
        // Выбираем случайный цвет для самолета
        val planeColor = PlaneColor.values().random()
        
        val intent = Intent(this, LobbyActivity::class.java).apply {
            putExtra("gameType", gameType)
            putExtra("isHost", isHost)
            putExtra("planeColor", planeColor)
            
            // Если не хост, то добавляем код комнаты
            if (!isHost && roomCode.isNotEmpty()) {
                putExtra("roomCode", roomCode)
            }
        }
        startActivity(intent)
    }
} 