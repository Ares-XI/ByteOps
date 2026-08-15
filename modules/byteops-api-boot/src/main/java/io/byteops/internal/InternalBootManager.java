package io.byteops.internal;

import io.byteops.boot.BootManager;
import io.byteops.shadow.ShadowUtils;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class InternalBootManager extends ShadowUtils {
    private static final InternalBootManager instance = new InternalBootManager();
    private static boolean isLocked = false;

    public static InternalBootManager getInstance() {
        return instance;
    }

    private static String getCurrentJarPath(PrintStream stream) {
        try {
            String path = BootManager.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            return URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            e.printStackTrace(stream);
            return null;
        }
    }

    public void init(Instrumentation inst, File[] jarLibraries, File[] jarsToModify, String name, String version, String jsonConfigName, boolean isLogParser, Class<?>[] classesToBlock, PrintStream printStream) {
        if(!isLocked) {
            if(inst == null) {
                new NullPointerException("Instrumentation must be not null").printStackTrace(printStream);
                return;
            }

            List<File> jarLibs = new ArrayList<>();
            List<File> jarClasspath = new ArrayList<>();

            String jarPath = getCurrentJarPath(printStream);
            if (jarPath != null) jarLibs.add(new File(jarPath));

            if(jarLibraries != null) for(File file: jarLibraries) if(file.getName().endsWith(".jar")) jarLibs.add(file);
            if(jarsToModify != null) for(File file: jarsToModify) if(file.getName().endsWith(".jar")) jarClasspath.add(file);
            if(jarLibs.isEmpty()) printStream.println("[WARN]: Libraries are empty");
            if(jarClasspath.isEmpty()) printStream.println("[WARN]: ClassPath are empty");

            printStream.println("=====================================");
            printStream.println("|| " + name + " started! Version: " + version);
            printStream.println("=====================================");

            classPath = jarClasspath.toArray(new File[0]);
            jsonName = jsonConfigName;
            logParser = isLogParser;
            blockedClasses = classesToBlock;
            stream = printStream;

            super.initAll(jarLibs.toArray(new File[0]));
            inst.addTransformer(super.getTransformer());
            isLocked = true;
        }
    }

    private String jsonName;
    private boolean logParser;
    private File[] classPath;
    private Class<?>[] blockedClasses;
    private PrintStream stream;

    public String getJsonName() {
        return jsonName;
    }

    public boolean isLogParser() {
        return logParser;
    }

    public File[] getClassPath() {
        return classPath;
    }

    public Class<?>[] getBlockedClasses() {
        return blockedClasses;
    }

    public PrintStream getPrintStream() {
        return stream;
    }

    private InternalBootManager() {}
}