#!/bin/bash
# deploy.sh -- 只编译 + 安装模块 APK。不碰 LSPosed 数据库、不碰任何系统配置。
set -eu
D=/root/workspace/xmod
cd "$D"
echo '[1/2] build'
ENTRY=com.dsh.xmod.HookEntry sh build.sh | tail -2
echo '[2/2] install'
cp mod_signed.apk /sdcard/DSH-Output/xmod-deploy.apk
psh "cp /sdcard/DSH-Output/xmod-deploy.apk /data/local/tmp/x.apk; chmod 644 /data/local/tmp/x.apk; /system/bin/pm install -r /data/local/tmp/x.apk" | tail -1
rm -f /sdcard/DSH-Output/xmod-deploy.apk
echo 'done. 之后请在 LSPosed 管理器里启用/勾选作用域（那是 UI 操作，脚本不代劳）'