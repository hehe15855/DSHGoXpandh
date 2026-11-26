<div align="center">
<h1>DSHxpandh</h1>

<a href="https://github.com/Xposed-Modules-Repo/io.github.hehe15855.dshxpandh/stargazers"><img alt="GitHub stars" src="https://img.shields.io/github/stars/Xposed-Modules-Repo/io.github.hehe15855.dshxpandh?label=Stars"></a>
<a href="https://github.com/Xposed-Modules-Repo/io.github.hehe15855.dshxpandh/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/Xposed-Modules-Repo/io.github.hehe15855.dshxpandh/total?label=Downloads"></a>
<a href="https://github.com/Xposed-Modules-Repo/io.github.hehe15855.dshxpandh/releases/latest"><img alt="Latest" src="https://img.shields.io/github/v/release/Xposed-Modules-Repo/io.github.hehe15855.dshxpandh"></a>

<p>在别的 App 运行时开一扇窗：改方法返回值、记加解密明文、往网页注入 JS、解浏览器 Cookie。</p>

<p>
  <b>支持框架</b>：
  <b><a href="https://github.com/LSPosed/LSPosed">LSPosed</a></b>
</p>
</div>

---

## 项目简介

- 基于 **libxposed API 102** 开发（同时向下兼容经典 Xposed API 环境）
- 挂在 LSPosed 上，在目标 App **运行时**动手，不用拆包、不用重签
- **独立的 LSPosed 模块**：配方与脚本都是纯文本，手写即生效；接入 DSH 后可用对话配置（可选）

## 功能分类

| 类型 | 作用 | 启用方式 |
|---|---|---|
| 抓明文 | hook 加解密方法，记录入参出参 | 选配方，重启目标 App |
| 改参数 / 改结果 | 替换方法收到的值或固定返回值 | 同上 |
| 网页注入 | 往 WebView 塞 JS（标准 / 腾讯 XWeb / Chromium 三种内核） | 脚本丢进 `scripts/`，几秒生效 |
| 取登录态 | 解浏览器 Cookie（v10 密文与明文） | 模块「密钥」页直接读 |
| 调内部方法 | 直接调 App 内部方法拿值 | 配方 `call` 模式 |

自带 13 个配方、9 个网页脚本，均可单独开关，**默认全部关闭**。

## 隐身

默认开启，遮挡 **18 个面 / 22 处检测点**，主进程与子进程都装：
类名、系统属性、环境变量、可疑路径、包列表、单包查询、进程/服务/任务列表、
堆栈与反射、注解、`Runtime.exec`、`ProcessBuilder`、NIO 读取、`readlink`、
`getCanonicalPath`、`loadLibrary`、调试器、安装来源、签名比对、调用来源。

> 只能挡 Java 层。native 直接读 `/proc/self/maps`、内核级检测仍能看到痕迹。

## 兼容性

| 项 | 要求 |
|---|---|
| Android | 8.0（API 24）～ 16（API 36） |
| LSPosed | API 93+ |
| Root | KernelSU / Magisk / APatch |
| 架构 | 不限：纯 Java，无 native 库 |

## 链接

- 源码：https://github.com/hehe15855/DSHGoXpandh
- 更新日志：[CHANGELOG](https://github.com/hehe15855/DSHGoXpandh/blob/master/CHANGELOG.md)

## 声明

仅供学习研究与自有设备调试使用。