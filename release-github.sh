#!/bin/bash
# 发布到 GitHub + LSPosed 模块仓库前置设置
# 用法：./release-github.sh [--desc-only]
# 需要：gh 已登录（gh auth login）
set -e
REPO=hehe15855/DSHGoXpandh

DESC="Runtime Java-layer access to any app: hook, read, rewrite. LSPosed module."
TOPICS="xposed xposed-module lsposed android reverse-engineering webview hook cookie-stealer-free"

command -v gh >/dev/null || { echo "需要 gh CLI"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "先跑 gh auth login"; exit 1; }

echo "[1/3] 设置仓库描述与 topics"
# gh 没有直接改 topics 的命令，用 API
gh api -X PATCH "repos/$REPO" \
  -f description="$DESC" \
  -f "topics[]=xposed" -f "topics[]=xposed-module" -f "topics[]=lsposed" \
  -f "topics[]=android" -f "topics[]=reverse-engineering" -f "topics[]=webview" \
  -f "topics[]=hook" -f "topics[]=cookie" \
  --jq '.description, .topics'

[ "$1" = "--desc-only" ] && { echo "仅更新描述，结束"; exit 0; }

echo "[2/3] 检查产物"
[ -f mod_signed.apk ] || { echo "先跑 ./build.sh"; exit 1; }
VER=$(aapt2 dump badging mod_signed.apk 2>/dev/null | grep -oP "versionName='\K[^']+" | head -1)
[ -z "$VER" ] && VER=$(node -p "require('./package.json').version" 2>/dev/null || echo "unknown")
echo "  APK: $(ls -lh mod_signed.apk | awk '{print $5}')  版本: $VER"

echo "[3/3] 推送提交"
git push origin HEAD 2>&1 | tail -3

echo ""
echo "=== 下一步（手动确认后执行）==="
echo "gh release create v$VER mod_signed.apk --title \"v$VER\" --notes-from-tag"
echo ""
echo "=== LSPosed 模块仓库 ==="
echo "上架需要去 https://github.com/Xposed-Modules-Repo 提 issue"
echo "填：仓库地址 + 模块 id（io.github.hehe15855.dshxpandh）"