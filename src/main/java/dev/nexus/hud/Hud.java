package dev.nexus.hud;

import dev.nexus.NexusClient;
import dev.nexus.config.ConfigManager;
import dev.nexus.module.Module;
import dev.nexus.module.ModuleManager;
import dev.nexus.module.impl.CombatModules;
import dev.nexus.module.impl.RenderModules;
import dev.nexus.util.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Hud {
    private Hud() {}

    public static int rainbow(double offset, boolean on) {
        if (!on) return 0xFFB388FF;
        double t = System.currentTimeMillis() / 1500.0 + offset;
        int r = (int) (127 + 128 * Math.sin(t * Math.PI * 2));
        int g = (int) (127 + 128 * Math.sin(t * Math.PI * 2 + 2.094));
        int b = (int) (127 + 128 * Math.sin(t * Math.PI * 2 + 4.188));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Bilgi ekranı renkleri: mor-sarı-beyaz tema (RGB kapalıysa tek renk). */
    static int theme(double off, boolean on) {
        return on ? Theme.color(off) : 0xFFB388FF;
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Dunya uzerine cizim yapan moduller (ESP, Tracers, OreESP ...)
        float pt = dt.getGameTimeDeltaPartialTick(false);
        for (Module m : new ArrayList<>(ModuleManager.MODULES)) {
            if (!m.enabled) continue;
            try {
                m.onRender(g, pt);
            } catch (Throwable t) {
                NexusClient.LOGGER.error("Modül {} çizim hatası verdi, kapatılıyor", m.name, t);
                m.enabled = false;
            }
        }

        Module hm = ModuleManager.get("HUD");
        if (!(hm instanceof RenderModules.HUD hud) || !hud.enabled) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        if (hud.watermark.get()) {
            String t = "Nexus \u00a77[" + ConfigManager.current + "]";
            g.fill(2, 2, 6 + mc.font.width(t) + 2, 14, 0x90000000);
            g.text(mc.font, t, 5, 4, theme(0, hud.rainbow.get()), true);
        }

        if (hud.arraylist.get()) {
            List<Module> on = new ArrayList<>();
            for (Module m : ModuleManager.MODULES) if (m.enabled && !m.hidden) on.add(m);
            on.sort(Comparator.comparingInt((Module m) -> mc.font.width(m.name)).reversed());
            int y = 2, i = 0;
            for (Module m : on) {
                int w = mc.font.width(m.name);
                int x = sw - w - 5;
                g.fill(x - 2, y, sw, y + 11, 0x90000000);
                g.fill(sw - 2, y, sw, y + 11, theme(i * 0.06, hud.rainbow.get()));
                g.text(mc.font, m.name, x, y + 2, theme(i * 0.06, hud.rainbow.get()), true);
                y += 11;
                i++;
            }
        }

        // Hedef bilgisi (KillAura)
        LivingEntity tg = CombatModules.KillAura.target;
        Module ka = CombatModules.KillAura.INSTANCE;
        if (hud.targetHud.get() && ka != null && ka.enabled && tg != null && tg.isAlive()) {
            int bx = sw / 2 + 16, by = sh / 2 + 16, bw = 120, bh = 34;
            g.fill(bx, by, bx + bw, by + bh, 0xB0101018);
            g.fill(bx, by, bx + 2, by + bh, theme(0, hud.rainbow.get()));
            String name = tg.getName().getString();
            g.text(mc.font, name, bx + 6, by + 4, 0xFFFFFFFF, true);
            float hp = tg.getHealth(), max = Math.max(1f, tg.getMaxHealth());
            String hpText = "Can: " + String.format(java.util.Locale.ROOT, "%.1f / %.0f", hp, max);
            g.text(mc.font, hpText, bx + 6, by + 15, 0xFFDDDDDD, true);
            int barW = bw - 12;
            g.fill(bx + 6, by + 26, bx + 6 + barW, by + 30, 0xFF2A2A34);
            float ratio = Math.max(0f, Math.min(1f, hp / max));
            int col = ratio > 0.5f ? 0xFF55FF55 : (ratio > 0.25f ? 0xFFFFC040 : 0xFFFF4040);
            g.fill(bx + 6, by + 26, bx + 6 + (int) (barW * ratio), by + 30, col);
        }

        int by = sh - 12;
        if (hud.coords.get()) {
            String c = String.format(java.util.Locale.ROOT, "Konum: %.1f / %.1f / %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
            g.text(mc.font, c, 4, by, 0xFFFFFFFF, true);
            by -= 11;
        }
        if (hud.fps.get()) {
            g.text(mc.font, mc.getFps() + " FPS", 4, by, 0xFFFFFFFF, true);
        }
    }
}
