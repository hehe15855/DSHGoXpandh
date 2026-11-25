# xhook 框架

> 一份自包含的运行时 hook 能力。别人把 `xhook.sh` 放进工作区就能用。

## 一、这是什么

Android 上的 LSPosed 模块 + 一个命令行接口。它给 agent（或人）提供在**别的 App 进程里执行代码**的能力。

```
别人                          xhook                       目标 App
──────────                    ──────                       ────────
xhook recipe apply X com.y     →  写配置到 com.y 私有目录
xhook restart com.y            →  重启 com.y
                               →  LSPosed 在 com.y 启动时注入
                               →  模块按配置 hook
xhook log com.y                →  读出结果
```

## 二、能力边界（能做什么 / 不能做什么）

### 能做

| 能力 | 命令 | 效果 |
|---|---|---|
| 看加密明文 | `recipe apply cipher-log` | AES/RSA/HMAC 输入输出全记下 |
| 改返回值 | `hook` / 编辑 json | `isVip()` 永远 true |
| 改入参 | `arg <pkg> <类> <方法> <位置> <值>` | 设备 ID、定位、时间戳随便换 |
| 条件匹配 | json 里 `when` | 只对含关键词的调用生效 |
| 调静态方法 | json 里 `actions` | 主动读某个值 |
| 读 Cookie | json 里 `actions` | WebView 的 cookie |
| 引用内置配方 | 配置里 `"recipe": "名字"` | 不用重装 APK |

### 不能做

| 限制 | 原因 |
|---|---|
| 有独立 zygote 的 App（Chrome） | LSPosed 注入不进 |
| 改 native（.so）层 | 当前只 hook Java |
| 不重启就改生效 | 配置在进程启动时读一次 |
| 在 UI 里保存配置 | ColorOS 上 prefs 不落盘（实测） |

## 三、怎么接进来（3 步）

**第 1 步：装模块**

```bash
bash xhook-install.sh
# 或手动：pm install -r xhook-v25.apk
```

**第 2 步：LSPosed 里启用 + 勾作用域**

```
模块列表 → DSH Module → 打开开关
→ 点进作用域，勾你要 hook 的 App
→ 勾完退出页面（不然不保存）
```

**第 3 步：用命令**

```bash
xhook recipe apply cipher-log com.example.app
xhook restart com.example.app
xhook log com.example.app
```

## 四、接口（9 条命令）

```
xhook list                     哪些 App 在跑、有没有外部配置
xhook add <关键词>             查包名并推配置
xhook hook <pkg> <目标>         加一个 hook（短名：md→MessageDigest.digest）
xhook arg <pkg> <类> <方法> <位置> <值>   改入参
xhook recipe list              列出内置配方
xhook recipe show <名>         看配方内容
xhook recipe apply <名> <pkg|关键词>      套配方（可叠加）
xhook show <pkg>               看当前生效配置
xhook restart <pkg>            重启让配置生效
xhook log [pkg]                看模块日志
xhook targets                  看内置目标表
xhook deps                     检查依赖（root/python3/nsenter）
```

## 五、环境无关（换台机器也能跑）

| 差异 | 应对 |
|---|---|
| root 是 KSU/Magisk/APatch | `psh` → `su` 自动探测 |
| 数据根 `/data/user/0` 或 `/data/data` | nsenter 内自动判断 |
| 没有 python3 | 退化成整文件覆盖 |
| 没有 nsenter | 明确报错 + 提示 busybox |
| 模块装在哪 | `XMOD=<路径>` 覆盖 |
| 配置放哪 | `XHOOK_TMP` / `XHOOK_CFG` 覆盖 |

**自检**：

```bash
xhook deps
# root=psh python3=1 nsenter=1     ← 全绿就能用
```

## 六、配置格式（唯一契约）

```json
{
  "process": "main",
  "sample": 3,
  "window_ms": 1000,
  "head": 200,
  "tail": 200,
  "max_logs": 400,
  "recipe": "cipher-log",
  "hooks": [
    {"class": "javax.crypto.Cipher", "method": "doFinal", "mode": "log"},
    {"class": "X", "method": "isVip", "mode": "return", "return_value": "true"},
    {"class": "X", "method": "setId", "mode": "args", "set": [{"i":0,"v":"FAKE"}]},
    {"class": "java.io.File", "method": "exists", "mode": "return",
     "when": {"i":0,"contains":"su"}, "return_value": "false"}
  ],
  "actions": [
    {"type": "call", "class": "java.util.TimeZone", "method": "getDefault"},
    {"type": "cookie", "url": "https://example.com"}
  ]
}
```

**四种 mode**：

```
log     看参数和返回值
return  改返回值（原类型自动转换）
args    改入参（按下标）
block   跳过原方法（未实现，用 return 代替）
```

## 七、输出在哪

**只走 `XposedBridge.log`，不写任何文件。**

```
/data/adb/lspd/log/modules*.log      ← root 可读
```

## 八、已知坑（写进代码里的）

1. **namespace 隔离** —— 全局的 `/data/user/0/<pkg>` 和 App 看到的不是同一份，必须 `nsenter -t <pid> -m --` 写
2. **Application 时序** —— `handleLoadPackage` 时 Application 还没建，Cookie 拿不到 Context，actions 挂在 `Application.onCreate`
3. **versionCode 必须递增** —— 同版本反复覆盖安装，LSPosed 认为模块没变，一直用旧路径
4. **`/sdcard` App 读不到** —— 分区存储，配置必须进 App 私有目录
5. **`pm install` 要从 `/data/local/tmp`** —— `/sdcard` 是 FUSE，system_server 读不了

## 九、别人怎么用（一句话）

```bash
cp xhook.sh recipes -r ~/my-workspace/ && bash xhook.sh deps
```

通了就能改任何 App 的 Java 行为。