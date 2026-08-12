package io.byteops.boot;

public abstract sealed class BootFlag permits BootFlag.Name, BootFlag.Version, BootFlag.ConfigName, BootFlag.BlockedClass {
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

    public static final class BlockedClass extends BootFlag {
        final Class<?> blockedClass;

        public BlockedClass(Class<?> blockedClass) {
            this.blockedClass = blockedClass;
        }
    }
}
