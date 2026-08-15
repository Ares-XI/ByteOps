package io.byteops.internal.util;

import org.jetbrains.annotations.ApiStatus;
import java.security.ProtectionDomain;

@ApiStatus.Internal
public final class ClassLoaderExtend {
    public static native void defineClass(ClassLoader loader, String name, byte[] bytecode, int opcode, int length, ProtectionDomain protectionDomain);
}
