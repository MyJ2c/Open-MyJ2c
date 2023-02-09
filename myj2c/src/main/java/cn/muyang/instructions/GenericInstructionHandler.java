package cn.muyang.instructions;

import cn.muyang.CatchesBlock;
import cn.muyang.MethodContext;
import cn.muyang.MethodProcessor;
import cn.muyang.Util;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.*;
import java.util.stream.Collectors;

public abstract class GenericInstructionHandler<T extends AbstractInsnNode> implements InstructionTypeHandler<T> {

    protected Map<String, String> props;
    protected String instructionName;
    protected String trimmedTryCatchBlock;

    @Override
    public void accept(MethodContext context, T node) {
        props = new HashMap<>();
        List<TryCatchBlockNode> tryCatchBlockNodeList = new ArrayList<>();
        for (TryCatchBlockNode tryCatchBlock : context.method.tryCatchBlocks) {
            if (!context.tryCatches.contains(tryCatchBlock)) {
                continue;
            }
            if (tryCatchBlockNodeList.stream().noneMatch(tryCatchBlockNode ->
                    Objects.equals(tryCatchBlockNode.type, tryCatchBlock.type))) {
                tryCatchBlockNodeList.add(tryCatchBlock);
            }
        }
        instructionName = MethodProcessor.INSTRUCTIONS.getOrDefault(node.getOpcode(), "NOTFOUND");
        props.put("line", String.valueOf(context.line));
        StringBuilder tryCatch = new StringBuilder("\n");
        //tryCatch.append("    ");
        if (tryCatchBlockNodeList.size() > 0) {
            String tryCatchLabelName = context.catches.computeIfAbsent(new CatchesBlock(tryCatchBlockNodeList.stream().map(item ->
                            new CatchesBlock.CatchBlock(item.type, item.handler)).collect(Collectors.toList())),
                    key -> String.format("L_CATCH_%d", context.catches.size()));
            tryCatch.append("if ((*env)->ExceptionCheck(env)) { " + "\n");
            tryCatch.append("   cstack0.l = (*env)->ExceptionOccurred(env);\n");
            for (TryCatchBlockNode tryCatchBlockNode : tryCatchBlockNodeList) {
                if (tryCatchBlockNode.type != null) {
                    //tryCatch.append("   if ((*env)->IsInstanceOf(env, cstack0.l, /*" + tryCatchBlockNode.type + "*/c_" + context.obfuscator.getCachedClasses().getId(tryCatchBlockNode.type) + "_(env)->clazz)) {\n");
                    tryCatch.append("   if ((*env)->IsInstanceOf(env, cstack0.l, c_" + context.obfuscator.getCachedClasses().getId(tryCatchBlockNode.type) + "_(env)->clazz)) {\n");
                    tryCatch.append("       (*env)->ExceptionClear(env);\n");
                    tryCatch.append("       goto ").append(tryCatchLabelName).append(";\n  }\n");
                }
            }
            tryCatch.append("       (*env)->ExceptionClear(env);\n");
            tryCatch.append("       goto ").append(tryCatchLabelName).append(";\n");
            tryCatch.append("}\n");
        } else {
            if ("void".equals(MethodProcessor.CPP_TYPES[context.ret.getSort()])) {
                tryCatch.append(context.getSnippets().getSnippet("TRYCATCH_VOID", Util.createMap()));
            } else {
                String type = "";
                switch (context.ret.getSort()) {
                    case Type.ARRAY:
                    case Type.OBJECT:
                        type = "l";
                        break;
                    case Type.BOOLEAN:
                        type = "z";
                        break;
                    case Type.BYTE:
                        type = "b";
                        break;
                    case Type.CHAR:
                        type = "c";
                        break;
                    case Type.DOUBLE:
                        type = "d";
                        break;
                    case Type.FLOAT:
                        type = "f";
                        break;
                    case Type.INT:
                        type = "i";
                        break;
                    case Type.LONG:
                        type = "j";
                        break;
                    case Type.SHORT:
                        type = "s";
                        break;
                    default:
                        type = "l";
                }
                tryCatch.append(context.getSnippets().getSnippet("TRYCATCH_EMPTY", Util.createMap(
                        "rettype", type
                )));
            }
        }
        props.put("trycatchhandler", tryCatch.toString());
        props.put("rettype", MethodProcessor.CPP_TYPES[context.ret.getSort()]);
        switch (context.ret.getSort()) {
            case 0:
                props.put("retvalue", " return ;");
                break;
            case 1:
                props.put("retvalue", " return temp0.z;");
                break;
            case 2:
                props.put("retvalue", " return temp0.c;");
                break;
            case 3:
                props.put("retvalue", " return temp0.b;");
                break;
            case 4:
                props.put("retvalue", " return temp0.s;");
                break;
            case 5:
                props.put("retvalue", " return temp0.i;");
                break;
            case 6:
                props.put("retvalue", " return temp0.f;");
                break;
            case 7:
                props.put("retvalue", " return temp0.j;");
                break;
            case 8:
                props.put("retvalue", " return temp0.d;");
                break;
            case 9:
                props.put("retvalue", " return (jarray)0;");
                break;
            case 10:
            case 11:
                props.put("retvalue", " return temp0.l;");
                break;
            default:
                props.put("retvalue", " return temp0.l;");
                break;
        }
        trimmedTryCatchBlock = tryCatch.toString().trim().replace('\n', ' ');

        for (int i = -5; i <= 5; i++) {
            props.put("stackindex" + (i >= 0 ? i : "m" + (-i)), String.valueOf(context.stackPointer + i));
        }

        //context.output.append("    ");
        process(context, node);

        if (instructionName != null) {
//            String m = "/*GenericInstructionHandler:" + instructionName + "," + node + "*/";;
//            if (node instanceof MethodInsnNode) {
//                MethodInsnNode n = (MethodInsnNode)node;
//                m = "/*GenericInstructionHandler:" + instructionName + ",MethodInsnNode:" + n.name+","+n.desc + ","+n.getType()+ "*/";
//            }
//            if (node instanceof VarInsnNode) {
//                VarInsnNode n = (VarInsnNode)node;
//                m = "/*GenericInstructionHandler:" + instructionName + ",VarInsnNode:" + n.var+","+n.getType() + "*/";
//            }
//            if (node instanceof JumpInsnNode) {
//                JumpInsnNode n = (JumpInsnNode)node;
//                m = "/*GenericInstructionHandler:" + instructionName + ",JumpInsnNode:" + n.label.getLabel()+"@"+n.label.getType()+","+n.getType() + "*/";
//            }
//            if (node instanceof InsnNode) {
//                InsnNode n = (InsnNode)node;
//                m = "/*GenericInstructionHandler:" + instructionName + ",InsnNode:" + n.getType()+ "*/";
//            }
//            if (node instanceof LdcInsnNode) {
//                LdcInsnNode n = (LdcInsnNode)node;
//                m = "/*GenericInstructionHandler:" + instructionName + ",LdcInsnNode:" + n.cst.toString() + "," + n.getOpcode() + "," + n.getType() + "*/";
//            }
//            if (node instanceof LabelNode) {
//                LabelNode n = (LabelNode)node;
//                m = "/*GenericInstructionHandler:" + instructionName + ",LabelNode:" + n.getLabel() + "," + n.getType() + "*/";
//            }
//            if (node instanceof TypeInsnNode) {
//                TypeInsnNode n = (TypeInsnNode)node;
//                m = "/*GenericInstructionHandler:" + instructionName + ",TypeInsnNode:" + n.desc + "," + n.getType() +","+n.getOpcode()+ "*/";
//            }
//            if (node instanceof FieldInsnNode) {
//                FieldInsnNode n = (FieldInsnNode)node;
//                m = "/*GenericInstructionHandler:" + instructionName + ",FieldInsnNode:" + n.name + "," + n.desc + "," + n.getType() + "*/";
//            }
//            if (node instanceof IincInsnNode) {
//                IincInsnNode n = (IincInsnNode)node;
//                m = "/*GenericInstructionHandler:" + instructionName + ",FieldInsnNode:" + n.var + "," + n.incr + "," + n.getType() + "*/";
//            }
//            if (node instanceof IntInsnNode) {
//                IntInsnNode n = (IntInsnNode)node;
//                m = "/*GenericInstructionHandler:" + instructionName + ",FieldInsnNode:" + n.operand + ","  + n.getType() + "*/";
//            }
//            context.output.append("\n"+m +"\n"+ context.obfuscator.getSnippets().getSnippet(instructionName, props));
            if ("ATHROW".equals(instructionName)) {
                props.put("class_ptr", "c_" + context.getCachedClasses().getId("java/lang/NullPointerException") + "_");
            }
            if ("NEW".equals(instructionName) && tryCatchBlockNodeList.size() > 0) {
                instructionName = "NEW_CATCH";
            }
/*            if ("<clinit>".equals(context.method.name) && "IDIV".equals(instructionName)) {
                instructionName = "IDIV_STATIC";
                props.put("class_ptr","c_"+context.getCachedClasses().getId("java/lang/ExceptionInInitializerError")+"_");
            }*/
            context.output.append(context.obfuscator.getSnippets().getSnippet(instructionName, props));
        }
        context.output.append("\n");
    }

    protected abstract void process(MethodContext context, T node);
}
