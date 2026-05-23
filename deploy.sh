#!/bin/bash
# 老李板面馆点餐系统 - 一键部署脚本
# 用法: bash deploy.sh

set -e

echo "=============================="
echo "  老李板面馆点餐系统 - 部署"
echo "=============================="

# 1. 打包前端
echo ""
echo "[1/3] 打包前端..."
cd web
npm install
npm run build
cd ..

# 2. 复制前端到服务器静态目录
echo "[2/3] 复制前端文件..."
rm -rf server/static
cp -r web/dist server/static

# 3. 编译后端
echo "[3/3] 编译后端..."
export JAVA_HOME="${JAVA_HOME:-/c/jdk17/zulu17.54.21-ca-jdk17.0.13-win_x64}"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :server:build --no-daemon

echo ""
echo "=============================="
echo "  部署完成！"
echo "=============================="
echo ""
echo "启动方式:"
echo "  cd server"
echo "  java -jar build/libs/server-all.jar"
echo ""
echo "或者直接运行:"
echo "  ./gradlew :server:run"
echo ""
echo "访问地址: http://你的IP:8080"
echo "局域网内其他设备用浏览器打开即可"
echo ""
