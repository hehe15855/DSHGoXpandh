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

## 脚本从哪来、删了能不能回来

两个来源，**同名时以你自己放的为准**：

| 来源 | 位置 | 能不能改内容 | 删了能回来吗 |
|---|---|---|---|
| **内置** | 打包在 APK 里，界面上带「内置」标记 | 改不了，只能开关或移除 | **能** —— 文件还在 APK 里，点「恢复被移除的内置脚本」就回来 |
| **本地** | `/sdcard/.../xhook/scripts/` | 随便改、删、加 | **不能** —— 文件真删了，没了就是没了 |

**本地脚本删除要点两下确认**（第一下按钮变成「确认删」），因为它不可恢复。

想改内置脚本的内容：把同名文件放进 `scripts/` 目录覆盖它，同名时以你放的为准。

---

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

## 网页注入是怎么工作的

模块会在三种 WebView 内核里找注入口，只要目标 App 在 LSPosed 作用域里：

| 内核 | 典型 App | 抓取方式 |
|---|---|---|
| `android.webkit.WebView` | 大多数工具类、阅读类 | hook 构造函数 |
| 腾讯 XWeb | 微信、QQ | hook 构造函数（接口同构） |
| Chromium `AwContents` | OPPO/Chrome/夸克/UC 等 | onDraw 记录正在绘制的实例 |

**注入节奏**：页面每次重绘注入一次（限流 800ms），另有 3 秒兜底。

**所以脚本必须写成幂等的** —— 同一个脚本会被执行很多次：

```js
(function () {
  // ✅ 用 id 判断，重复执行不会加第二份
  if (document.getElementById('my-style')) return;
  var s = document.createElement('style');
  s.id = 'my-style';
  s.textContent = 'a{color:red}';
  document.head.appendChild(s);

  // ✅ 页面重渲染会冲掉，用 MutationObserver 保活
  new MutationObserver(function () {
    if (!document.getElementById('my-style')) document.head.appendChild(s);
  }).observe(document.documentElement, {childList: true, subtree: true});
})();
```

**别做的**：`alert()`、无判断地 `location.reload()`、每次执行都发网络请求 —— 会变成每 3 秒一次。

**改不了的**：原生界面（不是 WebView 渲染的页面）。

## 版本

以 App 里显示的为准（首页标题旁），或读控制目录下 `status.json` 的 `version` 字段

## 开发与维护注意

这几条是真机上踩出来的，改代码前先看一眼。

### 隐身 hook 必须排除模块自己的进程

```java
if ("<模块包名>".equals(pkg)) return;
```

隐身会拦 `Runtime.exec`（防 root 探测）。如果不排除自己，模块读配置时跑 `su` 会被自己的 hook 挡掉，
界面就会显示「没有 Root 权限」，而 SukiSU 那边明明已经授权 —— 现象是两边说法矛盾。
同样要排除的还有 `File.listFiles`、`File.exists`、`ClassLoader.loadClass`、`Class.getDeclaredMethods`。

### 不要 killall lspd

`lspd` 是 LSPosed 的 root 守护进程，父进程是 init。杀掉之后它和 zygote 的 binder bridge 就断了，
新起的进程连不上旧的 bridge，**只能重启手机恢复**。所有模块都会失效（不只是本模块）。

改作用域的正确做法：写 `modules_config.db` → 重启手机 → 生效。不要在运行时反复重启 daemon。

### LSPosed 管理器可能根本没装

`pm list packages | grep lsposed` 查不到 `org.lsposed.manager` 时，管理器就是没装。
装回来：

```bash
pm install -r /data/adb/modules/zygisk_lsposed/manager.apk
```

### 改包名要动哪些地方

想用自己的包名重新发布，一共 4 处：

| 位置 | 改什么 |
|---|---|
| `AndroidManifest.xml` | `package="你的包名"` |
| `AndroidManifest.xml` | provider 的 `android:authorities="你的包名.cfg"` |
| `src/.../HookEntry.java` | `private static final String SELF = "你的包名"` |
| `src/.../XhookProvider.java` | `AUTHORITY = "你的包名.cfg"` |

另外还要把源码目录（`src/<把包名的点换成斜杠>/`）一起移过去，所有 `.java` 的 `package` 声明跟着改。

**不用改的**：
- `build.sh` —— 它从 Manifest 读包名，`xposed_init` 自动跟随
- `dsh-plugin/lib/index.js` —— 控制目录是探测的，不认包名
- 控制目录路径 —— 动态选

### 注入要覆盖三种 WebView 内核

| 内核 | 典型 App | 抓取方式 |
|---|---|---|
| `android.webkit.WebView` | 大多数工具/阅读类 | hook 构造函数 |
| 腾讯 XWeb | 微信、QQ | hook 构造函数（接口同构） |
| Chromium `AwContents` | OPPO/Chrome/夸克/UC | `onDraw` 记录正在绘制的实例 |

两个坑：

1. **只 hook `loadUrl` 会漏** —— `loadData` 加载的页面抓不到，要 hook 构造函数
2. **`evaluateJavaScript(js, null)` 会让 Chromium native 崩** —— 回调必须传一个空实现，不能传 null

### 配置读取依赖模块 App 进程

ContentProvider 跑在模块 App 里，App 被系统回收后 provider 就没了。现在的兜底：

- 脚本走 `/sdcard/.../scripts/` 文件回落（一直能用）
- 隐身开关读不到时**按开处理**（宁可多隐一层）
