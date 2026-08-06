package io.gammax.internal.util.visitor;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.util.*;

public final class InjectMethodVisitor extends MethodVisitor {
    private final String targetName;
    private final String modifyName;
    private final Map<String, String> fieldMap;
    private final Map<String, String> methodMap;

    public InsnList instructions;
    public final List<TryCatchBlockNode> tryCatchBlocks = new ArrayList<>();
    public final List<LocalVariableNode> localVariables = new ArrayList<>();
    public final List<LineNumberNode> lineNumbers = new ArrayList<>();
    public final InsnList insnList = new InsnList();

    private final Map<Label, LabelNode> labelMap = new HashMap<>();

    public int maxLocals;
    public int maxStack;

    public InjectMethodVisitor(Method method, Class<?> targetClass, Map<String, String> fieldMap, Map<String, String> methodMap) {
        super(Opcodes.ASM9);
        this.targetName = targetClass.getName().replace('.', '/');
        this.modifyName = method.getDeclaringClass().getName().replace('.', '/');
        this.fieldMap = fieldMap;
        this.methodMap = methodMap;
    }

    private LabelNode getLabelNode(Label label) {
        return labelMap.computeIfAbsent(label, LabelNode::new);
    }

    @Override
    public void visitInsn(int opcode) { insnList.add(new InsnNode(opcode)); }

    @Override
    public void visitIntInsn(int opcode, int operand) { insnList.add(new IntInsnNode(opcode, operand)); }

    @Override
    public void visitVarInsn(int opcode, int var) { insnList.add(new VarInsnNode(opcode, var)); }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (type.equals(modifyName)) type = targetName;
        insnList.add(new TypeInsnNode(opcode, type));
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        String key = name + ":" + desc;
        String newOwner = fieldMap.get(key);
        if (newOwner != null) insnList.add(new FieldInsnNode(opcode, newOwner, name, desc));
        else if (owner.equals(modifyName)) insnList.add(new FieldInsnNode(opcode, targetName, name, desc));
        else insnList.add(new FieldInsnNode(opcode, owner, name, desc));
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        String key = name + ":" + desc;
        String newOwner = methodMap.get(key);
        if (newOwner != null) insnList.add(new MethodInsnNode(opcode, newOwner, name, desc, itf));
        else if (owner.equals(modifyName)) insnList.add(new MethodInsnNode(opcode, targetName, name, desc, itf));
        else insnList.add(new MethodInsnNode(opcode, owner, name, desc, itf));
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
        insnList.add(new InvokeDynamicInsnNode(name, desc, bsm, bsmArgs));
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        insnList.add(new JumpInsnNode(opcode, getLabelNode(label)));
    }

    @Override
    public void visitLabel(Label label) {
        insnList.add(getLabelNode(label));
    }

    @Override
    public void visitLdcInsn(Object value) {
        if (value instanceof Type t && t.getInternalName().equals(modifyName)) value = Type.getObjectType(targetName);
        insnList.add(new LdcInsnNode(value));
    }

    @Override
    public void visitIincInsn(int var, int increment) {
        insnList.add(new IincInsnNode(var, increment));
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        LabelNode dfltNode = getLabelNode(dflt);
        LabelNode[] labelNodes = new LabelNode[labels.length];
        for (int i = 0; i < labels.length; i++) labelNodes[i] = getLabelNode(labels[i]);
        insnList.add(new TableSwitchInsnNode(min, max, dfltNode, labelNodes));
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        LabelNode dfltNode = getLabelNode(dflt);
        LabelNode[] labelNodes = new LabelNode[labels.length];
        for (int i = 0; i < labels.length; i++) labelNodes[i] = getLabelNode(labels[i]);
        insnList.add(new LookupSwitchInsnNode(dfltNode, keys, labelNodes));
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
        if (desc.contains(modifyName)) desc = desc.replace(modifyName, targetName);
        insnList.add(new MultiANewArrayInsnNode(desc, dims));
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        if (type != null && type.equals(modifyName)) type = targetName;
        tryCatchBlocks.add(new TryCatchBlockNode(
                getLabelNode(start),
                getLabelNode(end),
                getLabelNode(handler),
                type
        ));
    }

    @Override
    public void visitLineNumber(int line, Label start) {
        lineNumbers.add(new LineNumberNode(line, getLabelNode(start)));
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
        localVariables.add(new LocalVariableNode(
                name, desc, signature,
                getLabelNode(start),
                getLabelNode(end),
                index
        ));
    }

    @Override
    public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {}

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        this.maxStack = maxStack;
        this.maxLocals = maxLocals;
    }

    @Override
    public void visitEnd() {
        instructions = insnList;
    }
}