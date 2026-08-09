package io.gammax.api.util;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class InjectResult<T> {
    private final T value;
    private final Throwable throwable;
    private final boolean stop;
    private final List<LocalData> localMap = new ArrayList<>();

    private InjectResult(T value, Throwable throwable, boolean stop) {
        this.value = value;
        this.throwable = throwable;
        this.stop = stop;
    }

    public static InjectResult<Void> pass() {
        return new InjectResult<>(null, null, false);
    }

    public static InjectResult<Void> stop() {
        return new InjectResult<>(null, null, true);
    }

    public static <T> InjectResult<T> stop(T value) {
        return new InjectResult<>(value, null, true);
    }

    public static InjectResult<Void> error(Throwable throwable) {
        return new InjectResult<>(null, throwable, true);
    }

    public InjectResult<T> setLocals(LocalData... locals) {
        localMap.addAll(Arrays.asList(locals));
        return this;
    }

    @ApiStatus.Internal
    public boolean isStop() {
        return stop;
    }

    @ApiStatus.Internal
    public T getValue() throws Throwable {
        if(throwable != null) throw throwable;
        return value;
    }

    @ApiStatus.Internal
    public boolean hasLocalUpdate(int index) {
        for (LocalData d : localMap) if (d.getIndex() == index) return true;
        return false;
    }

    @ApiStatus.Internal
    public Object getLocalValue(int index) {
        for (LocalData d : localMap) if (d.getIndex() == index) return d.getValue();
        return null;
    }
}
