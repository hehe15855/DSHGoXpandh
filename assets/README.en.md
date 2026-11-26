# DSHxpandh

[![LSPosed](https://img.shields.io/badge/LSPosed-API%2093%2B-blue)](https://github.com/LSPosed/LSPosed)
[![Android](https://img.shields.io/badge/Android-8.0%20~%2016-green)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![Release](https://img.shields.io/github/v/release/hehe15855/DSHGoXpandh)](https://github.com/hehe15855/DSHGoXpandh/releases/latest)

[简体中文](README.md) | **English**

> **A window into other apps.** See what happens inside, and change it — no repacking, no re-signing. Install once, stays active.

The usual way to change an app's behaviour on Android is to unpack it, edit smali, re-sign. Slow, and what you install back is a different package.

This module takes another route: hook through LSPosed and act **while the app runs**. Change a method's return value, log plaintext before encryption, inject JS into a page, decrypt browser cookies — configure once, applies on every launch.

**It can plug into DSH to turn hand-editing into conversation.** DSH is optional: recipes and scripts are plain text and work on their own; connect it and the assistant reads the capability list, writes recipes, and pushes scripts for you.

---

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

Ships with **13 recipes** and **9 web scripts**, each toggleable. All scripts default to off.

## Three WebView engines

| Engine | Where |
|---|---|
| `android.webkit.WebView` | Most in-app web views |
| Tencent XWeb | WeChat, QQ |
| Chromium `AwContents` | OPPO / Chrome / Quark / UC |

Injection runs on load plus a periodic pass, surviving re-renders.

## Stealth

On by default. **Main process and subprocesses both get it** (`:push` / `:sandbox` / `:downloads` are where detectors live).

Covers 18 categories / 22 hook points:

| Surface | What is blocked |
|---|---|
| **Class names** | `loadClass` throws `ClassNotFoundException` for any Xposed / LSPosed / libxposed class |
| **System properties** | `getProperty` / `SystemProperties.get` / `getInt` / `getBoolean` return empty for framework keys |
| **Environment** | `getenv(key)` and the no-arg `getenv()` (full table) both drop framework entries |
| **Suspicious paths** | `/data/adb/modules`, `lspd`, `xposedbridge.jar` — `exists` / `isFile` / `isDirectory` / `listFiles` all say no |
| **Package lists** | `getInstalledPackages(AsUser)` / `getInstalledApplications(AsUser)` / `queryIntentActivities` omit the module |
| **Single package** | `getPackageInfo` / `getApplicationInfo` (incl. `AsUser`) throw `NameNotFoundException` |
| **Processes & services** | `getRunningAppProcesses` / `getRunningServices` / `getRunningTasks` filtered |
| **Stack & reflection** | `getStackTrace` / `getAllStackTraces` drop framework frames; `getDeclaredMethods` / `getFields` drop `$xposed` members |
| **Annotations** | `getAnnotations` / `getDeclaredAnnotations` strip xposed-related annotations |
| **Process spawn** | `Runtime.exec` and `ProcessBuilder.start` refuse commands pointing at suspicious paths |
| **File reads** | NIO `Files.readAllBytes` / `Os.readlink` / `File.getCanonicalPath` blocked |
| **Debugger** | `isDebuggerConnected` / `waitingForDebugger` / `isNativeDebuggerConnected` always false |
| **Install source** | `getInstallerPackageName` answers `com.android.vending` for the module (hides sideloading) |
| **Signatures** | `checkSignatures` returns `SIGNATURE_NO_MATCH` when the module is involved |
| **Calling source** | `getCallingPackage` / `getCallingActivity` never expose the module |
| **Native libs** | `ClassLoader.loadLibrary` throws `UnsatisfiedLinkError` for frida / xposed / lspd / substrate |
| **ApplicationInfo** | `nativeLibraryDir` nulled when it carries module traces |
| **uid lookup** | `getPackagesForUid` filters the module out |

Disable with `stealth.off` in the control directory — but **the honest boundary** is: Java layer only.

Native code reading `/proc/self/maps` directly, ART page-attribute checks, vaddr scans — Java cannot see or block those.
Dedicated detectors (Holmes and similar) walk the native path and will still find traces. Stopping those needs a Zygisk module or kernel layer.

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
| LSPosed | API 93+; built on libxposed API 102 |
| Root | KernelSU / Magisk / APatch |
| Arch | Any — pure Java, no native libs |

## Build

```bash
export ANDROID_JAR=/path/to/platforms/android-36/android.jar
export XAPI_JAR=/path/to/xapi.jar   # optional; not needed for the libxposed path
bash build.sh
```

Ships `api-libxposed/` (source + jar) and `meta-xposed/`, so a fresh clone builds without extra setup.

## Disclaimer

For learning, research, and debugging your own devices.

## License

[MIT](LICENSE)