package io.byteops.internal.format.data;

import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Method;

@ApiStatus.Internal
public final class ProvideMethod {
    private final Method method;

    public ProvideMethod(Method method) {
        this.method = method;
    }

    public Method method() {
        return method;
    }
}