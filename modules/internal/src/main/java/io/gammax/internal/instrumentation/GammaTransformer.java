package io.gammax.internal.instrumentation;

import io.gammax.internal.format.*;
import io.gammax.internal.format.functional.InjectMethod;
import io.gammax.internal.format.functional.InterfaceImplementation;
import io.gammax.internal.format.functional.ExtendField;
import io.gammax.internal.format.functional.ExtendMethod;
import io.gammax.internal.util.ClassLoaderExtend;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.*;

public class GammaTransformer implements ClassFileTransformer {

    public static final GammaTransformer instance = new GammaTransformer();

    private static final List<String> unsupportedPaths = new ArrayList<>();

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
    public byte[] transform(
            ClassLoader loader, String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] bytecode
    ) {
        if(className == null) return null;
        for(String str: unsupportedPaths) if(className.startsWith(str)) return null;

        if(GammaCacheRegistry.instance.isTargetPath(className.replace("/", "."))) {
            for (ModifyClass mixin : GammaCacheRegistry.instance.getCache()) {
                if (mixin.getTargetClass().getName().replace('.', '/').equals(className)) {
                    List<InjectMethod> injectors = Arrays.asList(mixin.getInjectors());
                    injectors.sort(Comparator.comparingInt(InjectMethod::getPriority));

                    for (ExtendField extendField : mixin.getExtendFields()) bytecode = extendField.modify(bytecode);
                    for (ExtendMethod extendMethod : mixin.getExtendMethods()) bytecode = extendMethod.modify(bytecode);
                    for (InjectMethod injectMethod : injectors) bytecode = injectMethod.modify(bytecode);
                    for (InterfaceImplementation implementation: mixin.getImplementations()) {
                        bytecode = implementation.modify(bytecode);
                        byte[] implementationBytecode = GammaClassLoader.instance.getClassBytes(implementation.getInterfaceClass().getName());
                        ClassLoaderExtend.defineClass(loader, implementation.getInterfaceClass().getName(), implementationBytecode, 0, implementationBytecode.length, protectionDomain);
                    }

                    return bytecode;
                }
            }
        }

        return null;
    }

    private GammaTransformer() {}
}