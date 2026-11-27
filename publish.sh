#!/bin/bash
# 一条命令发布：主仓库 + LSPosed 组织仓库
# 用法：
#   ./publish.sh            构建 + 推主仓库 + 发两处 Release
#   ./publish.sh --no-build 跳过构建（APK 已就绪）
#   ./publish.sh --dry-run  只打印要做什么
set -e
cd "$(dirname "$0")"

MAIN_REPO="hehe15855/DSHGoXpandh"
LSP_REPO="Xposed-Modules-Repo/io.github.hehe15855.dshxpandh"
# DRY 默认取环境变量，没有才是 0 —— 以前写死 DRY=0 会把 DRY=1 bash publish.sh 覆盖掉，
# 导致「只想预览」却真的建了 Release、传了 APK（2026-11-27 踩过）。
DRY=${DRY:-0}; BUILD=${BUILD:-1}
for a in "$@"; do
  [ "$a" = "--dry-run" ] && DRY=1
  [ "$a" = "--no-build" ] && BUILD=0
done

VER=$(grep -oP 'android:versionName="\K[^"]+' AndroidManifest.xml | head -1)
VC=$(grep -oP 'android:versionCode="\K[^"]+' AndroidManifest.xml | head -1)
[ -z "$VER" ] && { echo "读不到 versionName"; exit 1; }
APK="DSHxpandh-$VER.apk"
echo "版本: $VER (versionCode $VC)   APK: $APK"

if [ "$BUILD" = "1" ]; then
  echo "[1/4] 构建…"
  ./build.sh 2>&1 | tail -3
fi
[ -f mod_signed.apk ] || { echo "没有 mod_signed.apk"; exit 1; }
cp -f mod_signed.apk "$APK"

export MAIN_REPO LSP_REPO VER VC APK DRY

if [ "$DRY" = "1" ]; then
  echo "[DRY-RUN] 将执行："
  echo "  - git push origin HEAD"
  echo "  - 主仓库 Release: tag v$VER  挂 $APK"
  echo "  - 同步 .lsp-repo/{README.md,SUMMARY} → $LSP_REPO"
  echo "  - LSP 仓库 Release: tag $VC-$VER  挂 $APK"
  exit 0
fi

echo "[2/4] 推主仓库…"
git push origin HEAD 2>&1 | tail -2 || true

echo "[3/4] 创建 Release + 同步文档…"
python3 - <<'PYEOF'
import json, os, urllib.request, base64, re

MAIN_REPO = os.environ["MAIN_REPO"]
LSP_REPO  = os.environ["LSP_REPO"]
VER       = os.environ["VER"]
VC        = os.environ["VC"]
APK       = os.environ["APK"]
TAG_LSP   = VC + "-" + VER

creds = open(os.path.expanduser("~/.git-credentials")).read().strip()
m = re.match(r"https://([^:@]+):([^@]+)@", creds)
if not m:
    raise SystemExit("读不到 token")
TOKEN = m.group(2)

H = {"Authorization": "Bearer " + TOKEN, "Accept": "application/vnd.github+json",
     "User-Agent": "dsh-publish"}

def api(method, path, body=None, raw=None, ctype="application/json"):
    url = path if path.startswith("http") else "https://api.github.com" + path
    h = dict(H)
    data = None
    if raw is not None:
        data = raw; h["Content-Type"] = ctype
    elif body is not None:
        data = json.dumps(body).encode(); h["Content-Type"] = ctype
    req = urllib.request.Request(url, data=data, method=method, headers=h)
    try:
        return json.load(urllib.request.urlopen(req, timeout=120)), None
    except Exception as e:
        try:
            return None, json.loads(e.read().decode())
        except Exception:
            return None, str(e)

def ensure_release(repo, tag, name, notes, apk_path, apk_name):
    r, err = api("GET", "/repos/" + repo + "/releases/tags/" + tag)
    if r and r.get("id"):
        print("  [" + repo + "] Release " + tag + " 已存在，复用")
        rid = r["id"]
    else:
        target = "main" if "Xposed-Modules-Repo" in repo else "master"
        r, err = api("POST", "/repos/" + repo + "/releases",
                     {"tag_name": tag, "target_commitish": target, "name": name,
                      "body": notes, "draft": False, "prerelease": False})
        if not r:
            print("  [" + repo + "] 建 Release 失败: " + str(err)); return False
        rid = r["id"]
        print("  [" + repo + "] Release " + tag + " 已创建")
    r, _ = api("GET", "/repos/" + repo + "/releases/" + str(rid) + "/assets")
    if isinstance(r, list):
        for a in r:
            if a.get("name") == apk_name:
                api("DELETE", "/repos/" + repo + "/releases/assets/" + str(a["id"]))
    data = open(apk_path, "rb").read()
    url = "https://uploads.github.com/repos/" + repo + "/releases/" + str(rid) + "/assets?name=" + apk_name
    try:
        a, _ = api("POST", url, raw=data, ctype="application/vnd.android.package-archive")
        if a:
            print("  [" + repo + "] APK 已上传 " + a["name"] + " (" + str(a["size"]) + "B)")
            return True
        print("  [" + repo + "] APK 上传失败"); return False
    except Exception as e:
        print("  [" + repo + "] APK 上传异常: " + str(e)); return False

NOTES = "版本 " + VER + "（versionCode " + VC + "）"

ensure_release(MAIN_REPO, "v" + VER, VER, NOTES, APK, APK)

for fn in ("README.md", "SUMMARY"):
    p = os.path.join(".lsp-repo", fn)
    if not os.path.exists(p):
        continue
    content = open(p, encoding="utf-8").read()
    cur, _ = api("GET", "/repos/" + LSP_REPO + "/contents/" + fn)
    body = {"message": "docs: update " + fn,
            "content": base64.b64encode(content.encode()).decode(), "branch": "main"}
    if isinstance(cur, dict) and cur.get("sha"):
        body["sha"] = cur["sha"]
    r, err = api("PUT", "/repos/" + LSP_REPO + "/contents/" + fn, body)
    print("  [LSP] " + fn + " " + ("已同步" if r else "失败 " + str(err)))

ensure_release(LSP_REPO, TAG_LSP, VER, NOTES, APK, APK)

print()
print("完成：")
print("  主仓库: https://github.com/" + MAIN_REPO + "/releases/tag/v" + VER)
print("  LSP:    https://github.com/" + LSP_REPO + "/releases/tag/" + TAG_LSP)
PYEOF

echo "[4/4] 清理临时 APK"
rm -f "$APK"
echo "完成。LSP 侧约 3 个工作日内出现在 modules.lsposed.org"
