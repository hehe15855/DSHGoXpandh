#!/bin/bash
# release.sh -- xmod 的发布闸门。
#
# 灰尘落在这一句上：发出去的东西得是当前源码编出来的。
# 所以顺序是死的：先核对，核对不过就停，不给「先发出去再说」的口子。
#
# 用法:
#   ./release.sh        核对 + 打印要执行的 gh 命令（不推送）
#   ./release.sh --go   核对通过后真的发（外发，得有人点头才用这个参数）
set -u
D=$(cd "$(dirname "$0")" && pwd); cd "$D"
# 远端仓库真名是 DSHGoXpandh，别跟显示名一起改，改了更新检查就断
REPO=${REPO:-hehe15855/DSHGoXpandh}
GO=0; [ "${1:-}" = "--go" ] && GO=1

echo '[1/4] 源码 ↔ 本地产物 ↔ 线上 Release'
./verify-release.sh; RC=$?
if [ "$RC" -ne 0 ]; then
  echo
  case "$RC" in
    2) echo "✗ 有不一致：本地包落后于源码，或线上挂的是旧构建。重新 build 并核对通过再发。" ;;
    3) echo "✗ 取不到线上 Release（网络或仓库名）。核对做不成就当没通过。" ;;
    *) echo "✗ 核对失败（exit $RC），见上面输出。" ;;
  esac
  exit "$RC"
fi

echo
echo '[2/4] 工作区'
if [ -n "$(git status --porcelain)" ]; then
  echo '✗ 有未提交改动，先提交再发：'
  git status --short | head -10
  exit 4
fi
echo '干净'

echo
echo '[3/4] 版本号'
VN=$(grep -o 'versionName="[^"]*"' AndroidManifest.xml | head -1 | sed 's/.*="//;s/"//')
VC=$(grep -o 'versionCode="[0-9]*"' AndroidManifest.xml | head -1 | grep -o '[0-9]*')
TAG="v$VN"
echo "manifest: $VN / versionCode $VC    →  tag $TAG"
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  echo "（本仓库已有同名 tag，gh 会复用它）"
fi

echo
echo '[4/4] 发布'
APK="DSHxpandh-$TAG.apk"
echo "gh release create $TAG --repo $REPO \\"
echo "  --title \"DSHxpandh $TAG\" --generate-notes \\"
echo "  \"/tmp/$APK#$APK\""

if [ "$GO" = 1 ]; then
  echo
  echo '→ 执行'
  cp mod_signed.apk "/tmp/$APK" || exit 5
  gh release create "$TAG" --repo "$REPO" --title "DSHxpandh $TAG" \
    --generate-notes "/tmp/$APK#$APK" || exit 5
  echo
  echo '发完。再跑一次 ./verify-release.sh，第 2 行应该变成 ✓'
else
  echo
  echo '只是打印，没发。要真发：./release.sh --go（这一步是外发，等点头）'
fi