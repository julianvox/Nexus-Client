package dev.nexus.module;

import dev.nexus.NexusClient;
import dev.nexus.module.impl.*;
import dev.nexus.util.Helper;
import dev.nexus.util.Names;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    public static final List<Module> MODULES = new ArrayList<>();

    private ModuleManager() {}

    public static void init() {
        CombatModules.register(MODULES);
        DodgeModule.register(MODULES);
        MovementModules.register(MODULES);
        RenderModules.register(MODULES);
        BlockEspModules.register(MODULES);
        WorldModules.register(MODULES);
        PlayerModules.register(MODULES);
        MiscModules.register(MODULES);
    }

    /** Her modul icin ESC > Kontroller > Tus Atamalari altinda bir tus satiri olusturur. */
    public static void registerKeys() {
        Map<Category, KeyMapping.Category> cats = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            cats.put(c, KeyMapping.Category.register(Identifier.fromNamespaceAndPath(NexusClient.MOD_ID, c.name().toLowerCase(java.util.Locale.ROOT))));
        }
        for (Module m : MODULES) {
            if (m.hidden) continue;
            // 0 = atanmamis (SDL: SDL_SCANCODE_UNKNOWN)
            m.keyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(m.keyId(), 0, cats.get(m.category)));
        }
    }

    /** Eski Ingilizce adlar da calissin diye takma adlar. */
    private static final String[][] ALIASES = {
            {"killaura", "OtoSaldırı"}, {"aimassist", "NişanYardımı"}, {"triggerbot", "OtoTetik"},
            {"velocity", "GeriTepme"}, {"reach", "Menzil"}, {"criticals", "Kritik"}, {"autogapple", "OtoElma"},
            {"shieldbreaker", "OtoKalkanKır"}, {"autododge", "OtoKaçınma"}, {"godmode", "Ölümsüzlük"}, {"god", "Ölümsüzlük"}, {"dodge", "OtoKaçınma"}, {"autoshield", "OtoKalkanKır"},
            {"fly", "Uçuş"}, {"speed", "Hız"}, {"sprint", "Koşu"}, {"nofall", "DüşmeKoruma"}, {"step", "Basamak"},
            {"highjump", "YüksekZıplama"}, {"glide", "Süzülme"}, {"jesus", "SudaYürü"}, {"spider", "Örümcek"},
            {"airjump", "HavadaZıpla"}, {"autowalk", "OtoYürü"}, {"fullbright", "Aydınlık"}, {"esp", "VarlıkGöster"},
            {"tracers", "Çizgiler"}, {"zoom", "Yakınlaştır"}, {"hud", "BilgiEkranı"}, {"oreesp", "MadenGöster"},
            {"storageesp", "DepoGöster"}, {"nuker", "BlokKırıcı"}, {"scaffold", "OtoKöprü"}, {"autotool", "OtoAlet"},
            {"automine", "OtoKazı"}, {"autoeat", "OtoYemek"}, {"autoarmor", "OtoZırh"}, {"autototem", "OtoTotem"},
            {"cheststealer", "SandıkSoyucu"}, {"autorespawn", "OtoDoğ"}, {"antiafk", "AFKKoruma"},
            {"panic", "Panik"}};

    public static Module get(String name) {
        String k = Names.norm(name);
        for (Module m : MODULES) if (Names.norm(m.name).equals(k)) return m;
        for (String[] a : ALIASES) {
            if (a[0].equals(k)) {
                for (Module m : MODULES) if (m.name.equals(a[1])) return m;
            }
        }
        return null;
    }

    public static List<Module> byCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : MODULES) if (m.category == c && !m.hidden) out.add(m);
        out.sort(Comparator.comparing(m -> m.name));
        return out;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();

        // Kontroller menusunden atanan tuslar
        for (Module m : MODULES) {
            if (m.keyMapping == null) continue;
            while (m.keyMapping.consumeClick()) {
                if (mc.player != null && mc.level != null && Helper.screen() == null) m.toggle();
            }
        }

        if (mc.player == null || mc.level == null) return;

        for (Module m : new ArrayList<>(MODULES)) {
            if (!m.enabled) continue;
            try {
                if (m.pendingInit) {
                    m.pendingInit = false;
                    m.onEnable();
                }
                m.onTick();
            } catch (Throwable t) {
                NexusClient.LOGGER.error("Modül {} hata verdi, kapatılıyor", m.name, t);
                m.enabled = false;
                try { m.onDisable(); } catch (Throwable ignored) {}
            }
        }
    }

    public static void disableAll() {
        for (Module m : MODULES) {
            if (m.enabled && !m.hidden) {
                boolean old = m.silent;
                m.silent = true;
                m.setEnabled(false);
                m.silent = old;
            }
        }
    }
}
