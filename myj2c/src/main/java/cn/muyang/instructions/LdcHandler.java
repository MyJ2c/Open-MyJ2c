package cn.muyang.instructions;

import cn.muyang.MethodContext;
import cn.muyang.Util;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LdcInsnNode;

public class LdcHandler extends GenericInstructionHandler<LdcInsnNode> {

    public static String getIntString(int value) {
        return value == Integer.MIN_VALUE ? "(jint) 2147483648U" : String.valueOf(value);
    }

    public static String getLongValue(long value) {
        return value == Long.MIN_VALUE ? "(jlong) 9223372036854775808ULL" : String.valueOf(value) + "LL";
    }

    public static String getFloatValue(float value) {
        if (Float.isNaN(value)) {
            return "NAN";
        } else if (value == Float.POSITIVE_INFINITY) {
            return "HUGE_VALF";
        } else if (value == Float.NEGATIVE_INFINITY) {
            return "-HUGE_VALF";
        }
        return value + "f";
    }

    public static String getDoubleValue(double value) {
        if (Double.isNaN(value)) {
            return "NAN";
        } else if (value == Double.POSITIVE_INFINITY) {
            return "HUGE_VAL";
        } else if (value == Double.NEGATIVE_INFINITY) {
            return "-HUGE_VAL";
        }
        return String.valueOf(value);
    }

    @Override
    protected void process(MethodContext context, LdcInsnNode node) {
        boolean stringObf = context.obfuscator.isStringObf();
        Object cst = node.cst;
        if (cst instanceof String) {
            if (node.cst.toString() != null && node.cst.toString().length() > 0) {
                instructionName += "_STRING";
                props.put("cst_ptr", (stringObf ? Util.getStringObf(Util.utf82ints(node.cst.toString())) : "(unsigned short[]) {" + Util.utf82unicode(node.cst.toString()) + "}"));
                props.put("cst_length", "" + node.cst.toString().length());
            } else {
                instructionName += "_STRING_NULL";
            }
        } else if (cst instanceof Integer) {
            instructionName += "_INT";
            props.put("cst", getIntString((Integer) cst));
        } else if (cst instanceof Long) {
            instructionName += "_LONG";
            props.put("cst", getLongValue((Long) cst));
        } else if (cst instanceof Float) {
            instructionName += "_FLOAT";
            props.put("cst", getFloatValue((Float) node.cst));
        } else if (cst instanceof Double) {
            instructionName += "_DOUBLE";
            props.put("cst", getDoubleValue((Double) node.cst));
        } else if (cst instanceof Type) {
            instructionName += "_CLASS";

            props.put("class_ptr", "c_" + context.getCachedClasses().getId(node.cst.toString()) + "_");

            props.put("cst_ptr", context.getCachedClasses().getPointer(node.cst.toString()));
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public String insnToString(MethodContext context, LdcInsnNode node) {
        return String.format("LDC %s", node.cst);
    }

    @Override
    public int getNewStackPointer(LdcInsnNode node, int currentStackPointer) {
        if (node.cst instanceof Double || node.cst instanceof Long) {
            return currentStackPointer + 2;
        }
        return currentStackPointer + 1;
    }

}
