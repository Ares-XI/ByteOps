package io.gammax.api.util;

public final class InjectResult<T> {
    private final T value;
    private final Throwable throwable;
    private final boolean stop;

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
        return new InjectResult<>(null, throwable, false);
    }

    public boolean isStop() {
        return stop;
    }

    public T getValue() throws Throwable {
        if(throwable != null) throw throwable;
        return value;
    }
}
