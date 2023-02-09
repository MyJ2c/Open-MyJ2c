package cn.muyang.special;

import cn.muyang.MethodContext;
import cn.muyang.Util;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public class ClInitSpecialMethodProcessor implements SpecialMethodProcessor {

    @Override
    public String preProcess(MethodContext context) {
        //String name = String.format("%s_special_clinit%d", context.obfuscator.getNativeDir(), context.methodIndex);
        String name = "$myj2cClinit";
        if (!Util.getFlag(context.clazz.access, Opcodes.ACC_INTERFACE)) {
            context.proxyMethod = new MethodNode(
                    Opcodes.ACC_NATIVE | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                    name, context.method.desc, context.method.signature, new String[0]);
            context.clazz.methods.add(context.proxyMethod);
        }
        return name;
    }

    @Override
    public void postProcess(MethodContext context) {
        InsnList instructions = context.method.instructions;
        instructions.clear();
        instructions.add(new LdcInsnNode(context.classIndex));
        instructions.add(new LdcInsnNode(Type.getObjectType(context.clazz.name)));
        instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, context.obfuscator.getNativeDir() + "/Loader",
                "registerNativesForClass", "(ILjava/lang/Class;)V", false));

        instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, context.clazz.name,
                "$myj2cLoader", "()V", false));

        if (Util.getFlag(context.clazz.access, Opcodes.ACC_INTERFACE)) {
            if (context.nativeMethod == null) {
                throw new RuntimeException("Native method not created?!");
            }
            instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    context.obfuscator.getStaticClassProvider().getCurrentClassName(),
                    context.nativeMethod.name, context.nativeMethod.desc, false));
        } else {
            if (!context.obfuscator.getNoInitClassMap().containsKey(context.clazz.name)) {
                instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, context.clazz.name,
                        //String.format("%s_special_clinit%d", context.obfuscator.getNativeDir(), context.methodIndex), context.method.desc, false));
                        "$myj2cClinit", context.method.desc, false));
            }
        }

        instructions.add(new InsnNode(Opcodes.RETURN));
    }
}
