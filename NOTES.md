# DSHxpandh 开发笔记

踩过的坑、验证过的方法。按主题归类，方便以后翻。

---

## 一、WebView 注入：三种内核

同一个 App 可能同时存在多套 WebView，必须都覆盖。

| 内核 | 特征 | 怎么 hook |
|---|---|---|
| 标准 `android.webkit.WebView` | 大多数 App | hook **构造函数**（不是只 hook loadUrl） |
| 腾讯 XWeb `com.tencent.xweb.WebView` | 微信/QQ | 接口同构，同样 hook 构造函数 |
| Chromium `AwContents` | 系统 WebView 的底层，OPPO/Chrome/夸克 | 只能靠 `onDraw` 的 `thisObject` 拿到**真正在绘制的**实例 |

### 关键教训：WCTABS 不可靠

构造函数抓到的 AwContents 可能是**模块加载之前就建好的旧对象**，注入进去等于打到空壳上——日志显示成功，页面纹丝不动。

**真正在画的那一个，只有 `onDraw` 能告诉你。**

所以：onDraw 里 `LAST_DRAWN_AW = self`，兜底注入时优先用它。

### Chromium 的 evaluateJavaScript 有两个硬要求

```java
// ❌ 传 null 直接 native 崩（signal 5 SIGTRAP）
aw.evaluateJavaScript(js, null);

// ✅ 必须给个空实现
aw.evaluateJavaScript(js, new ValueCallback<String>() {
    public void onReceiveValue(String v) { }
});
```

崩了之后浏览器起不来，只能靠重启恢复。**这条试错成本极高，记住它。**

---

## 二、注入效果验证：别信日志

`emit()` 只证明函数被调用了，不证明页面变了。

**正确顺序**：

1. `document.title = 'PROBE-OK'` —— 看地址栏，一眼判断通道通不通
2. 通道通了再写真正的效果
3. 效果出来了再谈优化

**踩过的坑**：

- 用 `new Image().src = 'http://...'` 做探针 → 请求被 WebView 拦了，误判成没执行
- 用 `console.log` → 被浏览器自己的日志噪声淹没了
- 看 `script-inject` 日志以为成功 → 实际打在离屏 WebView 上

**结论：只认屏幕上看得见的变化。**

---

## 三、隐身的边界

### 必须排除模块自己

所有隐身 hook 第一行都要有：

```java
if (SELF.equals(pkg)) return;
```

漏掉的后果：模块自己的 `Runtime.exec("su")` 被自己的 hook 拦掉 → 界面显示「没有 root 权限」，其实是自己挡的。这个坑踩过一次，排查了很久。

### Java 层挡得住什么

| 检测手段 | 挡得住吗 |
|---|---|
| `getPackageInfo` 查模块包名 | ✅ |
| 扫类名找 `de.robv.android.xposed.*` | ✅ |
| 抓栈帧看 xposed 帧 | ✅ |
| 反射扫 `$xposed` 成员 | ✅（要 hook getDeclaredMethods 等 4 个入口） |
| `/proc/self/maps` 走 Java 读 | ⚠️ 只挡 BufferedReader 那条路 |
| **native 直接 fopen maps** | ❌ |
| **检查 ART 内存页属性** | ❌ |

**专业检测工具（Duck Detector 这类）全走 native + 多进程**，Java 层 hook 对它基本无效。这不是代码能补的，是框架层面的限制。

---

## 四、LSPosed 维护

### 绝对不要 killall lspd

`lspd` 和 zygote 之间有一条只在**启动时建立一次**的 binder 连接。杀掉 daemon 后：

```
新 lspd  → "no response from bridge"
zygote   → DeadObjectException
```

两边互相等，谁也重建不了。**只能重启手机。**

而且管理器会一起失效（它寄生在 `com.android.shell` 里）。

**改作用域的正确做法**：改 db + 让用户重启，不要动守护进程。

### 每次重装要修 apk_path

`pm install -r` 之后 LSPosed 记录的路径可能失效：

```sql
update modules set apk_path=? where module_pkg_name='...'
```

改完要 `chown 0:0 chmod 600`，并清掉 `-wal`/`-shm`。

---

## 五、Android 界面细节

### 状态栏 inset 要两头都处理

```java
int top = insets.getSystemWindowInsetTop();      // 容易被忘
int bottom = insets.getSystemWindowInsetBottom();
v.setPadding(0, top, 0, 0);
```

只处理 bottom 的话，标题会压在状态栏的时间上。

### 状态栏图标颜色要跟随背景

```java
if (isLightColor(BG)) {
    flags |= SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;       // 浅底 → 深色图标
    flags |= SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
}
```

只设 `setStatusBarColor()` 不设图标 → 浅色背景配白字，看不见。

### Dialog 别用 NoActionBar 主题

`Theme_DeviceDefault_Light_NoActionBar` 是 **Activity 主题**，配上 `MATCH_PARENT` 会盖住状态栏。用 `Theme_DeviceDefault_Light_Dialog`，它本来就在状态栏下方。

---

## 六、脚本管理

### 内置脚本从 APK 读，不部署副本

```java
// 从自己的 APK 里读 assets/scripts/
String apk = HookEntry.class.getProtectionDomain().getCodeSource().getLocation().getPath();
ZipFile z = new ZipFile(new File(URLDecoder.decode(apk, "UTF-8")));
```

好处：`/sdcard` 上不用放副本；改脚本内容要重新编译（但也意味着不会被误改）。

### 两种脚本的删除语义

| | 按钮 | 能恢复吗 |
|---|---|---|
| 内置 | 移除 | **能**（文件在 APK 里） |
| 本地 | 删（点两下确认） | **不能**（真删了） |

「恢复被移除的内置脚本」只清 prefs 标记，**不导出文件**——导了会让列表出现两套重名。

### 改脚本不用重装

`HookEntry` 每 3 秒扫一遍脚本源，内容变了才重新注入。调试流程是：改文件 → 刷新页面 → 看效果。

---

## 七、版本号规则

| 改动 | 版本号 |
|---|---|
| 功能、修 bug | 升中段 `12.6` → `12.7` |
| 文档、文案、小修 | 只加末位 `12.7.2` → `12.7.3` |
| 架构重构 | 大版本 `12.x` → `13.0` |

`versionCode` 每次都递增，LSPosed 靠它判断新旧。

---

## 八、部署流程（每次都要走完）

```bash
bash build.sh
cp mod_signed.apk /sdcard/DSH-Output/DSHxpandh-dist/DSHxpandh-vNNN.apk
# 拷到 /data/local/tmp 再装（pm install 读不了 /sdcard 的 FUSE）
psh 'cp ... /data/local/tmp/x.apk && pm install -r /data/local/tmp/x.apk'
# 拿到新路径，写回 LSPosed 配置
NEW=$(psh 'pm path <包名>' | sed 's/package://')
# 改 modules_config.db 的 apk_path，回写、chown、chmod、清 wal
# 重启模块 App 让 provider 生效
```

---

---

## 九、默认值的正确写法（以隐身开关为例）

**需求**：装完就是开的，且覆盖安装不会变回关。

**错误做法**：每次启动都 `putBoolean("stealth", true)` —— 用户手动关了也会被强制打开，等于开关失效。

**正确做法**：用一个「用户动过没有」的标记来区分。

```java
void ensureStealthDefault() {
    SharedPreferences p = getSharedPreferences(PREFS, 0);
    if (p.getBoolean("stealthUserSet", false)) return;   // 用户定过，不碰
    if (!p.getBoolean("stealth", false)) {
        p.edit().putBoolean("stealth", true).commit();    // 没定过 → 确保是开
    }
}

// 用户手动切换时打标记
void setStealth(boolean on) {
    p.edit().putBoolean("stealthUserSet", true).commit();
    ...
}
```

**验证方法**（别只看代码）：把 prefs 里的值手工改成 `false`，重启 App，看是否自动变回 `true`。

**通用到其他开关**：凡是「默认值要正确，但不能覆盖用户选择」的，都用这个模式 —— 一个 `xxxUserSet` 标记 + 启动时补默认值。

---

## 十、列表页为什么会卡（应用选择器实测）

119 个应用，点开后要等好几秒。三个开销点：

| 开销 | 原因 | 修法 |
|---|---|---|
| **同步加载** | `onCreate` 里读完全部 label 才返回 | 移到后台线程，先显示空列表 |
| **排序时反复查 label** | comparator 每次比较都调 `loadLabel`，O(n log n) 次 | 排序前预热一遍，之后走 HashMap |
| **图标每次都加载** | `getView` 里调 `loadIcon`，滚动一次调一次 | 按包名缓存 Drawable |

**最关键的是第一条**：窗口已经显示出来了，但列表要等全部读完才填充，用户看到的就是“卡着不动”。

```java
// onCreate 里先构建 UI（空列表）
setContentView(root);
rebuildList();   // 空的
// 后台加载
new Thread(() -> {
    loadApps();
    new Handler(Looper.getMainLooper()).post(() -> rebuildList());
}).start();
```

**通用原则**：任何要遍历几十上百个系统对象的列表，都别在主线程同步做；先出骨架，再填数据。

---

## 十一、用脚本批量改源码的坑

这轮我用 `s.replace('"");', '");')` 想修一处引号，结果全局替换**误伤 6 处** —— 像 `getString("x", "")`、`setText("")` 这种空字符串默认值全被改坏，编译报了一堆 `unclosed string literal`。

**教训**：

1. **全局 replace 之前先数一下命中次数**（`s.count(old)`），不是 1 就别用 replace
2. **按行号 + 断言替换**：定位到具体行，确认内容符合预期再改
3. **改完立刻编译**，别攒着改十处再编译 —— 报错位置会指向错误的源头
4. **TS 字符串里的 `\n` 经过 shell 传给 python 容易变成真换行**，改 Java 源码优先用 `edit` 工具（逐字面匹配），少用 shell + python 拼字符串

---

## 一句话总结

**验证看屏幕，不看日志；改配置别动守护进程；隐身只防 Java 层。**
