package io.byteops.internal.instrumentation;

import io.byteops.internal.InternalBootManager;
import org.jetbrains.annotations.ApiStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@ApiStatus.Internal
public abstract class JarClassLoader extends URLClassLoader implements AutoCloseable {
    private static JarClassLoader instance;

    public static JarClassLoader getInstance() {
        return instance;
    }

    protected static void setInstance(JarClassLoader instance) {
        if(instance.getClass().getName().equals("io.byteops.shadow.ShadowClassLoader")) JarClassLoader.instance = instance;
    }

    protected final Map<String, byte[]> byteCache = new ConcurrentHashMap<>();
    protected final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();
    protected final Map<String, JarFile> jarFiles = new HashMap<>();
    protected final Set<Class<?>> applyToDefine = new HashSet<>();

    public final Map<String, JarFile> getJarFiles() {
        return jarFiles;
    }

    public final Set<Class<?>> getApplyToDefine() {
        return applyToDefine;
    }

    @Override
    protected final Class<?> findClass(String name) throws ClassNotFoundException {
        if (classCache.containsKey(name)) return classCache.get(name);

        byte[] bytes = getClassBytes(name);
        if (bytes == null) throw new ClassNotFoundException(name);

        byteCache.put(name, bytes);
        Class<?> clazz = defineClass(name, bytes, 0, bytes.length);
        classCache.put(name, clazz);
        return clazz;
    }

    public final byte[] getClassBytes(String className) {
        if (byteCache.containsKey(className)) return byteCache.get(className);

        String classPath = className.replace('.', '/') + ".class";
        Map<String, JarFile> jarFiles = getJarFiles();

        for (JarFile jar : jarFiles.values()) {
            try {
                JarEntry entry = jar.getJarEntry(classPath);
                if (entry != null) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        byte[] data = new byte[4096];
                        int bytesRead;

                        while ((bytesRead = is.read(data, 0, data.length)) != -1) buffer.write(data, 0, bytesRead);

                        return buffer.toByteArray();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace(InternalBootManager.getInstance().getPrintStream());
            }
        }

        return null;
    }

    public JarClassLoader() {
        super(new URL[0]);
    }
}