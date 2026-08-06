package io.gammax.internal.util;

import io.gammax.api.util.Signature;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class DescriptorFormat {
    public static int getAccessModifiers(Field field) {
        int modifiers = field.getModifiers();
        int access = 0;

        if (Modifier.isPublic(modifiers)) access |= Opcodes.ACC_PUBLIC;
        if (Modifier.isPrivate(modifiers)) access |= Opcodes.ACC_PRIVATE;
        if (Modifier.isProtected(modifiers)) access |= Opcodes.ACC_PROTECTED;
        if (Modifier.isStatic(modifiers)) access |= Opcodes.ACC_STATIC;
        if (Modifier.isFinal(modifiers)) access |= Opcodes.ACC_FINAL;
        if (Modifier.isVolatile(modifiers)) access |= Opcodes.ACC_VOLATILE;
        if (Modifier.isTransient(modifiers)) access |= Opcodes.ACC_TRANSIENT;

        return access;
    }

    public static int getMethodAccess(Method method) {
        int modifiers = method.getModifiers();
        int access = 0;

        if (Modifier.isPublic(modifiers)) access |= Opcodes.ACC_PUBLIC;
        if (Modifier.isPrivate(modifiers)) access |= Opcodes.ACC_PRIVATE;
        if (Modifier.isProtected(modifiers)) access |= Opcodes.ACC_PROTECTED;
        if (Modifier.isStatic(modifiers)) access |= Opcodes.ACC_STATIC;
        if (Modifier.isFinal(modifiers)) access |= Opcodes.ACC_FINAL;
        if (Modifier.isSynchronized(modifiers)) access |= Opcodes.ACC_SYNCHRONIZED;
        if (Modifier.isNative(modifiers)) access |= Opcodes.ACC_NATIVE;
        if (Modifier.isAbstract(modifiers)) access |= Opcodes.ACC_ABSTRACT;
        if (Modifier.isStrict(modifiers)) access |= Opcodes.ACC_STRICT;

        return access;
    }

    public static String getMethodDescriptor(Signature signature) {
        return getMethodDescriptor(signature.parameters(), signature.result());
    }

    public static String getMethodDescriptor(Method method) {
        StringBuilder desc = new StringBuilder("(");
        for (Class<?> param : method.getParameterTypes()) desc.append(getDescriptor(param));
        desc.append(")").append(getDescriptor(method.getReturnType()));
        return desc.toString();
    }

    public static String getDescriptor(Class<?> type) {
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        if (type == void.class) return "V";

        if (type.isArray()) return "[" + getDescriptor(type.getComponentType());
        return "L" + type.getName().replace('.', '/') + ";";
    }

    public static int getLoadOpcode(Class<?> type) {
        if (type == int.class || type == boolean.class || type == byte.class || type == char.class || type == short.class) return Opcodes.ILOAD;
        if (type == long.class) return Opcodes.LLOAD;
        if (type == float.class) return Opcodes.FLOAD;
        if (type == double.class) return Opcodes.DLOAD;
        return Opcodes.ALOAD;
    }

    public static int getParamIndex(MethodNode method, int paramIndex, int startIndex) {
        int index = startIndex;
        Type[] argTypes = Type.getArgumentTypes(method.desc);
        for (int i = 0; i < paramIndex; i++) index += argTypes[i].getSize();
        return index;
    }

    public static String getMethodDescriptor(Class<?>[] parameterTypes, Class<?> returnType) {
        StringBuilder desc = new StringBuilder("(");
        for (Class<?> param : parameterTypes) desc.append(getDescriptor(param));
        desc.append(")").append(getDescriptor(returnType));
        return desc.toString();
    }

    public static int getReturnOpcode(Type type) {
        if (type == Type.VOID_TYPE) return Opcodes.RETURN;
        if (type == Type.BOOLEAN_TYPE || type == Type.BYTE_TYPE || type == Type.CHAR_TYPE || type == Type.SHORT_TYPE || type == Type.INT_TYPE) return Opcodes.IRETURN;
        if (type == Type.LONG_TYPE) return Opcodes.LRETURN;
        if (type == Type.FLOAT_TYPE) return Opcodes.FRETURN;
        if (type == Type.DOUBLE_TYPE) return Opcodes.DRETURN;
        return Opcodes.ARETURN;
    }

    private DescriptorFormat() {}
}