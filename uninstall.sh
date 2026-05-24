#!/bin/bash
if [ "$(id -u)" -ne 0 ]; then echo "请用 root 运行"; exit 1; fi
read -p "确定卸载？(y/N): " c; if [ "$c" != "y" ]; then echo "已取消"; exit 0; fi
systemctl stop ordering-system 2>/dev/null; systemctl disable ordering-system 2>/dev/null
rm -f /etc/systemd/system/ordering-system.service; systemctl daemon-reload
rm -rf /opt/ordering-system; userdel ordering 2>/dev/null
echo "卸载完成"
