package dev.nexus.module.impl;

import dev.nexus.module.Category;
import dev.nexus.module.Module;
import dev.nexus.module.ModuleManager;

import java.util.List;

public final class MiscModules {
    private MiscModules() {}

    public static void register(List<Module> l) {
        l.add(new Panic());
    }

    public static class Panic extends Module {
        public Panic() { super("Panik", "Tüm modülleri kapatır", Category.MISC); }

        @Override public void onEnable() {
            ModuleManager.disableAll();
            enabled = false;
        }
    }
}
