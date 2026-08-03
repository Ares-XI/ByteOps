package io.gammax.test;

import io.gammax.test.commads.TestMixinCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getCommand("test-mixin").setExecutor(new TestMixinCommand());
    }
}
