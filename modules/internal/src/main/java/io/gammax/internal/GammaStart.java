package io.gammax.internal;

import io.gammax.internal.exceptions.ModifyInternalException;
import io.gammax.internal.instrumentation.JarClassLoader;
import io.gammax.internal.instrumentation.DataCacheRegistry;
import io.gammax.internal.instrumentation.ModifyFormatTransformer;

import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static io.gammax.internal.util.ClassLoaderExtend.registerNatives;

public class GammaStart {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("=====================================");
        System.out.println("|| GammaX started! Version: 1.0 alpha");
        System.out.println("=====================================");

        registerLibraries();
        registerNatives();
        registerClose();

        DataCacheRegistry.instance.loadCache();
        inst.addTransformer(ModifyFormatTransformer.instance);
    }

    private static void registerLibraries() {
        String[] extraDirs = {"libraries", "cache", "versions"};

        for(String extraDir: extraDirs) {
            try(Stream<Path> stream = Files.walk(Paths.get(extraDir)).filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".jar"))) {
                stream.forEach(path -> {
                    try {
                        JarClassLoader.instance.registerJar(path.toFile());
                    } catch (Exception e) {
                        new ModifyInternalException(e).printStackTrace(System.err);
                    }
                });
            } catch (Exception e) {
                new ModifyInternalException(e).printStackTrace(System.err);
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
