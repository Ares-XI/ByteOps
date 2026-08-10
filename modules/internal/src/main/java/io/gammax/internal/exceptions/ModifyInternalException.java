package io.gammax.internal.exceptions;

public class ModifyInternalException extends RuntimeException {
    public ModifyInternalException(String message) {
        super(message);
    }

    public ModifyInternalException(Throwable t, String message) {
        super(message, t);
    }

    public ModifyInternalException(Throwable t) {
        super(t);
    }
}
