package io.github.hehe15855.dshxpandh;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cookie 离线解密。
 *
 * 三种取值方式，按数据库里每条记录的实际形态自动选：
 *   v10    —— 3 字节前缀 + AES-128-CBC（密钥 peanuts/saltysalt 派生，IV 是 16 个空格）
 *   plain  —— value 列直接是明文（Quark / UC / OPPO 这类 WebView 内核的库）
 *   v20    —— App-Bound，密钥不在磁盘上，解不开就明确说，不假装成功
 *
 * 与 python 脚本 chrome-cookie-decrypt.py 的行为逐条对齐：
 *   解密用 CBC/NoPadding（自己剥 PKCS#7），因为 IV 不对时首块必乱，
 *   而末块的 padding 仍然是对的 —— 让 Cipher 自动去 padding 反而会抛异常。
 */
final class CookieVault {

    /**
     * 扫本机所有 Chromium 系的 Cookies 库，返回 { 包名, 包内相对路径, 显示名 }。
     *
     * 不写死浏览器名单：任何 App 只要用 Chromium（自带内核 / WebView / XWeb），
     * 库都长在这几个位置之一。扫出来就直接能解，不用等适配。
     */
    static String[][] discover(Root root) {
        if (root == null) return new String[0][];
        // 关键：su 落在本 App 自己的 mount namespace，看不见别的 App 的 /data/data
        //（实测直接 ls 会报 No such file）—— 必须进 init 的 namespace 才扫得到。
        String inner = "ls -d /data/data/*/app_chrome/Default/Cookies"
                + " /data/data/*/app_webview/Default/Cookies"
                + " /data/data/*/app_u4_webview/Default/Cookies"
                + " /data/data/*/app_chrome/Profile*/Cookies"
                + " /data/user_de/0/*/app_chrome/Default/Cookies"
                + " 2>/dev/null | sort -u";
        String out = root.exec("nsenter -t 1 -m -- sh -c '" + inner + "'", 15000L);
        if (out == null) return new String[0][];
        List<String[]> found = new ArrayList<String[]>();
        String[] lines = out.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String p = lines[i].trim();
            String base = "/data/data/";
            int at = p.indexOf(base);
            if (at < 0) { base = "/data/user_de/0/"; at = p.indexOf(base); }
            if (at < 0) continue;
            String rest = p.substring(at + base.length());
            int slash = rest.indexOf("/");
            if (slash <= 0) continue;
            String pkg = rest.substring(0, slash);
            String rel = rest.substring(slash + 1);
            if (pkg.length() == 0 || rel.length() == 0) continue;
            found.add(new String[] { pkg, rel, prettyName(pkg, rel) });
        }
        return found.toArray(new String[found.size()][]);
    }

    /** 已知包名给个中文名，未知的显示包名本身（不猜） */
    private static String prettyName(String pkg, String rel) {
        for (int i = 0; i < BROWSERS.length; i++) {
            if (BROWSERS[i][0].equals(pkg) && BROWSERS[i][1].equals(rel)) return BROWSERS[i][2];
        }
        for (int i = 0; i < BROWSERS.length; i++) {
            if (BROWSERS[i][0].equals(pkg)) return BROWSERS[i][2];
        }
        String tail = rel.indexOf("Profile") >= 0 ? rel.substring(rel.indexOf("Profile")) : "";
        return pkg + (tail.length() > 0 ? " " + tail : "");
    }

    /** 一条 cookie 的结果。how = v10 / v10-hash / plain / v20 / fail */
    static final class Row {
        String host = "";
        String name = "";
        String how = "";
        String value = "";
    }

    /** 一次扫描汇总 */
    static final class Result {
        final List<Row> rows = new ArrayList<Row>();
        int v10 = 0, plain = 0, v20 = 0, fail = 0;
        String note = "";
        boolean ok = false;
        String dbPath = "";
    }

    /** 已知浏览器的 Cookies 位置：包名 / 包内相对路径 / 显示名 */
    static final String[][] BROWSERS = {
        { "com.android.chrome",        "app_chrome/Default/Cookies",          "Chrome" },
        { "com.chrome.beta",           "app_chrome/Default/Cookies",          "Chrome Beta" },
        { "com.chrome.dev",            "app_chrome/Default/Cookies",          "Chrome Dev" },
        { "com.microsoft.emmx",        "app_chrome/Default/Cookies",          "Edge" },
        { "com.quark.browser",         "app_u4_webview/Default/Cookies",      "夸克" },
        { "com.UCMobile",              "app_u4_webview/Default/Cookies",      "UC" },
        { "com.heytap.browser",        "app_webview/Default/Cookies",         "OPPO 浏览器" },
        { "com.tencent.mtt",           "app_webview/Default/Cookies",         "QQ 浏览器" },
        { "com.android.browser",       "app_webview/Default/Cookies",         "系统浏览器" }
    };

    /** root 执行通道，由 SettingsActivity 注入（它已经有 su 探活与超时处理） */
    interface Root {
        String exec(String cmd, long timeoutMs);
    }

    private CookieVault() { }

    /** PBKDF2-SHA1("peanuts","saltysalt",1) → 16 字节 AES-128 密钥 */
    static byte[] deriveKey() {
        try {
            PBEKeySpec spec = new PBEKeySpec("peanuts".toCharArray(),
                    "saltysalt".getBytes("UTF-8"), 1, 128);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return f.generateSecret(spec).getEncoded();
        } catch (Throwable t) {
            return null;
        }
    }

    /** v10：3 字节前缀 + AES-128-CBC（NoPadding，padding 自己剥） */
    static byte[] decryptV10(byte[] blob, byte[] key) {
        if (blob == null || key == null || blob.length < 3 + 16) return null;
        if (!(blob[0] == (byte) 'v' && blob[1] == (byte) '1' && blob[2] == (byte) '0')) return null;
        int len = blob.length - 3;
        if (len % 16 != 0) return null;
        try {
            Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
            byte[] iv = new byte[16];
            for (int i = 0; i < 16; i++) iv[i] = (byte) 0x20;   // Chrome 用 16 个空格
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return c.doFinal(blob, 3, len);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 剥掉 CBC 首块与（新版本 Chrome 的）32 字节 host 哈希。
     * 返回 null 表示这段不是可读明文。
     */
    static byte[] stripPlaintext(byte[] buf) {
        if (buf == null || buf.length <= 16) return null;
        byte[] strict = cut(buf, true);
        if (strict != null) return strict;
        byte[] loose = cut(buf, false);
        if (loose != null) return loose;
        return cutShort(buf);
    }

    /**
     * 短值兜底（logged_in=yes / i18nextLng=en 这类）。
     * 它们落在一个块内，去 padding 后只有几个字节；判据是「1..16 字节且 100% 可打印」。
     * 别嫌它松：卡太严会漏掉一大批短 cookie（实测 Chrome 207 条里 12 条栽在这）。
     */
    private static byte[] cutShort(byte[] buf) {
        int[] cuts = { 16, 32, 48 };
        for (int ci = 0; ci < cuts.length; ci++) {
            int c = cuts[ci];
            if (buf.length <= c) continue;
            byte[] v = new byte[buf.length - c];
            System.arraycopy(buf, c, v, 0, v.length);
            int pad = v[v.length - 1] & 0xff;
            if (pad >= 1 && pad <= 16 && pad <= v.length) {
                boolean allPad = true;
                for (int i = v.length - pad; i < v.length; i++) {
                    if ((v[i] & 0xff) != pad) { allPad = false; break; }
                }
                if (allPad) {
                    byte[] t = new byte[v.length - pad];
                    System.arraycopy(v, 0, t, 0, t.length);
                    v = t;
                }
            }
            if (v.length >= 1 && v.length <= 16 && printableRatio(v) == 1.0) return v;
        }
        return null;
    }

    private static byte[] cut(byte[] buf, boolean strict) {
        int[] cuts = { 16, 32, 48 };
        for (int ci = 0; ci < cuts.length; ci++) {
            int c = cuts[ci];
            if (buf.length <= c) continue;
            byte[] v = new byte[buf.length - c];
            System.arraycopy(buf, c, v, 0, v.length);
            int pad = v[v.length - 1] & 0xff;
            if (pad >= 1 && pad <= 16 && pad <= v.length) {
                boolean allPad = true;
                for (int i = v.length - pad; i < v.length; i++) {
                    if ((v[i] & 0xff) != pad) { allPad = false; break; }
                }
                if (allPad) {
                    byte[] t = new byte[v.length - pad];
                    System.arraycopy(v, 0, t, 0, t.length);
                    v = t;
                }
            }
            if (v.length == 0) continue;
            double p = printableRatio(v);
            if (strict) {
                if (p > 0.98 && (startsWith(v, "eyJ") || startsWith(v, "{"))) return v;
            } else if (v.length > 4 && p > 0.98) {
                return v;
            }
        }
        return null;
    }

    private static double printableRatio(byte[] v) {
        int n = 0;
        for (int i = 0; i < v.length; i++) {
            int b = v[i] & 0xff;
            if (b >= 32 && b < 127) n++;
        }
        return v.length == 0 ? 0 : ((double) n) / v.length;
    }

    private static boolean startsWith(byte[] v, String s) {
        if (v.length < s.length()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (v[i] != (byte) s.charAt(i)) return false;
        }
        return true;
    }

    /** 把某条记录解成明文；解不开时 how 说明原因 */
    private static Row decode(String host, String name, String plain, byte[] blob, byte[] key) {
        Row r = new Row();
        r.host = host == null ? "" : host;
        r.name = name == null ? "" : name;
        if (blob != null && blob.length >= 3
                && blob[0] == (byte) 'v' && blob[1] == (byte) '2' && blob[2] == (byte) '0') {
            r.how = "v20";
            r.value = "";
            return r;
        }
        if (blob != null && blob.length > 3) {
            byte[] pt = decryptV10(blob, key);
            if (pt != null) {
                byte[] val = stripPlaintext(pt);
                if (val != null && val.length > 0) {
                    r.how = "v10";
                    r.value = new String(val);          // 明文里可能是 UTF-8 中文
                    return r;
                }
            }
        }
        if (plain != null && plain.length() > 0) {
            r.how = "plain";
            r.value = plain;
            return r;
        }
        r.how = "fail";
        r.value = "";
        return r;
    }

    /**
     * 拉库 + 解密。必须在子线程调用（su 会阻塞）。
     *
     * @param hostFilter null / 空 = 全部；否则 host_key 或 name 包含该串才算
     */
    static Result harvest(Context ctx, Root root, String pkg, String rel, String hostFilter) {
        Result res = new Result();
        if (root == null) { res.note = "没有 root 通道"; return res; }
        String src = "/data/data/" + pkg + "/" + rel;
        File dir = new File(ctx.getFilesDir(), "ckvault");
        try { dir.mkdirs(); } catch (Throwable t) { }
        File dst = new File(dir, "ck-" + System.currentTimeMillis() + ".db");
        String uid = String.valueOf(android.os.Process.myUid());
        String dstPath = dst.getAbsolutePath();
        // WAL 也要一起拿，否则最近写入的 cookie 会缺
        // 同一个原因：读别的 App 的数据目录必须进 init namespace
        String inner = "cp " + src + " " + dstPath + " 2>/dev/null; "
                + "cp " + src + "-wal " + dstPath + "-wal 2>/dev/null; "
                + "cp " + src + "-shm " + dstPath + "-shm 2>/dev/null; "
                + "chown " + uid + ":" + uid + " " + dstPath + " " + dstPath + "-wal " + dstPath + "-shm 2>/dev/null; "
                + "chmod 600 " + dstPath + " 2>/dev/null; ls -l " + dstPath + " 2>&1";
        root.exec("nsenter -t 1 -m -- sh -c '" + inner + "'", 12000L);
        if (!dst.exists() || dst.length() == 0) {
            res.note = "库没拉出来（" + pkg + " 可能没装，或路径不对：" + rel + "）";
            cleanup(dst);
            return res;
        }
        res.dbPath = dstPath;
        byte[] key = deriveKey();
        if (key == null) { res.note = "密钥派生失败（缺 PBKDF2）"; cleanup(dst); return res; }
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = SQLiteDatabase.openDatabase(dstPath, null, SQLiteDatabase.OPEN_READONLY);
            String table = findTable(db, "cookies");
            if (table == null) {
                res.note = "这库里没有 cookies 表（不是浏览器数据库？）";
                return res;
            }
            String filter = hostFilter == null ? "" : hostFilter.trim().toLowerCase();
            c = db.rawQuery("select host_key,name,value,encrypted_value from " + table, null);
            int guard = 0;
            while (c.moveToNext() && guard < 5000) {
                guard++;
                String host = c.getString(0);
                String name = c.getString(1);
                String plain = c.isNull(2) ? "" : c.getString(2);
                byte[] blob = null;
                try { blob = c.getBlob(3); } catch (Throwable t) { }
                if (filter.length() > 0) {
                    String h = host == null ? "" : host.toLowerCase();
                    String n = name == null ? "" : name.toLowerCase();
                    if (!h.contains(filter) && !n.contains(filter)) continue;
                }
                Row r = decode(host, name, plain, blob, key);
                if ("v10".equals(r.how)) res.v10++;
                else if ("plain".equals(r.how)) res.plain++;
                else if ("v20".equals(r.how)) res.v20++;
                else res.fail++;
                res.rows.add(r);
            }
            res.ok = true;
        } catch (Throwable t) {
            res.note = "读库失败：" + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : (" " + t.getMessage()));
        } finally {
            try { if (c != null) c.close(); } catch (Throwable t) { }
            try { if (db != null) db.close(); } catch (Throwable t) { }
            cleanup(dst);
        }
        return res;
    }

    private static String findTable(SQLiteDatabase db, String want) {
        Cursor c = null;
        try {
            c = db.rawQuery("select name from sqlite_master where type='table'", null);
            while (c.moveToNext()) {
                String n = c.getString(0);
                if (want.equalsIgnoreCase(n)) return n;
            }
        } catch (Throwable t) {
        } finally {
            try { if (c != null) c.close(); } catch (Throwable t) { }
        }
        return null;
    }

    /** 临时库用完就删：里面是活的登录凭证 */
    private static void cleanup(File dst) {
        try { if (dst != null) dst.delete(); } catch (Throwable t) { }
        try { new File(dst.getAbsolutePath() + "-wal").delete(); } catch (Throwable t) { }
        try { new File(dst.getAbsolutePath() + "-shm").delete(); } catch (Throwable t) { }
    }

    /** 脱敏显示：短值原样，长值留头 28 字符 */
    static String mask(String v) {
        if (v == null) return "";
        if (v.length() <= 28) return v;
        return v.substring(0, 28) + "…(" + v.length() + "B)";
    }
}