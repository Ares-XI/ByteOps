package io.byteops.internal.format.data;

import java.lang.reflect.Parameter;

public final class LocalParameter {
    private final Parameter parameter;

    public LocalParameter(Parameter parameter) {
        this.parameter = parameter;
    }

    public Parameter parameter() {
        return parameter;
    }
}