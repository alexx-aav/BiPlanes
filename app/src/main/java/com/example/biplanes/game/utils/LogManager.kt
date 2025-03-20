package com.example.biplanes.game.utils

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Класс для управления логами приложения
 */
object LogManager {
    private const val TAG = "LogManager"
    
    /**
     * Сохраняет логи игры в файл в папке Downloads
     * @param context Контекст приложения
     * @return true если логи успешно сохранены, false в противном случае
     */
    fun saveLogsToFile(context: Context): Boolean {
        try {
            // Создаем имя файла с текущей датой и временем
            val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val fileName = "biplanes_log_$timeStamp.txt"
            
            // Получаем логи из logcat
            val process = Runtime.getRuntime().exec("logcat -d")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            val logContent = bufferedReader.use { it.readText() }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Для Android 10+ используем MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                
                val uri: Uri? = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(logContent.toByteArray())
                    }
                    Log.d(TAG, "Логи успешно сохранены в Downloads: $fileName")
                    return true
                } else {
                    Log.e(TAG, "Не удалось создать файл в Downloads")
                    return false
                }
            } else {
                // Для Android 9 и ниже используем прямой доступ
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val logFile = File(downloadsDir, fileName)
                
                logFile.writeText(logContent)
                
                // Сканируем файлы чтобы они отобразились в галерее/файловом менеджере
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(logFile.absolutePath),
                    arrayOf("text/plain"),
                    null
                )
                
                Log.d(TAG, "Логи успешно сохранены в Downloads: ${logFile.absolutePath}")
                return true
            }
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка при сохранении логов: ${e.message}", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Неожиданная ошибка при сохранении логов: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Очищает все логи
     */
    fun clearLogs() {
        try {
            Runtime.getRuntime().exec("logcat -c")
            Log.d(TAG, "Логи очищены")
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка при очистке логов: ${e.message}", e)
        }
    }
} 