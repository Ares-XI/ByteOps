package io.gammax.internal.instrumentation;

import com.google.gson.Gson;
import io.gammax.internal.util.data.ModifyConfigFormat;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class MultiJsonParser {
    public static final MultiJsonParser instance = new MultiJsonParser();

    private final Gson GSON = new Gson();

    public List<ModifyConfigFormat> loadAllModifyConfigs() {
        List<ModifyConfigFormat> result = new ArrayList<>();

        File pluginsDir = new File("plugins");

        if (pluginsDir.exists()) {
            File[] jarFiles = pluginsDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jarFiles != null) {
                for (File jarFile : jarFiles) {
                    try {
                        try (JarFile jar = new JarFile(jarFile)) {
                            if (jar.getJarEntry("gamma.json") != null) {
                                JarClassLoader.instance.registerJar(jarFile);
                                parseConfigFromJar(jar, result);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                    }
                }
            }
        }

        return result;
    }

    private void parseConfigFromJar(JarFile jar, List<ModifyConfigFormat> result) {
        try {
            JarEntry entry = jar.getJarEntry("gamma.json");
            try (InputStream is = jar.getInputStream(entry); Reader reader = new InputStreamReader(is)) {
                ModifyConfigFormat config = GSON.fromJson(reader, ModifyConfigFormat.class);
                if (config != null) result.add(config);
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    private MultiJsonParser() {}
}