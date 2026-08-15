package io.byteops.boot;

import io.byteops.internal.InternalBootManager;

import java.io.File;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;

public final class BootManager {
    private BootManager() {}

    public static void init(Instrumentation inst, File[] libs, File[] classpath, BootFlag... args) {
        String name = "ByteOps";
        String version = "1.0-alpha-build-0";
        String configName = "byte-ops";
        boolean logParser = true;
        List<Class<?>> blockedClasses = new ArrayList<>();
        PrintStream printStream = System.out;

        for(BootFlag arg: args) {
            if(arg instanceof BootFlag.Name) name = ((BootFlag.Name) arg).name;
            else if(arg instanceof BootFlag.Version) version = ((BootFlag.Version) arg).version;
            else if(arg instanceof BootFlag.ConfigName) configName = ((BootFlag.ConfigName) arg).configName;
            else if(arg instanceof BootFlag.LogParser) logParser = ((BootFlag.LogParser) arg).logParser;
            else if(arg instanceof BootFlag.BlockedClass) blockedClasses.add(((BootFlag.BlockedClass) arg).blockedClass);
            else if(arg instanceof BootFlag.PrintedStream) printStream = ((BootFlag.PrintedStream) arg).printStream;
            else printStream.println("[WARN]: Unknown node");
        }

        InternalBootManager.getInstance().init(inst, libs, classpath, name, version, configName, logParser, blockedClasses.toArray(new Class<?>[0]), printStream);
    }
}