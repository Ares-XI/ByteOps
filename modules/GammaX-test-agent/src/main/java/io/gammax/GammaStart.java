package io.gammax;

import io.byteops.boot.BootManager;
import io.byteops.boot.BootNode;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class GammaStart {
    public static void premain(String args, Instrumentation instrumentation) {
        List<File> libs = new ArrayList<>();
        List<File> classpath = new ArrayList<>();

        String[] extraDirs = {"libraries", "cache", "versions"};
        for(String extraDir: extraDirs) {
            try(Stream<Path> stream = Files.walk(Paths.get(extraDir)).filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".jar"))) {
                stream.forEach(path -> libs.add(path.toFile()));
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }

        try (Stream<Path> stream = Files.list(Path.of("plugins"))) {
            stream.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".jar")).forEach(path -> classpath.add(path.toFile()));
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }

        System.out.println(libs.size());
        System.out.println(classpath.size());

        BootManager.init(instrumentation, libs.toArray(new File[0]), classpath.toArray(new File[0]), new BootNode.Name("GammaX"), new BootNode.Version("1.0-alpha"), new BootNode.ConfigName("gamma"));
    }
}