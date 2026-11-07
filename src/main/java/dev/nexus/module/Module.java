package dev.nexus.module;

import dev.nexus.setting.Setting;
import dev.nexus.util.Names;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public static final Minecraft mc = Minecraft.getInstance();

    public final String name;
    public final String description;
    public final Category category;
    public final List<Setting> settings = new ArrayList<>();

    /** ESC > Kontroller > Tus Atamalari altinda gorunen tus (ModuleManager.registerKeys). */
    public KeyMapping keyMapping;

    public boolean enabled;
    public boolean hidden;
    public boolean pendingInit;
    public boolean silent;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <T extends Setting> T add(T s) {
        settings.add(s);
        return s;
    }

    /** Ceviri anahtari: key.nexus.mod.<ascii ad> */
    public String keyId() {
        StringBuilder sb = new StringBuilder("key.nexus.mod.");
        for (char ch : Names.norm(name).toCharArray()) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) sb.append(ch);
        }
        return sb.toString();
    }

    /** Atanan tusun ekranda gorunen adi, atanmamissa bos. */
    public String keyText() {
        if (keyMapping == null || keyMapping.isUnbound()) return "";
        return keyMapping.getTranslatedKeyMessage().getString();
    }

    public void toggle() { setEnabled(!enabled); }

    public void setEnabled(boolean v) {
        if (enabled == v) return;
        enabled = v;
        if (v) onEnable(); else onDisable();
        if (!silent && !hidden && mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal((v ? "\u00a7a+ " : "\u00a7c- ") + name));
        }
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
    public void onRender(GuiGraphicsExtractor g, float partialTick) {}

    public void resetSettings() {
        for (Setting s : settings) s.reset();
    }

    public Setting getSetting(String n) {
        String k = Names.norm(n);
        for (Setting s : settings) if (Names.norm(s.name).equals(k)) return s;
        return null;
    }
}
