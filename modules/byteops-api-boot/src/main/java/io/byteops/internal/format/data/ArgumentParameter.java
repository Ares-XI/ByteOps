package io.byteops.internal.format.data;

import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Parameter;

@ApiStatus.Internal
public class ArgumentParameter {
    private final Parameter parameter;

    public ArgumentParameter(Parameter parameter) {
        this.parameter = parameter;
    }

    public Parameter parameter() {
        return parameter;
    }
}
