@echo off
chcp 65001 >nul
echo =========================================
echo   点餐系统 - EXE 安装包构建脚本
echo =========================================

set JAVA_HOME=C:\Java\jdk-17\zulu17.54.21-ca-jdk17.0.13-win_x64
set PATH=%JAVA_HOME%\bin;%PATH%

:: 检查 Java
echo [1/6] 检查 Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 JDK 17
    pause
    exit /b 1
)
echo   Java OK

:: 构建前端
echo [2/6] 构建前端...
cd web
call npx vite build
if errorlevel 1 (
    echo [错误] 前端构建失败
    pause
    exit /b 1
)
cd ..

:: 复制前端到后端
echo [3/6] 复制前端文件...
if exist server\static rmdir /s /q server\static
xcopy /E /I /Y web\dist server\static >nul
echo   复制完成

:: 构建 Fat JAR
echo [4/6] 构建 Fat JAR...
call gradlew.bat :server:shadowJar --no-daemon
if errorlevel 1 (
    echo [错误] JAR 构建失败
    pause
    exit /b 1
)

:: 准备 jpackage 输入目录
echo [5/6] 准备打包文件...
if exist build\jpackage-input rmdir /s /q build\jpackage-input
mkdir build\jpackage-input
copy server\build\libs\server-all.jar build\jpackage-input\ordering-system.jar >nul
xcopy /E /I /Y server\static build\jpackage-input\static >nul

:: 创建启动脚本
echo @echo off > build\jpackage-input\start.bat
echo cd /d "%%~dp0" >> build\jpackage-input\start.bat
echo java -Xms128m -Xmx512m -jar ordering-system.jar >> build\jpackage-input\start.bat

:: 使用 jpackage 创建 EXE
echo [6/6] 创建 EXE 安装包...
if exist build\exe-output rmdir /s /q build\exe-output
mkdir build\exe-output

jpackage ^
    --type app-image ^
    --name "点餐系统" ^
    --input build\jpackage-input ^
    --main-jar ordering-system.jar ^
    --main-class com.ordering.system.ApplicationKt ^
    --java-options "-Xms128m" ^
    --java-options "-Xmx512m" ^
    --dest build\exe-output ^
    --app-version 1.0.0 ^
    --vendor "老李板面馆"

if errorlevel 1 (
    echo [错误] EXE 打包失败
    pause
    exit /b 1
)

echo.
echo =========================================
echo   打包完成！
echo   输出目录: build\exe-output\点餐系统
echo   运行: build\exe-output\点餐系统\点餐系统.exe
echo =========================================
pause
