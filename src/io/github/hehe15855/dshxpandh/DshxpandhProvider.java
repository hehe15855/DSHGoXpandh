package io.github.hehe15855.dshxpandh;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * 配置跨进程通道。
 *
 * 为什么需要它：HookEntry 跑在目标 App 的进程里（用目标 App 的 uid），
 * 读不到本模块的私有目录；而把配置丢在 /sdcard 又会留下痕迹。
 * 走 ContentProvider 就绕开了两边：配置存在模块自己的 SharedPreferences 里，
 * 目标进程用 Binder 过来取，不碰文件系统、也不需要 root。
 *
 * 这与 luckyTool / 超星模块的做法一致（两者 /sdcard 命中数都是 0）。
 */
public class DshxpandhProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.hehe15855.dshxpandh.cfg";
    public static final String URI_STR = "content://" + AUTHORITY;
    public static final String PREFS = "dshxpandh";


    /**
     * 控制目录探测。不能复用 HookEntry 里的同名方法：那是 Xposed 模块类，
     * provider 跑在模块 App 进程，这里一旦触发它的静态初始化就可能 ClassNotFound。
     */
    static String ctrlRoot() {
        // 只认 /sdcard/DSHxpandh 一个目录。旧候选 /sdcard/Download/DSHxpandh 已废弃：
        // 两个目录并存时，模块和 DSH 插件可能各写一边，表现为「刚写的文件读不到」。
        String[] cands = { "/sdcard/DSHxpandh" };
        for (String c : cands) {
            try {
                java.io.File d = new java.io.File(c);
                if (!d.isDirectory()) continue;
                return c;
            } catch (Throwable t) { }
        }
        return null;
    }


    /** 文件名用的短标签：包名 SHA-1 前 8 位。扫目录的人反推不出是哪个 App。 */
    static String cmdTag(String pkg) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(pkg.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Throwable t) { return ""; }
    }

    /** 共享命令的混淆口令；插件侧用同一个串做 XOR。 */
    public static final String XOR_KEY = "xhk.2026.offline";

    /** base64 + XOR 还原。防的是「扫 /sdcard 找明文 js 关键词」，不是防逆向。 */
    static String unx(String s) {
        try {
            byte[] raw = android.util.Base64.decode(s, android.util.Base64.NO_WRAP);
            byte[] k = XOR_KEY.getBytes("UTF-8");
            for (int i = 0; i < raw.length; i++) raw[i] = (byte) (raw[i] ^ k[i % k.length]);
            return new String(raw, "UTF-8");
        } catch (Throwable t) { return null; }
    }

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        try {
            SharedPreferences p = getContext().getSharedPreferences(PREFS, 0);
            if ("getBool".equals(method)) {
                out.putBoolean("v", p.getBoolean(arg == null ? "" : arg, false));
            } else if ("getStr".equals(method)) {
                String k = arg == null ? "" : arg;
                // 不能直接 p.getString(k)：同一个 key 存的是 boolean 时它会抛
                // ClassCastException（不是返回默认值），异常被外层 catch 吃掉后
                // Bundle 里什么都没写，调用方收到 Bundle[{}] —— 看起来像「键不存在」。
                // 正确做法：先看它是哪种类型，再按类型取。
                String s2 = "";
                try {
                    Object raw = p.getAll().get(k);
                    if (raw instanceof String) s2 = (String) raw;
                    else if (raw instanceof Boolean) s2 = ((Boolean) raw).booleanValue() ? "1" : "0";
                    else if (raw instanceof Integer) s2 = String.valueOf(((Integer) raw).intValue());
                    else if (raw instanceof Long) s2 = String.valueOf(((Long) raw).longValue());
                    else if (raw instanceof Float) s2 = String.valueOf(((Float) raw).floatValue());
                } catch (Throwable t2) { }
                out.putString("v", s2);
            } else if ("has".equals(method)) {
                out.putBoolean("v", p.contains(arg == null ? "" : arg));
            } else if ("getCmd".equals(method)) {
                String pkg = arg == null ? "" : arg;
                String v = "";
                // 顺序很重要：先看共享命令文件（DSH 刚写进去的新指令），再回退 prefs（老机制）。
                // 原来反过来 —— prefs 里只要有一丁点残留，共享通道就永远读不到，表现为「下发了没反应」。
                String root = ctrlRoot();
                if (root != null && pkg.length() > 0) {
                    java.io.File f = new java.io.File(root + "/c-" + cmdTag(pkg) + ".bin");
                    try {
                        if (f.exists() && f.length() > 0) {
                            java.io.BufferedReader r = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
                            StringBuilder sb = new StringBuilder();
                            String ln;
                            while ((ln = r.readLine()) != null) sb.append(ln);
                            r.close();
                            v = unx(sb.toString());
                            if (v == null) v = "";
                            // 读完立刻删：命令只该存续一次读取的时间窗
                            try { f.delete(); } catch (Throwable t3) { }
                        }
                    } catch (Throwable t2) { }
                }
                if (v.length() == 0) v = p.getString("cmd", "");
                out.putString("v", v == null ? "" : v);
            } else if ("setCmd".equals(method)) {
                // 清空/改写 prefs 里那条命令。插件清命令时靠它，否则「清了却还在跑」。
                // 用 __clear__ 做清空哨兵：`content call --arg ""` 会被当成语法错误，传不进空串。
                String val = arg == null ? "" : arg;
                SharedPreferences.Editor ed = p.edit();
                if (val.length() == 0 || "__clear__".equals(val)) ed.remove("cmd");
                else ed.putString("cmd", val);
                out.putBoolean("ok", ed.commit());
            } else if ("setStr".equals(method)) {
                // 通用写开关：DSH 插件（proot 内）要把「脚本停用/恢复」「隐身」这类状态写进来，
                // 只有 provider 这条路能碰到 App 私有 prefs —— 写 /sdcard 文件目标进程常读不到。
                // 参数格式 "key:value"；value 为 "0" 时写 "0"（表示明确关闭），不是删除。
                String a = arg == null ? "" : arg;
                int c = a.indexOf(':');
                if (c <= 0) { out.putBoolean("ok", false); return out; }
                String k = a.substring(0, c);
                String v = a.substring(c + 1);
                SharedPreferences.Editor ed = p.edit();
                ed.putString(k, v);
                out.putBoolean("ok", ed.commit());
            } else if ("getScripts".equals(method)) {
                // 一次 IPC 把启用着的脚本全带走，省得每个脚本调一次
                Bundle all = new Bundle();
                for (java.util.Map.Entry<String, ?> e : p.getAll().entrySet()) {
                    String k = e.getKey();
                    if (k != null && k.startsWith("script:") && e.getValue() instanceof String) {
                        all.putString(k.substring(7), (String) e.getValue());
                    }
                }
                out.putBundle("all", all);
            }
        } catch (Throwable t) { }
        return out;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}