package io.byteops.shadow;

import io.byteops.internal.InternalBootManager;
import io.byteops.internal.instrumentation.JarClassLoader;

import java.io.File;
import java.util.jar.JarFile;

final class ShadowClassLoader extends JarClassLoader {
    static final ShadowClassLoader instance = new ShadowClassLoader();

    static void init() {
        setInstance(instance);
    }

    void registerJar(File jarFile) throws Exception {
        JarFile jar = new JarFile(jarFile);
        jarFiles.put(jarFile.getAbsolutePath(), jar);
        super.addURL(jarFile.toURI().toURL());
    }

    void registerClassToDefine(Class<?> targetClass) {
        applyToDefine.add(targetClass);
    }

    @Override
    public void close() {
        byteCache.clear();
        classCache.clear();
        for (JarFile jar : jarFiles.values()) {
            try {
                jar.close();
            } catch (Exception e) {
                e.printStackTrace(InternalBootManager.getInstance().getPrintStream());
            }
        }
        jarFiles.clear();
    }
}
