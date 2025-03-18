from PIL import Image, ImageDraw
import os

# Создаем директорию, если она не существует
os.makedirs("app/src/main/assets/particles", exist_ok=True)

# Создаем новое изображение с прозрачным фоном
size = 32
image = Image.new('RGBA', (size, size), (0, 0, 0, 0))
draw = ImageDraw.Draw(image)

# Рисуем белый круг с мягкими краями
for i in range(size):
    for j in range(size):
        # Вычисляем расстояние от центра
        distance = ((i - size/2) ** 2 + (j - size/2) ** 2) ** 0.5
        
        # Максимальное расстояние от центра до угла
        max_distance = (size/2) * 1.414
        
        # Нормализуем расстояние
        normalized_distance = distance / (size/2)
        
        # Вычисляем прозрачность (альфа-канал)
        # Чем дальше от центра, тем более прозрачный
        if normalized_distance < 1:
            alpha = int(255 * (1 - normalized_distance))
            draw.point((i, j), fill=(255, 255, 255, alpha))

# Сохраняем изображение
image.save("app/src/main/assets/particles/particle.png")

print("Изображение частицы успешно создано и сохранено в app/src/main/assets/particles/particle.png") 