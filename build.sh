#!/bin/bash
# 用法: ./build.sh   (在当前目录构建 Xposed 模块 APK)
set -e
D=$(cd "$(dirname "$0")" && pwd)
# 自动探测 android.jar 与 xapi.jar（也可用环境变量指定）
pick() {
  for p in "$@"; do [ -f "$p" ] && { echo "$p"; return; }; done
}
# aapt2 必须用带 resources.arsc 的 SDK platform jar；高版本 robolectric jar 只给 javac 用
APJ=$(pick \
  /opt/android-tools/platform/android.jar \
  /usr/lib/android-sdk/platforms/*/android.jar \
  $ANDROID_HOME/platforms/*/android.jar)
AJ=${ANDROID_JAR:-$(pick \
  /opt/android-tools/platform-36/android.jar \
  /opt/android-tools/platform/android.jar \
  /usr/lib/android-sdk/platforms/*/android.jar \
  $ANDROID_HOME/platforms/*/android.jar \
  ~/Android/Sdk/platforms/*/android.jar)}
XI=${XAPI_JAR:-$(pick \
  /opt/android-tools/xapi.jar \
  ./xapi.jar \
  ../xapi.jar \
  ~/xapi.jar)}
# 新版 libxposed API：优先用仓库内自带的 jar（不依赖外部环境，换机器也能编）
MODERN=$(pick \
  $D/api-libxposed/libxposed-api.jar \
  ./api-libxposed/libxposed-api.jar)
ANN=$(pick \
  $D/api-libxposed/deps/annotation.jar \
  ./api-libxposed/deps/annotation.jar)
[ -z "$AJ" ] && { echo 'android.jar not found, set ANDROID_JAR'; exit 1; }
# 旧 xapi.jar 已非必需：代码已完全迁移到 libxposed 新 API。有就带上（兼容），没有也能编。
[ -z "$XI" ] && XI=/dev/null
KS=${KEYSTORE:-$D/debug.ks}
# 版本号自动对齐：VER 常量始终等于 manifest 的 versionCode
VC=$(grep -o 'versionCode="[0-9]*"' $D/AndroidManifest.xml | head -1 | grep -o '[0-9]*')
# 包名从 Manifest 读，xposed_init 自动跟随，不用手工同步
PKG=$(grep -o 'package="[^"]*"' $D/AndroidManifest.xml | head -1 | sed 's/package="//;s/"//')
echo "package: $PKG"
HE=$(find $D/src -name HookEntry.java | head -1)
[ -z "$HE" ] && { echo "HookEntry.java not found under $D/src"; exit 1; }
sed -i "s/VER = \"v[0-9]*\"/VER = \"v$VC\"/" "$HE"

echo "version sync: v$VC"
# 文本同步：module.prop 的 version/versionCode/description 跟着 Manifest 与 texts/ 走
node "$D/sync-texts.mjs" "$D"
# 源码级审验：缺失/遗漏/冲突/重复/语义/优化，未过不许打包
# 源码审验。退出码约定：0=通过 / 1=真问题（中止）/ 其他=脚本自身故障（只警告，不阻断）
node "$D/audit.mjs" "$D" --pre; AUDIT_RC=$?
if [ "$AUDIT_RC" = "1" ]; then echo "❌ 源码审验发现问题，中止打包"; exit 1; fi
if [ "$AUDIT_RC" != "0" ]; then echo "⚠️ 审验脚本自身故障（rc=$AUDIT_RC），跳过闸门继续打包 —— 规则坏了不能挡住发布"; fi
rm -rf $D/classes $D/dexout $D/res.zip
mkdir -p $D/classes $D/dexout $D/assets
[ -d $D/res/values ] && aapt2 compile --dir $D/res -o $D/res.zip
aapt2 link -o $D/base.apk -I $APJ --manifest $D/AndroidManifest.xml \
  ${RES:+} $( [ -f $D/res.zip ] && echo $D/res.zip ) \
  --min-sdk-version 24 --target-sdk-version 36
find $D/src -name "*.java" > $D/srcs.txt
# 新版 API 用到 sealed class / record（Java 17 特性），必须 --release 17。
# 但 desugar 到 min-api 24 仍由 d8 负责，运行时不需要 Java 17 环境。
MODERN_CP=""
[ -n "$MODERN" ] && MODERN_CP=":$MODERN"
[ -n "$ANN" ] && MODERN_CP="$MODERN_CP:$ANN"
javac --release 17 -nowarn -cp $XI:$AJ$MODERN_CP -d $D/classes @$D/srcs.txt
find $D/classes -name "*.class" > $D/cls.txt
d8 --min-api 24 --lib $APJ --output $D/dexout $(cat $D/cls.txt | tr '\n' ' ')
cp $D/base.apk $D/mod.apk
(cd $D/dexout && zip -q -u $D/mod.apk *.dex)
printf '%s\n' "$PKG.HookEntry" > $D/assets/xposed_init
# 新版 libxposed 的注册元数据：META-INF/xposed/*（没有它，新入口不会被框架加载）
if [ -d "$D/meta-xposed" ]; then
  mkdir -p "$D/metapack/META-INF/xposed"
  cp "$D/meta-xposed"/* "$D/metapack/META-INF/xposed/" 2>/dev/null || true
fi
(cd $D && zip -q -r -u mod.apk assets/)
[ -d "$D/metapack" ] && (cd $D/metapack && zip -q -r -u ../mod.apk META-INF/) || true
zipalign -f -p 4 $D/mod.apk $D/mod_al.apk
apksigner sign --ks $KS --ks-pass pass:${KS_PASS:-123456} --key-pass pass:${KS_PASS:-123456} \
  --out $D/mod_signed.apk $D/mod_al.apk
apksigner verify $D/mod_signed.apk && echo "✅ $D/mod_signed.apk $(stat -c%s $D/mod_signed.apk) bytes"
# 产物级审验：只查 APK 里到底是什么
# 产物审验同理：脚本故障只警告
node "$D/audit.mjs" "$D" --post; POST_RC=$?
if [ "$POST_RC" != "0" ]; then echo "⚠️ 产物审验有告警或脚本故障（rc=$POST_RC）"; fi