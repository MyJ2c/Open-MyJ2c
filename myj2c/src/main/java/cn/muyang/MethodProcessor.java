package cn.muyang;

import cn.muyang.instructions.*;
import cn.muyang.special.ClInitSpecialMethodProcessor;
import cn.muyang.special.DefaultSpecialMethodProcessor;
import cn.muyang.special.SpecialMethodProcessor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class MethodProcessor {

    public static final Map<Integer, String> INSTRUCTIONS = new HashMap<>();

    static {
        try {
            for (Field f : Opcodes.class.getFields()) {
                INSTRUCTIONS.put((int) f.get(null), f.getName());
            }
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static final String[] CPP_TYPES = {
            "void", // 0
            "jboolean", // 1
            "jchar", // 2
            "jbyte", // 3
            "jshort", // 4
            "jint", // 5
            "jfloat", // 6
            "jlong", // 7
            "jdouble", // 8
            "jarray", // 9
            "jobject", // 10
            "jobject" // 11
    };

    public static final int[] TYPE_TO_STACK = {
            1, 1, 1, 1, 1, 1, 1, 2, 2, 0, 0, 0
    };

    public static final int[] STACK_TO_STACK = {
            1, 1, 1, 2, 2, 0, 0, 0, 0
    };

    private final MYObfuscator obfuscator;
    private final InstructionHandlerContainer<?>[] handlers;

    public MethodProcessor(MYObfuscator obfuscator) {
        this.obfuscator = obfuscator;

        handlers = new InstructionHandlerContainer[16];
        addHandler(AbstractInsnNode.INSN, new InsnHandler(), InsnNode.class);
        addHandler(AbstractInsnNode.INT_INSN, new IntHandler(), IntInsnNode.class);
        addHandler(AbstractInsnNode.VAR_INSN, new VarHandler(), VarInsnNode.class);
        addHandler(AbstractInsnNode.TYPE_INSN, new TypeHandler(), TypeInsnNode.class);
        addHandler(AbstractInsnNode.FIELD_INSN, new FieldHandler(), FieldInsnNode.class);
        addHandler(AbstractInsnNode.METHOD_INSN, new MethodHandler(), MethodInsnNode.class);
        addHandler(AbstractInsnNode.INVOKE_DYNAMIC_INSN, new InvokeDynamicHandler(), InvokeDynamicInsnNode.class);
        addHandler(AbstractInsnNode.JUMP_INSN, new JumpHandler(), JumpInsnNode.class);
        addHandler(AbstractInsnNode.LABEL, new LabelHandler(), LabelNode.class);
        addHandler(AbstractInsnNode.LDC_INSN, new LdcHandler(), LdcInsnNode.class);
        addHandler(AbstractInsnNode.IINC_INSN, new IincHandler(), IincInsnNode.class);
        addHandler(AbstractInsnNode.TABLESWITCH_INSN, new TableSwitchHandler(), TableSwitchInsnNode.class);
        addHandler(AbstractInsnNode.LOOKUPSWITCH_INSN, new LookupSwitchHandler(), LookupSwitchInsnNode.class);
        addHandler(AbstractInsnNode.MULTIANEWARRAY_INSN, new MultiANewArrayHandler(), MultiANewArrayInsnNode.class);
        addHandler(AbstractInsnNode.FRAME, new FrameHandler(), FrameNode.class);
        addHandler(AbstractInsnNode.LINE, new LineNumberHandler(), LineNumberNode.class);
    }

    private <T extends AbstractInsnNode> void addHandler(int id, InstructionTypeHandler<T> handler, Class<T> instructionClass) {
        handlers[id] = new InstructionHandlerContainer<>(handler, instructionClass);
    }

    private SpecialMethodProcessor getSpecialMethodProcessor(String name) {
        switch (name) {
            case "<init>":
                return null;
            case "<clinit>":
                return new ClInitSpecialMethodProcessor();
            default:
                return new DefaultSpecialMethodProcessor();
        }
    }

    public static boolean shouldProcess(MethodNode method) {
        return !Util.getFlag(method.access, Opcodes.ACC_ABSTRACT) &&
                !Util.getFlag(method.access, Opcodes.ACC_NATIVE) &&
                !method.name.equals("<init>");
    }

    public void processMethod(MethodContext context) {
        MethodNode method = context.method;

        SpecialMethodProcessor specialMethodProcessor = getSpecialMethodProcessor(method.name);

        if ("<clinit>".equals(method.name) && method.instructions.size() == 0) {
            context.obfuscator.getNoInitClassMap().put(context.clazz.name, "1");
            specialMethodProcessor.postProcess(context);
            return;
        }
        StringBuilder output = context.output;

        if (specialMethodProcessor == null) {
            throw new RuntimeException(String.format("Could not find special method processor for %s", method.name));
        }

        output.append("/* " + context.clazz.name + ".").append(Util.escapeCommentString(method.name)).append(Util.escapeCommentString(method.desc)).append("*/");
        output.append("\n");
        specialMethodProcessor.preProcess(context);
/*      String methodName = specialMethodProcessor.preProcess(context);
        methodName = "__ngen_" + methodName.replace('/', '_');*/
        String methodName = Util.escapeCppNameString("myj2c_" + context.methodIndex);
        context.obfuscator.getClassMethodNameMap().put(context.clazz.name + "." + method.name + method.desc, methodName);
        //System.out.println(context.clazz.name + "." + method.name + method.desc + "------------------" + methodName);
        boolean isStatic = Util.getFlag(method.access, Opcodes.ACC_STATIC);
        context.ret = Type.getReturnType(method.desc);
        Type[] args = Type.getArgumentTypes(method.desc);

        context.argTypes = new ArrayList<>(Arrays.asList(args));
        if (!isStatic) {
            context.argTypes.add(0, Type.getType(Object.class));
        }

        //CachedMethodInfo methodInfo = new CachedMethodInfo(context.clazz.getClass().getSimpleName(), context.proxyMethod.name, context.proxyMethod.desc, isStatic);

        //obfuscator.getCachedMethods().getId(methodInfo);

        if (Util.getFlag(context.clazz.access, Opcodes.ACC_INTERFACE)) {
            String targetDesc = String.format("(%s)%s",
                    context.argTypes.stream().map(Type::getDescriptor).collect(Collectors.joining()),
                    context.ret.getDescriptor());

            String outerJavaMethodName = String.format("iface_static_%d_%d", context.classIndex, context.methodIndex);
            context.nativeMethod = new MethodNode(
                    Opcodes.ACC_NATIVE | Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                    outerJavaMethodName, targetDesc, null, new String[0]);

            String methodSource = String.format("            { (char *)%s, (char *)%s, (void *)&%s },\n",
                    outerJavaMethodName,
                    targetDesc, methodName);
            //String m = "/*outerJavaMethodName:" + outerJavaMethodName + " targetDesc:" + targetDesc + " methodName:" + methodName + "*/\n";
            obfuscator.getStaticClassProvider().addMethod(context.nativeMethod, /*m +*/ methodSource);
        } else {

            //String m = "/*outerJavaMethodName2:" + context.proxyMethod.name + " targetDesc2:" + method.desc + " methodName2:" + methodName + "*/\n";
            context.nativeMethods.append(/*m +*/ String.format("            { (char *)%s, (char *)%s, (void *)&%s },\n",
                    context.proxyMethod.name,
                    method.desc, methodName));
        }

        output.append(String.format("%s JNICALL %s(JNIEnv *env, ", CPP_TYPES[context.ret.getSort()], methodName));
        output.append("jobject obj");

        ArrayList<String> argNames = new ArrayList<>();
        if (!isStatic) argNames.add("obj");

        for (int i = 0; i < args.length; i++) {
            argNames.add("arg" + i);
            output.append(String.format(", %s arg%d", CPP_TYPES[args[i].getSort()], i));
        }

        output.append(") {").append("\n");

        /*if (!isStatic) {
            output.append("    jclass clazz = (*env)->CallObjectMethod(env, obj, (*env)->GetMethodID(env, obj, \"getClass\", \"()Ljava/lang/Class;\"));\n");
            output.append("    if ((*env)->ExceptionCheck(env)) { ").append(String.format("return (%s) 0;",
                    CPP_TYPES[context.ret.getSort()])).append(" }\n");
        }*/
        /*output.append("    jobject classloader = (*env)->CallObjectMethod(env, clazz, (*env)->GetMethodID(env, clazz, \"getClassLoader\", \"()Ljava/lang/ClassLoader;\"));\n");
        output.append("    if ((*env)->ExceptionCheck(env) || classloader == NULL) { ").append(String.format("return (%s) 0;",
                CPP_TYPES[context.ret.getSort()])).append(" }\n");*/
        //output.append("\n");
        //output.append("    jobject lookup = NULL;\n");

        if (method.tryCatchBlocks != null) {

            Set<String> classesForTryCatches = method.tryCatchBlocks.stream().filter((tryCatchBlock) -> (tryCatchBlock.type != null)).map(x -> x.type)
                    .collect(Collectors.toSet());
            classesForTryCatches.forEach((clazz) -> {
                int classId = context.getCachedClasses().getId(clazz);
                //context.output.append(String.format("//try-catch-class %s c_%s_\n", Util.escapeCommentString(clazz), classId));
            });

            for (TryCatchBlockNode tryCatch : method.tryCatchBlocks) {
                context.getLabelPool().getName(tryCatch.start.getLabel());
                //context.output.append(String.format("//try-catch-start label %s %s %s\n", tryCatch.type, tryCatch.start.getLabel(), context.getLabelPool().getName(tryCatch.start.getLabel())));
                context.getLabelPool().getName(tryCatch.end.getLabel());
                //context.output.append(String.format("//try-catch-end label %s %s\n", tryCatch.type, tryCatch.end.getLabel(), context.getLabelPool().getName(tryCatch.end.getLabel())));
                context.getLabelPool().getName(tryCatch.handler.getLabel());
                //context.output.append(String.format("//try-catch-handler label %s %s\n", tryCatch.type, tryCatch.handler.getLabel(), context.getLabelPool().getName(tryCatch.handler.getLabel())));
            }

        }


        if (method.maxStack > 0) {
            for (int i = 0; i < method.maxStack; i++) {
                output.append(String.format("jvalue cstack%s; memset(&cstack%s, 0, sizeof(jvalue));\n", i, i));
            }
            output.append("\n");
        } else {
            output.append(String.format("jvalue cstack%s; memset(&cstack%s, 0, sizeof(jvalue));\n", 0, 0));
        }

        if (method.maxLocals > 0) {
            for (int i = 0; i < method.maxLocals; i++) {
                output.append(String.format("jvalue clocal%s; memset(&clocal%s, 0, sizeof(jvalue));\n", i, i));
            }
        } else {
            output.append(String.format("jvalue clocal%s; memset(&clocal%s, 0, sizeof(jvalue));\n", 0, 0));
        }
        output.append("\n");
        //if (method.maxStack > 0 || method.maxLocals > 0) {
            output.append("jvalue temp0; memset(&temp0, 0, sizeof(jvalue));\n");
            output.append("\n");
        //}
        //output.append("\nprintf(\"run : " + methodName + "<" + context.clazz.name + "." + Util.escapeCommentString(method.name) + Util.escapeCommentString(method.desc) + ">\\n\");\n");

        int localIndex = 0;
        for (int i = 0; i < context.argTypes.size(); ++i) {
            Type current = context.argTypes.get(i);
            output.append(obfuscator.getSnippets().getSnippet(
                    "LOCAL_LOAD_ARG_" + current.getSort(), Util.createMap(
                            "index", localIndex,
                            "arg", argNames.get(i)
                    ))).append("\n");
            localIndex += current.getSize();
        }
        if (context.argTypes.size() > 0) {
            output.append("\n");
        }
        context.argTypes.forEach(t -> context.locals.add(TYPE_TO_STACK[t.getSort()]));

        context.stackPointer = 0;

        for (int instruction = 0; instruction < method.instructions.size(); ++instruction) {
            AbstractInsnNode node = method.instructions.get(instruction);
           /* context.output.append("// ").append(Util.escapeCommentString(handlers[node.getType()]
                    .insnToString(context, node))).append("; Stack: ").append(context.stackPointer).append("\n");*/
            handlers[node.getType()].accept(context, node);
            context.stackPointer = handlers[node.getType()].getNewStackPointer(node, context.stackPointer);
            // context.output.append("// New stack: ").append(context.stackPointer).append("\n");
            //output.append("\nprintf(\"run : " + context.stackPointer + "\\n\");\n");
        }

        boolean hasAddedNewBlocks = true;

        Set<CatchesBlock> proceedBlocks = new HashSet<>();

        while (hasAddedNewBlocks) {
            hasAddedNewBlocks = false;
            for (CatchesBlock catchBlock : new ArrayList<>(context.catches.keySet())) {
                if (proceedBlocks.contains(catchBlock)) {
                    continue;
                }
                proceedBlocks.add(catchBlock);
                output.append("    ").append(context.catches.get(catchBlock)).append(": ");
                CatchesBlock.CatchBlock currentCatchBlock = catchBlock.getCatches().get(0);
                if (currentCatchBlock.getClazz() == null) {
                    output.append(context.getSnippets().getSnippet("TRYCATCH_ANY_L", Util.createMap(
                            "handler_block", context.getLabelPool().getName(currentCatchBlock.getHandler().getLabel())
                    )));
                    output.append("\n");
                    continue;
                }
                output.append(context.getSnippets().getSnippet("TRYCATCH_CHECK_STACK", Util.createMap(
                        "exception_class_ptr", context.getCachedClasses().getPointer(currentCatchBlock.getClazz()),
                        "class_ptr", "c_" + context.getCachedClasses().getId(currentCatchBlock.getClazz()) + "_",
                        "handler_block", context.getLabelPool().getName(currentCatchBlock.getHandler().getLabel())
                )));
                output.append("\n");
                if (catchBlock.getCatches().size() == 1) {
                    //output.append("    ");
                    /*output.append(context.getSnippets().getSnippet("TRYCATCH_END_STACK", Util.createMap(
                            "rettype", CPP_TYPES[context.ret.getSort()]
                    )));*/

                    if ("void".equals(MethodProcessor.CPP_TYPES[context.ret.getSort()])) {
                        //tryCatch.append(context.getSnippets().getSnippet("TRYCATCH_VOID", Util.createMap()));
                        output.append(context.getSnippets().getSnippet("TRYCATCH_END_STACK_VOID", Util.createMap()));
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

                        output.append(context.getSnippets().getSnippet("TRYCATCH_END_STACK", Util.createMap(
                                "rettype", type
                        )));
                    }
                    output.append("\n");
                    continue;
                }
                CatchesBlock nextCatchesBlock = new CatchesBlock(catchBlock.getCatches().stream().skip(1).collect(Collectors.toList()));
                if (context.catches.get(nextCatchesBlock) == null) {
                    context.catches.put(nextCatchesBlock, String.format("L_CATCH_%d", context.catches.size()));
                    hasAddedNewBlocks = true;
                }
                output.append("    ");
                output.append(context.getSnippets().getSnippet("TRYCATCH_ANY_L", Util.createMap(
                        "handler_block", context.catches.get(nextCatchesBlock)
                )));
                output.append("\n");
            }
        }

        switch (context.ret.getSort()) {
            case 0:
                output.append("    return ;\n");
                break;
            case 1:
                output.append("    return temp0.z;\n");
                break;
            case 2:
                output.append("    return temp0.c;\n");
                break;
            case 3:
                output.append("    return temp0.b;\n");
                break;
            case 4:
                output.append("    return temp0.s;\n");
                break;
            case 5:
                output.append("    return temp0.i;\n");
                break;
            case 6:
                output.append("    return temp0.f;\n");
                break;
            case 7:
                output.append("    return temp0.j;\n");
                break;
            case 8:
                output.append("    return temp0.d;\n");
                break;
            case 9:
                output.append("    return (jarray)0;\n");
                break;
            case 10:
            case 11:
                output.append("    return temp0.l;\n");
                break;
            default:
                output.append("    return temp0.l;\n");
                break;
        }

        output.append("}\n\n");

        method.localVariables.clear();
        method.tryCatchBlocks.clear();

        specialMethodProcessor.postProcess(context);
    }

    public static String nameFromNode(MethodNode m, ClassNode cn) {
        return cn.name + '#' + m.name + '!' + m.desc;
    }

}
