package io.byteops.shadow;

import io.byteops.internal.InternalBootManager;
import io.byteops.internal.exceptions.ModifyFormatException;
import io.byteops.internal.exceptions.ModifyInternalException;
import io.byteops.internal.format.ModifyClass;
import io.byteops.internal.format.functional.ExtendField;
import io.byteops.internal.format.functional.ExtendMethod;
import io.byteops.internal.format.functional.Injector;
import io.byteops.internal.format.functional.InterfaceImplementation;
import io.byteops.internal.instrumentation.DataCacheRegistry;
import io.byteops.internal.instrumentation.JarClassLoader;
import io.byteops.internal.instrumentation.FormatModifyTransformer;
import io.byteops.internal.util.ClassCodeAnalyze;
import io.byteops.internal.util.ClassLoaderExtend;
import io.byteops.internal.util.DescriptorFormat;
import io.byteops.modify.util.MethodReference;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.*;

final class ShadowModifyTransformer extends FormatModifyTransformer implements ClassFileTransformer {
    static final ShadowModifyTransformer instance = new ShadowModifyTransformer();

    public static void init() {
        setInstance(instance);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] bytecode) {
        if(className == null) return null;

        List<Injector> injectMethods = new ArrayList<>();

        if (DataCacheRegistry.getInstance().isTargetPath(className.replace("/", "."))) {
            for (ModifyClass modifyClass : DataCacheRegistry.getInstance().getCache()) {
                if (modifyClass.getTargetClass().getName().replace('.', '/').equals(className)) {
                    for (Class<?> blockedClass : InternalBootManager.getInstance().getBlockedClasses()) {
                        if (blockedClass.getName().replace(".", "/").equals(className)) {
                            new ModifyFormatException("modifying this class is unsupported").printStackTrace(InternalBootManager.getInstance().getPrintStream());
                            return null;
                        }
                    }

                    for (String str : unsupportedPaths) {
                        if (className.startsWith(str)) {
                            new ModifyFormatException("modifying this class is unsupported").printStackTrace(InternalBootManager.getInstance().getPrintStream());
                            return null;
                        }
                    }

                    if (!acceptedClassLoaders.contains(loader)) {
                        for (Class<?> targetClass : JarClassLoader.getInstance().getApplyToDefine()) {
                            try {
                                loader.loadClass(targetClass.getName());
                            } catch (ClassNotFoundException e) {
                                byte[] targetClassBytes = JarClassLoader.getInstance().getClassBytes(targetClass.getName());
                                if (targetClassBytes == null) {
                                    new ModifyInternalException(e, "class bytecode not found in GammaClassLoader cache").printStackTrace(InternalBootManager.getInstance().getPrintStream());
                                    continue;
                                }
                                ClassLoaderExtend.defineClass(loader, targetClass.getName(), targetClassBytes, 0, targetClassBytes.length, targetClass.getProtectionDomain());
                            }
                        }
                        acceptedClassLoaders.add(loader);
                    }

                    Set<String> definedClasses = new HashSet<>();
                    definedClasses.add(className.replace('/', '.'));

                    byte[] modifyByteCode = JarClassLoader.getInstance().getClassBytes(modifyClass.getModifyClass().getName());
                    if (modifyByteCode == null) {
                        new ModifyInternalException("class bytecode not found in GammaClassLoader cache").printStackTrace(InternalBootManager.getInstance().getPrintStream());
                        continue;
                    }

                    String[] targetClasses = ClassCodeAnalyze.getClassPathsRecursive(modifyByteCode, loader, className);

                    for (String targetClass : targetClasses) defineClassRecursive(loader, targetClass, protectionDomain, definedClasses, className);
                    for (ExtendField extendField : modifyClass.getExtendFields()) bytecode = extendField.modify(bytecode);
                    for (ExtendMethod extendMethod : modifyClass.getExtendMethods()) bytecode = extendMethod.modify(bytecode);
                    for (InterfaceImplementation implementation : modifyClass.getImplementations()) bytecode = implementation.modify(bytecode);

                    Map<String, Integer> map = new HashMap<>();
                    for (Injector inject : modifyClass.getInjectors()) {
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

        injectMethods.sort(Comparator.comparingInt(Injector::getPriority));
        for (Injector injectMethod : injectMethods) bytecode = injectMethod.inject(bytecode);

        return bytecode;
    }
}
