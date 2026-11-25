# DSHxpandh · Xposed hook 工具箱

装一个模块，给 App 打 **Java 层 hook**：抓明文、改参数、注网页脚本、读 Cookie、调内部方法。

## 它不是什么

- **不是抓包工具** —— 抓不到 TLS 密文。要抓包请用 tcpdump / Charles / mitmproxy。
- **不做 native 层** —— 用 C/C++ 写的逻辑碰不到，那要 Zygisk 模块。
- **不需要 root** —— 它是 LSPosed 模块，权限由 LSPosed 提供。

## 安装

1. 先有 Magisk / KernelSU，再装 **LSPosed**
2. 安装本模块 APK
3. LSPosed → 模块 → 启用 **DSHxpandh**
4. 作用域 → 勾选**目标 App**（只勾你要 hook 的那个）
5. **强制停止目标 App 再打开**（不重启不生效）

## 三步用起来

```
① 打开 DSHxpandh → 配方页 → 填目标包名（如 com.example.app）
② 点一个配方，例如「抓加密明文」
③ 强制停止目标 App → 打开 → 操作几下 → 去 LSPosed 日志看结果
```

## 六种模式

| 模式 | 作用 | 典型用途 |
|---|---|---|
| `log` | 记下入参和返回值 | **加密**：原文→密文；**解密**：密文→明文（能直接看到解密结果）|
| `args` | 改入参 | 假装另一台设备、改请求内容 |
| `return` | 改返回值 | 让某个判断恒真（已开通 / 非会员限制）|
| `js` | 往 WebView 页面注入脚本 | 改标题、自动填表、去弹窗 |
| `cookie` | 读 WebView 的 Cookie | 拿网页登录态 |
| `call` | 主动调静态方法 | 不用等触发，直接取值 |

## 十二个内置配方

| 配方 | 做什么 |
|---|---|
| `cipher-log` | 抓 `Cipher` / `Mac` 的**加密前原文和解密后明文** |
| `fake-ua` | 换 User-Agent |
| `fake-device-id` | 换设备 ID |
| `fake-android-id` | 换 Android ID |
| `fake-location` | 换定位 |
| `block-su` | 拦 su 调用（防检测）|
| `block-logs` | 屏蔽刷屏日志 |
| `log-webview-url` | 记网页加载的地址 |
| `fake-screen` | 看/改屏幕参数 |
| `ssl-pinning-off` | 关证书校验（**实验性**，多数 App 无效果）|
| `crypto-full` | 全链路：`Cipher.update`+`doFinal`、`Mac`、`MessageDigest`、`Signature`、`SecretKeySpec`（看密钥）|
| `crypto-base64` | 看 `Base64.encodeToString` / `decode` 前后（密文常以 Base64 传输）|

## 让别人用 DSH 接管（可选）

如果对方也用 DSH（AI agent），装完只要说一句：

> 我装了 DSHxpandh 模块，接口在 `/sdcard/DSH-Output/xhook/`，先读 `DSH-GUIDE.md`

DSH 会自己读到：

| 文件 | 内容 |
|---|---|
| `capabilities.json` | 全部能力：6 模式 / 12 配方 / 开关 / 9 条限制 |
| `recipes/*.json` | 每个配方的 hook 配置，可直接 cp 到 `Download/xhook/<包名>.json` |
| `status.json` | 版本与通道状态 |
| `mods/` | 系统里装了哪些 Zygisk 模块 |

**这些文件在你第一次打开设置页时自动导出**，不用手动准备。

## 控制目录

模块**自动选**目录，不用你配：

1. `/sdcard/DSH-Output/xhook/` —— 非空就用它（DSH 用户）
2. `/sdcard/xhook/` —— 否则自己建这个（独立用户）

设置页底部会显示当前用的是哪个。

| 文件 | 作用 |
|---|---|
| `off` | 存在即停 JS 注入（删掉恢复）|
| `stealth` | 存在即对目标 App 隐藏 Xposed 特征 |
| `classhide` | 存在即隐藏 Xposed 类名 |
| `abilities/<mode>.off` | 单独关掉某个模式 |
| `scripts/*.js` | 多脚本，`<名>.js.off` 表示停用 |
| `cmd.json` | 一次性下发命令 `{"ts":时间戳,"js":"代码"}` |

这些文件**改完即时生效**（1.5–2.5 秒轮询），不用重启。

## 配置格式

放 `/sdcard/Download/xhook/<包名>.json`：

```json
{
  "hooks": [
    { "class": "android.webkit.WebSettings",
      "method": "getUserAgentString",
      "mode": "return",
      "return_value": "MyUA" }
  ]
}
```

多个类一起 hook 用 `"classes": ["a.b.C", "a.b.D"]`。

## 限制（看清再用）

1. **只能动 Java 代码**，C/C++ 写的逻辑碰不到。
2. **只能 hook 已加载的类**；加壳 / 动态加载的要等它自己加载出来。
3. **页面不是标准 WebView 的**，`js` 和 `cookie` 用不了。
4. **只对 LSPosed 勾过作用域的 App 生效**，没勾的进程完全不动。
5. **改值只影响方法调用**，已编译进底层的逻辑改不了。
6. 日志有上限：速度看配方的 `sample`/`window_ms`（默认每秒 5 次），总量看 `max_logs`（默认 1000）—— 两者都在配方 json 里，可以自己调。
7. **改配置后要重启目标 App**；只有控制目录里的开关是热生效的。
8. 模块痕迹藏不掉，专门的检测工具能发现。

## 常见问题

**套了配方没效果？**
→ 90% 是没重启目标 App。强制停止再打开。

**看不到日志？**
→ LSPosed 管理器里把日志级别调成 info；模块日志前缀是 `[xhook]`。

**LSPosed 里显示「已启用」但没反应？**
→ 检查作用域勾的是不是目标 App；或者装完模块后 app 路径变了（重装过），需要在 LSPosed 里关掉模块再打开。

**某个 App 完全不动？**
→ 它可能把关键逻辑放在 native 层了，Java hook 够不到。

## 版本

v4.9 / versionCode 47
