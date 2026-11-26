package io.github.hehe15855.dshxpandh.compat;

import java.lang.reflect.Executable;
import java.util.List;

/** 兼容层内部的链式接口：屏蔽 libxposed Chain 的具体类型。 */
public interface ChainLike {

    Executable getExecutable();

    Object getThisObject();

    List<Object> getArgs();

    /** 继续执行原方法（可换参数）。 */
    Object proceed(Object[] args) throws Throwable;
}