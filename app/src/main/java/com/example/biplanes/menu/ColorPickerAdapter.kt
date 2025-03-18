package com.example.biplanes.menu

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.biplanes.R
import com.example.biplanes.game.models.PlaneColor

/**
 * Адаптер для выбора цвета самолета в RecyclerView
 */
class ColorPickerAdapter private constructor(
    private val colors: List<Int>,
    private var selectedPosition: Int = 0,
    private val onColorSelected: (Int) -> Unit
) : RecyclerView.Adapter<ColorPickerAdapter.ColorViewHolder>() {
    private val TAG = "ColorPickerAdapter"

    /**
     * ViewHolder для элемента выбора цвета
     */
    class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorView: View = itemView.findViewById(R.id.colorView)
        val selectionIndicator: ImageView = itemView.findViewById(R.id.selectionIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        try {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_color_picker, parent, false)
            return ColorViewHolder(view)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreateViewHolder: ${e.message}", e)
            // Fallback в случае ошибки
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ColorViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        try {
            val color = colors[position]
            
            // Создаем круглый фон для цвета
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.OVAL
            shape.setColor(color)
            holder.colorView.background = shape
            
            // Показываем индикатор выбора, если этот цвет выбран
            holder.selectionIndicator.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE
            
            // Обработчик нажатия
            holder.itemView.setOnClickListener {
                val previousSelected = selectedPosition
                selectedPosition = position
                
                // Обновляем отображение для предыдущего и нового выбранного элемента
                notifyItemChanged(previousSelected)
                notifyItemChanged(selectedPosition)
                
                // Вызываем колбэк с выбранным цветом
                onColorSelected(color)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onBindViewHolder: ${e.message}", e)
        }
    }

    override fun getItemCount(): Int {
        return try {
            colors.size
        } catch (e: Exception) {
            Log.e(TAG, "Error in getItemCount: ${e.message}", e)
            0
        }
    }
    
    /**
     * Создает адаптер для работы с PlaneColor
     */
    companion object {
        fun fromPlaneColors(
            planeColors: List<PlaneColor>,
            selectedPosition: Int = 0,
            onColorSelected: (PlaneColor) -> Unit
        ): ColorPickerAdapter {
            // Преобразуем список PlaneColor в список Int
            val colorInts = planeColors.map { it.color }
            
            // Создаем адаптер с обработчиком, который преобразует Int обратно в PlaneColor
            return ColorPickerAdapter(colorInts, selectedPosition) { colorInt ->
                // Находим PlaneColor по значению color
                val planeColor = planeColors.find { it.color == colorInt }
                    ?: PlaneColor.BLUE // Если не найден, используем BLUE по умолчанию
                
                // Вызываем колбэк с найденным PlaneColor
                onColorSelected(planeColor)
            }
        }
    }
} 