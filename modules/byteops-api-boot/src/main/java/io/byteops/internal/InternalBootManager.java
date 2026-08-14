package io.byteops.internal;

import io.byteops.internal.instrumentation.JarClassLoader;
import io.byteops.internal.instrumentation.DataCacheRegistry;
import io.byteops.internal.instrumentation.ModifyFormatTransformer;

import java.io.File;
import java.lang.instrument.Instrumentation;

import static io.byteops.internal.util.ClassLoaderExtend.registerNatives;

public class InternalBootManager {
    private static String jsonName;
    private static File[] classPath;
    private static Class<?>[] blockedClasses;

    public static void init(Instrumentation inst, File[] jarLibraries, File[] jarsToModify, String name, String version, String jsonConfigName, Class<?>[] classesToBlock) {
        System.out.println("=====================================");
        System.out.println("|| " + name + " started! Version: " + version);
        System.out.println("=====================================");

        classPath = jarsToModify;
        jsonName = jsonConfigName;
        blockedClasses = classesToBlock;

        registerLibraries(jarLibraries);

        registerNatives();
        registerClose();

        DataCacheRegistry.instance.loadCache();
        inst.addTransformer(ModifyFormatTransformer.instance);
    }

    public static String getJsonName() {
        return jsonName;
    }

    public static File[] getClassPath() {
        return classPath;
    }

    public static Class<?>[] getBlockedClasses() {
        return blockedClasses;
    }

    private static void registerLibraries(File[] files) {
        for(File file: files) {
            try {
                JarClassLoader.instance.registerJar(file);
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }

    private static void registerClose() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            DataCacheRegistry.instance.clearCache();
            JarClassLoader.instance.close();
        }));
    }
}
