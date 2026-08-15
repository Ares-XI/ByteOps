package io.byteops.internal.format.data;

import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Parameter;

@ApiStatus.Internal
public final class LocalParameter {
    private final Parameter parameter;

    public LocalParameter(Parameter parameter) {
        this.parameter = parameter;
    }

    public Parameter parameter() {
        return parameter;
    }
}