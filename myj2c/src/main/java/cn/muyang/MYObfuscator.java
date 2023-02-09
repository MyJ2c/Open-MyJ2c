package cn.muyang;

import cn.muyang.asm.ClassMetadataReader;
import cn.muyang.asm.SafeClassWriter;
import cn.muyang.cache.*;
import cn.muyang.env.LicenseManager;
import cn.muyang.env.SetupManager;
import cn.muyang.helpers.ProcessHelper;
import cn.muyang.utils.DataTool;
import cn.muyang.utils.FileUtils;
import cn.muyang.utils.Snippets;
import cn.muyang.utils.StringUtils;
import cn.muyang.xml.Config;
import cn.muyang.xml.Match;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.*;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MYObfuscator {
    public static Path temp;
    public static final Locale locale = Locale.getDefault();
    private final Snippets snippets;
    private InterfaceStaticClassProvider staticClassProvider;
    private final MethodProcessor methodProcessor;

    private final NodeCache<String> cachedStrings;
    private final ClassNodeCache cachedClasses;
    private final MethodNodeCache cachedMethods;
    private final FieldNodeCache cachedFields;

    private Map<String, String> classMethodNameMap = new HashMap<>();

    private Map<String, String> noInitClassMap = new HashMap<>();

    private StringBuilder nativeMethods;

    private BootstrapMethodsPool bootstrapMethodsPool;

    private int currentClassId;
    private int methodIndex;
    private String nativeDir;
    private static String separator = File.separator;
    private static boolean stringObf = false;
    private static ClassMethodFilter classMethodFilter;

    public MYObfuscator() {
        snippets = new Snippets();
        cachedStrings = new NodeCache<>("(cstrings[%d])");
        cachedClasses = new ClassNodeCache("(cclasses[%d])");
        cachedMethods = new MethodNodeCache("(cmethods[%d])", cachedClasses);
        cachedFields = new FieldNodeCache("(cfields[%d])", cachedClasses);
        methodProcessor = new MethodProcessor(this);
    }

    public Path preProcess(Path inputJarPath, Config config, boolean useAnnotations) {
        if (config.getOptions() != null && "true".equals(config.getOptions().getStringObf())) {
            stringObf = true;
        }
        if (temp == null) {
            try {
                temp = Files.createTempDirectory("native-myj2c-");
            } catch (IOException e) {
            }
        }
        List<String> whiteList = new ArrayList<>();
        if (config.getIncludes() != null) {
            for (Match include : config.getIncludes()) {
                StringBuilder stringBuilder = new StringBuilder();
                if (StringUtils.isNotEmpty(include.getClassName())) {
                    stringBuilder.append(include.getClassName().replaceAll("\\.", "/"));
                }
                if (StringUtils.isNotEmpty(include.getMethodName())) {
                    stringBuilder.append("#" + include.getMethodName());
                    if (StringUtils.isNotEmpty(include.getMethodDesc())) {
                        stringBuilder.append("!" + include.getMethodDesc());
                    }
                } else {
                    if (StringUtils.isNotEmpty(include.getMethodDesc())) {
                        stringBuilder.append("#**!" + include.getMethodDesc());
                    }
                }
                whiteList.add(stringBuilder.toString());
            }
        }
        List<String> blackList = new ArrayList<>();
        if (config.getExcludes() != null) {
            for (Match include : config.getExcludes()) {
                StringBuilder stringBuilder = new StringBuilder();
                if (StringUtils.isNotEmpty(include.getClassName())) {
                    stringBuilder.append(include.getClassName().replaceAll("\\.", "/"));
                }
                if (StringUtils.isNotEmpty(include.getMethodName())) {
                    stringBuilder.append("#" + include.getMethodName());
                    if (StringUtils.isNotEmpty(include.getMethodDesc())) {
                        stringBuilder.append("!" + include.getMethodDesc());
                    }
                } else {
                    if (StringUtils.isNotEmpty(include.getMethodDesc())) {
                        stringBuilder.append("#**!" + include.getMethodDesc());
                    }
                }
                blackList.add(stringBuilder.toString());
            }
        }

        Path myj2cFile = temp.resolve(UUID.randomUUID() + ".myj2c");
        classMethodFilter = new ClassMethodFilter(blackList, whiteList, useAnnotations);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(myj2cFile))) {
            File jarFile = inputJarPath.toAbsolutePath().toFile();
            JarFile jar = new JarFile(jarFile);
            //预处理，找到需要混淆的类和方法
            jar.stream().forEach(entry -> {
                        try {
                            if (entry.getName().endsWith(".class")) {
                                Map<String, CachedClassInfo> cache = cachedClasses.getCache();
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                try (InputStream in = jar.getInputStream(entry)) {
                                    Util.transfer(in, baos);
                                }
                                byte[] src = baos.toByteArray();
                                ClassReader classReader = new ClassReader(src);
                                ClassNode classNode = new ClassNode();
                                classReader.accept(classNode, 0);
                                if (classMethodFilter.shouldProcess(classNode)) {
                                    CachedClassInfo classInfo = new CachedClassInfo(classNode.name, classNode.name, "", cache.size(), Util.getFlag(classNode.access, Opcodes.ACC_STATIC));
                                    for (FieldNode field : classNode.fields) {
                                        boolean isStatic = Util.getFlag(field.access, Opcodes.ACC_STATIC);
                                        CachedFieldInfo cachedFieldInfo = new CachedFieldInfo(classNode.name, field.name, field.desc, isStatic);
                                        classInfo.addCachedField(cachedFieldInfo);
                                    }
                                    if (config.getOptions() != null && "true".equals(config.getOptions().getFlowObf())) {
                                        //控制流混淆
                                        Flow.transformClass(classNode);
                                    }
                                    for (MethodNode method : classNode.methods) {
                                        if (!"<clinit>".equals(method.name)) {
                                            boolean isStatic = Util.getFlag(method.access, Opcodes.ACC_STATIC);
                                            CachedMethodInfo cachedMethodInfo = new CachedMethodInfo(classNode.name, method.name, method.desc, isStatic);
                                            classInfo.addCachedMethod(cachedMethodInfo);
                                            if (config.getOptions() != null && "true".equals(config.getOptions().getFlowObf())) {
                                                //控制流混淆
                                                Flow.transformMethod(classNode, method);
                                            }
                                        }
                                    }
                                    cache.put(classNode.name, classInfo);
                                    if (config.getOptions() != null && "true".equals(config.getOptions().getFlowObf())) {
                                        ClassWriter classWriter = new ClassWriter(classReader, 0);
                                        classNode.accept(classWriter);
                                        Util.writeEntry(out, entry.getName(), classWriter.toByteArray());
                                    } else {
                                        Util.writeEntry(jar, out, entry);
                                    }
                                } else {
                                    Util.writeEntry(jar, out, entry);
                                }
                            } else {
                                Util.writeEntry(jar, out, entry);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
            );
            jar.stream().close();
            out.closeEntry();
            out.close();
            jar.close();
            return myj2cFile;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void process(Path inputJarPath, Path myj2cJarPath, Path output, Config config, List<Path> inputLibs,
                        String plainLibName,String libUrl, boolean useAnnotations, boolean delete) throws IOException {
        ExecutorService threadPool = Executors.newCachedThreadPool();
        List<Path> libs = new ArrayList<>(inputLibs);
        libs.add(myj2cJarPath);
        ClassMetadataReader metadataReader = new ClassMetadataReader(libs.stream().map(x -> {
            try {
                return new JarFile(x.toFile());
            } catch (IOException ex) {
                return null;
            }
        }).collect(Collectors.toList()));

        Path outputDir;
        String outputName = "";
        if (output.toFile().isDirectory()) {
            outputDir = output;
            outputName = inputJarPath.toFile().getName();
        } else {
            if (output.toFile().getName().contains(".jar")) {
                outputDir = output.getParent();
                outputName = output.toFile().getName();
            } else {
                outputDir = output;
                outputName = inputJarPath.toFile().getName();
            }
        }
        if (locale.getLanguage().contains("zh")) {
            System.out.println("输出位置:" + outputDir.toFile().getAbsolutePath() + File.separator + outputName);
        } else {
            System.out.println("Output location:" + outputDir.toFile().getAbsolutePath() + File.separator + outputName);
        }
        Path cppDir = outputDir.resolve("cpp");
        Files.createDirectories(cppDir);
        Path cacheDir = cppDir.resolve(".cache");
        Files.createDirectories(cacheDir);
        Path zigTempDir = cppDir.resolve(".temp");
        Files.createDirectories(zigTempDir);
        Util.copyResource("sources/jni.h", cppDir);

        Map<String, ClassNode> map = new HashMap<>();
        Map<String, String> classNameMap = new HashMap<>();
        StringBuilder instructions = new StringBuilder();
        File jarFile = myj2cJarPath.toAbsolutePath().toFile();
        JarFile inputJar = new JarFile(inputJarPath.toFile());
        //Path tempFile = temp.resolve(UUID.randomUUID() + ".data");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(outputDir.resolve(outputName)))) {
            JarFile jar = new JarFile(jarFile);
            if (locale.getLanguage().contains("zh")) {
                System.out.println("正在解析 " + inputJarPath + "...");
            } else {
                System.out.println("Parsing " + inputJarPath + "...");
            }
            nativeDir = "myj2c/" + getRandomString(6);
            bootstrapMethodsPool = new BootstrapMethodsPool(nativeDir);
            staticClassProvider = new InterfaceStaticClassProvider(nativeDir);
            methodIndex = 1;

            AtomicInteger classNumber = new AtomicInteger();
            AtomicInteger methodNumber = new AtomicInteger();

            jar.stream().forEach(entry -> {
                if (entry.getName().equals(JarFile.MANIFEST_NAME)) return;
                try {
                    if (!entry.getName().endsWith(".class")) {
                        Util.writeEntry(jar, out, entry);
                        return;
                    }
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (InputStream in = jar.getInputStream(entry)) {
                        Util.transfer(in, baos);
                    }
                    byte[] src = baos.toByteArray();
                    if (Util.byteArrayToInt(Arrays.copyOfRange(src, 0, 4)) != 0xCAFEBABE) {
                        Util.writeEntry(out, entry.getName(), src);
                        return;
                    }
                    nativeMethods = new StringBuilder();
                    ClassReader classReader = new ClassReader(src);
                    ClassNode rawClassNode = new ClassNode();
                    classReader.accept(rawClassNode, ClassReader.SKIP_DEBUG);
                    if (!classMethodFilter.shouldProcess(rawClassNode) ||
                            rawClassNode.methods.stream().noneMatch(method -> MethodProcessor.shouldProcess(method) &&
                                    classMethodFilter.shouldProcess(rawClassNode, method))) {
                        //System.out.println("Skipping " + rawClassNode.name);
                        if (useAnnotations) {
                            ClassMethodFilter.cleanAnnotations(rawClassNode);
                            ClassWriter clearedClassWriter = new SafeClassWriter(metadataReader, Opcodes.ASM9);
                            rawClassNode.accept(clearedClassWriter);
                            Util.writeEntry(out, entry.getName(), clearedClassWriter.toByteArray());
                            return;
                        }
                        Util.writeEntry(out, entry.getName(), src);
                        return;
                    }

                    classNumber.getAndIncrement();
                    //System.out.println("<match className=\""+ rawClassNode.name +"\" />");

                   /* rawClassNode.methods.stream().filter(MethodProcessor::shouldProcess)
                            .filter(methodNode -> classMethodFilter.shouldProcess(rawClassNode, methodNode))
                    .forEach(methodNode -> Preprocessor.preprocess(rawClassNode, methodNode, platform));*/
                    //System.out.println("MethodFilter done");

                    ClassWriter preprocessorClassWriter = new SafeClassWriter(metadataReader, Opcodes.ASM9 | ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                    rawClassNode.accept(preprocessorClassWriter);
                    classReader = new ClassReader(preprocessorClassWriter.toByteArray());
                    ClassNode classNode = new ClassNode();
                    classReader.accept(classNode, 0);

                    if (classNode.methods.stream().noneMatch(x -> x.name.equals("<clinit>"))) {
                        classNode.methods.add(new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, new String[0]));
                    }
                    staticClassProvider.newClass();

                    map.put(classNode.name, classNode);
                    classNameMap.put(classNode.name, classReader.getClassName());

                    instructions.append("\n//" + classNode.name + "\n");

                    classNode.visitMethod(Opcodes.ACC_NATIVE | Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "$myj2cLoader", "()V", null, new String[0]);
                    classNode.version = 52;

                    for (int i = 0; i < classNode.methods.size(); i++) {
                        MethodNode method = classNode.methods.get(i);

                        if (!MethodProcessor.shouldProcess(method)) {
                            continue;
                        }

                        if (!classMethodFilter.shouldProcess(classNode, method) && !"<clinit>".equals(method.name)) {
                            continue;
                        }
                        //解析方法
                        MethodContext context = new MethodContext(this, method, methodIndex, classNode, currentClassId);
                        methodProcessor.processMethod(context);
                        instructions.append(context.output.toString().replace("\n", "\n    "));

                        nativeMethods.append(context.nativeMethods);

                        if ((classNode.access & Opcodes.ACC_INTERFACE) > 0) {
                            method.access &= ~Opcodes.ACC_NATIVE;
                        }
                        methodIndex++;
                        if (!"<clinit>".equals(method.name)) {
                            methodNumber.getAndIncrement();
                        }
                    }

                    if (!staticClassProvider.isEmpty()) {
                        cachedStrings.getPointer(staticClassProvider.getCurrentClassName().replace('/', '.'));
                    }

                    if (useAnnotations) {
                        ClassMethodFilter.cleanAnnotations(classNode);
                    }
                    ClassWriter classWriter = new SafeClassWriter(metadataReader, Opcodes.ASM9 | ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                    classNode.accept(classWriter);

                    //保存class文件
                    Util.writeEntry(out, entry.getName(), classWriter.toByteArray());
                    currentClassId++;
                } catch (IOException ex) {
                    ex.printStackTrace();
                    System.out.println("Error while processing " + entry.getName() + " " + ex.getMessage());
                }
            });

            Manifest mf = jar.getManifest();
            if (mf != null) {
                mf.getMainAttributes().put(new Attributes.Name("Built-By"), "myj2c V" + Main.VERSION);
                mf.getMainAttributes().put(new Attributes.Name("Built-Tools-Info"), "QQ GROUP:197453088");
                mf.getMainAttributes().put(new Attributes.Name("Built-Tools-Info-URL"), "www.myj2c.cn");
                out.putNextEntry(new ZipEntry(JarFile.MANIFEST_NAME));
                mf.write(out);
            }
            jar.close();
            inputJar.close();

            if (!Files.exists(Paths.get(outputDir + separator + "build" + separator + "lib"))) {
                Files.createDirectories(Paths.get(outputDir + separator + "build" + separator + "lib"));
            }

            for (ClassNode ifaceStaticClass : staticClassProvider.getReadyClasses()) {
                ClassWriter classWriter = new SafeClassWriter(metadataReader, Opcodes.ASM9 | ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                ifaceStaticClass.accept(classWriter);
                Util.writeEntry(out, ifaceStaticClass.name + ".class", classWriter.toByteArray());
            }

            out.flush();
            if (locale.getLanguage().contains("zh")) {
                System.out.println("共找到 " + classNumber.get() + " 个类文件 " + methodNumber.get() + " 个方法需要myj2c编译");
            } else {
                System.out.println("Total " + classNumber.get() + " class files and " + methodNumber.get() + " methods need compilation");
            }
            if ("1".equals(LicenseManager.getValue("type"))) {
                if (methodNumber.get() > Integer.parseInt(LicenseManager.getValue("method")) || classNumber.get() > Integer.parseInt(LicenseManager.getValue("class"))) {
                    if (locale.getLanguage().contains("zh")) {
                        System.out.println("您使用的是个人版授权,需要编译的类或方法超过最大数量！！！");
                    } else {
                        System.out.println("You are using personal edition authorization, and the number of classes or methods to be compiled exceeds the maximum!!!\n");
                    }
                    return;
                }
                if (locale.getLanguage().contains("zh")) {
                    System.out.println("将使用个人版授权为您编译！！！\n");
                } else {
                    System.out.println("Will be compiled for you with personal edition license ！！！\n");
                }
            } else if ("2".equals(LicenseManager.getValue("type"))) {
                if (locale.getLanguage().contains("zh")) {
                    if (locale.getLanguage().contains("zh")) {
                        System.out.println("将使用专业版授权为您编译！！！\n");
                    } else {
                        System.out.println("Will be compiled for you using the Professional License！！！\n");
                    }
                }
            }
            boolean free = false;
            if (StringUtils.isEmpty(LicenseManager.getValue("type"))) {
                if (methodNumber.get() == 1 && classNumber.get() == 1) {
                    free = true;
                    if (locale.getLanguage().contains("zh")) {
                        System.out.println("将使用免费版授权为您编译,编译文件不会过期！！！\n");
                    } else {
                        System.out.println(" t will be compiled for you with a free version license, and the compiled file will not expire ！！！\n");
                    }
                } else {
                    if (locale.getLanguage().contains("zh")) {
                        System.out.println("将使用试用版授权为您编译,当前编译的程序将在一周后过期,过期后将不能正常运行！！！\n");
                    } else {
                        System.out.println("The trial version will be authorized to compile for you. The currently compiled program will expire in a week, and will not work properly after expiration!!！\n");
                    }
                }
            }

            if (locale.getLanguage().contains("zh")) {
                System.out.println("正在把class文件转换成C语言代码");
            } else {
                System.out.println("Converting class file to C language code ");
            }
            genCode(cppDir, config, free, instructions, map, classNameMap);
            final long startTime = System.currentTimeMillis();
            List<Future> allCompileTask = new ArrayList<>();
            if (StringUtils.isEmpty(plainLibName)) {
                if (locale.getLanguage().contains("zh")) {
                    System.out.println("\n开始编译动态链接库文件");
                } else {
                    System.out.println("\nStart compiling the dynamic link library file");
                }
                List<String> libNames = new ArrayList<>();
                for (String target : config.getTargets()) {
                    String platformTypeName;
                    String osName;
                    String libName;
                    switch (target) {
                        case "WINDOWS_X86_64":
                            osName = "windows";
                            platformTypeName = "x86_64";
                            libName = "x64-windows.dll";
                            break;
                        case "MACOS_X86_64":
                            osName = "macos";
                            platformTypeName = "x86_64";
                            libName = "x64-macos.dylib";
                            break;
                        case "LINUX_X86_64":
                            osName = "linux";
                            platformTypeName = "x86_64";
                            libName = "x64-linux.so";
                            break;
                        case "WINDOWS_AARCH64":
                            osName = "windows";
                            platformTypeName = "aarch64";
                            libName = "arm64-windows.dll";
                            break;
                        case "MACOS_AARCH64":
                            osName = "macos";
                            platformTypeName = "aarch64";
                            libName = "arm64-macos.dylib";
                            break;
                        case "LINUX_AARCH64":
                            osName = "linux";
                            platformTypeName = "aarch64";
                            libName = "arm64-linux.so";
                            break;
                        default:
                            platformTypeName = "";
                            osName = "";
                            libName = "";
                            break;
                    }

                    String currentOSName = "";
                    if (SetupManager.isWindows()) {
                        currentOSName = "windows";
                    }
                    if (SetupManager.isLinux()) {
                        currentOSName = "linux";
                    }
                    if (SetupManager.isMacOS()) {
                        currentOSName = "macos";
                    }
                    String currentPlatformTypeName = "";
                    switch (System.getProperty("os.arch").toLowerCase()) {
                        case "x86_64":
                        case "amd64":
                            currentPlatformTypeName = "x86_64";
                            break;
                        case "aarch64":
                            currentPlatformTypeName = "aarch64";
                            break;
                        case "x86":
                            currentPlatformTypeName = "i386";
                            break;
                        default:
                            currentPlatformTypeName = "";
                            break;
                    }
                    if (locale.getLanguage().contains("zh")) {
                        System.out.println("开始编译:" + target);
                    } else {
                        System.out.println("Compiling:" + target);
                    }
                    String compilePath = System.getProperty("user.dir") + separator + "zig-" + currentOSName + "-" + currentPlatformTypeName + "-0.10.0" + separator + "zig" + (SetupManager.isWindows() ? ".exe" : "");
                    if (Files.exists(Paths.get(compilePath))) {
                        Future future = zigCompile(outputDir, compilePath, platformTypeName, osName, libName, libNames);
                        allCompileTask.add(future);
                    } else {
                        Path parent = Paths.get(System.getProperty("user.dir")).getParent();
                        Future future = zigCompile(outputDir, parent.toFile().getAbsolutePath() + separator + "zig-" + currentOSName + "-" + currentPlatformTypeName + "-0.10.0" + separator + "zig" + (SetupManager.isWindows() ? ".exe" : ""), platformTypeName, osName, libName, libNames);
                        allCompileTask.add(future);
                    }

                }

                //获取异步Future对象
                Map total = new HashMap();
                Future future = threadPool.submit(new Callable() {
                    @Override
                    public Long call() throws Exception {
                        for (Future task : allCompileTask) {
                            task.get();
                        }
                        long totalTime = System.currentTimeMillis() - startTime;
                        total.put("time", totalTime);
                        return totalTime;
                    }
                });

                int max = methodNumber.get();
                max = max > 50 ? max : 50;
                for (int i = 0; i <= max; i++) {
                    if (total.get("time") == null) {
                        try {
                            Thread.sleep(50 * config.getTargets().size());
                            while (i >= (max * 0.97) && total.get("time") == null) {
                                Thread.sleep(100);
                            }
                            if (total.get("time") != null) {
                                System.out.print(String.format("\r%s", progressBar(max, max)));
                                break;
                            }
                        } catch (Exception e) {
                        }
                    }
                    System.out.print(String.format("\r%s", progressBar(i, max)));
                }
                System.out.println("\n");
                if (locale.getLanguage().contains("zh")) {
                    try {
                        System.out.println(String.format("编译完成耗时 %dms", future.get()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    try {
                        System.out.println(String.format("Compilation time %dms", future.get()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                if (locale.getLanguage().contains("zh")) {
                    System.out.println("正在压缩已编译的动态链接库文件");
                } else {
                    System.out.println("Compressing compiled dynamic link library files ");

                }

                DataTool.compress(outputDir + separator + "build" + separator + "lib", outputDir + separator + "data.dat", Integer.getInteger("level", Deflater.BEST_SPEED));
                //Files.deleteIfExists(tempFile);

                //Files.deleteIfExists(temp);
                if (locale.getLanguage().contains("zh")) {
                    System.out.println("正在重新打包");
                } else {
                    System.out.println("Repackaging");
                }
                if (StringUtils.isEmpty(libUrl)) {
                    Util.writeEntry(out, nativeDir + "/data.dat", Files.readAllBytes(Paths.get(outputDir + separator + "data.dat")));
                } else {
                    Path uploadDir = outputDir.resolve("upload");
                    Files.createDirectories(uploadDir);
                    Files.deleteIfExists(Paths.get(outputDir + separator + "upload/data.dat"));
                    Files.copy(Paths.get(outputDir + separator + "data.dat"), Paths.get(outputDir + separator + "upload/data.dat"));
                }
                try {

                    if (locale.getLanguage().contains("zh")) {
                        System.out.println("清理临时文件");
                    } else {
                        System.out.println("Clean up temporary files");
                    }
                    FileUtils.clearDirectory(outputDir + separator + "cpp");
                    FileUtils.clearDirectory(outputDir + separator + "build");
                    Files.deleteIfExists(Paths.get(outputDir + separator + "data.dat"));
                } catch (Exception e) {
                }
            }

            addJniLoader(plainLibName, libUrl, metadataReader, out);

            StringBuilder builder = new StringBuilder().append("Created-By MuYang Java to C Bytecode Translator \n         myj2c V").append(Main.VERSION).append("\n         QQGROUP:197453088");
            if (config.getOptions() != null && StringUtils.isNotEmpty(config.getOptions().getExpireDate())) {
                builder.append("\n    Will expire on :" + config.getOptions().getExpireDate());
            }
            out.setComment(builder.toString());
            //Crasher.transformOutput(out);
            out.closeEntry();
            metadataReader.close();
            if (locale.getLanguage().contains("zh")) {
                System.out.println("myj2c编译任务已成功");
            } else {
                System.out.println("myj2c compilation task succeeded");
            }
            out.close();
            if (delete) {
                Files.deleteIfExists(inputJarPath);
            }
            FileUtils.clearDirectory(temp.toString());
            if (!Files.exists(Paths.get(outputDir + separator + outputName))) {
                Files.deleteIfExists(Paths.get(outputDir + separator + outputName));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addJniLoader(String plainLibName,String libUrl, ClassMetadataReader metadataReader, ZipOutputStream out) throws IOException {
        Path loader = Files.createTempFile("bin", null);
        try {
            byte[] arrayOfByte = new byte[2048];
            Path datFile = null;
            try {
                InputStream inputStream = MYObfuscator.class.getResourceAsStream("/myj2c.bin");
                if (inputStream == null) {
                    throw new UnsatisfiedLinkError(String.format("Failed to open dat file: myj2c.bin"));
                }
                try {
                    datFile = Files.createTempFile("dat", null);
                    FileOutputStream fileOutputStream = new FileOutputStream(datFile.toFile());
                    int size;
                    while ((size = inputStream.read(arrayOfByte)) != -1) {
                        fileOutputStream.write(arrayOfByte, 0, size);
                    }
                    fileOutputStream.close();
                } catch (Throwable throwable) {
                    throw throwable;
                } finally {
                    inputStream.close();
                }
            } catch (IOException exception) {
                throw new UnsatisfiedLinkError(String.format("Failed to copy file: %s", exception.getMessage()));
            }
            FileUtils.decryptToFile(datFile, loader, "MY20150501@2022");
            Files.deleteIfExists(datFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        JarFile loaderJar = new JarFile(loader.toFile());
        Map<String, String> classMap = new HashMap<>();
        loaderJar.stream().forEach(entry -> {
            if (entry.getName().endsWith(".class")) {
                classMap.put(entry.getName().replace(".class", ""), entry.getName());
            }
        });
        loaderJar.stream().forEach(entry -> {
                    if (!entry.getName().endsWith(".class")) {
                        return;
                    }
                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        try (InputStream in = loaderJar.getInputStream(entry)) {
                            Util.transfer(in, baos);
                        }
                        byte[] src = baos.toByteArray();
                        ClassReader classReader = new ClassReader(src);
                        ClassNode rawClassNode = new ClassNode();
                        classReader.accept(rawClassNode, ClassReader.SKIP_DEBUG);
                        ClassNode resultLoaderClass = new ClassNode();
                        String originalLoaderClassName = rawClassNode.name;

                        if (StringUtils.contains(entry.getName(), "Loader")) {
                            String loaderClassName = nativeDir + "/Loader";
                            if (plainLibName != null) {
                                if (StringUtils.contains(entry.getName(), "LoaderPlain")) {
                                    rawClassNode.methods.forEach(method -> {
                                        for (int i = 0; i < method.instructions.size(); i++) {
                                            AbstractInsnNode insnNode = method.instructions.get(i);
                                            if (insnNode instanceof LdcInsnNode && ((LdcInsnNode) insnNode).cst instanceof String &&
                                                    ((LdcInsnNode) insnNode).cst.equals("%LIB_NAME%")) {
                                                ((LdcInsnNode) insnNode).cst = plainLibName;
                                            }
                                        }
                                    });
                                    rawClassNode.accept(new ClassRemapper(resultLoaderClass, new Remapper() {
                                        @Override
                                        public String map(String internalName) {
                                            return internalName.equals(originalLoaderClassName) ? loaderClassName : classMap.get(internalName) != null ? nativeDir + "/" + internalName : internalName;
                                        }
                                    }));
                                    //rewriteClass(resultLoaderClass);

                                    ClassWriter classWriter = new SafeClassWriter(metadataReader, Opcodes.ASM9 | ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                                    for (ClassNode bootstrapClass : bootstrapMethodsPool.getClasses()) {
                                        bootstrapClass.accept(classWriter);
                                    }
                                    resultLoaderClass.accept(classWriter);
                                    Util.writeEntry(out, loaderClassName + ".class", classWriter.toByteArray());
                                }
                            } else if (StringUtils.contains(entry.getName(), "LoaderUnpack")) {
                                //if(StringUtils.isNotEmpty(libUrl)) {
                                    rawClassNode.methods.forEach(method -> {
                                        for (int i = 0; i < method.instructions.size(); i++) {
                                            AbstractInsnNode insnNode = method.instructions.get(i);
                                            if (insnNode instanceof LdcInsnNode && ((LdcInsnNode) insnNode).cst instanceof String &&
                                                    ((LdcInsnNode) insnNode).cst.equals("%LIB_URL%")) {
                                                ((LdcInsnNode) insnNode).cst = StringUtils.isNotEmpty(libUrl) ? libUrl : "";
                                            }
                                        }
                                    });
                                //}
                                rawClassNode.accept(new ClassRemapper(resultLoaderClass, new Remapper() {
                                    @Override
                                    public String map(String internalName) {
                                        return internalName.equals(originalLoaderClassName) ? loaderClassName : classMap.get(internalName) != null ? nativeDir + "/" + internalName : internalName;
                                    }
                                }));

                                //rewriteClass(resultLoaderClass);

                                ClassWriter classWriter = new SafeClassWriter(metadataReader, Opcodes.ASM9 | ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                                for (ClassNode bootstrapClass : bootstrapMethodsPool.getClasses()) {
                                    bootstrapClass.accept(classWriter);
                                }
                                resultLoaderClass.accept(classWriter);
                                Util.writeEntry(out, new StringBuilder().append(loaderClassName).append(".class").toString(), classWriter.toByteArray());
                            }
                        } else if (StringUtils.isEmpty(plainLibName)) {
                            String loaderClassName = new StringBuilder().append(nativeDir).append("/").append(originalLoaderClassName).toString();
                            rawClassNode.accept(new ClassRemapper(resultLoaderClass, new Remapper() {
                                @Override
                                public String map(String internalName) {
                                    return internalName.equals(originalLoaderClassName) ? loaderClassName : classMap.get(internalName) != null ? nativeDir + "/" + internalName : internalName;
                                }
                            }));
                            //rewriteClass(resultLoaderClass);

                            ClassWriter classWriter = new SafeClassWriter(metadataReader, Opcodes.ASM9 | ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                            resultLoaderClass.accept(classWriter);
                            Util.writeEntry(out, new StringBuilder().append(nativeDir).append("/").append(originalLoaderClassName).append(".class").toString(), classWriter.toByteArray());

                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
        );
        try {
            loaderJar.close();
            Files.deleteIfExists(loader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Snippets getSnippets() {
        return snippets;
    }


    public InterfaceStaticClassProvider getStaticClassProvider() {
        return staticClassProvider;
    }

    public NodeCache<String> getCachedStrings() {
        return cachedStrings;
    }

    public ClassNodeCache getCachedClasses() {
        return cachedClasses;
    }

    public MethodNodeCache getCachedMethods() {
        return cachedMethods;
    }

    public FieldNodeCache getCachedFields() {
        return cachedFields;
    }

    public String getNativeDir() {
        return nativeDir;
    }

    public BootstrapMethodsPool getBootstrapMethodsPool() {
        return bootstrapMethodsPool;
    }

    public Map<String, String> getClassMethodNameMap() {
        return classMethodNameMap;
    }


    public Map<String, String> getNoInitClassMap() {
        return noInitClassMap;
    }

    private static String getRandomString(int length) {
        //定义一个字符串（A-Z，a-z，0-9）即62位；
        String str = "zxcvbnmlkjhgfdsaqwertyuiopQWERTYUIOPASDFGHJKLZXCVBNM1234567890";
        //由Random生成随机数
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        sb.append(str.charAt(random.nextInt(26)));
        //长度为几就循环几次
        for (int i = 0; i < length - 1; ++i) {
            //产生0-61的数字
            int number = random.nextInt(62);
            //将产生的数字通过length次承载到sb中
            sb.append(str.charAt(number));
        }
        //将承载的字符转换成字符串
        return sb.toString();
    }


    private void varargsAccess(MethodNode methodNode) {
        if ((methodNode.access & Opcodes.ACC_SYNTHETIC) == 0
                && (methodNode.access & Opcodes.ACC_BRIDGE) == 0) {
            methodNode.access |= Opcodes.ACC_VARARGS;
        }
    }

    private void bridgeAccess(MethodNode methodNode) {
        if (!methodNode.name.contains("<")
                && !Modifier.isAbstract(methodNode.access)) {
            methodNode.access |= Opcodes.ACC_BRIDGE;
        }
    }

    private void syntheticAccess(ClassNode classNode) {
        classNode.access |= Opcodes.ACC_SYNTHETIC;
        classNode.fields
                .forEach(fieldNode -> fieldNode.access |= Opcodes.ACC_SYNTHETIC);
        classNode.methods
                .forEach(methodNode -> methodNode.access |= Opcodes.ACC_SYNTHETIC);
    }

    private void changeSource(ClassNode classNode) {
        classNode.sourceFile = this.getMassiveString();
        classNode.sourceDebug = this.getMassiveString();
    }

    private void changeSignature(ClassNode classNode) {
        classNode.signature = this.getMassiveString();
        classNode.fields.forEach(
                fieldNode -> fieldNode.signature = this.getMassiveString());
        classNode.methods.forEach(
                methodNode -> methodNode.signature = this.getMassiveString());
    }

    private void deprecatedAccess(ClassNode classNode) {
        classNode.access |= Opcodes.ACC_DEPRECATED;
        classNode.methods
                .forEach(methodNode -> methodNode.access |= Opcodes.ACC_DEPRECATED);
        classNode.fields
                .forEach(fieldNode -> fieldNode.access |= Opcodes.ACC_DEPRECATED);
    }

    private void transientAccess(ClassNode classNode) {
        classNode.fields
                .forEach(fieldNode -> fieldNode.access |= Opcodes.ACC_TRANSIENT);
    }

    private void removeNop(ClassNode classNode) {
        classNode.methods.parallelStream().forEach(
                methodNode -> Arrays.stream(methodNode.instructions.toArray())
                        .filter(insnNode -> insnNode.getOpcode() == Opcodes.NOP)
                        .forEach(insnNode -> {
                            methodNode.instructions.remove(insnNode);
                        }));
    }

    private String getMassiveString() {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Short.MAX_VALUE; i++) {
            builder.append(" ");
        }
        return builder.toString();
    }

    private void rewriteClass(ClassNode classNode) {
        // remove unnecessary insn
        this.removeNop(classNode);
        if (!Modifier.isInterface(classNode.access)) {
            this.transientAccess(classNode);
        }
        this.deprecatedAccess(classNode);
        // bad sources
        this.changeSource(classNode);
        // bad signatures
        this.changeSignature(classNode);
        // synthetic access (most decompilers doesn't show synthetic members)
        this.syntheticAccess(classNode);
        classNode.methods.forEach(methodNode -> {
            // bridge access (almost the same than synthetic)
            this.bridgeAccess(methodNode);
            // varargs access (crashes CFR when last parameter isn't array)
            this.varargsAccess(methodNode);
        });
    }

    public static String progressBar(int currentValue, int maxValue) {
        int progressBarLength = 33; //
        if (progressBarLength < 9 || progressBarLength % 2 == 0) {
            throw new ArithmeticException("formattedPercent.length() = 9! + even number of chars (one for each side)");
        }
        int currentProgressBarIndex = (int) Math.ceil(((double) progressBarLength / maxValue) * currentValue);
        String formattedPercent = String.format(" %5.1f %% ", (100 * currentProgressBarIndex) / (double) progressBarLength);
        int percentStartIndex = ((progressBarLength - formattedPercent.length()) / 2);

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int progressBarIndex = 0; progressBarIndex < progressBarLength; progressBarIndex++) {
            if (progressBarIndex <= percentStartIndex - 1
                    || progressBarIndex >= percentStartIndex + formattedPercent.length()) {
                sb.append(currentProgressBarIndex <= progressBarIndex ? " " : "=");
            } else if (progressBarIndex == percentStartIndex) {
                sb.append(formattedPercent);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private Future zigCompile(Path outputDir, String compilePath, String platformTypeName, String osName, String libName, List<String> libNames) {
        //创建线程池
        ExecutorService threadPool = Executors.newCachedThreadPool();
        //获取异步Future对象
        Future future = threadPool.submit(new Callable() {
            @Override
            public Long call() throws IOException {
                ProcessHelper.ProcessResult compileRunresult = ProcessHelper.run(outputDir, 3000 * 1000,
                        Arrays.asList(compilePath, "cc", "-O2", "-fno-sanitize=all", "-fno-sanitize-trap=all", "-O2", "-fno-optimize-sibling-calls", "-target", platformTypeName + "-" + osName, "-std=c11", "-fPIC", "-shared", "-s", "-fvisibility=hidden", "-fvisibility-inlines-hidden", "-I." + separator + "cpp", "-o." + separator + "build" + separator + "lib" + separator + libName, "." + separator + "cpp" + separator + "myj2c.c"));
                libNames.add(libName);
                compileRunresult.check("zig build");
                return compileRunresult.execTime;
            }
        });
        return future;
    }

    public static boolean isStringObf() {
        return stringObf;
    }


    private void genCode(Path cppDir, Config config, boolean free, StringBuilder instructions, Map<String, ClassNode> map, Map<String, String> classNameMap) throws IOException {
        BufferedWriter mainWriter = Files.newBufferedWriter(cppDir.resolve("myj2c.c").toAbsolutePath());
        mainWriter.append("#include <jni.h>\n" +
                "#include <stdatomic.h>\n" +
                "#include <string.h>\n" +
                "#include <stdbool.h>\n" +
                (stringObf ? "#include <stdarg.h>\n" : "") +
                "#include <math.h>\n\n");

        String signCode = LicenseManager.s();
        String sign = LicenseManager.getValue("sign");
        String appInfo = "";
        if (!free && (!signCode.equals(LicenseManager.v(66)) || !sign.equals(LicenseManager.v(88)))) {
            mainWriter.append("#include <time.h>\n\n");

            mainWriter.append("int info = 0;\n");
            //mainWriter.append("long expire = " + (System.currentTimeMillis() / 1000 + 7 * 24 * 3600) + ";\n");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            if (locale.getLanguage().contains("zh")) {
                appInfo = "\n\n              该应用使用myj2c试用版创建 \n\n            应用将在 " + sdf.format(new Date((System.currentTimeMillis() + 7 * 24 * 3600 * 1000))) + "过期 \n\n         移除该信息请加QQ群:197453088\n\n========================================================\n\n";
            } else {
                appInfo = "\n\n               The application was created using myj2c trial version \n\n            App will expire on " + sdf.format(new Date((System.currentTimeMillis() + 7 * 24 * 3600 * 1000))) + " \n\n          Please add QQ group to remove this information:197453088\n\n========================================================\n\n";
            }
        } else if (config.getOptions() != null && StringUtils.isNotEmpty(config.getOptions().getExpireDate())) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date expire = null;
            try {
                expire = sdf.parse(config.getOptions().getExpireDate());
                mainWriter.append("#include <time.h>\n\n");
                mainWriter.append("int info = 0;\n");
                //mainWriter.append("long expire = " + expire.getTime()/1000 + ";\n");

            } catch (ParseException e) {
            }
        }
        if (free && StringUtils.isEmpty(LicenseManager.getValue("type"))) {
            mainWriter.append("int info = 0;\n");
            if (locale.getLanguage().contains("zh")) {
                appInfo = "\n\n              该应用使用myj2c免费版创建" + " \n\n            (c) 2022-2024 myj2c 版权所有\n\n========================================================\n\n";
            } else {
                appInfo = "\n\n               This application was created with myj2c free version " + " \n\n            (c) 2022-2024 myj2c copyright\n\n========================================================\n\n";

            }
        }
        if ("1".equals(LicenseManager.getValue("type"))) {
            mainWriter.append("int info = 0;\n");
            if (locale.getLanguage().contains("zh")) {
                appInfo = "\n\n              " + (LicenseManager.getValue("product") == null ? "该应用使用myj2c个人版创建" : LicenseManager.getValue("product")) + " \n\n            (c) 2022-2024 " + LicenseManager.getValue("name") + " 版权所有\n\n========================================================\n\n";
            } else {
                appInfo = "\n\n              " + (LicenseManager.getValue("product") == null ? " This application is created using myj2c Personal Edition " : LicenseManager.getValue("product")) + " \n\n            (c) 2022-2024 " + LicenseManager.getValue("name") + " copyright\n\n========================================================\n\n";

            }
        }
        if (config.getOptions() != null && "true".equals(config.getOptions().getStringObf())) {
            mainWriter.append("\nstatic inline char* myj2c_c_str_obf(char *a, char *b, const size_t len) {\n" +
                    "    volatile char c[len]; memcpy((char*) c, b, len); memcpy(b, (char*) c, len);\n" +
                    "    for(size_t i = 0; i < len; i++) a[i] ^= b[i]; return a;\n" +
                    "}\n" +
                    "\n" +
                    "static inline unsigned short* myj2c_j_str_obf(unsigned short *a, unsigned short *b, const size_t len) {\n" +
                    "    volatile unsigned short c[len]; memcpy((unsigned short*) c, b, len); memcpy(b, (unsigned short*) c, len);\n" +
                    "    for(size_t i = 0; i < len; i++) a[i] ^= b[i]; return a;\n" +
                    "}\n\n");
        }
        if (!"".equals(appInfo) || (config.getOptions() != null && StringUtils.isNotEmpty(config.getOptions().getExpireDate()))) {
            mainWriter.append("struct cached_system {\n" +
                    "    jclass clazz;\n" +
                    "    jfieldID id_0;\n" +
                    "};\n" +
                    "\n" +
                    "static const struct cached_system* cc_system(JNIEnv *env) {\n" +
                    "    static struct cached_system cache;\n" +
                    "    static atomic_flag lock;\n" +
                    "    if (cache.clazz) return &cache;\n" +
                    "\n" +
                    "    jclass clazz = (*env)->FindClass(env, \"java/lang/System\");\n" +
                    "    while (atomic_flag_test_and_set(&lock)) {}\n" +
                    "    if (!cache.clazz) {\n" +
                    "        cache.clazz = (*env)->NewGlobalRef(env, clazz);\n" +
                    "        cache.id_0 = (*env)->GetStaticFieldID(env, clazz, \"out\", \"Ljava/io/PrintStream;\");\n" +
                    "    }\n" +
                    "    atomic_flag_clear(&lock);\n" +
                    "    return &cache;\n" +
                    "}\n" +
                    "\n" +
                    "struct cached_print {\n" +
                    "    jclass clazz;\n" +
                    "    jmethodID method_0;\n" +
                    "};\n" +
                    "\n" +
                    "static const struct cached_print* cc_print(JNIEnv *env) {\n" +
                    "    static struct cached_print cache;\n" +
                    "    static atomic_flag lock;\n" +
                    "    if (cache.clazz) return &cache;\n" +
                    "\n" +
                    "    jclass clazz = (*env)->FindClass(env, \"java/io/PrintStream\");\n" +
                    "    while (atomic_flag_test_and_set(&lock)) {}\n" +
                    "    if (!cache.clazz) {\n" +
                    "        cache.clazz = (*env)->NewGlobalRef(env, clazz);\n" +
                    "        cache.method_0 = (*env)->GetMethodID(env, clazz, \"println\", \"(Ljava/lang/String;)V\");\n" +
                    "    }\n" +
                    "    atomic_flag_clear(&lock);\n" +
                    "    return &cache;\n" +
                    "}");
        }
        mainWriter.append("void throw_exception(JNIEnv *env, const char *exception, const char *error, int line) {\n" +
                "        jclass exception_ptr = (*env)->FindClass(env, exception);\n" +
                "        if ((*env)->ExceptionCheck(env)) {\n" +
                "            (*env)->ExceptionDescribe(env);\n" +
                "            (*env)->ExceptionClear(env);\n" +
                "            return;\n" +
                "        }\n" +
                "        char str[strlen(error) + 10];\n" +
                "        sprintf(str, \"%s on %d\", error, line);\n" +
                "        (*env)->ThrowNew(env, exception_ptr,  str);\n" +
                "        (*env)->DeleteLocalRef(env, exception_ptr);\n" +
                "    }\n\n");
        //int index = 0;

        Iterator<Map.Entry<String, CachedClassInfo>> iterator = cachedClasses.getCache().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CachedClassInfo> next = iterator.next();
            int id = next.getValue().getId();
            StringBuffer fieldStr = new StringBuffer();
            List<CachedFieldInfo> fields = next.getValue().getCachedFields();
            for (int i = 0; i < fields.size(); i++) {
                CachedFieldInfo fieldInfo = fields.get(i);
                if (!"<init>".equals(fieldInfo.getName())) {
                    //String c = "    //" + fieldInfo.getName() + "\n";
                    fieldStr.append("    jfieldID id_").append(i).append(";\n");///*c +*/ "    jfieldID id_" + i + ";\n";
                }
            }
            StringBuffer methodStr = new StringBuffer();
            List<CachedMethodInfo> methods = next.getValue().getCachedMethods();
            for (int i = 0; i < methods.size(); i++) {
                //CachedMethodInfo methodInfo = methods.get(i);
                //String c = "    //" + methodInfo.getName() + "\n";
                methodStr.append("    jmethodID method_").append(i).append(";\n");///*c +*/  + i + ";\n";
            }
            //mainWriter.append("/*" + next.getKey() + "*/\n");
            mainWriter.append("struct cached_c_" + id + " {\n" +
                    "    jclass clazz;\n" +
                    fieldStr.toString() +
                    methodStr.toString() +
                    "    jboolean initialize;\n" +
                    "};\n\n");

            String cachedFieldStr = "";

            for (int i = 0; i < fields.size(); i++) {
                CachedFieldInfo fieldInfo = fields.get(i);
                //String c = "        //" + fieldInfo.getName() + "\n";
                if (!"<init>".equals(fieldInfo.getName())) {
                    if (fieldInfo.isStatic()) {
                        if (stringObf) {
                            /*c + */
                            cachedFieldStr = new StringBuilder().append(cachedFieldStr).append("        cache.id_").append(i).append(" = (*env)->GetStaticFieldID(env, clazz, ").append(Util.getStringObf(fieldInfo.getName())).append(", ").append(Util.getStringObf(fieldInfo.getDesc())).append(");\n").toString();
                        } else {
                            /*c + */
                            cachedFieldStr = new StringBuilder().append(cachedFieldStr).append("        cache.id_").append(i).append(" = (*env)->GetStaticFieldID(env, clazz, \"").append(fieldInfo.getName()).append("\", \"").append(fieldInfo.getDesc()).append("\");\n").toString();
                        }
                    } else {
                        if (stringObf) {
                            /*c + */
                            cachedFieldStr = new StringBuilder().append(cachedFieldStr).append("        cache.id_").append(i).append(" = (*env)->GetFieldID(env, clazz, ").append(Util.getStringObf(fieldInfo.getName())).append(", ").append(Util.getStringObf(fieldInfo.getDesc())).append(");\n").toString();
                        } else {
                            /*c + */
                            cachedFieldStr = new StringBuilder().append(cachedFieldStr).append("        cache.id_").append(i).append(" = (*env)->GetFieldID(env, clazz, \"").append(fieldInfo.getName()).append("\", \"").append(fieldInfo.getDesc()).append("\");\n").toString();
                        }
                    }
                }
            }
            String cachedMethodStr = "";
            for (int i = 0; i < methods.size(); i++) {
                CachedMethodInfo methodInfo = methods.get(i);
                //String c = "        //" + methodInfo.getName() + "\n";
                if (methodInfo.isStatic()) {
                    if (stringObf) {
                        /*c + */
                        cachedMethodStr = new StringBuilder().append(cachedMethodStr).append("        cache.method_").append(i).append(" = (*env)->GetStaticMethodID(env, clazz, ").append(Util.getStringObf(methodInfo.getName())).append(", ").append(Util.getStringObf(methodInfo.getDesc())).append(");\n").toString();
                    } else {
                        /*c + */
                        cachedMethodStr = new StringBuilder().append(cachedMethodStr).append("        cache.method_").append(i).append(" = (*env)->GetStaticMethodID(env, clazz, \"").append(methodInfo.getName()).append("\", \"").append(methodInfo.getDesc()).append("\");\n").toString();
                    }
                } else {
                    if (stringObf) {
                        /*c + */
                        cachedMethodStr = new StringBuilder().append(cachedMethodStr).append("        cache.method_").append(i).append(" = (*env)->GetMethodID(env, clazz, ").append(Util.getStringObf(methodInfo.getName())).append(", ").append(Util.getStringObf(methodInfo.getDesc())).append(");\n").toString();
                    } else {
                        /*c + */
                        cachedMethodStr = new StringBuilder().append(cachedMethodStr).append("        cache.method_").append(i).append(" = (*env)->GetMethodID(env, clazz, \"").append(methodInfo.getName()).append("\", \"").append(methodInfo.getDesc()).append("\");\n").toString();
                    }
                }
            }
            String clazz = new StringBuilder().append("    jclass clazz = (*env)->FindClass(env, \"").append(next.getKey()).append("\");\n").toString();
            if (stringObf) {
                clazz = new StringBuilder().append("    jclass clazz = (*env)->FindClass(env, ").append(Util.getStringObf(next.getKey())).append(");\n").toString();
            }
            mainWriter.append("static const struct cached_c_" + id + "* c_" + id + "_(JNIEnv *env) {\n" +
                    "    static struct cached_c_" + id + " cache;\n" +
                    "    static atomic_flag lock;\n" +
                    "    if (cache.initialize) return &cache;\n" +
                    "    cache.initialize = JNI_FALSE;\n" +
                    clazz +
                    "    while (atomic_flag_test_and_set(&lock)) {}\n" +
                    "    if (!cache.initialize) {\n" +
                    "        cache.clazz = (*env)->NewGlobalRef(env, clazz);\n" +
                    "        if ((*env)->ExceptionCheck(env) && !clazz) {\n" +
                    "            cache.initialize = JNI_FALSE;\n" +
                    "            (*env)->ExceptionDescribe(env);\n" +
                    "            (*env)->ExceptionClear(env);\n" +
                    "            atomic_flag_clear(&lock);\n" +
                    "            return &cache;\n" +
                    "        }\n" +
                    cachedFieldStr +
                    cachedMethodStr +
                    "        cache.initialize = JNI_TRUE;\n" +
                    "    }\n" +
                    "    atomic_flag_clear(&lock);\n" +
                    "    return &cache;\n" +
                    "}\n\n");
            //System.out.println("------" + next.getKey() + "," + next.getValue());
        }

        mainWriter.append(instructions);
        Iterator<Map.Entry<String, CachedClassInfo>> classIterator = cachedClasses.getCache().entrySet().iterator();
        while (classIterator.hasNext()) {
            Map.Entry<String, CachedClassInfo> next = classIterator.next();
            ClassNode classNode = map.get(next.getKey());
            if (classNode != null) {

                String registrationMethods = "";
                int methodCount = 0;
                //mainWriter.append("/*Class <" + next.getKey() + ">*/\n");
                for (MethodNode method : classNode.methods) {
                    if (!"<init>".equals(method.name) && !"<clinit>".equals(method.name) && !"$myj2cLoader".equals(method.name)) {
                        String methodName = null;
                        if ("$myj2cClinit".equals(method.name)) {
                            methodName = getClassMethodNameMap().get(classNode.name + ".<clinit>" + "()V");
                        } else {
                            methodName = getClassMethodNameMap().get(classNode.name + "." + method.name + method.desc);
                        }
                        if (methodName == null) {
                            continue;
                        }
                        if (stringObf) {
                            registrationMethods = new StringBuilder().append(registrationMethods).append("            {").append(Util.getStringObf(method.name)).append(", ").append(Util.getStringObf(method.desc)).append(", (void *) &").append(methodName).append("},\n").toString();
                        } else {
                            registrationMethods = new StringBuilder().append(registrationMethods).append("            {\"").append(method.name).append("\", \"").append(method.desc).append("\", (void *) &").append(methodName).append("},\n").toString();
                        }
                        methodCount++;
                    }
                }
                String className = classNameMap.get(classNode.name);
                if (Util.isValidJavaFullClassName(className.replaceAll("/", "."))) {
                    String licenseInfo = "if(info == 0){\n  info = 1;\n" +
                            "    jvalue cstack0; memset(&cstack0, 0, sizeof(jvalue));\n" +
                            "    jvalue cstack1; memset(&cstack1, 0, sizeof(jvalue));\n" +
                            "    \n" +
                            "    cstack0.l = (*env)->GetStaticObjectField(env, cc_system(env)->clazz, cc_system(env)->id_0); \n" +
                            "    if ((*env)->ExceptionCheck(env)) { return; }\n" +
                            "    cstack1.l = (*env)->NewString(env, " + (stringObf ? Util.getStringObf(Util.utf82ints(appInfo)) : "(unsigned short[]) {" + Util.utf82unicode(appInfo) + "}") + ", " + appInfo.length() + ");\n" +
                            "    (*env)->CallVoidMethod(env, cstack0.l, cc_print(env)->method_0, cstack1.l);\n" +
                            "    if ((*env)->ExceptionCheck(env)) { return; }\n" +
                            "}\n";
                    String methodName = NativeSignature.getJNICompatibleName(className);
                    if (!free && (!signCode.equals(LicenseManager.v(66)) || !sign.equals(LicenseManager.v(88)))) {
                        mainWriter.append(new StringBuilder().append("/* Native registration for <").append(className).append("> */\n").append("JNIEXPORT void JNICALL Java_").append(methodName).append("__00024myj2cLoader(JNIEnv *env, jclass clazz) {\n").append("    JNINativeMethod table[] = {\n").append(registrationMethods).append("    };\n").append(licenseInfo).append("\n").append("if(time(NULL)<" + (System.currentTimeMillis() / 1000 + 7 * 24 * 3600) + "){\n").append("    (*env)->RegisterNatives(env, clazz, table, ").append(methodCount).append(");\n").append("}else{\n").append("    jvalue cstack0; memset(&cstack0, 0, sizeof(jvalue));\n").append("    jvalue cstack1; memset(&cstack1, 0, sizeof(jvalue));\n").append("    \n").append("    cstack0.l = (*env)->GetStaticObjectField(env, cc_system(env)->clazz, cc_system(env)->id_0); \n").append("    if ((*env)->ExceptionCheck(env)) { return; }\n").append("    cstack1.l = (*env)->NewString(env, ").append(stringObf ? Util.getStringObf(Util.utf82ints((locale.getLanguage().contains("zh") ? "该应用使用myj2c试用版本创建，试用过期，已不能运行！！！" : "This application was created with myj2c trial version, the trial has expired, stop running!!!"))) : "(unsigned short[]) {" + Util.utf82unicode((locale.getLanguage().contains("zh") ? "该应用使用myj2c试用版本创建，试用过期，已不能运行！！！" : "This application was created with myj2c trial version, the trial has expired, stop running!!!")) + "}").append(", ").append((locale.getLanguage().contains("zh") ? "该应用使用myj2c试用版本创建，试用过期，已不能运行！！！" : "This application was created with myj2c trial version, the trial has expired, stop running!!!").length()).append(");\n").append("    (*env)->CallVoidMethod(env, cstack0.l, cc_print(env)->method_0, cstack1.l);\n").append("    if ((*env)->ExceptionCheck(env)) { return; }\n").append("    exit(-1);\n").append("}\n").append("}\n\n").toString());
                    } else if (free || "1".equals(LicenseManager.getValue("type"))) {
                        mainWriter.append(new StringBuilder().append("/* Native registration for <").append(className).append("> */\n").append("JNIEXPORT void JNICALL Java_").append(methodName).append("__00024myj2cLoader(JNIEnv *env, jclass clazz) {\n").append("    JNINativeMethod table[] = {\n").append(registrationMethods).append("    };\n").append(licenseInfo).append("\n").append("    (*env)->RegisterNatives(env, clazz, table, ").append(methodCount).append(");\n").append("}\n\n").toString());
                    } else if (config.getOptions() != null && StringUtils.isNotEmpty(config.getOptions().getExpireDate())) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        Date expire = null;
                        try {
                            expire = sdf.parse(config.getOptions().getExpireDate());
                        }catch (Exception e){

                        }
                        mainWriter.append(new StringBuilder().append("/* Native registration for <").append(className).append("> */\n").append("JNIEXPORT void JNICALL Java_").append(methodName).append("__00024myj2cLoader(JNIEnv *env, jclass clazz) {\n").append("    JNINativeMethod table[] = {\n").append(registrationMethods).append("    };\n").append("\n").append("if(time(NULL)<" + expire.getTime() / 1000 + "){\n").append("    (*env)->RegisterNatives(env, clazz, table, ").append(methodCount).append(");\n").append("}else{\n").append("    jvalue cstack0; memset(&cstack0, 0, sizeof(jvalue));\n").append("    jvalue cstack1; memset(&cstack1, 0, sizeof(jvalue));\n").append("    \n").append("    cstack0.l = (*env)->GetStaticObjectField(env, cc_system(env)->clazz, cc_system(env)->id_0); \n").append("    if ((*env)->ExceptionCheck(env)) { return; }\n").append("    cstack1.l = (*env)->NewString(env, ").append(stringObf ? Util.getStringObf(Util.utf82ints((locale.getLanguage().contains("zh") ? "应用已过期，停止运行！！！" : "The application has expired, stop running!!!"))) : "(unsigned short[]) {" + Util.utf82unicode((locale.getLanguage().contains("zh") ? "应用已过期，停止运行！！！" : "The application has expired, stop running!!!")) + "}").append(", ").append((locale.getLanguage().contains("zh") ? "应用已过期，停止运行！！！" : "The application has expired, stop running!!!").length()).append(");\n").append("    (*env)->CallVoidMethod(env, cstack0.l, cc_print(env)->method_0, cstack1.l);\n").append("    if ((*env)->ExceptionCheck(env)) { return; }\n").append("    exit(-1);\n").append("}\n").append("}\n\n").toString());
                    } else {
                        mainWriter.append(new StringBuilder().append("/* Native registration for <").append(className).append("> */\n").append("JNIEXPORT void JNICALL Java_").append(methodName).append("__00024myj2cLoader(JNIEnv *env, jclass clazz) {\n").append("    JNINativeMethod table[] = {\n").append(registrationMethods).append("    };\n").append("    (*env)->RegisterNatives(env, clazz, table, ").append(methodCount).append(");\n").append("}\n\n").toString());
                    }
                }
            }
        }
        mainWriter.close();
    }
}
