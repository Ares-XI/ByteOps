package io.gammax.internal.format.functional;

import io.gammax.internal.format.FunctionalModifier;
import io.gammax.internal.format.data.ProvideField;
import io.gammax.internal.format.data.ProvideMethod;
import io.gammax.internal.instrumentation.GammaClassLoader;
import io.gammax.internal.util.DescriptorFormat;
import io.gammax.internal.util.visitor.ExtendMethodVisitor;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;

public class ExtendMethod implements FunctionalModifier {
    private final Method method;
    private final Class<?> targetClass;

    private InsnList instructions;
    public ExtendMethodVisitor visitor;

    public ExtendMethod(Method method, Class<?> targetClass, ProvideField[] shadowFields, ProvideMethod[] shadowMethods, ExtendField[] uniqueFields, ExtendMethod[] uniqueMethods) {
        this.method = method;
        this.targetClass = targetClass;
        this.visitor = new ExtendMethodVisitor();

        buildFieldMap(shadowFields, uniqueFields);
        buildMethodMap(shadowMethods, uniqueMethods);
    }

    public Method getMethod() {
        return method;
    }

    public void updateMethodMap(ExtendMethod[] allUniqueMethods) {
        String targetName = targetClass.getName().replace('.', '/');

        for (ExtendMethod um : allUniqueMethods) {
            String key = um.getMethod().getName() + ":" + DescriptorFormat.getMethodDescriptor(um.getMethod());
            visitor.methodMap.put(key, targetName);
        }
        extractMethodInstructions();
    }

    private void buildFieldMap(ProvideField[] shadowFields, ExtendField[] uniqueFields) {
        for (ProvideField sf : shadowFields) {
            String key = sf.field().getName() + ":" + DescriptorFormat.getDescriptor(sf.field().getType());
            visitor.fieldMap.put(key, targetClass.getName().replace('.', '/'));
        }
        for (ExtendField uf : uniqueFields) {
            String key = uf.getField().getName() + ":" + DescriptorFormat.getDescriptor(uf.getField().getType());
            visitor.fieldMap.put(key, targetClass.getName().replace('.', '/'));
        }
    }

    private void buildMethodMap(ProvideMethod[] shadowMethods, ExtendMethod[] uniqueMethods) {
        for (ProvideMethod sm : shadowMethods) {
            String key = sm.method().getName() + ":" + DescriptorFormat.getMethodDescriptor(sm.method());
            visitor.methodMap.put(key, targetClass.getName().replace('.', '/'));
        }
        for (ExtendMethod um: uniqueMethods) {
            String key = um.getMethod().getName() + ":" + DescriptorFormat.getMethodDescriptor(um.getMethod());
            visitor.methodMap.put(key, targetClass.getName().replace('.', '/'));
        }
    }

    private void extractMethodInstructions() {
        try {
            byte[] mixinBytes = GammaClassLoader.instance.getClassBytes(method.getDeclaringClass().getName());
            if (mixinBytes == null) return;

            ClassReader reader = new ClassReader(mixinBytes);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (name.equals(ExtendMethod.this.method.getName()) && desc.equals(DescriptorFormat.getMethodDescriptor(method))) {
                        return visitor;
                    }
                    return null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            instructions = visitor.instructions;

        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    @Override
    public byte[] modify(byte[] targetClassBytes) {
        if (instructions == null || instructions.size() == 0) return targetClassBytes;

        ClassReader reader = new ClassReader(targetClassBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        ClassVisitor vis = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visitEnd() {
                MethodVisitor mv = cv.visitMethod(DescriptorFormat.getMethodAccess(method), method.getName(), DescriptorFormat.getMethodDescriptor(method), null, null);

                for (TryCatchBlockNode tcb : visitor.tryCatchBlocks) tcb.accept(mv);

                mv.visitCode();

                for (AbstractInsnNode insn : visitor.instructions) insn.accept(mv);
                for (LineNumberNode line : visitor.lineNumbers) line.accept(mv);
                for (LocalVariableNode local : visitor.localVariables) local.accept(mv);

                mv.visitMaxs(0, 0);
                mv.visitEnd();

                super.visitEnd();
            }
        };

        reader.accept(vis, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}