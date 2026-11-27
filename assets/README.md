# DSHxpandh

[![LSPosed](https://img.shields.io/badge/LSPosed-API%2093%2B-blue)](https://github.com/LSPosed/LSPosed)
[![Android](https://img.shields.io/badge/Android-8.0%20~%2016-green)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Release](https://img.shields.io/github/v/release/hehe15855/DSHGoXpandh)](https://github.com/hehe15855/DSHGoXpandh/releases/latest)

> **在别的 App 里开一扇窗。** 看得见它内部发生了什么，也能动手改——不用改包、不用重签，装一次常驻生效。

Android 上想改一个 App 的行为，常见做法是拆包、改 smali、重签名。慢，而且装回去就是另一个包了。

这个模块换了条路：挂在 LSPosed 上，在**目标 App 运行的时候**动手。抓加解密明文、改方法参数、改返回值、往网页里注入 JS、读浏览器 Cookie、调内部方法——配置写好，App 每次启动自动生效。

**它可以接入 DSH，把手工配置变成对话。** 不接也能用：配方和脚本都是纯文本，手写一样生效；接上之后，助手能自己读能力清单、配配方、下脚本，你说「把这个弹窗去掉」，它去做。

---

## 能做什么

六种模式，都是**运行时**生效，配好即用：

| 模式 | 干什么 | 典型场景 |
|---|---|---|
| **log** 抓明文 | 记下方法的入参和返回值 | 看加密前原文、解密后明文、API 返回 |
| **args** 改参数 | 替换方法收到的值 | 试不同输入，看 App 怎么反应 |
| **return** 改结果 | 让方法固定返回你的值 | 让某个判断永远成立 / 不成立 |
| **js** 改网页 | 往 WebView 注入脚本 | 改样式、去弹窗、自动填表、读 DOM |
| **cookie** 取登录态 | 读 Cookie（含 HttpOnly） | 拿来做后续请求 |
| **call** 调方法 | 直接调 App 内部方法 | 主动要数据，不用等它触发 |

自带一套配方（改设备 ID、伪装定位、关 SSL Pinning、关日志……）和一套网页脚本（去广告、倍速、解除复制限制……），自定义页可单独开关。改脚本不用重装模块，3 秒内自动重注入。

## 网页注入支持三种内核

这是相对同类工具的主要差别——很多模块只支持标准 WebView，遇到自带浏览器就失效。

| 内核 | 常见场景 |
|---|---|
| 标准 `android.webkit.WebView` | 大多数 App 内置网页 |
| 腾讯 XWeb | 微信、QQ |
| Chromium `AwContents` | OPPO / Chrome / 夸克 / UC 等自带浏览器 |

注入时机是页面加载 + 每 12 秒兜底重注入，页面重渲染后效果不会丢。

## 能力分界：哪件事该谁做

模块只管 **Java 层**。判断错了会白忙——Java 层的事用反汇编改文件是弯路，native 的事配配方永远不生效。

### 三层，怎么认

| 层 | 是什么 | 怎么认 |
|---|---|---|
| **Java 层** | Java/Kotlin 编译成 dex，跑在 ART 上 | jadx 能反编译出类名方法名；堆栈里有 `com.xxx.Yyy.method(` |
| **native 层** | C/C++ 编译成 .so，CPU 直接跑 | jadx 里只看到 `native` 声明，没有方法体 |
| **WebView 层** | App 内嵌网页，跑 JS | 有 DOM、有 fetch，能用开发者工具的思路理解 |

### 五步判断

1. 是一个 **Java 方法**吗？→ 用**本模块**配配方
2. 是**网页里的东西**（DOM / fetch / localStorage）？→ 用**本模块**丢 .js
3. 是**磁盘上的文件**（数据库 / mmkv / prefs）？→ 都别用，**root 直接读**
4. 逻辑在 **.so 里**？→ 用 **rizin / Ghidra** 改二进制
5. 是要看/改**网络包**？→ 用 **tcpdump / mitmproxy**

### 一句话判据

> 这段逻辑能不能按「类名.方法名」找到？
> 能找到 → **本模块**；只在 .so 里 → **改二进制**；数据已经在文件里 → **root 读**，两者都不用。

### 模块做不到的

- native 内部的自校验、算法、常量（Java 层看不到这次调用）
- 改 APK 文件、永久生效的静态改造
- 原生界面 / Flutter / Canvas 游戏（不是 WebView 渲染的）
- 没在 LSPosed 里勾选过的 App

### 谁都做不到的

- 加固壳的 native 反调试：能试，成功率看壳的版本
- TEE / 硬件密钥参与校验：拿不到就没法伪造
- 服务端校验：本地改成什么样，上游不认照样拒

> 完整结构化版本在 `capabilities.json` 的 `boundary` 字段，AI 助手读那个就能自动选路。

## 密钥页：Cookie 离线解密

浏览器把登录态存在自己的 SQLite 库里，`encrypted_value` 是 AES-128-CBC 密文。模块的「密钥」页直接读出来解开，不抓包。

| 形态 | 含义 | 处理 |
|---|---|---|
| `v10` | AES-128-CBC，密钥 PBKDF2-SHA1("peanuts","saltysalt",1) | 解开 |
| 无前缀（`value` 列有值） | 明文，WebView 内核的库常见 | 直接读 |
| `v20` | App-Bound，密钥在系统服务里、绑定应用身份 | 解不开，界面如实说明 |

扫描不写死浏览器名单：`/data/data/*/app_chrome|app_webview|app_u4_webview/…/Cookies` 全扫，扫到什么解什么。结果可导出到控制目录下的 `cookies.json`。

## 隐身

默认开启。**主进程与子进程都装**（`:push` / `:sandbox` / `:downloads` 这些是检测器常驻的地方）。

分 18 个面、共 22 处检测点（下表按面归类；一个面可能挂多处 hook）：

| 面 | 拦的是什么 |
|---|---|
| **类名** | `loadClass` 撞到 Xposed / LSPosed / libxposed 任何类，抛 `ClassNotFoundException` |
| **系统属性** | `getProperty` / `SystemProperties.get` / `getInt` / `getBoolean` 含框架关键词的键返回空 |
| **环境变量** | `getenv(key)` 与无参 `getenv()`（返回整表那个）都把框架相关项摘掉 |
| **可疑路径** | `/data/adb/modules`、`lspd`、`xposedbridge.jar` 等，`exists` / `isFile` / `isDirectory` / `listFiles` 一律说没有 |
| **包列表** | `getInstalledPackages(AsUser)` / `getInstalledApplications(AsUser)` / `queryIntentActivities` 里没有模块 |
| **单包查询** | `getPackageInfo` / `getApplicationInfo`（含 `AsUser` 版）抛 `NameNotFoundException` |
| **进程与服务** | `getRunningAppProcesses` / `getRunningServices` / `getRunningTasks` 过滤掉模块 |
| **堆栈与反射** | `getStackTrace` / `getAllStackTraces` 去掉框架帧；`getDeclaredMethods` / `getFields` 去掉 `$xposed` 成员 |
| **注解** | `getAnnotations` / `getDeclaredAnnotations` 摘掉 xposed 相关注解 |
| **进程启动** | `Runtime.exec` 与 `ProcessBuilder.start` 都不许跑指向可疑路径的命令 |
| **文件读取** | NIO `Files.readAllBytes` / `Os.readlink` / `File.getCanonicalPath` 一并挡 |
| **调试器** | `isDebuggerConnected` / `waitingForDebugger` / `isNativeDebuggerConnected` 恒 false |
| **安装来源** | `getInstallerPackageName` 被问模块时回 `com.android.vending`（遮侧载痕迹） |
| **签名比对** | `checkSignatures` 涉及模块时返回 `SIGNATURE_NO_MATCH` |
| **调用来源** | `getCallingPackage` / `getCallingActivity` 不暴露模块 |
| **native 库** | `ClassLoader.loadLibrary` 加载 frida / xposed / lspd / substrate 时抛 `UnsatisfiedLinkError` |
| **ApplicationInfo** | `nativeLibraryDir` 含模块痕迹时置空 |
| **uid 反查** | `getPackagesForUid` 过滤掉模块 |

控制目录放 `stealth.off` 可关掉——但**诚实的边界**是：只能挡 Java 层。

native 侧直接读 `/proc/self/maps`、检查 ART 内存页属性、扫 vaddr 段这些，Java 层看不到也拦不住。
专门的检测工具（如 Holmes）从 native 走，仍能发现痕迹。要挡那些，得靠 Zygisk 模块或内核层。

## 安装

1. 下载 [Releases](https://github.com/hehe15855/DSHGoXpandh/releases/latest) 里的 APK
2. 装好后到 **LSPosed 管理器**启用本模块
3. 勾选要操作的目标 App
4. **重启那个 App**（不是重启手机）
5. 打开本模块设置页，选配方或写自定义规则

首次打开会引导授存储权限——不给也能进，但脚本和配方用不了（界面显示「受限模式」）。

### 配合 AI 助手用（可选）

模块会把能力清单和操作说明导出到**控制目录**（默认 `/sdcard/DSHxpandh/`）。助手读到 `DSH-GUIDE.md` 就知道全部接口：

```
我装了 DSHxpandh 模块，接口在 /sdcard/DSHxpandh/，先读 DSH-GUIDE.md
```

自带一个 DSH 插件（`dsh-plugin/`），装上后聊天框上方会有状态条和面板：

```bash
dsh plugin --profile web add /sdcard/DSHxpandh/dsh-plugin
```

装完重启一次引擎。不装插件也能用，只是要手动改文件。

## 兼容性

| 项 | 要求 |
|---|---|
| Android | 8.0（API 24）～ 16（API 36）；APK 声明 `minSdk 24` / `targetSdk 36` |
| LSPosed | 现代 API 93+；经典版 1.9.x 与 Zygisk 版 2.x 都支持 |
| Root | KernelSU / Magisk / APatch —— LSPosed 本身就要 root |
| 存储权限 | 「所有文件访问」（写控制目录）；不给也能进，但脚本与配方用不了 |
| 架构 | 不限：纯 Java，无 native 库，armeabi-v7a / arm64-v8a / x86_64 通用 |
| 已实测 | Android 15 + LSPosed 2.2.1 + KernelSU；其余版本按上表兼容，未逐一跑过 |

## 常见问题

**改了配置不生效？**
重启目标 App。只有控制目录里的开关（`.off` 文件）是热生效的。

**找不到要 hook 的类名？**
用 jadx 反编译看。类名被混淆过的话，按行为找（搜字符串常量、看堆栈）。

**为什么有的 App 完全没反应？**
没在 LSPosed 里勾选它。模块不会碰没勾选的进程。

**网页注入没效果？**
先确认那个页面是不是真的 WebView（不是原生控件、Flutter 或 Canvas 画的）。三种内核都支持，但原生 UI 改不了。

**更新后文档变了？**
控制目录的 `capabilities.json`、`DSH-GUIDE.md` 跟着 APK 版本走。你手改过的会保留，除非升了模块版本。

**模块能被检测到吗？**
能。隐身只挡 Java 层探测，专门的检测工具从 native 侧查仍能发现。

## 从源码构建

| 依赖 | 版本 / 说明 |
|---|---|
| JDK | 8 或更高（源码按 `--release 8` 编译） |
| Android SDK · platform | 含 `android.jar`，API 24 以上（默认找 `platform-36`） |
| Android SDK · build-tools | `aapt2`、`d8`、`zipalign`、`apksigner` |
| LSPosed API | `xapi.jar`（libxposed API），API 93+ |

```bash
export ANDROID_JAR=/path/to/platforms/android-36/android.jar
export XAPI_JAR=/path/to/xapi.jar
export KS_PASS=yourpass          # 可选，签名口令，默认 123456
bash build.sh
# 产物：mod_signed.apk
```

不设环境变量时，脚本按顺序自动探测：`$ANDROID_HOME/platforms/*`、`/usr/lib/android-sdk/platforms/*`、`~/Android/Sdk/platforms/*`；`xapi.jar` 找 `./xapi.jar`、`../xapi.jar`、`~/xapi.jar`。都找不到会直接报错退出，不会静默产出空壳。

资源编译必须用带 `resources.arsc` 的 SDK platform jar；高版本 robolectric 的 android.jar 只够 javac 用，喂给 `aapt2` 会失败。

签名用同目录的 `debug.ks`（不在仓库里，自己放一个，或设 `KEYSTORE` 指向别处）。

构建带两道审验闸门：源码审验（`audit.mjs --pre`）不过就中止，产物审验（`--post`）只告警。版本号自动从 Manifest 同步到 `HookEntry`、`module.prop`、`version.json`。

### 发布前核对

```bash
./verify-release.sh
```

重编一次源码，然后比对两头的 `classes.dex`：本地 `mod_signed.apk` 里那份、线上最新 Release 的 asset 里那份。三行结论：

1. **本地产物 = 当前源码** — 本地包是不是刚编出来的
2. **线上 Release = 当前源码** — 挂出去的是不是同一次构建
3. **版本号** — release tag 和 manifest 的 `versionName`

退出码 `0` 全绿 / `2` 有不一致 / `3` 取不到远端（取不到不算通过）。

写它是为了 2026-11-26 那个上午：v12.9.0 的 Release 挂的是旧代码的包，源码提交了却没重新打包。只比文件名和字节数看不出来（两边都是 135138 字节）。

**发布了源码却没重打包，和跳过一个构建步骤是同一类错**：坏的地方不在构建，在「以为发布完了」。

## 项目结构

```
src/…/HookEntry.java        目标进程里的 hook 逻辑（注入、隐身）
src/…/SettingsActivity.java 设置界面
src/…/DshxpandhProvider.java    跨进程配置通道
src/…/AppPickerDialog.java  应用选择器
src/…/ProbeService.java     探针服务
src/…/CookieVault.java      Cookie 解密（v10 / 明文 / v20 检测）
assets/scripts/             内置脚本（9 个）
assets/recipes/             内置配方（13 个）
assets/capabilities.json    能力清单 + 能力分界（助手读这个）
assets/DSH-GUIDE.md         给助手的接口说明
module.prop                 LSPosed / Magisk 模块信息
CHANGELOG.md                更新日志
LICENSE                     MIT
NOTES.md                    开发笔记（踩过的坑）
```

## 开发笔记

踩过的坑都在 [NOTES.md](NOTES.md)：三种 WebView 内核的差异、Chromium 的 `evaluateJavaScript` 传 null 会崩、隐身为啥必须排除模块自己、LSPosed 的 binder 连接为什么不能靠 killall 重建。

## 免责声明

仅供学习研究与自有设备调试使用。请勿用于未授权的设备或违反服务条款的场景。

## License

[MIT](LICENSE)