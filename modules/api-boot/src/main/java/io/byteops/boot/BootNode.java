package io.byteops.boot;

public abstract sealed class BootNode permits BootNode.Name, BootNode.Version, BootNode.ConfigName, BootNode.BlockedClass {
    public static final class Name extends BootNode {
        final String name;

        public Name(String name) {
            this.name = name;
        }
    }

    public static final class Version extends BootNode {
        final String version;

        public Version(String version) {
            this.version = version;
        }
    }

    public static final class ConfigName extends BootNode {
        final String configName;

        public ConfigName(String configName) {
            this.configName = configName;
        }
    }

    public static final class BlockedClass extends BootNode {
        final Class<?> blockedClass;

        public BlockedClass(Class<?> blockedClass) {
            this.blockedClass = blockedClass;
        }
    }
}
