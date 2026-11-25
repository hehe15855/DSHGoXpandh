package io.github.lyx2839272_coder.dshxpandh;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class HookEntry implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    /**
     * 本类真的被加载进来了 = 这个进程确实被 hook 框架注入过。
     * 设置页直接问这个字段，就不用再靠探针进程间接推断 ——
     * LuckyTool 之类模块显示的「模块已激活」也是这个道理（YukiHookAPI 内部同样在查 XposedBridge）。
     */
    public static volatile boolean LOADED = false;

    private static final String TAG = "core";
    /** 本模块自己的包名：隐身 hook 必须排除它，否则会挡住自己的 su / 文件读取。改包名时只改这里。 */
    private static final String SELF = "io.github.lyx2839272_coder.dshxpandh";
    private static final String VER = "v142";
    // 控制目录：DSH 版优先，独立使用则落到通用目录（两者都可用，不需要改代码）
    private static final String ROOT = pickRoot();
    private static String pickRoot() {
        // 控制目录的选择原则：
        //   · /sdcard/DSH-Output/xhook 只有「已经有数据」时才沿用 —— 那是使用者自己的 DSH 输出目录，
        //     不是本模块的默认位置，新用户不该被迫生成它。
        //   · 否则落到中性目录 /sdcard/xhook。
        //   · 都不行才退到 Download。
        java.io.File dsh = new java.io.File("/sdcard/DSH-Output/xhook");
        if (dsh.isDirectory()) {
            String[] l = dsh.list();
            if (l != null && l.length > 0) return dsh.getPath();
        }
        java.io.File gen = new java.io.File("/sdcard/xhook");
        if (!gen.exists()) { try { gen.mkdirs(); } catch (Throwable t) {} }
        if (gen.isDirectory()) return gen.getPath();
        return "/sdcard/Download/xhook";
    }
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    // 全局注入开关：这两个文件任一存在即停掉 JS 注入（删掉即恢复）
    private static final String OFF_SWITCH = ROOT + "/off";
    // 隐身开关：存在即对目标进程隐藏 Xposed/LSPosed 特征
    private static final String STEALTH_SWITCH = ROOT + "/stealth";
    // 多脚本目录：每个 .js 一条命令，<名字>.off 表示停用
    private static final String SCRIPTS_DIR = ROOT + "/scripts";
    // 能力开关：abilities/<mode>.off 存在即不挂该模式的 hook
    private static final String ABIL_DIR = ROOT + "/abilities";
    // 检测器最常查的类名，命中就当作不存在
    private static final String[] STEALTH_CLASSES = {
        "de.robv.android.xposed.XposedBridge",
        "de.robv.android.xposed.XposedHelpers",
        "de.robv.android.xposed.XC_MethodHook",
        "de.robv.android.xposed.XposedInit",
        "de.robv.android.xposed.callbacks.XC_LoadPackage",
        "org.lsposed.lspd.core.Main",
        "org.lsposed.lspd.service.ILSPApplicationService",
        "org.lsposed.lspd.service.MagiskService",
        "org.lsposed.manager.App",
        "io.github.libxposed.api.XposedInterface"
    };
    // 前缀命中即隐藏：整包遮蔽，覆盖子类与内部类
    private static final String[] STEALTH_PREFIXES = {
        "de.robv.android.xposed.",
        "org.lsposed.",
        "io.github.libxposed.",
        "top.canyie.dreamland."
    };
    // 检测器常用来判断 root / 框架是否存在的路径片段
    private static final String[] STEALTH_PATHS = {
        "/data/adb/modules", "/data/adb/lspd", "/data/adb/ksu", "/data/adb/magisk",
        "/data/adb/ksud", "xposedbridge.jar", "de.robv.android.xposed", "lsposed"
    };
    // 环境变量 / 系统属性里常被拿去查的键
    private static final String[] STEALTH_PROPS = {
        "XPOSED", "LSPOSED", "LSPD", "MAGISK", "XPOSED_BRIDGE", "XPOSED_CLASS_DAG"
    };
    private static final String OFF_SWITCH_ALT = "/data/local/tmp/xhook.off";
    private static int clamp(int v, int lo, int hi, int def) {
        if (v < lo || v > hi) return def;
        return v;
    }
    private static final String[] CAND = {
        ROOT + "/cmd.json",
        "/data/local/tmp/cmd.json"
    };

    // 每进程日志总额度，用完即停，防止刷爆 LSPosed 日志
    private static final AtomicInteger LEFT = new AtomicInteger(2000);
    private static final java.util.List<java.lang.ref.WeakReference<Object>> WVS = new java.util.ArrayList<java.lang.ref.WeakReference<Object>>();
    private static final java.util.List<java.lang.ref.WeakReference<Object>> WCTABS = new java.util.ArrayList<java.lang.ref.WeakReference<Object>>();
    private static volatile String PENDING_JS = null;
    // 采样状态：key -> {windowStart, count}
    private static final Map<String, long[]> CTR = new java.util.concurrent.ConcurrentHashMap<String, long[]>();

    @Override
    public void initZygote(IXposedHookZygoteInit.StartupParam sp) {
        // 无状态，占位以兼容不同框架版本
    }

    private static File findAppCfg(String pkg) {
        String[] cands = {
            "/data/user/0/" + pkg + "/files/xhook.json",
            "/data/data/" + pkg + "/files/xhook.json"
        };
        for (String p : cands) {
            try {
                File f = new File(p);
                if (f.exists() && f.length() > 0) return f;
            } catch (Throwable t) {}
        }
        return null;
    }

    private static File findCmd(String pkg) {
        String[] cands = new String[CAND.length + 2];
        cands[0] = CAND[0];
        cands[1] = "/data/data/" + pkg + "/files/xhook.json";
        cands[2] = "/data/user/0/" + pkg + "/files/xhook.json";
        cands[CAND.length + 1] = CAND[1];
        for (int i = 3; i < CAND.length + 1; i++) cands[i] = CAND[i - 2];
        for (String p : cands) {
            try {
                File f = new File(p);
                if (f.exists() && f.length() > 0) return f;
            } catch (Throwable t) {}
        }
        return null;
    }

    // 采样：同一 key 在 windowMs 内最多 limit 次
    private static boolean allow(String key, int limit, long windowMs) {
        if (limit <= 0) return false;
        long now = System.currentTimeMillis();
        long[] st = CTR.get(key);
        if (st == null) {
            st = new long[]{now, 1L};
            CTR.put(key, st);
            return true;
        }
        synchronized (st) {
            if (now - st[0] >= windowMs) { st[0] = now; st[1] = 1L; return true; }
            if (st[1] < limit) { st[1]++; return true; }
            return false;
        }
    }

    /** 已经起过心跳的包，避免同一进程重复起线程 */
    private static final java.util.Set<String> BEATING = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /** 每 60 秒把 alive-<pkg> 的时间戳刷新一次，让「运行状态」有实时依据。 */
    private static void beat(final File f, final String procName, final String pkg) {
        try {
            String key = pkg + "@" + procName;
            if (!BEATING.add(key)) return;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    int pid = android.os.Process.myPid();
                    while (true) {
                        try {
                            Thread.sleep(60000L);
                            try {
                                FileOutputStream fo = new FileOutputStream(f, false);
                                fo.write((System.currentTimeMillis() + "\n" + procName + "\n" + pid).getBytes("UTF-8"));
                                fo.close();
                            } catch (Throwable te) { /* 写失败下一轮再试 */ }
                        } catch (Throwable tt) { return; }
                    }
                }
            }, "xhook-beat");
            t.setDaemon(true);
            t.start();
        } catch (Throwable t) {}
    }

    /**
     * 隐身开关：先问模块的配置通道（ContentProvider），取不到再回落文件。
     * provider 走 Binder，不依赖文件路径可见性，也不在 /sdcard 留东西。
     */
    /** 从模块的配置通道一次取回全部启用脚本；通道不可用返回 null（调用方回落扫目录） */
    private static java.util.Map<String, String> scriptsFromPrefs() {
        try {
            android.content.Context ctx = android.app.AndroidAppHelper.currentApplication();
            if (ctx == null) { emit("cfg", "prefs-no-ctx"); return null; }
            android.os.Bundle b = ctx.getContentResolver().call(
                    android.net.Uri.parse("content://" + XhookProvider.AUTHORITY),
                    "getScripts", null, null);
            if (b == null) { emit("cfg", "prefs-null-bundle"); return null; }
            android.os.Bundle all = b.getBundle("all");
            if (all == null) { emit("cfg", "prefs-null-all"); return null; }
            java.util.Map<String, String> m = new java.util.HashMap<String, String>();
            for (String k : all.keySet()) m.put(k, all.getString(k));
            emit("cfg", "prefs-ok " + m.size());
            return m;
        } catch (Throwable t) {
            emit("cfg", "prefs-err " + t.getClass().getSimpleName() + ":" + t.getMessage());
            return null;
        }
    }

    /** 从配置通道取一个字符串；通道不可用返回 null */
    private static String prefsStr(String key) {
        try {
            android.content.Context ctx = android.app.AndroidAppHelper.currentApplication();
            if (ctx == null) return null;
            android.os.Bundle b = ctx.getContentResolver().call(
                    android.net.Uri.parse("content://" + XhookProvider.AUTHORITY),
                    "getStr", key, null);
            if (b == null) return null;
            return b.getString("v");
        } catch (Throwable t) { return null; }
    }

    /**
     * 内置脚本：直接从模块 APK 的 assets/scripts/ 读。
     * 这样 /sdcard 上不用放副本，用户想加自己的再放那儿。
     */
    private static java.util.Map<String, String> builtinScripts() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<String, String>();
        java.util.zip.ZipFile z = null;
        try {
            String apk = null;
            try {
                apk = HookEntry.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            } catch (Throwable t) { }
            if (apk == null || apk.length() == 0) return m;
            if (apk.startsWith("file:")) apk = apk.substring(5);
            java.io.File f = new java.io.File(java.net.URLDecoder.decode(apk, "UTF-8"));
            if (!f.exists()) return m;
            z = new java.util.zip.ZipFile(f);
            java.util.Enumeration<? extends java.util.zip.ZipEntry> es = z.entries();
            while (es.hasMoreElements()) {
                java.util.zip.ZipEntry e = es.nextElement();
                String n = e.getName();
                if (!n.startsWith("assets/scripts/") || !n.endsWith(".js")) continue;
                String name = n.substring("assets/scripts/".length());
                java.io.InputStream is = null;
                try {
                    is = z.getInputStream(e);
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int k;
                    while ((k = is.read(buf)) > 0) bo.write(buf, 0, k);
                    m.put(name, new String(bo.toByteArray(), "UTF-8"));
                } catch (Throwable t) {
                } finally {
                    if (is != null) { try { is.close(); } catch (Throwable t) { } }
                }
            }
            emit("cfg", "builtin-scripts " + m.size());
        } catch (Throwable t) {
            emit("cfg", "builtin-err " + t.getClass().getSimpleName());
        } finally {
            if (z != null) { try { z.close(); } catch (Throwable t) { } }
        }
        return m;
    }

    /**
     * 这个脚本该不该跳过。
     * 规则：用户目录里有 .off → 停用；否则只有「示例1」默认开，其余要显式开过才跑。
     */
    private static boolean isScriptOff(String name) {
        try {
            if (new File(SCRIPTS_DIR, name + ".off").exists()) return true;
            String on = prefsStr("scriptOn:" + name);
            if ("1".equals(on)) return false;
            String off = prefsStr("scriptOff:" + name);
            if ("1".equals(off)) return true;
        } catch (Throwable t) { }
        // 默认只开那个无害的演示
        return !"示例1-链接标红.js".equals(name);
    }

    /**
     * 隐身开关：优先用 XSharedPreferences 直接读模块的 prefs 文件。
     * 走 provider 的话，模块 App 一被回收就返回 false，隐身会静默失效。
     */
    private static Boolean xspBool(String key) {
        try {
            de.robv.android.xposed.XSharedPreferences xp =
                    new de.robv.android.xposed.XSharedPreferences(SELF, "xhook");
            xp.reload();
            boolean has = xp.contains(key);
            boolean v = xp.getBoolean(key, false);
            emit("cfg", "xsp " + key + " has=" + has + " v=" + v);
            return has ? Boolean.valueOf(v) : null;
        } catch (Throwable t) {
            emit("cfg", "xsp-err " + t.getClass().getSimpleName());
            return null;
        }
    }

    private static boolean stealthOn() {
        Boolean xv = xspBool("stealth");
        if (xv != null) return xv.booleanValue();
        try {
            android.content.Context ctx = android.app.AndroidAppHelper.currentApplication();
            if (ctx != null) {
                android.os.Bundle b = ctx.getContentResolver().call(
                        android.net.Uri.parse("content://" + XhookProvider.AUTHORITY),
                        "getBool", "stealth", null);
                if (b != null && b.containsKey("v")) return b.getBoolean("v", false);
            }
        } catch (Throwable t) { /* provider 不可用 —— 走下面的兜底 */ }
        // 读不到配置时默认「隐身开」：模块 App 被系统回收后 provider 就没了，
        // 这时如果默认关，等于隐身静默失效、痕迹全暴露。宁可多隐一层。
        emit("cfg", "stealth-fallback-on");
        return true;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        LOADED = true;
        String pkg = "?";
        try {
            if (lp == null) return;
            pkg = String.valueOf(lp.packageName);
            if (lp.classLoader == null) return;
            if (stealthOn()) { SILENT = true; try { installStealth(pkg, lp.classLoader); } catch (Throwable ts) {} }
            try {
                // 2026-11-25：隐身不再删除 alive 标记。原逻辑会让模块失去「我在工作」的自证能力，
                // 而整个控制面（README/off/scripts/abilities）本来就在同一目录，删这一个文件不减少暴露。
                // 若日后要收紧隐身，应整体改控制面路径，而不是只藏证据。SILENT 日志静默不受影响。
                File ad = new File(ROOT);
                if (!ad.exists()) ad.mkdirs();
                String procName = safe(lp.processName);
                // 探针进程单独记一个文件：它是每次新起的进程，LSPosed 必须重新决定注不注入，
                // 所以「这条写得出来」就等于「模块此刻确实还启用着」—— 不需要 root 读配置。
                boolean isProbe = procName != null && procName.contains(":probe");
                final File aliveFile = new File(ad, (isProbe ? "probe-" : "alive-") + pkg);
                FileOutputStream afo = new FileOutputStream(aliveFile, false);
                afo.write((System.currentTimeMillis() + "\n" + procName + "\n" + android.os.Process.myPid()).getBytes("UTF-8"));
                afo.close();
                // 心跳：每 60 秒刷新一次时间戳。设置页据此判断「现在是否还有进程在跑」——
                // 只写一次的话，时间戳永远停在注入那一刻，模块关了也照样显示已连接。
                if (!isProbe) beat(aliveFile, procName, pkg);
            } catch (Throwable ta) {}
            emit(pkg, "attached " + safe(lp.processName));
            // 优先级：App 专属外部 > App 专属内置 > 全局外部 > 全局内置
            // 全局 /sdcard 放最后，否则一个文件会盖掉所有 App 的专属配置
            String src = null;
            try {
                String u0 = "/data/user/0/" + pkg + "/files/xhook.json";
                String dd = "/data/data/" + pkg + "/files/xhook.json";
                File fu = new File(u0), fd = new File(dd);
                emit(pkg, "probe u0=" + fu.exists() + "/" + fu.length() + " dd=" + fd.exists() + "/" + fd.length());
            } catch (Throwable t) { emit(pkg, "probe-err " + t.getClass().getSimpleName()); }
            File f = findAppCfg(pkg);
            if (f != null) { String t = readAll(f); if (t != null && t.length() > 0) src = "#ext:" + t; }
            // 用户自己的配置：/sdcard/Download/xhook/<包名>.json，优先于内置
            if (src == null) {
                String[] mine = {
                    "/sdcard/Download/xhook/" + pkg + ".json",
                    "/sdcard/xhook/" + pkg + ".json"
                };
                for (String mp : mine) {
                    try {
                        File mf = new File(mp);
                        if (mf.exists() && mf.length() > 0) {
                            String t = readAll(mf);
                            if (t != null && t.length() > 0) { src = "#usr:" + t; break; }
                            emit(pkg, "usr-noperm " + mp);
                        }
                    } catch (Throwable t) {}
                }
            }
            if (src == null) {
                String a = readAsset("assets/profiles/" + pkg + ".json");
                if (a != null) src = "#own:" + a;
            }
            if (src == null) {
                String[] globals = {
                    ROOT + "/config.json",
                    "/sdcard/xhook/config.json",
                    "/sdcard/Download/xhook.json",
                    "/data/local/tmp/xhook.json"
                };
                for (String gp : globals) {
                    try {
                        File gf = new File(gp);
                        if (gf.exists() && gf.length() > 0) {
                            String t = readAll(gf);
                            if (t != null && t.length() > 0) { src = "#glo:" + t; break; }
                        }
                    } catch (Throwable t) {}
                }
            }
            if (src == null) {
                String a = readAsset("assets/profiles/default.json");
                if (a != null) src = "#def:" + a;
            }
            if (src == null) { emit(pkg, "no cfg"); return; }
            String srcTag = src.substring(0, 4);
            Object root = MiniJson.parse(src.substring(5));
            if (!(root instanceof Map)) { emit(pkg, "bad json " + srcTag); return; }
            Map<String, Object> cfg = new java.util.HashMap<String, Object>();
            for (Map.Entry<?, ?> en : ((Map<?, ?>) root).entrySet()) {
                cfg.put(String.valueOf(en.getKey()), en.getValue());
            }
            // 配置里写 "recipe": "名称" 即套用 APK 内置配方

            String recName = s(cfg, "recipe");
            if (recName.length() > 0) {
                String rr = readAsset("assets/recipes/" + recName + ".json");
                if (rr == null) emit(pkg, "recipe-miss " + recName);
                else {
                    Object ro = MiniJson.parse(rr);
                    if (ro instanceof Map) {
                        Object rh = ((Map<?, ?>) ro).get("hooks");
                        if (rh instanceof List) {
                            List<Object> merged = new ArrayList<Object>();
                            Object oh = cfg.get("hooks");
                            if (oh instanceof List) merged.addAll((List<?>) oh);
                            merged.addAll((List<?>) rh);
                            cfg.put("hooks", merged);
                            emit(pkg, "recipe " + recName + " +" + ((List<?>) rh).size());
                        }
                    }
                }
            }
            List<?> targets = list(cfg, "enable_for");
            if (targets != null && targets.size() > 0) {
                boolean hit = false;
                for (Object t : targets) { if (pkg.equals(String.valueOf(t))) { hit = true; break; } }
                if (!hit) { emit(pkg, "not enabled"); return; }
            }
            // 进程过滤：默认只主进程，省掉子进程重复 hook
            String pmode = s(cfg, "process");
            if (pmode.length() == 0) pmode = "main";
            if (pmode.equals("main") && lp.processName != null && !lp.processName.equals(pkg)) {
                emit(pkg, "skip subproc " + lp.processName);
                return;
            }
            int ml = clamp(n(cfg, "max_logs", 2000), 1, 100000, 2000);
            LEFT.set(ml);
            int sample = clamp(n(cfg, "sample", 5), 1, 100, 5);
            int win = clamp(n(cfg, "window_ms", 1000), 100, 60000, 1000);
            // head/tail 优先；旧配置只写 max_len 时按对半折算，保持向后兼容
            int head = n(cfg, "head", -1);
            int tail = n(cfg, "tail", -1);
            int legacy = n(cfg, "max_len", -1);
            if (head < 0) head = legacy > 0 ? legacy / 2 : 200;
            if (tail < 0) tail = legacy > 0 ? legacy / 2 : 200;
            head = clamp(head, 0, 512, 200);
            tail = clamp(tail, 0, 512, 200);
            int nh = 0;
            List<?> hooks = list(cfg, "hooks");
            if (hooks != null) {
                for (Object o : hooks) {
                    if (!(o instanceof Map)) continue;
                    try { installHook(pkg, lp.classLoader, (Map<?, ?>) o, sample, win, head, tail); nh++; }
                    catch (Throwable t) { emit(pkg, "hook-err " + t); }
                }
            }
            // actions 需要 Context（CookieManager 靠它初始化），handleLoadPackage 时 Application 还没建
            final String fpkg = pkg;
            final ClassLoader fcl = lp.classLoader;
            final List<?> acts = list(cfg, "actions");
            if (acts != null && acts.size() > 0) {
                try {
                    XposedHelpers.findAndHookMethod("android.app.Application", lp.classLoader, "onCreate",
                        new XC_MethodHook() {
                            protected void afterHookedMethod(MethodHookParam mp) {
                                try {
                                    for (Object o : acts) {
                                        if (!(o instanceof Map)) continue;
                                        try { runAction(fpkg, fcl, (Map<?, ?>) o, mp.thisObject); }
                                        catch (Throwable t) { emit(fpkg, "act-err " + t); }
                                    }
                                } catch (Throwable t) { emit(fpkg, "act-loop " + t); }
                            }
                        });
                } catch (Throwable t) { emit(pkg, "act-hook-fail " + t.getClass().getSimpleName()); }
            }
            try { startCmdChannel(pkg, lp.classLoader); } catch (Throwable ts0) { emit(pkg, "cmd-start-err " + ts0); }
            try { startChromiumInject(pkg, lp.classLoader); } catch (Throwable ts1) { emit(pkg, "chr-start-err " + ts1); }
            emit(pkg, "ready " + VER + " " + srcTag + " hooks=" + nh + " sample=" + sample + "/" + win + "ms head=" + head + " tail=" + tail);
        } catch (Throwable t) {
            emit(pkg, "fatal " + t);
        }
    }
    private void installHook(final String pkg, ClassLoader cl, Map<?, ?> h,
                            final int sample, final long win, final int head, final int tail) {
        String mtd = s(h, "method");
        if (mtd.length() == 0) return;
        List<?> setList = list(h, "set");
        Map<?, ?> whenMap = m(h, "when");
        List<?> clsList = list(h, "classes");
        if (clsList != null && clsList.size() > 0) {
            for (Object co : clsList) {
                String c = String.valueOf(co);
                if (c.length() > 0) hookOne(pkg, cl, c, mtd, s(h, "mode"), s(h, "return_value"), setList, whenMap, sample, win, head, tail, s(h, "js"), s(h, "value"), s(h, "match"));
            }
            return;
        }
        String cls = s(h, "class");
        if (cls.length() == 0) return;
        hookOne(pkg, cl, cls, mtd, s(h, "mode"), s(h, "return_value"), setList, whenMap, sample, win, head, tail, s(h, "js"), s(h, "value"), s(h, "match"));
    }

    private void hookOne(final String pkg, ClassLoader cl, final String cls, final String mtd,
                         final String mode, final String ret, final List<?> setList, final Map<?, ?> whenMap,
                         final int sample, final long win, final int head, final int tail, final String js, final String val, final String match) {
        Class<?> c = null;
        try { c = XposedHelpers.findClass(cls, cl); }
        catch (Throwable t1) {
            String abil = (mode == null || mode.length() == 0) ? "log" : mode;
            if (new File(ABIL_DIR + "/" + abil + ".off").exists()) return;
            // 降级：按短名在 classLoader 里找
            try { c = Class.forName(cls, false, cl); } catch (Throwable t2) { emit(pkg, "class-miss " + cls); return; }
        }
        final String key = cls + "." + mtd;
        int cnt = 0;
        for (final Method m : c.getDeclaredMethods()) {
            if (!m.getName().equals(mtd)) continue;
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            if (mode.equals("args")) { applyArgs(pkg, key, p, setList, sample, win); return; }
                            // when: 只在指定参数含关键词时改写，否则放行
                            if (whenMap != null && whenMap.size() > 0 && !matchWhen(p.args, whenMap)) return;
                            if (!allow(key, sample, win)) return;
                            emit(pkg, "IN " + key + "(" + arr(p.args, head, tail) + ")");
                        } catch (Throwable t) {}
                    }
                    protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            if (mode.equals("js")) {
                                try {
                                    Object wv = null;
                                    Class<?> wvc = Class.forName("android.webkit.WebView");
                                    if (p.thisObject != null && wvc.isInstance(p.thisObject)) wv = p.thisObject;
                                    if (wv == null && p.args != null) { for (Object a : p.args) { if (a != null && wvc.isInstance(a)) { wv = a; break; } } }
                                    if (wv != null && js != null && js.length() > 0) {
                                        emit(pkg, "js-attach");
                                        String jsRun = js;
                                        try { jsRun = jsRun.replace("__VAL__", esc(val)).replace("__MATCH__", esc(match)); } catch (Throwable t2) {}
                                        final Object[] A = new Object[]{ wv, jsRun };
                                        final int[] C = new int[]{ 0 };
                                        final Runnable[] T = new Runnable[1];
                                        T[0] = new Runnable() { public void run() { try { XposedHelpers.callMethod(A[0], "evaluateJavascript", new Object[]{ A[1], null }); if (C[0]++ < 60) XposedHelpers.callMethod(A[0], "postDelayed", new Object[]{ T[0], 2000L }); } catch (Throwable te2) { } } };
                                        try { XposedHelpers.callMethod(wv, "post", new Object[]{ T[0] }); } catch (Throwable te2) { }
                                    }
                                } catch (Throwable te) { emit(pkg, "js-err " + te); }
                                return;
                            }
                            if (mode.equals("return") && ret.length() > 0) {
                                if (allow(key, sample, win)) {
                                    p.setResult(cast(p.getResult(), ret));
                                    emit(pkg, "OVR " + key + " -> " + ret);
                                }
                                return;
                            }
                            if (!allow(key, sample, win)) return;
                            emit(pkg, "OUT " + key + " -> " + brief(p.getResult(), head, tail));
                        } catch (Throwable t) {}
                    }
                });
                cnt++;
            } catch (Throwable t) {
                emit(pkg, "hook-conflict " + key + " " + t);
            }
        }
        emit(pkg, "hooked " + key + " x" + cnt);
    }

    private void runAction(String pkg, ClassLoader cl, Map<?, ?> a, Object ctx) {
        String type = s(a, "type");
        try {
            if (type.equals("call")) {
                String cls = s(a, "class");
                String mtd = s(a, "method");
                Class<?> c = XposedHelpers.findClass(cls, cl);
                Object v = null;
                for (Method m : c.getDeclaredMethods()) {
                    if (!m.getName().equals(mtd)) continue;
                    if (m.getParameterTypes().length != 0) continue;
                    m.setAccessible(true);
                    v = m.invoke(null);
                    break;
                }
                emit(pkg, "ACT " + cls + "." + mtd + " -> " + brief(v, 300, 300));
            } else if (type.equals("cookie")) {
                String url = s(a, "url");
                if (url.length() == 0) url = "https://www.google.com";
                Class<?> cm = XposedHelpers.findClass("android.webkit.CookieManager", cl);
                Object inst;
                try { inst = XposedHelpers.callStaticMethod(cm, "getInstance", ctx); }
                catch (Throwable t1) { inst = XposedHelpers.callStaticMethod(cm, "getInstance"); }
                Object v = XposedHelpers.callMethod(inst, "getCookie", url);
                emit(pkg, "COOKIE " + url + " -> " + brief(v, 400, 400));
            }
        } catch (Throwable t) {
            emit(pkg, "act-fail " + type + " " + t);
        }
    }

    private static Map<?, ?> m(Map<?, ?> src, String k) {
        Object o = src == null ? null : src.get(k);
        return o instanceof Map ? (Map<?, ?>) o : null;
    }

    private static boolean matchWhen(Object[] args, Map<?, ?> w) {
        if (args == null) return false;
        int i = n(w, "i", -1);
        if (i < 0 || i >= args.length) return false;
        String want = s(w, "contains");
        Object a = args[i];
        String got = a == null ? "" : String.valueOf(a);
        if (want.length() == 0) return true;
        return got.contains(want);
    }

    // 改入参：按 set 列表逐位替换，记录改动前后
    private static void applyArgs(String pkg, String key, XC_MethodHook.MethodHookParam p, List<?> setList, int sample, long win) {
        if (p == null || p.args == null || setList == null) return;
        StringBuilder sb = new StringBuilder();
        for (Object o : setList) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            int i = n(m, "i", -1);
            String v = s(m, "v");
            if (i < 0 || i >= p.args.length) { sb.append("bad-idx").append(i).append(" "); continue; }
            Object old = p.args[i];
            try {
                p.args[i] = cast(old, v);
                sb.append(i).append(":[").append(brief(old, 60, 20)).append("]->[").append(v).append("] ");
            } catch (Throwable t) {
                sb.append(i).append(":ERR ").append(t.getClass().getSimpleName()).append(" ");
            }
        }
        if (allow(key + ":arg", sample, win)) emit(pkg, "ARG " + key + " " + sb.toString());
    }

    private static Object cast(Object old, String v) {
        try {
            if (old instanceof Boolean) return Boolean.valueOf(Boolean.parseBoolean(v));
            if (old instanceof Integer) return Integer.valueOf(Integer.parseInt(v));
            if (old instanceof Long) return Long.valueOf(Long.parseLong(v));
            if (old instanceof Short) return Short.valueOf(Short.parseShort(v));
            if (old instanceof Byte) return Byte.valueOf(Byte.parseByte(v));
            if (old instanceof Float) return Float.valueOf(Float.parseFloat(v));
            if (old instanceof Double) return Double.valueOf(Double.parseDouble(v));
            if (old instanceof byte[]) return v.getBytes("UTF-8");
            return v;
        } catch (Throwable t) { return v; }
    }

    private static String arr(Object[] a, int head, int tail) {
        if (a == null || a.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(", "); sb.append(brief(a[i], head, tail)); }
        return sb.toString();
    }

    // 大 buffer 只取 head + tail，中间省略，长度照实报告
    private static String brief(Object o, int head, int tail) {
        if (o == null) return "null";
        try {
            if (o instanceof byte[]) {
                byte[] b = (byte[]) o;
                if (b.length <= head + tail) return hexish(b, 0, b.length);
                return "b[" + b.length + "] " + hexish(b, 0, head) + " ...(+ " + (b.length - head - tail) + " B)... " + hexish(b, b.length - tail, tail);
            }
            if (o.getClass().isArray()) return "arr[" + Array.getLength(o) + "]";
            String v = String.valueOf(o);
            if (v.length() <= head + tail) return v;
            return v.substring(0, head) + " ...(+ " + (v.length() - head - tail) + " chars)... " + v.substring(v.length() - tail);
        } catch (Throwable t) { return "?"; }
    }

    private static String hexish(byte[] b, int off, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = off; i < off + len && i < b.length; i++) {
            int v = b[i] & 0xff;
            if (v >= 32 && v < 127) sb.append((char) v);
            else { sb.append('\\'); sb.append(HEX[v >> 4]); sb.append(HEX[v & 0xf]); }
        }
        return sb.toString();
    }

    private static String esc(String v) { if (v == null) return ""; StringBuilder b = new StringBuilder(); for (int i = 0; i < v.length(); i++) { char c = v.charAt(i); if (c == '\\' || c == '\'') { b.append('\\').append(c); } else if (c == '\n') { b.append("\\n"); } else if (c == '\r') { b.append("\\r"); } else { b.append(c); } } return b.toString(); }

    /** 类名是否属于要隐藏的范围：全名命中，或落在被遮蔽的包前缀下。 */
    private static boolean isStealthClass(String n) {
        if (n == null) return false;
        for (String s : STEALTH_CLASSES) { if (n.equals(s) || n.startsWith(s + "$")) return true; }
        for (String s : STEALTH_PREFIXES) { if (n.startsWith(s)) return true; }
        return false;
    }

    /** 隐身：类名伪装 + 属性/环境变量/映射/包列表一并遮挡。 */
    private void installStealth(final String pkg, final ClassLoader cl) {
        try {
            // 注意：不 hook Class.forName —— 实测 hook 它会让目标 App 的类加载失败（androidx 启动期 CNFE 闪退）。
            // 类名遮挡只走 ClassLoader.loadClass 一条路径。
            if (false) {
            Class<?> klass = Class.forName("java.lang.Class");
            XposedBridge.hookAllMethods(klass, "forName", new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (p.args == null || p.args.length == 0 || !(p.args[0] instanceof String)) return;
                        String n = (String) p.args[0];
                        if (isStealthClass(n) && !new File(ROOT + "/classhide.off").exists()) p.setThrowable(new ClassNotFoundException(n));
                    } catch (Throwable t) { /* 隐身逻辑异常绝不能崩目标 App */ }
                }
            });
            }
            {
            Class<?> loader = Class.forName("java.lang.ClassLoader");
            XposedBridge.hookAllMethods(loader, "loadClass", new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (SELF.equals(pkg)) return;
                        if (p.args == null || p.args.length == 0 || !(p.args[0] instanceof String)) return;
                        String n = (String) p.args[0];
                        if (isStealthClass(n) && !new File(ROOT + "/classhide.off").exists()) p.setThrowable(new ClassNotFoundException(n));
                    } catch (Throwable t) { /* 隐身逻辑异常绝不能崩目标 App */ }
                }
            });
            }
            // 系统属性与环境变量：常见探测点
            try {
                Class<?> sys = Class.forName("java.lang.System");
                XposedBridge.hookAllMethods(sys, "getProperty", new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        if (p.args == null || p.args.length == 0 || !(p.args[0] instanceof String)) return;
                        String k = ((String) p.args[0]).toUpperCase();
                        for (String s : STEALTH_PROPS) { if (k.contains(s)) { p.setResult(null); return; } }
                    }
                });
                XposedBridge.hookAllMethods(sys, "getenv", new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        if (p.args == null || p.args.length == 0 || !(p.args[0] instanceof String)) return;
                        String k = ((String) p.args[0]).toUpperCase();
                        for (String s : STEALTH_PROPS) { if (k.contains(s)) { p.setResult(null); return; } }
                    }
                });
            } catch (Throwable t) {}

            // 进程映射 / 属性文件：把带 xposed 标识的行抹掉
            try {
                Class<?> br = Class.forName("java.io.BufferedReader");
                XposedBridge.hookAllMethods(br, "readLine", new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        Object r = p.getResult();
                        if (!(r instanceof String)) return;
                        String low = ((String) r).toLowerCase();
                        if (low.length() < 400 && (low.contains("xposed") || low.contains("lsposed") || low.contains("lspd") || low.contains("magisk"))) {
                            p.setResult(" ");
                        }
                    }
                });
            } catch (Throwable t) {}

            // 已安装包列表：去掉 LSPosed 管理器等模块侧包名
            try {
                Class<?> pm = Class.forName("android.app.ApplicationPackageManager");
                XC_MethodHook iph = new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        Object r = p.getResult();
                        if (!(r instanceof java.util.List)) return;
                        java.util.List<?> list = (java.util.List<?>) r;
                        for (int i = list.size() - 1; i >= 0; i--) {
                            Object o = list.get(i);
                            if (o == null) continue;
                            try {
                                Object v = o.getClass().getField("packageName").get(o);
                                if (v instanceof String && !v.equals(pkg) && isStealthClass((String) v)) list.remove(i);
                            } catch (Throwable t2) {}
                        }
                    }
                };
                XposedBridge.hookAllMethods(pm, "getInstalledPackages", iph);
                XposedBridge.hookAllMethods(pm, "getInstalledPackagesAsUser", iph);
            } catch (Throwable t) {}

            // 类的成员：Xposed 会往被 hook 的类里塞 $xposed 开头的静态字段/方法，反射一扫就露馅
            try {
                Class<?> kc = Class.forName("java.lang.Class");
                XC_MethodHook mh = new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            if (SELF.equals(pkg)) return;
                            Object r = p.getResult();
                            if (!(r instanceof Object[])) return;
                            Object[] arr = (Object[]) r;
                            java.util.ArrayList<Object> keep = new java.util.ArrayList<Object>();
                            for (Object o : arr) {
                                if (o == null) continue;
                                String n = "";
                                try { n = String.valueOf(o.getClass().getMethod("getName").invoke(o)); } catch (Throwable t2) {}
                                if (n.startsWith("$xposed") || n.toLowerCase().contains("xposed")) continue;
                                keep.add(o);
                            }
                            if (keep.size() == arr.length) return;
                            Class<?> ct = arr.getClass().getComponentType();
                            Object[] out = (Object[]) java.lang.reflect.Array.newInstance(ct, keep.size());
                            for (int i = 0; i < keep.size(); i++) out[i] = keep.get(i);
                            p.setResult(out);
                        } catch (Throwable t2) { }
                    }
                };
                XposedBridge.hookAllMethods(kc, "getDeclaredMethods", mh);
                XposedBridge.hookAllMethods(kc, "getDeclaredFields", mh);
                XposedBridge.hookAllMethods(kc, "getMethods", mh);
                XposedBridge.hookAllMethods(kc, "getFields", mh);
            } catch (Throwable t) {}

            // 栈帧：异常栈里不应出现 xposed / lsposed 的帧
            try {
                Class<?> th = Class.forName("java.lang.Throwable");
                XposedBridge.hookAllMethods(th, "getStackTrace", new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        Object r = p.getResult();
                        if (!(r instanceof StackTraceElement[])) return;
                        StackTraceElement[] arr = (StackTraceElement[]) r;
                        java.util.ArrayList<StackTraceElement> keep = new java.util.ArrayList<StackTraceElement>();
                        for (StackTraceElement e : arr) {
                            if (e == null) continue;
                            String cn = e.getClassName();
                            if (cn != null && isStealthClass(cn)) continue;
                            keep.add(e);
                        }
                        if (keep.size() != arr.length) p.setResult(keep.toArray(new StackTraceElement[keep.size()]));
                    }
                });
            } catch (Throwable t) {}

            // 资源查找：模块的 jar 里有 xposed_init，getResource 一扫就露馅
            try {
                Class<?> clc = Class.forName("java.lang.ClassLoader");
                XC_MethodHook rh = new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            if (p.args == null || p.args.length == 0 || !(p.args[0] instanceof String)) return;
                            String n = ((String) p.args[0]).toLowerCase();
                            if (n.contains("xposed") || n.contains("lsposed") || n.contains("edxp")) p.setResult(null);
                        } catch (Throwable t2) { }
                    }
                };
                XposedBridge.hookAllMethods(clc, "getResource", rh);
                XposedBridge.hookAllMethods(clc, "getResourceAsStream", rh);
            } catch (Throwable t) {}

            // 敏感目录的列表：列出来就看见模块的目录名
            try {
                if (new File(ROOT + "/stealth-file.off").exists()) throw new RuntimeException("skip");
                Class<?> fc2 = Class.forName("java.io.File");
                XposedBridge.hookAllMethods(fc2, "listFiles", new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            // 模块自己的进程要读 LSPosed 配置库，不能挡
                            if (SELF.equals(pkg)) return;
                            Object self = p.thisObject;
                            if (self == null) return;
                            String low = String.valueOf(self).toLowerCase();
                            boolean hit = false;
                            for (String sp : STEALTH_PATHS) { if (low.contains(sp)) { hit = true; break; } }
                            if (!hit) return;
                            Object r = p.getResult();
                            if (!(r instanceof Object[])) return;
                            Object[] arr = (Object[]) r;
                            java.util.ArrayList<Object> keep = new java.util.ArrayList<Object>();
                            for (Object o : arr) {
                                if (o == null) continue;
                                String n = String.valueOf(o).toLowerCase();
                                if (n.contains("xposed") || n.contains("lsposed") || n.contains("magisk")) continue;
                                keep.add(o);
                            }
                            if (keep.size() == arr.length) return;
                            Object[] out = (Object[]) java.lang.reflect.Array.newInstance(arr.getClass().getComponentType(), keep.size());
                            for (int i = 0; i < keep.size(); i++) out[i] = keep.get(i);
                            p.setResult(out);
                        } catch (Throwable t2) { }
                    }
                });
            } catch (Throwable t) {}

            // Thread 的栈帧入口（检测工具常用 getAllStackTraces）
            try {
                Class<?> thc = Class.forName("java.lang.Thread");
                XC_MethodHook sh = new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            if (SELF.equals(pkg)) return;
                            Object r = p.getResult();
                            if (!(r instanceof StackTraceElement[])) return;
                            StackTraceElement[] arr = (StackTraceElement[]) r;
                            java.util.ArrayList<StackTraceElement> keep = new java.util.ArrayList<StackTraceElement>();
                            for (StackTraceElement e : arr) {
                                if (e == null) continue;
                                String cn = e.getClassName();
                                if (cn != null && isStealthClass(cn)) continue;
                                keep.add(e);
                            }
                            if (keep.size() != arr.length) p.setResult(keep.toArray(new StackTraceElement[keep.size()]));
                        } catch (Throwable t2) { }
                    }
                };
                XposedBridge.hookAllMethods(thc, "getStackTrace", sh);
                XposedBridge.hookAllMethods(thc, "getAllStackTraces", sh);
            } catch (Throwable t) {}

            // 应用列表：getInstalledApplications 也要滤掉模块侧包名
            try {
                Class<?> pm = Class.forName("android.app.ApplicationPackageManager");
                XposedBridge.hookAllMethods(pm, "getInstalledApplications", new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        Object r = p.getResult();
                        if (!(r instanceof java.util.List)) return;
                        java.util.List<?> list = (java.util.List<?>) r;
                        for (int i = list.size() - 1; i >= 0; i--) {
                            Object o = list.get(i);
                            if (o == null) continue;
                            try {
                                Object v = o.getClass().getField("packageName").get(o);
                                if (v instanceof String && !v.equals(pkg) && isStealthClass((String) v)) list.remove(i);
                            } catch (Throwable t2) {}
                        }
                    }
                });
                // 精确查 LSPosed 包名 = 不存在
                XC_MethodHook pih = new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam p) {
                        if (SELF.equals(pkg)) return;
                        if (p.args == null || p.args.length == 0 || !(p.args[0] instanceof String)) return;
                        String n = (String) p.args[0];
                        if (n.equals(pkg)) return;  // 自己查自己放行，否则版本号读不出来
                        if (isStealthClass(n)) p.setThrowable(new android.content.pm.PackageManager.NameNotFoundException(n));
                    }
                };
                // 公开 API 与 AsUser 变体都要盖住，否则检测工具换个入口查就能比对出「行为不一致」
                XposedBridge.hookAllMethods(pm, "getPackageInfo", pih);
                XposedBridge.hookAllMethods(pm, "getPackageInfoAsUser", pih);
            } catch (Throwable t) {}

            // root / magisk 探测：拦 su 与 magisk 相关命令（默认关闭，见 stealth-exec 标记）
            try {
                if (new File(ROOT + "/stealth-exec.off").exists()) throw new RuntimeException("skip");
                Class<?> rt = Class.forName("java.lang.Runtime");
                XposedBridge.hookAllMethods(rt, "exec", new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam p) {
                        // 模块自己要跑 su 读配置，不能拦
                        if (SELF.equals(pkg)) return;
                        String cmd = null;
                        if (p.args != null && p.args.length > 0) {
                            Object a0 = p.args[0];
                            if (a0 instanceof String) cmd = (String) a0;
                            else if (a0 instanceof String[]) {
                                StringBuilder sb = new StringBuilder();
                                for (String s : (String[]) a0) { if (s != null) sb.append(s).append(' '); }
                                cmd = sb.toString();
                            }
                        }
                        if (cmd == null) return;
                        String low = cmd.toLowerCase();
                        if (low.contains("su ") || low.contains("/su") || low.contains("which su") || low.contains("magisk")) {
                            p.setThrowable(new java.io.IOException("Permission denied"));
                        }
                    }
                });
            } catch (Throwable t) {}

            // 调试状态：把 TracerPid 行伪装成 0
            try {
                Class<?> br2 = Class.forName("java.io.BufferedReader");
                XposedBridge.hookAllMethods(br2, "readLine", new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        Object r = p.getResult();
                        if (!(r instanceof String)) return;
                        String s = (String) r;
                        if (s.toLowerCase().contains("tracerpid")) p.setResult("TracerPid:\t0");
                    }
                });
            } catch (Throwable t) {}

            // 痕迹路径：exists / isFile / isDirectory 一律回答「没有」（开关 stealth-file）
            try {
                if (new File(ROOT + "/stealth-file.off").exists()) throw new RuntimeException("skip");
                Class<?> fc = Class.forName("java.io.File");
                XC_MethodHook fh = new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        if (SELF.equals(pkg)) return;
                        if (!Boolean.TRUE.equals(p.getResult())) return;
                        Object self = p.thisObject;
                        if (self == null) return;
                        String low = String.valueOf(self).toLowerCase();
                        for (String sp : STEALTH_PATHS) {
                            if (low.contains(sp)) { p.setResult(Boolean.FALSE); return; }
                        }
                    }
                };
                XposedBridge.hookAllMethods(fc, "exists", fh);
                XposedBridge.hookAllMethods(fc, "isFile", fh);
                XposedBridge.hookAllMethods(fc, "isDirectory", fh);
            } catch (Throwable t) {}

            // Android 的 SystemProperties（与 System.getProperty 是两套 API）
            try {
                Class<?> sp = XposedHelpers.findClassIfExists("android.os.SystemProperties", cl);
                if (sp != null) {
                    XposedBridge.hookAllMethods(sp, "get", new XC_MethodHook() {
                        protected void afterHookedMethod(MethodHookParam p) {
                            if (p.args == null || p.args.length == 0 || !(p.args[0] instanceof String)) return;
                            String k = ((String) p.args[0]).toUpperCase();
                            for (String s : STEALTH_PROPS) { if (k.contains(s)) { p.setResult(""); return; } }
                        }
                    });
                }
            } catch (Throwable t) {}

            // 另外两个包查询入口：queryIntentActivities / getInstalledModules
            try {
                Class<?> pm2 = Class.forName("android.app.ApplicationPackageManager");
                XposedBridge.hookAllMethods(pm2, "queryIntentActivities", new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        Object r = p.getResult();
                        if (!(r instanceof java.util.List)) return;
                        java.util.List<?> list = (java.util.List<?>) r;
                        for (int i = list.size() - 1; i >= 0; i--) {
                            Object ri = list.get(i);
                            if (ri == null) continue;
                            try {
                                Object ai = ri.getClass().getField("activityInfo").get(ri);
                                if (ai == null) continue;
                                Object pn = ai.getClass().getField("packageName").get(ai);
                                if (pn instanceof String && isStealthClass((String) pn)) list.remove(i);
                            } catch (Throwable t2) {}
                        }
                    }
                });
                XposedBridge.hookAllMethods(pm2, "getInstalledModules", new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        Object r = p.getResult();
                        if (!(r instanceof java.util.List)) return;
                        java.util.List<?> list = (java.util.List<?>) r;
                        for (int i = list.size() - 1; i >= 0; i--) {
                            Object mi = list.get(i);
                            if (mi == null) continue;
                            try {
                                Object pn = mi.getClass().getField("packageName").get(mi);
                                if (pn instanceof String && isStealthClass((String) pn)) list.remove(i);
                            } catch (Throwable t2) {}
                        }
                    }
                });
            } catch (Throwable t) {}

            emit(pkg, "stealth-on");
        } catch (Throwable t) { emit(pkg, "stealth-fail " + t); }
    }

    private void startCmdChannel(final String pkg, ClassLoader cl) {
        try {
            Class<?> wv = XposedHelpers.findClass("android.webkit.WebView", cl);
            // 以前只 hook loadUrl —— 用 loadData / loadDataWithBaseURL 加载的页面完全抓不到。
            // 改成 hook 构造函数，任何 WebView 实例一创建就登记。
            XposedBridge.hookAllConstructors(wv, new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) {
                    if (p.thisObject != null) { synchronized (WVS) { WVS.add(new java.lang.ref.WeakReference<Object>(p.thisObject)); } }
                }
            });
            // loadUrl 保留：有些 WebView 是系统内部复用的实例，构造函数可能早于模块加载
            XposedBridge.hookAllMethods(wv, "loadUrl", new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) { if (p.thisObject != null) { synchronized (WVS) { WVS.add(new java.lang.ref.WeakReference<Object>(p.thisObject)); } } }
            });
            // 腾讯 XWeb（微信/QQ 等用）：接口和标准 WebView 一致，同样登记
            try {
                Class<?> xwv = XposedHelpers.findClass("com.tencent.xweb.WebView", cl);
                XC_MethodHook xh = new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        if (p.thisObject != null) { synchronized (WVS) { WVS.add(new java.lang.ref.WeakReference<Object>(p.thisObject)); } }
                    }
                };
                XposedBridge.hookAllConstructors(xwv, xh);
                XposedBridge.hookAllMethods(xwv, "loadUrl", xh);
                emit(pkg, "xweb-hook-on");
            } catch (Throwable tx) { emit(pkg, "xweb-skip " + tx.getClass().getSimpleName()); }
            emit(pkg, "cmd-channel-on");
        } catch (Throwable t) { emit(pkg, "cmd-hook-err " + t); return; }
        Thread th = new Thread(new Runnable() {
            public void run() {
                long last = 0L;
                java.util.HashMap<String, Integer> lastScripts = new java.util.HashMap<String, Integer>();
                long lastForce = 0L;
                while (true) {
                    try {
                        Thread.sleep(2500L);
                        // 每 12 秒全量重注入一次：页面加载/切换会新建 WebView，
                        // 只在内容变化时注入会漏掉这些新页面。脚本都写成幂等的，重复执行无副作用。
                        long nowMs = System.currentTimeMillis();
                        // onDraw 不常触发，靠它保活不够；3 秒兜底一次，用 LAST_DRAWN_AW 打真页面
                        if (nowMs - lastForce > 3000L) { lastForce = nowMs; lastScripts.clear(); }
                        // 全局开关：控制目录里出现 off 文件就整段跳过，删掉即恢复
                        if (new File(OFF_SWITCH).exists() || new File(OFF_SWITCH_ALT).exists()) continue;
                        // 脚本来源：APK 内置的 + 用户自己放目录里的（同名以用户的为准）
                        try {
                            java.util.Map<String, String> all = new java.util.LinkedHashMap<String, String>();
                            all.putAll(builtinScripts());
                            File sd = new File(SCRIPTS_DIR);
                            File[] fs = sd.listFiles();
                            if (fs != null) {
                                for (File f : fs) {
                                    String n = f.getName();
                                    if (!n.endsWith(".js")) continue;
                                    String code = readAll(f);
                                    if (code != null && code.length() > 0) all.put(n, code);
                                }
                            }
                            for (java.util.Map.Entry<String, String> e : all.entrySet()) {
                                String n = e.getKey();
                                String code = e.getValue();
                                if (code == null || code.length() == 0) continue;
                                if (isScriptOff(n)) continue;
                                int h = code.hashCode();
                                Integer prev = lastScripts.get(n);
                                if (prev != null && prev.intValue() == h) continue;
                                lastScripts.put(n, Integer.valueOf(h));
                                injectAll(code);
                                emit(pkg, "script-inject(" + n + ") " + n + " " + code.length());
                            }
                        } catch (Throwable tsc) {}
                        // 指令：优先从模块的配置通道取（模块写 /sdcard 那套跟这里从没对上过）
                        String s = prefsStr("cmd");
                        if (s == null || s.length() == 0) {
                            // 回落：目标 App 自己的私有目录（留给手工投放的场合）
                            String[] cands = new String[]{ "/data/user/0/" + pkg + "/files/xhook.cmd.json", "/data/data/" + pkg + "/files/xhook.cmd.json" };
                            for (String pth : cands) {
                                try {
                                    File fl = new File(pth);
                                    if (fl.exists() && fl.length() > 0) { s = readAll(fl); if (s != null && s.length() > 0) break; }
                                } catch (Throwable t0) { }
                            }
                        }
                        if (s == null || s.length() == 0) continue;
                        Object o = MiniJson.parse(s);
                        if (!(o instanceof Map)) continue;
                        Map<?, ?> mm = (Map<?, ?>) o;
                        Object tsv = mm.get("ts");
                        long ts = tsv instanceof Number ? ((Number) tsv).longValue() : 0L;
                        if (ts <= last) continue;
                        last = ts;
                        Object jsv = mm.get("js");
                        if (jsv == null) continue;
                        String js = String.valueOf(jsv);
                        if (js.length() == 0) continue;
                        js = js.replace("__MATCH__", esc(mm.get("match") == null ? "" : String.valueOf(mm.get("match")))).replace("__VAL__", esc(mm.get("value") == null ? "" : String.valueOf(mm.get("value"))));
                        injectAll(js);
                        PENDING_JS = js;
                        emit(pkg, "cmd-pending");
                    } catch (Throwable t1) {}
                }
            }
        });
        th.setDaemon(true);
        th.start();
    }
    private static int nAwDummy = 0;
    /** 当前要注入的脚本：onDraw 每次重绘都拿它注入，这样页面重渲染后能自动恢复 */
    private static volatile String CURRENT_JS = null;
    /** onDraw 注入限流时间戳 */
    private static volatile long lastDrawInject = 0L;
    /** 最后一次真正绘制过的 AwContents —— 它才是当前页面，WCTABS 里那几个多半是旧的 */
    private static volatile Object LAST_DRAWN_AW = null;

    private void injectAll(final String js) {
        java.util.List<Object> alive = new java.util.ArrayList<Object>();
        synchronized (WVS) {
            java.util.Iterator<java.lang.ref.WeakReference<Object>> it = WVS.iterator();
            while (it.hasNext()) { Object w = it.next().get(); if (w == null) it.remove(); else alive.add(w); }
        }
        // Chromium：主动遍历。之前崩是因为回调传 null，现在传空实现，可以主动调了。
        // 不能只靠 onDraw 钩子 —— 页面静止时不重绘，注入永远轮不到。
        int nAw = 0;
        java.util.List<Object> aws = new java.util.ArrayList<Object>();
        synchronized (WCTABS) {
            java.util.Iterator<java.lang.ref.WeakReference<Object>> it = WCTABS.iterator();
            while (it.hasNext()) { Object w = it.next().get(); if (w == null) it.remove(); else aws.add(w); }
        }
        // 优先用最近绘制过的那个（真页面）；它不在 WCTABS 里时，遍历只是补充
        Object drawn = LAST_DRAWN_AW;
        if (drawn != null) {
            try {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    public void run() { try { evalAw(drawn, js); } catch (Throwable t) {} }
                });
                nAw++;
            } catch (Throwable t) {}
        }
        for (final Object aw : aws) {
            if (aw == drawn) continue;
            try {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    public void run() { try { evalAw(aw, js); nAwDummy++; } catch (Throwable t) {} }
                });
                nAw++;
            } catch (Throwable t) {}
        }
        try { CURRENT_JS = js; } catch (Throwable t) { }
        emit("inject", "targets std=" + alive.size() + " chromium=" + nAw);
        for (final Object w : alive) {
            try { XposedHelpers.callMethod(w, "post", new Object[]{ new Runnable() { public void run() { try { XposedHelpers.callMethod(w, "evaluateJavascript", new Object[]{ js, null }); } catch (Throwable t) {} } } }); } catch (Throwable t) {}
        }
    }

    private void startChromiumInject(final String pkg, ClassLoader cl) {
        try {
            Class<?> d = XposedHelpers.findClass("org.chromium.android_webview.AwContents", cl);
            XC_MethodHook h = new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Object aw = p.thisObject;
                        if (aw != null) { synchronized (WCTABS) { WCTABS.add(new java.lang.ref.WeakReference<Object>(aw)); } emit(pkg, "aw-tab"); }
                    } catch (Throwable t) {}
                }
            };
            XposedBridge.hookAllConstructors(d, h);
            // 每次重绘都注入：页面实际在用的 AwContents 往往不是 WCTABS 里那几个（模块加载前就建好了），
            // 只有 onDraw 里的 thisObject 才是真在画的那个。
            XC_MethodHook dh = new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Object self = p.thisObject;
                        if (self == null) return;
                        LAST_DRAWN_AW = self;   // 记住它，定时兜底时用它注入
                        String cj = CURRENT_JS;
                        if (cj == null) return;
                        // 限流：onDraw 每帧都可能触发，不限速会每秒执行几十次 JS
                        long now = System.currentTimeMillis();
                        if (now - lastDrawInject < 800L) return;
                        lastDrawInject = now;
                        evalAw(p.thisObject, cj);
                    } catch (Throwable t) { }
                }
            };
            XposedBridge.hookAllMethods(d, "onDraw", dh);
            XposedBridge.hookAllMethods(d, "onSizeChanged", dh);
            emit(pkg, "chromium-hook-on");
        } catch (Throwable t) { emit(pkg, "chromium-hook-err " + t); }
    }
    private Object chromiumTab(Object delegate) {
        if (delegate == null) return null;
        for (java.lang.reflect.Field f : delegate.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            String tn = f.getType().getName();
            if (tn.contains("TabImpl")) { try { f.setAccessible(true); return f.get(delegate); } catch (Throwable t) {} }
        }
        return null;
    }
    private void injectChromium(final String js) {
        java.util.List<Object> alive = new java.util.ArrayList<Object>();
        synchronized (WCTABS) { java.util.Iterator<java.lang.ref.WeakReference<Object>> it = WCTABS.iterator(); while (it.hasNext()) { Object w = it.next().get(); if (w == null) it.remove(); else alive.add(w); } }
        final String fjs = js;
        for (final Object aw : alive) {
            try {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        emit("chromium", "inj-cls " + aw.getClass().getName());
                        try {
                            for (java.lang.reflect.Method m : aw.getClass().getMethods()) {
                                if (!m.getName().toLowerCase().contains("evaluatejavascript")) { if (m.getName().toLowerCase().contains("javascript")) emit("chromium", "m-" + m.getName()); continue; }
                                Class<?>[] pt = m.getParameterTypes();
                                if (pt.length >= 1 && pt[0] == String.class) { Object[] a = new Object[pt.length]; a[0] = fjs; for (int i = 1; i < pt.length; i++) a[i] = null; m.setAccessible(true); m.invoke(aw, a); emit("chromium", "aw-eval-ok"); return; }
                            }
                            Class<?> lup = Class.forName("org.chromium.content_public.browser.LoadUrlParams", false, aw.getClass().getClassLoader());
                            Object params = newLoadUrlParams(lup, "javascript:" + android.net.Uri.encode(fjs));
                            if (params != null) for (java.lang.reflect.Method m : aw.getClass().getMethods()) { if (!m.getName().equals("loadUrl")) continue; Class<?>[] pt = m.getParameterTypes(); if (pt.length == 1 && pt[0] == lup) { m.setAccessible(true); m.invoke(aw, params); emit("chromium", "aw-loadurl-ok"); break; } }
                        } catch (Throwable t) {}
                    }
                });
            } catch (Throwable t) {}
        }
    }
    private Object newLoadUrlParams(Class<?> lup, String url) {
        try {
            for (java.lang.reflect.Constructor<?> c : lup.getDeclaredConstructors()) { java.lang.Class<?>[] pt = c.getParameterTypes(); if (pt.length == 1 && pt[0] == String.class) { c.setAccessible(true); return c.newInstance(url); } }
            for (java.lang.reflect.Constructor<?> c : lup.getDeclaredConstructors()) { java.lang.Class<?>[] pt = c.getParameterTypes(); c.setAccessible(true); if (pt.length == 2 && pt[0] == int.class && pt[1] == String.class) return c.newInstance(0, url); if (pt.length == 2 && pt[0] == String.class && pt[1] == int.class) return c.newInstance(url, 0); }
        } catch (Throwable t) {}
        return null;
    }

    private void evalAw(Object aw, String js) {
        if (aw == null || js == null) return;
        try {
            // 回调不能传 null：Chromium 内部会直接解引用，之前就是这么 native 崩的
            android.webkit.ValueCallback<String> cb = new android.webkit.ValueCallback<String>() {
                public void onReceiveValue(String v) { }
            };
            for (java.lang.reflect.Method m : aw.getClass().getMethods()) {
                if (!m.getName().toLowerCase().contains("evaluatejavascript")) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length < 1 || pt[0] != String.class) continue;
                Object[] a = new Object[pt.length];
                a[0] = js;
                for (int i = 1; i < pt.length; i++) {
                    a[i] = (i == 1 && pt[i].getName().contains("ValueCallback")) ? cb : null;
                }
                m.setAccessible(true);
                m.invoke(aw, a);
                emit("chromium", "aw-eval-ok");
                return;
            }
            emit("chromium", "aw-eval-nomethod");
        } catch (Throwable t) { emit("chromium", "aw-eval-err " + t.getClass().getSimpleName()); }
    }

    private static String safe(String v) { return v == null ? "?" : v; }
    private static String s(Map<?, ?> m, String k) { Object v = m.get(k); return v == null ? "" : String.valueOf(v); }
    private static int n(Map<?, ?> m, String k, int def) { Object v = m.get(k); return v instanceof Number ? ((Number) v).intValue() : def; }
    private static List<?> list(Map<?, ?> m, String k) { Object v = m.get(k); return v instanceof List ? (List<?>) v : null; }

    // 读 APK 内置配置（assets/profiles/*.json）
    private static String readAsset(String path) {
        java.io.InputStream is = null;
        try {
            is = HookEntry.class.getClassLoader().getResourceAsStream(path);
            if (is == null) return null;
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            is.close();
            String s = new String(bo.toByteArray(), "UTF-8");
            return s.length() == 0 ? null : s;
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (is != null) is.close(); } catch (Throwable t) {}
        }
    }

    private static String readAll(File f) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String ln;
            while ((ln = br.readLine()) != null) sb.append(ln);
            br.close();
            return sb.toString();
        } catch (Throwable t) { return ""; }
    }

    // 总额度控制：日志不落盘，只走 XposedBridge.log，且条数封顶
    private static volatile boolean SILENT = false;

    private static void emit(String pkg, String msg) {
        if (SILENT) return;
        int left = LEFT.decrementAndGet();
        if (left < 0) return;
        if (left == 0) {
            try { XposedBridge.log(TAG + " budget exhausted"); } catch (Throwable t) {}
        }
        try { XposedBridge.log(TAG + " [" + pkg + "] " + msg); } catch (Throwable t) {}
    }
    static class MiniJson {
        private final String s;
        private int i;
        private MiniJson(String t) { s = t; }
        static Object parse(String t) {
            try { MiniJson p = new MiniJson(t); p.ws(); return p.val(); } catch (Throwable e) { return null; }
        }
        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        private Object val() {
            if (i >= s.length()) return null;
            char c = s.charAt(i);
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"') return str();
            return lit();
        }
        private Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            i++; ws();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (i < s.length()) {
                ws();
                if (i < s.length() && s.charAt(i) == '}') { i++; break; }
                String k = str();
                ws();
                if (i < s.length() && s.charAt(i) == ':') i++;
                ws();
                m.put(k, val());
                ws();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                if (i < s.length() && s.charAt(i) == '}') { i++; break; }
                break;
            }
            return m;
        }
        private List<Object> arr() {
            List<Object> l = new ArrayList<Object>();
            i++; ws();
            if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
            while (i < s.length()) {
                ws();
                if (i < s.length() && s.charAt(i) == ']') { i++; break; }
                l.add(val());
                ws();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                if (i < s.length() && s.charAt(i) == ']') { i++; break; }
                break;
            }
            return l;
        }
        private String str() {
            StringBuilder sb = new StringBuilder();
            if (i < s.length() && s.charAt(i) == '"') i++;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '\\') {
                    if (i >= s.length()) break;
                    char e = s.charAt(i++);
                    if (e == 'n') sb.append('\n');
                    else if (e == 't') sb.append('\t');
                    else if (e == 'r') sb.append('\r');
                    else if (e == 'u' && i + 4 <= s.length()) {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    } else sb.append(e);
                } else if (c == '"') break;
                else sb.append(c);
            }
            return sb.toString();
        }
        private Object lit() {
            int st = i;
            while (i < s.length() && ",}] ".indexOf(s.charAt(i)) < 0) i++;
            String t = s.substring(st, i).trim();
            if (t.equals("true")) return Boolean.TRUE;
            if (t.equals("false")) return Boolean.FALSE;
            if (t.equals("null")) return null;
            try { return Double.valueOf(t); } catch (Throwable e) { return t; }
        }
    }
}