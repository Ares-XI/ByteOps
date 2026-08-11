package io.byteops.internal.instrumentation;

import io.byteops.internal.format.ModifyClass;
import io.byteops.internal.util.ClassCodeAnalyze;
import io.byteops.internal.util.DescriptorFormat;
import io.byteops.modify.util.MethodReference;
import io.byteops.internal.InternalBootManager;
import io.byteops.internal.exceptions.ModifyFormatException;
import io.byteops.internal.exceptions.ModifyInternalException;
import io.byteops.internal.format.functional.InjectMethod;
import io.byteops.internal.format.functional.InterfaceImplementation;
import io.byteops.internal.format.functional.ExtendField;
import io.byteops.internal.format.functional.ExtendMethod;
import io.byteops.internal.util.ClassLoaderExtend;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ModifyFormatTransformer implements ClassFileTransformer {
    public static final ModifyFormatTransformer instance = new ModifyFormatTransformer();

    private static final List<String> unsupportedPaths = new ArrayList<>();

    private static final Set<ClassLoader> acceptedClassLoaders = ConcurrentHashMap.newKeySet();

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

    private static final ThreadLocal<Set<String>> currentlyTransforming = ThreadLocal.withInitial(HashSet::new);

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] bytecode) {
        if(className == null) return null;

        Set<String> transforming = currentlyTransforming.get();
        if (!transforming.add(className)) return null;

        try {
            List<InjectMethod> injectMethods = new ArrayList<>();

            if (DataCacheRegistry.instance.isTargetPath(className.replace("/", "."))) {
                for (ModifyClass modifyClass : DataCacheRegistry.instance.getCache()) {
                    if (modifyClass.getTargetClass().getName().replace('.', '/').equals(className)) {
                        for (Class<?> blockedClass : InternalBootManager.getBlockedClasses()) {
                            if (blockedClass.getName().replace(".", "/").equals(className)) {
                                new ModifyFormatException("modifying this class is unsupported").printStackTrace(System.err);
                                return null;
                            }
                        }

                        for (String str : unsupportedPaths)
                            if (className.startsWith(str)) {
                                new ModifyFormatException("modifying this class is unsupported").printStackTrace(System.err);
                                return null;
                            }

                        if (!acceptedClassLoaders.contains(loader)) {
                            for (Class<?> targetClass : JarClassLoader.instance.getApplyToDefine()) {
                                try {
                                    loader.loadClass(targetClass.getName());
                                } catch (ClassNotFoundException e) {
                                    byte[] targetClassBytes = JarClassLoader.instance.getClassBytes(targetClass.getName());
                                    if (targetClassBytes == null) {
                                        new ModifyInternalException(e, "class bytecode not found in GammaClassLoader cache").printStackTrace(System.err);
                                        continue;
                                    }
                                    ClassLoaderExtend.defineClass(loader, targetClass.getName(), targetClassBytes, 0, targetClassBytes.length, targetClass.getProtectionDomain());
                                }

                                acceptedClassLoaders.add(loader);
                            }
                        }

                        Set<String> definedClasses = new HashSet<>();
                        definedClasses.add(className.replace('/', '.'));

                        byte[] modifyByteCode = JarClassLoader.instance.getClassBytes(modifyClass.getModifyClass().getName());
                        if (modifyByteCode == null) {
                            new ModifyInternalException("class bytecode not found in GammaClassLoader cache").printStackTrace(System.err);
                            continue;
                        }

                        String[] targetClasses = ClassCodeAnalyze.getClassPathsRecursive(modifyByteCode, loader, className);

                        for (String targetClass : targetClasses)
                            defineClassRecursive(loader, targetClass, protectionDomain, definedClasses, className);
                        for (ExtendField extendField : modifyClass.getExtendFields())
                            bytecode = extendField.modify(bytecode);
                        for (ExtendMethod extendMethod : modifyClass.getExtendMethods())
                            bytecode = extendMethod.modify(bytecode);
                        for (InterfaceImplementation implementation : modifyClass.getImplementations())
                            bytecode = implementation.modify(bytecode);
                        Map<String, Integer> map = new HashMap<>();
                        for (InjectMethod inject : modifyClass.getInjectors()) {
                            MethodReference injectSig = inject.getAnnotation().method();
                            String key = injectSig.method() + ":" + DescriptorFormat.getMethodDescriptor(injectSig);

                            int currentCount = map.getOrDefault(key, 0);
                            inject.preparing(bytecode, currentCount);
                            injectMethods.add(inject);
                            map.put(key, currentCount + 1);
                        }
                    }
                }
            }

            injectMethods.sort(Comparator.comparingInt(InjectMethod::getPriority));
            for (InjectMethod injectMethod : injectMethods) bytecode = injectMethod.inject(bytecode);

            transforming.remove(className);
            return bytecode;
        } finally {
            transforming.remove(className);
        }
    }

    private void defineClassRecursive(ClassLoader loader, String className, ProtectionDomain protectionDomain, Set<String> definedClasses, String currentTargetClass) {
        String binaryName = className.replace('/', '.');
        String currentTargetBinaryName = currentTargetClass.replace('/', '.');

        if (!definedClasses.add(binaryName)) return;
        if (binaryName.equals(currentTargetBinaryName)) return;

        if (binaryName.startsWith("java.") || binaryName.startsWith("jdk.") || binaryName.startsWith("sun.") || binaryName.startsWith("javax.")) return;

        byte[] bytes = JarClassLoader.instance.getClassBytes(binaryName);
        if (bytes == null) return;

        String[] deps = ClassCodeAnalyze.getClassPaths(bytes);
        for (String dep : deps) defineClassRecursive(loader, dep, protectionDomain, definedClasses, currentTargetClass);

        try {
            ClassLoaderExtend.defineClass(loader, binaryName, bytes, 0, bytes.length, protectionDomain);
        } catch (Exception ex) {
            new ModifyInternalException(ex, "failed to define " + binaryName).printStackTrace(System.err);
        }
    }

    private ModifyFormatTransformer() {}
}