package io.byteops.internal.format.functional;

import io.byteops.internal.exceptions.ModifyInternalException;
import io.byteops.internal.format.FunctionalModifier;
import io.byteops.internal.util.DescriptorFormat;
import org.objectweb.asm.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class ExtendField implements FunctionalModifier {
    private final Field field;
    private Object constantValue;

    public ExtendField(Field field) {
        this.field = field;
        if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())) extractConstantValue();
    }

    public Field getField() {
        return field;
    }

    private void extractConstantValue() {
        try {
            field.setAccessible(true);
            Object value = field.get(null);

            if (value instanceof String) constantValue = value;
            else if (value instanceof Integer) constantValue = value;
            else if (value instanceof Long) constantValue = value;
            else if (value instanceof Float) constantValue = value;
            else if (value instanceof Double) constantValue = value;
            else if (value instanceof Byte) constantValue = ((Byte) value).intValue();
            else if (value instanceof Short) constantValue = ((Short) value).intValue();
            else if (value instanceof Character) constantValue = value;
            else if (value instanceof Boolean) constantValue = ((Boolean) value) ? 1 : 0;
        } catch (Exception e) {
            new ModifyInternalException(e).printStackTrace(System.err);
        }
    }

    @Override
    public byte[] modify(byte[] originalClassBytes) {
        ClassReader reader = new ClassReader(originalClassBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, access, name, signature, superName, interfaces);

                FieldVisitor fv = cv.visitField(
                        DescriptorFormat.getAccessModifiers(field),
                        field.getName(),
                        DescriptorFormat.getDescriptor(field.getType()),
                        null,
                        constantValue
                );

                if (fv != null) fv.visitEnd();
            }
        };

        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}