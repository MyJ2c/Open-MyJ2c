package cn.muyang.env;

import cn.muyang.helpers.ProcessHelper;
import cn.muyang.utils.FileUtils;
import cn.muyang.utils.Zipper;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public class SetupManager {
    private static String OS = System.getProperty("os.name").toLowerCase();
    public static final Locale locale = Locale.getDefault();

    public static void init() {
        String platformTypeName = getPlatformTypeName();
        String fileName = null;
        String dirName = null;

        if (platformTypeName != null && !"".equals(platformTypeName)) {
            if (isLinux()) {
                fileName = "zig-linux-" + platformTypeName + "-0.10.0.tar.xz";
                dirName = "zig-linux-" + platformTypeName + "-0.10.0";
            } else if (isMacOS()) {
                fileName = "zig-macos-" + platformTypeName + "-0.10.0.tar.xz";
                dirName = "zig-macos-" + platformTypeName + "-0.10.0";
            } else if (isWindows()) {
                fileName = "zig-windows-" + platformTypeName + "-0.10.0.zip";
                dirName = "zig-windows-" + platformTypeName + "-0.10.0";
            }
        } else {
            if (locale.getLanguage().contains("zh")) {
                System.out.println("暂不支持该系统类型,请联系开发者");
            } else {
                System.out.println("This system is not supported. Please contact the developer");
            }
            return;
        }
        downloadZigCompiler(fileName, dirName);
    }

    private static String getPlatformTypeName() {
        String platform = System.getProperty("os.arch").toLowerCase();
        String platformTypeName;
        switch (platform) {
            case "x86_64":
            case "amd64":
                platformTypeName = "x86_64";
                break;
            case "aarch64":
                platformTypeName = "aarch64";
                break;
            case "x86":
                platformTypeName = "i386";
                break;
            default:
                platformTypeName = "";
                break;
        }
        return platformTypeName;
    }

    public static boolean isLinux() {
        return OS.indexOf("linux") >= 0;
    }

    public static boolean isMacOS() {
        return OS.indexOf("mac") >= 0 && OS.indexOf("os") > 0;
    }


    public static boolean isWindows() {
        return OS.indexOf("windows") >= 0;
    }

    public static void downloadZigCompiler(String fileName, String dirName) {
        try {
            String currentDir = System.getProperty("user.dir");
            if (Files.exists(Paths.get(currentDir + File.separator + dirName))) {
                String compilePath = currentDir + File.separator + dirName + File.separator + "zig" + (SetupManager.isWindows() ? ".exe" : "");
                if (Files.exists(Paths.get(compilePath))) {
                    ProcessHelper.ProcessResult compileRunresult = ProcessHelper.run(Paths.get(currentDir + File.separator + dirName), 160_000,
                            Arrays.asList(compilePath, "version"));
                    if (locale.getLanguage().contains("zh")) {
                        System.out.println("\nzig安装版本:" + compileRunresult.stdout);
                    } else {
                        System.out.println("\nZig install version:" + compileRunresult.stdout);
                    }
                    if (compileRunresult.stdout.contains("0.10.0")) {
                        if (locale.getLanguage().contains("zh")) {
                            System.out.println("交叉编译工具已安装:" + currentDir + File.separator + dirName);
                        } else {
                            System.out.println("Cross compilation tool installed:" + currentDir + File.separator + dirName);

                        }
                        return;
                    }
                }
                FileUtils.clearDirectory(currentDir + File.separator + dirName);
            }
            if (locale.getLanguage().contains("zh")) {
                System.out.println("正在下载交叉编译工具");
            } else {
                System.out.println("Downloading cross compilation tool");
            }
            if (locale.getLanguage().contains("zh")) {
                System.out.println("下载链接：https://ziglang.org/download/0.10.0/" + fileName);
            } else {
                System.out.println("Download link：https://ziglang.org/download/0.10.0/" + fileName);
            }
            InputStream in = new URL("https://ziglang.org/download/0.10.0/" + fileName).openStream();
            Files.copy(in, Paths.get(currentDir + File.separator + fileName), StandardCopyOption.REPLACE_EXISTING);
            if (locale.getLanguage().contains("zh")) {
                System.out.println("下载完成,正在解压");
            } else {
                System.out.println("Download completed, decompressing");
            }
            unzipFile(currentDir, fileName, currentDir);

            deleteFile(currentDir, fileName + ".temp");
            deleteFile(currentDir, fileName);
            if (locale.getLanguage().contains("zh")) {
                System.out.println("安装交叉编译工具完成");
            } else {
                System.out.println("Installation of cross compilation tool completed");
            }
            if (!SetupManager.isWindows()) {
                String compilePath = currentDir + File.separator + dirName + File.separator + "zig";
                ProcessHelper.run(Paths.get(currentDir), 160_000, Arrays.asList("chmod", "777", compilePath));
                if (locale.getLanguage().contains("zh")) {
                    System.out.println("设置运行权限成功");
                } else {
                    System.out.println("Successfully set running permission");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteFile(String path, String file) {
        new File(path + File.separator + file).delete();
    }

    public static void unzipFile(String path, String file, String destination) {
        try {
            Zipper.extract(Paths.get(path + File.separator + file), Paths.get(destination));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getZigGlobalCacheDirectory(boolean clear) {
        String platformTypeName = getPlatformTypeName();
        String dirName = null;
        if (platformTypeName != null && !"".equals(platformTypeName)) {
            if (isLinux()) {
                dirName = "zig-linux-" + platformTypeName + "-0.10.0";
            } else if (isMacOS()) {
                dirName = "zig-macos-" + platformTypeName + "-0.10.0";
            } else if (isWindows()) {
                dirName = "zig-windows-" + platformTypeName + "-0.10.0";
            }
        }
        String currentDir = System.getProperty("user.dir");
        if (Files.exists(Paths.get(currentDir + File.separator + dirName))) {
            String compilePath = currentDir + File.separator + dirName + File.separator + "zig" + (SetupManager.isWindows() ? ".exe" : "");
            if (Files.exists(Paths.get(compilePath))) {
                try {
                    ProcessHelper.ProcessResult compileRunresult = ProcessHelper.run(Paths.get(currentDir + File.separator + dirName), 160_000,
                            Arrays.asList(compilePath, "env"));
                    Gson gson = new Gson();
                    Map<String, String> map = gson.fromJson(compileRunresult.stdout, Map.class);
                    if (clear) {
                        FileUtils.clearDirectory(map.get("global_cache_dir"));
                    }
                    return map.get("global_cache_dir");
                } catch (IOException e) {
                }
            }
        }
        if (locale.getLanguage().contains("zh")) {
            System.out.println("获取zig临时文件目录失败");
        } else {
            System.out.println("Failed to get zig temporary file directory");
        }
        return "";
    }

}
