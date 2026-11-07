package dev.nexus.command;

import dev.nexus.config.ConfigManager;
import dev.nexus.gui.ClickGui;
import dev.nexus.module.Module;
import dev.nexus.module.ModuleManager;
import dev.nexus.setting.Setting;
import dev.nexus.util.FriendManager;
import dev.nexus.util.Helper;
import dev.nexus.util.Names;

public final class Commands {
    private Commands() {}

    /** true dönerse mesaj sunucuya gönderilmez. */
    public static boolean handle(String msg) {
        if (!msg.startsWith(".")) return false;
        String[] a = msg.substring(1).trim().split("\\s+");
        if (a.length == 0 || a[0].isEmpty()) return true;
        String cmd = Names.norm(a[0]);
        try {
            switch (cmd) {
                case "yardim", "help" -> {
                    Helper.msg(".menu  |  .ac <modül>  |  .ayar <modül> <ayar> <değer>  |  .moduller");
                    Helper.msg(".profil kaydet|yukle|liste [ad]  |  .arkadas ekle|sil|liste <isim>  |  .sifirla  |  .panik");
                    Helper.msg("Tuş atamak için: ESC > Seçenekler > Kontroller > Tuş Atamaları > Nexus");
                }
                case "menu", "gui" -> Helper.mc.execute(() -> Helper.openScreen(new ClickGui()));
                case "ac", "kapat", "t", "toggle" -> {
                    Module m = need(a, 1);
                    if (m != null) m.toggle();
                }
                case "ayar", "set" -> {
                    Module m = need(a, 1);
                    if (m == null) break;
                    if (a.length < 4) {
                        StringBuilder sb = new StringBuilder("Ayarlar: ");
                        for (Setting s : m.settings) sb.append(s.name).append('=').append(s.display()).append("  ");
                        Helper.msg(sb.toString());
                        break;
                    }
                    Setting s = m.getSetting(a[2]);
                    if (s == null) { Helper.msg("Böyle bir ayar yok: " + a[2]); break; }
                    s.setFromString(a[3]);
                    ConfigManager.save();
                    Helper.msg(m.name + "." + s.name + " = " + s.display());
                }
                case "moduller", "modules" -> {
                    StringBuilder sb = new StringBuilder();
                    for (Module m : ModuleManager.MODULES) if (!m.hidden) sb.append(m.enabled ? "\u00a7a" : "\u00a77").append(m.name).append(' ');
                    Helper.msg(sb.toString());
                }
                case "profil", "config", "cfg" -> {
                    String sub = a.length > 1 ? Names.norm(a[1]) : "liste";
                    switch (sub) {
                        case "kaydet", "save" -> {
                            ConfigManager.save(a.length > 2 ? a[2] : ConfigManager.current);
                            Helper.msg("Kaydedildi: " + ConfigManager.current);
                        }
                        case "yukle", "load" -> {
                            if (a.length < 3) { Helper.msg("Kullanım: .profil yukle <ad>"); break; }
                            Helper.msg(ConfigManager.load(a[2]) ? "Yüklendi: " + a[2] : "Bulunamadı: " + a[2]);
                        }
                        default -> Helper.msg("Profiller: " + String.join(", ", ConfigManager.list()));
                    }
                }
                case "arkadas", "dost", "friend", "f" -> {
                    String sub = a.length > 1 ? Names.norm(a[1]) : "liste";
                    switch (sub) {
                        case "ekle", "add" -> {
                            if (a.length < 3) { Helper.msg("Kullanım: .arkadas ekle <isim>"); break; }
                            FriendManager.NAMES.add(a[2].toLowerCase(java.util.Locale.ROOT));
                            ConfigManager.save();
                            Helper.msg("Arkadaş eklendi: " + a[2]);
                        }
                        case "sil", "remove", "del" -> {
                            if (a.length < 3) { Helper.msg("Kullanım: .arkadas sil <isim>"); break; }
                            FriendManager.NAMES.remove(a[2].toLowerCase(java.util.Locale.ROOT));
                            ConfigManager.save();
                            Helper.msg("Arkadaş silindi: " + a[2]);
                        }
                        default -> Helper.msg("Arkadaşlar: " + String.join(", ", FriendManager.NAMES));
                    }
                }
                case "sifirla", "reset" -> {
                    ConfigManager.resetAll();
                    Helper.msg("Tüm ayarlar sıfırlandı.");
                }
                case "panik", "panic" -> ModuleManager.disableAll();
                default -> Helper.msg("Bilinmeyen komut. .yardım yaz.");
            }
        } catch (Throwable t) {
            Helper.msg("Hata: " + t.getMessage());
        }
        return true;
    }

    private static Module need(String[] a, int i) {
        if (a.length <= i) { Helper.msg("Modül adı gerekli."); return null; }
        Module m = ModuleManager.get(a[i]);
        if (m == null) Helper.msg("Böyle bir modül yok: " + a[i]);
        return m;
    }
}
