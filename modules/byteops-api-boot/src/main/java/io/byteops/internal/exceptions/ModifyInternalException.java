package io.byteops.internal.exceptions;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
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
