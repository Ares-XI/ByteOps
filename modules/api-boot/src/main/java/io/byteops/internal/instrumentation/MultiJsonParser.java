package io.byteops.internal.instrumentation;

import com.google.gson.Gson;
import io.byteops.internal.InternalBootManager;
import io.byteops.internal.util.data.ModifyConfigFormat;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class MultiJsonParser {
    public static final MultiJsonParser instance = new MultiJsonParser();

    private final Gson GSON = new Gson();

    public List<ModifyConfigFormat> loadAllModifyConfigs() {
        List<ModifyConfigFormat> result = new ArrayList<>();

        for (File jarFile : InternalBootManager.getClassPath()) {
            try {
                try (JarFile jar = new JarFile(jarFile)) {
                    if (jar.getJarEntry(InternalBootManager.getJsonName() + ".json") != null) {
                        JarClassLoader.instance.registerJar(jarFile);
                        parseConfigFromJar(jar, result);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }

        return result;
    }

    private void parseConfigFromJar(JarFile jar, List<ModifyConfigFormat> result) {
        try {
            JarEntry entry = jar.getJarEntry(InternalBootManager.getJsonName() + ".json");
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