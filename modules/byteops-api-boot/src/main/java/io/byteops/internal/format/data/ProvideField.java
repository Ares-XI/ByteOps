package io.byteops.internal.format.data;

import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Field;

@ApiStatus.Internal
public final class ProvideField {
    private final Field field;

    public ProvideField(Field field) {
        this.field = field;
    }

    public Field field() {
        return field;
    }
}