package io.github.hehe15855.dshxpandh.compat;

import io.github.libxposed.api.XposedInterface.Chain;

import java.lang.reflect.Executable;
import java.util.List;

/** libxposed 的 Chain 适配成 ChainLike。 */
final class ChainAdapter implements ChainLike {

    private final Chain chain;

    ChainAdapter(Chain c) { this.chain = c; }

    @Override public Executable getExecutable() { return chain.getExecutable(); }

    @Override public Object getThisObject() { return chain.getThisObject(); }

    @Override public List<Object> getArgs() { return chain.getArgs(); }

    @Override public Object proceed(Object[] args) throws Throwable {
        return chain.proceed(args);
    }
}