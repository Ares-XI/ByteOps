package io.byteops.internal.instrumentation;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import io.byteops.internal.InternalBootManager;
import io.byteops.internal.util.ModifyConfigFormat;

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
                    JarEntry entry = null;
                    if (!InternalBootManager.getJsonName().equals("$byteops.json")) entry = jar.getJarEntry(InternalBootManager.getJsonName() + ".json");
                    if (entry == null) entry = jar.getJarEntry("$byteops.json");
                    if (entry != null) {
                        JarClassLoader.instance.registerJar(jarFile);
                        try (InputStream is = jar.getInputStream(entry); Reader reader = new InputStreamReader(is)) {
                            ModifyConfigFormat config = GSON.fromJson(reader, ModifyConfigFormat.class);
                            if (config != null) result.add(config);
                        } catch (JsonIOException | JsonSyntaxException | IOException | SecurityException e) {
                            e.printStackTrace(System.err);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }

        return result;
    }

    private MultiJsonParser() {}
}