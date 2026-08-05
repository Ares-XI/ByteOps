package io.gammax.internal.format.functional;

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
            System.err.println("[InterfaceImplementation] Error: null or empty class bytes");
            return null;
        }

        System.out.println("[InterfaceImplementation] ===== START =====");
        System.out.println("[InterfaceImplementation] Class bytes size: " + classBytes.length);
        System.out.println("[InterfaceImplementation] Interface: " + interfaceClass.getName());

        ClassReader reader;
        ClassNode classNode;
        try {
            reader = new ClassReader(classBytes);
            classNode = new ClassNode();
            reader.accept(classNode, ClassReader.EXPAND_FRAMES);
        } catch (Exception e) { //TODO catch with custom exception
            System.err.println("[InterfaceImplementation] Error reading class: " + e);
            e.printStackTrace(System.err);
            return null;
        }

        System.out.println("[InterfaceImplementation] Original interfaces: " + classNode.interfaces);

        String interfaceName = interfaceClass.getName().replace('.', '/');
        if (!classNode.interfaces.contains(interfaceName)) {
            classNode.interfaces.add(interfaceName);
            System.out.println("[InterfaceImplementation] ✅ Added interface: " + interfaceName); //TODO catch with custom exception
        } else {
            System.out.println("[InterfaceImplementation] ⚠️ Interface already present: " + interfaceName); //TODO catch with custom exception
        }

        System.out.println("[InterfaceImplementation] New interfaces: " + classNode.interfaces);

        validateMethods(classNode);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        try {
            classNode.accept(writer);
        } catch (Exception e) {
            System.err.println("[InterfaceImplementation] Error during class writing: " + e);
            e.printStackTrace(System.err);
            return classBytes;
        }
        byte[] result = writer.toByteArray();

        ClassReader checkReader = new ClassReader(result);
        ClassNode checkNode = new ClassNode();
        checkReader.accept(checkNode, 0);
        System.out.println("[InterfaceImplementation] Final interfaces after write: " + checkNode.interfaces);
        System.out.println("[InterfaceImplementation] Result bytecode size: " + result.length);
        System.out.println("[InterfaceImplementation] ===== END =====\n");

        return result;
    }

    private void validateMethods(ClassNode classNode) {
        System.out.println("[InterfaceImplementation] Validating methods from " + interfaceClass.getName());

        Set<String> existingMethods = new HashSet<>();
        for (MethodNode mn : classNode.methods) {
            existingMethods.add(mn.name + mn.desc);
            System.out.println("  Existing method: " + mn.name + mn.desc);
        }

        for (Method method : interfaceClass.getDeclaredMethods()) {
            String methodName = method.getName();
            String methodDesc = DescriptorFormat.getMethodDescriptor(method);
            String signature = methodName + methodDesc;

            System.out.println("  Looking for interface method: " + signature);

            boolean found = existingMethods.contains(signature);
            if (!found) {
                System.err.println("[InterfaceImplementation] ❌ Method " + signature +
                        " from interface " + interfaceClass.getName() + " NOT found in class"); //TODO catch with custom exception
            } else {
                System.out.println("[InterfaceImplementation] ✅ Method " + signature + " found");
            }
        }
    }
}
