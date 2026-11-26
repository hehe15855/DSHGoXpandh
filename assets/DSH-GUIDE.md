# 让 DSH 接管 DSHxpandh

## 你只要说这一句

> 我装了 DSHxpandh 模块，接口在 `/sdcard/DSHxpandh/`，先读 `DSH-GUIDE.md`

DSH 读完这个文件就知道全部接口，不需要你解释任何细节。

> **路径说明**：控制目录固定为 `/sdcard/DSHxpandh/`，不存在时模块会自动创建；该位置不可用时退回 `/sdcard/Download/DSHxpandh/`。实际路径以 App「设置 → 文件」页显示的为准。

> ## ⚠️ 装完或改完插件：重启一次引擎
>
> 装好插件后**重启一次 DSH 引擎**，`dshxpandh_status` 四个工具就注册进来了，当前会话下一轮就能用。
> 重启后仍然没有，去 `dsh-app/diagnostics.md` 的 appErrors 段看有没有 `cannot get property "tools" without inject`（说明插件没声明 `inject`）。

## 模块自带 DSH 插件（推荐）

模块里已经带了对接插件，导出的位置：

```
/sdcard/DSHxpandh/dsh-plugin/
├── package.json
├── cordis.patch.yml
└── lib/index.js
```

装法（**只有 dsh-go 手机版支持，网页版 dsh 用不了**）：

```bash
dsh plugin --profile web add /sdcard/DSHxpandh/dsh-plugin
```

装完重启 DSH，App 里就会出现：

- **状态条**：聊天框上方显示「DSHxpandh 版本 开/关」
- **面板**：版本、通道、6 项能力 ✓✗、脚本列表、已配置包名
- **按钮**：开通道 / 脚本全开 / 脚本全关

不装插件也能用 —— 下面这些文件照样能读，只是要手动操作。

## DSH 会自己读到什么

| 文件 | 内容 |
|---|---|
| `capabilities.json` | 全部能力：6 种模式、13 个配方、开关、限制 |
| `recipes/*.json` | 13 个配方各自的 hook 配置（可直接套用）|
| `status.json` | 当前版本、通道开关状态、注入总开关 |

## DSH 能替你做的四件事

### 1. 套配方（最常用）

```bash
cp /sdcard/DSHxpandh/recipes/cipher-log.json \
   /sdcard/Download/DSHxpandh/<目标包名>.json
```

然后**重启目标 App**。

配方清单看 `capabilities.json` 的 `recipes` 数组 —— 每个都有 `when` 说明什么时候用。

### 2. 开关网页脚本通道

```bash
touch /sdcard/DSHxpandh/off     # 停
rm /sdcard/DSHxpandh/off        # 开
```

热生效，不用重启。

### 3. 单独关掉某个能力

```bash
mkdir -p /sdcard/DSHxpandh/abilities
touch /sdcard/DSHxpandh/abilities/js.off   # 关掉 js
rm /sdcard/DSHxpandh/abilities/js.off      # 开回来
```

可用 key：`log` `args` `return` `js` `cookie` `call`（见 capabilities.json 的 modes）

### 4. 下发一次性命令

```bash
echo '{"ts": '$(date +%s%3N)', "js": "document.title=\"hi\""}' > \
  /sdcard/DSHxpandh/cmd.json
```

`ts` 必须比上次大，模块才认。

## 写注入脚本时必须遵守

脚本会被**反复执行**（页面每次重绘 + 每 3 秒兜底），所以必须幂等：

```js
(function () {
  if (document.getElementById('my-style')) return;   // 已有就跳过
  var s = document.createElement('style');
  s.id = 'my-style';
  s.textContent = 'a{color:red}';
  document.head.appendChild(s);
  new MutationObserver(function () {
    if (!document.getElementById('my-style')) document.head.appendChild(s);   // 被冲掉就补
  }).observe(document.documentElement, {childList: true, subtree: true});
})();
```

**禁止**：`alert()`、无判断的 `location.reload()`、每次执行都发网络请求。

**支持的内核**：标准 WebView / 腾讯 XWeb / Chromium(AwContents) —— 三种都覆盖。
**不支持的**：原生界面（不是 WebView 渲染的页面改不了）。

## 写作须知

- **改配置要重启目标 App**；只有控制目录里的开关是热生效的
- 模块读配方时按 `<包名>.json` 找，包名填错就不生效
- 具体做不到什么，看 `capabilities.json` 的 `limits`（9 条）
- 日志在 LSPosed 管理器里看，前缀 `core`

## 如果 DSH 说找不到文件

先让它打开模块设置页一次 —— 这样 `capabilities.json` 和 `recipes/` 才会被导出到这个目录。
