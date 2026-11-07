package dev.nexus.config;

import com.google.gson.*;
import dev.nexus.NexusClient;
import dev.nexus.module.Module;
import dev.nexus.module.ModuleManager;
import dev.nexus.module.impl.RenderModules;
import dev.nexus.setting.Setting;
import dev.nexus.util.FriendManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Profiller: .minecraft/config/nexus/<ad>.json. Tuşlar vanilla Kontroller menüsünde (options.txt) tutulur. */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("nexus");
    public static String current = "default";

    private ConfigManager() {}

    private static Path file(String name) {
        return DIR.resolve(name.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".json");
    }

    public static void init() {
        try {
            Files.createDirectories(DIR);
            Path last = DIR.resolve("_last.txt");
            if (Files.exists(last)) current = Files.readString(last, StandardCharsets.UTF_8).trim();
            if (current.isEmpty()) current = "default";
        } catch (IOException e) {
            NexusClient.LOGGER.error("Config başlatılamadı", e);
        }
        load(current);
    }

    public static void save() { save(current); }

    public static void save(String name) {
        try {
            Files.createDirectories(DIR);
            JsonObject root = new JsonObject();

            JsonObject mods = new JsonObject();
            for (Module m : ModuleManager.MODULES) {
                JsonObject o = new JsonObject();
                o.addProperty("enabled", m.enabled && !m.hidden);
                JsonObject st = new JsonObject();
                for (Setting s : m.settings) st.add(s.name, s.save());
                o.add("settings", st);
                mods.add(m.name, o);
            }
            root.add("modules", mods);

            JsonArray fr = new JsonArray();
            for (String n : FriendManager.NAMES) fr.add(n);
            root.add("friends", fr);

            JsonObject ui = new JsonObject();
            ui.addProperty("slimFont", UiPrefs.slimFont);
            ui.addProperty("fx", UiPrefs.fx);
            root.add("ui", ui);

            Files.writeString(file(name), GSON.toJson(root), StandardCharsets.UTF_8);
            current = name;
            Files.writeString(DIR.resolve("_last.txt"), name, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NexusClient.LOGGER.error("Config kaydedilemedi", e);
        }
    }

    public static boolean load(String name) {
        Path f = file(name);
        if (!Files.exists(f)) {
            current = name;
            return false;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();

            if (root.has("modules")) {
                JsonObject mods = root.getAsJsonObject("modules");
                for (Module m : ModuleManager.MODULES) {
                    if (!mods.has(m.name)) continue;
                    JsonObject o = mods.getAsJsonObject(m.name);
                    if (o.has("settings")) {
                        JsonObject st = o.getAsJsonObject("settings");
                        for (Setting s : m.settings) if (st.has(s.name)) s.load(st.get(s.name));
                    }
                    if (!m.hidden && o.has("enabled")) {
                        boolean want = o.get("enabled").getAsBoolean();
                        if (m.enabled && !want) {
                            boolean old = m.silent;
                            m.silent = true;
                            m.setEnabled(false);
                            m.silent = old;
                        } else if (!m.enabled && want) {
                            m.enabled = true;
                            m.pendingInit = true;
                        }
                    }
                }
            }

            if (root.has("friends")) {
                FriendManager.NAMES.clear();
                for (JsonElement e : root.getAsJsonArray("friends")) FriendManager.NAMES.add(e.getAsString().toLowerCase(java.util.Locale.ROOT));
            }

            if (root.has("ui")) {
                JsonObject ui = root.getAsJsonObject("ui");
                if (ui.has("slimFont")) UiPrefs.slimFont = ui.get("slimFont").getAsBoolean();
                if (ui.has("fx")) UiPrefs.fx = ui.get("fx").getAsBoolean();
            }

            current = name;
            Files.writeString(DIR.resolve("_last.txt"), name, StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            NexusClient.LOGGER.error("Config yüklenemedi", e);
            return false;
        }
    }

    public static void resetAll() {
        for (Module m : ModuleManager.MODULES) {
            if (m.enabled && !m.hidden) {
                boolean old = m.silent;
                m.silent = true;
                m.setEnabled(false);
                m.silent = old;
            }
            m.resetSettings();
            if (m instanceof RenderModules.HUD) m.enabled = true;
        }
        save();
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(DIR)) {
            s.forEach(p -> {
                String n = p.getFileName().toString();
                if (n.endsWith(".json")) out.add(n.substring(0, n.length() - 5));
            });
        } catch (IOException ignored) {}
        out.sort(String::compareToIgnoreCase);
        return out;
    }
}
