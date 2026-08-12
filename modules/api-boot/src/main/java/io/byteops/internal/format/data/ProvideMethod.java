package io.byteops.internal.format.data;

import java.lang.reflect.Method;

public final class ProvideMethod {
    private final Method method;

    public ProvideMethod(Method method) {
        this.method = method;
    }

    public Method method() {
        return method;
    }
}