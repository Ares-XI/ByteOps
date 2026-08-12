package io.byteops.internal.format.data;

import java.lang.reflect.Field;

public final class ProvideField {
    private final Field field;

    public ProvideField(Field field) {
        this.field = field;
    }

    public Field field() {
        return field;
    }
}