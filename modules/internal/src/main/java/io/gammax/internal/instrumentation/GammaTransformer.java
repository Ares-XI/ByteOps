package io.gammax.internal.instrumentation;

import io.gammax.api.util.MethodReference;
import io.gammax.internal.exeptions.ModifyFormatException;
import io.gammax.internal.exeptions.ModifyInternalException;
import io.gammax.internal.format.*;
import io.gammax.internal.format.functional.InjectMethod;
import io.gammax.internal.format.functional.InterfaceImplementation;
import io.gammax.internal.format.functional.ExtendField;
import io.gammax.internal.format.functional.ExtendMethod;
import io.gammax.internal.util.ClassLoaderExtend;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.*;

public final class GammaTransformer implements ClassFileTransformer {
    public static final GammaTransformer instance = new GammaTransformer();

    private static final List<String> unsupportedPaths = new ArrayList<>();

    private static final Set<ClassLoader> acceptedClassLoaders = new HashSet<>();

    static {
        unsupportedPaths.add("java/");
        unsupportedPaths.add("jdk/");
        unsupportedPaths.add("sun/");
        unsupportedPaths.add("com/google/gson/");
        unsupportedPaths.add("org/intellij/");
        unsupportedPaths.add("org/jetbrains/");
        unsupportedPaths.add("org/objectweb/asm/");
        unsupportedPaths.add("io/gammax/");
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] bytecode) {
        if(className == null) return null;

        List<InjectMethod> injectMethods = new ArrayList<>();

        if(GammaCacheRegistry.instance.isTargetPath(className.replace("/", "."))) {
            for (ModifyClass mixin : GammaCacheRegistry.instance.getCache()) {
                if (mixin.getTargetClass().getName().replace('.', '/').equals(className)) {
                    for(String str: unsupportedPaths) if(className.startsWith(str)) {
                        new ModifyFormatException("modifying this class is unsupported").printStackTrace(System.err);
                        return null;
                    }

                    if(!acceptedClassLoaders.contains(loader)) {
                        for(Class<?> targetClass: GammaClassLoader.instance.getApplyToDefine()) {
                            try {
                                loader.loadClass(targetClass.getName());
                            } catch (ClassNotFoundException e) {
                                byte[] targetClassBytes = GammaClassLoader.instance.getClassBytes(targetClass.getName());
                                if(targetClassBytes == null) {
                                    new ModifyInternalException(e, "class bytecode not found in GammaClassLoader cache").printStackTrace(System.err);
                                    continue;
                                }
                                ClassLoaderExtend.defineClass(loader, targetClass.getName(), targetClassBytes, 0, targetClassBytes.length, targetClass.getProtectionDomain());
                            }

                            acceptedClassLoaders.add(loader);
                        }
                    }

                    for (ExtendField extendField : mixin.getExtendFields()) bytecode = extendField.modify(bytecode);
                    for (ExtendMethod extendMethod : mixin.getExtendMethods()) bytecode = extendMethod.modify(bytecode);
                    for (InterfaceImplementation implementation: mixin.getImplementations()) {
                        bytecode = implementation.modify(bytecode);
                        byte[] implementationBytecode = GammaClassLoader.instance.getClassBytes(implementation.getInterfaceClass().getName());
                        if(implementationBytecode == null) {
                            new ModifyInternalException("interface bytecode not found in GammaClassLoader cache").printStackTrace(System.err);
                            continue;
                        }
                        ClassLoaderExtend.defineClass(loader, implementation.getInterfaceClass().getName(), implementationBytecode, 0, implementationBytecode.length, protectionDomain);
                    }
                    Map<MethodReference, Integer> map = new HashMap<>();
                    for (InjectMethod inject : mixin.getInjectors()) {
                        MethodReference injectSig = inject.getAnnotation().method();
                        boolean found = false;

                        for (Map.Entry<MethodReference, Integer> entry : map.entrySet()) {
                            MethodReference existingSig = entry.getKey();

                            if (existingSig.method().equals(injectSig.method()) && existingSig.result().equals(injectSig.result()) && Arrays.equals(existingSig.parameters(), injectSig.parameters())) {
                                int currentCount = entry.getValue();
                                inject.preparing(bytecode, currentCount);
                                injectMethods.add(inject);
                                map.put(existingSig, currentCount + 1);
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            inject.preparing(bytecode, 0);
                            injectMethods.add(inject);
                            map.put(injectSig, 1);
                        }
                    }
                }
            }
        }

        injectMethods.sort(Comparator.comparingInt(InjectMethod::getPriority));
        for (InjectMethod injectMethod: injectMethods) bytecode = injectMethod.inject(bytecode);

        return bytecode;
    }

    private GammaTransformer() {}
}