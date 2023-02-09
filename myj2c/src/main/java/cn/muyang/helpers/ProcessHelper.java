package cn.muyang.helpers;

import cn.muyang.env.SetupManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ProcessHelper {
    public static class ProcessResult {
        public static final Locale locale = Locale.getDefault();
        public int exitCode;
        public long execTime;
        public boolean timeout;
        public String stdout;
        public String stderr;
        public String commandLine;

        public ProcessResult() {
            stdout = stderr = "";
        }

        public void check(String processName) {
            if (!timeout && exitCode == 0) {
                return;
            }
            if (timeout) {
                System.err.println(processName + " 编译超时,可能是您编译的类和方法太多或者机器性能较低导致编译超时");
            } else {
                if (commandLine.contains("zig") && commandLine.contains("myj2c")) {

                    if (locale.getLanguage().contains("zh")) {
                        System.err.println(processName + " 编译错误:" + stderr);
                        System.err.println("已为您自动清理zig临时文件:" + SetupManager.getZigGlobalCacheDirectory(true) + " 请重新运行");
                        System.out.println("如果再次运行失败,请手动删除后重试,手动删除后仍失败请反馈问题给开发者");
                    } else {
                        System.err.println(processName + " Compilation Error:" + stderr);
                        System.err.println("The zig temporary files have been automatically cleaned up for you :" + SetupManager.getZigGlobalCacheDirectory(true) + "  Please run again");
                        System.out.println(" Running again still failed. Please delete it manually and try again. If the manual deletion still fails, please feed back the problem to the developer ");
                    }
                }
            }
            //System.err.println("Command line: \n" + commandLine);
            System.err.println("exit: \n" + exitCode);
            System.err.println("stdout: \n" + stdout);
            System.err.println("stderr: \n" + stderr);
            throw new RuntimeException(processName + " " + (timeout ? "命令执行超时" : "命令执行出错"));
        }
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    private static void readStream(InputStream is, Consumer<String> consumer) {
        executor.submit(() -> {
            try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(isr)) {

                int count;
                char[] buf = new char[1 << 10];
                while ((count = reader.read(buf)) != -1) {
                    consumer.accept(String.copyValueOf(buf, 0, count));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public static ProcessResult run(Path directory, long timeLimit, List<String> command) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Map<String, String> environment = processBuilder.environment();
        environment.put("ZIG_GLOBAL_CACHE_DIR", directory + File.separator + "cpp" + File.separator + ".cache");
        environment.put("TEMP", directory + File.separator + "cpp" + File.separator + ".temp");
        environment.put("TMP", directory + File.separator + "cpp" + File.separator + ".temp");
        Process process = processBuilder.directory(directory.toFile()).start();
        long startTime = System.currentTimeMillis();

        ProcessResult result = new ProcessResult();
        result.commandLine = String.join(" ", command);

        StringBuilder stdoutBuilder = new StringBuilder();
        StringBuilder stderrBuilder = new StringBuilder();

        readStream(process.getInputStream(), stdoutBuilder::append);
        readStream(process.getErrorStream(), stderrBuilder::append);
        try {
            if (!process.waitFor(timeLimit, TimeUnit.MILLISECONDS)) {
                result.timeout = true;
                process.destroyForcibly();
            }
            process.waitFor();
        } catch (InterruptedException ignored) {
        }

        result.stdout = stdoutBuilder.toString();
        result.stderr = stderrBuilder.toString();
        result.execTime = System.currentTimeMillis() - startTime;
        result.exitCode = process.exitValue();
        if (process.exitValue() != 0) {
            try {
                process.waitFor(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
            process.destroyForcibly();
        }
        return result;
    }
}
