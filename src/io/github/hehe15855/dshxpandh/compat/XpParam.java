package io.github.hehe15855.dshxpandh.compat;

import java.lang.reflect.Executable;
import java.util.List;

/** 对应 XC_MethodHook.MethodHookParam：args / thisObject / result / throwable。 */
public class XpParam {

    private final ChainLike chain;
    private Object[] args;
    private Object result;
    private Throwable throwable;
    private boolean resultSet;

    XpParam(ChainLike chain) {
        this.chain = chain;
        List<Object> a = chain.getArgs();
        this.args = a == null ? new Object[0] : a.toArray();
    }

    public Executable getExecutable() { return chain.getExecutable(); }

    public Object getThisObject() { return chain.getThisObject(); }

    public Object[] getArgs() { return args; }

    public void setArgs(Object[] a) { this.args = a == null ? new Object[0] : a; }

    public Object getArg(int i) { return args != null && i >= 0 && i < args.length ? args[i] : null; }

    public void setArg(int i, Object v) { if (args != null && i >= 0 && i < args.length) args[i] = v; }

    public Object getResult() { return result; }

    public void setResult(Object r) { result = r; resultSet = true; }

    public boolean hasResult() { return resultSet; }

    public Throwable getThrowable() { return throwable; }

    public void setThrowable(Throwable t) { throwable = t; }
}