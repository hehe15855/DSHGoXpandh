# 更新日志
## v13.3.1 (175)（2026-11-27）

### 文本

- **公告页第 3 步补上脚本入口**：原来只说「选配方，或写自定义 hook」，
  要给网页加效果的人不知道从哪进。现在写明「自定义 → 注入脚本 → 加入脚本」。
- **公告页「能做六件事」卡补一句边界**：脚本来自 APK 内置或界面导入，两边都不用往
  `/sdcard` 丢文件。
- **帮助与排查卡加一条**：「脚本没生效 → 只能从加入脚本导入，往 scripts/ 丢 .js 不生效」。
  这张卡原来有五条，恰好漏了这个最容易踩的。

### 承接上一版（172/173）

- 自定义页两处过时文案：脚本区和指令区都写着「丢进去也会自动出现」，已改成实际行为
  （脚本只能界面导入；cmds/ 只是历史记录，不会自己执行）。
- 能力清单里给 AI 看的两处同样的说法也一并改了。

## v13.3.0 (172)（2026-11-27）

### 文本

- **修掉一个数字对不上的说法**：隐身说明写「遮挡 18 个面、22 处检测点」，但代码里
  `surfaces=22` 指的是已挂载的 hook 数，不是「检测点」；「18 个面」也无对应来源。
  改成「从下面这些面挡住探测」，后面逐条列出，不再报总数。
- README 中英文开头都补齐六种能力（原来只提了 4 种，漏掉改参数和调内部方法）。

### 行为（承接上一版）

- `setAllScripts()` 里还剩一段遍历 `/sdcard/DSHxpandh/scripts/` 并写进 prefs 的代码，
  这是上一版漏掉的第二处自动导入 —— 点「全部开启」时目录里的 .js 会被一起导进来。
  已删。现在两处都没有了，目录里的文件彻底不参与加载。

## v13.3.0 (170)（2026-11-27）

### 修复

- **脚本目录不再自动加载**。以前 `/sdcard/DSHxpandh/scripts/` 里的 `.js` 会被
  `syncScriptsToPrefs()` 自动同步进 prefs —— 丢个文件进目录就「自动出现、自动生效」，
  用户改界面里的版本反而不生效（目录旧副本优先级更高）。现在这个方法留成空实现，
  脚本只能通过界面「加入脚本」导入；那个目录只存 `.off` / `.on` 开关标记。
- 删掉无调用的 `releaseSampleScripts()`（4.6K 死代码）：它原本会把内置示例写进目录。

### 文档

- `DSH-GUIDE.md` 新增「脚本从哪来」一节：只有内置和界面导入两条路，写明目录不参与加载。
- `HELP.md` 与 `写脚本指南.md` 里「把 .js 丢进 scripts/ 就自动注入」的说法是错的，已改。

## v13.2.0 (168)（2026-11-27）

### 变更

- **模块描述重写，覆盖全部六种能力**：抓明文、改参数、改返回值、注网页脚本、
  读 Cookie、调内部方法。旧描述只提了四件事，漏掉「改参数」和「调内部方法」。
  同时写明「配方和脚本随包附带」「不需要 root」「只对勾选过的 App 生效」。
- **能力清单 summary 同步**，按六种模式逐条对应。
- **去掉四处写死的数量**（界面文案、DSH-GUIDE 两处、README）。数字本身没错，
  但以后增删配方/脚本就要追着改四处，改成不带数字的表述不会过期。

### 工具

- `publish.sh` 的 `DRY` 由硬赋值改成读环境变量：以前 `DRY=1 bash publish.sh`
  会被脚本里的 `DRY=0` 覆盖，说是预览、实际真建了 Release 并上传 APK。
## v13.2.0 (167)（2026-11-27）

### 变更

- **版本号显示改成带 build 号**：界面、诊断信息里统一显示 `13.2.0 (167)`，
  括号里是 versionCode。只看 `13.2.0` 分不清装的是哪一次构建，带上之后
  用户报障时念一遍就知道是不是最新。
- `module.prop` 的 `version` 同步成 `v13.2.0 (167)`。
- 内部两处**改用纯版本号**，避免括号混进去：
  - 检查更新的 `User-Agent`（远端按点分数字比对，带括号会认不出）；
  - 跟远端 latest 比对的当前版本。
## v13.1.9（2026-11-27）

### 修复

- **隐身开关关不掉**：provider 的 `getStr` 在 key 存的是布尔值时抛
  `ClassCastException`（Android 不允许跨类型读），异常被外层 catch 吞掉后
  Bundle 里什么都没写，调用方收到 `Bundle[{}]`，便一路退到「默认隐身开」的兜底。
  现在先查 `getAll()` 判断类型再取，字符串/布尔/数字都能正确返回。
- **兜底会覆盖用户设置**：读不到时无条件返回 `true`，等于你关掉也不生效。
  现在先尊重本进程上次读到的真实值，真的从没读到过才用默认值。

### 优化（开关统一）

- 6 个开关（`stealth` / `off` / `classhide` / `stealth-file` / `stealth-exec` /
  `scriptOff:<名>`）统一走同一个读取方法，优先级：
  **App 内部（prefs，经 provider）> 上次真实值 > 文件标记 > 调用方默认**。
  以前各处自己判断、文件优先，目标进程读不到 `/sdcard` 时会各说各话。

### 优化（其它）

- 读 LSPosed 配置库的临时副本，改成只留最新一份（原来每点一次「重新检测」就堆一个）。
- 「重新检测」的显示加上检测时刻，能区分「按钮有没有触发」和「心跳在不在跳」。
## v13.1.8（2026-11-27）

### 修复

- **界面导入的脚本被目录里同名旧副本盖掉**：三个来源合并进同一个 map 后，
  后写的覆盖先写的，而目录 `/sdcard/DSHxpandh/scripts/` 排最后，等于它优先级最高。
  实测 `kb-block.js` 在目录里是 1498 字节的旧版、prefs 里是 1231 字节的新版，
  注入的一直是目录那份，用户改界面里的版本却不见效。
  现在目录只补空缺（`all.containsKey(n)` 就跳过），优先级改为：
  **prefs（界面导入）> 内置 > 目录**。
## v13.1.7（2026-11-27）

### 修复

- **重装后误报「LSPosed 未激活」**：判断顺序里探针结果排在前面，
  而探针靠 `/sdcard` 上的 `probe-*` 文件；卸载重装清掉文件后，
  三元表达式短路掉 `lspActive()`（直接看进程里有没有 XposedBridge），
  于是模块明明在跑却显示未激活。现在 `lspActive()` 放最前面。

### 优化（路径）

- 控制目录统一为 `/sdcard/DSHxpandh`，去掉 `/sdcard/Download/DSHxpandh` 旧候选。
  两个目录并存时模块和 DSH 插件可能各写一边，表现为「刚写的文件对方读不到」。
  用户配置目录随之并到 `DSHxpandh/user/`。

### 优化（清理）

- 隐身开启时清理盘上残留，清三类：共享命令文件（`c-*.bin` / `cmd-*.json`）、
  说明文档（`*.md`）、旧目录 `/sdcard/Download/DSHxpandh`。
  用户自己导入的脚本不动。
- 触发点改为三个明确事件：模块启动、退出界面、检测到模块进程结束。
  原来的每分钟循环已去掉——空转要反复起线程并探一次 root，纯耗电。
- 进程被杀（force-stop / OOM 都是 SIGKILL）时 JVM 的 shutdown hook 不执行，
  这条由 DSH 插件的心跳兜住：每轮探一次模块进程，活转死就立刻强制清。
- 没有 root 时一律跳过：那时共享文件是唯一命令通道，清掉等于断了下发路径。
## v13.1.6（2026-11-27）

### 修复

- **界面导入的脚本不生效**：`scriptsFromPrefs()` 写好了却没有任何调用点，
  只有内置脚本能跑。现在接上，优先级为 目录 > prefs > 内置。
- **隐身开关显示与实际不一致**：目标进程里 XSharedPreferences 常返回 null，
  兜底逻辑恒判「开」，界面显示关、实际在隐身。
  现在开关会同步写 `stealth` / `nostealth` 两个标记文件，目标进程优先读文件。

### 工具

- `audit.mjs` 的 F4 检查窗口从 900 字放宽到 2400 字：
  函数里注释一多，末尾的 toast 会被挤出窗口、报出假警报。
## v13.1.5（2026-11-27）

### 增强（隐身）

- 隐身从 15 类补到 **18 个面 / 22 处检测点**，主进程与子进程都装：
  - `ProcessBuilder.start`、`getRunningTasks`、`getPackagesForUid`
  - `getAnnotations` / `getDeclaredAnnotations`（摘掉 xposed 注解）
  - `isDebuggerConnected` / `waitingForDebugger` / `isNativeDebuggerConnected`
  - `getInstalledPackagesAsUser` / `getInstalledApplicationsAsUser` / `getApplicationInfoAsUser`
  - `getRunningAppProcesses` / `getRunningServices` / `getInstallerPackageName`
  - `Files.readAllBytes` / `Os.readlink` / `File.getCanonicalPath`
  - `ClassLoader.loadLibrary`（拦 frida/xposed/lspd/substrate）
  - `SystemProperties.getInt` / `getBoolean`、无参 `System.getenv()`
  - `checkSignatures` / `getCallingPackage` / `getCallingActivity`
  - `ApplicationInfo.nativeLibraryDir`
- **子进程也装隐身**（原来 `process=main` 会在装隐身之前 return，检测器在 `:push` / `:sandbox` 里跑就等于裸奔）

### 修（关键 bug）

- `prefsStr` 只走 provider，而 hook 早期 `currentApplication()` 是 null → 所有配置读取静默失败。
  改成三级兜底：**直接解析 prefs xml → getRemotePreferences → provider**。
  这一条同时修好了「隐身开关读不到」「debug 永远关着」「脚本开关点了没反应」。
- root 判定统一看 `uid=0`，不再只看退出码（部分 KernelSU 会静默回落到 App 自己的 uid，退出码照样是 0）
- `emit` 在 `SILENT` 下吞日志，导致子进程隐身装了也看不到。新增 `emitAlways()` 给关键节点用

### 新增（调试日志）

- **导出日志**：一键抓 logcat（走 su）写成 `logcat-<时间>.txt`，含模块日志、注入日志、崩溃栈、状态快照
- **关闭即清痕迹**：删导出文件 + 清 logcat（`-b all -c`），并核对清前清后行数
- **立即清痕迹**按钮，结果如实回报（清干净了还是只删了文件）

### 修（脚本开关）

- 双信号判定：`.off` / `.on` 文件（DSH 侧操作）优先于 prefs（设置页操作）
- 「全部开启 / 关闭」同时处理内置脚本与用户脚本，并同步标记文件
- 恢复按钮恢复后开关默认全关（示例1 不再自动跑）

### 改（界面与文案）

- 公告页字号统一 13sp，精简为三张卡 + 底部「查看完整说明」入口
- 详细说明页字号 12→13sp、表格 10→12sp
- 帮助 / 说明页加 Markdown 渲染，不再显示 `#` `**` 这类源码符号
- 帮助对话框背景跟随主题（原来浅色系统下白底浅字看不见）
- 隐身旁白从 6 条扩到完整清单，并说清「子进程也装」「native 挡不住」

### 修正（文档）

- `DSH-GUIDE` 里「下发命令写 cmd.json」是错的（实际是 `c-<包名SHA1前8位>.bin` + XOR 混淆），改成用 `dshxpandh_inject`
- `dshxpandh_script` 描述里「先判 window.__done」不实，改成「重复执行无副作用」
- `capabilities.json` 补 `stealth` 字段（18 面 / 22 点 / 覆盖范围 / 挡不住的）；「300 字节」改成「各 200（上限 512）」
- `module.prop` 描述多行堆叠修复（`sync-texts` 每次叠加一段）

### 仓库

- 提交 `api-libxposed/`（源码 + jar）与 `meta-xposed/`，clone 下来即可编译

## v13.1.0（2026-11-27）

### 新增

- **能力分界写进 App**：说清「哪件事该谁做」——模块只管 Java 层，native 层的 .so 逻辑得交给 rizin/Ghidra 改二进制，落盘数据 root 直接读。
  - `capabilities.json` 新增 `boundary` 字段：术语定义、五步判断（decide）、七张分层卡（cards）、对照表（quickTable）、一句话判据（rule）、三条谁都做不到（notCovered）。
  - `DSH-GUIDE.md` 新增「能力分界：哪件事该谁做」一节，人读版，含五步判断、对照表、硬边界。
  - 别人装完模块、DSH 读指南，都能自己判断该用哪条路，不用猜。

### 修正

- **导出文档会冲掉用户手改的内容**：原来 `exportCapabilities()` 每次 `onResume` 都用 assets 覆盖控制目录，用户改了 `capabilities.json` 下次打开设置页就没了。
  - 改成版本戳机制：控制目录放 `.spec-rev`（内容 = APK versionName），只在「文件不存在」或「本地戳 ≠ APK 版本」时才写。
  - 用户手改后不升 APK 就保留；升了 APK 才拿新内容。

## v13.0.2（2026-11-26）

### 修正

- **帮助文档里「改完插件必须开新对话」是错的**：DSH 的工具集合是活的（`tools/change` 事件触发按 agent 重新 `restrict`），注册表一变，正在跑的会话下一轮就同步。**改完插件只要重启引擎**。之前那条结论是把「插件注册失败」误判成了「会话缓存」。
- 排查口径补实：`dsh-app/diagnostics.md` 的 appErrors 段出现 `cannot get property "tools" without inject`，就是插件没声明 `inject`（v13.0.1 已修）。

### 变更

- 帮助文档不再抄一份名称清单 —— 手写副本会成为第二个源头、跟着漂移。只留排查必需的工具名；命名定型的记录放在本文件。
## v13.0.1（2026-11-26）

### 修正（重要）

- **插件四个工具此前从未注册成功**：cordis 里没有 `inject` 声明就拿不到 `ctx.tools`，诊断日志中的「cannot get property "tools" without inject」即此。补上 `export const inject = ['tools', 'systemPrompt']` 后四个工具才会出现。
- 插件内「打开下载页」指向 `hehe15855/DSHxpandh`（实测 404），改回真仓库 `hehe15855/DSHGoXpandh`（实测 200）。
- `dshxpandh_inject` 描述原写「需要 root」，与代码不符：有 root 写 App 私有目录，没 root 写控制目录共享文件，只有「没 root + 开着隐身」才拒绝。`status` 输出里的同类表述一并改正。
- `dshxpandh_logs` 的关键词原用双引号拼进 shell，含空格会被拆成两个参数；改为取回日志后在 node 侧过滤。

### 变更

- 14 条工具描述 884 → 752 字，系统提示声明 510 → 455 字（只删冗余，事实逐条对照代码改准）
- **命名定型**：显示名、控制目录、插件、四个工具统一为 DSHxpandh 一族，本版起不再改名。名称的唯一源头是插件 `package.json`（name / dshGo.title）与模块常量（ROOT、PREFS、工具注册名），文档不再抄一份清单。
## v13.0.0（2026-11-26）

### 变更

- **命名统一**：显示名、控制目录、插件与工具全部收敛到一个名字（显示名 `DSHxpandh`）
  - 控制目录：`/sdcard/xhook` → `/sdcard/DSHxpandh`（不再多候选，只有 Download 兜底）
  - 插件：`local-xhook` → `local-dshxpandh`，标题/状态条/面板 id 同步
  - 工具：`xhook_status/script/inject/logs` → `dshxpandh_status/script/inject/logs`
- Provider 类改名 `XhookProvider` → `DshxpandhProvider`，prefs 名 `xhook` → `dshxpandh`，调用统一走常量
- 清理脚本改名 `dshxpandh-clean.sh`；`module.prop` 的 name 与描述同步

### 修正

- 审计脚本不再写死插件路径：按名字在 `dsh-go/plugins` 里自动发现，改名后不会静默失效
- 插件 `pickRoot()` 去掉重复候选与永假分支
## v12.9.10（2026-11-26）

### 修复：心跳只跳一次就停

- `ctx.effect` 收的是「返回清理函数的工厂」，我们直接把清理逻辑当参数传进去，结果它被**当场执行**——定时器刚设好就被自己清掉。表现：`apply` 走完、心跳跳一次、然后永远静止
- 改成 `ctx.effect(() => () => {...})`。同一个 effect 还负责注销工具和面板，所以这条也影响工具可用性

### 打开 DSH 立刻就连上

- 光靠 60 秒定时器，刚打开 DSH 那段时间模块侧看不到连接
- 现在 `apply` 一进来就写心跳，**任何一个工具被调用也顺手刷新**——用过一次工具，连接立刻变成新鲜的

### 配套：plugin-audit 的 P12 真正生效

- 原来读 `/opt/dsh/package.json` 的 version 字段，那文件里没有这个字段，一直空转
- 改成从 `dsh --version` 取（实测 `0.2.2-rc.1`），现在能真正比对插件的 `engines.dsh` 版本要求
## v12.9.9（2026-11-26）

### 修复：引擎重启后对接插件不再加载

- 根因是插件里一行 `export const inject = ['tools']`。加上它之后，引擎重启就不再加载这个插件：心跳停 27 分钟、DSH 里的四个工具全部消失，而插件代码本身没有任何问题
- 判定依据：三个能正常工作的 dsh-go 插件（local-fish-hud / local-magpie / local-output-dir）同样调用 `ctx.tools.register`，却都没有声明 inject
- 删掉声明后触发热重载立即恢复：`apply-hit` 写出 `apply-ok`、心跳恢复每分钟一跳

### 插件健壮性：加载失败不再无声无息

- **`apply-hit`**：apply 一进来就写一条（含时间戳与阶段）。有了它，「插件没加载」和「加载了但后面挂了」能一眼分开
- **`beat-err`**：心跳写失败不再被 try/catch 静默吞掉，留一条错误痕迹
- **隐身时不写**：stealth 开着的时候这些痕迹一个都不写

### 配套：跨插件审验脚本

仓库外新增 `scripts/plugin-audit.mjs`，17 项检查，其中三层判断「装没装」：文件层（目录/配置/软链）、加载层（心跳新鲜度）、功能层（真调 provider）。这次就是靠它定位到问题的
## v12.9.8（2026-11-26）

### 内置帮助：把踩过的坑写进 APK

- 新增 `assets/HELP.md`（约 2800 字），按「症状 → 原因 → 解决」写清八类问题：DSH 里看不到工具、显示未连接、点释放没反应、插件装不上、注入不生效、隐身开不下发、免 root 环境、共享命令文件长什么样
- 另附七条注意事项：别删控制目录、必须勾选并重启目标 App、隐身+无 root 的取舍、脚本同名用户优先、调试日志记得关、改版本要同步四处、DSH 重启后开新会话
- 设置页新增「帮助与排查」卡片：五条最高频问题直接列在卡片上，底部「看完整帮助」用可滚动对话框打开全文
- 同时把 `HELP.md` 导出到控制目录，方便复制给别人

## v12.9.7（2026-11-26）

### 手动入口：两边都能自己确认一次

- 心跳只由 DSH 引擎写，它被系统冻结或在后台被清时会停写，卡片就会一直显示「未连接」，看起来像插件坏了
- **模块侧**：DSH 连接卡新增「重新检测」——重读心跳，并按情况说清是「插件没装」「从没跑过」还是「心跳停在 N 分钟前（DSH 可能被冻结）」
- **DSH 侧**：面板的「重新检测」改成「检测并上报」——点一下立刻补写一次心跳，再查模块状态，等于手动喊一声「我还在」

### 适配免 root 环境（Shizuku / LSPatch 这类）

- 注入命令原来写目标 App 私有目录（`/data/user/0/<pkg>/files/`），那一步必须 root；现在改走共享文件 `/sdcard/DSHxpandh/cmd-<pkg>.json`
- `DshxpandhProvider` 新增 `getCmd`：目标进程通过 ContentProvider 取命令时，先看模块 prefs，没有再读控制目录里的共享文件（provider 跑在模块进程，有存储权限）
- 整条链变成：谁都能写 /sdcard 上的命令文件 → 模块通过 provider 交给目标进程 → **全程不需要 root**
- DSH 插件侧配套：`xhook_inject` 不再强制要求 psh/root，改为写共享文件

## v12.9.6（2026-11-26）

### 编译时同步文本，版本号不再脱节

- `module.prop` 一直停在 v12.9.0/147，实际 APK 已经 153——`build.sh` 只同步了 `HookEntry` 的 `VER` 常量，从没管过 `module.prop`。现在新增 `sync-texts.mjs`，编译前把 Manifest 的 `versionName`/`versionCode` 和 `texts/description.txt` 一起写进 `module.prop`
- 简介改为单一来源 `texts/description.txt`，改文本不用再翻两处

### 应用内公告更新

- 写明内置插件与四个工具，以及「不装 DSH，本模块全部功能照常手工可用」

## v12.9.5（2026-11-26）

### 修复：点「释放插件」没有任何反馈

- 释放其实成功了（文件确实落到控制目录），但只有一个几百毫秒的 toast，卡片文字不变，看起来像没反应
- 改成同时更新卡片状态文字：「已释放 N 项到 <路径>」

### 心跳不带可识别字串

- 对接插件写的心跳原来含 `local-xhook/1.0.0`，被扫到就是明确特征；现在只写时间戳

## v12.9.4（2026-11-26）

### 新增：DSH 对接做成一个包，装上即用

- 设置页新增「DSH 连接」三态卡片：未装 DSH / 未连接 / 已连接（心跳 5 分钟阈值），整卡可点直接跳转 DSH
- 「安装到 DSH」按钮把内置插件释放到控制目录 `dsh-plugin/`，只读不写 DSH 内部配置，出错不会弄坏引擎
- Manifest 增加 `io.github.ccch1mneyyy.dsh_app` 包可见性声明（否则 Android 11+ 查不到 DSH 装没装）
- **核心功能对 DSH 零依赖**：`HookEntry` 里 DSH 相关引用为 0，hook、注入、配方、脚本全部在模块内独立工作

## v12.9.3（2026-11-26）

### 修复：内置脚本一个都读不到

- `builtinScripts()` 在目标 App 进程里拿不到模块自己的 APK：`getCodeSource()` 返回的是宿主 APK，`/data/app` 又被 SELinux 挡住扫不到。两条路都断，内置脚本列表永远是空的，所以「开关是开的、页面没反应」
- 改成模块 App 启动时把自己的 APK 路径写进 prefs，目标进程通过 provider 读出来（这条通道一直是通的，注入指令就靠它）
- 实测：`builtin-scripts 9`、`script-inject 437 次`、`aw-eval-ok 822 次 / 0 失败`

### 修复：脚本开关

- `scriptOn` / `scriptOff` 两键不互斥，点「关闭」后旧键还在，界面永远读回「开」——改成置一个清另一个
- 「全部开启 / 全部关闭」原来只遍历 /sdcard 上的用户脚本目录（模块 uid 读不到），内置脚本和 prefs 里的用户脚本都不在循环里，但提示照样弹「已关闭」
- `syncScriptsToPrefs()` 每次刷新先清空所有 `script:` 键，再从读不到的目录往回填，用户导入的脚本当次就被吃光——目录读不到时直接返回
- 用户脚本开关只看 `.off` 文件，跟内置脚本的 prefs 逻辑不一致；统一走 prefs

### 修复：脚本注入被互相覆盖

- `CURRENT_JS` 只有一个槽，9 个脚本循环注入时互相覆盖，onDraw 重绘只能恢复最后一个，其余脚本的效果在页面重渲染后丢失。改成存「所有启用脚本的拼接」

### 修复：导入与预填

- 「加入脚本」只能写 /sdcard（模块 uid 写不进），改为主副本存 prefs 的 `script:<名字>`
- 导入的脚本名带着 `primary:` 和目录前缀，只取文件名
- `DshxpandhProvider` 加 `xposedsharedprefs` 声明前，目标进程读模块 prefs 一直失败（`has=false`）

### 修复：界面

- 开局权限引导只判断存储权限，且 `onCreate` 里 `return` 之后主界面根本没建 —— 从系统设置授权回来仍停在引导页。改成两项权限都齐才算通过，并在 `onResume` 重新判断后直接 `recreate()` 进主界面
- 调试日志开关有两套状态：界面看 `/sdcard` 上的标记文件、目标进程读 prefs，于是「界面显示开、实际是关，要点两次」。统一成只认 prefs
- 外观类脚本（网页反色 / 暗色模式）都在改整页样式，同时开必然互相覆盖；「全部开启」时只保留一个，列表里标「外观互斥」

### 调整

- 「示例3-去掉遮罩弹窗」加强：`MutationObserver` 持续清后出现的弹窗、`!important` 防被覆盖、解开 body 的滚动锁。同时去掉 `[class*="ad-"]` —— 它会误伤 `download-btn` / `head-top` 这类

## v12.9.2（2026-11-26）

### 修复

- 「全部开启 / 全部关闭」对内置脚本完全无效：原来只遍历 /sdcard 上的用户脚本目录，那个目录属主是 DSH，模块 uid 写不进去也读不到，内置脚本从来不在循环里，但提示照样弹「已关闭」
- 脚本开关状态改为写 prefs（`scriptOn:` / `scriptOff:` 两键互斥），不再依赖写 /sdcard。`builtinOn()` 先查 `scriptOn`，原来只置一个键不清另一个，点「关闭」之后旧键还在，界面永远读回「开」

## v12.9.1（2026-11-26）

### 修复

- v12.9.0 的 release 包是在「更新源改用 GitHub API」那次提交前 39 秒打的，dex 里没有这次修复，装 v12.9.0 的人检查更新会一直看到旧版本。本版重新编译并发布，补上这个缺口

## v12.9.0（2026-11-26）

### 新增

- 密钥页「作用域」卡：显示当前作用域，一键把已装的浏览器写进 LSPosed 配置
- 更新卡只保留「检查更新」和「打开 GitHub 仓库」两个按钮；检查结果可直接跳 Releases 页

### 修复

- `rootEditScope` 原来只写 `scope` 表，LSPosed 找不到 apk 路径，模块永远登记不上（界面报「缺少 LSPosed 授权」的根因）—— 现在 `modules` / `modules_state` / `scope` 三张表一起补
- 状态条误报未激活：登记表落后于实际注入时，改用注入探针的结果兜底
- 检查更新改用 GitHub API：`jsDelivr` 对 `@master` 有 12 小时缓存，发新版后仍报旧版本

## v12.8.6（2026-11-26）

### 新增：密钥页（Cookie 离线解密）

- 直接读浏览器的 Cookies 库并解密，不用插件、不用抓包
- 三种形态自动分：`v10` 密文解密 / `value` 列明文（WebView 内核）/ `v20` App-Bound 明确报错
- 扫描不写死浏览器名单：`/data/data/*/app_chrome|app_webview|app_u4_webview/…/Cookies` 全扫，一次扫描实测出 65 个库
- 按站点分组折叠，每组一个「复制头」，直接得到可用的 `Cookie:` 请求头
- 脱敏 / 明文一键切换；可导出 `/sdcard/DSHxpandh/cookies.json`

实测：Chrome 库 207 条 cookie 全部解出（0 失败），与参考实现逐字节一致。

### 修复

- 短值 cookie（`logged_in=yes`、`i18nextLng=en`）被判为解密失败 → 补短值判据
- `su -c` 落在本 App 的 mount namespace，扫不到别的 App 的 `/data/data` → 走 `nsenter -t 1 -m`
- 标签页显示顺序与点击目标错位（点「配方」跳到了密钥页）
- 未知包名在目标行重复显示

### 变更

- 新增第 6 个标签页「密钥」
- 推荐作用域扩到 7 个浏览器包（Chrome / 夸克 / UC / Edge / QQ 浏览器 / 系统浏览器 / 自带浏览器）

## v12.8.2（2026-11-25）

- 首个公开版本：抓明文 / 改参数 / 改返回值 / 注入网页 JS / 读 Cookie / 调内部方法六种能力
- 网页注入支持标准 WebView、腾讯 XWeb、Chromium 三种内核
- 隐身模式、调试日志开关