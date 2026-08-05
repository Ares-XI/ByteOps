package io.gammax.internal.format.functional;

import io.gammax.api.Arg;
import io.gammax.api.experemental.Inject;
import io.gammax.api.util.At;
import io.gammax.api.util.Signature;
import io.gammax.internal.format.data.ArgumentParameter;
//import io.gammax.internal.format.data.LocalParameter; TODO will be added later
import io.gammax.internal.format.data.ProvideField;
import io.gammax.internal.format.data.ProvideMethod;
import io.gammax.internal.format.FunctionalModifier;
import io.gammax.internal.instrumentation.GammaClassLoader;
import io.gammax.internal.util.DescriptorFormat;
import io.gammax.internal.util.visitor.InjectMethodVisitor;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static org.objectweb.asm.Opcodes.*;

public final class InjectMethod implements FunctionalModifier {
    private final Method method;
    private final Class<?> targetClass;
    private final Inject annotation;
    private final InjectMethodVisitor visitor;
    private final Map<String, String> fieldMap = new HashMap<>();
    private final Map<String, String> methodMap = new HashMap<>();
    private final ArgumentParameter[] argumentParams;
//    private final LocalParameter[] localParameters; TODO will be added later

    public InjectMethod(Method method, Class<?> targetClass, ProvideField[] provideFields, ExtendField[] extendFields, ProvideMethod[] provideMethods, ExtendMethod[] extendMethods, ArgumentParameter[] argumentParams) {
        this.method = method;
        this.targetClass = targetClass;
        this.annotation = method.getAnnotation(Inject.class);
        this.argumentParams = argumentParams;
//        this.localParameters = localParameters; TODO will be added later

        buildFieldMap(provideFields, extendFields);
        buildMethodMap(provideMethods, extendMethods);

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

    private MethodNode createInjectorMethodNode(String name) {
        int access = ACC_PRIVATE;
        if (Modifier.isStatic(method.getModifiers())) access |= ACC_STATIC;

        MethodNode mn = new MethodNode(
                access,
                name,
                DescriptorFormat.getMethodDescriptor(method),
                null,
                null);

        visitor.instructions.forEach(mn.instructions::add);

        mn.tryCatchBlocks.addAll(visitor.tryCatchBlocks);
        mn.localVariables.addAll(visitor.localVariables);

        mn.maxLocals = visitor.maxLocals;
        mn.maxStack = visitor.maxStack;

        return mn;
    }

    private void injectIntoTargetMethod(MethodNode targetMethod, String injectorName) {
        // === 1. Находим точку вставки ===
        AbstractInsnNode point = targetMethod.instructions.getFirst();
        if (point == null) {
            System.err.println("[Inject] ❌ Target method has no instructions: " + targetMethod.name);
            return;
        }

        // === 2. Конструкторы: вставляем ПОСЛЕ super()/this() ===
        if (targetMethod.name.equals("<init>") && annotation.at() == At.HEAD) {
            AbstractInsnNode current = point;
            while (current != null) {
                if (current instanceof MethodInsnNode min &&
                        min.name.equals("<init>") &&
                        min.getOpcode() == INVOKESPECIAL) {
                    point = current.getNext();
                    break;
                }
                current = current.getNext();
            }
            if (point == null) {
                point = targetMethod.instructions.getFirst();
            }
        }

        // === 3. Строим вызов инжектора ===
        InsnList callCode = buildCallCode(injectorName, targetMethod);

        // === 4. tryCatchBlocks из инжектора ===
        // ИСПРАВЛЕНИЕ: Убираем targetMethod.tryCatchBlocks.addAll(visitor.tryCatchBlocks);
        // Try-catch блоки самого инжектора остаются внутри injectorMethod (в classNode.methods).
        // В targetMethod мы добавляем только наш новый try-catch для InjectResult.

        // === 5. Находим свободные слоты ===
        // ИСПРАВЛЕНИЕ: Инжектор выполняется в отдельном фрейме стека, его локалки не пересекаются с targetMethod.
        int baseSlot = targetMethod.maxLocals;
        int throwableSlot = baseSlot + 1;

        // === 6. Строим try-catch ===
        LabelNode tryStart = new LabelNode(new Label());
        LabelNode tryEnd = new LabelNode(new Label());
        LabelNode catchStart = new LabelNode(new Label());
        LabelNode continueLabel = new LabelNode(new Label());

        InsnList tryBlock = new InsnList();

        // Вызов инжектора
        tryBlock.add(callCode);
        tryBlock.add(new VarInsnNode(ASTORE, baseSlot));

        // Проверка isStop()
        tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
        tryBlock.add(new MethodInsnNode(
                INVOKEVIRTUAL,
                "io/gammax/api/util/InjectResult",
                "isStop",
                "()Z",
                false
        ));
        tryBlock.add(new JumpInsnNode(IFEQ, continueLabel));

        // Обработка stop == true (Return logic)
        Type returnType = Type.getReturnType(targetMethod.desc);

        if (returnType == Type.VOID_TYPE) {
            tryBlock.add(new InsnNode(RETURN));
        } else if (returnType.getSort() == Type.OBJECT || returnType.getSort() == Type.ARRAY) {
            tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
            tryBlock.add(new MethodInsnNode(
                    INVOKEVIRTUAL,
                    "io/gammax/api/util/InjectResult",
                    "getValue",
                    "()Ljava/lang/Object;",
                    false
            ));
            tryBlock.add(new TypeInsnNode(CHECKCAST, returnType.getInternalName()));
            tryBlock.add(new InsnNode(ARETURN));
        } else {
            // Примитив: анбоксинг
            tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
            tryBlock.add(new MethodInsnNode(
                    INVOKEVIRTUAL,
                    "io/gammax/api/util/InjectResult",
                    "getValue",
                    "()Ljava/lang/Object;",
                    false
            ));
            String wrapperType = getWrapperType(returnType);
            tryBlock.add(new TypeInsnNode(CHECKCAST, wrapperType));
            String unboxMethod = getUnboxMethod(returnType);
            String unboxDesc = getUnboxDesc(returnType);
            tryBlock.add(new MethodInsnNode(
                    INVOKEVIRTUAL,
                    wrapperType,
                    unboxMethod,
                    unboxDesc,
                    false
            ));
            tryBlock.add(new InsnNode(DescriptorFormat.getReturnOpcode(returnType)));
        }

        // Catch-блок
        InsnList catchBlock = new InsnList();
        catchBlock.add(new VarInsnNode(ASTORE, throwableSlot));
        catchBlock.add(new TypeInsnNode(NEW, "java/lang/RuntimeException"));
        catchBlock.add(new InsnNode(DUP));
        catchBlock.add(new VarInsnNode(ALOAD, throwableSlot));
        catchBlock.add(new MethodInsnNode(
                INVOKESPECIAL,
                "java/lang/RuntimeException",
                "<init>",
                "(Ljava/lang/Throwable;)V",
                false
        ));
        catchBlock.add(new InsnNode(ATHROW));

        // === 7. ВСТАВЛЯЕМ КОД В TARGET METHOD ===
        targetMethod.instructions.insertBefore(point, tryStart);
        targetMethod.instructions.insertBefore(point, tryBlock);
        targetMethod.instructions.insertBefore(point, tryEnd);

        // ИСПРАВЛЕНИЕ: Вставляем catch-блок ПЕРЕД continueLabel.
        targetMethod.instructions.insertBefore(point, catchStart);
        targetMethod.instructions.insertBefore(point, catchBlock);

        // ⭐ ГЛАВНОЕ ИСПРАВЛЕНИЕ: continueLabel должен быть ПОСЛЕ catch-блока!
        // Теперь, если isStop() == false, IFEQ прыгнет сюда, и выполнение корректно
        // продолжится оригинальным кодом метода (point), минуя выброс исключения.
        targetMethod.instructions.insertBefore(point, continueLabel);

        // === 8. ДОБАВЛЯЕМ try-catch В ТАБЛИЦУ ИСКЛЮЧЕНИЙ ===
        targetMethod.tryCatchBlocks.add(new TryCatchBlockNode(
                tryStart,
                tryEnd,
                catchStart,
                "java/lang/Throwable"
        ));

        // === 9. Пересчитываем maxLocals (Опционально, COMPUTE_MAXS сделает это сам) ===
        targetMethod.maxLocals = throwableSlot + 1;
        targetMethod.maxStack = Math.max(targetMethod.maxStack, 4); // 4 с запасом на стековые операции обертки
    }

    private InsnList buildCallCode(String injectorName, MethodNode targetMethod) {
        InsnList callCode = new InsnList();

        boolean targetStatic = (targetMethod.access & ACC_STATIC) != 0;
        boolean injectorStatic = Modifier.isStatic(method.getModifiers());

        if (!targetStatic && !injectorStatic) callCode.add(new VarInsnNode(ALOAD, 0));

        int base = targetStatic ? 0 : 1;

        for (ArgumentParameter ap : argumentParams) {
            int idx = ap.parameter().getAnnotation(Arg.class).value();
            int paramIndex = DescriptorFormat.getParamIndex(targetMethod, idx, base);
            callCode.add(new VarInsnNode(DescriptorFormat.getLoadOpcode(ap.parameter().getType()), paramIndex));
        }

        callCode.add(new MethodInsnNode(injectorStatic ? INVOKESTATIC : INVOKEVIRTUAL, targetClass.getName().replace('.', '/'), injectorName, DescriptorFormat.getMethodDescriptor(method), false));

        return callCode;
    }

    private String getWrapperType(Type type) {
        if (type == Type.BOOLEAN_TYPE) return "java/lang/Boolean";
        if (type == Type.BYTE_TYPE) return "java/lang/Byte";
        if (type == Type.CHAR_TYPE) return "java/lang/Character";
        if (type == Type.SHORT_TYPE) return "java/lang/Short";
        if (type == Type.INT_TYPE) return "java/lang/Integer";
        if (type == Type.LONG_TYPE) return "java/lang/Long";
        if (type == Type.FLOAT_TYPE) return "java/lang/Float";
        if (type == Type.DOUBLE_TYPE) return "java/lang/Double";
        throw new IllegalArgumentException("Unknown primitive type: " + type);
    }

    private String getUnboxMethod(Type type) {
        if (type == Type.BOOLEAN_TYPE) return "booleanValue";
        if (type == Type.BYTE_TYPE) return "byteValue";
        if (type == Type.CHAR_TYPE) return "charValue";
        if (type == Type.SHORT_TYPE) return "shortValue";
        if (type == Type.INT_TYPE) return "intValue";
        if (type == Type.LONG_TYPE) return "longValue";
        if (type == Type.FLOAT_TYPE) return "floatValue";
        if (type == Type.DOUBLE_TYPE) return "doubleValue";
        throw new IllegalArgumentException("Unknown primitive type: " + type);
    }

    private String getUnboxDesc(Type type) {
        if (type == Type.BOOLEAN_TYPE) return "()Z";
        if (type == Type.BYTE_TYPE) return "()B";
        if (type == Type.CHAR_TYPE) return "()C";
        if (type == Type.SHORT_TYPE) return "()S";
        if (type == Type.INT_TYPE) return "()I";
        if (type == Type.LONG_TYPE) return "()J";
        if (type == Type.FLOAT_TYPE) return "()F";
        if (type == Type.DOUBLE_TYPE) return "()D";
        throw new IllegalArgumentException("Unknown primitive type: " + type);
    }

    // Находит максимальный индекс локальной переменной, используемой в методе
    private int getMaxLocalIndex(InsnList instructions) {
        int max = -1;
        for (AbstractInsnNode insn : instructions) {
            if (insn instanceof VarInsnNode varInsn) {
                int size = getVarSize(varInsn);
                max = Math.max(max, varInsn.var + size - 1);
            }
            if (insn instanceof IincInsnNode iincInsn) {
                max = Math.max(max, iincInsn.var);
            }
        }
        return max;
    }

    // Возвращает размер (1 или 2) для long/double
    private int getVarSize(VarInsnNode varInsn) {
        int opcode = varInsn.getOpcode();
        if (opcode == Opcodes.LLOAD || opcode == Opcodes.LSTORE ||
                opcode == Opcodes.DLOAD || opcode == Opcodes.DSTORE) {
            return 2;
        }
        return 1;
    }

    // ===== 7. PUBLIC API =====

    @Override
    public byte[] modify(byte[] bytecode) {
        ClassNode classNode = new ClassNode();
        new ClassReader(bytecode).accept(classNode, ClassReader.SKIP_FRAMES);

        String injectorName = "injector$" + UUID.randomUUID().toString().replace("-", "");
        MethodNode injectorMethod = createInjectorMethodNode(injectorName);
        classNode.methods.add(injectorMethod);

        Signature targetSig = annotation.signature();
        String targetDesc = DescriptorFormat.getMethodDescriptor(targetSig);
        MethodNode targetMethod = null;

        for (MethodNode method : classNode.methods) {
            if (method.name.equals(annotation.method()) && method.desc.equals(targetDesc)) {
                targetMethod = method;
                break;
            }
        }

        if (targetMethod == null) {
            System.err.println("[Inject] ❌ Target method not found: " + annotation.method() + targetDesc);
            return bytecode;
        }

        if ((targetMethod.access & ACC_ABSTRACT) != 0 || (targetMethod.access & ACC_NATIVE) != 0) {
            System.err.println("[Inject] ❌ Target method " + annotation.method() + targetDesc + " is abstract/native");
            return bytecode;
        }

        injectIntoTargetMethod(targetMethod, injectorName);

        // ⭐⭐ ПЕРЕСЧИТЫВАЕМ ВСЕ ФРЕЙМЫ И МАКСИМУМЫ
        for (MethodNode method : classNode.methods) {
            Iterator<AbstractInsnNode> it = method.instructions.iterator();
            while (it.hasNext()) {
                AbstractInsnNode insn = it.next();
                if (insn instanceof FrameNode) {
                    it.remove(); // <-- Это работает!
                }
            }
        }

        // ИСПОЛЬЗУЕМ КЛАССОВЫЙ РАЙТЕР С ПЕРЕСЧЁТОМ
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public int getPriority() {
        return annotation.priority();
    }
}