package io.github.hehe15855.dshxpandh;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

import io.github.hehe15855.dshxpandh.compat.XpBridge;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/**
 * 新版 libxposed 入口（API 102）。
 *
 * 为什么要有这一层：现有 hook 逻辑写在 HookEntry.handleLoadPackage(XC_LoadPackage.LoadPackageParam) 里，
 * 直接改成新写法要重写 26 个匿名回调。这里造一个「旧参数形态」的代理对象传进去，
 * 主体逻辑一行不动 —— 迁移风险压到最低。
 *
 * 入口注册：META-INF/xposed/java_init.list 写本类全名。
 */
public class ModernEntry extends XposedModule {

    private static final String TAG = "core";
    /** onModuleLoaded 时记下的进程名：PackageReadyParam 没有这个 getter。 */
    private String processName = "";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        // 框架实例先交给兼容层，后面所有 hook 都走它
        XpBridge.install(this);
        HookEntry.LOADED = true;
        processName = param.getProcessName();
        log(android.util.Log.INFO, "DSHxpandh", "modern entry loaded: " + processName);
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        // 本模块不碰 system_server
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        try {
            if (param == null) return;
            String pkg = param.getPackageName();
            ClassLoader cl = param.getClassLoader();
            if (pkg == null || cl == null) return;

            // 直接交给主体逻辑：不再经过旧参数对象（新版框架下 de.robv.* 不存在）
            new HookEntry().dispatch(pkg, cl, processName);
        } catch (Throwable t) {
            log(android.util.Log.ERROR, "DSHxpandh", "onPackageReady failed: " + t);
        }
    }
}