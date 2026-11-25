package io.github.lyx2839272_coder.dshxpandh;

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
public class XhookProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.lyx2839272_coder.dshxpandh.cfg";
    public static final String URI_STR = "content://" + AUTHORITY;
    public static final String PREFS = "xhook";

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        try {
            SharedPreferences p = getContext().getSharedPreferences(PREFS, 0);
            if ("getBool".equals(method)) {
                out.putBoolean("v", p.getBoolean(arg == null ? "" : arg, false));
            } else if ("getStr".equals(method)) {
                out.putString("v", p.getString(arg == null ? "" : arg, ""));
            } else if ("has".equals(method)) {
                out.putBoolean("v", p.contains(arg == null ? "" : arg));
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
