package io.byteops.internal.util.visitor;

import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

@ApiStatus.Internal
public final class ExtendMethodVisitor extends MethodVisitor {
    public InsnList instructions;
    public final List<TryCatchBlockNode> tryCatchBlocks = new ArrayList<>();
    public final List<LocalVariableNode> localVariables = new ArrayList<>();
    public final List<LineNumberNode> lineNumbers = new ArrayList<>();
    public final Map<String, String> fieldMap = new HashMap<>();
    public final Map<String, String> methodMap = new HashMap<>();
    public final InsnList insnList = new InsnList();

    public int maxLocals;
    public int maxStack;

    public ExtendMethodVisitor() {
        super(Opcodes.ASM9);
    }

    @Override
    public void visitCode() {}

    @Override
    public void visitInsn(int opcode) {
        insnList.add(new InsnNode(opcode));
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        insnList.add(new IntInsnNode(opcode, operand));
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        insnList.add(new VarInsnNode(opcode, var));
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        insnList.add(new TypeInsnNode(opcode, type));
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        String key = name + ":" + desc;
        String newOwner = fieldMap.get(key);

        if (newOwner != null) insnList.add(new FieldInsnNode(opcode, newOwner, name, desc));
        else insnList.add(new FieldInsnNode(opcode, owner, name, desc));
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        String key = name + ":" + desc;
        String newOwner = methodMap.get(key);

        if (newOwner != null) insnList.add(new MethodInsnNode(opcode, newOwner, name, desc, itf));
        else insnList.add(new MethodInsnNode(opcode, owner, name, desc, itf));
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
        insnList.add(new InvokeDynamicInsnNode(name, desc, bsm, bsmArgs));
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        insnList.add(new JumpInsnNode(opcode, new LabelNode(label)));
    }

    @Override
    public void visitLabel(org.objectweb.asm.Label label) {
        insnList.add(new LabelNode(label));
    }

    @Override
    public void visitLdcInsn(Object value) {
        insnList.add(new LdcInsnNode(value));
    }

    @Override
    public void visitIincInsn(int var, int increment) {
        insnList.add(new IincInsnNode(var, increment));
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, org.objectweb.asm.Label... labels) {
        LabelNode dfltNode = new LabelNode(dflt);
        LabelNode[] labelNodes = new LabelNode[labels.length];
        for (int i = 0; i < labels.length; i++) labelNodes[i] = new LabelNode(labels[i]);
        insnList.add(new TableSwitchInsnNode(min, max, dfltNode, labelNodes));
    }

    @Override
    public void visitLookupSwitchInsn(org.objectweb.asm.Label dflt, int[] keys, Label[] labels) {
        LabelNode dfltNode = new LabelNode(dflt);
        LabelNode[] labelNodes = new LabelNode[labels.length];
        for (int i = 0; i < labels.length; i++) labelNodes[i] = new LabelNode(labels[i]);
        insnList.add(new LookupSwitchInsnNode(dfltNode, keys, labelNodes));
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
        insnList.add(new MultiANewArrayInsnNode(desc, dims));
    }

    @Override
    public void visitTryCatchBlock(org.objectweb.asm.Label start, org.objectweb.asm.Label end, org.objectweb.asm.Label handler, String type) {
        tryCatchBlocks.add(new TryCatchBlockNode(
                new LabelNode(start),
                new LabelNode(end),
                new LabelNode(handler),
                type
        ));
    }

    @Override
    public void visitLineNumber(int line, Label start) {
        lineNumbers.add(new LineNumberNode(line, new LabelNode(start)));
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature, org.objectweb.asm.Label start, org.objectweb.asm.Label end, int index) {
        localVariables.add(new LocalVariableNode(
                name, desc, signature,
                new LabelNode(start),
                new LabelNode(end),
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
