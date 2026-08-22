package io.byteops.shadow;

import io.byteops.internal.InternalBootManager;
import io.byteops.internal.exceptions.ModifyInternalException;
import io.byteops.internal.util.ClassLoaderExtend;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.net.URL;
import java.nio.file.Files;

@ApiStatus.Internal
public abstract class ShadowUtils {
    public static final Class<?> SHADOW_CLASS_LOADER = ShadowClassLoader.class;
    public static final Class<?> SHADOW_JSON_PARSER = ShadowJsonParser.class;
    public static final Class<?> SHADOW_CACHE_REGISTRY = ShadowCacheRegistry.class;
    public static final Class<?> SHADOW_MODIFY_TRANSFORMER = ShadowModifyTransformer.class;

    private static boolean isLocked = false;

    protected void initAll(File[] libraries) {
        if(notEqualInstance()) return;
        if(isLocked) return;

        File[] currentLibraries = new File[]{};
        if(libraries != null) currentLibraries = libraries;
        if(currentLibraries.length == 0) InternalBootManager.getInstance().getPrintStream().println("[WARN]: libraries is empty");

        ShadowClassLoader.init();
        ShadowJsonParser.init();
        ShadowCacheRegistry.init();
        ShadowModifyTransformer.init();

        registerLibraries(currentLibraries);

        registerNatives();
        registerClose();

        ShadowCacheRegistry.instance.loadCache();
        isLocked = true;
    }

    protected ClassFileTransformer getTransformer() {
        if(notEqualInstance()) return null;
        return ShadowModifyTransformer.instance;
    }

    private boolean notEqualInstance() {
        return !getClass().equals(InternalBootManager.class);
    }

    private void registerLibraries(File[] files) {
        for(File file: files) {
            try {
                ShadowClassLoader.instance.registerJar(file);
            } catch (Exception e) {
                e.printStackTrace(InternalBootManager.getInstance().getPrintStream());
            }
        }
    }

    private void registerClose() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ShadowCacheRegistry.instance.clearCache();
            ShadowClassLoader.instance.close();
        }));
    }

    private void registerNatives() {
        String osName = System.mapLibraryName("class-loader-library");
        String osBit = System.getProperty("sun.arch.data.model").equals("32") ? "x86" : "x64";
        String osExt = osName.substring(osName.lastIndexOf("."));

        String resourcePath = "native/class-loader-library-" + osBit + osExt;
        URL url = ClassLoaderExtend.class.getClassLoader().getResource(resourcePath);

        if(url == null) throw new RuntimeException("resource not found: " + resourcePath);

        File file;

        try {
            file = File.createTempFile(osName.substring(0, osName.lastIndexOf(".")), osExt);
        } catch (IOException e) {
            throw new ModifyInternalException(e);
        }

        try (InputStream is = url.openStream(); OutputStream os = Files.newOutputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) os.write(buffer, 0, bytesRead);
        } catch (IOException e) {
            throw new ModifyInternalException(e);
        }

        System.load(file.toPath().toString());
        file.deleteOnExit();
    }
}
