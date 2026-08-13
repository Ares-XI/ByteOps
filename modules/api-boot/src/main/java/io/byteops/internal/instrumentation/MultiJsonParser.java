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
                    JarEntry paramEntry = null;
                    JarEntry fixedEntry = jar.getJarEntry("$byteops.json");

                    if(!InternalBootManager.getJsonName().equals("$byteops.json")) paramEntry = jar.getJarEntry(InternalBootManager.getJsonName());

                    if (paramEntry != null) {
                        JarClassLoader.instance.registerJar(jarFile);
                        try (InputStream is = jar.getInputStream(paramEntry); Reader reader = new InputStreamReader(is)) {
                            ModifyConfigFormat config = GSON.fromJson(reader, ModifyConfigFormat.class);
                            if (config != null) result.add(config);
                        } catch (JsonIOException | JsonSyntaxException | IOException | SecurityException e) {
                            e.printStackTrace(System.err);
                        }
                    }
                    else if (fixedEntry != null) {
                        JarClassLoader.instance.registerJar(jarFile);
                        try (InputStream is = jar.getInputStream(fixedEntry); Reader reader = new InputStreamReader(is)) {
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