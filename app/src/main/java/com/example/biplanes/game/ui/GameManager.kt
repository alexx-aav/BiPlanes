package com.example.biplanes.game.ui

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.example.biplanes.game.models.GameType
import com.example.biplanes.game.models.PlaneColor
import com.example.biplanes.game.models.Player
import com.example.biplanes.game.models.Vector2D
import com.example.biplanes.network.GameMessage
import com.example.biplanes.ui.EjectButton

class GameManager(
    private val gameType: GameType,
    private val isHost: Boolean,
    private val playerColor: PlaneColor,
    private val playerId: String,
    private val players: MutableList<Player>,
    private val gameView: GameView,
    private val fireButton: View,
    private val ejectButton: EjectButton,
    private val pauseOverlay: View,
    private val gameOverOverlay: View,
    private val joystick: JoystickView,
    private val gameActivityCallback: GameActivityCallback
) {

    private val TAG = "GameManager"

    private var isPaused = false
    private var isFiring = false
    private var isEjecting = false
    private var lastFireTime = 0L //
    private val fireDelay = 300L
    private var lastEjectTime = 0L //
    private val ejectDelay = 1000L
    private var lastPlaneMovementSendTime = 0L
    private val planeMovementSendDelay = 50L //ms
    private var isPlaneMovementSendEnabled = true
    private var isFireSendEnabled = true
    private var isEjectSendEnabled = true

    private val handler = Handler(Looper.getMainLooper())

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateControls()
            handler.postDelayed(this, updateInterval)
        }
    }

    init {
        setupButtons()
    }

    fun startGame() {        
        handler.post(updateRunnable)

        Log.d(
            TAG,
            "Игра запущена: gameType=$gameType, isHost=$isHost, planeColor=$playerColor, playerId=$playerId, число игроков=${players.size}"
        )

        gameActivityCallback.onGameStarted(isMultiplayer())
    }

    private fun updateGame() {
        try {
            val joystickX = joystick.getXPercent()
            val joystickY = joystick.getYPercent()

            if (joystickX != 0f || joystickY != 0f) {
                Log.d(TAG, "Джойстик активен: X=$joystickX, Y=$joystickY")
            }

            gameView.controlPlayerPlane(joystickX, joystickY, isFiring, isEjecting)

            if (System.currentTimeMillis() % 3000 < 16) {
                Log.d(
                    TAG,
                    "Джойстик: X=$joystickX, Y=$joystickY, Стрельба=$isFiring, Катапульта=$isEjecting"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в updateGame: ${e.message}")
        }
    }

    fun togglePause() {
        isPaused = !isPaused
        if (isPaused) {
            gameView.pause()
            pauseOverlay.visibility = View.VISIBLE
            handler.removeCallbacks(updateRunnable)
        } else {
            gameView.resume()
            pauseOverlay.visibility = View.GONE
            handler.post(updateRunnable)
        }
    }

    fun showGameOver() {
        try {
            Log.d(TAG, "Показываем экран окончания игры")
            gameActivityCallback.runOnUi{
                gameOverOverlay.visibility = View.VISIBLE
                gameView.setGameOver(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при показе экрана окончания игры: ${e.message}")
        }
    }

    fun restartGame() {
        try {
            Log.d(TAG, "Перезапуск игры")
            gameOverOverlay.visibility = View.GONE
            gameView.restart()
            isFiring = false
            isEjecting = false
            isPaused = false

            Log.d(TAG, "Игра перезапущена")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при перезапуске игры: ${e.message}")
        }
    }

    private fun setupButtons() {
        fireButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isFiring = true
                    Log.d(TAG, "Fire button pressed")

                    true
                }

                MotionEvent.ACTION_UP -> {
                    isFiring = false
                    Log.d(TAG, "Fire button released")
                    true
                }

                else -> false
            }
        }

        ejectButton.setOnEjectListener {
            Log.d(TAG, "Eject button pressed")

            isEjecting = true

            val playerPlane = gameView.getPlayerPlane()
            if (playerPlane != null) {
                Log.d(
                    TAG,
                    "Вызываем катапультирование напрямую для самолета на позиции (${playerPlane.position.x}, ${playerPlane.position.y})"
                )
                gameView.ejectPilot(playerPlane)
            } else {
                Log.e(TAG, "Не удалось получить самолет игрока для катапультирования")
            }

            updateGame()

            Handler(Looper.getMainLooper()).postDelayed({
                isEjecting = false
                Log.d(TAG, "Eject flag reset after delay")
            }, 500)
        }
    }

    fun updateControls() {
        if (isPaused) return
        updateGame()

        if (isMultiplayer() && isPlaneMovementSendEnabled) {
            val playerPlane = gameView.getPlayerPlane()
            if (playerPlane != null) {
                val message = GameMessage.PlaneMovement(
                    playerId = playerId,
                    position = playerPlane.position,
                    rotation = playerPlane.rotation,
                    velocity = playerPlane.velocity
                )
                try {
                    val currentTime = System.currentTimeMillis()
                    sendPlaneMovement(message,currentTime)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка отправки сообщения о движении: ${e.message}")
                }
            }
        }
        if(isMultiplayer()&& isFireSendEnabled){
            sendFire()
        }
        if (isMultiplayer()&&isEjectSendEnabled) {
                lastEjectTime = System.currentTimeMillis()

                val playerPlane = gameView.getPlayerPlane()
                if (playerPlane != null) {
                    val message = GameMessage.Eject(
                        playerId = playerId,
                        position = playerPlane.position
                    )
                    try {
                        gameActivityCallback.sendMessage(message)
                    } catch (e: Exception) {
                       Log.e(TAG, "Ошибка отправки сообщения о катапультировании: ${e.message}")
                    }
        }
    }

    private fun sendFire(){
        if (isFiring && System.currentTimeMillis() - lastFireTime > fireDelay) {
            lastFireTime = System.currentTimeMillis()

            val playerPlane = gameView.getPlayerPlane()
            if (playerPlane != null) {
                val angle = Math.toRadians(playerPlane.rotation.toDouble())
                val bulletVelocity = Vector2D(
                    Math.cos(angle).toFloat() * 15f,
                    Math.sin(angle).toFloat() * 15f
                )

                val bulletOffset = Vector2D(
                    Math.cos(angle).toFloat() * playerPlane.width / 2,
                    Math.sin(angle).toFloat() * playerPlane.width / 2
                )
                val bulletPosition = Vector2D(
                    playerPlane.position.x + bulletOffset.x,
                    playerPlane.position.y + bulletOffset.y
                )

                val message = GameMessage.Fire(
                    playerId = playerId,
                    position = bulletPosition,
                    velocity = bulletVelocity,
                    color = playerColor.color
                )
                try {
                    gameActivityCallback.sendMessage(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка отправки сообщения о выстреле: ${e.message}")
                }
            }
        }
    }

    private fun sendPlaneMovement(message: GameMessage.PlaneMovement,currentTime:Long){
        if (currentTime - lastPlaneMovementSendTime > planeMovementSendDelay) {
            gameActivityCallback.sendMessage(message)
            lastPlaneMovementSendTime = currentTime
        }
    }

    private fun sendEject(){
        val playerPlane = gameView.getPlayerPlane()
        if (playerPlane != null) {
            val message = GameMessage.Eject(playerId = playerId, position = playerPlane.position)
            gameActivityCallback.sendMessage(message)

        }
    }

    fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
    }

    fun onResume(){
        if (!isPaused) {
            gameView.resume()
            handler.post(updateRunnable)
        }
    }

    fun onPause(){
        gameView.pause()
        handler.removeCallbacks(updateRunnable)

    }

    private fun stopJoystickUpdates() {
        handler.removeCallbacks(joystickUpdateRunnable)
    }

    private fun isMultiplayer() = gameType != GameType.TRAINING
    
     companion object {
        private const val updateInterval = 16L
    }
      interface GameActivityCallback {
        fun sendMessage(message: Any)
        fun runOnUi(action: () -> Unit)
        fun onGameStarted(isMultiplayer: Boolean)
    }
}