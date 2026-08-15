package io.byteops.shadow;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import io.byteops.internal.InternalBootManager;
import io.byteops.internal.instrumentation.MultiJsonParser;
import io.byteops.internal.util.ModifyConfigFormat;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class ShadowJsonParser extends MultiJsonParser {
    static final ShadowJsonParser instance = new ShadowJsonParser();

    static void init() {
        setInstance(instance);
    }

    @Override
    public List<ModifyConfigFormat> loadAllModifyConfigs() {
        List<ModifyConfigFormat> result = new ArrayList<>();

        for (File jarFile : InternalBootManager.getInstance().getClassPath()) {
            try {
                try (JarFile jar = new JarFile(jarFile)) {
                    JarEntry entry = null;
                    if (!InternalBootManager.getInstance().getJsonName().equals("$byteops.json")) entry = jar.getJarEntry(InternalBootManager.getInstance().getJsonName() + ".json");
                    if (entry == null) entry = jar.getJarEntry("$byteops.json");
                    if (entry != null) {
                        ShadowClassLoader.instance.registerJar(jarFile);
                        try (InputStream is = jar.getInputStream(entry); Reader reader = new InputStreamReader(is)) {
                            ModifyConfigFormat config = GSON.fromJson(reader, ModifyConfigFormat.class);
                            if (config != null) result.add(config);
                        } catch (JsonIOException | JsonSyntaxException | IOException | SecurityException e) {
                            e.printStackTrace(InternalBootManager.getInstance().getPrintStream());
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace(InternalBootManager.getInstance().getPrintStream());
            }
        }

        return result;
    }
}
