package cn.muyang.instructions;

import cn.muyang.MethodContext;
import cn.muyang.MethodProcessor;
import cn.muyang.Util;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.TypeInsnNode;

public class TypeHandler extends GenericInstructionHandler<TypeInsnNode> {

    @Override
    protected void process(MethodContext context, TypeInsnNode node) {
        props.put("desc", node.desc);

        props.put("class_ptr", "c_" + context.getCachedClasses().getId(node.desc) + "_");

        String instructionName = MethodProcessor.INSTRUCTIONS.getOrDefault(node.getOpcode(), "NOTFOUND");
        if ("CHECKCAST".equals(instructionName)) {
            props.put("exception_ptr", "c_" + context.getCachedClasses().getId("java/lang/ClassCastException") + "_");
        }

        props.put("desc_ptr", node.desc);
    }

    @Override
    public String insnToString(MethodContext context, TypeInsnNode node) {
        return String.format("%s %s", Util.getOpcodeString(node.getOpcode()), node.desc);
    }

    @Override
    public int getNewStackPointer(TypeInsnNode node, int currentStackPointer) {
        switch (node.getOpcode()) {
            case Opcodes.ANEWARRAY:
            case Opcodes.CHECKCAST:
            case Opcodes.INSTANCEOF:
                return currentStackPointer;
            case Opcodes.NEW:
                return currentStackPointer + 1;
        }
        throw new RuntimeException();
    }
}
