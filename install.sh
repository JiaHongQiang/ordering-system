#!/bin/bash
set -e
APP_NAME="ordering-system"
INSTALL_DIR="/opt/$APP_NAME"
SERVICE_USER="ordering"
JAVA_MIN_VERSION=17
echo "========================================="
echo "  点餐系统 - 安装程序"
echo "========================================="
if [ "$(id -u)" -ne 0 ]; then echo "[错误] 请用 root 运行: sudo bash install.sh"; exit 1; fi
echo "[1/6] 检查 Java..."
if ! command -v java &> /dev/null; then echo "[错误] 未找到 Java: sudo apt install -y openjdk-17-jre-headless"; exit 1; fi
JAVA_VER=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VER" -lt "$JAVA_MIN_VERSION" ]; then echo "[错误] Java 版本过低"; exit 1; fi
echo "  Java $JAVA_VER OK"
echo "[2/6] 停止旧服务..."
systemctl stop $APP_NAME 2>/dev/null || true
echo "[3/6] 创建用户..."
if ! id "$SERVICE_USER" &>/dev/null; then useradd --system --shell /usr/sbin/nologin --home-dir $INSTALL_DIR $SERVICE_USER; fi
echo "[4/6] 安装文件..."
mkdir -p $INSTALL_DIR/{lib,static,data,logs}
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cp -r "$SCRIPT_DIR/app/lib/"* $INSTALL_DIR/lib/
cp -r "$SCRIPT_DIR/app/static/"* $INSTALL_DIR/static/
cat > $INSTALL_DIR/start.sh << 'SEOF'
#!/bin/bash
cd /opt/ordering-system
nohup java -Xms128m -Xmx512m -cp "lib/*" com.ordering.system.ApplicationKt > logs/app.log 2>&1 &
echo $! > app.pid
echo "已启动 PID: $(cat app.pid)"
SEOF
chmod +x $INSTALL_DIR/start.sh
cat > $INSTALL_DIR/stop.sh << 'XEOF'
#!/bin/bash
if [ -f /opt/ordering-system/app.pid ]; then
  kill $(cat /opt/ordering-system/app.pid) 2>/dev/null && echo "已停止" || echo "进程不存在"
  rm -f /opt/ordering-system/app.pid
else
  pkill -f "com.ordering.system.ApplicationKt" && echo "已停止" || echo "未找到"
fi
XEOF
chmod +x $INSTALL_DIR/stop.sh
chown -R $SERVICE_USER:$SERVICE_USER $INSTALL_DIR
chmod -R 755 $INSTALL_DIR; chmod -R 770 $INSTALL_DIR/data $INSTALL_DIR/logs
echo "[5/6] 配置 systemd..."
cat > /etc/systemd/system/$APP_NAME.service << SVCEOF
[Unit]
Description=点餐系统
After=network.target
[Service]
Type=simple
User=$SERVICE_USER
WorkingDirectory=$INSTALL_DIR
ExecStart=/usr/bin/java -Xms128m -Xmx512m -cp "$INSTALL_DIR/lib/*" com.ordering.system.ApplicationKt
Restart=always
RestartSec=5
StandardOutput=append:$INSTALL_DIR/logs/app.log
StandardError=append:$INSTALL_DIR/logs/app.log
[Install]
WantedBy=multi-user.target
SVCEOF
systemctl daemon-reload && systemctl enable $APP_NAME
echo "[6/6] 启动服务..."
systemctl start $APP_NAME && sleep 2
if systemctl is-active --quiet $APP_NAME; then
  echo ""; echo "========================================="; echo "  安装成功！"; echo "========================================="
  echo "  访问: http://$(hostname -I | awk '{print $1}'):8080"
  echo "  管理: sudo systemctl start/stop/status $APP_NAME"
  echo "  日志: sudo tail -f $INSTALL_DIR/logs/app.log"
else
  echo "[警告] 启动失败: sudo journalctl -u $APP_NAME -n 20"
fi
