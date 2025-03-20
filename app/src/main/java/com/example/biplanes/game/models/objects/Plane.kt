package com.example.biplanes.game.models.objects

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import com.example.biplanes.game.models.Vector2D
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import android.graphics.Color
import kotlin.random.Random

class Plane(
    var position: Vector2D,
    val width: Float,
    val height: Float,
    val color: Int,
    val isPlayer: Boolean,
    var maxSpeed: Float
) {
    var id: String = ""
    var velocity: Vector2D = Vector2D(0f, 0f)
    var acceleration: Vector2D = Vector2D(0f, 0f)
    var rotation: Float = 0f
    var rotationVelocity: Float = 0f
    var isDestroyed: Boolean = false
    var hasPilot: Boolean = true
    var health = 100
    var pilot: Pilot? = null
        private set
    
    // Добавляем переменную для режима тренировки
    private var isTrainingMode = false
    
    // Физические константы
    private val gravity = Vector2D(0f, 0.01f)       // Значительно уменьшенная гравитация для стабильного полета
    private val dragCoefficient = 0.002f            // Минимальное сопротивление воздуха
    private var liftCoefficient = 1.0f              // Значительно увеличенная подъемная сила
    private var enginePower = 3.0f                  // Увеличенная мощность двигателя
    private var rotationSpeed = 1.8f                // Уменьшенная скорость поворота для более плавного управления (было 2.5f)
    private val minSpeed = 5.0f                     // Минимальная скорость для полета
    private val turnFactor = 0.12f                  // Уменьшенный коэффициент поворота (было 0.15f)
    
    // Добавляем новые константы для физики падения
    private val fallGravity = Vector2D(0f, 0.015f)  // Увеличенная гравитация для падения
    private val fallDragCoefficient = 0.003f        // Увеличенное сопротивление воздуха при падении
    private val maxFallSpeed = 15.0f                // Максимальная скорость падения
    private val rotationDamping = 0.98f             // Затухание вращения
    private val targetFallAngle = 45f               // Целевой угол падения
    private val angleChangeSpeed = 0.5f             // Скорость изменения угла к целевому
    
    private val TAG = "Plane"
    private val planePath = Path()
    private val wingPath = Path()
    private val tailPath = Path()
    private val propellerPath = Path()
    private var propellerAngle = 0f
    private var engineThrottle = 0.9f               // Устанавливаем начальный газ на оптимальное значение
    private var smokeTimer = 0f                     // Таймер для эффекта дыма
    
    // Переменные для отслеживания времени вне экрана
    var isOutOfScreen = false
    var outOfScreenTime = 0f
    val maxOutOfScreenTime = 3.0f  // Максимальное время вне экрана в секундах

    // Добавляем переменные для управления физикой полета
    private var ignoreLift = false
    private var autopilotEnabled = true
    
    init {
        Log.d(TAG, "Plane created at position (${position.x}, ${position.y}), width: $width, height: $height, color: $color")
        
        // Убираем поднятие самолета вверх при инициализации
        // position.y -= 300f  // Поднимаем самолет на 300 пикселей выше
        
        // Инициализируем начальную скорость для стабильного полета
        velocity = Vector2D(minSpeed * 1.5f, -0.5f)  // Горизонтальная скорость с небольшим подъемом
        
        // Устанавливаем начальный газ на оптимальное значение
        engineThrottle = 0.9f
        
        // Устанавливаем начальное ускорение для стабильного полета
        acceleration = Vector2D(0.1f, -0.05f)  // Небольшое ускорение вперед и вверх
        
        // Устанавливаем начальный угол для небольшого подъема
        rotation = -5f  // Небольшой угол вверх для начального набора высоты
    }

    fun assignPilot(newPilot: Pilot) {
        pilot = newPilot
        newPilot.plane = this
        Log.d(TAG, "Pilot assigned to plane")
    }

    /**
     * Возвращает пилота самолета
     * @return пилот самолета или null, если пилота нет
     */
    fun getPilotObject(): Pilot? {
        return pilot
    }

    /**
     * Катапультирует пилота из самолета
     */
    fun ejectPilot() {
        pilot = null
        hasPilot = false
        // После катапультирования самолет продолжает движение по инерции
        // Добавляем небольшое случайное вращение для реализма
        rotationVelocity = (Random.nextFloat() - 0.5f) * 0.5f
        // Снижаем тягу двигателя постепенно
        engineThrottle = 0.3f
        Log.d(TAG, "Пилот катапультировался, самолет продолжает движение по инерции")
    }

    /**
     * Обновляет состояние самолета
     * @param deltaTime время, прошедшее с последнего обновления
     */
    fun update(deltaTime: Float) {
        try {
            // Обновляем угол пропеллера
            propellerAngle = (propellerAngle + 15f) % 360f
            
            if (isDestroyed) {
                // Если самолет уничтожен, просто применяем гравитацию и сопротивление воздуха
                velocity.add(Vector2D(0f, gravity.y * 2f))
                velocity.multiply(0.99f)
                position = position.add(Vector2D(velocity.x * deltaTime * 60f, velocity.y * deltaTime * 60f))
                
                // Добавляем вращение для уничтоженного самолета
                rotation += 2f * deltaTime * 60f
                return
            }
            
            // Если в самолете нет пилота, он должен падать
            if (!hasPilot) {
                // Сохраняем текущую горизонтальную скорость
                val currentHorizontalSpeed = velocity.x
                
                // Применяем увеличенную гравитацию для падения
                velocity.add(Vector2D(0f, fallGravity.y))
                
                // Вычисляем текущую скорость
                val currentSpeed = Math.sqrt((velocity.x * velocity.x + velocity.y * velocity.y).toDouble()).toFloat()
                
                // Ограничиваем максимальную скорость падения
                if (currentSpeed > maxFallSpeed) {
                    velocity.multiply(maxFallSpeed / currentSpeed)
                }
                
                // Постепенно замедляем горизонтальную скорость с учетом сопротивления воздуха
                velocity.x *= (1f - fallDragCoefficient * deltaTime * 60f)
                
                // Плавно меняем угол самолета к целевому углу падения
                val targetAngle = if (velocity.x > 0) targetFallAngle else -targetFallAngle
                val angleDiff = targetAngle - rotation
                rotation += angleDiff * angleChangeSpeed * deltaTime * 60f
                
                // Добавляем вращение с затуханием
                rotation += rotationVelocity * deltaTime * 60f
                rotationVelocity *= rotationDamping
                
                // Обновляем позицию
                position = position.add(Vector2D(velocity.x * deltaTime * 60f, velocity.y * deltaTime * 60f))
                return
            }
            
            // Вычисляем направление движения самолета
            val direction = Vector2D(
                Math.cos(Math.toRadians(rotation.toDouble())).toFloat(),
                Math.sin(Math.toRadians(rotation.toDouble())).toFloat()
            )
            
            // Применяем тягу двигателя - увеличиваем мощность тяги пропорционально газу
            val thrustMagnitude = enginePower * engineThrottle * 1.5f  // Увеличиваем множитель для более заметного эффекта
            val thrust = Vector2D(
                direction.x * thrustMagnitude,
                direction.y * thrustMagnitude
            )
            
            // Применяем силы
            applyForce(thrust)
            
            // Применяем гравитацию
            applyForce(gravity)
            
            // Вычисляем текущую скорость для логирования
            val currentSpeed = Math.sqrt((velocity.x * velocity.x + velocity.y * velocity.y).toDouble()).toFloat()
            Log.d(TAG, "Текущая скорость самолета: $currentSpeed, газ: $engineThrottle, thrust: $thrustMagnitude")
            
            // Вычисляем подъемную силу (перпендикулярно направлению движения)
            val speed = Math.sqrt((velocity.x * velocity.x + velocity.y * velocity.y).toDouble()).toFloat()
            
            // Подъемная сила зависит от скорости и угла атаки
            // Для режима тренировки увеличиваем подъемную силу
            val liftMultiplier = if (isTrainingMode) 1.5f else 1.0f
            
            // Вычисляем подъемную силу перпендикулярно направлению движения
            val lift = Vector2D(
                -direction.y * speed * liftCoefficient * liftMultiplier,
                direction.x * speed * liftCoefficient * liftMultiplier
            )
            
            // Применяем подъемную силу
            applyForce(lift)
            
            // Применяем сопротивление воздуха (зависит от скорости)
            val drag = Vector2D(
                -velocity.x * dragCoefficient * speed,
                -velocity.y * dragCoefficient * speed
            )
            
            // Применяем сопротивление воздуха
            applyForce(drag)
            
            // Обновляем скорость на основе ускорения
            velocity.add(Vector2D(acceleration.x * deltaTime * 60f, acceleration.y * deltaTime * 60f))
            
            // Сбрасываем ускорение
            acceleration.x = 0f
            acceleration.y = 0f
            
            // Ограничиваем скорость
            if (speed > maxSpeed) {
                velocity.multiply(maxSpeed / speed)
            }
            
            // Добавляем минимальную скорость для предотвращения падения в режиме тренировки
            if (isTrainingMode && speed < minSpeed) {
                velocity.multiply(minSpeed / speed)
            }
            
            // Обновляем позицию
            position = position.add(Vector2D(velocity.x * deltaTime * 60f, velocity.y * deltaTime * 60f))
            
            // Обновляем таймер дыма
            smokeTimer += deltaTime
            
            // В режиме тренировки добавляем небольшую стабилизацию для более простого управления
            if (isTrainingMode) {
                // Если самолет начинает падать слишком быстро, добавляем подъемную силу
                if (velocity.y > 3.0f) {
                    velocity.y *= 0.95f
                }
                
                // Если самолет летит слишком медленно, добавляем небольшое ускорение
                if (speed < minSpeed * 1.2f) {
                    velocity.add(Vector2D(direction.x * 0.1f, direction.y * 0.1f))
                }
            }
            
            // Добавляем начальную скорость, если самолет стоит на месте
            if (speed < 0.1f) {
                velocity.x = direction.x * minSpeed * 1.5f
                velocity.y = direction.y * minSpeed * 1.5f
                Log.d(TAG, "Самолет стоял на месте, добавлена начальная скорость: $velocity")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в методе update: ${e.message}")
            e.printStackTrace()
        }
    }

    fun applyForce(force: Vector2D) {
        // Исправляем добавление силы к ускорению через переприсваивание
        acceleration = acceleration.add(force)
    }
    
    // Метод для применения крутящего момента к самолету
    private fun applyTorque(torque: Float) {
        // Изменяем угол поворота самолета
        rotation += torque * rotationSpeed
        
        // Нормализуем угол в диапазоне [0, 360)
        while (rotation < 0) rotation += 360f
        while (rotation >= 360) rotation -= 360f
    }

    /**
     * Управляет самолетом с помощью джойстика
     * @param joystickX горизонтальное отклонение джойстика (-1..1)
     * @param joystickY вертикальное отклонение джойстика (-1..1)
     */
    fun steer(joystickX: Float, joystickY: Float) {
        // Если в самолете нет пилота, им нельзя управлять
        if (!hasPilot) {
            return
        }
        
        try {
            // Проверяем силу отклонения джойстика
            val joystickMagnitude = Math.sqrt((joystickX * joystickX + joystickY * joystickY).toDouble()).toFloat()
            
            // Вычисляем текущую скорость
            val currentSpeed = Math.sqrt((velocity.x * velocity.x + velocity.y * velocity.y).toDouble()).toFloat()
            
            // ПРЯМОЕ УПРАВЛЕНИЕ СКОРОСТЬЮ: чем дальше отклонен джойстик, тем выше скорость
            // Если джойстик в центре, снижаем скорость до минимальной
            if (joystickMagnitude < 0.1f) {
                // При нейтральном положении джойстика снижаем скорость до минимальной
                engineThrottle = 0.3f  // Минимальный газ
            } else {
                // Устанавливаем газ прямо пропорционально отклонению джойстика
                // При максимальном отклонении - максимальный газ
                engineThrottle = 0.3f + joystickMagnitude * 0.7f
            }
            
            // Определяем целевую скорость на основе газа
            val targetSpeed = minSpeed + (maxSpeed - minSpeed) * engineThrottle
            
            // Целевое направление зависит от джойстика
            if (joystickMagnitude > 0.1f) {
                // Вычисляем целевой угол на основе джойстика
                var targetAngle = Math.toDegrees(Math.atan2(joystickY.toDouble(), joystickX.toDouble())).toFloat()
                while (targetAngle < 0) targetAngle += 360f
                
                // Находим кратчайший путь поворота
                var angleDiff = targetAngle - rotation
                while (angleDiff > 180) angleDiff -= 360
                while (angleDiff < -180) angleDiff += 360
                
                // Плавно поворачиваем к целевому углу
                val turnAmount = (angleDiff * 0.1f).coerceIn(-rotationSpeed, rotationSpeed)
                rotation += turnAmount
            }
            
            // Нормализуем угол
            while (rotation < 0) rotation += 360f
            while (rotation >= 360) rotation -= 360f
            
            // Вычисляем направление движения на основе текущего угла
            val directionRad = Math.toRadians(rotation.toDouble())
            val direction = Vector2D(
                Math.cos(directionRad).toFloat(),
                Math.sin(directionRad).toFloat()
            )
            
            // КРИТИЧЕСКИЙ МОМЕНТ: устанавливаем скорость напрямую в зависимости от направления и газа
            val accelerationFactor = 0.03f  // Уменьшаем с 0.05f до 0.03f для более плавного разгона
            
            // Целевой вектор скорости
            val targetVelocity = Vector2D(
                direction.x * targetSpeed,
                direction.y * targetSpeed
            )
            
            // Плавно меняем текущую скорость в сторону целевой
            velocity.x += (targetVelocity.x - velocity.x) * accelerationFactor * 5f
            velocity.y += (targetVelocity.y - velocity.y) * accelerationFactor * 5f
            
            // Добавляем небольшую гравитацию для реализма
            velocity.y += 0.01f
            
            // Применяем сопротивление воздуха для стабильности
            val dragFactor = 0.99f
            velocity = velocity.multiply(dragFactor)
            
            // Логируем подробную информацию для отладки
            Log.d(TAG, "УПРАВЛЕНИЕ: отклонение=$joystickMagnitude, газ=$engineThrottle, " +
                     "целевая скорость=$targetSpeed, фактическая скорость=$currentSpeed")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в методе steer: ${e.message}")
            e.printStackTrace()
        }
    }

    fun draw(canvas: Canvas, paint: Paint) {
        if (isDestroyed) return
        
        try {
            // Сохраняем текущее состояние канваса
            canvas.save()
            
            // Перемещаем канвас в позицию самолета
            canvas.translate(position.x, position.y)
            
            // Поворачиваем канвас на угол поворота самолета
            canvas.rotate(rotation)
            
            // Рисуем дым от двигателя при высокой мощности
            if (engineThrottle > 0.7f) {
                drawEngineSmoke(canvas, paint)
            }
            
            // Рисуем самолет
            drawBiplane(canvas, paint)
            
            // Восстанавливаем состояние канваса
            canvas.restore()
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing plane: ${e.message}", e)
        }
    }
    
    private fun drawEngineSmoke(canvas: Canvas, paint: Paint) {
        // Сохраняем настройки краски
        val originalColor = paint.color
        val originalStyle = paint.style
        val originalAlpha = paint.alpha
        
        // Настраиваем краску для дыма
        paint.style = Paint.Style.FILL
        paint.color = 0xFF888888.toInt() // Серый цвет для дыма
        
        // Рисуем несколько клубов дыма
        for (i in 0 until 3) {
            val offset = i * 10f
            val size = 5f + i * 3f
            val alpha = (255 * (1f - i / 3f) * 0.7f).toInt()
            paint.alpha = alpha
            
            val smokeX = -width/2 - offset - 10f
            val smokeY = abs(kotlin.math.sin((smokeTimer + i) * 2f)) * 5f
            
            canvas.drawCircle(smokeX, smokeY, size, paint)
        }
        
        // Восстанавливаем настройки краски
        paint.color = originalColor
        paint.style = originalStyle
        paint.alpha = originalAlpha
    }
    
    private fun drawBiplane(canvas: Canvas, paint: Paint) {
        val w = width
        val h = height
        
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
        canvas.rotate(propellerAngle)
        
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

    fun checkCollision(bullet: Bullet): Boolean {
        if (isDestroyed || bullet.isDestroyed) return false
        
        // Простая проверка столкновения на основе расстояния
        val distance = Vector2D.distance(position, bullet.position)
        return distance < (width + bullet.radius) / 2
    }

    // Метод для установки режима тренировки
    fun setTrainingMode(isTraining: Boolean) {
        isTrainingMode = isTraining
        // В режиме тренировки увеличиваем здоровье самолета
        if (isTraining) {
            health = 200
            
            // Устанавливаем более стабильные физические параметры для режима тренировки
            // Уменьшаем гравитацию для более легкого управления
            gravity.y = 0.005f
            
            // Увеличиваем подъемную силу
            liftCoefficient = 1.5f
            
            // Увеличиваем мощность двигателя
            enginePower = 4.0f
            
            // Уменьшаем скорость поворота для более плавного управления
            rotationSpeed = 2.0f
            
            // Устанавливаем начальную скорость для стабильного полета
            velocity = Vector2D(10.0f, 0f)
            
            // Устанавливаем начальный газ на оптимальное значение
            engineThrottle = 0.95f
            
            Log.d(TAG, "Training mode enabled with enhanced flight parameters")
        }
    }

    fun takeDamage(damage: Int) {
        // В режиме тренировки уменьшаем получаемый урон
        val actualDamage = if (isTrainingMode) damage / 2 else damage
        health -= actualDamage
        Log.d(TAG, "Plane took $actualDamage damage, health: $health")
        
        if (health <= 0) {
            isDestroyed = true
            ejectPilot()
            Log.d(TAG, "Plane destroyed")
        } else if (isTrainingMode && health < 50) {
            // В режиме тренировки при низком здоровье самолет начинает дымиться
            // Это визуальный индикатор повреждения
            Log.d(TAG, "Plane damaged, health critical: $health")
        }
    }

    // Метод для проверки, находится ли самолет за пределами экрана
    fun checkOutOfBounds(screenWidth: Float, screenHeight: Float, deltaTime: Float): Boolean {
        // Если самолет вылетает за верхнюю или нижнюю границу экрана
        val isOutOfScreenVertically = position.y < -height || position.y > screenHeight + height
        
        // Если самолет вылетает за левую или правую границу экрана, перемещаем его на противоположную сторону
        if (position.x < -width) {
            position.x = screenWidth + width / 2
        } else if (position.x > screenWidth + width) {
            position.x = -width / 2
        }
        
        // Если самолет был за пределами экрана по вертикали
        if (isOutOfScreenVertically) {
            if (!isOutOfScreen) {
                // Только что вышел за пределы
                isOutOfScreen = true
                outOfScreenTime = 0f
            } else {
                // Уже был за пределами, увеличиваем счетчик
                outOfScreenTime += deltaTime
            }
        } else {
            // Самолет в пределах экрана
            isOutOfScreen = false
            outOfScreenTime = 0f
        }
        
        // Возвращаем true, если самолет был за пределами экрана по вертикали слишком долго
        return isOutOfScreen && outOfScreenTime >= maxOutOfScreenTime
    }

    fun damage(amount: Int) {
        health -= amount
        if (health <= 0) {
            destroy()
        }
    }
    
    fun destroy() {
        isDestroyed = true
    }

    /**
     * Устанавливает, нужно ли игнорировать подъемную силу для самолета
     * @param ignore true, если нужно игнорировать подъемную силу
     */
    fun setIgnoreLift(ignore: Boolean) {
        this.ignoreLift = ignore
    }
    
    /**
     * Проверяет, игнорируется ли подъемная сила для самолета
     * @return true, если подъемная сила игнорируется
     */
    fun isLiftIgnored(): Boolean {
        return ignoreLift
    }
    
    /**
     * Включает или выключает автопилот для самолета
     * @param enabled true, если автопилот включен
     */
    fun setAutopilotEnabled(enabled: Boolean) {
        this.autopilotEnabled = enabled
    }
    
    /**
     * Проверяет, включен ли автопилот для самолета
     * @return true, если автопилот включен
     */
    fun isAutopilotEnabled(): Boolean {
        return autopilotEnabled
    }
}