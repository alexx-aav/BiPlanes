# Скрипт для сборки APK через Android Studio
Write-Host "Начинаю сборку APK..."

# Путь к Android Studio (измените на ваш путь)
$studioPath = "C:\Program Files\Android\Android Studio\bin\studio64.exe"

# Проверяем существование Android Studio
if (Test-Path $studioPath) {
    Write-Host "Android Studio найдена по пути: $studioPath"
    
    # Запускаем Android Studio с параметрами для сборки APK
    & $studioPath "C:\MyGame" --build "assembleDebug" --stacktrace
    
    Write-Host "Команда сборки отправлена в Android Studio"
    Write-Host "Проверьте папку C:\MyGame\app\build\outputs\apk\debug\ после завершения сборки"
} else {
    Write-Host "Android Studio не найдена по пути: $studioPath"
    Write-Host "Пожалуйста, укажите правильный путь к Android Studio в скрипте"
} 