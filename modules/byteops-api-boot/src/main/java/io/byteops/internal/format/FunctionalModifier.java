package io.byteops.internal.format;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface FunctionalModifier {
    byte[] modify(byte[] bytecode);
}
