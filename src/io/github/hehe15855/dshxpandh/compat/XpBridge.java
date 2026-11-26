package io.github.hehe15855.dshxpandh.compat;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedInterface.Hooker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 兼容层：把 libxposed 新版 API 包成旧版 XposedBridge/XC_MethodHook 的用法。
 *
 * 为什么不直接改成新写法：那意味着重写 26 个匿名回调 + 32 处 hookAllMethods，
 * 而 hook 最容易出的故障是「编译通过但静默不生效」，一次全改完很难定位。
 * 这里只做一层薄适配，旧调用几乎不用动。
 *
 * 对应关系：
 *   XposedBridge.hookAllMethods(cl, name, hook)  -> hookAllMethods()
 *   XposedBridge.hookAllConstructors(cl, hook)   -> hookAllConstructors()
 *   XposedBridge.hookMethod(method, hook)        -> hookMethod()
 *   XposedBridge.log(msg)                        -> log()
 */
public final class XpBridge {

    private static XposedInterface api;
    private static final List<HookHandle> HANDLES = new ArrayList<>();

    private XpBridge() {}

    /** 模块入口拿到框架实例后调一次。 */
    public static void install(XposedInterface i) { api = i; }

    public static boolean ready() { return api != null; }

    public static void log(String msg) {
        if (api == null) return;
        try { api.log(android.util.Log.INFO, "DSHxpandh", msg); } catch (Throwable ignored) {}
    }

    public static void log(Throwable t) {
        if (api == null || t == null) return;
        try { api.log(android.util.Log.ERROR, "DSHxpandh", String.valueOf(t), t); } catch (Throwable ignored) {}
    }

    /** 等价的 findClassIfExists：找不到返回 null，不抛。 */
    public static Class<?> findClass(String name, ClassLoader cl) {
        try { return Class.forName(name, false, cl); } catch (Throwable t) { return null; }
    }

    /**
     * 按名字 hook 全部同名方法，并沿继承链向上找 —— 旧版 XposedBridge.hookAllMethods 就是这个行为。
     * 漏掉继承链会少挂方法（比如 ContextWrapper.getPackageManager），所以必须要走上去。
     */
    public static List<HookHandle> hookAllMethods(Class<?> cl, String name, XpCallback cb) {
        List<HookHandle> out = new ArrayList<>();
        if (cl == null || api == null) return out;
        // 去重：父类里 override 过的同一个方法只挂一次
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Class<?> c = cl; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                StringBuilder sig = new StringBuilder(m.getName()).append("(");
                for (Class<?> pt : m.getParameterTypes()) sig.append(pt.getName()).append(",");
                sig.append(")");
                if (!seen.add(sig.toString())) continue;
                HookHandle h = hookOne(m, cb);
                if (h != null) out.add(h);
            }
        }
        return out;
    }

    /** hook 连父类一起找 —— 旧版 hookAllMethods 会走继承链，这里补上。 */
    public static List<HookHandle> hookAllMethodsDeep(Class<?> cl, String name, XpCallback cb) {
        List<HookHandle> out = new ArrayList<>();
        for (Class<?> c = cl; c != null && c != Object.class; c = c.getSuperclass()) {
            out.addAll(hookAllMethods(c, name, cb));
        }
        return out;
    }

    @SuppressWarnings("rawtypes")
    public static List<HookHandle> hookAllConstructors(Class<?> cl, XpCallback cb) {
        List<HookHandle> out = new ArrayList<>();
        if (cl == null || api == null) return out;
        for (Constructor c : cl.getDeclaredConstructors()) {
            HookHandle h = hookOne(c, cb);
            if (h != null) out.add(h);
        }
        return out;
    }

    public static HookHandle hookMethod(Executable ex, XpCallback cb) {
        return hookOne(ex, cb);
    }

    /** 等价 XposedHelpers.callMethod：按名字调实例方法。 */
    public static Object callMethod(Object obj, String name, Object... args) throws Throwable {
        if (obj == null) throw new NullPointerException("callMethod on null: " + name);
        Class<?> c = obj.getClass();
        java.lang.reflect.Method m = findMethod(c, name, args);
        if (m == null) throw new NoSuchMethodException(c.getName() + "#" + name);
        m.setAccessible(true);
        try { return m.invoke(obj, args); }
        catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause() == null ? e : e.getCause(); }
    }

    /** 等价 XposedHelpers.callStaticMethod。 */
    public static Object callStaticMethod(Class<?> c, String name, Object... args) throws Throwable {
        java.lang.reflect.Method m = findMethod(c, name, args);
        if (m == null) throw new NoSuchMethodException(c.getName() + "#" + name);
        m.setAccessible(true);
        try { return m.invoke(null, args); }
        catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause() == null ? e : e.getCause(); }
    }

    /** 等价 XposedHelpers.findClassIfExists：找不到返回 null。 */
    public static Class<?> findClassIfExists(String name, ClassLoader cl) {
        try { return Class.forName(name, false, cl); } catch (Throwable t) { return null; }
    }

    /** 按参数个数+可赋值性挑方法（旧 API 也这么干）。 */
    private static java.lang.reflect.Method findMethod(Class<?> c, String name, Object[] args) {
        int n = args == null ? 0 : args.length;
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterTypes().length != n) continue;
                Class<?>[] pt = m.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < n; i++) {
                    if (args[i] == null) continue;
                    if (!boxed(pt[i]).isInstance(args[i])) { ok = false; break; }
                }
                if (ok) return m;
            }
        }
        return null;
    }

    private static Class<?> boxed(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == int.class) return Integer.class;
        if (t == long.class) return Long.class;
        if (t == boolean.class) return Boolean.class;
        if (t == double.class) return Double.class;
        if (t == float.class) return Float.class;
        if (t == short.class) return Short.class;
        if (t == byte.class) return Byte.class;
        if (t == char.class) return Character.class;
        return t;
    }

    /**
     * 等价旧版 XSharedPreferences：读模块自己的 SharedPreferences。
     * 新版用 getRemotePreferences(group) —— 这是官方替代品，比 XSharedPreferences 可靠
     * （旧版在 apexdata 路径下经常读不到，就是之前「隐身开关有时失效」的根因）。
     */
    public static android.content.SharedPreferences remotePrefs(String group) {
        if (api == null) return null;
        try { return api.getRemotePreferences(group); } catch (Throwable t) { return null; }
    }

    public static String getString(String group, String key, String def) {
        try {
            android.content.SharedPreferences sp = remotePrefs(group);
            return sp == null ? def : sp.getString(key, def);
        } catch (Throwable t) { return def; }
    }

    public static Boolean getBool(String group, String key) {
        try {
            android.content.SharedPreferences sp = remotePrefs(group);
            if (sp == null || !sp.contains(key)) return null;
            return Boolean.valueOf(sp.getBoolean(key, false));
        } catch (Throwable t) { return null; }
    }

    /** 等价 AndroidAppHelper.currentApplication()：走 ActivityThread。 */
    public static android.content.Context currentApplication() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method m = at.getDeclaredMethod("currentApplication");
            m.setAccessible(true);
            return (android.content.Context) m.invoke(null);
        } catch (Throwable t) { return null; }
    }

    /** 等价旧版 XposedHelpers.findAndHookMethod：按名字+参数类型找方法再 hook。 */
    public static HookHandle findAndHook(String className, ClassLoader cl, String method, Class<?>[] paramTypes, XpCallback cb) {
        try {
            Class<?> c = Class.forName(className, false, cl);
            java.lang.reflect.Method m = c.getDeclaredMethod(method, paramTypes == null ? new Class<?>[0] : paramTypes);
            return hookOne(m, cb);
        } catch (Throwable t) {
            log("findAndHook miss: " + className + "#" + method + " -> " + t);
            return null;
        }
    }

    /** 单点 hook：把一个 Executable 包成 Hooker。 */
    public static HookHandle hookOne(Executable ex, XpCallback cb) {
        if (ex == null || api == null || cb == null) return null;
        try {
            HookHandle h = api.hook(ex).setPriority(50).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    return handle(chain, cb);
                }
            });
            HANDLES.add(h);
            return h;
        } catch (Throwable t) {
            log("hook failed: " + ex + " -> " + t);
            return null;
        }
    }

    /** 把新 API 的 Chain 跑成旧版 before/after 的语义。 */
    private static Object handle(Chain chain, XpCallback cb) throws Throwable {
        XpParam p = new XpParam(new ChainAdapter(chain));

        // before：旧版语义 —— setResult 后原方法不再执行
        cb.runBefore(p);
        if (p.hasResult()) return p.getResult();

        Object ret;
        try {
            ret = p.getArgs().length == chain.getArgs().size()
                    ? chain.proceed()
                    : chain.proceed(p.getArgs());
        } catch (Throwable t) {
            p.setThrowable(t);
            try { cb.runAfter(p); } catch (Throwable ignored) {}
            if (p.hasResult()) return p.getResult();
            throw t;
        }
        p.setResult(ret);
        cb.runAfter(p);
        return p.hasResult() ? p.getResult() : ret;
    }

    /** 全部解除（热重载时用）。 */
    public static void unhookAll() {
        for (HookHandle h : HANDLES) {
            try { h.unhook(); } catch (Throwable ignored) {}
        }
        HANDLES.clear();
    }
}