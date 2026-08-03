package io.gammax.internal.instrumentation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class GammaClassLoader extends URLClassLoader implements AutoCloseable {

    public static final GammaClassLoader instance = new GammaClassLoader();

    private final Map<String, byte[]> byteCache = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> classCache = new ConcurrentHashMap<>();
    private final Map<String, JarFile> jarFiles = new HashMap<>();
    private final Set<Class<?>> applyToDefine = new HashSet<>();

    public Map<String, JarFile> getJarFiles() {
        return jarFiles;
    }

    public Set<Class<?>> getApplyToDefine() {
        return applyToDefine;
    }

    public void registerJar(File jarFile) throws Exception {
        JarFile jar = new JarFile(jarFile);
        jarFiles.put(jarFile.getAbsolutePath(), jar);
        super.addURL(jarFile.toURI().toURL());
    }

    public void registerClassToDefine(Class<?> targetClass) {
        applyToDefine.add(targetClass);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (classCache.containsKey(name)) return classCache.get(name);

        byte[] bytes = getClassBytes(name);
        if (bytes == null) throw new ClassNotFoundException(name);

        byteCache.put(name, bytes);
        Class<?> clazz = defineClass(name, bytes, 0, bytes.length);
        classCache.put(name, clazz);
        return clazz;
    }

    public byte[] getClassBytes(String className) {
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
                e.printStackTrace(System.err); //TODO catch with custom exception
            }
        }

        return null;
    }

    @Override
    public void close() {
        byteCache.clear();
        classCache.clear();
        for (JarFile jar : jarFiles.values()) {
            try {
                jar.close();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
        jarFiles.clear();
    }

    private GammaClassLoader() {
        super(new URL[0]);
    }
}