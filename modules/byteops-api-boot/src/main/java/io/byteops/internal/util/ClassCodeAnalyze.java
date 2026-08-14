package io.byteops.internal.util;

import io.byteops.internal.instrumentation.JarClassLoader;
import org.objectweb.asm.*;

import java.util.*;

public final class ClassCodeAnalyze {
    private static final Set<String> IGNORED_CLASSES = Set.of(
            "io/byteops/modify/Arg", "io/byteops/modify/Extend",
            "io/byteops/modify/Local", "io/byteops/modify/Inject",
            "io/byteops/modify/Modify", "io/byteops/modify/Provide",
            "io/byteops/modify/util/At", "io/byteops/modify/util/InjectResult",
            "io/byteops/modify/util/LocalData",
            "io/byteops/modify/util/MethodReference"
    );

    public static String[] getClassPathsRecursive(byte[] bytecode, ClassLoader loader, String currentTargetClass) {
        Set<String> visited = new HashSet<>();
        Set<String> result = new LinkedHashSet<>();
        collectRecursive(bytecode, loader, visited, result, currentTargetClass);
        return result.toArray(new String[0]);
    }

    private static void collectRecursive(byte[] bytecode, ClassLoader loader, Set<String> visited, Set<String> result, String currentTargetClass) {
        String currentTargetBinaryName = currentTargetClass.replace('/', '.');
        String[] directPaths = getClassPaths(bytecode);

        for (String path : directPaths) {
            if (IGNORED_CLASSES.contains(path)) continue;
            if (path.startsWith("java/") || path.startsWith("jdk/") || path.startsWith("sun/") || path.startsWith("javax/")) continue;
            if (!visited.add(path)) continue;

            String binaryName = path.replace('/', '.');

            if (binaryName.equals(currentTargetBinaryName)) continue;

            try {
                loader.loadClass(binaryName);
                continue;
            } catch (ClassNotFoundException | LinkageError ignored) {}

            byte[] depBytes = JarClassLoader.instance.getClassBytes(binaryName);
            if (depBytes == null) continue;

            result.add(path);
            collectRecursive(depBytes, loader, visited, result, currentTargetClass);
        }
    }

    public static String[] getClassPaths(byte[] bytecode) {
        Set<String> classes = new HashSet<>();

        ClassReader reader = new ClassReader(bytecode);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {

            private boolean isIgnoredAnnotation(String descriptor) {
                if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                    String className = descriptor.substring(1, descriptor.length() - 1);
                    return IGNORED_CLASSES.contains(className);
                }
                return false;
            }

            private void addClassName(String className) {
                if (className == null || className.isEmpty()) return;
                int genericIndex = className.indexOf('<');
                if (genericIndex != -1) className = className.substring(0, genericIndex);
                while (className.startsWith("[")) className = className.substring(1);
                if (className.length() == 1 && "ZBCSIFJD".indexOf(className.charAt(0)) != -1) return;
                if (className.startsWith("L") && className.endsWith(";")) className = className.substring(1, className.length() - 1);
                if (!className.isEmpty()) classes.add(className);
            }

            private void addType(Type type) {
                if (type.getSort() == Type.OBJECT) addClassName(type.getInternalName());
                else if (type.getSort() == Type.ARRAY) addType(type.getElementType());
            }

            private void addDesc(String desc) {
                if (desc == null) return;
                if (desc.startsWith("(")) {
                    for (Type arg : Type.getArgumentTypes(desc)) addType(arg);
                    addType(Type.getReturnType(desc));
                } else {
                    addType(Type.getType(desc));
                }
            }

            private void addSignature(String signature) {
                if (signature == null) return;
                int i = 0;
                while (i < signature.length()) {
                    int start = signature.indexOf('L', i);
                    if (start == -1) break;
                    int end = -1;
                    for (int j = start + 1; j < signature.length(); j++) {
                        char c = signature.charAt(j);
                        if (c == ';' || c == '<' || c == '>') {
                            end = j;
                            break;
                        }
                    }
                    if (end == -1) break;
                    if (signature.charAt(end) == ';') {
                        String className = signature.substring(start + 1, end);
                        addClassName(className);
                    }
                    i = end + 1;
                }
            }

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                if (superName != null) addClassName(superName);
                if (interfaces != null) for (String iface : interfaces) addClassName(iface);
                addSignature(signature);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                addDesc(descriptor);
                addSignature(signature);
                if (value instanceof Type) addType((Type) value);
                return new FieldVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (isIgnoredAnnotation(descriptor)) return null;
                        addDesc(descriptor);
                        return collectAnnotationClasses();
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                addDesc(descriptor);
                addSignature(signature);
                if (exceptions != null) classes.addAll(Arrays.asList(exceptions));

                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (isIgnoredAnnotation(descriptor)) return null;
                        addDesc(descriptor);
                        return collectAnnotationClasses();
                    }

                    @Override
                    public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                        if (isIgnoredAnnotation(descriptor)) return null;
                        addDesc(descriptor);
                        return collectAnnotationClasses();
                    }

                    @Override
                    public AnnotationVisitor visitAnnotationDefault() {
                        return collectAnnotationClasses();
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        addClassName(type);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        addClassName(owner);
                        addDesc(descriptor);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        addClassName(owner);
                        addDesc(descriptor);
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                        addDesc(descriptor);
                        addClassName(bootstrapMethodHandle.getOwner());
                        for (Object arg : bootstrapMethodArguments) {
                            if (arg instanceof Type) addType((Type) arg);
                            if (arg instanceof Handle) addClassName(((Handle) arg).getOwner());
                        }
                    }

                    @Override
                    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                        if (type != null) addClassName(type);
                    }

                    @Override
                    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                        addDesc(descriptor);
                    }

                    @Override
                    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
                        addDesc(descriptor);
                        addSignature(signature);
                    }
                };
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (isIgnoredAnnotation(descriptor)) return null;
                addDesc(descriptor);
                return collectAnnotationClasses();
            }

            private AnnotationVisitor collectAnnotationClasses() {
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        if (value instanceof Type) addType((Type) value);
                    }

                    @Override
                    public void visitEnum(String name, String descriptor, String value) {
                        addDesc(descriptor);
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                        if (isIgnoredAnnotation(descriptor)) return null;
                        addDesc(descriptor);
                        return this;
                    }

                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        return this;
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES);

        return classes.toArray(new String[0]);
    }

    private ClassCodeAnalyze() {}
}
