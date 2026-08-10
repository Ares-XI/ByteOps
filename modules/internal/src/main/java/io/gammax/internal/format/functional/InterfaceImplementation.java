package io.gammax.internal.format.functional;

import io.gammax.internal.exceptions.ModifyFormatException;
import io.gammax.internal.exceptions.ModifyInternalException;
import io.gammax.internal.format.FunctionalModifier;
import io.gammax.internal.util.DescriptorFormat;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public final class InterfaceImplementation implements FunctionalModifier {
    private final Class<?> interfaceClass;

    public InterfaceImplementation(Class<?> interfaceClass) {
        this.interfaceClass = interfaceClass;
    }

    public Class<?> getInterfaceClass() {
        return interfaceClass;
    }

    @Override
    public byte[] modify(byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) {
            new ModifyInternalException("class bytes are invalid").printStackTrace(System.err);
            return null;
        }

        ClassReader reader = new ClassReader(classBytes);;
        ClassNode classNode = new ClassNode();

        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        String interfaceName = interfaceClass.getName().replace('.', '/');

        if (!classNode.interfaces.contains(interfaceName)) classNode.interfaces.add(interfaceName);

        validateMethods(classNode);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);

        byte[] result = writer.toByteArray();

        ClassReader checkReader = new ClassReader(result);
        ClassNode checkNode = new ClassNode();
        checkReader.accept(checkNode, 0);

        return result;
    }

    private void validateMethods(ClassNode classNode) {
        Set<String> existingMethods = new HashSet<>();

        for (MethodNode mn : classNode.methods) existingMethods.add(mn.name + mn.desc);

        for (Method method : interfaceClass.getDeclaredMethods()) {
            String methodName = method.getName();
            String methodDesc = DescriptorFormat.getMethodDescriptor(method);
            String signature = methodName + methodDesc;

            boolean found = existingMethods.contains(signature);
            if (!found) new ModifyFormatException("Method " + signature + " from interface " + interfaceClass.getName() + " NOT found in class").printStackTrace(System.err);
        }
    }
}
