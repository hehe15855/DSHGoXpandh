# DSHGoXpandh

[![LSPosed](https://img.shields.io/badge/LSPosed-API%2093%2B-blue)](https://github.com/LSPosed/LSPosed)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

> 给 DSH Go 做的扩展模块。DSH Go 是跑在手机里的 agent，这个模块给它在别的 App 里开一扇窗：能看见 App 内部发生了什么，也能动手改。

DSH Go 本身跑在手机里，但它看不见别的 App 内部：微信发了什么请求、浏览器页面长什么样、某个按钮背后的值是什么。

这个模块补上这一块。它挂在 LSPosed 上，把目标 App 的运行时能力**开一个口子**给 DSH Go：

- DSH Go 说「抓一下这个 App 的请求明文」→ 模块记录方法调用，写到控制目录
- DSH Go 说「把页面里那个弹窗去掉」→ 模块往 WebView 注入 JS
- DSH Go 说「换个设备 ID 试试」→ 模块替换返回值

**hook 是手段，让 DSH Go 能干成事才是目的。**

---

## 它给 DSH Go 提供什么

| 能力 | DSH Go 能用来做什么 |
|---|---|
| **log** 抓明文 | 看 App 请求的原文、加密前后的内容、API 返回 |
| **args** 改参数 | 试不同的输入看 App 怎么反应 |
| **return** 改结果 | 让某个判断成立/不成立，测分支逻辑 |
| **js** 改网页 | 改页面内容、自动填表、去弹窗、读 DOM |
| **cookie** 取登录态 | 拿网页 Cookie 做后续请求 |
| **call** 调方法 | 直接问 App 内部要数据，不用等它自己触发 |

## 怎么接

模块把能力清单和操作说明导出到**控制目录**——默认 `/sdcard/xhook/`；如果你本来就有 `/sdcard/DSH-Output/xhook/`，会沿用它；两个都不行退回 `/sdcard/Download/xhook/`。DSH Go 读到 `DSH-GUIDE.md` 就知道全部接口，然后能自己完成这些事：

```
「我装了 DSHGoXpandh，接口在 /sdcard/xhook/，先读 DSH-GUIDE.md」
```

也自带一个 DSH 插件（`dsh-plugin/`），装上后聊天框上方会出现状态条和面板。

## 网页注入支持三种内核

这是相对同类工具的主要差别——很多模块只支持标准 WebView，遇到自带浏览器就失效。

| 内核 | 常见场景 |
|---|---|
| 标准 `android.webkit.WebView` | 大多数 App 内置网页 |
| 腾讯 XWeb | 微信、QQ |
| Chromium `AwContents` | OPPO / Chrome / 夸克 / UC 等自带浏览器 |

注入时机是**页面每次重绘 + 每 3 秒兜底**，页面重渲染后效果不会丢。

## 密钥页：Cookie 离线解密

浏览器把登录态存在自己的 SQLite 库里，`encrypted_value` 是 AES-128-CBC 密文。模块的「密钥」页直接读出来解开，不装插件、不抓包。

| 形态 | 含义 | 处理 |
|---|---|---|
| `v10` | AES-128-CBC，密钥 PBKDF2-SHA1("peanuts","saltysalt",1) | 解开 |
| 无前缀（`value` 列有值） | 明文，WebView 内核的库常见 | 直接读 |
| `v20` | App-Bound，密钥在系统服务里、绑定应用身份 | 解不开，界面如实说明 |

扫描不写死浏览器名单：`/data/data/*/app_chrome|app_webview|app_u4_webview/…/Cookies` 全扫，扫到什么解什么。结果可导出到控制目录下的 `cookies.json` 交给 DSH Go。

几个坑（代码注释里也写着）：

- CBC 的 IV 不对只毁第一块，所以解密用 `NoPadding` 自己剥 PKCS#7；用 `PKCS5Padding` 会在首块错位时抛异常，把能解的值一起丢掉
- Chrome 130+ 的明文前面垫了 32 字节 `SHA256(host_key)`，要先丢掉再取值
- 临时库用完即删——里面是活的登录凭证

## 内置脚本

自带 9 个（3 示例 + 6 模板），「自定义」页可单独开关。**改脚本不用重装模块**——改完 3 秒内自动重注入。

## 隐身

默认开启，对目标 App 隐藏 15 类 Java 层特征。

控制目录有开关关掉它——但**诚实的边界**是：只能挡 Java 层探测，native 直接读 `/proc/self/maps`、检查 ART 内存页属性这些，Java 层无能为力。

## 兼容性

| 项 | 要求 |
|---|---|
| Android | 8.0（API 24）～ 16（API 36）；APK 声明 `minSdk 24` / `targetSdk 36` |
| LSPosed | 现代 API 93+；经典版 1.9.x 与 Zygisk 版 2.x 都支持 |
| Root | KernelSU / Magisk / APatch —— LSPosed 本身就要 root |
| 存储权限 | 「所有文件访问」（写控制目录）；不给也能进，但脚本与配方用不了 |
| 架构 | 不限：纯 Java，无 native 库，armeabi-v7a / arm64-v8a / x86_64 通用 |
| 已实测 | Android 15 + LSPosed 2.2.1 + KernelSU；其余版本按上表兼容，未逐一跑过 |

## 安装

1. 下载 [Releases](../../releases) 里的 APK
2. 装好后到 **LSPosed 管理器**启用本模块
3. 勾选要用 DSH Go 操作的目标 App
4. **重启那个 App**（不是重启手机）
5. 打开本模块设置页，选配方或写自定义规则
6. 让 DSH Go 读 `DSH-GUIDE.md` 接管

首次打开会引导授存储权限——不给也能进，但脚本和配方用不了（界面显示「受限模式」）。

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

## 项目结构

```
src/…/HookEntry.java        目标进程里的 hook 逻辑（注入、隐身）
src/…/SettingsActivity.java 设置界面
src/…/XhookProvider.java    跨进程配置通道
src/…/AppPickerDialog.java  应用选择器
src/…/ProbeService.java     探针服务
src/…/CookieVault.java      Cookie 解密（v10 / 明文 / v20 检测）
assets/scripts/             内置脚本
assets/recipes/             内置配方
assets/capabilities.json    能力清单（DSH Go 读这个）
module.prop                 LSPosed / Magisk 模块信息
CHANGELOG.md                更新日志
LICENSE                     MIT
NOTES.md                    开发笔记（踩过的坑）
```

## 开发笔记

踩过的坑都在 [NOTES.md](NOTES.md)：三种 WebView 内核的差异、Chromium 的 `evaluateJavaScript` 传 null 会崩、隐身为啥必须排除模块自己、LSPosed 的 binder 连接为什么不能靠 killall 重建。

## 免责声明

仅供学习研究与自有设备调试使用。请勿用于未授权的设备或违反服务条款的场景。

## 更新日志

见 [CHANGELOG.md](CHANGELOG.md)。

## License

[MIT](LICENSE)
