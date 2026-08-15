package io.byteops.internal.instrumentation;

import io.byteops.internal.InternalBootManager;
import io.byteops.internal.util.ClassCodeAnalyze;
import io.byteops.internal.exceptions.ModifyInternalException;
import io.byteops.internal.util.ClassLoaderExtend;
import org.jetbrains.annotations.ApiStatus;

import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public abstract class FormatModifyTransformer {
    private static FormatModifyTransformer instance;

    public static FormatModifyTransformer getInstance() {
        return instance;
    }

    protected static void setInstance(FormatModifyTransformer instance) {
        if(instance.getClass().getName().equals("io.byteops.shadow.ShadowModifyTransformer")) FormatModifyTransformer.instance = instance;
    }

    protected static final List<String> unsupportedPaths = new ArrayList<>();
    protected static final Set<ClassLoader> acceptedClassLoaders = ConcurrentHashMap.newKeySet();

    static {
        unsupportedPaths.add("java/");
        unsupportedPaths.add("jdk/");
        unsupportedPaths.add("sun/");
        unsupportedPaths.add("com/google/gson/");
        unsupportedPaths.add("org/intellij/");
        unsupportedPaths.add("org/jetbrains/");
        unsupportedPaths.add("org/objectweb/asm/");
        unsupportedPaths.add("io/byteops/");
    }

    protected final void defineClassRecursive(ClassLoader loader, String className, ProtectionDomain protectionDomain, Set<String> definedClasses, String currentTargetClass) {
        String binaryName = className.replace('/', '.');
        String currentTargetBinaryName = currentTargetClass.replace('/', '.');

        if (!definedClasses.add(binaryName)) return;
        if (binaryName.equals(currentTargetBinaryName)) return;

        if (binaryName.startsWith("java.") || binaryName.startsWith("jdk.") || binaryName.startsWith("sun.") || binaryName.startsWith("javax.")) return;

        byte[] bytes = JarClassLoader.getInstance().getClassBytes(binaryName);
        if (bytes == null) return;

        String[] deps = ClassCodeAnalyze.getClassPaths(bytes);
        for (String dep : deps) defineClassRecursive(loader, dep, protectionDomain, definedClasses, currentTargetClass);

        try {
            ClassLoaderExtend.defineClass(loader, binaryName, bytes, 0, bytes.length, protectionDomain);
        } catch (Exception e) {
            new ModifyInternalException(e, "failed to define " + binaryName).printStackTrace(InternalBootManager.getInstance().getPrintStream());
        }
    }
}