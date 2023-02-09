package cn.muyang.instructions;

import cn.muyang.MethodContext;
import cn.muyang.Util;
import cn.muyang.cache.CachedClassInfo;
import cn.muyang.cache.CachedFieldInfo;
import cn.muyang.cache.CachedMethodInfo;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class MethodHandler extends GenericInstructionHandler<MethodInsnNode> {

    private static Type simplifyType(Type type) {
        switch (type.getSort()) {
            case Type.OBJECT:
            case Type.ARRAY:
                return Type.getObjectType("java/lang/Object");
            case Type.METHOD:
                throw new RuntimeException();
        }
        return type;
    }

    private static String simplifyDesc(String desc) {
        return Type.getMethodType(simplifyType(Type.getReturnType(desc)), Arrays.stream(Type.getArgumentTypes(desc))
                .map(MethodHandler::simplifyType).toArray(Type[]::new)).getDescriptor();
    }

    @Override
    protected void process(MethodContext context, MethodInsnNode node) {
        boolean stringObf = context.obfuscator.isStringObf();
        //System.out.println(node.owner+","+node.getType()+","+node.name+","+node.desc);
        if (node.owner.equals("java/lang/invoke/MethodHandle") &&
                (node.name.equals("invokeExact") || node.name.equals("invoke")) &&
                node.getOpcode() == Opcodes.INVOKEVIRTUAL) {

            //System.out.println(node.owner+node.getType()+node.name+node.desc);
            // stack - mh, args
            String methodDesc = simplifyDesc(Type.getMethodType(Type.getReturnType(node.desc),
                    Stream.concat(Arrays.stream(new Type[]{
                            Type.getObjectType("java/lang/invoke/MethodHandle")
                    }), Arrays.stream(Type.getArgumentTypes(node.desc))).toArray(Type[]::new)).getDescriptor());
            Type[] methodArguments = Type.getArgumentTypes(methodDesc);
            methodArguments[0] = Type.getObjectType("java/lang/invoke/MethodHandle");
            methodDesc = Type.getMethodDescriptor(Type.getReturnType(methodDesc), methodArguments);
            String mhDesc = simplifyDesc(node.desc);
            //context.output.append("printf(\"main run "+System.currentTimeMillis()+"\\n\");");
            context.output.append("temp0.l = (*env)->NewObjectArray(env, " + Type.getArgumentTypes(mhDesc).length + ", c_" + context.getCachedClasses().getClass("java/lang/Object").getId() + "_(env)->clazz, NULL);\n");
            //context.output.append("(*env)->SetObjectArrayElement(env, temp1.l, 0, cstack1.l);");
            for (int i = 0; i < Type.getArgumentTypes(mhDesc).length; i++) {
                Type argumentType = Type.getArgumentTypes(mhDesc)[i];
                switch (argumentType.getSort()) {
                    case Type.BOOLEAN:
                    case Type.CHAR:
                    case Type.BYTE:
                    case Type.INT:
                        CachedClassInfo integer = context.getCachedClasses().getClass("java/lang/Integer");
                        context.output.append("(*env)->SetObjectArrayElement(env, temp0.l, " + i + ", (*env)->CallStaticObjectMethod(env, c_" + integer.getId() + "_(env)->clazz, c_" + integer.getId() + "_(env)->method_" + integer.getCachedMethodId(new CachedMethodInfo("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", true)) + ", cstack" + (1 + i) + ".l));\n");
                        break;
                    case Type.FLOAT:
                        CachedClassInfo jfloat = context.getCachedClasses().getClass("java/lang/Float");
                        context.output.append("(*env)->SetObjectArrayElement(env, temp0.l, " + i + ", (*env)->CallStaticObjectMethod(env, c_" + jfloat.getId() + "_(env)->clazz, c_" + jfloat.getId() + "_(env)->method_" + jfloat.getCachedMethodId(new CachedMethodInfo("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", true)) + ", cstack" + (1 + i) + ".l));\n");
                        break;
                    case Type.LONG:
                        CachedClassInfo jlong = context.getCachedClasses().getClass("java/lang/Long");
                        context.output.append("(*env)->SetObjectArrayElement(env, temp0.l, " + i + ", (*env)->CallStaticObjectMethod(env, c_" + jlong.getId() + "_(env)->clazz, c_" + jlong.getId() + "_(env)->method_" + jlong.getCachedMethodId(new CachedMethodInfo("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", true)) + ", cstack" + (1 + i) + ".l));\n");
                        break;
                    case Type.DOUBLE:
                        CachedClassInfo jdouble = context.getCachedClasses().getClass("java/lang/Double");
                        context.output.append("(*env)->SetObjectArrayElement(env, temp0.l, " + i + ", (*env)->CallStaticObjectMethod(env, c_" + jdouble.getId() + "_(env)->clazz, c_" + jdouble.getId() + "_(env)->method_" + jdouble.getCachedMethodId(new CachedMethodInfo("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", true)) + ", cstack" + (1 + i) + ".l));\n");
                        break;
                    case Type.ARRAY:
                    case Type.OBJECT:
                        context.output.append("(*env)->SetObjectArrayElement(env, temp0.l, " + i + ", cstack" + (1 + i) + ".l);\n");
                        break;
                    case Type.METHOD:
                        CachedClassInfo clazz = context.getCachedClasses().getClass(context.clazz.name);
                        CachedClassInfo javaClass = context.getCachedClasses().getClass("java/lang/Class");
                        CachedClassInfo methodType = context.getCachedClasses().getClass("java/lang/invoke/MethodType");
                        context.output.append("(*env)->SetObjectArrayElement(env, temp0.l, " + i + ", (*env)->CallStaticObjectMethod(env, /*java/lang/invoke/MethodType*/c_" + methodType.getId() + "_(env)->clazz, /*fromMethodDescriptorString*/c_" + methodType.getId() + "_(env)->method_" + methodType.getCachedMethodId(new CachedMethodInfo("java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", true)) + ", /*" + argumentType + "*/(*env)->NewString(env, " + (stringObf?Util.getStringObf(Util.utf82ints(argumentType.toString())) :"(unsigned short[]) {"+Util.utf82unicode(argumentType.toString())+"}") + ", " + argumentType.toString().length() + "), (*env)->CallObjectMethod(env,/*" + context.clazz.name + "*/c_" + clazz.getId() + "_(env)->clazz, /*java/lang/Class.getClassLoader*/c_" + javaClass.getId() + "_(env)->method_" + javaClass.getCachedMethodId(new CachedMethodInfo("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false)) + ")));\n");
                        break;
                    default:
                        context.output.append("(*env)->SetObjectArrayElement(env, temp0.l, " + i + ", cstack" + (1 + i) + ".l);\n");
                        break;
                }
            }
            CachedClassInfo methodHandle = context.getCachedClasses().getClass("java/lang/invoke/MethodHandle");
            if (Type.getReturnType(mhDesc).getSize() == 0) {
                context.output.append("(*env)->CallObjectMethod(env, cstack0.l, c_" + methodHandle.getId() + "_(env)->method_" + methodHandle.getCachedMethodId(new CachedMethodInfo("java/lang/invoke/MethodHandle", "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;", false)) + ", temp0.l);\n");
            } else {
                Type returnType = Type.getReturnType(mhDesc);
                switch (returnType.getSort()) {
                    case Type.BOOLEAN:
                        CachedClassInfo jbool = context.getCachedClasses().getClass("java/lang/Boolean");
                        context.output.append("cstack0.z = (*env)->CallBooleanMethod(env, (*env)->CallObjectMethod(env, cstack0.l, c_" + methodHandle.getId() + "_(env)->method_" + methodHandle.getCachedMethodId(new CachedMethodInfo("java/lang/invoke/MethodHandle", "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;", false)) + ", temp0.l),c_" + jbool.getId() + "_(env)->method_" + jbool.getCachedMethodId(new CachedMethodInfo("java/lang/Boolean", "booleanValue", "()Z", false)) + ");\n");
                        break;
                    case Type.CHAR:
                        CachedClassInfo jchar = context.getCachedClasses().getClass("java/lang/Character");
                        context.output.append("cstack0.c = (*env)->CallCharMethod(env, (*env)->CallObjectMethod(env, cstack0.l, c_" + methodHandle.getId() + "_(env)->method_" + methodHandle.getCachedMethodId(new CachedMethodInfo("java/lang/invoke/MethodHandle", "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;", false)) + ", temp0.l),c_" + jchar.getId() + "_(env)->method_" + jchar.getCachedMethodId(new CachedMethodInfo("java/lang/Character", "charValue", "()C", false)) + ");\n");
                        break;
                    case Type.BYTE:
                        CachedClassInfo jbyte = context.getCachedClasses().getClass("java/lang/Byte");
                        context.output.append("cstack0.b = (*env)->CallByteMethod(env, (*env)->CallObjectMethod(env, cstack0.l, c_" + methodHandle.getId() + "_(env)->method_" + methodHandle.getCachedMethodId(new CachedMethodInfo("java/lang/invoke/MethodHandle", "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;", false)) + ", temp0.l),c_" + jbyte.getId() + "_(env)->method_" + jbyte.getCachedMethodId(new CachedMethodInfo("java/lang/Byte", "byteValue", "()B", false)) + ");\n");
                        break;
                    case Type.INT:
                        CachedClassInfo jint = context.getCachedClasses().getClass("java/lang/Integer");
                        context.output.append("cstack0.i = (*env)->CallIntMethod(env, (*env)->CallObjectMethod(env, cstack0.l, c_" + methodHandle.getId() + "_(env)->method_" + methodHandle.getCachedMethodId(new CachedMethodInfo("java/lang/invoke/MethodHandle", "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;", false)) + ", temp0.l),c_" + jint.getId() + "_(env)->method_" + jint.getCachedMethodId(new CachedMethodInfo("java/lang/Integer", "intValue", "()I", false)) + ");\n");
                        break;
                    case Type.FLOAT:
                        CachedClassInfo jfloat = context.getCachedClasses().getClass("java/lang/Float");
                        context.output.append("cstack0.f = (*env)->CallFloatMethod(env, (*env)->CallObjectMethod(env, cstack0.l, c_" + methodHandle.getId() + "_(env)->method_" + methodHandle.getCachedMethodId(new CachedMethodInfo("java/lang/invoke/MethodHandle", "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;", false)) + ", temp0.l),c_" + jfloat.getId() + "_(env)->method_" + jfloat.getCachedMethodId(new CachedMethodInfo("java/lang/Float", "floatValue", "()F", false)) + ");\n");
                        break;
                    case Type.LONG:
                        CachedClassInfo jlong = context.getCachedClasses().getClass("java/lang/Long");
                        context.output.append("cstack0.j = (*env)->CallLongMethod(env, (*env)->CallObjectMethod(env, cstack0.l, c_" + methodHandle.getId() + "_(env)->method_" + methodHandle.getCachedMethodId(new CachedMethodInfo("java/lang/invoke/MethodHandle", "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;", false)) + ", temp0.l),c_" + jlong.getId() + "_(env)->method_" + jlong.getCachedMethodId(new CachedMethodInfo("java/lang/Long", "longValue", "()J", false)) + ");\n");
                        break;
                    case Type.DOUBLE:
                        CachedClassInfo jdouble = context.getCachedClasses().getClass("java/lang/Double");
                        context.output.append("cstack0.d = (*env)->CallDoubleMethod(env, (*env)->CallObjectMethod(env, cstack0.l, c_" + methodHandle.getId() + "_(env)->method_" + methodHandle.getCachedMethodId(new CachedMethodInfo("java/lang/invoke/MethodHandle", "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;", false)) + ", temp0.l),c_" + jdouble.getId() + "_(env)->method_" + jdouble.getCachedMethodId(new CachedMethodInfo("java/lang/Double", "doubleValue", "()D", false)) + ");\n");
                        break;
                    case Type.ARRAY:
                    case Type.OBJECT:
                        context.output.append("cstack0.l = (*env)->CallObjectMethod(env, cstack0.l, c_" + methodHandle.getId() + "_(env)->method_" + methodHandle.getCachedMethodId(new CachedMethodInfo("java/lang/invoke/MethodHandle", "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;", false)) + ", temp0.l);\n");
                        break;
                    case Type.METHOD:
                        break;
                    default:
                        context.output.append("cstack0.l = (*env)->CallObjectMethod(env, cstack0.l, c_" + methodHandle.getId() + "_(env)->method_" + methodHandle.getCachedMethodId(new CachedMethodInfo("java/lang/invoke/MethodHandle", "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;", false)) + ", temp0.l);\n");
                        break;
                }
            }
            context.output.append(props.get("trycatchhandler"));
            return;
        }

        props.put("class_ptr", "c_" + context.getCachedClasses().getId(node.owner) + "_");
        CachedClassInfo classInfo = context.getCachedClasses().getClass(node.owner);
        List<CachedFieldInfo> cachedFields = classInfo.getCachedFields();
        for (int i = 0; i < cachedFields.size(); i++) {
            CachedFieldInfo fieldNode = cachedFields.get(i);
            if (fieldNode.getName().equals(node.name)) {
                //System.out.println("field_id:" + "=====" +node.name+"," + "id_" + i +","+node.owner);
                props.put("field_id", "id_" + i);
            }
        }

        Type returnType = Type.getReturnType(node.desc);
        Type[] args = Type.getArgumentTypes(node.desc);
        instructionName += "_" + returnType.getSort();

        StringBuilder argsBuilder = new StringBuilder();
        List<Integer> argOffsets = new ArrayList<>();

        int stackOffset = context.stackPointer;
        for (Type argType : args) {
            stackOffset -= argType.getSize();
        }
        int argumentOffset = stackOffset;
        for (Type argType : args) {
            argOffsets.add(argumentOffset);
            argumentOffset += argType.getSize();
        }

        boolean isStatic = node.getOpcode() == Opcodes.INVOKESTATIC;
        int objectOffset = isStatic ? 0 : 1;

        for (int i = 0; i < argOffsets.size(); i++) {
            argsBuilder.append(", ").append(context.getSnippets().getSnippet("INVOKE_ARG_" + args[i].getSort(),
                    Util.createMap("index", argOffsets.get(i))));
        }

        props.put("objectstackindex", String.valueOf(stackOffset - objectOffset));
        props.put("returnstackindex", String.valueOf(stackOffset - objectOffset));

        List<CachedMethodInfo> cachedMethods = classInfo.getCachedMethods();
        for (int i = 0; i < cachedMethods.size(); i++) {
            CachedMethodInfo cachedMethodInfo = cachedMethods.get(i);
            if (cachedMethodInfo.getName().equals(node.name) && cachedMethodInfo.getDesc().equals(node.desc)) {
                props.put("methodid", "method_" + i);
            }
        }
        if (props.get("methodid") == null) {
            CachedMethodInfo methodInfo = new CachedMethodInfo(node.owner, node.name, node.desc, isStatic);
            methodInfo.setId(cachedMethods.size());
            cachedMethods.add(methodInfo);
            //System.out.println("add:=========" + node.owner + node.name + methodInfo);
            props.put("methodid", "method_" + (cachedMethods.size() - 1));
        }

        props.put("args", argsBuilder.toString());
    }

    @Override
    public String insnToString(MethodContext context, MethodInsnNode node) {
        return String.format("%s %s.%s%s", Util.getOpcodeString(node.getOpcode()), node.owner, node.name, node.desc);
    }

    @Override
    public int getNewStackPointer(MethodInsnNode node, int currentStackPointer) {
        if (node.getOpcode() != Opcodes.INVOKESTATIC) {
            currentStackPointer -= 1;
        }
        return currentStackPointer - Arrays.stream(Type.getArgumentTypes(node.desc)).mapToInt(Type::getSize).sum()
                + Type.getReturnType(node.desc).getSize();
    }
}
