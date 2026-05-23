@echo off
chcp 65001 >nul
echo ==============================
echo   老李板面馆点餐系统 - 部署
echo ==============================

REM 1. 打包前端
echo.
echo [1/3] 打包前端...
cd web
call npm install
call npm run build
cd ..

REM 2. 复制前端到服务器静态目录
echo [2/3] 复制前端文件...
if exist server\static rmdir /s /q server\static
xcopy /E /I /Y web\dist server\static

REM 3. 编译后端并生成可执行包
echo [3/3] 编译后端...
if not defined JAVA_HOME set JAVA_HOME=C:\jdk17\zulu17.54.21-ca-jdk17.0.13-win_x64
set PATH=%JAVA_HOME%\bin;%PATH%
call gradlew.bat :server:installDist --no-daemon

echo.
echo ==============================
echo   部署完成！
echo ==============================
echo.
echo 启动方式:
echo   server\build\install\server\bin\server.bat
echo.
echo 或者直接运行:
echo   gradlew.bat :server:run
echo.
echo 访问地址: http://你的IP:8080
echo 局域网内其他设备用浏览器打开即可
echo.
pause
