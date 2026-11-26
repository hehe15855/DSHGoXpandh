# 更新日志

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
- 脱敏 / 明文一键切换；可导出 `/sdcard/xhook/cookies.json`

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