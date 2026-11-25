package io.github.lyx2839272_coder.dshxpandh;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 应用选择器：默认只列用户装的 App，右上角菜单可以切换显示系统应用。
 * 顶部有搜索框，按应用名或包名过滤。
 */
public class AppPickerDialog extends Dialog {

    public interface OnPick { void onPick(String pkg, String label); }

    private final OnPick cb;
    private final List<ApplicationInfo> all = new ArrayList<ApplicationInfo>();
    private final List<ApplicationInfo> shown = new ArrayList<ApplicationInfo>();
    private final List<String> labels = new ArrayList<String>();
    private boolean showSystem = false;
    private String query = "";
    private AppAdapter adapter;
    private TextView title;
    // 缓存：loadLabel / loadIcon 都要查 PackageManager，反复调就慢
    private final java.util.HashMap<String, String> labelCache = new java.util.HashMap<String, String>();
    private final java.util.HashMap<String, Drawable> iconCache = new java.util.HashMap<String, Drawable>();

    public AppPickerDialog(Context c, OnPick cb) {
        // 用 Dialog 系的主题：它本来就在状态栏下方，不会把时间和电量盖掉。
        // 之前用 NoActionBar（Activity 主题）+ 全屏布局，连状态栏一起遮了。
        super(c, android.R.style.Theme_DeviceDefault_Light_Dialog);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        this.cb = cb;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        // 不在主线程读应用列表 —— 119 个应用每个都要 loadLabel，同步做会卡好几秒

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // 顶部：返回 + 标题 + 菜单
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(14), dp(12), dp(10));

        TextView back = new TextView(getContext());
        back.setText("←");
        back.setTextSize(22);
        back.setTextColor(0xFF222222);
        back.setPadding(0, 0, dp(12), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismiss(); }
        });
        bar.addView(back);

        title = new TextView(getContext());
        title.setText("选择应用");
        title.setTextSize(18);
        title.setTextColor(0xFF222222);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView menu = new TextView(getContext());
        menu.setText("⋮");
        menu.setTextSize(22);
        menu.setTextColor(0xFF222222);
        menu.setPadding(dp(8), 0, 0, 0);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(v); }
        });
        bar.addView(menu);
        root.addView(bar);

        // 搜索框
        EditText search = new EditText(getContext());
        search.setHint("搜索应用或包名");
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setBackgroundColor(0xFFF2F2F2);
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(dp(12), 0, dp(12), dp(8));
        root.addView(search, sp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                query = e.toString().trim().toLowerCase();
                rebuildList();
            }
        });

        ListView lv = new ListView(getContext());
        lv.setDividerHeight(0);
        adapter = new AppAdapter();
        lv.setAdapter(adapter);
        root.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));

        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                ApplicationInfo ai = shown.get(pos);
                cb.onPick(ai.packageName, String.valueOf(labels.get(pos)));
                dismiss();
            }
        });

        setContentView(root);
        if (getWindow() != null) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            // 关键：整体下移状态栏高度。不然窗口从屏幕最顶开始，状态栏被盖住，
            // 底下的设置页标题会从半透明状态栏那里透出来，看起来像两行叠在一起。
            lp.y = statusBarHeight();
            getWindow().setAttributes(lp);
            getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.WHITE));
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            // 白底 → 状态栏图标要变深色，不然时间和电量在白背景上看不见
            try {
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    getWindow().getDecorView().setSystemUiVisibility(
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                }
            } catch (Throwable t) { }
        }
        // 先把空列表显示出来，后台再填数据
        rebuildList();
        new Thread(new Runnable() {
            @Override public void run() {
                loadApps();
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() { rebuildList(); }
                });
            }
        }).start();
    }

    private void loadApps() {
        try {
            all.clear();
            List<ApplicationInfo> list = getContext().getPackageManager().getInstalledApplications(0);
            all.addAll(list);
            // 先把 label 全算一遍，排序时就不用在 comparator 里反复查（那是 O(n log n) 次调用）
            for (ApplicationInfo ai : all) labelOf(ai);
            Collections.sort(all, new Comparator<ApplicationInfo>() {
                @Override public int compare(ApplicationInfo a, ApplicationInfo b) {
                    return labelOf(a).compareToIgnoreCase(labelOf(b));
                }
            });
        } catch (Throwable t) { }
    }

    private String labelOf(ApplicationInfo ai) {
        String c = labelCache.get(ai.packageName);
        if (c != null) return c;
        String v;
        try { v = String.valueOf(ai.loadLabel(getContext().getPackageManager())); } catch (Throwable t) { v = ai.packageName; }
        labelCache.put(ai.packageName, v);
        return v;
    }

    private void rebuildList() {
        shown.clear();
        labels.clear();
        for (ApplicationInfo ai : all) {
            boolean sys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (sys && !showSystem) continue;
            String label = labelOf(ai);
            if (query.length() > 0
                    && !label.toLowerCase().contains(query)
                    && !ai.packageName.toLowerCase().contains(query)) continue;
            shown.add(ai);
            labels.add(label);
        }
        if (title != null) title.setText("选择应用（" + shown.size() + "）");
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(getContext(), anchor);
        pm.getMenu().add(showSystem ? "只显示用户应用" : "显示系统应用").setOnMenuItemClickListener(
            new android.view.MenuItem.OnMenuItemClickListener() {
                @Override public boolean onMenuItemClick(android.view.MenuItem item) {
                    showSystem = !showSystem;
                    rebuildList();
                    return true;
                }
            });
        pm.show();
    }

    private int statusBarHeight() {
        try {
            int id = getContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) return getContext().getResources().getDimensionPixelSize(id);
        } catch (Throwable t) { }
        return dp(24);
    }

    private int navBarHeight() {
        try {
            int id = getContext().getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (id > 0) return getContext().getResources().getDimensionPixelSize(id);
        } catch (Throwable t) { }
        return dp(16);
    }

    private int dp(int v) {
        return (int) (v * getContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public View getView(int pos, View cv, ViewGroup parent) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));

            ApplicationInfo ai = shown.get(pos);
            android.widget.ImageView iv = new android.widget.ImageView(getContext());
            Drawable d = iconCache.get(ai.packageName);
            if (d == null) {
                try { d = ai.loadIcon(getContext().getPackageManager()); } catch (Throwable t) { }
                if (d != null) iconCache.put(ai.packageName, d);
            }
            if (d != null) iv.setImageDrawable(d);
            row.addView(iv, new LinearLayout.LayoutParams(dp(40), dp(40)));

            LinearLayout col = new LinearLayout(getContext());
            col.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(getContext());
            name.setText(String.valueOf(labels.get(pos)));
            name.setTextSize(15);
            name.setTextColor(0xFF222222);
            col.addView(name);
            TextView pkg = new TextView(getContext());
            pkg.setText(ai.packageName);
            pkg.setTextSize(10);
            pkg.setTextColor(0xFF999999);
            col.addView(pkg);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
            cp.setMargins(dp(14), 0, 0, 0);
            row.addView(col, cp);

            TextView arrow = new TextView(getContext());
            arrow.setText("›");
            arrow.setTextSize(20);
            arrow.setTextColor(0xFFCCCCCC);
            row.addView(arrow);

            return row;
        }
    }
}