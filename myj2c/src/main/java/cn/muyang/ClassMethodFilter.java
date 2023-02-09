package cn.muyang;

import cn.muyang.nativeobfuscator.Native;
import cn.muyang.nativeobfuscator.NotNative;
import cn.muyang.utils.AntPathMatcher;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class ClassMethodFilter {
    private static final String NATIVE_ANNOTATION_DESC = Type.getDescriptor(Native.class);
    private static final String NOT_NATIVE_ANNOTATION_DESC = Type.getDescriptor(NotNative.class);

    private final List<String> blackList;
    private final List<String> whiteList;
    private final boolean useAnnotations;

    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ClassMethodFilter(List<String> blackList, List<String> whiteList, boolean useAnnotations) {
        this.blackList = blackList;
        this.whiteList = whiteList;
        this.useAnnotations = useAnnotations;
    }

    public boolean shouldProcess(ClassNode classNode) {
        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            return false;
        }
        if (!Util.isValidJavaFullClassName(classNode.name.replaceAll("/", "."))) {
            return false;
        }
        if (this.blackList != null) {
            for (String black : this.blackList) {
                if (!black.contains("#")) {
                    if (pathMatcher.matchStart(black, classNode.name)) {
                        return false;
                    }
                }
            }
        }

        boolean toMethod = false;

        if (this.whiteList != null && this.whiteList.size() > 0) {
            for (String white : this.whiteList) {
                if (!white.contains("#")) {
                    if (pathMatcher.matchStart(white, classNode.name)) {
                        return true;
                    }
                } else {
                    String whiteClass = white.split("#")[0];
                    if (pathMatcher.matchStart(whiteClass, classNode.name)) {
                        toMethod = true;
                    }
                }
            }
            if (!toMethod) {
                return false;
            }
        }

        if (useAnnotations) {
            if (classNode.invisibleAnnotations != null &&
                    classNode.invisibleAnnotations.stream().anyMatch(annotationNode ->
                            annotationNode.desc.equals(NATIVE_ANNOTATION_DESC))) {
                return true;
            }
        }
        return classNode.methods.stream().anyMatch(methodNode -> this.shouldProcess(classNode, methodNode));
    }

    public boolean shouldProcess(ClassNode classNode, MethodNode methodNode) {
        if (this.blackList != null) {
            for (String black : this.blackList) {
                if (black.contains("#")) {
                    String blackClass = black.split("#")[0];
                    if (pathMatcher.matchStart(blackClass, classNode.name)) {
                        String blackMethod = black.split("#")[1];
                        if (blackMethod.contains("!")) {
                            if (pathMatcher.matchStart(blackMethod, methodNode.name + '!' + methodNode.desc)) {
                                return false;
                            }
                        } else {
                            if (pathMatcher.matchStart(blackMethod, methodNode.name)) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        if (this.whiteList != null && this.whiteList.size() > 0) {
            boolean bl = false;
            for (String white : this.whiteList) {
                if (white.contains("#")) {
                    bl = true;
                    String whiteClass = white.split("#")[0];
                    if (pathMatcher.matchStart(whiteClass, classNode.name)) {
/*                        if ("<init>".equals(methodNode.name) || "<clinit>".equals(methodNode.name)) {
                            return true;
                        }*/
                        String whiteMethod = white.split("#")[1];
                        if (whiteMethod.contains("!")) {
                            if (pathMatcher.matchStart(whiteMethod, methodNode.name + '!' + methodNode.desc)) {
                                return true;
                            }
                        } else {
                            if (pathMatcher.matchStart(whiteMethod, methodNode.name)) {
                                return true;
                            }
                        }
                    }
                }
            }
            if (bl) {
                return false;
            }
        }

        if (useAnnotations) {
            boolean classIsMarked = classNode.invisibleAnnotations != null &&
                    classNode.invisibleAnnotations.stream().anyMatch(annotationNode ->
                            annotationNode.desc.equals(NATIVE_ANNOTATION_DESC));
            if (methodNode.invisibleAnnotations != null &&
                    methodNode.invisibleAnnotations.stream().anyMatch(annotationNode ->
                            annotationNode.desc.equals(NATIVE_ANNOTATION_DESC))) {
                return true;
            }
            return classIsMarked && (methodNode.invisibleAnnotations == null || methodNode.invisibleAnnotations
                    .stream().noneMatch(annotationNode -> annotationNode.desc.equals(
                            NOT_NATIVE_ANNOTATION_DESC)));
        } else {
            return true;
        }

    }

    public static void cleanAnnotations(ClassNode classNode) {
        if (classNode.invisibleAnnotations != null) {
            classNode.invisibleAnnotations.removeIf(annotationNode -> annotationNode.desc.equals(NATIVE_ANNOTATION_DESC));
        }
        classNode.methods.stream()
                .filter(methodNode -> methodNode.invisibleAnnotations != null)
                .forEach(methodNode -> methodNode.invisibleAnnotations.removeIf(annotationNode ->
                        annotationNode.desc.equals(NATIVE_ANNOTATION_DESC) || annotationNode.desc.equals(NOT_NATIVE_ANNOTATION_DESC)));
    }
}
