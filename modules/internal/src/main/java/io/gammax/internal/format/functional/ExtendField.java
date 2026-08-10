package io.gammax.internal.format.functional;

import io.gammax.internal.exceptions.ModifyInternalException;
import io.gammax.internal.format.FunctionalModifier;
import io.gammax.internal.util.DescriptorFormat;
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

            switch (value) {
                case String ignored -> constantValue = value;
                case Integer ignored -> constantValue = value;
                case Long ignored -> constantValue = value;
                case Float ignored -> constantValue = value;
                case Double ignored -> constantValue = value;
                case Byte b -> constantValue = b.intValue();
                case Short i -> constantValue = i.intValue();
                case Character ignored -> constantValue = value;
                case Boolean b -> constantValue = b ? 1 : 0;
                case null, default -> {}
            }
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