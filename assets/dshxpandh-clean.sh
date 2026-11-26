#!/bin/bash
# DSHxpandh 控制目录：单项删除 / 一键清除 / 备份 / 还原
# 铁律：任何删除动作前先备份；还原前也备份当前（防止还原后更后悔）。
set -u
# 控制目录自动探测：与模块的 pickRoot 保持一致（DSH 目录非空优先，否则用通用目录）
pick_ctrl() {
    local d=/sdcard/DSHxpandh
    if [ -d "$d" ] && [ -n "$(ls -A "$d" 2>/dev/null)" ]; then printf '%s' "$d"; return; fi
    d=/sdcard/DSHxpandh
    if [ -d "$d" ]; then printf '%s' "$d"; return; fi
    printf '%s' /sdcard/DSHxpandh
}
CTRL=$(pick_ctrl)
BAK=$(dirname "$CTRL")/DSHxpandh-backups
STAMP=$(date +%Y%m%d-%H%M%S)

# 只往 stdout 输出最终路径；所有提示走 stderr，避免被 $(...) 捕获污染。
do_backup() {
    if [ ! -d "$CTRL" ]; then echo '控制目录不存在' >&2; return 1; fi
    mkdir -p "$BAK" 2>/dev/null || { echo '建备份目录失败' >&2; return 1; }
    local n=0
    if [ -d "$BAK" ]; then
        n=$(ls -1 "$BAK" 2>/dev/null | grep -c '^bk-' 2>/dev/null || true)
        case "$n" in ''|*[!0-9]*) n=0 ;; esac
    fi
    local dir="$BAK/bk-$STAMP-$n"
    if ! cp -a "$CTRL" "$dir" 2>/dev/null; then
        echo "备份失败（目标 $dir）" >&2; return 1
    fi
    printf '%s' "$dir"
}

list_items() {
    echo '可单独删除的项：'
    echo '  off              网页脚本总闸（存在=停）'
    echo '  stealth          隐身开关（存在=开）'
    echo '  classhide        类名隐藏（存在=开）'
    echo '  cmd.json         一次性下发命令'
    echo '  abilities/<x>    单项能力开关，x=log|args|return|js|cookie|call'
    echo '  scripts/<x.js>   单个网页脚本'
    echo '  recipes/         导出的配方目录'
    echo '  dsh-plugin/      内嵌的 DSH 插件'
    echo '  （capabilities.json / DSH-GUIDE.md / status.json 会自动重建，删了没意义）'
}

case "${1:-}" in
  backup)
    p=$(do_backup) || exit 1
    echo "已备份到: $p"
    ;;
  list)
    echo "备份列表（$BAK）："
    if [ -d "$BAK" ]; then ls -1 "$BAK" 2>/dev/null | grep '^bk-' | sed 's/^/  /' || true; fi
    echo ''; list_items
    ;;
  rm)
    t="${2:-}"
    if [ -z "$t" ]; then list_items; exit 1; fi
    p="$CTRL/$t"
    if [ ! -e "$p" ]; then echo "不存在: $p"; exit 1; fi
    d=$(do_backup) || exit 1
    rm -rf "$p"
    echo "已删除: $t"
    echo "★ 要还原: $0 restore $(basename "$d")"
    ;;
  clear)
    if [ ! -d "$CTRL" ]; then echo '控制目录不存在'; exit 1; fi
    echo "准备清空 $CTRL（当前 $(ls -1 "$CTRL" 2>/dev/null | wc -l) 项）"
    d=$(do_backup) || exit 1
    echo "已自动备份到: $d"
    find "$CTRL" -mindepth 1 -delete 2>/dev/null
    echo '已清空（模块下次打开设置页会自动重建必要文件）'
    echo ''
    echo '★ 要还原就执行：'
    echo "  $0 restore $(basename "$d")"
    ;;
  restore)
    t="${2:-}"
    if [ -z "$t" ]; then
        echo "用法: $0 restore <备份名>"; ls -1 "$BAK" 2>/dev/null | grep '^bk-' | sed 's/^/  /' || true; exit 1
    fi
    src="$BAK/$t"
    if [ ! -d "$src" ]; then echo "备份不存在: $src"; exit 1; fi
    if [ -d "$CTRL" ] && [ -n "$(ls -A "$CTRL" 2>/dev/null)" ]; then
        pre=$(do_backup) || exit 1
        echo "还原前已备份当前状态: $pre"
    fi
    find "$CTRL" -mindepth 1 -delete 2>/dev/null
    cp -a "$src/." "$CTRL/" 2>/dev/null || { echo '还原失败'; exit 1; }
    echo "已还原: $t"
    ;;
  show)
    t="${2:-}"
    if [ -z "$t" ]; then echo "用法: $0 show <备份名>"; exit 1; fi
    find "$BAK/$t" -maxdepth 2 2>/dev/null | sed "s#$BAK/$t#  #" | head -30
    ;;
  *)
    echo "用法: $0 <命令>"
    echo '  list                列出备份与可删项'
    echo '  rm <项>             删单项（自动备份）'
    echo '  clear               一键清空（自动备份 + 打印还原命令）'
    echo '  backup              只备份'
    echo '  restore <备份名>    还原（还原前也会备份当前）'
    echo '  show <备份名>       看备份里有什么'
    exit 1
    ;;
esac
