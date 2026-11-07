package dev.nexus.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

public final class Names {
    private Names() {}

    /** Turkce karakterleri ASCII'ye indirir, kucuk harfe cevirir (komut/arama icin). */
    public static String norm(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\u0130', '\u0131', 'I' -> sb.append('i');
                case '\u015e', '\u015f' -> sb.append('s');
                case '\u011e', '\u011f' -> sb.append('g');
                case '\u00dc', '\u00fc' -> sb.append('u');
                case '\u00d6', '\u00f6' -> sb.append('o');
                case '\u00c7', '\u00e7' -> sb.append('c');
                default -> sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static final Map<String, String> MOBS = Map.ofEntries(
            Map.entry("zombie", "Zombi"), Map.entry("skeleton", "\u0130skelet"), Map.entry("creeper", "Creeper"),
            Map.entry("spider", "\u00d6r\u00fcmcek"), Map.entry("cave_spider", "Ma\u011fara \u00d6r\u00fcmce\u011fi"),
            Map.entry("enderman", "Enderman"), Map.entry("witch", "Cad\u0131"), Map.entry("slime", "Bal\u00e7\u0131k"),
            Map.entry("magma_cube", "Magma K\u00fcp\u00fc"), Map.entry("ghast", "Ghast"), Map.entry("blaze", "Blaze"),
            Map.entry("wither_skeleton", "Wither \u0130skeleti"), Map.entry("zombified_piglin", "Zombile\u015fmi\u015f Piglin"),
            Map.entry("piglin", "Piglin"), Map.entry("piglin_brute", "Piglin Vah\u015fisi"), Map.entry("hoglin", "Hoglin"),
            Map.entry("phantom", "Hayalet"), Map.entry("drowned", "Bo\u011fulmu\u015f"), Map.entry("husk", "Husk"),
            Map.entry("stray", "Stray"), Map.entry("pillager", "Ya\u011fmac\u0131"), Map.entry("ravager", "Ravager"),
            Map.entry("evoker", "B\u00fcy\u00fcc\u00fc"), Map.entry("guardian", "Gardiyan"), Map.entry("shulker", "Shulker"),
            Map.entry("silverfish", "G\u00fcm\u00fc\u015fbal\u0131\u011f\u0131"), Map.entry("endermite", "Endermite"),
            Map.entry("warden", "Warden"), Map.entry("wither", "Wither"), Map.entry("ender_dragon", "Ender Ejderhas\u0131"),
            Map.entry("cow", "\u0130nek"), Map.entry("pig", "Domuz"), Map.entry("sheep", "Koyun"), Map.entry("chicken", "Tavuk"),
            Map.entry("horse", "At"), Map.entry("donkey", "E\u015fek"), Map.entry("wolf", "Kurt"), Map.entry("cat", "Kedi"),
            Map.entry("villager", "K\u00f6yl\u00fc"), Map.entry("iron_golem", "Demir Golem"), Map.entry("bat", "Yarasa"),
            Map.entry("rabbit", "Tav\u015fan"), Map.entry("fox", "Tilki"), Map.entry("bee", "Ar\u0131"),
            Map.entry("squid", "M\u00fcrekkepbal\u0131\u011f\u0131"), Map.entry("dolphin", "Yunus"),
            Map.entry("turtle", "Kaplumba\u011fa"), Map.entry("parrot", "Papa\u011fan"), Map.entry("llama", "Lama"),
            Map.entry("panda", "Panda"), Map.entry("polar_bear", "Kutup Ay\u0131s\u0131"), Map.entry("goat", "Ke\u00e7i"));

    /** Varligin Turkce adi (oyuncuysa kullanici adi). */
    public static String entity(Entity e) {
        if (e instanceof Player) return e.getName().getString();
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        String tr = MOBS.get(path);
        if (tr != null) return tr;
        String s = path.replace('_', ' ');
        return s.isEmpty() ? "?" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
