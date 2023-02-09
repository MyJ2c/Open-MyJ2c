package cn.muyang;

import cn.muyang.cache.ClassNodeCache;
import cn.muyang.cache.FieldNodeCache;
import cn.muyang.cache.MethodNodeCache;
import cn.muyang.cache.NodeCache;
import cn.muyang.utils.LabelPool;
import cn.muyang.utils.Snippets;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.*;

public class MethodContext {

    public MYObfuscator obfuscator;

    public final MethodNode method;

    public final ClassNode clazz;
    public final int methodIndex;
    public final int classIndex;

    public final StringBuilder output;
    public final StringBuilder nativeMethods;

    public Type ret;
    public ArrayList<Type> argTypes;

    public int line;
    public List<Integer> stack;
    public List<Integer> locals;
    public Set<TryCatchBlockNode> tryCatches;
    public Map<CatchesBlock, String> catches;

    public MethodNode proxyMethod;
    public MethodNode nativeMethod;

    public int stackPointer;

    private final LabelPool labelPool = new LabelPool();

    public MethodContext(MYObfuscator obfuscator, MethodNode method, int methodIndex, ClassNode clazz,
                         int classIndex) {
        this.obfuscator = obfuscator;
        this.method = method;
        this.methodIndex = methodIndex;
        this.clazz = clazz;
        this.classIndex = classIndex;

        this.output = new StringBuilder();
        this.nativeMethods = new StringBuilder();

        this.line = -1;
        this.stack = new ArrayList<>();
        this.locals = new ArrayList<>();
        this.tryCatches = new HashSet<>();
        this.catches = new HashMap<>();
    }

    public NodeCache<String> getCachedStrings() {
        return obfuscator.getCachedStrings();
    }

    public ClassNodeCache getCachedClasses() {
        return obfuscator.getCachedClasses();
    }

    public MethodNodeCache getCachedMethods() {
        return obfuscator.getCachedMethods();
    }

    public FieldNodeCache getCachedFields() {
        return obfuscator.getCachedFields();
    }

    public Snippets getSnippets() {
        return obfuscator.getSnippets();
    }

    public LabelPool getLabelPool() {
        return labelPool;
    }


}
