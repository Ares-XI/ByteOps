package io.gammax.internal;

import io.gammax.internal.instrumentation.GammaClassLoader;
import io.gammax.internal.instrumentation.GammaCacheRegistry;
import io.gammax.internal.instrumentation.GammaTransformer;

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

        GammaCacheRegistry.instance.loadCache();
        inst.addTransformer(GammaTransformer.instance);
    }

    private static void registerLibraries() {
        String[] extraDirs = {"libraries", "cache", "versions"};

        for(String extraDir: extraDirs) {
            try(Stream<Path> stream = Files.walk(Paths.get(extraDir)).filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".jar"))) {
                stream.forEach(path -> {
                    try {
                        GammaClassLoader.instance.registerJar(path.toFile());
                    } catch (Exception e) {
                        e.printStackTrace(System.err); //TODO catch with custom exception
                    }
                });
            } catch (Exception e) {
                e.printStackTrace(System.err); //TODO catch with custom exception
            }
        }
    }

    private static void registerClose() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                GammaCacheRegistry.instance.clearCache();
                GammaClassLoader.instance.close();
            } catch (Exception e) {
                e.printStackTrace(System.err); //TODO catch with custom exception
            }
        }));
    }
}
