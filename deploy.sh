#!/bin/bash
# deploy.sh -- 编译并安装到通过 adb 连接的设备。不碰 LSPosed 数据库、不碰任何系统配置。
set -eu
D=$(cd "$(dirname "$0")" && pwd)
cd "$D"

echo '[1/2] build'
bash build.sh | tail -2

echo '[2/2] install'
adb install -r mod_signed.apk

echo 'done。之后在 LSPosed 管理器里启用模块、勾选作用域，再重启目标 App。'