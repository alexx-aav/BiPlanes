package com.example.biplanes.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * Вид для отображения анимированного самолета в меню
 */
class MenuPlaneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val planePath = Path()
    private val wingPath = Path()
    private val tailPath = Path()
    private val propellerPath = Path()
    private val cloudPath = Path()
    
    private var planeColor = Color.BLUE
    private var secondPlaneColor = Color.RED
    private var propellerAngle = 0f
    private var secondPropellerAngle = 0f
    private var planeX = 0f
    private var planeY = 0f
    private var secondPlaneX = 0f
    private var secondPlaneY = 0f
    private var planeScale = 1f
    private var animationTime = 0f
    
    // Облака
    private val clouds = mutableListOf<Cloud>()
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 200
        style = Paint.Style.FILL
    }
    
    // Фон
    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(34, 139, 34) // Зеленая земля
    }
    private var groundHeight = 0f
    
    // Класс для хранения данных об облаке
    private data class Cloud(
        var x: Float,
        var y: Float,
        val size: Float,
        var speed: Float
    )
    
    init {
        // Запускаем анимацию
        post(object : Runnable {
            override fun run() {
                updateAnimation()
                invalidate()
                postDelayed(this, 16) // ~60 FPS
            }
        })
    }
    
    /**
     * Устанавливает цвет самолета
     */
    fun setPlaneColor(color: Int) {
        planeColor = color
        invalidate()
    }
    
    /**
     * Обновляет анимацию самолета
     */
    private fun updateAnimation() {
        animationTime += 0.016f // Увеличиваем время анимации
        
        // Обновляем позицию первого самолета (плавное движение по синусоиде)
        planeX = width * 0.7f + sin(animationTime * 0.5f) * width * 0.1f
        planeY = height * 0.3f + sin(animationTime) * height * 0.05f
        
        // Обновляем позицию второго самолета (движение в противоположном направлении)
        secondPlaneX = width * 0.3f - sin(animationTime * 0.7f) * width * 0.15f
        secondPlaneY = height * 0.7f - sin(animationTime * 1.2f) * height * 0.08f
        
        // Вращаем пропеллеры
        propellerAngle = (propellerAngle + 15f) % 360f
        secondPropellerAngle = (secondPropellerAngle + 18f) % 360f
        
        // Обновляем облака
        updateClouds()
    }
    
    /**
     * Обновляет позиции облаков
     */
    private fun updateClouds() {
        // Перемещаем облака
        val cloudsToRemove = mutableListOf<Cloud>()
        for (cloud in clouds) {
            cloud.x += cloud.speed
            if (cloud.x > width + cloud.size) {
                cloudsToRemove.add(cloud)
            }
        }
        clouds.removeAll(cloudsToRemove)
        
        // Добавляем новые облака, если их мало
        if (clouds.size < 5 && Random.nextFloat() < 0.02f) {
            val size = 50f + Random.nextFloat() * 100f
            val y = Random.nextFloat() * (height * 0.7f)
            val speed = 1f + Random.nextFloat() * 2f
            clouds.add(Cloud(-size, y, size, speed))
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Инициализируем фон при изменении размера
        groundHeight = h * 0.85f
        
        // Настраиваем градиент для неба
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, groundHeight,
            Color.rgb(135, 206, 250), // Светло-голубой
            Color.rgb(135, 206, 235), // Голубой
            Shader.TileMode.CLAMP
        )
        
        // Создаем начальные облака
        clouds.clear()
        for (i in 0 until 5) {
            val size = 50f + Random.nextFloat() * 100f
            val x = Random.nextFloat() * w
            val y = Random.nextFloat() * (h * 0.7f)
            val speed = 1f + Random.nextFloat() * 2f
            clouds.add(Cloud(x, y, size, speed))
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Если размеры еще не определены, выходим
        if (width == 0 || height == 0) return
        
        // Рисуем фон
        drawBackground(canvas)
        
        // Вычисляем масштаб самолета в зависимости от размера экрана
        planeScale = width * 0.0015f
        
        // Рисуем первый самолет
        canvas.save()
        canvas.translate(planeX, planeY)
        canvas.scale(planeScale, planeScale)
        drawBiplane(canvas, planeColor, propellerAngle)
        canvas.restore()
        
        // Рисуем второй самолет
        canvas.save()
        canvas.translate(secondPlaneX, secondPlaneY)
        canvas.scale(planeScale * 0.8f, planeScale * 0.8f) // Немного меньше
        // Не переворачиваем самолет, а просто меняем направление движения
        drawBiplane(canvas, secondPlaneColor, secondPropellerAngle)
        canvas.restore()
    }
    
    /**
     * Рисует фон (небо, облака, землю)
     */
    private fun drawBackground(canvas: Canvas) {
        // Рисуем небо
        canvas.drawRect(0f, 0f, width.toFloat(), groundHeight, skyPaint)
        
        // Рисуем облака
        for (cloud in clouds) {
            cloudPath.reset()
            cloudPath.addCircle(cloud.x, cloud.y, cloud.size, Path.Direction.CW)
            cloudPath.addCircle(cloud.x + cloud.size * 0.7f, cloud.y - cloud.size * 0.3f, cloud.size * 0.7f, Path.Direction.CW)
            cloudPath.addCircle(cloud.x + cloud.size * 0.7f, cloud.y + cloud.size * 0.3f, cloud.size * 0.7f, Path.Direction.CW)
            cloudPath.addCircle(cloud.x + cloud.size * 1.4f, cloud.y, cloud.size * 0.8f, Path.Direction.CW)
            canvas.drawPath(cloudPath, cloudPaint)
        }
        
        // Рисуем землю
        canvas.drawRect(0f, groundHeight, width.toFloat(), height.toFloat(), groundPaint)
    }
    
    /**
     * Рисует биплан
     */
    private fun drawBiplane(canvas: Canvas, color: Int, propAngle: Float) {
        val w = 100f
        val h = 60f
        
        // Сохраняем оригинальные настройки краски
        val originalColor = paint.color
        val originalStyle = paint.style
        val originalStrokeWidth = paint.strokeWidth
        
        // Рисуем фюзеляж (вид сбоку)
        paint.color = color
        paint.style = Paint.Style.FILL
        
        // Основной фюзеляж - более обтекаемая форма
        planePath.reset()
        planePath.moveTo(-w/2, 0f)                // Задняя часть фюзеляжа
        planePath.quadTo(-w/2 + w/10, -h/5, -w/3, -h/5)  // Верхняя кривая задней части
        planePath.lineTo(w/4, -h/5)               // Верх фюзеляжа
        planePath.quadTo(w/2 - w/10, -h/10, w/2, 0f)     // Обтекаемый нос
        planePath.quadTo(w/2 - w/10, h/10, w/4, h/5)     // Нижняя часть носа
        planePath.lineTo(-w/3, h/5)               // Низ фюзеляжа
        planePath.quadTo(-w/2 + w/10, h/5, -w/2, 0f)     // Нижняя кривая задней части
        planePath.close()
        canvas.drawPath(planePath, paint)
        
        // Добавляем обводку для фюзеляжа
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w/50
        paint.color = darkenColor(color)
        canvas.drawPath(planePath, paint)
        
        // Рисуем верхнее крыло (вид сбоку) с изогнутым профилем
        paint.style = Paint.Style.FILL
        paint.color = color
        
        wingPath.reset()
        wingPath.moveTo(-w/4, -h/5)               // Соединение с фюзеляжем
        wingPath.quadTo(-w/4, -h/2.2f, -w/5, -h/2)      // Изогнутая задняя кромка
        wingPath.lineTo(w/5, -h/2)                // Верхняя линия крыла
        wingPath.quadTo(w/4, -h/2.2f, w/4, -h/5)        // Изогнутая передняя кромка
        wingPath.close()
        canvas.drawPath(wingPath, paint)
        
        // Обводка верхнего крыла
        paint.style = Paint.Style.STROKE
        paint.color = darkenColor(color)
        canvas.drawPath(wingPath, paint)
        
        // Рисуем нижнее крыло (вид сбоку) с изогнутым профилем
        paint.style = Paint.Style.FILL
        paint.color = color
        
        wingPath.reset()
        wingPath.moveTo(-w/4, h/5)                // Соединение с фюзеляжем
        wingPath.quadTo(-w/4, h/2.2f, -w/5, h/2)        // Изогнутая задняя кромка
        wingPath.lineTo(w/5, h/2)                 // Нижняя линия крыла
        wingPath.quadTo(w/4, h/2.2f, w/4, h/5)          // Изогнутая передняя кромка
        wingPath.close()
        canvas.drawPath(wingPath, paint)
        
        // Обводка нижнего крыла
        paint.style = Paint.Style.STROKE
        paint.color = darkenColor(color)
        canvas.drawPath(wingPath, paint)
        
        // Рисуем хвостовое оперение (вид сбоку)
        paint.style = Paint.Style.FILL
        paint.color = color
        
        // Горизонтальный стабилизатор
        tailPath.reset()
        tailPath.moveTo(-w/2, -h/10)              // Верхнее соединение с фюзеляжем
        tailPath.lineTo(-w/2 - w/5, -h/10)        // Задняя кромка
        tailPath.lineTo(-w/2 - w/5, -h/20)        // Задний край
        tailPath.lineTo(-w/2, -h/20)              // Нижнее соединение с фюзеляжем
        tailPath.close()
        canvas.drawPath(tailPath, paint)
        
        // Обводка горизонтального стабилизатора
        paint.style = Paint.Style.STROKE
        paint.color = darkenColor(color)
        canvas.drawPath(tailPath, paint)
        
        // Вертикальный стабилизатор
        tailPath.reset()
        tailPath.moveTo(-w/2, -h/10)              // Нижнее соединение с фюзеляжем
        tailPath.lineTo(-w/2 - w/8, -h/3)         // Верхняя точка
        tailPath.lineTo(-w/2 - w/6, -h/3)         // Задняя верхняя точка
        tailPath.lineTo(-w/2, -h/20)              // Заднее соединение с фюзеляжем
        tailPath.close()
        canvas.drawPath(tailPath, paint)
        
        // Обводка вертикального стабилизатора
        paint.style = Paint.Style.STROKE
        paint.color = darkenColor(color)
        canvas.drawPath(tailPath, paint)
        
        // Рисуем двигатель и пропеллер
        // Двигатель (круглый)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF444444.toInt()  // Темно-серый цвет для двигателя
        canvas.drawCircle(w/2 - w/20, 0f, w/12, paint)
        
        // Обводка двигателя
        paint.style = Paint.Style.STROKE
        paint.color = 0xFF222222.toInt()  // Почти черный для обводки
        canvas.drawCircle(w/2 - w/20, 0f, w/12, paint)
        
        // Пропеллер
        canvas.save()
        canvas.translate(w/2, 0f)           // Перемещаем к носу самолета
        canvas.rotate(propAngle)
        
        propellerPath.reset()
        // Более реалистичная форма пропеллера
        paint.style = Paint.Style.FILL
        paint.color = 0xFF8B4513.toInt()    // Коричневый цвет для пропеллера
        
        // Первая лопасть
        propellerPath.moveTo(0f, -h/2.5f)
        propellerPath.quadTo(w/30, -h/5, 0f, 0f)
        propellerPath.quadTo(-w/30, -h/5, 0f, -h/2.5f)
        propellerPath.close()
        
        // Вторая лопасть
        propellerPath.moveTo(0f, h/2.5f)
        propellerPath.quadTo(w/30, h/5, 0f, 0f)
        propellerPath.quadTo(-w/30, h/5, 0f, h/2.5f)
        propellerPath.close()
        
        canvas.drawPath(propellerPath, paint)
        
        // Обводка пропеллера
        paint.style = Paint.Style.STROKE
        paint.color = 0xFF5D4037.toInt()    // Темно-коричневый для обводки
        paint.strokeWidth = w/100
        canvas.drawPath(propellerPath, paint)
        
        // Центр пропеллера (ступица)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFD7CCC8.toInt()    // Светло-коричневый для ступицы
        canvas.drawCircle(0f, 0f, w/25, paint)
        
        canvas.restore()
        
        // Рисуем кабину пилота (вид сбоку)
        // Основа кабины
        paint.style = Paint.Style.FILL
        paint.color = 0xFF87CEEB.toInt()    // Голубой цвет для стекла кабины
        
        // Более детализированная кабина
        val cabinPath = Path()
        cabinPath.moveTo(-w/10, -h/5)
        cabinPath.quadTo(0f, -h/3, w/10, -h/5)
        cabinPath.lineTo(w/10, -h/10)
        cabinPath.lineTo(-w/10, -h/10)
        cabinPath.close()
        canvas.drawPath(cabinPath, paint)
        
        // Обводка кабины
        paint.style = Paint.Style.STROKE
        paint.color = 0xFF5D4037.toInt()    // Темно-коричневый для обводки
        paint.strokeWidth = w/80
        canvas.drawPath(cabinPath, paint)
        
        // Рисуем стойки между крыльями (вид сбоку)
        paint.color = 0xFF8B4513.toInt()    // Коричневый цвет для стоек
        paint.strokeWidth = w/40
        canvas.drawLine(-w/6, -h/5, -w/6, h/5, paint)  // Задняя стойка
        canvas.drawLine(w/6, -h/5, w/6, h/5, paint)    // Передняя стойка
        
        // Добавляем детали - растяжки между крыльями
        paint.strokeWidth = w/100
        paint.color = 0xFF5D4037.toInt()    // Темно-коричневый для растяжек
        
        // Диагональные растяжки
        canvas.drawLine(-w/6, -h/5, w/6, h/5, paint)
        canvas.drawLine(-w/6, h/5, w/6, -h/5, paint)
        
        // Восстанавливаем настройки краски
        paint.color = originalColor
        paint.style = originalStyle
        paint.strokeWidth = originalStrokeWidth
    }
    
    // Вспомогательный метод для затемнения цвета (для обводки)
    private fun darkenColor(color: Int): Int {
        val factor = 0.7f
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt()
        val g = (Color.green(color) * factor).toInt()
        val b = (Color.blue(color) * factor).toInt()
        return Color.argb(a, r, g, b)
    }
} 