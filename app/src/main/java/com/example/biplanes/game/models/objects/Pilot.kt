package com.example.biplanes.game.models.objects

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.example.biplanes.game.models.Vector2D
import android.util.Log
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.random.Random
import android.graphics.Color

/**
 * Класс, представляющий пилота самолета с реалистичной физикой и анимацией.
 */
class Pilot(
    val color: Int,
    val size: Float = 20f
) {
    // Связь с самолетом
    var plane: Plane? = null
    
    // Добавляем ссылку на дом
    var house: House? = null
    
    // Физические параметры
    var position = Vector2D(0f, 0f)
    var velocity = Vector2D(0f, 0f)
    var rotation = 0f
    var rotationVelocity = 0f
    
    // Состояния пилота
    var isEjected = false
    var isRescued = false
    var parachuteOpened = false
    var parachuteDeploying = false
    var parachuteFullyDeployed = false
    var isOnGround = false
    var state = State.IN_PLANE
    
    // Таймеры и счетчики
    var parachuteOpenTime = 0f
    var runningAnimationTime = 0f
    var ejectionTime = 0f
    var groundContactTime = 0f
    var parachuteDeploymentProgress = 0f
    
    // Физические константы
    private val gravityValue = 2.0f
    private val gravity = Vector2D(0f, gravityValue) 
    private val airResistance = 0.01f
    private val parachuteResistance = 0.1f
    private val parachuteDeployTime = 0.3f  // Уменьшаем время раскрытия парашюта (было 0.5f)
    private val parachuteOpenDelay = 0.1f   // Сильно уменьшаем задержку открытия парашюта (было 0.3f)
    private val runningSpeed = 1.0f
    private val runningAnimationSpeed = 8.0f
    private val maxFallingSpeed = 20.0f
    private val maxParachuteSpeed = 3.0f
    private val groundRecoveryTime = 0.5f // Время на восстановление после приземления
    
    // Константы для анимации
    private val limbSwingFactor = 0.8f
    private val parachuteWidth = size * 5f
    private val parachuteHeight = size * 3f
    private val parachuteLines = 6
    
    // Тэг для логирования
    private val TAG = "Pilot"
    
    /**
     * Состояния пилота
     */
    enum class State {
        IN_PLANE,        // В самолете
        EJECTING,        // В процессе катапультирования
        FALLING,         // Свободное падение
        DEPLOYING,       // Раскрытие парашюта
        PARACHUTING,     // Спуск на парашюте
        LANDING,         // Приземление
        RUNNING,         // Бег по земле
        RESCUED          // Спасен
    }
    
    /**
     * Дополнительный конструктор, принимающий позицию
     */
    constructor(position: Vector2D) : this(Color.BLUE, 20f) {
        this.position = position
        this.isEjected = true
        this.state = State.FALLING
    }
    
    /**
     * Обновление состояния пилота
     */
    fun update(deltaTime: Float = 0.016f) {
        if (state == State.IN_PLANE || state == State.RESCUED) return
        
        // Защита от некорректных значений deltaTime
        val safeDeltaTime = if (deltaTime.isNaN() || deltaTime <= 0f || deltaTime > 0.1f) 0.016f else deltaTime
        
        // Увеличиваем время с момента катапультирования
        ejectionTime += safeDeltaTime
        
        // ПРИНУДИТЕЛЬНАЯ ГРАВИТАЦИЯ - ВСЕГДА применяем гравитацию к пилоту без парашюта
        if (state != State.PARACHUTING) {
            // Прямое значение гравитации без множителей, чтобы гарантировать движение вниз
            velocity.y += 4.0f * safeDeltaTime * 60f  // ОЧЕНЬ СИЛЬНАЯ ГРАВИТАЦИЯ
        }
        
        // Обязательно логируем каждое обновление для отладки
        if (ejectionTime < 10.0f && ejectionTime % 0.2f < 0.02f) {
            Log.d(TAG, "ОТЛАДКА: стадия=$state, позиция=${position.x.toInt()},${position.y.toInt()}, " +
                  "скорость=${velocity.x.toInt()},${velocity.y.toInt()}, время=$ejectionTime")
        }
        
        // ОЧЕНЬ ВАЖНО - ЕСЛИ САМОЛЕТ КАТАПУЛЬТИРОВАЛСЯ НИЗКО
        // Немедленно открываем парашют, если пилот уже близко к земле
        if (state == State.FALLING && parachuteOpenTime < parachuteOpenDelay && position.y > 1500) {
            parachuteOpenTime = parachuteOpenDelay  // Принудительно запускаем раскрытие парашюта
            Log.d(TAG, "ПРИНУДИТЕЛЬНОЕ открытие парашюта из-за близости к земле! y=${position.y}")
        }
        
        when (state) {
            State.EJECTING -> {
                // Первая стадия катапультирования - быстро переходим в FALLING
                if (ejectionTime > 0.1f) {
                    state = State.FALLING
                    Log.d(TAG, "ПЕРЕХОД: EJECTING -> FALLING, pos=$position, vel=$velocity")
                }
                
                // Случайное вращение
                rotation += rotationVelocity * safeDeltaTime * 60f
                rotationVelocity *= 0.95f
            }
            
            State.FALLING -> {
                // ПРЯМАЯ СИЛА ГРАВИТАЦИИ вместо косвенных вычислений
                velocity.y += gravityValue * 2f // Дополнительная гравитация для падения
                
                // Применяем сопротивление воздуха
                val speed = velocity.length()
                val dragX = -velocity.x * airResistance * speed
                val dragY = -velocity.y * airResistance * speed
                velocity.x += dragX
                velocity.y += dragY
                
                // Ограничиваем максимальную скорость падения
                if (velocity.y > maxFallingSpeed) {
                    velocity.y = maxFallingSpeed
                }
                
                // Увеличиваем время падения
                parachuteOpenTime += safeDeltaTime
                
                // ГАРАНТИРОВАННО раскрываем парашют после задержки
                // Упрощаем логику проверки
                if (parachuteOpenTime >= parachuteOpenDelay) {
                    state = State.DEPLOYING
                    parachuteDeploying = true
                    parachuteDeploymentProgress = 0f
                    
                    // ВАЖНЫЙ МОМЕНТ: устанавливаем флаг начала раскрытия парашюта
                    parachuteOpened = true
                    
                    Log.d(TAG, "ПАРАШЮТ НАЧИНАЕТ РАСКРЫВАТЬСЯ: время=$parachuteOpenTime, позиция=$position")
                }
                
                // Вращение пилота при падении
                rotation += rotationVelocity * safeDeltaTime * 60f
                rotationVelocity *= 0.98f
            }
            
            State.DEPLOYING -> {
                // Прогресс раскрытия парашюта
                parachuteDeploymentProgress += safeDeltaTime / parachuteDeployTime
                parachuteDeploymentProgress = min(1.0f, parachuteDeploymentProgress)
                
                // Применяем гравитацию, но уменьшаем её по мере раскрытия парашюта
                val gravityFactor = 1.0f - min(0.8f, parachuteDeploymentProgress)
                velocity.y += gravityValue * gravityFactor
                
                // Увеличиваем сопротивление воздуха по мере раскрытия парашюта
                val resistanceFactor = min(1.0f, parachuteDeploymentProgress)
                val effectiveResistance = airResistance + (parachuteResistance - airResistance) * resistanceFactor
                
                val speed = velocity.length()
                val dragX = -velocity.x * effectiveResistance * speed
                val dragY = -velocity.y * effectiveResistance * speed
                velocity.x += dragX
                velocity.y += dragY
                
                // Замедляем вращение при раскрытии парашюта
                rotationVelocity *= 0.9f
                rotation += rotationVelocity * safeDeltaTime * 60f
                
                // Когда парашют полностью раскрыт
                if (parachuteDeploymentProgress >= 1.0f) {
                    parachuteFullyDeployed = true
                    parachuteOpened = true
                    state = State.PARACHUTING
                    Log.d(TAG, "ПЕРЕХОД: DEPLOYING -> PARACHUTING (парашют открыт)")
                    
                    // Стабилизируем пилота
                    rotation = 0f
                    rotationVelocity = 0f
                }
            }
            
            State.PARACHUTING -> {
                // Применяем гравитацию, но с меньшей силой
                velocity.y += gravityValue * 0.3f
                
                // Сильное сопротивление из-за парашюта
                val speed = velocity.length()
                val dragX = -velocity.x * parachuteResistance * speed
                val dragY = -velocity.y * parachuteResistance * speed
                velocity.x += dragX
                velocity.y += dragY
                
                // Ограничиваем скорость падения с парашютом
                if (velocity.y > maxParachuteSpeed) {
                    velocity.y = maxParachuteSpeed
                }
                
                // Случайные боковые движения
                if (Random.nextFloat() < 0.05f) {
                    velocity.x += (Random.nextFloat() - 0.5f) * 0.1f
                }
                
                // Ограничиваем боковую скорость
                velocity.x = velocity.x.coerceIn(-0.8f, 0.8f)
            }
            
            State.LANDING -> {
                // Увеличиваем время на земле
                groundContactTime += safeDeltaTime
                
                // Замедляем движение на земле
                velocity = velocity.add(Vector2D(0f, -0.9f * safeDeltaTime * 60f))
                
                // После восстановления переходим в состояние бега
                if (groundContactTime >= groundRecoveryTime) {
                    state = State.RUNNING
                    // Устанавливаем начальную скорость бега в случайном направлении
                    velocity = velocity.add(Vector2D(if (Random.nextBoolean()) runningSpeed else -runningSpeed, 0f))
                }
            }
            
            State.RUNNING -> {
                // Обновляем анимацию бега
                runningAnimationTime += safeDeltaTime * runningAnimationSpeed
                
                // Определяем направление к дому
                house?.let { house ->
                    // Вычисляем вектор направления к дому
                    val houseCenter = Vector2D(
                        house.position.x,
                        house.position.y - house.height / 2
                    )
                    
                    val directionToHouse = Vector2D(
                        houseCenter.x - position.x,
                        0f // Не бежим вверх или вниз, только по горизонтали
                    )
                    
                    // Если нужно бежать вправо
                    if (directionToHouse.x > 0) {
                        velocity = Vector2D(runningSpeed, 0f)
                    } else {
                        // Если нужно бежать влево
                        velocity = Vector2D(-runningSpeed, 0f)
                    }
                    
                    // Логируем информацию
                    if (Math.abs(position.x - houseCenter.x) < 50f) {
                        Log.d(TAG, "Пилот приближается к дому, расстояние: ${Math.abs(position.x - houseCenter.x)}")
                    }
                } ?: run {
                    // Если дом не определен, бежим в случайном направлении
                    // Ограничиваем скорость бега
                    velocity = velocity.add(Vector2D(0f, -velocity.y))
                    
                    // Случайное изменение направления
                    if (Random.nextFloat() < 0.01f) {
                        velocity = velocity.add(Vector2D(-velocity.x, 0f))
                    }
                }
            }
            
            else -> {
                // Ничего не делаем для других состояний
            }
        }
        
        // ВАЖНО - обновляем позицию на основе скорости - проверяем, что это происходит
        val oldPosition = Vector2D(position.x, position.y) // Сохраняем старую позицию для отладки
        
        // Обновляем позицию, применяя скорость
        position.x += velocity.x * safeDeltaTime * 60f
        position.y += velocity.y * safeDeltaTime * 60f
        
        // Логируем изменение позиции для отладки
        if (position.x == oldPosition.x && position.y == oldPosition.y && velocity.length() > 0.1f) {
            Log.e(TAG, "ОШИБКА: Позиция не изменилась несмотря на скорость $velocity")
        }
    }
    
    /**
     * Отрисовка пилота
     */
    fun draw(canvas: Canvas, paint: Paint) {
        if (state == State.IN_PLANE || state == State.RESCUED) return
        
        // Сохраняем текущие настройки краски
        val originalColor = paint.color
        val originalStyle = paint.style
        val originalStrokeWidth = paint.strokeWidth
        
        // Сохраняем состояние канваса
        canvas.save()
        
        // Перемещаем канвас в позицию пилота
        canvas.translate(position.x, position.y)
        
        // Вращаем канвас, если пилот в воздухе и парашют не раскрыт полностью
        if ((state == State.EJECTING || state == State.FALLING || 
             (state == State.DEPLOYING && parachuteDeploymentProgress < 0.8f)) && 
            !parachuteFullyDeployed) {
            canvas.rotate(rotation)
        }
        
        when (state) {
            State.EJECTING, State.FALLING -> {
                drawFallingPilot(canvas, paint)
            }
            
            State.DEPLOYING -> {
                // Рисуем раскрывающийся парашют
                if (parachuteDeploying) {
                    drawDeployingParachute(canvas, paint, parachuteDeploymentProgress)
                }
                drawFallingPilot(canvas, paint)
            }
            
            State.PARACHUTING -> {
                // Рисуем полностью раскрытый парашют
                drawParachute(canvas, paint)
                drawParachutingPilot(canvas, paint)
            }
            
            State.LANDING -> {
                drawLandingPilot(canvas, paint)
            }
            
            State.RUNNING -> {
                // Анимация бега
                drawRunningPilot(canvas, paint)
            }
            
            else -> {
                // Для других состояний просто рисуем круг
                paint.color = color
                paint.style = Paint.Style.FILL
                canvas.drawCircle(0f, 0f, size / 2, paint)
            }
        }
        
        // Восстанавливаем состояние канваса
        canvas.restore()
        
        // Восстанавливаем настройки краски
        paint.color = originalColor
        paint.style = originalStyle
        paint.strokeWidth = originalStrokeWidth
    }
    
    /**
     * Отрисовка падающего пилота
     */
    private fun drawFallingPilot(canvas: Canvas, paint: Paint) {
        // Рисуем тело пилота
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawCircle(0f, 0f, size / 2, paint)
        
        // Рисуем руки и ноги
        paint.strokeWidth = size / 5
        paint.style = Paint.Style.STROKE
        
        // Руки разведены в стороны при падении
        canvas.drawLine(-size / 2, -size / 4, -size, -size / 2, paint)
        canvas.drawLine(size / 2, -size / 4, size, -size / 2, paint)
        
        // Ноги слегка согнуты
        canvas.drawLine(0f, size / 2, -size / 2, size, paint)
        canvas.drawLine(0f, size / 2, size / 2, size, paint)
    }
    
    /**
     * Отрисовка пилота на парашюте
     */
    private fun drawParachutingPilot(canvas: Canvas, paint: Paint) {
        // Рисуем тело пилота
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawCircle(0f, 0f, size / 2, paint)
        
        // Рисуем руки и ноги
        paint.strokeWidth = size / 5
        paint.style = Paint.Style.STROKE
        
        // Руки подняты вверх, держатся за стропы
        canvas.drawLine(-size / 2, -size / 4, -size / 2, -size, paint)
        canvas.drawLine(size / 2, -size / 4, size / 2, -size, paint)
        
        // Ноги вместе, слегка согнуты
        canvas.drawLine(0f, size / 2, -size / 4, size, paint)
        canvas.drawLine(0f, size / 2, size / 4, size, paint)
    }
    
    /**
     * Отрисовка приземляющегося пилота
     */
    private fun drawLandingPilot(canvas: Canvas, paint: Paint) {
        // Рисуем тело пилота
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawCircle(0f, 0f, size / 2, paint)
        
        // Рисуем руки и ноги
        paint.strokeWidth = size / 5
        paint.style = Paint.Style.STROKE
        
        // Руки в стороны для баланса
        canvas.drawLine(-size / 2, -size / 4, -size, 0f, paint)
        canvas.drawLine(size / 2, -size / 4, size, 0f, paint)
        
        // Ноги согнуты в коленях для амортизации
        canvas.drawLine(0f, size / 2, -size / 2, size * 0.8f, paint)
        canvas.drawLine(-size / 2, size * 0.8f, -size / 3, size * 1.2f, paint)
        canvas.drawLine(0f, size / 2, size / 2, size * 0.8f, paint)
        canvas.drawLine(size / 2, size * 0.8f, size / 3, size * 1.2f, paint)
    }
    
    /**
     * Отрисовка бегущего пилота
     */
    private fun drawRunningPilot(canvas: Canvas, paint: Paint) {
        // Рисуем тело пилота
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawCircle(0f, 0f, size / 2, paint)
        
        // Рисуем руки и ноги с анимацией
        paint.strokeWidth = size / 5
        paint.style = Paint.Style.STROKE
        
        // Определяем направление бега
        val direction = if (velocity.x >= 0) 1 else -1
        
        // Анимация рук и ног
        val armAngle = sin(runningAnimationTime) * 45f * limbSwingFactor
        val legAngle = sin(runningAnimationTime + PI.toFloat() / 2) * 30f * limbSwingFactor
        
        // Руки
        canvas.save()
        canvas.rotate(armAngle)
        canvas.drawLine(0f, -size / 4, direction * size, 0f, paint)
        canvas.restore()
        
        canvas.save()
        canvas.rotate(-armAngle)
        canvas.drawLine(0f, -size / 4, -direction * size, 0f, paint)
        canvas.restore()
        
        // Ноги
        canvas.save()
        canvas.rotate(legAngle)
        canvas.drawLine(0f, size / 4, direction * size / 2, size, paint)
        canvas.restore()
        
        canvas.save()
        canvas.rotate(-legAngle)
        canvas.drawLine(0f, size / 4, -direction * size / 2, size, paint)
        canvas.restore()
    }
    
    /**
     * Отрисовка раскрывающегося парашюта
     */
    private fun drawDeployingParachute(canvas: Canvas, paint: Paint, progress: Float) {
        // Сохраняем текущие настройки краски
        val originalColor = paint.color
        val originalStyle = paint.style
        
        // Вычисляем текущие размеры раскрывающегося парашюта
        val currentWidth = parachuteWidth * progress
        val currentHeight = parachuteHeight * progress
        
        // Рисуем купол парашюта
        paint.color = 0xFFFFFFFF.toInt() // Белый цвет для парашюта
        paint.style = Paint.Style.FILL
        
        val parachutePath = Path()
        parachutePath.moveTo(-currentWidth / 2, -currentHeight - size)
        parachutePath.quadTo(0f, -currentHeight - size * 2 * progress, currentWidth / 2, -currentHeight - size)
        parachutePath.lineTo(currentWidth / 2, -currentHeight)
        parachutePath.quadTo(0f, -currentHeight + size / 2 * progress, -currentWidth / 2, -currentHeight)
        parachutePath.close()
        
        canvas.drawPath(parachutePath, paint)
        
        // Рисуем стропы парашюта
        paint.color = 0xFF888888.toInt() // Серый цвет для строп
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size / 10 * progress
        
        // Рисуем стропы с учетом прогресса раскрытия
        for (i in 0 until parachuteLines) {
            val x = -currentWidth / 2 + i * currentWidth / (parachuteLines - 1)
            val y = -currentHeight - sin(i * PI.toFloat() / (parachuteLines - 1)) * size * progress
            canvas.drawLine(x, y, 0f, 0f, paint)
        }
        
        // Восстанавливаем настройки краски
        paint.color = originalColor
        paint.style = originalStyle
    }
    
    /**
     * Отрисовка полностью раскрытого парашюта
     */
    private fun drawParachute(canvas: Canvas, paint: Paint) {
        // Сохраняем текущие настройки краски
        val originalColor = paint.color
        val originalStyle = paint.style
        val originalStrokeWidth = paint.strokeWidth
        
        // Добавляем колебания парашюта для реализма
        val waveOffset = sin(ejectionTime * 2f) * size / 4
        
        // Рисуем купол парашюта
        paint.color = 0xFFFFFFFF.toInt() // Белый цвет для парашюта
        paint.style = Paint.Style.FILL
        
        val parachutePath = Path()
        parachutePath.moveTo(-parachuteWidth / 2, -parachuteHeight - size)
        parachutePath.quadTo(-parachuteWidth / 4, -parachuteHeight - size * 2 + waveOffset, 
                            0f, -parachuteHeight - size * 2.2f)
        parachutePath.quadTo(parachuteWidth / 4, -parachuteHeight - size * 2 + waveOffset, 
                            parachuteWidth / 2, -parachuteHeight - size)
        parachutePath.lineTo(parachuteWidth / 2, -parachuteHeight)
        parachutePath.quadTo(0f, -parachuteHeight + size / 2, -parachuteWidth / 2, -parachuteHeight)
        parachutePath.close()
        
        canvas.drawPath(parachutePath, paint)
        
        // Рисуем стропы парашюта
        paint.color = 0xFF888888.toInt() // Серый цвет для строп
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size / 10
        
        // Рисуем несколько строп для реализма
        for (i in 0 until parachuteLines) {
            val x = -parachuteWidth / 2 + i * parachuteWidth / (parachuteLines - 1)
            val y = -parachuteHeight - sin(i * PI.toFloat() / (parachuteLines - 1)) * size
            canvas.drawLine(x, y, 0f, 0f, paint)
        }
        
        // Восстанавливаем настройки краски
        paint.color = originalColor
        paint.style = originalStyle
        paint.strokeWidth = originalStrokeWidth
    }
    
    /**
     * Катапультирует пилота из самолета
     */
    fun eject() {
        if (state != State.IN_PLANE) return
        
        // Устанавливаем флаги катапультирования
        isEjected = true
        state = State.EJECTING
        ejectionTime = 0f
        parachuteOpenTime = 0f
        
        // Начальный импульс вверх - УМЕНЬШАЕМ до разумных значений
        velocity = Vector2D(
            (Random.nextFloat() - 0.5f) * 3f, // Случайное горизонтальное движение
            -8.0f  // Умеренный начальный импульс вверх (было -15.0f)
        )
        
        // Устанавливаем вращение
        rotationVelocity = (Random.nextFloat() - 0.5f) * 20f
        
        // Отсоединяем пилота от самолета
        plane = null
        
        Log.d(TAG, "КАТАПУЛЬТИРОВАНИЕ! позиция=$position, скорость=$velocity")
    }
    
    /**
     * Катапультирует пилота из самолета с указанием позиции
     */
    fun eject(x: Float, y: Float) {
        // Устанавливаем позицию пилота
        position = Vector2D(x, y)
        
        // Вызываем основной метод катапультирования
        eject()
    }
    
    /**
     * Обработка контакта с землей
     */
    fun onGroundContact(groundLevel: Float) {
        if (state == State.PARACHUTING) {
            // Устанавливаем позицию на уровне земли
            position = Vector2D(position.x, groundLevel)
            
            // Переходим в состояние приземления
            state = State.LANDING
            isOnGround = true
            groundContactTime = 0f
            
            // Сбрасываем скорость
            velocity = Vector2D(velocity.x * 0.5f, 0f)
            
            Log.d(TAG, "Pilot landed on ground at position (${position.x}, $groundLevel)")
        } else if (state == State.FALLING || state == State.DEPLOYING) {
            // Если пилот падает без парашюта или с не полностью раскрытым парашютом,
            // то он разбивается (в реальной игре здесь можно добавить анимацию гибели)
            position = Vector2D(position.x, groundLevel)
            isOnGround = true
            velocity = Vector2D(0f, 0f)
            
            // В данной реализации просто переводим в состояние бега
            state = State.RUNNING
            velocity = Vector2D(if (Random.nextBoolean()) runningSpeed else -runningSpeed, 0f)
            
            Log.d(TAG, "Pilot crashed on ground at position (${position.x}, $groundLevel)")
        }
    }
    
    /**
     * Спасение пилота
     */
    fun rescue() {
        isRescued = true
        state = State.RESCUED
        velocity = Vector2D(0f, 0f)
        Log.d(TAG, "Pilot rescued")
    }
    
    /**
     * Устанавливает флаг катапультирования
     * @param ejected true, если пилот катапультировался
     */
    fun setEjectionState(ejected: Boolean) {
        isEjected = ejected
    }
    
    /**
     * Устанавливает ссылку на дом для определения направления бега
     */
    fun assignHouse(house: House) {
        this.house = house
        Log.d(TAG, "Установлена ссылка на дом в позиции (${house.position.x}, ${house.position.y})")
    }
} 