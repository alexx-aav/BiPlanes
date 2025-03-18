package com.example.biplanes.game.ui

import android.graphics.Canvas
import android.view.SurfaceHolder
import android.util.Log

/**
 * Игровой поток, отвечающий за обновление и отрисовку игрового состояния
 * с заданной частотой кадров.
 */
class GameThread(private val surfaceHolder: SurfaceHolder, private val gameView: GameView) : Thread() {
    var running: Boolean = false
    
    // Настройки производительности
    private var targetFPS = 60
    private var targetFrameTime = (1000 / targetFPS).toLong()
    private val maxFrameSkips = 5 // Максимальное количество пропускаемых кадров
    
    // Статистика производительности
    private var frameCount = 0
    private var totalTime = 0L
    private var framesSkipped = 0
    private var framesDrawn = 0
    private var lastFpsTime = 0L
    private var currentFPS = 0
    
    private val TAG = "GameThread"

    companion object {
        private var canvas: Canvas? = null
    }
    
    /**
     * Устанавливает целевую частоту кадров
     * @param fps целевая частота кадров
     */
    fun setTargetFPS(fps: Int) {
        targetFPS = fps.coerceIn(30, 120) // Ограничиваем FPS в разумных пределах
        targetFrameTime = (1000 / targetFPS).toLong()
        Log.d(TAG, "Target FPS set to $targetFPS")
    }
    
    /**
     * Возвращает текущую частоту кадров
     * @return текущая частота кадров
     */
    fun getCurrentFPS(): Int {
        return currentFPS
    }

    override fun run() {
        Log.d(TAG, "GameThread started running")
        
        var startTime: Long
        var timeMillis: Long
        var waitTime: Long
        var frameTime: Long
        var totalSkippedFrames = 0
        
        lastFpsTime = System.currentTimeMillis()

        while (running) {
            startTime = System.nanoTime()
            canvas = null
            var framesSkippedNow = 0

            try {
                canvas = surfaceHolder.lockCanvas()
                val currentCanvas = canvas
                if (currentCanvas != null) {
                    synchronized(surfaceHolder) {
                        try {
                            // Обновляем состояние игры
                            gameView.update()
                            
                            // Отрисовываем кадр
                            gameView.draw(currentCanvas)
                            framesDrawn++
                            
                            frameCount++
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in game loop: ${e.message}", e)
                            framesSkipped++
                            totalSkippedFrames++
                        }
                    }
                } else {
                    Log.w(TAG, "Canvas is null, skipping frame")
                    framesSkipped++
                    totalSkippedFrames++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error locking canvas: ${e.message}", e)
                framesSkipped++
                totalSkippedFrames++
            } finally {
                try {
                    canvas?.let { 
                        surfaceHolder.unlockCanvasAndPost(it) 
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error unlocking canvas: ${e.message}", e)
                }
            }

            // Вычисляем, сколько времени заняла обработка кадра
            frameTime = (System.nanoTime() - startTime) / 1000000
            
            // Вычисляем, сколько времени нужно подождать до следующего кадра
            waitTime = targetFrameTime - frameTime
            
            // Если обработка заняла больше времени, чем нужно для целевого FPS,
            // то не ждем и сразу переходим к следующему кадру
            if (waitTime > 0) {
                try {
                    sleep(waitTime)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sleeping thread: ${e.message}", e)
                }
            } else {
                // Если мы не успеваем обрабатывать кадры с заданной частотой,
                // пропускаем отрисовку нескольких кадров, но продолжаем обновлять состояние
                while (waitTime < 0 && framesSkippedNow < maxFrameSkips) {
                    try {
                        // Обновляем состояние игры без отрисовки
                        gameView.update()
                        
                        // Увеличиваем счетчики пропущенных кадров
                        framesSkipped++
                        totalSkippedFrames++
                        framesSkippedNow++
                        
                        // Пересчитываем оставшееся время
                        frameTime = (System.nanoTime() - startTime) / 1000000
                        waitTime = targetFrameTime - frameTime
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating game state during frame skip: ${e.message}", e)
                        break
                    }
                }
            }

            // Считаем FPS каждую секунду
            timeMillis = System.currentTimeMillis()
            totalTime += frameTime
            
            if (timeMillis - lastFpsTime >= 1000) {
                currentFPS = (frameCount * 1000 / (timeMillis - lastFpsTime)).toInt()
                
                // Логируем статистику производительности
                if (frameCount > 0) {
                    val avgFrameTime = totalTime / frameCount
                    Log.d(TAG, "FPS: $currentFPS, Avg frame time: $avgFrameTime ms, " +
                               "Frames drawn: $framesDrawn, Frames skipped: $framesSkipped")
                }
                
                // Сбрасываем счетчики
                frameCount = 0
                framesSkipped = 0
                totalTime = 0
                lastFpsTime = timeMillis
                
                // Если слишком много пропущенных кадров, снижаем целевой FPS
                if (totalSkippedFrames > targetFPS && targetFPS > 30) {
                    val newTargetFPS = (targetFPS * 0.9).toInt().coerceAtLeast(30)
                    Log.w(TAG, "Too many skipped frames ($totalSkippedFrames), " +
                              "reducing target FPS from $targetFPS to $newTargetFPS")
                    setTargetFPS(newTargetFPS)
                    totalSkippedFrames = 0
                }
            }
        }
        
        Log.d(TAG, "GameThread stopped running")
    }
} 