package io.github.lyx2839272_coder.dshxpandh;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.content.ContentResolver;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends Activity {

    // 控制目录：DSH 版优先，独立使用则落到通用目录
    static final String CTRL = pickRoot();

    static String pickRoot() {
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
    static final String OFF = CTRL + "/off";
    static final String CMD = CTRL + "/cmd.json";
    static final String STEALTH = CTRL + "/stealth";
    static final String SCRIPTS = CTRL + "/scripts";
    /** 发过的指令存这里，每条一个 json（跟 cmd.json 同格式） */
    static final String CMDS = CTRL + "/cmds";
    static final String ABIL = CTRL + "/abilities";
    static final String USERDIR = "/sdcard/Download/xhook";
    static final String Q = String.valueOf((char) 34);
    static final String CO = String.valueOf((char) 44);
    static final String CL = String.valueOf((char) 58);

    static int BG = 0xff0f1319;
    static int CARD = 0xff171c24;
    static int LINE = 0xff232b36;
    static int TXT = 0xffe6edf6;
    static int SUB = 0xff8b9cb3;
    static int ACC = 0xff5aa9ff;
    static int OK = 0xff43c98b;
    static int BAD = 0xffef6d6d;
    static int CHIP = 0xff1b2330;
    static boolean darkMode = true;

    /** 深浅两套配色；跟随系统或按设置强制。 */
    static void initTheme(boolean night) {
        if (night) {
            BG = 0xff0f1319; CARD = 0xff171c24; LINE = 0xff232b36;
            TXT = 0xffe6edf6; SUB = 0xff8b9cb3; ACC = 0xff5aa9ff;
            OK = 0xff43c98b; BAD = 0xffef6d6d; CHIP = 0xff1b2330;
        } else {
            BG = 0xfff2f5f9; CARD = 0xffffffff; LINE = 0xffdfe6ef;
            TXT = 0xff12161c; SUB = 0xff5f6f83; ACC = 0xff2f7fe0;
            OK = 0xff1f9e6a; BAD = 0xffd8443f; CHIP = 0xffeef3fa;
        }
    }

    TextView statusLabel, foot, dotDot, bannerHead, bannerBody, bannerIcon;
    TextView rootAuthHint, rootPathHint, rootLspHint, modList, healthHint, cmdHint;
    LinearLayout cmdBox;
    LinearLayout scopeBox;
    String lastScopeRendered = null;
    LinearLayout banner;
    LinearLayout scriptBox;
    TextView[] abilDots;
    ScrollView[] ps;
    LinearLayout[] pr;
    TextView[] navLbl;
    TextView[] navIco;
    int curTab = 0;
    /** 页签总数：状态 / 配方 / 自定义 / Root / 设置 / 密钥 */
    static final int TABS = 6;
    /** 导航栏显示顺序 → 面板下标。密钥排在状态后面，面板仍是 pr[5] */
    private final int[] navOrder = { 0, 5, 1, 2, 3, 4 };
    Switch sw;
    EditText ePkg, eCls, eMtd, eMode, eVal;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        applyTheme();
        paintWindow();
        ensureStealthDefault();
        if (!agreed()) { showNotice(true); return; }
        if (alwaysNotice()) { showNotice(false); return; }
        // 公告之后：没给存储权限就先引导一次；用户可以选择「受限进入」，之后不再打扰
        if (!hasAllFiles() && !permSkipped()) { showPermGuide(); return; }
        buildMain();
    }

    /** 用户已经选过「先不授权，受限进入」 */
    boolean permSkipped() {
        try { return getSharedPreferences("xhook", 0).getBoolean("permSkipped", false); } catch (Throwable t) { return false; }
    }

    /**
     * 权限引导：说明为什么需要、能干什么、不给会怎样。
     * 三个出口：去授权 / 受限进入（不再提示）/ 稍后（下次还会问）
     */
    void showPermGuide() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int pad = dp(20);
        root.setPadding(pad, dp(40), pad, pad);

        TextView h = new TextView(this);
        h.setText("还需要一项权限");
        h.setTextSize(22);
        h.setTextColor(TXT);
        root.addView(h);

        TextView sub = new TextView(this);
        sub.setText("给了才能完整用，不给也能进，只是有几项用不了。");
        sub.setTextSize(13);
        sub.setTextColor(SUB);
        sub.setPadding(0, dp(8), 0, dp(20));
        root.addView(sub);

        LinearLayout card1 = card(root, "存储权限", "MANAGE_EXTERNAL_STORAGE");
        card1.addView(body("用于：写控制目录（/sdcard/.../xhook/），脚本、配方、导出的清单都在那儿。"));
        card1.addView(body("不给会怎样：脚本读不到，配方存不了，只能看看界面。"));

        LinearLayout card2 = card(root, "应用列表", "安装即授予，不用手动开");
        card2.addView(body("用于：配方页「从已安装应用里选」——不用手打包名。"));

        Button go = new Button(this);
        go.setText("去授权");
        go.setTextColor(0xFFFFFFFF);
        go.setBackgroundColor(ACC);
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openStorageSettings(); }
        });
        root.addView(go, new LinearLayout.LayoutParams(-1, dp(48)));

        Button limited = ghost(root, "先不授权，以受限模式进入");
        limited.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { getSharedPreferences("xhook", 0).edit().putBoolean("permSkipped", true).commit(); } catch (Throwable t) { }
                recreate();
            }
        });

        android.widget.ScrollView svPerm = new android.widget.ScrollView(this);
        svPerm.addView(root);
        // 顶部让出状态栏，不然标题会压在时间和电量上
        root.setPadding(pad, dp(40) + statusBarHeightPx(), pad, pad);
        setContentView(svPerm);
    }

    int statusBarHeightPx() {
        try {
            int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) return getResources().getDimensionPixelSize(id);
        } catch (Throwable t) { }
        return dp(24);
    }

    /** 跳到系统的「所有文件访问权限」页 */
    void openStorageSettings() {
        try {
            android.content.Intent i = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            i.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            try { startActivity(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
            catch (Throwable t2) { toast("请到 系统设置 → 应用 → 本模块 → 权限 里手动开启"); }
        }
    }

    /**
     * 隐身的默认值。
     * 规则：只要用户没在界面上动过这个开关，就一直是开的 —— 装完即用，覆盖安装也不会变回关。
     * 用户手动关过（stealthUserSet=true），就尊重他的选择，不再强制打开。
     */
    void ensureStealthDefault() {
        try {
            android.content.SharedPreferences p = getSharedPreferences(XhookProvider.PREFS, 0);
            if (p.getBoolean("stealthUserSet", false)) return;      // 用户自己定过，不碰
            if (!p.getBoolean("stealth", false)) {
                p.edit().putBoolean("stealth", true).putBoolean("stealthInit", true).commit();
            }
        } catch (Throwable t) {}
    }

    String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Throwable t) { return "?"; }
    }

    boolean hasAllFiles() {
        if (Build.VERSION.SDK_INT < 30) return true;
        try {
            java.lang.reflect.Method m = Environment.class.getMethod("isExternalStorageManager");
            return Boolean.TRUE.equals(m.invoke(null));
        } catch (Throwable t) { return false; }
    }

    /** 心跳超过这个时间没刷新，就当那个进程已经不在了（HookEntry 每 60 秒刷一次） */
    static final long ALIVE_FRESH_MS = 3 * 60_000L;

    /**
     * 读 HookEntry 留下的 alive-<pkg>，只认最近有心跳的。
     * 文件里第一行是毫秒时间戳，第二行是进程名，第三行是 pid。
     * 以前只看文件名，所以模块关掉、进程被杀之后，旧文件还让人以为「已连接」。
     */
    String aliveList() {
        StringBuilder sb = new StringBuilder();
        try {
            File d = new File(CTRL);
            File[] fs = d.listFiles();
            if (fs == null) return "";
            long now = System.currentTimeMillis();
            for (File f : fs) {
                String n = f.getName();
                if (!n.startsWith("alive-")) continue;
                String p = n.substring(6);
                if (p.equals(getPackageName())) continue;  // 模块自己不算目标进程
                long ts = 0;
                try {
                    java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
                    String ln = r.readLine();
                    r.close();
                    if (ln != null) ts = Long.parseLong(ln.trim());
                } catch (Throwable t2) {}
                if (ts <= 0 || now - ts > ALIVE_FRESH_MS) continue;  // 陈旧标记不算
                if (sb.length() > 0) sb.append("、");
                sb.append(p);
            }
        } catch (Throwable t) {}
        return sb.toString();
    }

    /**
     * 本进程有没有被 hook 框架注入 —— 有就是「模块已激活」。
     * 两条路：HookEntry 自己置的静态标记（最直接），以及 XposedBridge 类能否加载。
     * 这就是 LuckyTool / YukiHookAPI 那套判断的核心，不需要 root，也不需要探针。
     */
    boolean lspActive() {
        try { if (HookEntry.LOADED) return true; } catch (Throwable t) { }
        try { Class.forName("de.robv.android.xposed.XposedBridge"); return true; } catch (Throwable t) { }
        return false;
    }

    /** 框架 API 版本；拿不到返回 -1 */
    int xposedApiVersion() {
        try {
            Class<?> c = Class.forName("de.robv.android.xposed.XposedBridge");
            java.lang.reflect.Method m = c.getMethod("getXposedVersion");
            Object v = m.invoke(null);
            if (v instanceof Integer) return ((Integer) v).intValue();
        } catch (Throwable t) { }
        return -1;
    }

    /** 上次会话留下的探针结论 —— 打开页面先拿它顶上，不用干等 */
    boolean probeCached() {
        try { return getSharedPreferences("xhook", 0).getBoolean("probeInjected", false); } catch (Throwable t) { return false; }
    }

    void saveProbe(boolean v) {
        try { getSharedPreferences("xhook", 0).edit().putBoolean("probeInjected", v).apply(); } catch (Throwable t) { }
    }

    /** 最近一次探针的结论：true = 那一下新起的进程确实被注入了 */
    private volatile boolean lastProbeInjected = false;
    /** 最近一次探针是什么时候下结论的 */
    private volatile long lastProbeAt = 0L;

    /** probe-<pkg> 距今多少毫秒；-1 = 从没被注入过。 */
    long probeAgeMs() {
        try {
            File f = new File(new File(CTRL), "probe-" + getPackageName());
            if (!f.exists()) return -1;
            long ts = 0;
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
            String ln = r.readLine();
            r.close();
            if (ln != null) ts = Long.parseLong(ln.trim());
            if (ts <= 0) return -1;
            long d = System.currentTimeMillis() - ts;
            return d < 0 ? 0 : d;
        } catch (Throwable t) { return -1; }
    }

    /**
     * 跑一次注入探针：起一个 :probe 进程，看它会不会被注入。
     * 这是普通 App 唯一能问出「模块现在还启不启用」的办法 —— /data/adb 是 root:root 700，
     * 读不到 LSPosed 的配置；而已加载的进程不会因为模块被关就卸载 hook，光看心跳会一直显示已连接。
     */
    void runProbe() {
        final long startedAt = System.currentTimeMillis();
        try { startService(new Intent(this, ProbeService.class)); } catch (Throwable t) { }
        // 1.8 秒后看探针文件是不是刚刚被写过 —— 那说明这个新进程确实被注入了。
        // 进程内的 LOADED 静态标记不能当判据：老进程里的 hook 不会因为模块被关就消失，
        // 只有「新起的进程会不会被注入」才能反映此刻的真实状态。
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                long age = probeAgeMs();
                lastProbeInjected = age >= 0 && age < (System.currentTimeMillis() - startedAt) + 2500L;
                lastProbeAt = System.currentTimeMillis();
                saveProbe(lastProbeInjected);
                updateBanner();
            }
        }, 700L);
    }

    /**
     * 模块自己这个进程的心跳距今多少毫秒。
     * 它有心跳 = LSPosed 确实在把本模块注入进来（框架层通了）；
     * 没有 = 模块在 LSPosed 里没启用，或者刚被 force-stop 还没重启。
     */
    long selfAgeMs() {
        try {
            File f = new File(new File(CTRL), "alive-" + getPackageName());
            if (!f.exists()) return -1;
            long ts = 0;
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
            String ln = r.readLine();
            r.close();
            if (ln != null) ts = Long.parseLong(ln.trim());
            if (ts <= 0) return -1;
            long d = System.currentTimeMillis() - ts;
            return d < 0 ? 0 : d;
        } catch (Throwable t) { return -1; }
    }

    /** 全部 alive 标记里最新的一次心跳距今多少毫秒；没有就返回 -1。 */
    long aliveAgeMs() {
        long newest = -1;
        try {
            File[] fs = new File(CTRL).listFiles();
            if (fs == null) return -1;
            for (File f : fs) {
                String n = f.getName();
                if (!n.startsWith("alive-")) continue;
                if (n.substring(6).equals(getPackageName())) continue;
                long ts = 0;
                try {
                    java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
                    String ln = r.readLine();
                    r.close();
                    if (ln != null) ts = Long.parseLong(ln.trim());
                } catch (Throwable t2) {}
                if (ts > newest) newest = ts;
            }
        } catch (Throwable t) {}
        return newest <= 0 ? -1 : Math.max(0, System.currentTimeMillis() - newest);
    }

    static java.util.Map<String, String> modSnap = null;
    static long modSnapAt = 0L;

    /** 模块进程是普通 App uid，进不去 /data/adb（700 root）；用户授权后用 su 复核一次并缓存。 */
    /** 模块自己是普通 App uid，进不去 /data/adb（700 root）；改读外部写出的快照，零权限、无授权弹窗。 */
    /** 手动检测：清缓存重读快照；空则说明为什么空、怎么补。 */
    void detectModules() {
        modSnap = null;
        modSnapAt = 0L;
        java.util.Map<String, String> m = moduleSnapshot();
        if (m.isEmpty()) {
            toast("没有快照。模块自己读不到 /data/adb（需要 root），快照要由 root 侧生成：见下方说明");
        } else {
            toast("读到 " + m.size() + " 个模块");
        }
        refresh();
    }

    java.util.Map<String, String> moduleSnapshot() {
        long now = System.currentTimeMillis();
        if (modSnap != null && now - modSnapAt < 5000L) return modSnap;
        java.util.Map<String, String> m = new java.util.HashMap<String, String>();
        // 数据源统一放 prefs：有 root 时由 refreshModsViaRoot() 实时刷新，
        // 没 root 就显示上次读到的（或空），不再依赖 /sdcard 上的快照文件。
        try {
            android.content.SharedPreferences p = getSharedPreferences(XhookProvider.PREFS, 0);
            for (java.util.Map.Entry<String, ?> e : p.getAll().entrySet()) {
                String k = e.getKey();
                if (k != null && k.startsWith("mod:") && e.getValue() instanceof String) {
                    m.put(k.substring(4), (String) e.getValue());
                }
            }
        } catch (Throwable t) {}
        modSnap = m;
        modSnapAt = now;
        return m;
    }

    /**
     * 有 root 就实时读 Zygisk 模块状态写进 prefs —— 不再需要 /sdcard 上的外部快照。
     * 必须在后台线程调（runRoot 是同步阻塞的）。
     */
    void refreshModsViaRoot() {
        if (!rootVerified) return;
        String[] ids = { "YH_YC", "fgetpriority", "hma_oss_zygisk", "zygisk_lsposed" };
        StringBuilder cmd = new StringBuilder();
        for (String id : ids) {
            cmd.append("if [ -f /data/adb/modules/").append(id).append("/disable ]; then echo \"")
               .append(id).append(" off\"; elif [ -d /data/adb/modules/").append(id)
               .append(" ]; then echo \"").append(id).append(" on\"; fi; ");
        }
        String out = runRoot(cmd.toString(), 5000L);
        if (out == null) return;
        try {
            android.content.SharedPreferences.Editor ed = getSharedPreferences(XhookProvider.PREFS, 0).edit();
            for (String ln : out.split("\n")) {
                String[] kv = ln.trim().split(" ");
                if (kv.length == 2) ed.putString("mod:" + kv[0], kv[1]);
            }
            ed.commit();
            modSnap = null;   // 让界面下次重读
            modSnapAt = 0L;
        } catch (Throwable t) { }
    }

    void paintWindow() {
        try {
            getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
            getWindow().setStatusBarColor(BG);
            if (Build.VERSION.SDK_INT >= 26) getWindow().setNavigationBarColor(BG);
            // 状态栏/导航栏图标跟着背景明暗走。
            // 之前只设了背景色没管图标，浅色背景配浅色图标 —— 时间和电量基本看不清。
            int flags = 0;
            if (isLightColor(BG)) {
                if (Build.VERSION.SDK_INT >= 23) flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            if (Build.VERSION.SDK_INT >= 23) {
                getWindow().getDecorView().setSystemUiVisibility(flags);
            }
        } catch (Throwable t) {}
    }

    String updUrl() {
        try { return getSharedPreferences("xhook", 0).getString("updateUrl", ""); } catch (Throwable t) { return ""; }
    }

    /** 拉更新源 JSON，和本地版本比。网络在后台线程跑。 */
    void checkUpdate() {
        final String url = updUrl();
        if (url.length() == 0) { toast("先填更新源地址并保存"); return; }
        toast("检查中…");
        new Thread(new Runnable() {
            @Override public void run() {
                String r = null;
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.setRequestProperty("User-Agent", "DSHxpandh/" + versionName());
                    if (c.getResponseCode() != 200) { r = "服务器返回 " + c.getResponseCode(); }
                    else {
                        java.io.InputStream is = c.getInputStream();
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int k;
                        while ((k = is.read(buf)) > 0) bo.write(buf, 0, k);
                        is.close();
                        org.json.JSONObject o = new org.json.JSONObject(new String(bo.toByteArray(), "UTF-8"));
                        String remote = o.optString("version", "");
                        String note = o.optString("note", "");
                        String cur = versionName();
                        if (remote.length() == 0) r = "更新源没写 version 字段";
                        else if (remote.equals(cur)) r = "已是最新（" + cur + "）";
                        else r = "有新版本 " + remote + "（当前 " + cur + "）" + (note.length() > 0 ? "\n" + note : "");
                    }
                } catch (Throwable t) {
                    r = "检查失败：" + t.getClass().getSimpleName();
                }
                final String result = r == null ? "检查失败" : r;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        new android.app.AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("检查更新")
                            .setMessage(result)
                            .setPositiveButton("知道了", null)
                            .show();
                    }
                });
            }
        }).start();
    }

    /** 调试日志开关：靠控制目录里有没有 debug 这个文件 */
    boolean debugLogOn() {
        try { return new File(CTRL, "debug").exists(); } catch (Throwable t) { return false; }
    }

    void setDebugLog(boolean on) {
        File f = new File(CTRL, "debug");
        try {
            if (on) {
                File d = new File(CTRL);
                if (!d.exists()) d.mkdirs();
                if (!f.exists()) f.createNewFile();
                toast("调试日志已开启，重启目标 App 后生效");
            } else {
                boolean del = f.exists() && f.delete();
                toast(del ? "已关闭，标记文件已删除" : "已关闭");
            }
        } catch (Throwable t) {
            toast("操作失败：" + t.getClass().getSimpleName());
        }
    }

    /** QUERY_ALL_PACKAGES 是不是有效 */
    boolean hasQueryAll() {
        try {
            return getPackageManager().checkPermission(
                    "android.permission.QUERY_ALL_PACKAGES", getPackageName())
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { return false; }
    }

    /** 老的存储读写权限（Android 10 及以下有意义） */
    boolean hasLegacyStorage() {
        try {
            return checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { return Build.VERSION.SDK_INT >= 30; }
    }

    /**
     * 一行权限：名称 + 说明 + 状态；没给就显示「去授予」，点了跳系统设置。
     * action 为 null 表示这是安装即授予的权限，没有可跳的页面。
     */
    void permRow(LinearLayout parent, String name, boolean ok, String why, final String action) {
        LinearLayout r = row(parent);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView tn = new TextView(this);
        tn.setText(name);
        tn.setTextSize(14);
        tn.setTextColor(TXT);
        col.addView(tn);
        TextView td = new TextView(this);
        td.setText(ok ? "已授予" : why);
        td.setTextSize(11);
        td.setTextColor(ok ? OK : SUB);
        col.addView(td);
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView btn = new TextView(this);
        btn.setText(ok ? "已授予" : (action == null ? "安装即授予" : "去授予"));
        btn.setTextSize(12);
        btn.setTextColor(ok ? SUB : ACC);
        btn.setPadding(dp(10), dp(6), dp(4), dp(6));
        if (!ok && action != null) {
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { jumpPerm(action); }
            });
        }
        r.addView(btn);
    }

    void jumpPerm(String action) {
        try {
            android.content.Intent i = new android.content.Intent(action);
            i.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            try { startActivity(new android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
            catch (Throwable t2) { toast("请到系统设置 → 应用 → 本模块 → 权限 里开启"); }
        }
    }

    /** 判断一个颜色是深是浅（用来决定状态栏图标用黑还是白） */
    boolean isLightColor(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 128;
    }

    String themePref() { return getSharedPreferences("xhook", 0).getString("theme", "auto"); }

    void applyTheme() {
        String t = themePref();
        boolean night = "dark".equals(t);
        if ("auto".equals(t)) {
            int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            night = mode == Configuration.UI_MODE_NIGHT_YES;
        }
        darkMode = night;
        initTheme(night);
    }

    void setThemePref(String v) {
        getSharedPreferences("xhook", 0).edit().putString("theme", v).commit();
        recreate();
    }

    boolean suppressSw = false;

    boolean agreed() { return getSharedPreferences("xhook", 0).getBoolean("agreed", false); }

    boolean alwaysNotice() { return getSharedPreferences("xhook", 0).getBoolean("always", false); }

    void setAlways(boolean v) { getSharedPreferences("xhook", 0).edit().putBoolean("always", v).commit(); }

    void agree() {
        getSharedPreferences("xhook", 0).edit().putBoolean("agreed", true).commit();
        recreate();
    }

    TextView body(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(SUB);
        t.setLineSpacing(dp(3), 1f);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    /** 把 README.md 简单渲染成可读的段落（标题放大、表格用小字）。 */
    void renderDoc(LinearLayout root, String doc) {
        for (String raw : doc.split("\\n")) {
            String ln = raw.trim();
            if (ln.length() == 0) {
                android.view.View gap = new android.view.View(this);
                root.addView(gap, new LinearLayout.LayoutParams(-1, dp(6)));
                continue;
            }
            if (ln.startsWith("```") || ln.startsWith("---")) continue;
            TextView v = new TextView(this);
            v.setTextColor(TXT);
            if (ln.startsWith("### ")) { v.setText(ln.substring(4)); v.setTextSize(13); v.setTypeface(Typeface.DEFAULT_BOLD); }
            else if (ln.startsWith("## ")) { v.setText(ln.substring(3)); v.setTextSize(15); v.setTypeface(Typeface.DEFAULT_BOLD); }
            else if (ln.startsWith("# ")) { v.setText(ln.substring(2)); v.setTextSize(20); v.setTypeface(Typeface.DEFAULT_BOLD); }
            else if (ln.startsWith("|")) { v.setText(ln); v.setTextSize(9); v.setTextColor(SUB); }
            else { v.setText(ln); v.setTextSize(12); }
            v.setPadding(0, dp(2), 0, dp(2));
            root.addView(v);
        }
    }

    /** 完整说明页：直接渲染打包进 APK 的 README.md。 */
    void showDoc() {
        String doc = readAsset("README.md");
        if (doc == null || doc.length() < 50) { toast("说明文件缺失"); return; }
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        sv.addView(root);
        Button back = new Button(this);
        back.setText("返回设置");
        back.setAllCaps(false);
        back.setTextColor(ACC);
        back.setBackgroundDrawable(box(CHIP, LINE, 8));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { buildMain(); }
        });
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(44)));
        renderDoc(root, doc);
        setContentView(sv);
    }

    void showNotice(final boolean first) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        sv.addView(root);

        TextView t = new TextView(this);
        t.setText("DSHxpandh");
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(TXT);
        root.addView(t);

        TextView sub = new TextView(this);
        sub.setText("首次使用说明 · 确认后才进入设置");
        sub.setTextSize(12);
        sub.setTextColor(SUB);
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        // 公告第一块：怎么接入 DSH（给别人看的重点）
        LinearLayout cDsh = card(root, "接入 DSH（可选）", "如果你也用 DSH 这类 AI agent");
        cDsh.addView(body("装完对 DSH 说这一句就行："));
        cDsh.addView(body("「我装了 DSHxpandh 模块，接口在 " + CTRL + "/，先读 DSH-GUIDE.md」"));
        int recipeCount = 0;
        try {
            org.json.JSONObject cjN = caps();
            if (cjN != null) recipeCount = cjN.optJSONArray("recipes") == null ? 0 : cjN.getJSONArray("recipes").length();
        } catch (Throwable te) { }
        cDsh.addView(body("DSH 会自己读到能力清单、" + recipeCount + " 个配方和全部操作说明，然后就能替你套配方、开关能力、下发命令。"));
        cDsh.addView(body("模块自带对接插件，装在：" + CTRL + "/dsh-plugin/"));
        cDsh.addView(body("装法：dsh plugin --profile web add " + CTRL + "/dsh-plugin"));
        cDsh.addView(body("如果装了 DSH，重启后聊天框上方会出现 xhook 状态条和面板（仅 dsh-go 手机版有这套界面）。"));

        LinearLayout c1 = card(root, "怎么用");
        c1.addView(body("1. 在 LSPosed 里启用本模块，并勾选要 hook 的目标 App"));
        c1.addView(body("2. 重启那个 App（不是重启手机）"));
        c1.addView(body("3. 回到本页选配方或写自定义 hook"));
        c1.addView(body("4. 配置写到 " + CTRL + "/<包名>.json"));

        LinearLayout c2 = card(root, "能力（详细）");
        c2.addView(body("log · 抓明文"));
        c2.addView(body("把 App 加密前的原始内容记下来。登录信息、请求参数都在里面。"));
        c2.addView(body("args · 改参数"));
        c2.addView(body("让它发出你指定的参数，例如换成另一台设备的标识。"));
        c2.addView(body("return · 改结果"));
        c2.addView(body("让某个判断固定成立，例如永远显示「已开通」。"));
        c2.addView(body("js · 改网页"));
        c2.addView(body("往浏览器页面里塞脚本：改标题、自动填表、去掉弹窗。"));
        c2.addView(body("cookie · 取登录态"));
        c2.addView(body("把网页的登录 Cookie 读出来。"));
        c2.addView(body("call · 调方法"));
        c2.addView(body("直接调用 App 内部的方法拿值，不用等它自己触发。"));

        // 限制从清单渲染，跟主界面同源，改 json 即同步
        LinearLayout c4 = card(root, "限制（先看清）");
        org.json.JSONObject cjLimN = caps();
        if (cjLimN != null) {
            try {
                org.json.JSONArray ln = cjLimN.getJSONArray("limits");
                for (int li = 0; li < ln.length(); li++) c4.addView(body((li + 1) + ". " + ln.getString(li)));
            } catch (Throwable tn) {}
        }

        LinearLayout c3 = card(root, "务必知道");
        c3.addView(body("本模块需要读写 /sdcard 才能放置配置与状态文件。"));
        c3.addView(body("注入日志含明文与设备指纹，走 logcat（adb logcat | grep core 能看到），不落盘；LSPosed 若开了日志捕获才会另存到 /data/adb/lspd/log/。"));
        c3.addView(body("关闭注入只停 JS 通道，已挂上的日志 hook 仍会生效。"));
        c3.addView(body("只在你自己拥有或已获授权的设备上使用。"));

        final LinearLayout noticeRoot = root;
        if (Build.VERSION.SDK_INT >= 20) {
            sv.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    int b = 0, t = 0;
                    try { b = insets.getSystemWindowInsetBottom(); } catch (Throwable e) {}
                    try { t = insets.getSystemWindowInsetTop(); } catch (Throwable e) {}
                    noticeRoot.setPadding(dp(16), dp(24) + t, dp(16), dp(24) + b);
                    return insets;
                }
            });
        }
        Button ok = primary(root, first ? "我已了解，开始使用" : "知道了");
        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (first) agree(); else buildMain();
            }
        });

        setContentView(sv);
    }

    /** 每页顶部的标题 + 一句能力介绍 */
    void pageHead(LinearLayout root, String title, String desc) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(16), dp(18), dp(16), dp(8));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(22);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(TXT);
        head.addView(t);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(12);
        d.setTextColor(SUB);
        d.setLineSpacing(dp(2), 1f);
        d.setPadding(0, dp(4), 0, 0);
        head.addView(d);
        root.addView(head);
    }

    /** 独立的 Root 页：放那些必须有 root 才能做、而且 DSH 有 root 也不好做的事 */
    /** 把授权状态 / 框架信息填进 Root 页 */
    void refreshRootPage() {
        if (rootAuthHint != null) {
            if (rootVerified) {
                rootAuthHint.setText("✓ 已授予 —— su 能正常执行");
                rootAuthHint.setTextColor(OK);
            } else {
                rootAuthHint.setText("✗ 未授予 —— 去 SukiSU / KernelSU 的超级用户列表里给本模块授权");
                rootAuthHint.setTextColor(BAD);
            }
        }
        if (rootLspHint != null) {
            StringBuilder sb = new StringBuilder();
            String[] info = rootInfo;
            if (info != null && info.length > 0 && info[0].length() > 0) sb.append("框架：LSPosed ").append(info[0]);
            Boolean v = rootVerdict;
            if (v != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("启用状态：").append(v.booleanValue() ? "已启用" : "已停用（去 LSPosed 里开）");
            }
            if (sb.length() == 0) sb.append(rootVerified ? "读取中…" : "需要 Root 权限才能读");
            rootLspHint.setText(sb.toString());
        }
        applyRootState();
        rebuildScopeRows();
    }

    /** 作用域列表：一行一个，右边 ✕ 移除；内容没变就不重建，免得闪 */
    void rebuildScopeRows() {
        if (scopeBox == null) return;
        String sc = lastScopes == null ? "" : lastScopes;
        if (sc.equals(lastScopeRendered)) return;
        lastScopeRendered = sc;
        scopeBox.removeAllViews();
        if (sc.length() == 0) {
            TextView empty = body("作用域是空的 —— 下面加一个包名进去");
            scopeBox.addView(empty);
            return;
        }
        for (String raw : sc.split(",")) {
            final String p = raw.trim();
            if (p.length() == 0) continue;
            LinearLayout r = row(scopeBox);
            TextView t = new TextView(this);
            t.setText(p);
            t.setTextSize(12);
            t.setTextColor(TXT);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView x = new TextView(this);
            x.setText("移除");
            x.setTextSize(12);
            x.setTextColor(SUB);
            x.setGravity(Gravity.CENTER);
            x.setPadding(dp(14), dp(4), dp(6), dp(4));
            x.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new Thread(new Runnable() {
                        @Override public void run() {
                            final boolean ok = rootEditScope(p, false);
                            final String now = ok ? rootScopes() : null;
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    if (!ok) { toast("移除失败：没有 Root 权限"); return; }
                                    lastScopes = now;
                                    lastScopeRendered = null;
                                    rebuildScopeRows();
                                    toast("已移出作用域，重启 " + p + " 后生效");
                                }
                            });
                        }
                    }, "xhook-scoperrm").start();
                }
            });
            r.addView(x);
        }
    }

    /** 上次读到的作用域，给 Root 页复用 */
    private volatile String lastScopes = null;

    /** 记住非空的作用域 —— 重装 APK 后 LSPosed 会把它清空，靠这个恢复 */
    void rememberScopes(String sc) {
        if (sc == null || sc.length() == 0) return;
        try { getSharedPreferences("xhook", 0).edit().putString("lastScopes", sc).apply(); } catch (Throwable t) { }
    }

    String rememberedScopes() {
        try { return getSharedPreferences("xhook", 0).getString("lastScopes", ""); } catch (Throwable t) { return ""; }
    }

    /**
     * 重装 APK 时 LSPosed 会把本模块的作用域清空（重装等于换了 APK，它按新模块处理）。
     * 这里比对「上次记录的」和「现在读到的」：上次有、现在没了 → 自动补回去。
     */
    String restoreScopesIfLost() {
        String now = rootScopes();
        if (now.length() > 0) { rememberScopes(now); return now; }
        String last = rememberedScopes();
        if (last.length() == 0) return now;
        int fixed = 0;
        for (String raw : last.split(",")) {
            String q = raw.trim();
            if (q.length() > 0 && rootEditScope(q, true)) fixed++;
        }
        if (fixed > 0) {
            final int n = fixed;
            runOnUiThread(new Runnable() {
                @Override public void run() { toast("重装后 LSPosed 清空了作用域，已自动恢复 " + n + " 个"); }
            });
        }
        return rootScopes();
    }

    void buildRootPage(LinearLayout root) {
        pageHead(root, "Root 增强",
                "需要 Root 权限才能做的运维类操作：读 LSPosed 配置库、修失效的模块路径、清孤儿记录。"
                + "\n没有 Root 时这些按钮不可用，但 hook 本身照常工作。"
                + "\n作用域勾选和模块启用开关，请去 LSPosed 管理器里配 —— 这里不重复一套，免得两边打架。");

        // ① 授权状态 + 一键进 LSPosed
        LinearLayout c1 = card(root, "Root 授权", "在 SukiSU / KernelSU 的超级用户列表里给本模块授权", 1);
        rootAuthHint = body("检查中…");
        c1.addView(rootAuthHint);
        // 「打开 LSPosed」不单独放按钮 —— 点最上面的状态卡就是它，这里不再重复

        // ② 模块注册路径
        LinearLayout c2 = card(root, "模块注册路径", "每次装 APK，/data/app 里的哈希都会变，LSPosed 就找不到模块了", 1);
        rootPathHint = body("检查中…");
        c2.addView(rootPathHint);
        Button btnFix = new Button(this);
        btnFix.setText("检查并修复");
        btnFix.setAllCaps(false);
        btnFix.setTextSize(12);
        btnFix.setTextColor(ACC);
        btnFix.setGravity(Gravity.CENTER);
        btnFix.setPadding(0, 0, 0, 0);
        btnFix.setBackgroundDrawable(box(CHIP, LINE, 8));
        LinearLayout.LayoutParams fixLp = new LinearLayout.LayoutParams(-1, dp(40));
        fixLp.topMargin = dp(10);
        c2.addView(btnFix, fixLp);
        markRootOnly(btnFix);
        btnFix.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override public void run() {
                        final String msg = rootSyncApkPath();
                        runOnUiThread(new Runnable() {
                            @Override public void run() { rootPathHint.setText(msg); }
                        });
                    }
                }, "xhook-fix2").start();
            }
        });

        // 作用域与启用开关交给 LSPosed 自己管 —— 模块这边只做「不让它坏掉」的运维



        // ④ 已装模块清单
        LinearLayout c5 = card(root, "LSPosed 里装了哪些模块", "● 已启用 / ○ 已停用", 1);
        modList = body("读取中…");
        c5.addView(modList);
        new Thread(new Runnable() {
            @Override public void run() {
                final String list = rootModuleList();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        modList.setText(list == null ? "读不到（需要 Root 权限）" : (list.length() == 0 ? "一个都没有" : list));
                    }
                });
            }
        }, "xhook-modlist").start();

        // ⑤ 配置库体检（一键清孤儿 + 修失效路径）
        LinearLayout c6 = card(root, "LSPosed 配置库体检", "检查 LSPosed 里登记的每个 hook 模块，它们指向的 APK 路径是否还有效。"
                + "跟本模块无关，也不动 Zygisk 那一层。");
        healthHint = body("还没体检过");
        c6.addView(healthHint);
        Button btnHealth = new Button(this);
        btnHealth.setText("体检 LSPosed 配置库");
        btnHealth.setAllCaps(false);
        btnHealth.setGravity(Gravity.CENTER);
        btnHealth.setPadding(0, 0, 0, 0);
        btnHealth.setTextSize(12);
        btnHealth.setTextColor(ACC);
        btnHealth.setBackgroundDrawable(box(CHIP, LINE, 8));
        LinearLayout.LayoutParams hlLp = new LinearLayout.LayoutParams(-1, dp(40));
        hlLp.topMargin = dp(10);
        c6.addView(btnHealth, hlLp);
        markRootOnly(btnHealth);
        btnHealth.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                healthHint.setText("体检中，请稍等…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        final String report = rootHealthCheck();
                        final String list = rootModuleList();
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                healthHint.setText(report);
                                // 体检完顺手刷新模块清单
                                if (modList != null) modList.setText(list == null ? "读不到（需要 Root 权限）" : list);
                            }
                        });
                    }
                }, "xhook-health").start();
            }
        });

        // ⑥ 为什么放在这里做
        LinearLayout c4 = card(root, "为什么放在模块里做", "DSH 有 root 也绕不过的几件事");
        c4.addView(body("· 从外部改配置库：要先拷出来、用 sqlite 改、再拷回去，还得处理 WAL 和文件权限"));
        c4.addView(body("· 模块自己的 APK 路径只有它自己最清楚，重装那一刻就变了"));
        c4.addView(body("· 重启目标 App 让 hook 生效，模块内一步完成，不用来回切工具"));
        c4.addView(body("· Root 授权被撤销时，只有模块进程里能立刻感知并降级，DSH 那边要等下一次调用才发现"));
    }

    void buildMain() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);

        // 受限模式横幅：用户选了「先不授权」，功能会缺一块，得让人看得见
        if (!hasAllFiles()) {
            LinearLayout warn = new LinearLayout(this);
            warn.setOrientation(LinearLayout.HORIZONTAL);
            warn.setBackgroundColor(0xFFFFF3CD);
            warn.setPadding(dp(14), dp(10), dp(14), dp(10));
            TextView wt = new TextView(this);
            wt.setText("受限模式：没有存储权限，脚本和配方用不了");
            wt.setTextSize(12);
            wt.setTextColor(0xFF8A6D3B);
            warn.addView(wt, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView wb = new TextView(this);
            wb.setText("去开启");
            wb.setTextSize(12);
            wb.setTextColor(ACC);
            wb.setPadding(dp(8), 0, 0, 0);
            wb.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openStorageSettings(); }
            });
            warn.addView(wb);
            outer.addView(warn);
        }

        FrameLayout fp = new FrameLayout(this);
        ps = new ScrollView[TABS];
        pr = new LinearLayout[TABS];
        for (int i = 0; i < TABS; i++) {
            ScrollView s = new ScrollView(this);
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setPadding(dp(16), dp(6), dp(16), dp(24));
            s.addView(r);
            ps[i] = s;
            pr[i] = r;
            fp.addView(s);
        }
        try { buildRootPage(pr[3]); } catch (Throwable t) { }
        try { buildCookiePage(pr[5]); } catch (Throwable t) { }
        LinearLayout root = pr[0];

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(16), dp(18), dp(16), dp(6));

        LinearLayout hd = new LinearLayout(this);
        hd.setOrientation(LinearLayout.HORIZONTAL);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("DSHxpandh");
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TXT);
        hd.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView ver = new TextView(this);
        String vn = "?";
        try { vn = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Throwable t) {}
        ver.setText("v" + vn);
        ver.setTextSize(11);
        ver.setTextColor(ACC);
        ver.setBackgroundDrawable(box(CHIP, LINE, 8));
        ver.setPadding(dp(8), dp(3), dp(8), dp(3));
        hd.addView(ver);
        head.addView(hd);

        TextView sub = new TextView(this);
        sub.setText("LSPosed 运行时 hook · 注入通道 / 配方 / 自定义");
        sub.setTextSize(12);
        sub.setTextColor(SUB);
        sub.setPadding(0, dp(4), 0, dp(2));
        head.addView(sub);
        outer.addView(head);
        outer.addView(fp, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout cs = card(root, "运行状态", "模块、权限与已注入进程");
        banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding(dp(16), dp(14), dp(16), dp(14));
        // 底色由 updateBanner 按状态决定（激活＝绿、未激活＝红）

        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);

        bannerHead = new TextView(this);
        bannerHead.setTextSize(19);
        bannerHead.setTypeface(Typeface.DEFAULT_BOLD);
        headRow.addView(bannerHead, new LinearLayout.LayoutParams(0, -2, 1f));

        bannerIcon = new TextView(this);
        bannerIcon.setTextSize(30);
        bannerIcon.setGravity(Gravity.CENTER);
        headRow.addView(bannerIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        banner.addView(headRow);
        bannerBody = new TextView(this);
        bannerBody.setTextSize(12);
        bannerBody.setLineSpacing(dp(3), 1f);
        bannerBody.setPadding(0, dp(8), 0, 0);
        banner.addView(bannerBody);
        // 整块可点：未连接时点它直接去 LSPosed 里查，不用自己找入口
        banner.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runProbe(); openLsposed(); }
        });
        cs.addView(banner);

        String uiTitle = "注入通道", uiSub = "只管「往网页塞东西」这一路，跟 App 里的 hook 无关";
        String uiSwitch = "指令脚本开关";
        try {
            org.json.JSONObject cjUi = caps();
            if (cjUi != null) {
                org.json.JSONObject u = cjUi.optJSONObject("ui");
                if (u != null) {
                    uiTitle = u.optString("channelTitle", uiTitle);
                    uiSub = u.optString("channelSub", uiSub);
                    uiSwitch = u.optString("switchLabel", uiSwitch);
                }
            }
        } catch (Throwable tu) {}
        LinearLayout c1 = card(root, uiTitle, uiSub);
        LinearLayout r1 = row(c1);
        LinearLayout dot = new LinearLayout(this);
        dot.setOrientation(LinearLayout.VERTICAL);
        dot.setGravity(Gravity.CENTER);
        dot.setBackgroundDrawable(box(CHIP, LINE, 12));
        dotDot = new TextView(this);
        dotDot.setText("●");
        dotDot.setTextSize(20);
        dotDot.setTextColor(OK);
        dotDot.setGravity(Gravity.CENTER);
        dotDot.setIncludeFontPadding(false);
        dotDot.setWidth(dp(52));
        dotDot.setHeight(dp(52));
        dot.addView(dotDot, new LinearLayout.LayoutParams(dp(52), dp(52)));
        r1.addView(dot, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(8), 0);
        TextView l1 = new TextView(this);
        l1.setText(uiSwitch);
        l1.setTextSize(15);
        l1.setTextColor(TXT);
        col.addView(l1);
        statusLabel = new TextView(this);
        statusLabel.setTextSize(11);
        statusLabel.setTextColor(SUB);
        col.addView(statusLabel);
        r1.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        sw = new Switch(this);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                if (suppressSw) return;
                setInject(checked);
            }
        });
        r1.addView(sw);

        LinearLayout c2 = card(root, "能力", "六种 hook 模式，可单独开关");
        // 从 capabilities.json 的 modes 渲染；读不到才用兜底，保证任何情况都能用
        java.util.List<String[]> abList = new java.util.ArrayList<String[]>();
        org.json.JSONObject cjModes = caps();
        if (cjModes != null) {
            try {
                org.json.JSONArray ms = cjModes.getJSONArray("modes");
                for (int mi = 0; mi < ms.length(); mi++) {
                    org.json.JSONObject o = ms.getJSONObject(mi);
                    abList.add(new String[] { o.getString("id"), o.getString("name"), o.optString("when", "") });
                }
            } catch (Throwable tm) {}
        }
        if (abList.isEmpty()) {
            abList.add(new String[] { "log", "抓明文", "把加密前的原话记下来" });
            abList.add(new String[] { "args", "改参数", "让它发出你指定的值" });
            abList.add(new String[] { "return", "改结果", "让判断固定成立" });
            abList.add(new String[] { "js", "改网页", "往页面里塞脚本" });
            abList.add(new String[] { "cookie", "取登录态", "拿到网页的 Cookie" });
            abList.add(new String[] { "call", "调方法", "直接调 App 内部方法" });
        }
        String[][] ab = abList.toArray(new String[abList.size()][]);
        abilDots = new TextView[ab.length];
        for (int i = 0; i < ab.length; i++) {
            final String key = ab[i][0];
            LinearLayout r = row(c2);
            TextView d = new TextView(this);
            d.setText("●");
            d.setTextSize(14);
            d.setTextColor(OK);
            d.setIncludeFontPadding(false);
            d.setGravity(Gravity.CENTER);
            abilDots[i] = d;
            r.addView(d, new LinearLayout.LayoutParams(dp(22), dp(34)));
            LinearLayout abCol = new LinearLayout(this);
            abCol.setOrientation(LinearLayout.VERTICAL);
            TextView nm = new TextView(this);
            nm.setText(ab[i][0] + "  " + ab[i][1]);
            nm.setTextSize(13);
            nm.setTextColor(TXT);
            abCol.addView(nm);
            TextView ds = new TextView(this);
            ds.setText(ab[i][2]);
            ds.setTextSize(10);
            ds.setTextColor(SUB);
            abCol.addView(ds);
            r.addView(abCol, new LinearLayout.LayoutParams(0, -2, 1f));
            Switch sw2 = new Switch(this);
            sw2.setChecked(!new File(ABIL, key + ".off").exists());
            sw2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton v, boolean checked) { setAbility(key, checked); }
            });
            r.addView(sw2);
        }
        // 详细说明同样来自清单的 detail
        org.json.JSONObject cjDet = caps();
        if (cjDet != null) {
            try {
                org.json.JSONArray ms2 = cjDet.getJSONArray("modes");
                for (int mi2 = 0; mi2 < ms2.length(); mi2++) {
                    org.json.JSONObject o = ms2.getJSONObject(mi2);
                    c2.addView(body(o.getString("id") + " · " + o.getString("name")));
                    org.json.JSONArray dt = o.optJSONArray("detail");
                    if (dt != null) for (int dj = 0; dj < dt.length(); dj++) c2.addView(body(dt.getString(dj)));
                }
            } catch (Throwable td) {}
        }

        LinearLayout cL = card(root, "限制（做不到的）");
        // 限制也来自清单，编号在渲染时加
        org.json.JSONObject cjLim = caps();
        if (cjLim != null) {
            try {
                org.json.JSONArray lm = cjLim.getJSONArray("limits");
                for (int li = 0; li < lm.length(); li++) {
                    cL.addView(body((li + 1) + ". " + lm.getString(li)));
                }
            } catch (Throwable tl) {}
        }

        pageHead(pr[1], "配方",
                "现成的 hook 配置，选一个套到目标 App 上就能用。"
                + "\n不用写类名方法 —— 抓明文、改返回值、读 Cookie、伪造屏幕这些都在里面。"
                + "\n目标包名不用手打，点「从已安装应用里选」挑就行。"
                  + "\n这一页不需要 Root 权限。");
        LinearLayout c3 = card(pr[1], "配方", "一键套用到目标 App");
        TextView hint3 = new TextView(this);
        hint3.setText("写入 " + CTRL + "/<包名>.json，重启目标 App 生效");
        hint3.setTextSize(11);
        hint3.setTextColor(SUB);
        hint3.setPadding(0, 0, 0, dp(8));
        c3.addView(hint3);
        ePkg = input(c3, "目标包名，例如 com.heytap.browser");
        Button bPickApp = ghost(c3, "从已安装应用里选");
        bPickApp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAppPicker(); }
        });
        // 从 capabilities.json 渲染；读不到才回退硬编码
        boolean rendered = false;
        org.json.JSONObject cj = caps();
        if (cj != null) {
            try {
                org.json.JSONArray rs = cj.getJSONArray("recipes");
                for (int ri = 0; ri < rs.length(); ri++) {
                    org.json.JSONObject o = rs.getJSONObject(ri);
                    c3.addView(recipeRow(o.getString("id"), o.getString("name"), o.optString("when", "")));
                }
                rendered = rs.length() > 0;
            } catch (Throwable tr) {}
        }
        if (!rendered) {
            String[][] rc = {{ "cipher-log", "抓加密明文" }, { "fake-ua", "换 User-Agent" },
                { "fake-device-id", "换设备 ID" }, { "fake-android-id", "换 Android ID" },
                { "fake-location", "换定位" }, { "block-su", "拦 su 调用" }, { "block-logs", "屏蔽日志" },
                { "log-webview-url", "记网页加载地址" }, { "fake-screen", "看屏幕尺寸" },
                { "ssl-pinning-off", "关证书校验(实验)" },
                { "crypto-full", "全链路加解密" }, { "crypto-base64", "看 Base64 编解码" }};
            for (final String[] r : rc) c3.addView(recipeRow(r[0], r[1]));
        }

        pageHead(pr[2], "自定义",
                "两件事："
                + "\n· 手写 hook —— 指定类名 + 方法名，选动作（记录 / 改参数 / 改返回值 / 执行 JS）"
                + "\n· 往网页塞 JS —— 注入脚本（常驻，可开关）或注入指令（一次性，不用刷新）"
                  + "\n自带 9 个脚本（3 个示例 + 6 个模板），列表里标「内置」的就是；改脚本不用重装，3 秒内生效。"
                + "\n这一页不需要 Root 权限。");
        LinearLayout c4 = card(pr[2], "自定义 hook", "手写类名与方法");
        eCls = input(c4, "类名 · 例如 android.webkit.WebSettings");
        eMtd = input(c4, "方法名 · 例如 getUserAgentString");
        eMode = input(c4, "模式 log / args / return / js");
        eVal = input(c4, "值（return_value 或 js 代码，可空）");
        Button w = primary(c4, "生成并写入");
        w.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { writeCustom(); }
        });

        pageHead(pr[4], "设置",
                "外观、隐身、底层隐藏、公告与文件位置。"
                + "\n跟 hook 逻辑无关的部分都在这里；只有「底层隐藏」的检测会用到 Root，没 Root 时读的是外部快照。");
        LinearLayout cP = card(pr[4], "应用权限", "检查并授予模块所需的权限");
        permRow(cP, "所有文件访问权限", hasAllFiles(),
                "写控制目录（脚本、配方、导出的清单）",
                "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
        permRow(cP, "读取应用列表权限", hasQueryAll(),
                "配方页「从已安装应用里选」",
                null);
        permRow(cP, "存储读写权限", hasLegacyStorage(),
                "Android 10 及以下的兼容路径",
                null);

        LinearLayout cU = card(pr[4], "检查更新", "填一个更新源地址，有新版会提示");
        cU.addView(body("更新源是一个 JSON 地址，返回 {\"version\":\"12.8.0\",\"url\":\"下载页\",\"note\":\"说明\"}"));
        final EditText eUpd = new EditText(this);
        eUpd.setText(updUrl());
        eUpd.setHint("https://…/version.json（留空=不检查）");
        eUpd.setTextSize(12);
        eUpd.setSingleLine(true);
        cU.addView(eUpd);
        LinearLayout uRow = row(cU);
        Button bSaveU = ghost(uRow, "保存地址");
        bSaveU.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    getSharedPreferences("xhook", 0).edit()
                        .putString("updateUrl", eUpd.getText().toString().trim()).commit();
                    toast("已保存");
                } catch (Throwable t) { }
            }
        });
        Button bCheck = primary(uRow, "检查");
        bCheck.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkUpdate(); }
        });

        LinearLayout cD = card(pr[4], "调试日志", "排查问题时打开，平时关着");
        LinearLayout dRow = row(cD);
        TextView dLabel = new TextView(this);
        dLabel.setText("输出模块日志到 logcat");
        dLabel.setTextSize(14);
        dLabel.setTextColor(TXT);
        dRow.addView(dLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch swD = new Switch(this);
        swD.setChecked(debugLogOn());
        swD.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean checked) { setDebugLog(checked); }
        });
        dRow.addView(swD);
        cD.addView(body("打开后，即使隐身开着也会把 hook 过程写进 logcat（adb logcat | grep core 能看到）。"));
        cD.addView(body("关闭时会自动删除控制目录里的标记文件，日志立刻恢复静默。"));

        LinearLayout cA = card(pr[4], "外观", "背景色跟随系统或自己选");
        LinearLayout seg = row(cA);
        final String[] vals = { "auto", "light", "dark" };
        final String[] lbl = { "跟随系统", "浅色", "深色" };
        String cur = themePref();
        for (int i = 0; i < 3; i++) {
            final String v = vals[i];
            Button b = new Button(this);
            b.setText(lbl[i]);
            b.setAllCaps(false);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, 0, 0, 0);
            b.setTextSize(12);
            boolean on = v.equals(cur);
            b.setTextColor(on ? Color.WHITE : TXT);
            b.setBackgroundDrawable(box(on ? ACC : CHIP, on ? ACC : LINE, 8));
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) { setThemePref(v); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            lp.rightMargin = dp(6);
            seg.addView(b, lp);
        }

        // 注入相关的东西都放「自定义」页：指令 + 脚本放在一起，设置页只管外观和隐藏
        // 注入指令：版式对齐下面的「注入脚本」——主按钮在上、说明在中、次级按钮和列表在下
        LinearLayout c5 = card(pr[2], "注入指令",
                "一次性：发送后立刻对已打开的页面执行，不刷新。常驻的用下面的注入脚本。", 0);

        final EditText etCmd = new EditText(this);
        etCmd.setHint("写一段 JS，比如 document.body.style.background='#000'");
        etCmd.setTextSize(12);
        etCmd.setTextColor(TXT);
        etCmd.setHintTextColor(SUB);
        etCmd.setMinLines(2);
        etCmd.setGravity(Gravity.TOP);
        c5.addView(etCmd);

        Button bSend = primary(c5, "发送");
        bSend.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String js = etCmd.getText().toString().trim();
                if (js.length() == 0) { toast("先写点东西"); return; }
                if (sendCmd(js)) { etCmd.setText(""); rebuildCmds(); }
            }
        });
        c5.addView(body("发一次执行一次，不会常驻。想确认链路通不通，用下面的「写入测试指令」。"));

        LinearLayout testRow = row(c5);
        Button bImport = ghost(testRow, "导入指令文件");
        bImport.setLayoutParams(new LinearLayout.LayoutParams(0, dp(44), 1f));
        bImport.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickCmdFile(); }
        });
        Button bTest = ghost(testRow, "写入测试指令");
        bTest.setLayoutParams(new LinearLayout.LayoutParams(0, dp(44), 1f));
        bTest.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { writeTest(); rebuildCmds(); }
        });

        final Button bClear = ghost(c5, "清空注入指令");
        final long[] lastClear = { 0L };
        bClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (now - lastClear[0] > 3000L) {
                    lastClear[0] = now;
                    bClear.setText("再点一次确认清空指令");
                    toast("只删当前指令和下面的历史，注入脚本不动");
                    return;
                }
                lastClear[0] = 0L;
                bClear.setText("清空注入指令");
                clearAllCmds();
            }
        });

        c5.addView(body("发过的指令（最近 10 条，点「重发」再执行一次）"));
        cmdBox = new LinearLayout(this);
        cmdBox.setOrientation(LinearLayout.VERTICAL);
        c5.addView(cmdBox);
        c5.addView(body("目录 " + CMDS + "，丢 .json 进去也会自动出现"));

        LinearLayout cSc = card(pr[2], "注入脚本",
                "常驻：每个启用的脚本在页面加载后都会注入，可单独开关或删除。"
                + "\n一次性的用上面的注入指令。", 0);
        Button b3 = primary(cSc, "加入脚本（选文件）");
        b3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickFile(); }
        });
        cSc.addView(body("选 .js 进脚本列表；选 .json 当 App 配置（需先在配方页填包名）"));
        Button bRestore = ghost(cSc, "恢复被移除的内置脚本");
        bRestore.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // 只做一件事：把「移除」过的内置脚本重新显示出来、恢复默认开关。
                // 不往 /sdcard 导副本 —— 内置脚本本来就在 APK 里，导出来只会让列表出现重复的两套。
                int n = 0;
                try {
                    android.content.SharedPreferences p = getSharedPreferences(XhookProvider.PREFS, 0);
                    android.content.SharedPreferences.Editor ed = p.edit();
                    for (String k : p.getAll().keySet()) {
                        if (k.startsWith("scriptHide:") || k.startsWith("scriptOff:") || k.startsWith("scriptOn:")) {
                            ed.remove(k);
                            n++;
                        }
                    }
                    ed.commit();
                } catch (Throwable t) { }
                rebuildScripts();
                toast(n > 0 ? "已恢复 " + n + " 项" : "没有需要恢复的");
            }
        });

        LinearLayout srow = row(cSc);
        Button sOn = ghost(srow, "全部开启");
        sOn.setLayoutParams(new LinearLayout.LayoutParams(0, dp(44), 1f));
        sOn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { setAllScripts(true); }
        });
        Button sOff = ghost(srow, "全部关闭");
        LinearLayout.LayoutParams lpOff = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lpOff.leftMargin = dp(8);
        sOff.setLayoutParams(lpOff);
        sOff.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { setAllScripts(false); }
        });

        TextView sh = new TextView(this);
        sh.setText("已加入的脚本（自动识别）");
        sh.setTextSize(12);
        sh.setTextColor(SUB);
        sh.setPadding(0, dp(10), 0, dp(2));
        cSc.addView(sh);
        scriptBox = new LinearLayout(this);
        scriptBox.setOrientation(LinearLayout.VERTICAL);
        cSc.addView(scriptBox);
        cSc.addView(body("目录 " + CTRL + "/scripts，丢 .js 进去也会自动出现"));

        LinearLayout cS = card(pr[4], "隐身", "反 Java 层探测");
        LinearLayout rS = row(cS);
        TextView lS = new TextView(this);
        lS.setText("隐藏 Xposed 特征");
        lS.setTextSize(14);
        lS.setTextColor(TXT);
        rS.addView(lS, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch swS = new Switch(this);
        swS.setChecked(stealthOn());
        swS.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton v, boolean checked) { setStealth(checked); }
        });
        rS.addView(swS);
        cS.addView(body("开启后对目标 App 隐藏这些："));
        cS.addView(body("· 模块与 Xposed 的类名（查出来是「不存在」）"));
        cS.addView(body("· XPOSED / LSPOSED / MAGISK 等系统属性与读取痕迹"));
        cS.addView(body("· 进程映射里带 xposed 标识的短行"));
        cS.addView(body("· 包列表与精确查询里的 LSPosed 管理器"));
        cS.addView(body("· 异常栈里露出的 xposed 调用帧"));
        cS.addView(body("· su / magisk 相关命令探测"));
        cS.addView(body("仍然挡不住：native 直接读 /proc/self/maps、内核级检测、以及模块 APK 自身的 dex 结构。"));

        LinearLayout cBot = card(pr[4], "底层隐藏", "系统里已装的 Zygisk 模块");
        String[][] zm = {
            { "YH_YC", "月虹一键隐藏", "root / 越狱层" },
            { "fgetpriority", "syscall 结果隐藏", "内核层" },
            { "hma_oss_zygisk", "HMA 应用列表", "Zygisk 后端" },
            { "zygisk_lsposed", "LSPosed", "框架本体" }
        };
        for (int i = 0; i < zm.length; i++) {
            File d = new File("/data/adb/modules/" + zm[i][0]);
            boolean gone = !d.exists();
            boolean disabled = !gone && new File(d, "disable").exists();
            String snap = moduleSnapshot().get(zm[i][0]);
            boolean unknown = gone && snap == null;
            if (gone && snap != null) { gone = false; disabled = "off".equals(snap); }
            boolean on = !gone && !disabled;
            LinearLayout r = row(cBot);
            TextView zdot = new TextView(this);
            zdot.setText("●");
            zdot.setTextSize(12);
            zdot.setTextColor(on ? OK : BAD);
            zdot.setGravity(Gravity.CENTER);
            r.addView(zdot, new LinearLayout.LayoutParams(dp(20), dp(30)));
            TextView nm = new TextView(this);
            nm.setText(zm[i][1] + "  " + (unknown ? "未检测" : (gone ? "未安装" : (on ? "运行中" : "已停用"))));
            nm.setTextSize(12);
            nm.setTextColor(TXT);
            r.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView role = new TextView(this);
            role.setText(zm[i][2]);
            role.setTextSize(10);
            role.setTextColor(SUB);
            r.addView(role);
        }
        Button det = new Button(this);
        det.setText("重新检测");
        det.setAllCaps(false);
        det.setGravity(Gravity.CENTER);
        det.setPadding(0, 0, 0, 0);
        det.setTextSize(12);
        det.setTextColor(ACC);
        det.setBackgroundDrawable(box(CHIP, LINE, 8));
        det.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { detectModules(); }
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(40));
        dlp.topMargin = dp(10);
        cBot.addView(det, dlp);
        cBot.addView(body("快照来源：" + CTRL + "/mods（模块是普通 App uid，读不到 /data/adb；需 root 侧跑 lsp-modules 生成）"));
        cBot.addView(body("分工：root 与内核痕迹交给 YH_YC / fgetpriority；包列表 hma + 本模块各挡一层；本模块只补 Java 层的类名、属性、栈帧、TracerPid。"));
        cBot.addView(body("注意：HMA 与本模块都会改 PackageManager 返回的列表，同时开启是双保险，但排查问题时可先关一个。"));

        LinearLayout cN = card(pr[4], "公告");
        LinearLayout rN = row(cN);
        TextView lN = new TextView(this);
        lN.setText("每次启动显示使用说明");
        lN.setTextSize(14);
        lN.setTextColor(TXT);
        rN.addView(lN, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch swN = new Switch(this);
        swN.setChecked(alwaysNotice());
        swN.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton v, boolean checked) {
                setAlways(checked);
                toast(checked ? "下次启动仍会显示说明" : "只在首次显示");
            }
        });
        rN.addView(swN);

        Button re = ghost(cN, "现在查看使用说明");
        re.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showDoc(); }
        });

        LinearLayout c6 = card(pr[4], "文件", "配置与状态落点");
        foot = new TextView(this);
        foot.setTextSize(11);
        foot.setTypeface(Typeface.MONOSPACE);
        foot.setTextColor(SUB);
        c6.addView(foot);

        final LinearLayout navRef = (LinearLayout) navbar();
        outer.addView(navRef);
        if (Build.VERSION.SDK_INT >= 20) {
            outer.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    int b = 0, t = 0;
                    try { b = insets.getSystemWindowInsetBottom(); } catch (Throwable e) {}
                    // 顶部也要让出来 —— 之前只处理了底部，标题会压在状态栏的时间和电量上
                    try { t = insets.getSystemWindowInsetTop(); } catch (Throwable e) {}
                    v.setPadding(0, t, 0, 0);
                    if (navRef != null) navRef.setPadding(0, dp(6), 0, dp(8) + b);
                    for (int k = 0; k < TABS; k++) {
                        if (pr != null && pr[k] != null) pr[k].setPadding(dp(16), dp(6), dp(16), dp(24) + b);
                    }
                    return insets;
                }
            });
        }
        setContentView(outer);
        try { releaseSampleScripts(false); } catch (Throwable t) { }
        showTab(0);
        try { rebuildCmds(); } catch (Throwable t) { }
        try { syncScriptsToPrefs(); } catch (Throwable t) { }   // 启动就把脚本状态推给目标进程
        // 收紧：只接受桌面图标那种启动（MAIN / LAUNCHER），外部 App 用别的 action 拉起时直接退出
        try {
            Intent it = getIntent();
            String act = it == null ? null : it.getAction();
            if (act != null && !Intent.ACTION_MAIN.equals(act)) {
                finish();
                return;
            }
        } catch (Throwable t) { }
        refresh();
        if (Build.VERSION.SDK_INT >= 20) { try { outer.requestApplyInsets(); } catch (Throwable t) {} }
        askStorage();
    }

    LinearLayout navbar() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(CHIP);
        nav.setPadding(0, dp(6), 0, dp(8));
        // 这两个数组的**顺序**就是显示顺序，必须与 navOrder 的映射一致
        String[] icons = { "◉", "◈", "▤", "✎", "⛭", "⚙" };
        String[] names = { "状态", "密钥", "配方", "自定义", "Root", "设置" };
        navLbl = new TextView[TABS];
        navIco = new TextView[TABS];
        for (int i = 0; i < TABS; i++) {
            final int idx = navOrder[i];
            LinearLayout it = new LinearLayout(this);
            it.setOrientation(LinearLayout.VERTICAL);
            it.setGravity(Gravity.CENTER);
            TextView ic = new TextView(this);
            ic.setText(icons[i]);
            ic.setTextSize(16);
            ic.setTextColor(SUB);
            ic.setIncludeFontPadding(false);
            ic.setPadding(0, 0, 0, dp(2));
            ic.setGravity(Gravity.CENTER);
            ic.setWidth(dp(20));
            it.addView(ic);
            TextView t = new TextView(this);
            t.setText(names[i]);
            t.setTextSize(11);
            t.setTextColor(SUB);
            t.setGravity(Gravity.CENTER);
            t.setIncludeFontPadding(false);
            it.addView(t);
            navIco[i] = ic;
            navLbl[i] = t;
            it.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { showTab(idx); }
            });
            nav.addView(it, new LinearLayout.LayoutParams(0, dp(54), 1f));
        }
        return nav;
    }

    /* ───────── 密钥页：Cookie 解密 ───────── */

    /** 当前选中的浏览器（CookieVault.BROWSERS 下标） */
    private int ckPick = 0;
    private EditText ckHost;
    private LinearLayout ckOut;
    private TextView ckTarget;
    private CookieVault.Result ckLast;
    /** 列表里显示明文还是脱敏 */
    private boolean ckPlain = false;
    /** 已展开的站点（按 host_key 记） */
    private final java.util.Set<String> ckOpen = new java.util.HashSet<String>();
    /** 当前可选的库列表：默认内置几个，扫描后换成扫到的全部 */
    private String[][] ckList = CookieVault.BROWSERS;

    String ckTargetLabel() {
        if (ckList == null || ckList.length == 0) return "（还没扫到库）";
        if (ckPick < 0 || ckPick >= ckList.length) ckPick = 0;
        String[] b = ckList[ckPick];
        return b[2].equals(b[0]) ? b[0] : (b[2] + " · " + b[0]);
    }

    void buildCookiePage(LinearLayout root) {
        pageHead(root, "密钥",
                "把浏览器自己的 Cookie 读出来、解开。"
                + "\n三种取值方式自动选：v10 密文解密 / value 列明文 / v20 明确报错。"
                + "\n离线解密要 Root；不想给 Root，就用下面的运行时抓取。");

        LinearLayout c1 = card(root, "离线解密", "读浏览器数据库自己解", 1);
        ckTarget = body("目标：" + ckTargetLabel());
        c1.addView(ckTarget);

        Button bScan = ghost(c1, "扫描本机所有浏览器库");
        bScan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runCookieScan(); }
        });

        Button bPick = ghost(c1, "换一个库");
        bPick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ckList == null || ckList.length == 0) { toast("先扫一次"); return; }
                ckPick = (ckPick + 1) % ckList.length;
                if (ckTarget != null) ckTarget.setText("目标：" + ckTargetLabel());
            }
        });

        ckHost = input(c1, "过滤：站点或 cookie 名（即时生效），例如 google.com");
        ckHost.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                if (ckLast != null) redrawCookies();
            }
        });

        Button go = primary(c1, "拉取并解密");
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runCookieHarvest(); }
        });
        markRootOnly(go);
        markRootOnly(bPick);
        markRootOnly(bScan);

        ckOut = new LinearLayout(this);
        ckOut.setOrientation(LinearLayout.VERTICAL);
        ckOut.setPadding(0, dp(6), 0, 0);
        c1.addView(ckOut);

        LinearLayout c2 = card(root, "运行时抓取", "不用解密 · hook 已装进目标 App");
        c2.addView(body("登录态在内存里本来就是明文。给目标 App 套一条配方，它自己会吐出来："));
        c2.addView(body("· 取登录态 —— hook CookieManager.getCookie，拿活 Cookie"));
        c2.addView(body("· 抓加密明文 —— hook Cipher / Mac，加密前和解密后的原文都记下来"));
        c2.addView(body("· 全链路加解密 —— 再加 MessageDigest / Base64 / Signature"));
        Button bRcp = ghost(c2, "去配方页套用");
        bRcp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(1); }
        });

        LinearLayout c3 = card(root, "解不开的时候", "v20 / App-Bound");
        c3.addView(body("前缀是 v20，说明密钥由系统服务保管、绑定浏览器自己的身份，磁盘上没有，离线解不开。"));
        c3.addView(body("这时只有一条路：以它的身份跑代码。模块的 hook 就在它进程里 —— 用上面的运行时抓取，或者 Frida 挂 OSCrypt_DecryptString。"));
        c3.addView(body("别在这条路上反复试密钥组合：v20 的密钥算不出来，试多少次都是乱码。"));
    }

    void runCookieScan() {
        if (ckOut == null) return;
        ckOut.removeAllViews();
        ckOut.addView(body("正在扫 /data/data 下的 Cookies 库…"));
        new Thread(new Runnable() {
            @Override public void run() {
                final String[][] hit = CookieVault.discover(new CookieVault.Root() {
                    @Override public String exec(String cmd, long timeoutMs) { return runRoot(cmd, timeoutMs); }
                });
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (ckOut == null) return;
                        ckOut.removeAllViews();
                        if (hit.length == 0) {
                            TextView t = body("没扫到。没有 Root，或者这台机器上确实没有 Chromium 系的库。");
                            t.setTextColor(BAD);
                            ckOut.addView(t);
                            return;
                        }
                        ckList = hit;
                        ckPick = 0;
                        // 扫到一堆库里默认挑 Chrome：那是绝大多数人要抓的那个
                        for (int i = 0; i < hit.length; i++) {
                            if ("com.android.chrome".equals(hit[i][0])) { ckPick = i; break; }
                        }
                        if (ckTarget != null) ckTarget.setText("目标：" + ckTargetLabel());
                        ckOut.addView(body("扫到 " + hit.length + " 个库。点「换一个库」切换，或直接解密当前这个："));
                        for (int i = 0; i < hit.length && i < 20; i++) {
                            String nm = hit[i][2].equals(hit[i][0]) ? hit[i][0] : (hit[i][2] + "   " + hit[i][0]);
                            ckOut.addView(body("· " + nm));
                        }
                    }
                });
            }
        }, "ck-scan").start();
    }

    void runCookieHarvest() {
        if (ckList == null || ckList.length == 0) { toast("先扫描一次"); return; }
        if (ckPick < 0 || ckPick >= ckList.length) ckPick = 0;
        final String pkg = ckList[ckPick][0];
        final String rel = ckList[ckPick][1];
        final String host = "";   // 一次拉全，过滤放界面里做：改关键词不用重拉库
        if (ckOut == null) return;
        ckOut.removeAllViews();
        ckOut.addView(body("正在拉库并解密（su 可能会弹授权）…"));
        new Thread(new Runnable() {
            @Override public void run() {
                final CookieVault.Result r = CookieVault.harvest(SettingsActivity.this,
                        new CookieVault.Root() {
                            @Override public String exec(String cmd, long timeoutMs) { return runRoot(cmd, timeoutMs); }
                        }, pkg, rel, host);
                runOnUiThread(new Runnable() {
                    @Override public void run() { renderCookies(r); }
                });
            }
        }, "ck-harvest").start();
    }

    /** 按 host 分组，组头可折叠；改关键词只重渲染，不重拉库 */
    void renderCookies(CookieVault.Result r) {
        ckLast = r;
        redrawCookies();
    }

    void redrawCookies() {
        if (ckOut == null) return;
        ckOut.removeAllViews();
        CookieVault.Result r = ckLast;
        if (r == null) return;
        if (!r.ok) {
            TextView t = body(r.note.length() > 0 ? r.note : "没解出东西");
            t.setTextColor(BAD);
            ckOut.addView(t);
            return;
        }
        java.util.List<CookieVault.Row> hit = ckFiltered();
        String sum = "共 " + r.rows.size() + " 条 · 解出 " + (r.v10 + r.plain)
                + "（密文 " + r.v10 + " / 明文 " + r.plain + "）";
        if (r.v20 > 0) sum = sum + " · v20 解不开 " + r.v20;
        if (r.fail > 0) sum = sum + " · 失败 " + r.fail;
        TextView st = body(sum);
        st.setTextColor((r.v20 + r.fail) > 0 ? BAD : ACC);
        ckOut.addView(st);

        LinearLayout tools = row(ckOut);
        Button bPlain = ghost(tools, ckPlain ? "明文显示中" : "脱敏显示");
        LinearLayout.LayoutParams lpT1 = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lpT1.rightMargin = dp(6);
        bPlain.setLayoutParams(lpT1);
        bPlain.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ckPlain = !ckPlain;
                redrawCookies();
            }
        });
        Button bEx = ghost(tools, "导出 JSON");
        bEx.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1f));
        bEx.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exportCookies(); }
        });

        if (hit.isEmpty()) {
            ckOut.addView(body(r.rows.isEmpty() ? "这个库里没有记录" : "没有匹配的 cookie"));
            return;
        }
        // 按 host 归组，保持库里原有顺序
        final java.util.Map<String, java.util.List<CookieVault.Row>> groups =
                new java.util.LinkedHashMap<String, java.util.List<CookieVault.Row>>();
        for (int i = 0; i < hit.size(); i++) {
            CookieVault.Row one = hit.get(i);
            java.util.List<CookieVault.Row> bucket = groups.get(one.host);
            if (bucket == null) {
                bucket = new java.util.ArrayList<CookieVault.Row>();
                groups.put(one.host, bucket);
            }
            bucket.add(one);
        }
        boolean single = groups.size() == 1;
        java.util.Iterator<String> it = groups.keySet().iterator();
        while (it.hasNext()) {
            final String host = it.next();
            final java.util.List<CookieVault.Row> bucket = groups.get(host);
            boolean open = single || ckOpen.contains(host);
            ckOut.addView(hostHeader(host, bucket, open));
            if (!open) continue;
            for (int i = 0; i < bucket.size(); i++) ckOut.addView(cookieItem(bucket.get(i)));
        }
    }

    java.util.List<CookieVault.Row> ckFiltered() {
        java.util.List<CookieVault.Row> out = new java.util.ArrayList<CookieVault.Row>();
        if (ckLast == null) return out;
        String q = ckHost == null ? "" : ckHost.getText().toString().trim().toLowerCase();
        for (int i = 0; i < ckLast.rows.size(); i++) {
            CookieVault.Row one = ckLast.rows.get(i);
            if (q.length() == 0) { out.add(one); continue; }
            String h = one.host == null ? "" : one.host.toLowerCase();
            String n = one.name == null ? "" : one.name.toLowerCase();
            if (h.contains(q) || n.contains(q)) out.add(one);
        }
        return out;
    }

    /** 一个站点的组头：点开/收起，右边一键复制整条 Cookie 请求头 */
    View hostHeader(final String host, final java.util.List<CookieVault.Row> bucket, final boolean open) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        holder.setGravity(Gravity.CENTER_VERTICAL);
        holder.setPadding(dp(6), dp(10), dp(6), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        holder.setLayoutParams(lp);

        TextView arrow = new TextView(this);
        arrow.setText(open ? "▾" : "▸");
        arrow.setTextSize(12);
        arrow.setTextColor(ACC);
        arrow.setPadding(0, 0, dp(8), 0);
        holder.addView(arrow);

        TextView label = new TextView(this);
        label.setText(host.length() == 0 ? "（无站点）" : host);
        label.setTextSize(12);
        label.setTextColor(TXT);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        holder.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

        int usable = 0;
        for (int i = 0; i < bucket.size(); i++) if (bucket.get(i).value.length() > 0) usable++;
        TextView cnt = new TextView(this);
        cnt.setText(bucket.size() + " 条" + (usable < bucket.size() ? " / 可用 " + usable : ""));
        cnt.setTextSize(10);
        cnt.setTextColor(usable == 0 ? BAD : SUB);
        holder.addView(cnt);

        if (usable > 0) {
            TextView cp = new TextView(this);
            cp.setText("复制头");
            cp.setTextSize(11);
            cp.setTextColor(ACC);
            cp.setPadding(dp(10), 0, 0, 0);
            cp.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { copyCookieHeader(host, bucket); }
            });
            holder.addView(cp);
        }

        holder.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ckOpen.contains(host)) ckOpen.remove(host);
                else ckOpen.add(host);
                redrawCookies();
            }
        });
        return holder;
    }

    /** 拼成一条 Cookie 请求头，拿去就能直接 curl */
    void copyCookieHeader(String host, java.util.List<CookieVault.Row> bucket) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bucket.size(); i++) {
            CookieVault.Row one = bucket.get(i);
            if (one.value.length() == 0) continue;
            if (sb.length() > 0) sb.append("; ");
            sb.append(one.name).append('=').append(one.value);
        }
        if (sb.length() == 0) { toast("这一组没有可用的值"); return; }
        copyText(sb.toString(), host + " 的 Cookie 头（" + sb.length() + " 字符）");
    }

    View cookieItem(final CookieVault.Row r) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(10), dp(8), dp(10), dp(8));
        item.setBackgroundDrawable(box(CARD, LINE, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        item.setLayoutParams(lp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView nm = new TextView(this);
        nm.setText(r.name);
        nm.setTextSize(12);
        nm.setTextColor(TXT);
        nm.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView how = new TextView(this);
        how.setText(r.how);
        how.setTextSize(10);
        how.setTextColor(("v20".equals(r.how) || "fail".equals(r.how)) ? BAD : ACC);
        head.addView(how);
        item.addView(head);

        TextView tvHost = new TextView(this);
        tvHost.setText(r.host);
        tvHost.setTextSize(10);
        tvHost.setTextColor(SUB);
        item.addView(tvHost);

        TextView val = new TextView(this);
        val.setText(r.value.length() == 0 ? "（空）" : (ckPlain ? r.value : CookieVault.mask(r.value)));
        val.setTextSize(11);
        val.setTextColor(SUB);
        val.setTypeface(Typeface.MONOSPACE);
        item.addView(val);

        item.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (r.value.length() == 0) copyText(r.host + "\t" + r.name, "主机与名称");
                else copyText(r.value, r.name);
            }
        });
        return item;
    }

    void copyText(String s, String what) {
        try {
            android.content.ClipboardManager c =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            c.setPrimaryClip(android.content.ClipData.newPlainText("xhook", s));
            toast(what + " 已复制");
        } catch (Throwable t) { toast("复制失败"); }
    }

    void exportCookies() {
        if (ckLast == null) return;
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (int i = 0; i < ckLast.rows.size(); i++) {
                CookieVault.Row r = ckLast.rows.get(i);
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("host", r.host);
                o.put("name", r.name);
                o.put("how", r.how);
                o.put("value", r.value);
                arr.put(o);
            }
            org.json.JSONObject top = new org.json.JSONObject();
            top.put("package", ckList[ckPick][0]);
            top.put("cookieRel", ckList[ckPick][1]);
            top.put("time", System.currentTimeMillis());
            top.put("count", ckLast.rows.size());
            top.put("items", arr);
            java.io.File dir = new java.io.File(CTRL);   // 跟着控制目录走，别写死
            dir.mkdirs();
            java.io.File f = new java.io.File(dir, "cookies.json");
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(top.toString(1).getBytes("UTF-8"));
            fo.close();
            toast("已写到 " + f.getAbsolutePath());
        } catch (Throwable t) {
            toast("导出失败：" + t.getClass().getSimpleName());
        }
    }

    void showTab(int i) {
        curTab = i;
        for (int k = 0; k < TABS; k++) {
            if (ps != null && ps[k] != null) ps[k].setVisibility(k == i ? View.VISIBLE : View.GONE);
        }
        for (int k = 0; k < TABS; k++) {
            boolean on = navOrder[k] == i;
            if (navLbl != null && navLbl[k] != null) {
                navLbl[k].setTextColor(on ? ACC : SUB);
                navLbl[k].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            }
            if (navIco != null && navIco[k] != null) navIco[k].setTextColor(on ? ACC : SUB);
        }
    }

    GradientDrawable box(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    LinearLayout card(LinearLayout root, String name) { return card(root, name, null, -1); }

    LinearLayout card(LinearLayout root, String name, String sub) { return card(root, name, sub, -1); }

    /**
     * rootTag：-1 不标 / 0 免 Root / 1 需 Root。
     * 标在标题右边，让人一眼知道这张卡动不动就要授权。
     */
    LinearLayout card(LinearLayout root, String name, String sub, int rootTag) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundDrawable(box(CARD, LINE, 14));
        wrap.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        root.addView(wrap, lp);

        LinearLayout hrow = new LinearLayout(this);
        hrow.setOrientation(LinearLayout.HORIZONTAL);
        hrow.setGravity(Gravity.CENTER_VERTICAL);

        TextView h = new TextView(this);
        h.setText(name);
        h.setTextSize(12);
        h.setTextColor(ACC);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        hrow.addView(h, new LinearLayout.LayoutParams(0, -2, 1f));

        if (rootTag >= 0) {
            TextView tag = new TextView(this);
            tag.setText(rootTag == 1 ? "需 Root" : "免 Root");
            tag.setTextSize(9);
            tag.setTextColor(rootTag == 1 ? 0xffff8a65 : SUB);
            tag.setBackgroundDrawable(box(rootTag == 1 ? (darkMode ? 0xff3A2418 : 0xffFFF0E6) : CHIP, LINE, 6));
            tag.setPadding(dp(5), dp(2), dp(5), dp(2));
            hrow.addView(tag);
        }

        hrow.setPadding(0, 0, 0, sub == null ? dp(6) : dp(2));
        wrap.addView(hrow);
        if (sub != null) {
            TextView s2 = new TextView(this);
            s2.setText(sub);
            s2.setTextSize(11);
            s2.setTextColor(SUB);
            s2.setPadding(0, 0, 0, dp(8));
            wrap.addView(s2);
        }
        return wrap;
    }

    LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    View grid() {
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        String[][] items = {{ "log", "明文日志" }, { "args", "改入参" }, { "return", "改返回值" },
            { "js", "注入 JS" }, { "cookie", "读 Cookie" }, { "call", "调静态方法" }};
        for (int r = 0; r < 2; r++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 3; c++) {
                String[] it = items[r * 3 + c];
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setBackgroundDrawable(box(CHIP, LINE, 10));
                TextView a = new TextView(this);
                a.setText(it[0]);
                a.setTextSize(14);
                a.setTextColor(ACC);
                a.setTypeface(Typeface.DEFAULT_BOLD);
                a.setGravity(Gravity.CENTER);
                cell.addView(a, new LinearLayout.LayoutParams(-1, -2));
                TextView b = new TextView(this);
                b.setText(it[1]);
                b.setTextSize(10);
                b.setTextColor(SUB);
                b.setGravity(Gravity.CENTER);
                cell.addView(b, new LinearLayout.LayoutParams(-1, -2));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1f);
                lp.rightMargin = c == 2 ? 0 : dp(6);
                lp.topMargin = dp(6);
                line.addView(cell, lp);
            }
            w.addView(line);
        }
        return w;
    }

    View chipsLegacy() {
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        String[][] items = {{ "log", "明文日志" }, { "args", "改入参" }, { "return", "改返回值" },
            { "js", "注入网页 JS" }, { "cookie", "读 Cookie" }, { "call", "调静态方法" }};
        LinearLayout line = row(w);
        for (int i = 0; i < items.length; i++) {
            if (i == 3) { line = row(w); }
            TextView t = new TextView(this);
            t.setText(items[i][0] + " · " + items[i][1]);
            t.setTextSize(11);
            t.setTextColor(TXT);
            t.setBackgroundDrawable(box(CHIP, 0, 8));
            t.setPadding(dp(9), dp(5), dp(9), dp(5));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(6);
            lp.topMargin = dp(4);
            line.addView(t, lp);
        }
        return w;
    }

    EditText input(LinearLayout parent, String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.parseColor("#5c6b7e"));
        e.setTextColor(TXT);
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setBackgroundDrawable(box(BG, LINE, 10));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        parent.addView(e, lp);
        return e;
    }

    Button primary(LinearLayout parent, String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setBackgroundDrawable(box(ACC, 0, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.topMargin = dp(12);
        parent.addView(b, lp);
        return b;
    }

    Button ghost(LinearLayout parent, String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setTextColor(TXT);
        b.setTextSize(13);
        b.setBackgroundDrawable(box(CHIP, LINE, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.topMargin = dp(8);
        parent.addView(b, lp);
        return b;
    }

    /** 读能力清单（Melon 式 manifest）：UI 从清单渲染，加配方只改 json 不改 Java。 */
    org.json.JSONObject caps() {
        try {
            String json = readAsset("capabilities.json");
            return json == null ? null : new org.json.JSONObject(json);
        } catch (Throwable t) { return null; }
    }

    View recipeRow(final String name, final String desc) {
        return recipeRow(name, desc, "");
    }

    View recipeRow(final String id, final String title, final String when) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        holder.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(when.length() > 0 ? title : (title.length() > 0 ? title : id));
        t.setTextSize(13);
        t.setTextColor(TXT);
        col.addView(t);
        if (when.length() > 0) {
            TextView w = new TextView(this);
            w.setText(when);
            w.setTextSize(10);
            w.setTextColor(SUB);
            col.addView(w);
        }
        holder.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        Button b = new Button(this);
        b.setText("套用");
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setTextSize(12);
        b.setTextColor(ACC);
        b.setBackgroundDrawable(box(CHIP, LINE, 8));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { applyRecipe(id, when.length() > 0 ? title : id); }
        });
        holder.addView(b, new LinearLayout.LayoutParams(dp(64), dp(34)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        holder.setLayoutParams(lp);
        return holder;
    }

    String pkg() { return ePkg.getText().toString().trim(); }

    void applyRecipe(String name, String desc) {
        String p = pkg();
        if (p.length() == 0) { toast("先填目标包名"); return; }
        String json = readAsset("recipes/" + name + ".json");
        if (json == null) { toast("配方读取失败"); return; }
        toast(writeUser(p, json) ? desc + " 已写入，重启 " + p + " 生效" : "写入失败，检查存储权限");
    }

    void writeCustom() {
        String p = pkg();
        String cls = eCls.getText().toString().trim();
        String mtd = eMtd.getText().toString().trim();
        String mode = eMode.getText().toString().trim();
        String val = eVal.getText().toString().trim();
        if (p.length() == 0 || cls.length() == 0 || mtd.length() == 0) { toast("包名 / 类名 / 方法都要填"); return; }
        if (mode.length() == 0) mode = "log";
        StringBuilder h = new StringBuilder("{");
        h.append(Q).append("class").append(Q).append(CL).append(Q).append(esc(cls)).append(Q).append(CO);
        h.append(Q).append("method").append(Q).append(CL).append(Q).append(esc(mtd)).append(Q).append(CO);
        h.append(Q).append("mode").append(Q).append(CL).append(Q).append(esc(mode)).append(Q);
        if (val.length() > 0) {
            String k = mode.equals("return") ? "return_value" : (mode.equals("js") ? "js" : "value");
            h.append(CO).append(Q).append(k).append(Q).append(CL).append(Q).append(esc(val)).append(Q);
        }
        h.append("}");
        StringBuilder j = new StringBuilder("{");
        j.append(Q).append("process").append(Q).append(CL).append(Q).append("main").append(Q).append(CO);
        j.append(Q).append("sample").append(Q).append(CL).append("2").append(CO);
        j.append(Q).append("window_ms").append(Q).append(CL).append("1000").append(CO);
        j.append(Q).append("head").append(Q).append(CL).append("300").append(CO);
        j.append(Q).append("tail").append(Q).append(CL).append("300").append(CO);
        j.append(Q).append("max_logs").append(Q).append(CL).append("600").append(CO);
        j.append(Q).append("hooks").append(Q).append(CL).append("[").append(h).append("]}");
        toast(writeUser(p, j.toString()) ? p + ".json 已写入" : "写入失败，检查存储权限");
    }

    boolean writeUser(String p, String json) {
        try {
            File d = new File(USERDIR);
            if (!d.exists()) d.mkdirs();
            FileOutputStream fo = new FileOutputStream(new File(d, p + ".json"), false);
            fo.write(json.getBytes("UTF-8"));
            fo.close();
            refresh();
            return true;
        } catch (Throwable t) { return false; }
    }

    String readAsset(String path) {
        try {
            InputStream in = getAssets().open(path);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) { return null; }
    }

    String esc(String v) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == (char) 34 || c == (char) 92) { b.append((char) 92).append(c); }
            else if (c == (char) 10) { b.append((char) 92).append('n'); }
            else b.append(c);
        }
        return b.toString();
    }

    void setInject(boolean on) {
        File off = new File(OFF);
        try {
            if (on) { if (off.exists()) off.delete(); }
            else { File d = new File(CTRL); if (!d.exists()) d.mkdirs(); if (!off.exists()) off.createNewFile(); }
        } catch (Throwable t) {}
        refresh();
        toast(on ? "注入已开启" : "注入已关闭");
    }

    static final int REQ_PICK = 1001;

    void pickFile() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_PICK);
        } catch (Throwable t) { toast("没有文件选择器可用"); }
    }

    /** 选一个 .json 当注入指令导进 cmds/ */
    static final int REQ_PICK_CMD = 9002;

    void pickCmdFile() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_PICK_CMD);
        } catch (Throwable t) { toast("没有文件选择器可用"); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;

        // —— 选文件导入「注入指令」——
        if (req == REQ_PICK_CMD) {
            Uri cu = data.getData();
            if (cu == null) { toast("没拿到文件"); return; }
            String cname = "";
            try { cname = cu.getLastPathSegment(); } catch (Throwable t) { }
            if (cname == null) cname = "";
            String ctext = readUri(cu);
            if (ctext == null) { toast("读取失败：文件超过 1MB 或不可读"); return; }
            String base = cname.length() == 0 ? ("cmd-" + System.currentTimeMillis()) : cname;
            if (!base.toLowerCase().endsWith(".json")) base = base + ".json";
            try {
                File cd = new File(CMDS); if (!cd.exists()) cd.mkdirs();
                FileOutputStream fo = new FileOutputStream(new File(cd, base), false);
                fo.write(ctext.getBytes("UTF-8"));
                fo.close();
                rebuildCmds();
                toast("已导入指令 " + base + "，点「重发」就能执行");
            } catch (Throwable t) { toast("导入失败"); }
            return;
        }

        if (req != REQ_PICK) return;
        Uri uri = data.getData();
        if (uri == null) { toast("没拿到文件"); return; }
        String name = "";
        try { name = uri.getLastPathSegment(); } catch (Throwable t) {}
        if (name == null) name = "";
        String text = readUri(uri);
        if (text == null) { toast("读取失败：文件超过 1MB 或不可读"); return; }
        if (name.toLowerCase().endsWith(".json")) {
            String p = pkg();
            if (p.length() == 0) { toast("先在配方页填目标包名"); return; }
            if (writeUser(p, text)) toast("配置已写入 " + p + ".json");
            else toast("写入失败，检查存储权限");
        } else {
            String base = name.length() == 0 ? ("script-" + System.currentTimeMillis()) : name;
            if (!base.toLowerCase().endsWith(".js")) base = base + ".js";
            writeScript(base, text);
        }
    }

    static final int MAX_PICK = 1048576;

    String readUri(Uri u) {
        try {
            InputStream in = getContentResolver().openInputStream(u);
            if (in == null) return null;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_PICK) { in.close(); return null; }
                bo.write(buf, 0, n);
            }
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) { return null; }
    }

    /**
     * 按字节原样读文本 —— 不能按行拼，按行拼会多出/吃掉换行，
     * 导致 prefs 里的内容和文件差几个字节，目标进程 hash 对不上就会重复注入。
     */
    String readTextFile(File f) {
        try {
            int len = (int) f.length();
            if (len <= 0) return "";
            byte[] b = new byte[len];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int off = 0, n;
            while (off < len && (n = in.read(b, off, len - off)) > 0) off += n;
            in.close();
            return new String(b, 0, off, "UTF-8");
        } catch (Throwable t) { return null; }
    }

    /**
     * 把 scripts 目录里「启用着的」脚本同步进 SharedPreferences。
     * 目标进程通过 ContentProvider 过来取，不用去 /sdcard 读文件。
     * 关掉的脚本不传，省带宽也省目标进程的解析。
     */
    void syncScriptsToPrefs() {
        try {
            android.content.SharedPreferences p = getSharedPreferences(XhookProvider.PREFS, 0);
            android.content.SharedPreferences.Editor ed = p.edit();
            // 先清掉上一轮的 script: 键，避免删掉的脚本还在
            for (String k : p.getAll().keySet()) {
                if (k != null && k.startsWith("script:")) ed.remove(k);
            }
            File sd = new File(SCRIPTS);
            File[] fs = sd.listFiles();
            if (fs != null) {
                java.util.Arrays.sort(fs, new java.util.Comparator<File>() {
                    @Override public int compare(File a, File b) { return a.getName().compareTo(b.getName()); }
                });
                int total = 0;
                for (File f : fs) {
                    String n = f.getName();
                    if (!n.endsWith(".js")) continue;
                    if (new File(sd, n + ".off").exists()) continue;   // 关着的不传
                    String code = readTextFile(f);
                    if (code == null || code.length() == 0) continue;
                    if (total + code.length() > 400000) break;          // Bundle 有上限，防超
                    total += code.length();
                    ed.putString("script:" + n, code);
                }
            }
            ed.commit();
        } catch (Throwable t) { }
    }

    void writeScript(String name, String js) {
        try {
            File d = new File(SCRIPTS); if (!d.exists()) d.mkdirs();
            FileOutputStream fo = new FileOutputStream(new File(d, name), false);
            fo.write(js.getBytes("UTF-8"));
            fo.close();
            refresh();
            toast("已加入 " + name);
        } catch (Throwable t) { toast("写入失败，检查存储权限"); }
    }

    void setAbility(String key, boolean on) {
        try {
            File f = new File(ABIL, key + ".off");
            if (on) { if (f.exists()) f.delete(); }
            else {
                File d = new File(ABIL); if (!d.exists()) d.mkdirs();
                if (!f.exists()) f.createNewFile();
            }
            refresh();
            toast(key + (on ? " 已开启" : " 已关闭"));
        } catch (Throwable t) {}
    }

    void toggleScript(String name, boolean on) {
        try {
            File f = new File(SCRIPTS, name + ".off");
            if (on) { if (f.exists()) f.delete(); }
            else {
                File d = new File(SCRIPTS); if (!d.exists()) d.mkdirs();
                if (!f.exists()) f.createNewFile();
            }
            toast(name + (on ? " 已启用" : " 已停用"));
        } catch (Throwable t) {}
        rebuildScripts();
    }

    /**
     * 内置示例脚本：scripts 目录空的时候释放一份进去，让「自动识别」有东西可认。
     * 默认写成关闭状态（.off），不让用户一打开网页就被改样式。
     */
    /**
     * force=false（启动时）：什么都不做 —— 内置脚本已经从 APK 直接注入，不需要部署副本。
     * force=true（用户点「恢复内置示例脚本」）：把内置的导到 scripts/ 目录，供手动修改。
     */
    void releaseSampleScripts(boolean force) {
        if (!force) return;
        try {
            File sd = new File(SCRIPTS);
            if (!sd.exists()) sd.mkdirs();
            File[] exist = sd.listFiles();
            if (!force && exist != null && exist.length > 0) return;   // 已经有脚本，不打扰
            String[] names = getAssets().list("scripts");
            if (names == null || names.length == 0) return;
            for (String n : names) {
                if (!n.endsWith(".js")) continue;
                java.io.InputStream is = getAssets().open("scripts/" + n);
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int k;
                while ((k = is.read(buf)) > 0) bo.write(buf, 0, k);
                is.close();
                FileOutputStream fo = new FileOutputStream(new File(sd, n), false);
                fo.write(bo.toByteArray());
                fo.close();
                new FileOutputStream(new File(sd, n + ".off"), false).close();
            }
        } catch (Throwable t) { }
    }

    void setAllScripts(boolean on) {
        File sd = new File(SCRIPTS);
        File[] fs = sd.listFiles();
        if (fs != null) {
            for (File f : fs) {
                String n = f.getName();
                if (!n.endsWith(".js")) continue;
                toggleScript(n, on);
            }
        }
        rebuildScripts();
        toast(on ? "全部脚本已开启" : "全部脚本已关闭");
    }

    /** 打开应用选择器（默认只列用户应用，右上角菜单可切系统应用） */
    void showAppPicker() {
        try {
            AppPickerDialog d = new AppPickerDialog(this, new AppPickerDialog.OnPick() {
                @Override public void onPick(String pkg, String label) {
                    ePkg.setText(pkg);
                    toast("已选 " + label);
                }
            });
            d.show();
        } catch (Throwable t) {
            toast("打不开应用列表：" + t.getClass().getSimpleName());
        }
    }

    /** 内置脚本被「从列表移除」过（不是真删，APK 里的还在） */
    boolean builtinHidden(String name) {
        try {
            return "1".equals(getSharedPreferences(XhookProvider.PREFS, 0).getString("scriptHide:" + name, ""));
        } catch (Throwable t) { return false; }
    }

    /** 内置脚本的开关状态存在 prefs 里（prefs 里有 scriptOn:xxx=1 表示开过） */
    boolean builtinOn(String name) {
        try {
            android.content.SharedPreferences p = getSharedPreferences(XhookProvider.PREFS, 0);
            if ("1".equals(p.getString("scriptOn:" + name, ""))) return true;
            if ("1".equals(p.getString("scriptOff:" + name, ""))) return false;
        } catch (Throwable t) { }
        return "示例1-链接标红.js".equals(name);   // 默认只开这个演示
    }

    void setBuiltinOn(String name, boolean on) {
        try {
            getSharedPreferences(XhookProvider.PREFS, 0).edit()
                .putString(on ? "scriptOn:" + name : "scriptOff:" + name, "1")
                .commit();
        } catch (Throwable t) { }
        toast(name + (on ? " 已启用" : " 已停用"));
    }

    void rebuildScripts() {
        if (scriptBox == null) return;
        scriptBox.removeAllViews();
        boolean any = false;

        // ① 内置脚本（打包在 APK 里，只能开关，删不掉）
        try {
            String[] bn = getAssets().list("scripts");
            if (bn != null) {
                java.util.Arrays.sort(bn);
                for (final String n : bn) {
                    if (!n.endsWith(".js")) continue;
                    if (builtinHidden(n)) continue;   // 用户从列表移除过，就不再显示
                    any = true;
                    LinearLayout r = row(scriptBox);
                    TextView t = new TextView(this);
                    t.setText(n + "  内置");
                    t.setTextSize(12);
                    t.setTextColor(SUB);
                    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
                    Switch sw = new Switch(this);
                    sw.setChecked(builtinOn(n));
                    sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        public void onCheckedChanged(CompoundButton v, boolean checked) { setBuiltinOn(n, checked); }
                    });
                    r.addView(sw);
                    // 从这里移除：不删 APK 里的文件，只是不再显示、不再注入
                    TextView del = new TextView(this);
                    del.setText("移除");
                    del.setTextSize(12);
                    del.setTextColor(BAD);
                    del.setGravity(Gravity.CENTER);
                    del.setPadding(dp(10), dp(4), dp(2), dp(4));
                    del.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            try {
                                getSharedPreferences(XhookProvider.PREFS, 0).edit()
                                    .putString("scriptHide:" + n, "1")
                                    .putString("scriptOff:" + n, "1")
                                    .commit();
                            } catch (Throwable t) { }
                            rebuildScripts();
                            toast("已从列表移除（APK 里的还在，点「恢复内置示例脚本」能拿回来）");
                        }
                    });
                    r.addView(del);
                }
            }
        } catch (Throwable t) { }

        File sd = new File(SCRIPTS);
        if (!sd.exists()) { try { sd.mkdirs(); } catch (Throwable t) {} }
        File[] fs = sd.listFiles();
        if (fs != null) {
            for (File f : fs) {
                final String n = f.getName();
                if (!n.endsWith(".js")) continue;
                any = true;
                LinearLayout r = row(scriptBox);
                TextView t = new TextView(this);
                long kb = Math.max(1, f.length() / 1024);
                t.setText(n + "  " + kb + "KB");
                t.setTextSize(12);
                t.setTextColor(TXT);
                r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
                Switch sw = new Switch(this);
                sw.setChecked(!new File(sd, n + ".off").exists());
                sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton v, boolean checked) { toggleScript(n, checked); }
                });
                r.addView(sw);
                // 单独删这一个脚本（连带它的 .off 开关文件）
                TextView del = new TextView(this);
                del.setText("删");
                del.setTextSize(12);
                del.setTextColor(BAD);
                del.setGravity(Gravity.CENTER);
                del.setPadding(dp(12), dp(4), dp(2), dp(4));
                del.setOnClickListener(new View.OnClickListener() {
                    private boolean armed = false;
                    private void reset() { armed = false; del.setText("删"); del.setTextColor(BAD); }
                    @Override public void onClick(View v) {
                        if (!armed) {
                            // 本地脚本删了不可恢复（内置的能恢复，本地的不行），所以要点两下
                            armed = true;
                            del.setText("确认删");
                            del.setTextColor(0xFFFF3B30);
                            try {
                                v.postDelayed(new Runnable() { public void run() { if (armed) reset(); } }, 3000L);
                            } catch (Throwable t) { }
                            return;
                        }
                        boolean ok = new File(sd, n).delete();
                        new File(sd, n + ".off").delete();
                        rebuildScripts();
                        toast(ok ? "已删除 " + n + "（不可恢复）" : "删不掉：" + n);
                    }
                });
                r.addView(del);
            }
        }
        if (!any) scriptBox.addView(body("（还没有脚本，点上面「自定义命令」选个 .js 加入）"));
        syncScriptsToPrefs();   // 界面刷新即同步给目标进程
    }

    void writeCmdJs(String js) {
        try {
            File d = new File(CTRL); if (!d.exists()) d.mkdirs();
            StringBuilder s = new StringBuilder("{");
            s.append(Q).append("ts").append(Q).append(CL).append(System.currentTimeMillis()).append(CO);
            s.append(Q).append("js").append(Q).append(CL).append(Q).append(esc(js)).append(Q).append("}");
            FileOutputStream fo = new FileOutputStream(CMD, false);
            fo.write(s.toString().getBytes("UTF-8"));
            fo.close();
            refresh();
        } catch (Throwable t) { toast("写入失败"); }
    }

    void cleanAlive() {
        try {
            String[] fs = new File(CTRL).list();
            if (fs == null) return;
            for (String n : fs) {
                if (n.startsWith("alive-") || n.startsWith("probe-")) new File(CTRL, n).delete();
            }
        } catch (Throwable t) {}
    }

    /** 隐身开关：以 SharedPreferences 为主（目标进程走 provider 读），文件只做老版本兼容 */
    boolean stealthOn() {
        try {
            return getSharedPreferences(XhookProvider.PREFS, 0).getBoolean("stealth", false);
        } catch (Throwable t) { return false; }
    }

    void setStealth(boolean on) {
        // 记下「用户亲自改过」，之后就不再强制默认开
        try { getSharedPreferences(XhookProvider.PREFS, 0).edit().putBoolean("stealthUserSet", true).commit(); } catch (Throwable t) { }
        if (on) cleanAlive();
        // 只写配置：目标进程走 provider 读，不再往 /sdcard 丢标记文件
        try { getSharedPreferences(XhookProvider.PREFS, 0).edit().putBoolean("stealth", on).commit(); } catch (Throwable t) { }
        refresh();
        toast(on ? "隐身已开启，重启目标 App 生效" : "隐身已关闭");
    }

    /**
     * 只清指令：当前 cmd.json + cmds/ 历史。
     * 脚本不动 —— 那是你一个个加进来的，要删就点每行后面的「删」。
     */
    void clearAllCmds() {
        int n = 0;
        try {
            android.content.SharedPreferences p = getSharedPreferences(XhookProvider.PREFS, 0);
            android.content.SharedPreferences.Editor ed = p.edit();
            for (String k : p.getAll().keySet()) {
                if (k.startsWith("cmd")) { ed.remove(k); n++; }
            }
            ed.commit();
        } catch (Throwable t) { }
        // 顺手清掉过渡期留下的文件
        try { File c = new File(CMD); if (c.exists()) c.delete(); } catch (Throwable t) { }
        try {
            File cd = new File(CMDS);
            File[] cf = cd.listFiles();
            if (cf != null) for (File f : cf) if (f.getName().endsWith(".json")) f.delete();
        } catch (Throwable t) { }
        rebuildCmds();
        refreshCmdHint();
        refresh();
        toast(n > 0 ? "已清空 " + n + " 条指令（脚本没动）" : "本来就是空的");
    }

    boolean sendCmd(String js) { return sendCmd(js, "已发送，目标 App 里 2.5 秒内执行"); }

    /** 发一条指令：写 cmd.json 触发执行，同时存一份历史到 cmds/ */
    boolean sendCmd(String js, String okMsg) {
        try {
            long ts = System.currentTimeMillis();
            String body2 = "{\"ts\":" + ts + ",\"js\":\"" + esc(js) + "\"}";
            android.content.SharedPreferences p = getSharedPreferences(XhookProvider.PREFS, 0);
            android.content.SharedPreferences.Editor ed = p.edit();
            ed.putString("cmd", body2);              // 目标进程读这个执行
            ed.putString("cmd:" + ts, js);           // 历史，只给这个界面用
            // 历史最多留 10 条，超了删最旧的
            java.util.List<String> keys = new java.util.ArrayList<String>();
            for (String k : p.getAll().keySet()) if (k.startsWith("cmd:")) keys.add(k);
            java.util.Collections.sort(keys);
            while (keys.size() > 10) ed.remove(keys.remove(0));
            ed.commit();
            refreshCmdHint();
            toast(okMsg);
            return true;
        } catch (Throwable t) { toast("发送失败"); return false; }
    }

    /** 历史指令列表：最近 10 条，可重发、可删 */
    void rebuildCmds() {
        if (cmdBox == null) return;
        cmdBox.removeAllViews();
        android.content.SharedPreferences p = getSharedPreferences(XhookProvider.PREFS, 0);
        java.util.List<String> keys = new java.util.ArrayList<String>();
        for (String k : p.getAll().keySet()) if (k.startsWith("cmd:")) keys.add(k);
        if (keys.isEmpty()) {
            cmdBox.addView(body("（还没发过指令）"));
            return;
        }
        java.util.Collections.sort(keys, java.util.Collections.reverseOrder());   // 最新的在前
        for (final String k : keys) {
            final String js = p.getString(k, "");
            if (js == null || js.length() == 0) continue;
            String show = js.length() > 36 ? js.substring(0, 36) + "…" : js;
            LinearLayout r = row(cmdBox);
            TextView t = new TextView(this);
            t.setText(show);
            t.setTextSize(11);
            t.setTextColor(TXT);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView re = new TextView(this);
            re.setText("重发");
            re.setTextSize(11);
            re.setTextColor(ACC);
            re.setPadding(dp(10), dp(4), dp(6), dp(4));
            re.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { sendCmd(js, "已重发"); rebuildCmds(); }
            });
            r.addView(re);
            TextView del = new TextView(this);
            del.setText("删");
            del.setTextSize(11);
            del.setTextColor(BAD);
            del.setPadding(dp(6), dp(4), dp(2), dp(4));
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try { getSharedPreferences(XhookProvider.PREFS, 0).edit().remove(k).commit(); } catch (Throwable t) { }
                    rebuildCmds();
                    toast("已删");
                }
            });
            r.addView(del);
        }
    }

    /** 刷新「指令文件」那一行的状态 */
    void refreshCmdHint() {
        if (cmdHint == null) return;
        String cmdStr = null;
        try { cmdStr = getSharedPreferences(XhookProvider.PREFS, 0).getString("cmd", null); } catch (Throwable t) { }
        if (cmdStr != null && cmdStr.length() > 0) {
            String preview = cmdStr;
            if (preview.length() > 90) preview = preview.substring(0, 90) + "…";
            cmdHint.setText("当前指令已下发\n内容：" + preview);
            cmdHint.setTextColor(TXT);
        } else {
            cmdHint.setText("还没有下发过指令。\n「写入测试指令」是一次性的，执行一次就结束；常驻的用下面的注入脚本。");
            cmdHint.setTextColor(SUB);
        }
    }

    /** 写入测试指令 —— 走和「发送」同一条路，所以也会出现在下面的历史列表里 */
    void writeTest() {
        sendCmd("document.title='xhook-test'", "测试指令已下发，去目标 App 看标题变没变");
        refresh();
        rebuildCmds();
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    /**
     * 打开 LSPosed 管理器。
     * 这台机器上没有独立的 LSPosed 包名（dumpsys 查不到 org.lsposed.manager），
     * 管理器是靠 secret code 唤起的 —— KernelSU 里那个模块的 action.sh 就是发这个广播：
     *   am broadcast -a android.telephony.action.SECRET_CODE -d android_secret_code://5776733 android
     * 普通 App 直接发可能被系统拦，所以失败时退回到「告诉用户怎么手点」。
     */
    void openLsposed() {
        // ① 有 root 就直接发 secret code 广播 —— 一步进 LSPosed，不用任何人动手。
        //    SECRET_CODE 是受保护广播，普通 App 发不出去（实测 Permission Denial），
        //    但本模块已经在 SukiSU 的授权名单里，su 可以的。
        new Thread(new Runnable() {
            @Override public void run() {
                final boolean ok = rootBroadcast();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (ok) { toast("已打开 LSPosed"); return; }
                        openLsposedFallback();
                    }
                });
            }
        }, "xhook-openlspd").start();
    }

    /**
     * 用 root 发 secret code 广播。成功返回 true。
     * 没有 root、被拒、或者 am 报错都返回 false —— 调用方会退回别的路子。
     */
    /**
     * 用 root 读 LSPosed 的配置库，直接拿到「本模块此刻是否启用」。
     * 这是最准的一路 —— 不依赖进程有没有被注入、也不用等探针。
     * 返回 TRUE/FALSE；读不到返回 null（会退回探针判定）。
     */
    Boolean rootEnabled() {
        File dir = getFilesDir();
        try { dir.mkdirs(); } catch (Throwable t) { }   // 目录可能还没建过，cp 会无处可放
        String dst = new File(dir, "lspd_state.db").getAbsolutePath();
        String uid = String.valueOf(android.os.Process.myUid());
        String cmd = "rm -f " + dst + " " + dst + "-wal " + dst + "-shm; "
                + "cp /data/adb/lspd/config/modules_config.db " + dst + " && "
                + "cp /data/adb/lspd/config/modules_config.db-wal " + dst + "-wal 2>/dev/null; "
                + "chown " + uid + ":" + uid + " " + dst + " 2>/dev/null; "
                + "chmod 600 " + dst + " 2>/dev/null; echo OK";
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[] { "su", "-c", cmd });
            final Process pp = p;
            final StringBuilder sb = new StringBuilder();
            Thread rd = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        java.io.InputStream is = pp.getInputStream();
                        byte[] b = new byte[1024];
                        int n;
                        while ((n = is.read(b)) > 0) sb.append(new String(b, 0, n, "UTF-8"));
                    } catch (Throwable t) { }
                }
            });
            rd.setDaemon(true);
            rd.start();
            long end = System.currentTimeMillis() + 6000L;
            while (System.currentTimeMillis() < end) {
                try { p.exitValue(); break; } catch (Throwable nr) { try { Thread.sleep(50L); } catch (Throwable s) { break; } }
            }
            try { if (p.exitValue() != 0) return null; } catch (Throwable t) { p.destroy(); return null; }
            if (!sb.toString().contains("OK")) return null;
        } catch (Throwable t) {
            if (p != null) { try { p.destroy(); } catch (Throwable t2) { } }
            return null;
        }
        android.database.sqlite.SQLiteDatabase db = null;
        android.database.Cursor c = null;
        try {
            db = android.database.sqlite.SQLiteDatabase.openDatabase(dst, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery("select enabled from modules_state where module_pkg_name=?", new String[] { getPackageName() });
            if (c.moveToFirst()) return c.getInt(0) == 1;
            return Boolean.FALSE;   // 表里没有这一行 = 没启用
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (c != null) c.close(); } catch (Throwable t) { }
            try { if (db != null) db.close(); } catch (Throwable t) { }
        }
    }

    /** Root 到底通不通：su 成功执行过一次就是 true */
    private volatile boolean rootAvailable = false;
    /** 有没有测过 —— 没测过就不灰，避免打开瞬间按钮闪一下 */
    private volatile boolean rootKnown = false;
    /** 真正确认过 uid=0 才算有 root。su -c 退出码 0 不等于提权成功（本机会静默回落到 App 自己的 uid） */
    private volatile boolean rootVerified = false;

    /** 需要 Root 才能用的控件：没权限时统一置灰并禁用 */
    private final java.util.List<View> rootOnly = new java.util.ArrayList<View>();

    void markRootOnly(View v) {
        if (v != null) rootOnly.add(v);
    }

    /** 把「有没有 root」应用到这些控件上 */
    void applyRootState() {
        boolean on = !rootKnown || rootVerified;
        for (View v : rootOnly) {
            try {
                v.setEnabled(on);
                v.setAlpha(on ? 1f : 0.35f);
            } catch (Throwable t) { }
        }
    }

    /** 框架版本 / Root 来源：0=LSPosed 版本串，1=Root 来源串；null = 没读到 */
    private volatile String[] rootInfo = null;

    /** 后台读一次版本信息（module.prop + ksud），读完刷新界面 */
    void refreshRootInfo() {
        new Thread(new Runnable() {
            @Override public void run() {
                String cmd = "grep '^version=' /data/adb/modules/zygisk_lsposed/module.prop 2>/dev/null | head -1; "
                        + "echo @@; "
                        + "ksud -V 2>/dev/null | head -1";
                String out = runRoot(cmd, 6000L);
                if (out != null) {
                    String[] parts = out.split("@@");
                    if (parts.length >= 2) {
                        String lsp = parts[0].replace("version=", "").trim();
                        String ksu = parts[1].trim();
                        if (lsp.length() > 0 || ksu.length() > 0) {
                            rootInfo = new String[] { lsp, ksu };
                        }
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() { updateBanner(); }
                });
            }
        }, "xhook-rootinfo").start();
    }

    /** 通用的 root 执行：返回 stdout，失败返回 null（同步，必须在子线程调用） */
    String runRoot(String cmd, long timeoutMs) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[] { "su", "-c", cmd });
            final Process pp = p;
            final StringBuilder sb = new StringBuilder();
            Thread rd = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        java.io.InputStream is = pp.getInputStream();
                        byte[] b = new byte[2048];
                        int n;
                        while ((n = is.read(b)) > 0) sb.append(new String(b, 0, n, "UTF-8"));
                    } catch (Throwable t) { }
                }
            });
            rd.setDaemon(true);
            rd.start();
            long end = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < end) {
                try { p.exitValue(); break; } catch (Throwable nr) { try { Thread.sleep(50L); } catch (Throwable s) { break; } }
            }
            try {
                if (p.exitValue() != 0) { rootAvailable = false; return null; }
            } catch (Throwable t) { p.destroy(); rootAvailable = false; return null; }
            rootAvailable = true;
            return sb.toString();
        } catch (Throwable t) {
            if (p != null) { try { p.destroy(); } catch (Throwable t2) { } }
            return null;
        }
    }

    /* ───────── 以下只在有 Root 时可用 ───────── */



    /**
     * 检查 LSPosed 里记的 apk_path 是不是当前这个 APK，不对就用 root 修回去。
     * 每次重装 APK，/data/app/~~xxx== 那段哈希都会变，LSPosed 就找不到模块了 ——
     * 以前只能靠外部手工改库（拷出来、改、拷回去），现在模块自己修。
     * 返回一句人话给界面显示。
     */
    String rootSyncApkPath() {
        String real = getPackageCodePath();
        File dir = getFilesDir();
        try { dir.mkdirs(); } catch (Throwable t) { }
        String dst = new File(dir, "lspd_chk.db").getAbsolutePath();
        String uid = String.valueOf(android.os.Process.myUid());
        String pull = runRoot("cp /data/adb/lspd/config/modules_config.db " + dst
                + " && chown " + uid + ":" + uid + " " + dst + " && chmod 600 " + dst + " && echo OK", 6000L);
        if (pull == null || !pull.contains("OK")) return "路径检查失败：没有 Root 权限";

        String recorded = null;
        android.database.sqlite.SQLiteDatabase db = null;
        android.database.Cursor c = null;
        try {
            db = android.database.sqlite.SQLiteDatabase.openDatabase(dst, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery("select apk_path from modules where module_pkg_name=?", new String[] { getPackageName() });
            if (c.moveToFirst()) recorded = c.getString(0);
        } catch (Throwable t) {
            return "路径检查失败：配置库读不了";
        } finally {
            try { if (c != null) c.close(); } catch (Throwable t) { }
            try { if (db != null) db.close(); } catch (Throwable t) { }
        }
        if (recorded == null) return "LSPosed 里还没有本模块的记录，先在模块列表里加一下";
        if (recorded.equals(real)) return "路径正常";

        // 写回去
        String editDst = new File(dir, "lspd_chk_edit.db").getAbsolutePath();
        String pull2 = runRoot("cp /data/adb/lspd/config/modules_config.db " + editDst
                + " && chown " + uid + ":" + uid + " " + editDst + " && chmod 600 " + editDst + " && echo OK", 6000L);
        if (pull2 == null || !pull2.contains("OK")) return "路径失效，但改不了（没有 Root 权限）";
        try {
            android.database.sqlite.SQLiteDatabase w = android.database.sqlite.SQLiteDatabase
                    .openDatabase(editDst, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE);
            w.execSQL("update modules set apk_path=? where module_pkg_name=?",
                    new Object[] { real, getPackageName() });
            w.close();
        } catch (Throwable t) {
            return "路径失效，写回失败";
        }
        String back = runRoot("rm -f /data/adb/lspd/config/modules_config.db-wal /data/adb/lspd/config/modules_config.db-shm; "
                + "cp " + editDst + " /data/adb/lspd/config/modules_config.db"
                + " && chown 0:0 /data/adb/lspd/config/modules_config.db"
                + " && chmod 600 /data/adb/lspd/config/modules_config.db && echo OK", 6000L);
        return (back != null && back.contains("OK")) ? "路径已自动修好（重装后 LSPosed 又能找到本模块）" : "路径失效，写回失败";
    }

    /**
     * 体检：把 LSPosed 配置库里所有模块的 apk_path 验一遍。
     *   · 路径还在 → 正常
     *   · 路径没了但包还装着 → 自动修成当前真实路径（换版/重装后常见）
     *   · 路径没了包也没了 → 列为孤儿，顺带删掉
     * 这些事以前得靠外部手工逐个查改，现在一键完成。
     */
    String rootHealthCheck() {
        File dir = getFilesDir();
        try { dir.mkdirs(); } catch (Throwable t) { }
        String dst = new File(dir, "lspd_health.db").getAbsolutePath();
        String uid = String.valueOf(android.os.Process.myUid());
        String pull = runRoot("cp /data/adb/lspd/config/modules_config.db " + dst
                + " && chown " + uid + ":" + uid + " " + dst + " && chmod 600 " + dst + " && echo OK", 6000L);
        if (pull == null || !pull.contains("OK")) {
            String msg = "读不到 LSPosed 配置库（没有 Root 权限）";
            writeLog("health", msg);   // 失败也要留一条，否则排查时什么都看不到
            return msg;
        }

        // 先把所有模块和路径读出来
        java.util.List<String[]> rows = new java.util.ArrayList<String[]>();
        android.database.sqlite.SQLiteDatabase db = null;
        android.database.Cursor c = null;
        try {
            db = android.database.sqlite.SQLiteDatabase.openDatabase(dst, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery("select module_pkg_name, apk_path from modules", null);
            while (c.moveToNext()) rows.add(new String[] { c.getString(0), c.getString(1) });
        } catch (Throwable t) {
            return "读配置库失败";
        } finally {
            try { if (c != null) c.close(); } catch (Throwable t) { }
            try { if (db != null) db.close(); } catch (Throwable t) { }
        }
        if (rows.size() == 0) return "LSPosed 里还没有登记任何 hook 模块";

        // 一条命令把所有路径是否存在查完，省得 su 调 N 次
        StringBuilder probe = new StringBuilder();
        for (String[] r : rows) {
            String p = r[1] == null ? "" : r[1];
            probe.append("[ -f ").append(p).append(" ] && echo Y || echo N; ");
        }
        String res = runRoot(probe.toString(), 10000L);
        if (res == null) return "检查路径失败";
        String[] flags = res.trim().split("\n");

        int okN = 0, fixN = 0, dropN = 0;
        StringBuilder detail = new StringBuilder();
        java.util.List<String> toDrop = new java.util.ArrayList<String>();
        android.database.sqlite.SQLiteDatabase w = null;
        try {
            w = android.database.sqlite.SQLiteDatabase.openDatabase(dst, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE);
            for (int i = 0; i < rows.size(); i++) {
                String pkg = rows.get(i)[0];
                boolean exists = i < flags.length && "Y".equals(flags[i].trim());
                if (exists) { okN++; continue; }
                // 路径失效：问系统这个包还在不在
                String real = runRoot("/system/bin/pm path " + pkg + " 2>/dev/null | head -1", 4000L);
                String newPath = null;
                if (real != null) {
                    for (String ln : real.split("\n")) {
                        String s = ln.trim();
                        if (s.startsWith("package:")) { newPath = s.substring(8); break; }
                    }
                }
                if (newPath != null && newPath.length() > 0) {
                    try {
                        w.execSQL("update modules set apk_path=? where module_pkg_name=?", new Object[] { newPath, pkg });
                        fixN++;
                        detail.append("\n修好 ").append(pkg);
                    } catch (Throwable t) { }
                } else {
                    toDrop.add(pkg);
                    detail.append("\n孤儿 ").append(pkg);
                }
            }
            for (String pkg : toDrop) {
                try {
                    w.execSQL("delete from modules where module_pkg_name=?", new Object[] { pkg });
                    w.execSQL("delete from modules_state where module_pkg_name=?", new Object[] { pkg });
                    w.execSQL("delete from scope where module_pkg_name=?", new Object[] { pkg });
                    dropN++;
                } catch (Throwable t) { }
            }
        } catch (Throwable t) {
            return "写配置库失败";
        } finally {
            try { if (w != null) w.close(); } catch (Throwable t) { }
        }

        if (fixN > 0 || dropN > 0) {
            String back = runRoot("rm -f /data/adb/lspd/config/modules_config.db-wal /data/adb/lspd/config/modules_config.db-shm; "
                    + "cp " + dst + " /data/adb/lspd/config/modules_config.db"
                    + " && chown 0:0 /data/adb/lspd/config/modules_config.db"
                    + " && chmod 600 /data/adb/lspd/config/modules_config.db && echo OK", 6000L);
            if (back == null || !back.contains("OK")) return "改动没能写回（" + detail + "）";
        }

        StringBuilder out = new StringBuilder();
        out.append("LSPosed 登记了 ").append(rows.size()).append(" 个 hook 模块：");
        if (fixN == 0 && dropN == 0) {
            out.append("路径全部有效");
        } else {
            out.append(okN).append(" 个正常");
            if (fixN > 0) out.append("、").append(fixN).append(" 个已修好路径");
            if (dropN > 0) out.append("、").append(dropN).append(" 个孤儿已清掉");
        }
        if (detail.length() > 0) out.append(detail);
        return out.toString();
    }

    /** 读 LSPosed 里所有已装模块和它们的启用状态，每行一条 */
    String rootModuleList() {
        File dir = getFilesDir();
        try { dir.mkdirs(); } catch (Throwable t) { }
        String dst = new File(dir, "lspd_mods.db").getAbsolutePath();
        String uid = String.valueOf(android.os.Process.myUid());
        String pull = runRoot("cp /data/adb/lspd/config/modules_config.db " + dst
                + " && chown " + uid + ":" + uid + " " + dst + " && chmod 600 " + dst + " && echo OK", 6000L);
        if (pull == null || !pull.contains("OK")) return null;
        android.database.sqlite.SQLiteDatabase db = null;
        android.database.Cursor c = null;
        StringBuilder sb = new StringBuilder();
        try {
            db = android.database.sqlite.SQLiteDatabase.openDatabase(dst, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery("select m.module_pkg_name, ifnull(s.enabled, -1) from modules m "
                    + "left join modules_state s on s.module_pkg_name = m.module_pkg_name", null);
            while (c.moveToNext()) {
                String p = c.getString(0);
                int en = c.getInt(1);
                if (sb.length() > 0) sb.append("\n");
                sb.append(en == 1 ? "● " : "○ ").append(p);
                if (p != null && p.equals(getPackageName())) sb.append("（本模块）");
            }
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (c != null) c.close(); } catch (Throwable t) { }
            try { if (db != null) db.close(); } catch (Throwable t) { }
        }
        return sb.toString();
    }

    /** 读本模块当前的作用域包名，逗号分隔；读不到返回空串 */
    String rootScopes() {
        File dir = getFilesDir();
        try { dir.mkdirs(); } catch (Throwable t) { }
        String dst = new File(dir, "lspd_scope.db").getAbsolutePath();
        String uid = String.valueOf(android.os.Process.myUid());
        String pull = runRoot("cp /data/adb/lspd/config/modules_config.db " + dst
                + " && chown " + uid + ":" + uid + " " + dst + " && chmod 600 " + dst + " && echo OK", 6000L);
        if (pull == null || !pull.contains("OK")) return "";
        android.database.sqlite.SQLiteDatabase db = null;
        android.database.Cursor c = null;
        StringBuilder sb = new StringBuilder();
        try {
            db = android.database.sqlite.SQLiteDatabase.openDatabase(dst, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery("select app_pkg_name from scope where module_pkg_name=?", new String[] { getPackageName() });
            while (c.moveToNext()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(c.getString(0));
            }
        } catch (Throwable t) {
            return "";
        } finally {
            try { if (c != null) c.close(); } catch (Throwable t) { }
            try { if (db != null) db.close(); } catch (Throwable t) { }
        }
        return sb.toString();
    }

    /**
     * 用 root 改作用域：把某个包加进 / 移出本模块的作用域。
     * 以前要开 LSPosed 管理器点半天，现在在这直接改配置库。
     */
    boolean rootEditScope(String pkg, boolean add) {
        if (pkg == null) return false;
        pkg = pkg.trim();
        if (pkg.length() == 0 || pkg.indexOf(' ') >= 0) return false;
        File dir = getFilesDir();
        try { dir.mkdirs(); } catch (Throwable t) { }
        String dst = new File(dir, "lspd_scope_edit.db").getAbsolutePath();
        String uid = String.valueOf(android.os.Process.myUid());
        String pull = runRoot("cp /data/adb/lspd/config/modules_config.db " + dst
                + " && chown " + uid + ":" + uid + " " + dst + " && chmod 600 " + dst + " && echo OK", 6000L);
        if (pull == null || !pull.contains("OK")) return false;
        try {
            android.database.sqlite.SQLiteDatabase w = android.database.sqlite.SQLiteDatabase
                    .openDatabase(dst, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE);
            if (add) {
                w.execSQL("insert or ignore into scope (module_pkg_name, app_pkg_name, user_id) values (?, ?, 0)",
                        new Object[] { getPackageName(), pkg });
            } else {
                w.execSQL("delete from scope where module_pkg_name=? and app_pkg_name=?",
                        new Object[] { getPackageName(), pkg });
            }
            w.close();
        } catch (Throwable t) { return false; }
        String back = runRoot("rm -f /data/adb/lspd/config/modules_config.db-wal /data/adb/lspd/config/modules_config.db-shm; "
                + "cp " + dst + " /data/adb/lspd/config/modules_config.db"
                + " && chown 0:0 /data/adb/lspd/config/modules_config.db"
                + " && chmod 600 /data/adb/lspd/config/modules_config.db && echo OK", 6000L);
        if (back == null || !back.contains("OK")) return false;
        lastScopes = rootScopes();
        return true;
    }

    /** 用 root 强制停掉作用域里的 App —— 改完作用域/hook 配置后要重启它们才生效 */
    int rootRestartScoped() {
        String scopes = rootScopes();
        if (scopes.length() == 0) return 0;
        int n = 0;
        for (String raw : scopes.split(",")) {
            String p = raw.trim();
            if (p.length() == 0) continue;
            String r = runRoot("am force-stop " + p + " && echo OK", 4000L);
            if (r != null && r.contains("OK")) n++;
        }
        return n;
    }

    /** 最近一次 root 读到的启用状态；null = 读不到 */
    private volatile Boolean rootVerdict = null;

    /** 后台读一次配置，读完刷新界面 —— su 调用会阻塞，绝不能在主线程做 */
    void refreshRootVerdict() {
        new Thread(new Runnable() {
            @Override public void run() {
                Boolean v = rootEnabled();
                rootVerdict = v;
                runOnUiThread(new Runnable() {
                    @Override public void run() { updateBanner(); }
                });
            }
        }, "xhook-rootread").start();
    }

    /**
     * 一次性 root 自检：只在 root-probe.txt 不存在时跑，结果写进去。
     * 目的很明确 —— 判断本 App 进程到底能不能调到 su（SukiSU 的 su 是 magic mount，
     * 未必对普通 App 可见）。有了这个结论才知道该不该用 root 增强能力。
     */
    void probeRootOnce() {
        File logDir = new File(getFilesDir(), "logs");
        if (!logDir.exists()) logDir.mkdirs();
        final File out = new File(logDir, "root-probe.txt");
        new Thread(new Runnable() {
            @Override public void run() {
                String result;
                Process p = null;
                try {
                    p = Runtime.getRuntime().exec(new String[] { "su", "-c", "id" });
                    final Process pp = p;
                    final StringBuilder sb = new StringBuilder();
                    Thread rd = new Thread(new Runnable() {
                        @Override public void run() {
                            try {
                                java.io.InputStream is = pp.getInputStream();
                                byte[] b = new byte[2048];
                                int n;
                                while ((n = is.read(b)) > 0) sb.append(new String(b, 0, n, "UTF-8"));
                            } catch (Throwable t) { }
                        }
                    });
                    rd.setDaemon(true);
                    rd.start();
                    long endTime = System.currentTimeMillis() + 6000L;
                    while (System.currentTimeMillis() < endTime) {
                        try { p.exitValue(); break; } catch (Throwable notYet) {
                            try { Thread.sleep(60L); } catch (Throwable s) { break; }
                        }
                    }
                    String txt = "exit=? out=" + sb.toString().trim().replace('\n', ' ');
                    try { txt = "exit=" + p.exitValue() + " out=" + sb.toString().trim().replace('\n', ' '); }
                    catch (Throwable t) { p.destroy(); txt = txt + " (超时未退出)"; }
                    // 关键：退出码 0 不代表提权成功，必须看到 uid=0
                    try {
                        rootVerified = sb.toString().contains("uid=0");
                        if (rootVerified) rootAvailable = true;   // 两个标记保持一致，界面才不会自相矛盾
                    } catch (Throwable t) { }
                    result = txt;
                } catch (Throwable t) {
                    if (p != null) { try { p.destroy(); } catch (Throwable t2) { } }
                    result = "exec-failed:" + t.getClass().getSimpleName() + ":" + t.getMessage();
                }
                try { writeLog("rootprobe", result); } catch (Throwable t) { }
                try {
                    FileOutputStream fo = new FileOutputStream(out, false);
                    fo.write((System.currentTimeMillis() + "\n" + result).getBytes("UTF-8"));
                    fo.close();
                } catch (Throwable t) { }
            }
        }, "xhook-rootprobe").start();
    }

    /** 诊断日志存 prefs：不落 /sdcard，也不依赖 App 私有目录能否建子目录 */
    void writeLog(String tag, String msg) {
        try {
            getSharedPreferences(XhookProvider.PREFS, 0).edit()
                    .putString("log:" + tag, System.currentTimeMillis() + "\n" + msg).commit();
        } catch (Throwable t) { }
    }

    /** 上一次 root 调用失败的原因，给用户看 */
    static volatile String lastRootError = null;

    boolean rootBroadcast() {
        lastRootError = null;
        // 用绝对路径：su 里头的 PATH 未必含 /system/bin，光写 am 会「找不到命令」
        String cmd = "/system/bin/am broadcast -a android.telephony.action.SECRET_CODE -d android_secret_code://5776733 android";
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[] { "su", "-c", cmd });
            final Process pp = p;
            final StringBuilder sb = new StringBuilder();
            Thread reader = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        java.io.InputStream is = pp.getInputStream();
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = is.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
                    } catch (Throwable t) { }
                }
            });
            reader.setDaemon(true);
            reader.start();
            long deadline = System.currentTimeMillis() + 8000L;
            while (System.currentTimeMillis() < deadline) {
                try { p.exitValue(); break; } catch (Throwable stillRunning) {
                    try { Thread.sleep(60L); } catch (Throwable s) { break; }
                }
            }
            int code;
            try { code = p.exitValue(); } catch (Throwable t) { p.destroy(); lastRootError = "su 没响应（8 秒超时）"; return false; }
            try { Thread.sleep(200L); } catch (Throwable t) { }
            String out = sb.toString().trim();
            if (out.contains("SecurityException") || out.contains("Permission Denial")) {
                lastRootError = "广播被拒：" + out.substring(0, Math.min(120, out.length()));
                return false;
            }
            if (code != 0) {
                lastRootError = "su 退出码 " + code + (out.length() > 0 ? "：" + out.substring(0, Math.min(120, out.length())) : "");
                writeLog("am-fail", "code=" + code + " out=" + out);
                return false;
            }
            writeLog("am-ok", out);
            return true;
        } catch (Throwable t) {
            if (p != null) { try { p.destroy(); } catch (Throwable t2) { } }
            lastRootError = "调不起 su：" + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : " " + t.getMessage());
            return false;
        }
    }

    /** root 走不通时的退路：管理器 → 组件 → category → 拨号盘预填 → 提示 */
    void openLsposedFallback() {
        String[] managers = { "com.sukisu.nova", "me.weishu.kernelsu", "com.topjohnwu.magisk" };
        for (String pkg : managers) {
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    String why = lastRootError == null ? "没有 root 权限" : lastRootError;
                    toast("root 不可用（" + why + "），已打开 " + pkg);
                    return;
                }
            } catch (Throwable t) { /* 试下一个 */ }
        }
        try {
            Intent i = new Intent();
            i.setClassName("com.android.shell", "org.lsposed.manager.ui.activity.MainActivity");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        } catch (Throwable t) { /* 退下一种 */ }
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory("org.lsposed.manager.LAUNCH_MANAGER");
            i.setPackage("com.android.shell");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        } catch (Throwable t) { /* 权限不够，继续 */ }
        try {
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:*%23*%235776733%23*%23"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            toast("已把 *#*#5776733#*#* 填进拨号盘，按拨号键就能进 LSPosed");
            return;
        } catch (Throwable t) { /* 连拨号盘都没有，继续 */ }
        toast("LSPosed 只能自己开：拨号盘输 *#*#5776733*#*#*");
    }

    /** 定时器：只刷状态条，不重设开关，免得打扰正在操作的控件 */
    /**
     * Root 权限监听器：页面在前台时每 15 秒探一次 su。
     * 用户在 SukiSU 里撤掉授权 / 重新授予时，这里会立刻反映出来，
     * 不用等下一次点按钮才发现「怎么没权限了」。
     */
    private final Runnable rootWatch = new Runnable() {
        @Override public void run() {
            new Thread(new Runnable() {
                @Override public void run() {
                    // 只跑 true，不产生副作用；能拿到退出码 0 就是有 root
                    boolean ok = runRoot("true", 3000L) != null;
                    rootKnown = true;
                    final boolean changed = (ok != rootAvailable);
                    if (changed) {
                        rootAvailable = ok;
                        if (!ok) { rootVerdict = null; rootInfo = null; }
                    }
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (changed) {
                                updateBanner();
                                toast(ok ? "Root 权限已授予" : "Root 权限被撤销——部分功能不可用");
                                if (ok) { refreshRootVerdict(); refreshRootInfo(); }
                            }
                        }
                    });
                }
            }, "xhook-rootwatch").start();
            // 有 root 就勤快点（15 秒），没有就放慢到 60 秒 ——
            // 否则「每次询问」模式下会不停弹授权框
            try { getWindow().getDecorView().postDelayed(this, rootVerified ? 15000L : 60000L); } catch (Throwable t) { }
        }
    };

    private int probeTick = 0;

    private final Runnable bannerTick = new Runnable() {
        @Override public void run() {
            try {
                // 每 12 次（约 60 秒）重新探一次：模块被关掉后，这里会自己翻成未连接
                if (probeTick++ % 12 == 0) runProbe();
                updateBanner();
            } catch (Throwable t) {}
            try { getWindow().getDecorView().postDelayed(this, 8000L); } catch (Throwable t) {}
        }
    };

    /**
     * 状态条只看心跳：三分钟内还有进程在刷新，才算真的连上了。
     * 以前不管有没有进程在跑都写「模块已激活」，所以在 LSPosed 里关掉模块之后它也不变。
     */
    void updateBanner() {
        if (bannerHead == null) return;
        boolean off = new File(OFF).exists();
        String alive = aliveList();
        // 判据优先顺序：root 读到的配置 > 本次探针 > 上次缓存 > 进程内即时判断。
        // root 那一路最准：配置里 enabled=0 就是关掉了，跟进程死活无关。
        boolean probed = lastProbeAt > 0L;
        Boolean rootV = rootVerdict;
        boolean lspOk = rootV != null
                ? rootV.booleanValue()
                : (probed ? lastProbeInjected : (probeCached() || lspActive()));
        boolean targetOk = alive.length() > 0;
        boolean connected = lspOk;
        // Root 只是增强：没它 hook 照常跑，少了"直读配置"和"一键开 LSPosed"而已
        boolean hasRoot = rootVerified;

        // 激活＝绿底大卡，未激活＝红底，一眼能看出来
        int bgCol = connected ? (darkMode ? 0xff16301A : 0xffE7F6E9) : (darkMode ? 0xff3A1D1D : 0xffFDECEC);
        int fgCol = connected ? (darkMode ? 0xff7EE08C : 0xff1B5E20) : (darkMode ? 0xffF08A82 : 0xffB3261E);
        int subCol = connected ? (darkMode ? 0xffB9D9BE : 0xff33603A) : (darkMode ? 0xffD8B0B0 : 0xff7A2E28);
        if (banner != null) banner.setBackgroundDrawable(box(bgCol, 0, 14));
        bannerHead.setText(connected ? "模块已激活" : "模块未激活");
        bannerHead.setTextColor(fgCol);
        if (bannerIcon != null) {
            bannerIcon.setText(connected ? "✓" : "✕");
            bannerIcon.setTextColor(fgCol);
        }
        bannerBody.setTextColor(subCol);

        StringBuilder sb2 = new StringBuilder();
        sb2.append("版本：").append(versionName());
        String[] info = rootInfo;
        if (info != null) {
            if (info.length > 0 && info[0].length() > 0) sb2.append("\n框架版本：LSPosed ").append(info[0]);
            if (info.length > 1 && info[1].length() > 0) sb2.append("\nRoot 来源：KernelSU ").append(info[1]);
        } else {
            sb2.append("\n框架版本：读取中…");
        }
        if (connected) {
            sb2.append("\n✓ LSPosed 已启用本模块（hook 会正常跑）");
            if (hasRoot) sb2.append("\n✓ Root 权限已授予");
            else sb2.append("\n⚠ 没有 Root 权限：只少了「直读框架状态」和「一键打开 LSPosed」，hook 不受影响");
            if (targetOk) sb2.append("\n已注入：").append(alive);
            else sb2.append("\n作用域的 App 还没启动，启动后即生效");
        } else {
            // LSPosed 是命脉：没有它，一行 hook 都不会跑
            if (rootV != null) {
                sb2.append("\n✕ 缺少 LSPosed 授权 —— 模块不会生效，所有 hook 都不工作");
                sb2.append("\n   去模块列表开启本模块，再把本 App 划掉重开");
            } else {
                sb2.append("\n· LSPosed 状态没读到，点这里打开它确认");
            }
            // Root 只是增强，明说丢了什么、没丢什么
            if (hasRoot) sb2.append("\n✓ Root 权限已授予");
            else sb2.append("\n⚠ 没有 Root 权限：只少了「直读框架状态」和「一键打开 LSPosed」，不影响 hook");
        }
        sb2.append("\n注入 ").append(off ? "关" : "开")
           .append(" · 隐身 ").append(stealthOn() ? "开" : "关")
           .append(" · 存储 ").append(hasAllFiles() ? "有" : "未授予");
        bannerBody.setText(sb2.toString());
        refreshRootPage();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
        // 每次回到页面都同步一次脚本：外部改过 scripts/*.off 也能跟上
        try { syncScriptsToPrefs(); } catch (Throwable t) { }
        // 一进来就探一次：回到页面时状态要是旧的，用户会以为模块还在
        runProbe();
        try { probeRootOnce(); } catch (Throwable t) { }
        try { refreshRootVerdict(); } catch (Throwable t) { }
        try { refreshRootInfo(); } catch (Throwable t) { }
        // 重装后 apk_path 会失效，进来就自动校正一次（没 root 就只是读一下失败，无副作用）
        new Thread(new Runnable() {
            @Override public void run() {
                // 内建自检：每次打开都全库体检一遍 —— 修失效路径、清孤儿记录。
                // 别人装了本模块也一样受益，不需要额外装什么工具。
                final String report = rootHealthCheck();
                rootKnown = true;
                writeLog("health", report);
                final String sc = restoreScopesIfLost();
                lastScopes = sc;
                // 有 root 就顺手把 Zygisk 模块状态实时读一遍（替代 /sdcard 快照）
                try { refreshModsViaRoot(); } catch (Throwable t) { }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (rootPathHint != null) rootPathHint.setText(report);
                        if (healthHint != null) healthHint.setText(report);
                        if (bannerBody != null && (report.contains("修复") || report.contains("孤儿"))) {
                            toast("配置库自检：" + report);
                        }
                        lastScopeRendered = null;
                        refreshRootPage();
                    }
                });
            }
        }, "xhook-autofix").start();
        getWindow().getDecorView().postDelayed(bannerTick, 5000L);
        getWindow().getDecorView().postDelayed(rootWatch, 1500L);
    }

    @Override protected void onPause() {
        super.onPause();
        getWindow().getDecorView().removeCallbacks(bannerTick);
        getWindow().getDecorView().removeCallbacks(rootWatch);
    }

    void refresh() {
        boolean off = new File(OFF).exists();
        File cmd = new File(CMD);
        if (sw != null) {
            suppressSw = true;
            sw.setChecked(!off);
            suppressSw = false;
        }
        if (statusLabel != null) {
            String cmdState = "无";
            try {
                String cs = getSharedPreferences(XhookProvider.PREFS, 0).getString("cmd", null);
                if (cs != null && cs.length() > 0) cmdState = Math.max(1, cs.length() / 1024) + "KB";
            } catch (Throwable t) { }
            statusLabel.setText(off ? "已关闭 · 通道停，能力栏的 hook 照常" : "已开启 · 当前指令 " + cmdState);
            statusLabel.setTextColor(SUB);
            if (dotDot != null) dotDot.setTextColor(off ? BAD : OK);
        }
        updateBanner();
        if (foot != null) {
            StringBuilder b = new StringBuilder();
            b.append("控制目录   ").append(CTRL).append((char) 10);
            String[] cf = new File(CTRL).list();
            if (cf == null || cf.length == 0) b.append("  （空）").append((char) 10);
            else for (String n : cf) b.append("  ").append(n).append((char) 10);
            b.append((char) 10).append("用户配置   ").append(USERDIR).append((char) 10);
            String[] uf = new File(USERDIR).list();
            if (uf == null || uf.length == 0) b.append("  （空）").append((char) 10);
            else for (String n : uf) b.append("  ").append(n).append((char) 10);
            b.append((char) 10).append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));
            foot.setText(b.toString());
        }
        if (abilDots != null) {
            String[] ks = { "log", "args", "return", "js", "cookie", "call" };
            for (int i = 0; i < ks.length && i < abilDots.length; i++) {
                if (abilDots[i] != null) abilDots[i].setTextColor(new File(ABIL, ks[i] + ".off").exists() ? BAD : OK);
            }
        }
        rebuildScripts();
        publishStatus();
    }

    static String lastStatusCore = null;

    /** 把能力清单导出到控制目录：外部（人或 agent）读这一个文件就知道模块能做什么。 */
    /** 内容变了才写，避免每次启动都重写（也修掉「已存在就跳过」导致版本不更新）。 */
    void writeIfChanged(File f, String content) {
        if (content == null || content.length() == 0) return;
        try {
            byte[] b = content.getBytes("UTF-8");
            if (f.exists() && f.length() == b.length) {
                byte[] old = new byte[b.length];
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                int off = 0;
                while (off < old.length) {
                    int r = in.read(old, off, old.length - off);
                    if (r <= 0) break;
                    off += r;
                }
                in.close();
                if (java.util.Arrays.equals(old, b)) return;
            }
            FileOutputStream fo = new FileOutputStream(f, false);
            fo.write(b);
            fo.close();
            f.setReadable(true, false);
        } catch (Throwable t) {}
    }

    /** 递归导出 assets 下的整棵目录树（用于内嵌的 DSH 插件）。 */
    void exportAssetTree(String assetDir, File outDir) {
        String[] items = null;
        try { items = getAssets().list(assetDir); } catch (Throwable t) { return; }
        if (items == null || items.length == 0) return;
        if (!outDir.exists()) outDir.mkdirs();
        for (String n : items) {
            String path = assetDir + "/" + n;
            String[] sub = null;
            try { sub = getAssets().list(path); } catch (Throwable t2) {}
            if (sub != null && sub.length > 0) {
                exportAssetTree(path, new File(outDir, n));
            } else {
                writeIfChanged(new File(outDir, n), readAsset(path));
            }
        }
    }

    /** 把能力清单 + 全部配方 + 给 DSH 的指南导出到控制目录，外部（人或 agent）读这些就能操作。 */
    void exportCapabilities() {
        try {
            File d = new File(CTRL);
            if (!d.exists()) d.mkdirs();
            // 导出的清单要把版本号换成当前版本，别让 assets 里那份写死的数字一直过期
            String capJson = readAsset("capabilities.json");
            try {
                org.json.JSONObject capObj = new org.json.JSONObject(capJson);
                capObj.put("version", versionName());
                capJson = capObj.toString(2);
            } catch (Throwable t) { }
            writeIfChanged(new File(d, "capabilities.json"), capJson);
            writeIfChanged(new File(d, "DSH-GUIDE.md"), readAsset("DSH-GUIDE.md"));
            writeIfChanged(new File(d, "写脚本指南.md"), readAsset("写脚本指南.md"));
            exportAssetTree("dsh-plugin", new File(d, "dsh-plugin"));
            writeIfChanged(new File(d, "xhook-clean.sh"), readAsset("xhook-clean.sh"));
            String[] rs = null;
            try { rs = getAssets().list("recipes"); } catch (Throwable t0) {}
            if (rs != null && rs.length > 0) {
                File rd = new File(d, "recipes");
                if (!rd.exists()) rd.mkdirs();
                for (String n : rs) {
                    if (!n.endsWith(".json")) continue;
                    writeIfChanged(new File(rd, n), readAsset("recipes/" + n));
                }
            }
        } catch (Throwable t) {}
    }

    void publishStatus() {
        exportCapabilities();
        try {
            File d = new File(CTRL);
            if (!d.exists()) d.mkdirs();
            String ver = "?";
            try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Throwable t) {}
            boolean inj = !new File(OFF).exists();
            boolean hasCmd = new File(CMD).exists();
            String core = ver + "|" + inj + "|" + hasCmd;
            if (core.equals(lastStatusCore)) return;
            lastStatusCore = core;
            StringBuilder s = new StringBuilder("{");
            s.append(Q).append("name").append(Q).append(CL).append(Q).append("DSHxpandh").append(Q).append(CO);
            s.append(Q).append("version").append(Q).append(CL).append(Q).append(ver).append(Q).append(CO);
            s.append(Q).append("inject").append(Q).append(CL).append(inj ? "true" : "false").append(CO);
            s.append(Q).append("cmd").append(Q).append(CL).append(hasCmd ? "true" : "false").append(CO);
            s.append(Q).append("ts").append(Q).append(CL).append(System.currentTimeMillis());
            s.append("}");
            FileOutputStream fo = new FileOutputStream(new File(d, "status.json"), false);
            fo.write(s.toString().getBytes("UTF-8"));
            fo.close();
        } catch (Throwable t) {}
    }

    void askStorage() {
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            java.lang.reflect.Method m = Environment.class.getMethod("isExternalStorageManager");
            if (Boolean.TRUE.equals(m.invoke(null))) return;
        } catch (Throwable t) { return; }
        try {
            Intent i = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {}
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
