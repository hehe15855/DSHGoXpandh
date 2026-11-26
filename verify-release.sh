#!/bin/bash
# 用法: ./verify-release.sh
#
# 核对三样东西是不是同一份 dex：
#   源码  → build.sh 能编出什么
#   本地  → mod_signed.apk 是不是刚编的
#   线上  → GitHub Release 挂的 asset 是不是同一份
#
# 会重编（覆盖 mod_signed.apk），编前产物存 /tmp/xmod-verify-<ts>/
# 只读远端，不推送、不上传。
set -u
D=$(cd "$(dirname "$0")" && pwd); cd "$D"
# 远端仓库真名，勿随显示名改
REPO=${REPO:-hehe15855/DSHGoXpandh}
BAK=/tmp/xmod-verify-$(date +%m%d-%H%M%S); mkdir -p "$BAK"

dexsha()  { unzip -p "$1" classes.dex 2>/dev/null | sha256sum | cut -c1-16; }
dexsize() { unzip -p "$1" classes.dex 2>/dev/null | wc -c | tr -d ' '; }
ver()     { grep -o 'versionCode="[0-9]*"' AndroidManifest.xml | head -1 | grep -o '[0-9]*'; }

# ── 编前：本地产物 ───────────────────────────────
if [ -f mod_signed.apk ]; then
  cp mod_signed.apk "$BAK/pre.apk"
  PRE=$(dexsha mod_signed.apk); PRESZ=$(dexsize mod_signed.apk)
else
  PRE=""; PRESZ=0
fi

# ── 重编 ────────────────────────────────────────
echo "重编源码（build.sh）…"
if ! ./build.sh >"$BAK/build.log" 2>&1; then
  echo "✗ build.sh 失败，见 $BAK/build.log"; tail -6 "$BAK/build.log"; exit 1
fi
SRC=$(dexsha mod_signed.apk); SRCSZ=$(dexsize mod_signed.apk)
VC=$(ver); VN=$(grep -o 'versionName="[^"]*"' AndroidManifest.xml | head -1 | sed 's/.*="//;s/"//')

# ── 线上 ────────────────────────────────────────
TAG="?"; REL=""; URL=""
JSON=$(curl -s -m 15 -H 'Accept: application/vnd.github+json' \
  "https://api.github.com/repos/$REPO/releases/latest" 2>/dev/null)
if [ -n "$JSON" ]; then
  TAG=$(printf '%s' "$JSON" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("tag_name","?"))' 2>/dev/null || echo "?")
  URL=$(printf '%s' "$JSON" | python3 -c 'import sys,json;a=json.load(sys.stdin).get("assets") or [];print(a[0]["browser_download_url"] if a else "")' 2>/dev/null)
fi
if [ -n "$URL" ]; then
  curl -sL -m 90 -o "$BAK/rel.apk" "$URL"
  REL=$(dexsha "$BAK/rel.apk"); RELSZ=$(dexsize "$BAK/rel.apk")
else
  REL=""; RELSZ=0
fi

# ── 结论 ────────────────────────────────────────
echo
echo "源码 dex  $SRC  ${SRCSZ}B  v$VN/$VC"
if [ -z "$PRE" ]; then
  echo "本地包    （编前没有产物）"
  L1="skip"
elif [ "$PRE" = "$SRC" ]; then
  echo "本地包    = 源码   $PRE"
  L1="ok"
else
  echo "本地包    ≠ 源码   $PRE ($PRESZ B) → 编前那份落后于当前源码"
  L1="bad"
fi
if [ -z "$REL" ]; then
  echo "线上包    取不到（tag $TAG）"
  L2="skip"
elif [ "$REL" = "$SRC" ]; then
  echo "线上包    = 源码   $REL  tag $TAG"
  L2="ok"
else
  echo "线上包    ≠ 源码   $REL ($RELSZ B)  tag $TAG"
  L2="bad"
fi

echo
echo "──────── 三行结论 ────────"
[ "$L1" = ok ] && echo "1. 本地产物 = 当前源码 ✓" || echo "1. 本地产物 = 当前源码 ✗ ($L1)"
[ "$L2" = ok ] && echo "2. 线上 Release = 当前源码 ✓" || echo "2. 线上 Release = 当前源码 ✗ ($L2)"
if [ "$L2" = ok ] && [ "$TAG" != "v$VN" ] && [ "$TAG" != "?" ]; then
  echo "3. 版本号：线上 tag $TAG ≠ 本地 v$VN ✗"
elif [ "$TAG" = "v$VN" ]; then
  echo "3. 版本号：线上 tag $TAG = 本地 v$VN ✓"
else
  echo "3. 版本号：线上 tag $TAG / 本地 v$VN （读不到或不同）"
fi
echo
echo "备份在 $BAK"
if [ "$L1" = bad ] || [ "$L2" = bad ]; then exit 2; fi
if [ "$L2" = skip ]; then exit 3; fi   # 取不到远端 ≠ 通过
exit 0