package io.byteops.internal.exceptions;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class ModifyFormatException extends RuntimeException {
    public ModifyFormatException(String message) {
        super(message);
    }
}
