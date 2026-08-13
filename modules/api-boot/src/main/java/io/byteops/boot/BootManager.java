package io.byteops.boot;

import io.byteops.internal.InternalBootManager;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class BootManager {
    public static void init(Instrumentation inst, File[] libs, File[] classpath, BootFlag... args) {
        if(inst == null) throw new NullPointerException("Instrumentation must be not null");

        List<File> jarLibs = new ArrayList<>();
        List<File> jarClasspath = new ArrayList<>();

        String jarPath = getCurrentJarPath();
        if (jarPath != null) jarLibs.add(new File(jarPath));

        if(libs != null) for(File file: libs) if(file.getName().endsWith(".jar")) jarLibs.add(file);
        if(classpath != null) for(File file: classpath) if(file.getName().endsWith(".jar")) jarClasspath.add(file);
        if(jarLibs.isEmpty()) System.out.println("[WARN]: Libraries are empty");
        if(jarClasspath.isEmpty()) System.out.println("[WARN]: ClassPath are empty");

        String name = "ByteOps";
        String version = "1.0-alpha-build-0";
        String configName = "byte-ops";
        List<Class<?>> blockedClasses = new ArrayList<>();

        for(BootFlag arg: args) {
            if(arg instanceof BootFlag.Name) name = ((BootFlag.Name) arg).name;
            else if(arg instanceof BootFlag.Version) version = ((BootFlag.Version) arg).version;
            else if(arg instanceof BootFlag.ConfigName) configName = ((BootFlag.ConfigName) arg).configName;
            else if(arg instanceof BootFlag.BlockedClass) blockedClasses.add(((BootFlag.BlockedClass) arg).blockedClass);
            else System.out.println("[WARN]: Unknown node");
        }

        InternalBootManager.init(inst, jarLibs.toArray(new File[0]), jarClasspath.toArray(new File[0]), name, version, configName, blockedClasses.toArray(new Class<?>[0]));
    }

    private static String getCurrentJarPath() {
        try {
            String path = BootManager.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            return URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }
    }
}