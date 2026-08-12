package io.byteops.internal.util;

import io.byteops.internal.exceptions.ModifyInternalException;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.security.ProtectionDomain;

public final class ClassLoaderExtend {
    public static native void defineClass(ClassLoader loader, String name, byte[] bytecode, int opcode, int length, ProtectionDomain protectionDomain);

    public static void registerNatives() {
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
