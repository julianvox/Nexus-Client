package dev.nexus.module.impl;

import dev.nexus.hud.Hud;
import dev.nexus.module.Category;
import dev.nexus.module.Module;
import dev.nexus.setting.BoolSetting;
import dev.nexus.setting.ListSetting;
import dev.nexus.setting.MobTable;
import dev.nexus.setting.NumSetting;
import dev.nexus.util.FriendManager;
import dev.nexus.util.Helper;
import dev.nexus.util.Names;
import dev.nexus.util.Overlay;
import dev.nexus.util.TargetFilter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class RenderModules {
    private RenderModules() {}

    public static void register(List<Module> l) {
        l.add(new Fullbright());
        l.add(new ESP());
        l.add(new Tracers());
        l.add(new Zoom());
        l.add(new HUD());
    }

    static boolean shows(Entity e, boolean pl, boolean mo, boolean an) {
        if (!(e instanceof LivingEntity) || e == Module.mc.player) return false;
        if (e instanceof Player) return pl;
        if (e instanceof Enemy) return mo;
        return an;
    }

    public static class Fullbright extends Module {
        public Fullbright() { super("Aydınlık", "Her yer aydınlık görünür", Category.RENDER); }

        @Override public void onTick() {
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
        }

        @Override public void onDisable() {
            if (mc.player != null) mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    public static class ESP extends Module {
        final ListSetting targets = add(MobTable.create("Hedefler", true, true, false, false));
        final NumSetting range = add(new NumSetting("Menzil", 16, 128, 4, 48));
        final BoolSetting glow = add(new BoolSetting("Parıltı", true));
        final BoolSetting boxes = add(new BoolSetting("Kutular", true));
        final BoolSetting labels = add(new BoolSetting("Yazılar", true));
        final BoolSetting rainbow = add(new BoolSetting("RGB", true));
        private List<Entity> cache = new ArrayList<>();

        public ESP() { super("VarlıkGöster", "Oyuncu ve canlıları duvar arkasından gösterir", Category.RENDER); }

        @Override public void onTick() {
            List<Entity> list = new ArrayList<>();
            for (Entity e : Helper.nearby(range.get())) {
                if (!(e instanceof LivingEntity) || e == mc.player) continue;
                boolean show = TargetFilter.allow(e, targets, false, false, false);
                e.setGlowingTag(glow.get() && show);
                if (show) list.add(e);
            }
            cache = list;
        }

        @Override public void onRender(GuiGraphicsExtractor g, float pt) {
            if (!boxes.get() && !labels.get()) return;
            Overlay.begin(pt);
            double[] a = new double[2];
            int i = 0;
            for (Entity e : cache) {
                if (e.isRemoved() || !e.isAlive()) continue;
                Vec3 pos = e.getPosition(pt);
                double hw = e.getBbWidth() / 2.0, h = e.getBbHeight();
                int col = FriendManager.isFriend(e) ? 0xFF55FF55 : Hud.rainbow(i++ * 0.1, rainbow.get());
                if (boxes.get()) Overlay.flatBox(g, pos.x - hw, pos.y, pos.z - hw, pos.x + hw, pos.y + h, pos.z + hw, col);
                if (labels.get() && Overlay.project(pos.x, pos.y + h + 0.25, pos.z, a)) {
                    LivingEntity le = (LivingEntity) e;
                    String t = Names.entity(e) + " " + (int) Math.ceil(le.getHealth()) + "HP " + (int) mc.player.distanceTo(e) + "m";
                    Overlay.label(g, t, a[0], a[1], col);
                }
            }
        }

        @Override public void onDisable() {
            cache = new ArrayList<>();
            if (!Helper.ready()) return;
            for (Entity e : Helper.nearby(160)) e.setGlowingTag(false);
        }
    }

    public static class Tracers extends Module {
        final ListSetting targets = add(MobTable.create("Hedefler", true, false, false, false));
        final NumSetting range = add(new NumSetting("Menzil", 16, 128, 4, 48));
        final NumSetting thickness = add(new NumSetting("Kalınlık", 1, 3, 1, 1));
        final BoolSetting rainbow = add(new BoolSetting("RGB", true));
        private List<Entity> cache = new ArrayList<>();

        public Tracers() { super("Çizgiler", "Nişangahtan varlıklara çizgi çeker", Category.RENDER); }

        @Override public void onTick() {
            List<Entity> list = new ArrayList<>();
            for (Entity e : Helper.nearby(range.get())) {
                if (TargetFilter.allow(e, targets, false, false, false)) list.add(e);
            }
            cache = list;
        }

        @Override public void onRender(GuiGraphicsExtractor g, float pt) {
            Overlay.begin(pt);
            double[] a = new double[2];
            int i = 0;
            for (Entity e : cache) {
                if (e.isRemoved() || !e.isAlive()) continue;
                Vec3 pos = e.getPosition(pt);
                if (!Overlay.project(pos.x, pos.y + e.getBbHeight() / 2.0, pos.z, a)) continue;
                int col = FriendManager.isFriend(e) ? 0xFF55FF55 : Hud.rainbow(i++ * 0.1, rainbow.get());
                Overlay.line(g, Overlay.sw / 2.0, Overlay.sh / 2.0, a[0], a[1], col, thickness.getInt());
            }
        }

        @Override public void onDisable() { cache = new ArrayList<>(); }
    }

    public static class Zoom extends Module {
        final NumSetting fov = add(new NumSetting("GörüşAçısı", 10, 60, 1, 30));
        Integer prev;

        public Zoom() { super("Yakınlaştır", "Görüntüyü yakınlaştırır", Category.RENDER); }

        @Override public void onEnable() {
            if (mc.options != null) prev = mc.options.fov().get();
        }

        @Override public void onTick() {
            if (prev == null) prev = mc.options.fov().get();
            mc.options.fov().set(Math.max(30, fov.getInt()));
        }

        @Override public void onDisable() {
            if (prev != null && mc.options != null) mc.options.fov().set(prev);
            prev = null;
        }
    }

    public static class HUD extends Module {
        public final BoolSetting watermark = add(new BoolSetting("Logo", true));
        public final BoolSetting arraylist = add(new BoolSetting("ModülListesi", true));
        public final BoolSetting coords = add(new BoolSetting("Koordinat", true));
        public final BoolSetting fps = add(new BoolSetting("FPS", true));
        public final BoolSetting targetHud = add(new BoolSetting("HedefBilgisi", true));
        public final BoolSetting rainbow = add(new BoolSetting("RGB", true));

        public HUD() {
            super("BilgiEkranı", "Ekran bilgilerini gösterir", Category.RENDER);
            this.enabled = true;
        }
    }
}
