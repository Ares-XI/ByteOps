package io.byteops.internal.format.functional;

import io.byteops.internal.InternalBootManager;
import io.byteops.internal.exceptions.ModifyFormatException;
import io.byteops.internal.exceptions.ModifyInternalException;
import io.byteops.internal.format.FunctionalModifier;
import io.byteops.internal.util.DescriptorFormat;
import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

@ApiStatus.Internal
public final class InterfaceImplementation implements FunctionalModifier {
    private final Class<?> interfaceClass;

    @Override
    public byte[] modify(byte[] classBytes) {
        try {
            if (classBytes == null || classBytes.length == 0) {
                new ModifyInternalException("class bytes are invalid").printStackTrace(InternalBootManager.getInstance().getPrintStream());
                return classBytes;
            }

            ClassReader reader = new ClassReader(classBytes);
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
        } catch (Throwable t) {
            t.printStackTrace(InternalBootManager.getInstance().getPrintStream());
            return classBytes;
        }
    }

    public InterfaceImplementation(Class<?> interfaceClass) {
        this.interfaceClass = interfaceClass;
    }

    private void validateMethods(ClassNode classNode) {
        Set<String> existingMethods = new HashSet<>();

        for (MethodNode mn : classNode.methods) existingMethods.add(mn.name + mn.desc);

        for (Method method : interfaceClass.getDeclaredMethods()) {
            String methodName = method.getName();
            String methodDesc = DescriptorFormat.getMethodDescriptor(method);
            String signature = methodName + methodDesc;

            boolean found = existingMethods.contains(signature);
            if (!found) new ModifyFormatException("Method " + signature + " from interface " + interfaceClass.getName() + " NOT found in class").printStackTrace(InternalBootManager.getInstance().getPrintStream());
        }
    }
}
