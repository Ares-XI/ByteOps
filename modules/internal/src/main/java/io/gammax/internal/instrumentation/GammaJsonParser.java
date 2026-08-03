package io.gammax.internal.instrumentation;

import com.google.gson.Gson;
import io.gammax.internal.util.data.GammaConfigFormat;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class GammaJsonParser {

    public static final GammaJsonParser instance = new GammaJsonParser();

    private final Gson GSON = new Gson();

    public List<GammaConfigFormat> loadAllModifyConfigs() {
        List<GammaConfigFormat> result = new ArrayList<>();

        File pluginsDir = new File("plugins");

        if (pluginsDir.exists()) {
            File[] jarFiles = pluginsDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jarFiles != null) {
                for (File jarFile : jarFiles) {
                    try {
                        try (JarFile jar = new JarFile(jarFile)) {
                            if (jar.getJarEntry("gamma.json") != null) {
                                GammaClassLoader.instance.registerJar(jarFile);
                                parseConfigFromJar(jar, result);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace(System.err); //TODO catch with custom exception
                    }
                }
            }
        }

        return result;
    }

    private void parseConfigFromJar(JarFile jar, List<GammaConfigFormat> result) {
        try {
            JarEntry entry = jar.getJarEntry("gamma.json");
            try (InputStream is = jar.getInputStream(entry); Reader reader = new InputStreamReader(is)) {
                GammaConfigFormat config = GSON.fromJson(reader, GammaConfigFormat.class);
                if (config != null) result.add(config);
            }
        } catch (Exception e) {
            e.printStackTrace(System.err); //TODO catch with custom exception
        }
    }

    private GammaJsonParser() {}
}