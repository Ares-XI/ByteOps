package io.gammax.internal.format.functional;

import io.gammax.api.Arg;
import io.gammax.api.Inject;
import io.gammax.api.Local;
import io.gammax.api.util.Signature;
import io.gammax.internal.exeptions.ModifyInternalException;
import io.gammax.internal.format.data.ArgumentParameter;
import io.gammax.internal.format.data.LocalParameter;
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
import java.util.*;

import static org.objectweb.asm.Opcodes.*;

public final class InjectMethod implements FunctionalModifier {
    private final Method method;
    private final Class<?> targetClass;
    private final Inject annotation;
    private final InjectMethodVisitor visitor;
    private final Map<String, String> fieldMap = new HashMap<>();
    private final Map<String, String> methodMap = new HashMap<>();
    private final ArgumentParameter[] argumentParams;
    private final LocalParameter[] localParameters;

    public InjectMethod(Method method, Class<?> targetClass, ProvideField[] provideFields, ExtendField[] extendFields, ProvideMethod[] provideMethods, ExtendMethod[] extendMethods, ArgumentParameter[] argumentParams, LocalParameter[] localParameters) {
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

    private void extractMethodInstructions() {
        byte[] mixinBytes = GammaClassLoader.instance.getClassBytes(method.getDeclaringClass().getName());
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

    private void injectIntoTargetMethod(MethodNode targetMethod, String injectorName) {
        switch (annotation.at()) {
            case HEAD -> injectAtHead(targetMethod, injectorName);
            case RETURN -> injectAtReturn(targetMethod, injectorName);
            default -> new ModifyInternalException("Unsupported At: " + annotation.at()).printStackTrace(System.err);
        }
    }

    private void injectAtHead(MethodNode targetMethod, String injectorName) {
        AbstractInsnNode point = targetMethod.instructions.getFirst();
        if (point == null) {
            new ModifyInternalException("Target method has no instructions: " + targetMethod.name).printStackTrace(System.err);
            return;
        }

        if (targetMethod.name.equals("<init>")) {
            AbstractInsnNode current = point;
            while (current != null) {
                if (current instanceof MethodInsnNode min && min.name.equals("<init>") && min.getOpcode() == INVOKESPECIAL) {
                    point = current.getNext();
                    break;
                }
                current = current.getNext();
            }
            if (point == null) point = targetMethod.instructions.getFirst();
        }

        injectBeforeInsn(targetMethod, point, injectorName);
    }

    private void injectAtReturn(MethodNode targetMethod, String injectorName) {
        int index = annotation.index();

        List<AbstractInsnNode> returnNodes = new ArrayList<>();
        for (AbstractInsnNode insn = targetMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) if (isReturnInsn(insn)) returnNodes.add(insn);

        if (returnNodes.isEmpty()) {
            new ModifyInternalException("no return found in " + targetMethod.name + targetMethod.desc).printStackTrace(System.err);
            return;
        }

        List<AbstractInsnNode> targets;
        if (index == -1) targets = returnNodes;
        else if (index >= 0 && index < returnNodes.size()) targets = List.of(returnNodes.get(index));
        else {
            new ModifyInternalException("return index out of bounds: " + index + " (found " + returnNodes.size() + " returns in " + targetMethod.name + ")").printStackTrace(System.err);
            return;
        }

        for (AbstractInsnNode returnInsn : targets) injectBeforeReturn(targetMethod, returnInsn, injectorName);
    }

    private void injectBeforeInsn(MethodNode targetMethod, AbstractInsnNode point, String injectorName) {
        Type returnType = Type.getReturnType(targetMethod.desc);

        InsnList callCode = buildCallCode(injectorName, targetMethod);

        int baseSlot = targetMethod.maxLocals;
        int throwableSlot = baseSlot + 1;

        LabelNode tryStart = new LabelNode(new Label());
        LabelNode tryEnd = new LabelNode(new Label());
        LabelNode catchStart = new LabelNode(new Label());
        LabelNode continueLabel = new LabelNode(new Label());

        InsnList tryBlock = new InsnList();

        tryBlock.add(callCode);
        tryBlock.add(new VarInsnNode(ASTORE, baseSlot));

        tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
        tryBlock.add(new MethodInsnNode(INVOKEVIRTUAL, "io/gammax/api/util/InjectResult", "isStop", "()Z", false));
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

    private void injectBeforeReturn(MethodNode targetMethod, AbstractInsnNode returnInsn, String injectorName) {
        Type returnType = Type.getReturnType(targetMethod.desc);
        boolean isVoid = returnType == Type.VOID_TYPE;

        InsnList callCode = buildCallCode(injectorName, targetMethod);

        int baseSlot = targetMethod.maxLocals;
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

        tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
        tryBlock.add(new MethodInsnNode(INVOKEVIRTUAL, "io/gammax/api/util/InjectResult", "isStop", "()Z", false));
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

    private void addReturnLogic(InsnList tryBlock, int baseSlot, Type returnType) {
        if (returnType == Type.VOID_TYPE) {
            tryBlock.add(new InsnNode(RETURN));
        } else if (returnType.getSort() == Type.OBJECT || returnType.getSort() == Type.ARRAY) {
            tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
            tryBlock.add(new MethodInsnNode(INVOKEVIRTUAL, "io/gammax/api/util/InjectResult", "getValue", "()Ljava/lang/Object;", false));
            tryBlock.add(new TypeInsnNode(CHECKCAST, returnType.getInternalName()));
            tryBlock.add(new InsnNode(ARETURN));
        } else {
            tryBlock.add(new VarInsnNode(ALOAD, baseSlot));
            tryBlock.add(new MethodInsnNode(INVOKEVIRTUAL, "io/gammax/api/util/InjectResult", "getValue", "()Ljava/lang/Object;", false));
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

    private boolean isReturnInsn(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return opcode == RETURN || opcode == ARETURN || opcode == IRETURN || opcode == LRETURN || opcode == FRETURN || opcode == DRETURN;
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

    @Override
    public byte[] modify(byte[] bytecode) {
        ClassNode classNode = new ClassNode();
        new ClassReader(bytecode).accept(classNode, ClassReader.SKIP_FRAMES);

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
            new ModifyInternalException("inject target method not found: " + annotation.method() + targetDesc).printStackTrace(System.err);
            return bytecode;
        }

        if ((targetMethod.access & ACC_ABSTRACT) != 0 || (targetMethod.access & ACC_NATIVE) != 0) {
            new ModifyInternalException("inject target method is abstract/native: " + annotation.method() + targetDesc).printStackTrace(System.err);
            return bytecode;
        }

        boolean targetStatic = (targetMethod.access & ACC_STATIC) != 0;

        String injectorName = "injector$" + UUID.randomUUID().toString().replace("-", "");
        MethodNode injectorMethod = createInjectorMethodNode(injectorName, targetStatic);
        classNode.methods.add(injectorMethod);

        injectIntoTargetMethod(targetMethod, injectorName);

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
}