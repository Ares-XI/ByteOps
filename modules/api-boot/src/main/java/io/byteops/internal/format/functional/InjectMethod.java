package io.byteops.internal.format.functional;

import io.byteops.modify.Arg;
import io.byteops.modify.Inject;
import io.byteops.modify.Local;
import io.byteops.modify.util.MethodReference;
import io.byteops.internal.exceptions.ModifyInternalException;
import io.byteops.internal.format.data.ArgumentParameter;
import io.byteops.internal.format.data.LocalParameter;
import io.byteops.internal.format.data.ProvideField;
import io.byteops.internal.format.data.ProvideMethod;
import io.byteops.internal.instrumentation.JarClassLoader;
import io.byteops.internal.util.DescriptorFormat;
import io.byteops.internal.util.visitor.InjectMethodVisitor;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
import java.util.*;

import static org.objectweb.asm.Opcodes.*;

public final class InjectMethod {
    private final java.lang.reflect.Method method;
    private final Class<?> targetClass;
    private final Inject annotation;
    private final InjectMethodVisitor visitor;
    private final Map<String, String> fieldMap = new HashMap<>();
    private final Map<String, String> methodMap = new HashMap<>();
    private final ArgumentParameter[] argumentParams;
    private final LocalParameter[] localParameters;

    private int injectorIndex;
    private int originalMaxLocals;
    private static final int SLOTS_PER_INJECTOR = 32;

    private MethodNode preparedTargetMethod;
    private List<AbstractInsnNode> preparedPoints;

    public InjectMethod(java.lang.reflect.Method method, Class<?> targetClass, ProvideField[] provideFields, ExtendField[] extendFields, ProvideMethod[] provideMethods, ExtendMethod[] extendMethods, ArgumentParameter[] argumentParams, LocalParameter[] localParameters) {
        this.method = method;
        this.targetClass = targetClass;
        this.annotation = method.getAnnotation(Inject.class);
        this.argumentParams = argumentParams;
        this.localParameters = localParameters;

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

    private void injectAtPoint(MethodNode targetMethod, AbstractInsnNode point, String injectorName, int baseSlot) {
        switch (annotation.at()) {
            case HEAD -> injectBeforeInsn(targetMethod, point, injectorName, baseSlot);
            case RETURN -> injectBeforeReturn(targetMethod, point, injectorName, baseSlot);
            case INVOKE, NEW, GET, PUT -> injectBeforeInvoke(targetMethod, point, injectorName, baseSlot);
            case THROW -> injectBeforeThrow(targetMethod, point, injectorName, baseSlot);
        }
    }

    private void extractMethodInstructions() {
        byte[] mixinBytes = JarClassLoader.instance.getClassBytes(method.getDeclaringClass().getName());
        if (mixinBytes == null) {
            new ModifyInternalException("[Inject] mixinBytes = null for " + method.getName()).printStackTrace(System.err);
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

        if (visitor.instructions == null) new ModifyInternalException("instructions is null for " + method.getName()).printStackTrace(System.err);
    }

    private MethodNode createInjectorMethodNode(String name, boolean targetStatic) {
        int access = ACC_PRIVATE;
        if (Modifier.isStatic(method.getModifiers()) || targetStatic) access |= ACC_STATIC;

        MethodNode mn = new MethodNode(access, name, DescriptorFormat.getMethodDescriptor(method), null, null);

        visitor.instructions.forEach(mn.instructions::add);
        mn.tryCatchBlocks.addAll(visitor.tryCatchBlocks);
        mn.localVariables.addAll(visitor.localVariables);
        mn.maxLocals = visitor.maxLocals;
        mn.maxStack = visitor.maxStack;

        return mn;
    }

    private List<AbstractInsnNode> findInjectionPoints(MethodNode targetMethod) {
        List<AbstractInsnNode> allPoints = new ArrayList<>();

        switch (annotation.at()) {
            case HEAD -> {
                AbstractInsnNode point = targetMethod.instructions.getFirst();
                if (targetMethod.name.equals("<init>")) {
                    AbstractInsnNode current = point;
                    while (current != null) {
                        if (current instanceof MethodInsnNode min && min.name.equals("<init>") && min.getOpcode() == INVOKESPECIAL) {
                            point = current.getNext();
                            break;
                        }
                        current = current.getNext();
                    }
                }
                if (point != null) allPoints.add(point);
            }
            case RETURN -> {
                for (AbstractInsnNode insn = targetMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) if (isReturnInsn(insn) && !isInjectorReturn(insn)) allPoints.add(insn);
            }
            case INVOKE -> {
                for (AbstractInsnNode insn = targetMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) if (isInvokeOrBinaryOp(insn) && !isInjectorCall(insn)) allPoints.add(insn);
            }
            case NEW -> {
                for (AbstractInsnNode insn = targetMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) if (isNewInsn(insn) && !isInjectorNew(insn)) allPoints.add(insn);
            }
            case GET -> {
                for (AbstractInsnNode insn = targetMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) if (isGetInsn(insn) && isNotInjectorSlotAccess(insn)) allPoints.add(insn);
            }
            case PUT -> {
                for (AbstractInsnNode insn = targetMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) if (isPutInsn(insn) && isNotInjectorSlotAccess(insn)) allPoints.add(insn);
            }
            case THROW -> {
                for (AbstractInsnNode insn = targetMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) if (isThrowInsn(insn) && !isInjectorThrow(insn)) allPoints.add(insn);
            }
        }

        int index = annotation.index();
        if (index == -1) return allPoints;
        else if (index >= 0 && index < allPoints.size()) return List.of(allPoints.get(index));
        else {
            new ModifyInternalException("Index out of bounds: " + index + " (found " + allPoints.size() + ")").printStackTrace(System.err);
            return List.of();
        }
    }

    private void injectBeforeInvoke(MethodNode targetMethod, AbstractInsnNode invokeInsn, String injectorName, int baseSlot) {
        Type[] stackTypes = getStackTypesBeforeInsn(invokeInsn);
        Type methodReturnType = Type.getReturnType(targetMethod.desc);

        InsnList callCode = buildCallCode(injectorName, targetMethod);

        int throwableSlot = baseSlot + 1;
        int tempSlotStart = baseSlot + 2;

        LabelNode tryStart = new LabelNode(new Label());
        LabelNode tryEnd = new LabelNode(new Label());
        LabelNode catchStart = new LabelNode(new Label());
        LabelNode continueLabel = new LabelNode(new Label());

        InsnList tryBlock = new InsnList();

        int currentSlot = tempSlotStart;
        int[] savedSlots = new int[stackTypes.length];
        for (int i = stackTypes.length - 1; i >= 0; i--) {
            savedSlots[i] = currentSlot;
            tryBlock.add(new VarInsnNode(getStoreOpcode(stackTypes[i]), currentSlot));
            currentSlot += stackTypes[i].getSize();
        }

        tryBlock.add(callCode);
        tryBlock.add(new VarInsnNode(ASTORE, baseSlot));

        addLocalUpdateLogic(tryBlock, baseSlot, targetMethod);

        tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
        tryBlock.add(new MethodInsnNode(INVOKEVIRTUAL, "io/byteops/modify/util/InjectResult", "isStop", "()Z", false));
        tryBlock.add(new JumpInsnNode(IFEQ, continueLabel));

        addReturnLogic(tryBlock, baseSlot, methodReturnType);

        InsnList catchBlock = buildCatchBlock(throwableSlot);

        targetMethod.instructions.insertBefore(invokeInsn, tryStart);
        targetMethod.instructions.insertBefore(invokeInsn, tryBlock);
        targetMethod.instructions.insertBefore(invokeInsn, tryEnd);
        targetMethod.instructions.insertBefore(invokeInsn, catchStart);
        targetMethod.instructions.insertBefore(invokeInsn, catchBlock);
        targetMethod.instructions.insertBefore(invokeInsn, continueLabel);

        for (int i = 0; i < stackTypes.length; i++) targetMethod.instructions.insertBefore(invokeInsn, new VarInsnNode(getLoadOpcode(stackTypes[i]), savedSlots[i]));

        targetMethod.tryCatchBlocks.add(new TryCatchBlockNode(tryStart, tryEnd, catchStart, "java/lang/Throwable"));

        targetMethod.maxLocals = Math.max(targetMethod.maxLocals, currentSlot);
        targetMethod.maxStack = Math.max(targetMethod.maxStack, 4);
    }

    private void injectBeforeInsn(MethodNode targetMethod, AbstractInsnNode point, String injectorName, int baseSlot) {
        Type returnType = Type.getReturnType(targetMethod.desc);

        InsnList callCode = buildCallCode(injectorName, targetMethod);

        int throwableSlot = baseSlot + 1;

        LabelNode tryStart = new LabelNode(new Label());
        LabelNode tryEnd = new LabelNode(new Label());
        LabelNode catchStart = new LabelNode(new Label());
        LabelNode continueLabel = new LabelNode(new Label());

        InsnList tryBlock = new InsnList();

        tryBlock.add(callCode);
        tryBlock.add(new VarInsnNode(ASTORE, baseSlot));

        addLocalUpdateLogic(tryBlock, baseSlot, targetMethod);

        tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
        tryBlock.add(new MethodInsnNode(INVOKEVIRTUAL, "io/byteops/modify/util/InjectResult", "isStop", "()Z", false));
        tryBlock.add(new JumpInsnNode(IFEQ, continueLabel));

        addReturnLogic(tryBlock, baseSlot, returnType);

        InsnList catchBlock = buildCatchBlock(throwableSlot);

        targetMethod.instructions.insertBefore(point, tryStart);
        targetMethod.instructions.insertBefore(point, tryBlock);
        targetMethod.instructions.insertBefore(point, tryEnd);
        targetMethod.instructions.insertBefore(point, catchStart);
        targetMethod.instructions.insertBefore(point, catchBlock);
        targetMethod.instructions.insertBefore(point, continueLabel);

        targetMethod.tryCatchBlocks.add(new TryCatchBlockNode(tryStart, tryEnd, catchStart, "java/lang/Throwable"));

        targetMethod.maxLocals = Math.max(targetMethod.maxLocals, throwableSlot + 1);
        targetMethod.maxStack = Math.max(targetMethod.maxStack, 4);
    }

    private void injectBeforeReturn(MethodNode targetMethod, AbstractInsnNode returnInsn, String injectorName, int baseSlot) {
        Type returnType = Type.getReturnType(targetMethod.desc);
        boolean isVoid = returnType == Type.VOID_TYPE;

        InsnList callCode = buildCallCode(injectorName, targetMethod);

        int throwableSlot = baseSlot + 1;
        int returnValueSlot = baseSlot + 2;

        LabelNode tryStart = new LabelNode(new Label());
        LabelNode tryEnd = new LabelNode(new Label());
        LabelNode catchStart = new LabelNode(new Label());
        LabelNode continueLabel = new LabelNode(new Label());

        InsnList tryBlock = new InsnList();

        if (!isVoid) tryBlock.add(new VarInsnNode(getStoreOpcode(returnType), returnValueSlot));

        tryBlock.add(callCode);
        tryBlock.add(new VarInsnNode(ASTORE, baseSlot));

        addLocalUpdateLogic(tryBlock, baseSlot, targetMethod);

        tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
        tryBlock.add(new MethodInsnNode(INVOKEVIRTUAL, "io/byteops/modify/util/InjectResult", "isStop", "()Z", false));
        tryBlock.add(new JumpInsnNode(IFEQ, continueLabel));

        addReturnLogic(tryBlock, baseSlot, returnType);

        InsnList catchBlock = buildCatchBlock(throwableSlot);

        targetMethod.instructions.insertBefore(returnInsn, tryStart);
        targetMethod.instructions.insertBefore(returnInsn, tryBlock);
        targetMethod.instructions.insertBefore(returnInsn, tryEnd);
        targetMethod.instructions.insertBefore(returnInsn, catchStart);
        targetMethod.instructions.insertBefore(returnInsn, catchBlock);
        targetMethod.instructions.insertBefore(returnInsn, continueLabel);

        if (!isVoid) targetMethod.instructions.insertBefore(returnInsn, new VarInsnNode(getLoadOpcode(returnType), returnValueSlot));

        targetMethod.tryCatchBlocks.add(new TryCatchBlockNode(tryStart, tryEnd, catchStart, "java/lang/Throwable"));

        targetMethod.maxLocals = Math.max(targetMethod.maxLocals, returnValueSlot + 1);
        targetMethod.maxStack = Math.max(targetMethod.maxStack, 4);
    }

    private void injectBeforeThrow(MethodNode targetMethod, AbstractInsnNode throwInsn, String injectorName, int baseSlot) {
        Type methodReturnType = Type.getReturnType(targetMethod.desc);

        InsnList callCode = buildCallCode(injectorName, targetMethod);

        int throwableSlot = baseSlot + 1;
        int exceptionSlot = baseSlot + 2;

        LabelNode tryStart = new LabelNode(new Label());
        LabelNode tryEnd = new LabelNode(new Label());
        LabelNode catchStart = new LabelNode(new Label());

        InsnList tryBlock = new InsnList();

        tryBlock.add(new VarInsnNode(ASTORE, exceptionSlot));

        tryBlock.add(callCode);
        tryBlock.add(new VarInsnNode(ASTORE, baseSlot));

        addLocalUpdateLogic(tryBlock, baseSlot, targetMethod);

        tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
        tryBlock.add(new MethodInsnNode(INVOKEVIRTUAL, "io/byteops/modify/util/InjectResult", "isStop", "()Z", false));

        LabelNode restoreLabel = new LabelNode(new Label());
        tryBlock.add(new JumpInsnNode(IFEQ, restoreLabel));

        addReturnLogic(tryBlock, baseSlot, methodReturnType);

        tryBlock.add(restoreLabel);
        tryBlock.add(new VarInsnNode(ALOAD, exceptionSlot));

        InsnList catchBlock = buildCatchBlock(throwableSlot);

        targetMethod.instructions.insertBefore(throwInsn, tryStart);
        targetMethod.instructions.insertBefore(throwInsn, tryBlock);
        targetMethod.instructions.insertBefore(throwInsn, tryEnd);
        targetMethod.instructions.insertBefore(throwInsn, catchStart);
        targetMethod.instructions.insertBefore(throwInsn, catchBlock);

        targetMethod.tryCatchBlocks.add(new TryCatchBlockNode(tryStart, tryEnd, catchStart, "java/lang/Throwable"));

        targetMethod.maxLocals = Math.max(targetMethod.maxLocals, exceptionSlot + 1);
        targetMethod.maxStack = Math.max(targetMethod.maxStack, 4);
    }

    private void addReturnLogic(InsnList tryBlock, int baseSlot, Type returnType) {
        if (returnType == Type.VOID_TYPE) {
            tryBlock.add(new InsnNode(RETURN));
        } else if (returnType.getSort() == Type.OBJECT || returnType.getSort() == Type.ARRAY) {
            tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
            tryBlock.add(new MethodInsnNode(INVOKEVIRTUAL, "io/byteops/modify/util/InjectResult", "getValue", "()Ljava/lang/Object;", false));
            tryBlock.add(new TypeInsnNode(CHECKCAST, returnType.getInternalName()));
            tryBlock.add(new InsnNode(ARETURN));
        } else {
            tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
            tryBlock.add(new MethodInsnNode(INVOKEVIRTUAL, "io/byteops/modify/util/InjectResult", "getValue", "()Ljava/lang/Object;", false));
            String wrapperType = getWrapperType(returnType);
            tryBlock.add(new TypeInsnNode(CHECKCAST, wrapperType));
            tryBlock.add(new MethodInsnNode(INVOKEVIRTUAL, wrapperType, getUnboxMethod(returnType), getUnboxDesc(returnType), false));
            tryBlock.add(new InsnNode(DescriptorFormat.getReturnOpcode(returnType)));
        }
    }

    private InsnList buildCatchBlock(int throwableSlot) {
        InsnList catchBlock = new InsnList();
        catchBlock.add(new VarInsnNode(ASTORE, throwableSlot));
        catchBlock.add(new TypeInsnNode(NEW, "java/lang/RuntimeException"));
        catchBlock.add(new InsnNode(DUP));
        catchBlock.add(new VarInsnNode(ALOAD, throwableSlot));
        catchBlock.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/Throwable;)V", false));
        catchBlock.add(new InsnNode(ATHROW));
        return catchBlock;
    }

    private InsnList buildCallCode(String injectorName, MethodNode targetMethod) {
        InsnList callCode = new InsnList();

        boolean targetStatic = (targetMethod.access & ACC_STATIC) != 0;
        boolean injectorStatic = Modifier.isStatic(method.getModifiers()) || targetStatic;

        if (!targetStatic && !injectorStatic) callCode.add(new VarInsnNode(ALOAD, 0));

        int base = targetStatic ? 0 : 1;

        for (ArgumentParameter ap : argumentParams) {
            int idx = ap.parameter().getAnnotation(Arg.class).value();
            int paramIndex = DescriptorFormat.getParamIndex(targetMethod, idx, base);
            callCode.add(new VarInsnNode(DescriptorFormat.getLoadOpcode(ap.parameter().getType()), paramIndex));
        }

        for (LocalParameter lp : localParameters) {
            int localIndex = lp.parameter().getAnnotation(Local.class).value();
            int rawSlot = resolveLocalSlot(targetMethod, localIndex);
            if (rawSlot == -1) {
                new ModifyInternalException("@Local(" + localIndex + ") not found in " + targetMethod.name).printStackTrace(System.err);
                continue;
            }
            callCode.add(new VarInsnNode(DescriptorFormat.getLoadOpcode(lp.parameter().getType()), rawSlot));
        }

        callCode.add(new MethodInsnNode(
                injectorStatic ? INVOKESTATIC : INVOKEVIRTUAL,
                targetClass.getName().replace('.', '/'),
                injectorName,
                DescriptorFormat.getMethodDescriptor(method),
                false
        ));

        return callCode;
    }

    private int resolveLocalSlot(MethodNode targetMethod, int localIndex) {
        if (targetMethod.localVariables == null || targetMethod.localVariables.isEmpty()) {
            new ModifyInternalException("No LocalVariableTable in " + targetMethod.name + ". Compile with -g or -parameters.").printStackTrace(System.err);
            return -1;
        }

        boolean isStatic = (targetMethod.access & ACC_STATIC) != 0;
        Type[] argTypes = Type.getArgumentTypes(targetMethod.desc);

        int argsEndSlot = isStatic ? 0 : 1;
        for (Type argType : argTypes) argsEndSlot += argType.getSize();

        List<LocalVariableNode> userLocals = new ArrayList<>();
        for (LocalVariableNode lvn : targetMethod.localVariables) if (lvn.index >= argsEndSlot) userLocals.add(lvn);

        Set<Integer> seenSlots = new HashSet<>();
        List<LocalVariableNode> uniqueLocals = new ArrayList<>();
        for (LocalVariableNode lvn : userLocals) if (seenSlots.add(lvn.index)) uniqueLocals.add(lvn);

        uniqueLocals.sort(Comparator.comparingInt(lvn -> lvn.index));

        if (localIndex < 0 || localIndex >= uniqueLocals.size()) {
            new ModifyInternalException("@Local(" + localIndex + ") out of bounds. " + "Available locals: " + uniqueLocals.size()).printStackTrace(System.err);
            return -1;
        }

        return uniqueLocals.get(localIndex).index;
    }

    private boolean isNotInjectorSlotAccess(AbstractInsnNode insn) {
        if (insn instanceof VarInsnNode vin) return vin.var < originalMaxLocals;
        return true;
    }

    private boolean isInjectorReturn(AbstractInsnNode insn) {
        AbstractInsnNode prev = insn.getPrevious();
        while (prev != null) {
            if (prev instanceof MethodInsnNode min) return min.owner.equals("io/byteops/modify/util/InjectResult") && min.name.equals("getValue");
            if (prev instanceof LabelNode) break;
            prev = prev.getPrevious();
        }
        return false;
    }

    private boolean isInjectorNew(AbstractInsnNode insn) {
        if (insn instanceof TypeInsnNode tin && tin.getOpcode() == NEW) return tin.desc.equals("java/lang/RuntimeException");
        return false;
    }

    private boolean isInjectorThrow(AbstractInsnNode insn) {
        if (insn.getOpcode() != ATHROW) return false;
        AbstractInsnNode prev = insn.getPrevious();
        while (prev != null) {
            if (prev instanceof MethodInsnNode min) return min.owner.equals("java/lang/RuntimeException") && min.name.equals("<init>");
            if (prev instanceof LabelNode) break;
            prev = prev.getPrevious();
        }
        return false;
    }

    private boolean isReturnInsn(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return opcode == RETURN || opcode == ARETURN || opcode == IRETURN || opcode == LRETURN || opcode == FRETURN || opcode == DRETURN;
    }

    private boolean isGetInsn(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return opcode == GETFIELD || opcode == GETSTATIC ||
                opcode == ILOAD || opcode == LLOAD || opcode == FLOAD ||
                opcode == DLOAD || opcode == ALOAD;
    }

    private boolean isPutInsn(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return opcode == PUTFIELD || opcode == PUTSTATIC ||
                opcode == ISTORE || opcode == LSTORE || opcode == FSTORE ||
                opcode == DSTORE || opcode == ASTORE;
    }

    private int getStoreOpcode(Type type) {
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) return ASTORE;
        if (type == Type.LONG_TYPE) return LSTORE;
        if (type == Type.DOUBLE_TYPE) return DSTORE;
        if (type == Type.FLOAT_TYPE) return FSTORE;
        return ISTORE;
    }

    private int getLoadOpcode(Type type) {
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) return ALOAD;
        if (type == Type.LONG_TYPE) return LLOAD;
        if (type == Type.DOUBLE_TYPE) return DLOAD;
        if (type == Type.FLOAT_TYPE) return FLOAD;
        return ILOAD;
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
    private boolean isInvokeOrBinaryOp(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return isMethodInvoke(opcode) || isBinaryOp(opcode) || opcode == CHECKCAST || opcode == INSTANCEOF;
    }

    private boolean isMethodInvoke(int opcode) {
        return opcode == INVOKEVIRTUAL || opcode == INVOKESTATIC || opcode == INVOKESPECIAL || opcode == INVOKEINTERFACE || opcode == INVOKEDYNAMIC;
    }

    private boolean isBinaryOp(int opcode) {
        return opcode == IADD || opcode == ISUB || opcode == IMUL || opcode == IDIV || opcode == IREM ||
                opcode == LADD || opcode == LSUB || opcode == LMUL || opcode == LDIV || opcode == LREM ||
                opcode == FADD || opcode == FSUB || opcode == FMUL || opcode == FDIV || opcode == FREM ||
                opcode == DADD || opcode == DSUB || opcode == DMUL || opcode == DDIV || opcode == DREM ||
                opcode == ISHL || opcode == ISHR || opcode == IUSHR ||
                opcode == LSHL || opcode == LSHR || opcode == LUSHR ||
                opcode == IAND || opcode == IOR || opcode == IXOR ||
                opcode == LAND || opcode == LOR || opcode == LXOR;
    }

    private boolean isNewInsn(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return opcode == NEW || opcode == NEWARRAY || opcode == ANEWARRAY || opcode == MULTIANEWARRAY;
    }

    private boolean isThrowInsn(AbstractInsnNode insn) {
        return insn.getOpcode() == ATHROW;
    }

    private boolean isInjectorCall(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode min) {
            if (min.name.startsWith("injector$")) return true;
            if (min.owner.equals("io/byteops/modify/util/InjectResult")) return true;
            return min.owner.equals("java/lang/RuntimeException");
        }
        return false;
    }

    private Type[] getStackTypesBeforeInsn(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode min) return Type.getArgumentTypes(min.desc);
        if (insn instanceof InvokeDynamicInsnNode idin) return Type.getArgumentTypes(idin.desc);
        if (insn instanceof MultiANewArrayInsnNode manain) {
            Type[] types = new Type[manain.dims];
            Arrays.fill(types, Type.INT_TYPE);
            return types;
        }

        int opcode = insn.getOpcode();

        if (opcode == NEW) return new Type[0];
        if (opcode == NEWARRAY || opcode == ANEWARRAY) return new Type[]{Type.INT_TYPE};
        if (opcode == CHECKCAST || opcode == INSTANCEOF) return new Type[]{Type.getType("Ljava/lang/Object;")};
        if (opcode == GETFIELD) {
            if (insn instanceof FieldInsnNode fin) {
                String ownerType = "L" + fin.owner + ";";
                return new Type[]{Type.getType(ownerType)};
            }
            return new Type[]{Type.getType("Ljava/lang/Object;")};
        }
        if (opcode == GETSTATIC) return new Type[0];
        if (opcode == ILOAD || opcode == LLOAD || opcode == FLOAD || opcode == DLOAD || opcode == ALOAD) return new Type[0];
        if (opcode == PUTFIELD) {
            if (insn instanceof FieldInsnNode fin) {
                String ownerType = "L" + fin.owner + ";";
                Type valueType = Type.getType(fin.desc);
                return new Type[]{Type.getType(ownerType), valueType};
            }
            return new Type[]{Type.getType("Ljava/lang/Object;"), Type.INT_TYPE};
        }
        if (opcode == PUTSTATIC) {
            if (insn instanceof FieldInsnNode fin) return new Type[]{Type.getType(fin.desc)};
            return new Type[]{Type.INT_TYPE};
        }
        if (opcode == ISTORE) return new Type[]{Type.INT_TYPE};
        if (opcode == LSTORE) return new Type[]{Type.LONG_TYPE};
        if (opcode == FSTORE) return new Type[]{Type.FLOAT_TYPE};
        if (opcode == DSTORE) return new Type[]{Type.DOUBLE_TYPE};
        if (opcode == ASTORE) return new Type[]{Type.getType("Ljava/lang/Object;")};

        return getBinaryOpOperandTypes(opcode);
    }

    private Type[] getBinaryOpOperandTypes(int opcode) {
        return switch (opcode) {
            case IADD, ISUB, IMUL, IDIV, IREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR -> new Type[]{Type.INT_TYPE, Type.INT_TYPE};
            case LADD, LSUB, LMUL, LDIV, LREM, LAND, LOR, LXOR -> new Type[]{Type.LONG_TYPE, Type.LONG_TYPE};
            case FADD, FSUB, FMUL, FDIV, FREM -> new Type[]{Type.FLOAT_TYPE, Type.FLOAT_TYPE};
            case DADD, DSUB, DMUL, DDIV, DREM -> new Type[]{Type.DOUBLE_TYPE, Type.DOUBLE_TYPE};
            case LSHL, LSHR, LUSHR -> new Type[]{Type.LONG_TYPE, Type.INT_TYPE};
            default -> new Type[0];
        };
    }

    private void addLocalUpdateLogic(InsnList tryBlock, int baseSlot, MethodNode targetMethod) {
        if (localParameters == null) return;

        for (LocalParameter lp : localParameters) {
            int localIndex = lp.parameter().getAnnotation(Local.class).value();
            int rawSlot = resolveLocalSlot(targetMethod, localIndex);
            if (rawSlot == -1) continue;

            Type paramType = Type.getType(lp.parameter().getType());

            tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
            tryBlock.add(new IntInsnNode(SIPUSH, localIndex));
            tryBlock.add(new MethodInsnNode(INVOKEVIRTUAL, "io/byteops/modify/util/InjectResult", "hasLocalUpdate", "(I)Z", false));

            LabelNode skipUpdate = new LabelNode(new Label());
            tryBlock.add(new JumpInsnNode(IFEQ, skipUpdate));

            tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
            tryBlock.add(new IntInsnNode(SIPUSH, localIndex));
            tryBlock.add(new MethodInsnNode(INVOKEVIRTUAL, "io/byteops/modify/util/InjectResult", "getLocalValue", "(I)Ljava/lang/Object;", false));

            if (paramType.getSort() == Type.OBJECT || paramType.getSort() == Type.ARRAY) {
                tryBlock.add(new TypeInsnNode(CHECKCAST, paramType.getInternalName()));
                tryBlock.add(new VarInsnNode(ASTORE, rawSlot));
            } else {
                String wrapperType = getWrapperType(paramType);
                tryBlock.add(new TypeInsnNode(CHECKCAST, wrapperType));
                tryBlock.add(new MethodInsnNode(INVOKEVIRTUAL, wrapperType, getUnboxMethod(paramType), getUnboxDesc(paramType), false));
                tryBlock.add(new VarInsnNode(getStoreOpcode(paramType), rawSlot));
            }

            tryBlock.add(skipUpdate);
        }
    }

    public void preparing(byte[] bytecode, int index) {
        this.injectorIndex = index;

        ClassNode preparedClassNode = new ClassNode();
        new ClassReader(bytecode).accept(preparedClassNode, ClassReader.SKIP_FRAMES);

        MethodReference targetSig = annotation.method();
        String targetDesc = DescriptorFormat.getMethodDescriptor(targetSig);
        preparedTargetMethod = null;

        for (MethodNode method : preparedClassNode.methods) {
            if (method.name.equals(targetSig.method()) && method.desc.equals(targetDesc)) {
                preparedTargetMethod = method;
                break;
            }
        }

        if (preparedTargetMethod == null) {
            new ModifyInternalException("inject target method not found: " + targetSig.method() + targetDesc).printStackTrace(System.err);
            return;
        }

        if ((preparedTargetMethod.access & ACC_ABSTRACT) != 0 || (preparedTargetMethod.access & ACC_NATIVE) != 0) {
            new ModifyInternalException("inject target method is abstract/native: " + targetSig.method() + targetDesc).printStackTrace(System.err);
            return;
        }

        this.originalMaxLocals = preparedTargetMethod.maxLocals;
        preparedPoints = findInjectionPoints(preparedTargetMethod);
    }

    public byte[] inject(byte[] bytecode) {
        if (preparedTargetMethod == null || preparedPoints == null || preparedPoints.isEmpty()) return bytecode;

        ClassNode classNode = new ClassNode();
        new ClassReader(bytecode).accept(classNode, ClassReader.SKIP_FRAMES);

        MethodReference targetSig = annotation.method();
        String targetDesc = DescriptorFormat.getMethodDescriptor(targetSig);
        MethodNode targetMethod = null;

        for (MethodNode method : classNode.methods) {
            if (method.name.equals(targetSig.method()) && method.desc.equals(targetDesc)) {
                targetMethod = method;
                break;
            }
        }

        if (targetMethod == null) return bytecode;

        List<AbstractInsnNode> points = findInjectionPoints(targetMethod);
        if (points.isEmpty()) return bytecode;

        boolean targetStatic = (targetMethod.access & ACC_STATIC) != 0;
        String injectorName = "injector$" + UUID.randomUUID().toString().replace("-", "");
        MethodNode injectorMethod = createInjectorMethodNode(injectorName, targetStatic);
        classNode.methods.add(injectorMethod);

        int baseSlot = originalMaxLocals + injectorIndex * SLOTS_PER_INJECTOR;

        for (AbstractInsnNode point : points) injectAtPoint(targetMethod, point, injectorName, baseSlot);

        for (MethodNode method : classNode.methods) {
            Iterator<AbstractInsnNode> it = method.instructions.iterator();
            while (it.hasNext()) {
                AbstractInsnNode insn = it.next();
                if (insn instanceof FrameNode) it.remove();
            }
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public int getPriority() {
        return annotation.priority();
    }

    public Inject getAnnotation() {
        return annotation;
    }
}