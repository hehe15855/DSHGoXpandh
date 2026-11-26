# DSHxpandh

[![LSPosed](https://img.shields.io/badge/LSPosed-API%2093%2B-blue)](https://github.com/LSPosed/LSPosed)
[![Android](https://img.shields.io/badge/Android-8.0%20~%2016-green)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

[简体中文](README.md) | **English**

> **A window into other apps.** See what happens inside, and change it — no repacking, no re-signing. Install once, stays active.

The usual way to change an app's behaviour on Android is to unpack it, edit smali, re-sign. Slow, and what you install back is a different package.

This module takes another route: hook through LSPosed and act **while the app runs**. Change a method's return value, log plaintext before encryption, inject JS into a page, decrypt browser cookies — configure once, applies on every launch.

**It can plug into DSH to turn hand-editing into conversation.** DSH is optional: recipes and scripts are plain text and work on their own; connect it and the assistant reads the capability list, writes recipes, and pushes scripts for you.

## What it does

Six modes, all runtime:

| Mode | What it does | Typical use |
|---|---|---|
| **log** | Record arguments and return value | Plaintext before encryption, API responses |
| **args** | Replace received values | Try inputs, observe reactions |
| **return** | Force a return value | Make a check always pass or fail |
| **js** | Inject a script into a WebView | Restyle, kill popups, autofill, read DOM |
| **cookie** | Read cookies (incl. HttpOnly) | Reuse sessions |
| **call** | Invoke an internal method | Pull data without a trigger |

Ships with **13 recipes** and **9 web scripts**, each toggleable.

## Three WebView engines

| Engine | Where |
|---|---|
| `android.webkit.WebView` | Most in-app web views |
| Tencent XWeb | WeChat, QQ |
| Chromium `AwContents` | OPPO / Chrome / Quark / UC |

Injection runs on load plus a 12-second pass, surviving re-renders.

## Where the boundary is

**Java layer only.** If the logic lives in a `.so`, no recipe will fire.

| Layer | How to tell | Tool |
|---|---|---|
| **Java** | jadx yields names; traces show `com.xxx.Yyy.method(` | **This module** |
| **native** | only a `native` declaration | rizin / Ghidra |
| **WebView** | DOM, fetch, localStorage | **This module** (.js) |
| **On disk** | SQLite, mmkv, prefs | root |

> Can this logic be found by `class.method`? Yes → this module. Only inside `.so` → patch the binary. Already in a file → read with root.

**Out of scope:** native self-checks, APK modification, native UI / Flutter / Canvas, apps not scoped in LSPosed.

## Install

1. Download from [Releases](https://github.com/hehe15855/DSHGoXpandh/releases/latest)
2. Enable in **LSPosed Manager**
3. Scope the target app
4. **Restart that app**
5. Pick a recipe or write your own

## Compatibility

| Item | Requirement |
|---|---|
| Android | 8.0 (API 24) – 16 (API 36) |
| LSPosed | API 93+ |
| Root | KernelSU / Magisk / APatch |
| Arch | Any — pure Java |

## Build

```bash
export ANDROID_JAR=/path/to/platforms/android-36/android.jar
export XAPI_JAR=/path/to/xapi.jar
bash build.sh
```

## Disclaimer

For learning, research, and debugging your own devices.

## License

[MIT](LICENSE)
