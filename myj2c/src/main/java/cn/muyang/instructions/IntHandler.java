package cn.muyang.instructions;

import cn.muyang.MethodContext;
import cn.muyang.Util;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.IntInsnNode;

public class IntHandler extends GenericInstructionHandler<IntInsnNode> {

    @Override
    protected void process(MethodContext context, IntInsnNode node) {
        props.put("operand", String.valueOf(node.operand));
        if (node.getOpcode() == Opcodes.NEWARRAY) {
            instructionName += "_" + node.operand;
        }
    }

    @Override
    public String insnToString(MethodContext context, IntInsnNode node) {
        return String.format("%s %d", Util.getOpcodeString(node.getOpcode()), node.operand);
    }

    @Override
    public int getNewStackPointer(IntInsnNode node, int currentStackPointer) {
        switch (node.getOpcode()) {
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                return currentStackPointer + 1;
            case Opcodes.NEWARRAY:
                return currentStackPointer;
        }
        throw new RuntimeException();
    }
}
