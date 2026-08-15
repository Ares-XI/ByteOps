package io.byteops.boot;

import java.io.PrintStream;

public abstract class BootFlag {
    public static final class Name extends BootFlag {
        final String name;

        public Name(String name) {
            this.name = name;
        }
    }

    public static final class Version extends BootFlag {
        final String version;

        public Version(String version) {
            this.version = version;
        }
    }

    public static final class ConfigName extends BootFlag {
        final String configName;

        public ConfigName(String configName) {
            this.configName = configName;
        }
    }

    public static final class LogParser extends BootFlag {
        final boolean logParser;

        public LogParser(boolean logParser) {
            this.logParser = logParser;
        }
    }

    public static final class BlockedClass extends BootFlag {
        final Class<?> blockedClass;

        public BlockedClass(Class<?> blockedClass) {
            this.blockedClass = blockedClass;
        }
    }

    public static final class PrintedStream extends BootFlag {
        final PrintStream printStream;

        public PrintedStream(PrintStream printStream) {
            this.printStream = printStream;
        }
    }

    private BootFlag() {}
}
