@echo off
chcp 65001 >nul
echo =========================================
echo   点餐系统 - 一键打包脚本
echo =========================================

:: 检查 Java
echo [1/5] 检查 Java 环境...
if not exist "C:\Java\jdk-17\zulu17.54.21-ca-jdk17.0.13-win_x64\bin\java.exe" (
    echo [错误] 未找到 JDK 17，请确认 C:\Java\jdk-17 目录
    pause
    exit /b 1
)
set JAVA_HOME=C:\Java\jdk-17\zulu17.54.21-ca-jdk17.0.13-win_x64
echo   Java OK

:: 构建前端
echo [2/5] 构建前端...
cd web
call npx vite build
if errorlevel 1 (
    echo [错误] 前端构建失败
    pause
    exit /b 1
)
cd ..

:: 复制前端到后端
echo [3/5] 复制前端文件...
if exist server\static rmdir /s /q server\static
xcopy /E /I /Y web\dist server\static >nul
echo   复制完成

:: 构建后端
echo [4/5] 构建后端...
call gradlew.bat :server:installDist --no-daemon
if errorlevel 1 (
    echo [错误] 后端构建失败
    pause
    exit /b 1
)

:: 打包
echo [5/5] 打包部署文件...
if exist ordering-system-deploy rmdir /s /q ordering-system-deploy
mkdir ordering-system-deploy\app\lib
mkdir ordering-system-deploy\app\static
xcopy /E /I /Y server\build\install\server\lib ordering-system-deploy\app\lib >nul
xcopy /E /I /Y server\static ordering-system-deploy\app\static >nul
copy /Y install.sh ordering-system-deploy\ >nul 2>nul
copy /Y uninstall.sh ordering-system-deploy\ >nul 2>nul

:: 用 PowerShell 打包 tar.gz
powershell -Command "Com-Archive -Path 'ordering-system-deploy\*' -DestinationPath 'ordering-system-deploy.zip' -Force"
if exist ordering-system-deploy.tar.gz del ordering-system-deploy.tar.gz
powershell -Command "& { tar -czf ordering-system-deploy.tar.gz -C ordering-system-deploy . }"
if errorlevel 1 (
    echo [提示] tar 不可用，已生成 zip 包
    move ordering-system-deploy.zip ordering-system-deploy.tar.gz >nul 2>nul
)

:: 清理临时目录
rmdir /s /q ordering-system-deploy

echo.
echo =========================================
echo   打包完成！
echo   文件: ordering-system-deploy.tar.gz
echo =========================================
pause
