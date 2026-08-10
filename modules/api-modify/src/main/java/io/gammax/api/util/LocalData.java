package io.gammax.api.util;

import org.jetbrains.annotations.ApiStatus;

public final class LocalData {
    private final int index;
    private final Object value;

    public LocalData(int index, Object value) {
        this.index = index;
        this.value = value;
    }

    @ApiStatus.Internal
    public int getIndex() {
        return index;
    }

    @ApiStatus.Internal
    public Object getValue() {
        return value;
    }
}
