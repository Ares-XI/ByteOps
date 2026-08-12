package io.byteops.internal.format.data;

import java.lang.reflect.Parameter;

public class ArgumentParameter {
    private final Parameter parameter;

    public ArgumentParameter(Parameter parameter) {
        this.parameter = parameter;
    }

    public Parameter parameter() {
        return parameter;
    }
}
