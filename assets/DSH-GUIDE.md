# 接入 DSH：把 DSHxpandh 交给助手操作

> 这一步是**可选**的。DSHxpandh 本身是个独立的 LSPosed 模块——配方（`recipes/*.json`）和脚本（`scripts/*.js`）都是纯文本，手写一样生效，不需要 DSH 在场。
> 接上 DSH 的好处是把「翻文档、写 JSON、推脚本」变成一句话。

## 你只要说这一句

> 我装了 DSHxpandh 模块，接口在 `/sdcard/DSHxpandh/`，先读 `DSH-GUIDE.md`

DSH 读完这个文件就知道全部接口，不需要你解释任何细节。

> **路径说明**：控制目录固定为 `/sdcard/DSHxpandh/`，不存在时模块会自动创建。旧的 `/sdcard/Download/DSHxpandh/` 已废弃（两个目录并存会让模块和 DSH 各写一边，出现「刚写的文件读不到」）。实际路径以 App「设置 → 文件」页显示的为准。

> ## ⚠️ 装完或改完插件：重启一次引擎
>
> 装好插件后**重启一次 DSH 引擎**，四个工具（`dshxpandh_status` / `_script` / `_inject` / `_logs`）就注册进来了，当前会话下一轮就能用。
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
   /sdcard/DSHxpandh/user/<目标包名>.json
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

**用工具，别手写文件**：

```
dshxpandh_inject { pkg: "com.example.app", js: "document.title=\"hi\"" }
```

有 root 时它写进目标 App 私有目录（隐身开着也能用）；没 root 走控制目录的共享文件。

> 共享文件名不是 `cmd.json`：是 `c-<包名 SHA1 前 8 位>.bin`，内容还要 XOR+base64 混淆（由工具负责）。
> 手写那个文件基本不会对，用工具就行。

## 写注入脚本时必须遵守

脚本会被**反复执行**（页面每次重绘 + 2.5 秒轮询兜底），所以必须幂等：

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


## 能力分界：哪件事该谁做

模块只管 **Java 层**。判断错了会白忙——Java 层的事用 rizin 改文件是弯路，native 的事配配方永远不生效。

### 先认清三层

| 层 | 是什么 | 怎么认 |
|---|---|---|
| **Java 层** | Java/Kotlin 编译成 dex，跑在 ART 上 | jadx 能反编译出类名方法名；堆栈里有 `com.xxx.Yyy.method(` |
| **native 层** | C/C++ 编译成 .so，CPU 直接跑 | jadx 里只看到 `native` 声明，没有方法体 |
| **WebView 层** | App 内嵌网页，跑 JS | 有 DOM、有 fetch，能用开发者工具的思路理解 |

### 五步判断（照着走）

1. 是一个 **Java 方法**吗？→ 是，用**模块**配配方
2. 是**网页里的东西**（DOM/fetch/localStorage）？→ 是，用**模块**丢 .js
3. 是**磁盘上的文件**（数据库/mmkv/prefs）？→ 都别用，**root 直接读**
4. 逻辑在 **.so 里**？→ 用 **shell**，rizin/Ghidra 改指令
5. 是要看/改**网络包**？→ 用 **shell**，tcpdump/mitmproxy

### 对照表

| 你要做的事 | 用哪个 | 为什么 |
|---|---|---|
| 让某个判断固定成立 | 模块 | Java 层，改返回值一行搞定 |
| 看加密前的原文 | 模块 | hook Cipher/Mac/MessageDigest 记入参 |
| 改网页样子 / 去弹窗 | 模块 | .js 三秒生效 |
| 读 App 存的某个值 | shell(root) | 文件里就有，不用注入 |
| 改 .so 里的算法 | shell | 模块看不到 native |
| 看 App 发了什么请求 | shell | tcpdump/mitmproxy 更直接 |
| 去掉 APK 里的检测代码 | shell | 要重打包 |
| 绕过反 hook 检测 | 模块 | 已写好，且比外部注入隐蔽 |

### 各自的硬边界

**模块做不到**：

- native 内部的自校验、算法、常量（Java 层看不到这次调用）
- 改 APK 文件、永久生效的静态改造
- 原生界面 / Flutter / Canvas 游戏（不是 WebView 渲染的）
- 没在 LSPosed 里勾选过的 App

**三条谁都做不到**：

- 加固壳的 native 反调试：能试，成功率看壳的版本
- TEE / 硬件密钥参与校验：拿不到就没法伪造
- 服务端校验：本地改成什么样，上游不认照样拒

### 一句话判断

> 这段逻辑能不能按「类名.方法名」找到？
> 能找到 → **模块**；只在 .so 里 → **shell**；数据已经在文件里 → **root 读**，两者都不用。

> 完整结构化版本在 `capabilities.json` 的 `boundary` 字段，DSH 读那个就能自动选路。

