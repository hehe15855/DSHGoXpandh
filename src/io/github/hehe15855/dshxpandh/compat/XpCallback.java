package io.github.hehe15855.dshxpandh.compat;

/**
 * 对齐旧 XC_MethodHook 的写法：子类只覆写 beforeHookedMethod / afterHookedMethod。
 * 这样迁移时调用处几乎不用改 —— 只把 new XC_MethodHook() 换成 new XpCallback()。
 */
public abstract class XpCallback {

    /** 方法调用前。参数通过 p.getArgs() 看，改值用 p.setArg()。 */
    protected void beforeHookedMethod(XpParam p) throws Throwable {}

    /** 方法调用后。改返回值用 p.setResult()。 */
    protected void afterHookedMethod(XpParam p) throws Throwable {}

    // 兼容层内部调用入口（外部别用）
    public final Object runBefore(XpParam p) throws Throwable {
        beforeHookedMethod(p);
        return p.hasResult() ? p.getResult() : null;
    }

    public final void runAfter(XpParam p) throws Throwable {
        afterHookedMethod(p);
    }
}