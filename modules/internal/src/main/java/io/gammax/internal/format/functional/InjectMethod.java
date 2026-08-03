package io.gammax.internal.format.functional;

import io.gammax.api.experemental.Inject;
import io.gammax.internal.format.data.ArgumentParameter;
import io.gammax.internal.format.data.LocalParameter;
import io.gammax.internal.format.data.ProvideField;
import io.gammax.internal.format.data.ProvideMethod;
import io.gammax.internal.format.FunctionalModifier;
import io.gammax.internal.instrumentation.GammaClassLoader;
import io.gammax.internal.util.DescriptorFormat;
import io.gammax.internal.util.visitor.InjectMethodVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class InjectMethod implements FunctionalModifier {
    private final Method method;
    private final Class<?> targetClass;
    private final Inject annotation;
    private final InjectMethodVisitor visitor;
    private final Map<String, String> fieldMap = new HashMap<>();
    private final Map<String, String> methodMap = new HashMap<>();
    private final ArgumentParameter[] argumentParams;
    private final LocalParameter[] localParams;

    public InjectMethod(Method method, Class<?> targetClass, ProvideField[] shadowFields, ExtendField[] uniqueFields, ProvideMethod[] shadowMethods, ExtendMethod[] uniqueMethods, ArgumentParameter[] argumentParams, LocalParameter[] localParams) {
        this.method = method;
        this.targetClass = targetClass;
        this.annotation = method.getAnnotation(Inject.class);
        this.argumentParams = argumentParams;
        this.localParams = localParams;

        buildFieldMap(shadowFields, uniqueFields);
        buildMethodMap(shadowMethods, uniqueMethods);

        this.visitor = new InjectMethodVisitor(method, targetClass, fieldMap, methodMap);
        extractMethodInstructions();
    }

    private void buildFieldMap(ProvideField[] shadowFields, ExtendField[] uniqueFields) {
        String targetName = targetClass.getName().replace('.', '/');
        for (ProvideField sf : shadowFields) {
            String key = sf.field().getName() + ":" + DescriptorFormat.getDescriptor(sf.field().getType());
            fieldMap.put(key, targetName);
        }
        for (ExtendField uf : uniqueFields) {
            String key = uf.getField().getName() + ":" + DescriptorFormat.getDescriptor(uf.getField().getType());
            fieldMap.put(key, targetName);
        }
    }

    private void buildMethodMap(ProvideMethod[] shadowMethods, ExtendMethod[] uniqueMethods) {
        String targetName = targetClass.getName().replace('.', '/');
        for (ProvideMethod sm : shadowMethods) {
            String key = sm.method().getName() + ":" + DescriptorFormat.getMethodDescriptor(sm.method());
            methodMap.put(key, targetName);
        }
        for (ExtendMethod um : uniqueMethods) {
            String key = um.getMethod().getName() + ":" + DescriptorFormat.getMethodDescriptor(um.getMethod());
            methodMap.put(key, targetName);
        }
    }

    private void extractMethodInstructions() {
        try {
            byte[] mixinBytes = GammaClassLoader.instance.getClassBytes(method.getDeclaringClass().getName());
            if (mixinBytes == null) {
                System.out.println("[Inject] mixinBytes = null for " + method.getName());
                return;
            }

            String injectorDesc = DescriptorFormat.getMethodDescriptor(method);
            new ClassReader(mixinBytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (name.equals(method.getName()) && desc.equals(injectorDesc)) return visitor;
                    return null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            if (visitor.instructions == null) System.out.println("[Inject] ❌ instructions is null for " + method.getName()); //TODO catch with custom exception
        } catch (Exception e) {
            e.printStackTrace(System.err); //TODO catch with custom exception
        }
    }

    @Override
    public byte[] modify(byte[] bytecode) {
        return new byte[0];
    }

    public int getPriority() {
        return annotation.priority();
    }
}