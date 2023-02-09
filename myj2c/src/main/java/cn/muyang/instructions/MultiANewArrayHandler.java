package cn.muyang.instructions;

import cn.muyang.MethodContext;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MultiANewArrayHandler extends GenericInstructionHandler<MultiANewArrayInsnNode> {
    @Override
    protected void process(MethodContext context, MultiANewArrayInsnNode node) {
        this.instructionName = null;
        context.output.append(this.genCode(context, node));
    }

    @Override
    public String insnToString(MethodContext context, MultiANewArrayInsnNode node) {
        return String.format("MULTIANEWARRAY %d %s", Integer.valueOf(node.dims), node.desc);
    }

    @Override
    public int getNewStackPointer(MultiANewArrayInsnNode node, int currentStackPointer) {
        return currentStackPointer - node.dims + 1;
    }

    private String genCode(MethodContext context, MultiANewArrayInsnNode node) {
        StringBuffer code = new StringBuffer("{\n");
        int dimensions = node.dims;
        code.append( "jsize dim[] = ").append(String.format("{ %s };\n", IntStream.range(0, dimensions).mapToObj((i) -> String.format("cstack%d.i", i)).collect(Collectors.joining(", "))));
        String desc = node.desc;
        desc = desc.substring(1);
        code.append("cstack0.l = (*env)->NewObjectArray(env, dim[0], c_").append(context.getCachedClasses().getId(desc)).append("_(env)->clazz, NULL);\n");
        code.append(this.getSub(context, node, 0, dimensions));
        code.append("};");
        return code.toString();
    }

    private String getSub(MethodContext context, MultiANewArrayInsnNode node, int index, int max) {
        StringBuilder code = new StringBuilder();
        if (index < max - 1) {
            StringBuilder space = new StringBuilder();
            for (int i = 0; i < index + 1; ++i) {
                space.append("    ");
            }
            int next = index + 1;
            code.append(space).append("for(jsize d").append(index).append(" = 0; d").append(index).append(" < dim[").append(index).append("]; d").append(index).append("++) {\n");
            String desc = node.desc;
            for (int i = 0; i < next + 1; ++i) {
                desc = desc.substring(1);
            }
            String s = space.toString();
            switch (Type.getType(desc).getSort()) {
                case 1: {
                    code.append(s).append("            cstack").append(next).append(".l = (*env)->NewBooleanArray(env, dim[").append((int)next).append("]);\n");
                    break;
                }
                case 2: {
                    code.append(s).append("            cstack").append(next).append(".l = (*env)->NewCharArray(env, dim[").append((int)next).append("]);\n");
                    break;
                }
                case 3: {
                    code.append(s).append("            cstack").append(next).append(".l = (*env)->NewByteArray(env, dim[").append((int)next).append("]);\n");
                    break;
                }
                case 4: {
                    code.append(s).append("            cstack").append(next).append(".l = (*env)->NewShortArray(env, dim[").append((int)next).append("]);\n");
                    break;
                }
                case 5: {
                    code.append(s).append("            cstack").append(next).append(".l = (*env)->NewIntArray(env, dim[").append((int)next).append("]);\n");
                    break;
                }
                case 6: {
                    code.append(s).append("            cstack").append(next).append(".l = (*env)->NewFloatArray(env, dim[").append((int)next).append("]);\n");
                    break;
                }
                case 7: {
                    code.append(s).append("            cstack").append(next).append(".l = (*env)->NewLongArray(env, dim[").append((int)next).append("]);\n");
                    break;
                }
                case 8: {
                    code.append(s).append("            cstack").append(next).append(".l = (*env)->NewDoubleArray(env, dim[").append((int)next).append("]);\n");
                    break;
                }
                default: {
                    code.append(s).append("            cstack").append(next).append(".l = (*env)->NewObjectArray(env, dim[").append(next).append("], c_").append(context.getCachedClasses().getId(desc)).append("_(env)->clazz, NULL);\n");
                }
            }
            code.append(space).append("            (*env)->SetObjectArrayElement(env, cstack").append(index).append(".l, d").append(index).append(", cstack").append(next).append(".l);\n");
            code.append(this.getSub(context, node, next, max));
            code.append(space).append("}\n");
        }
        return code.toString();
    }

}
