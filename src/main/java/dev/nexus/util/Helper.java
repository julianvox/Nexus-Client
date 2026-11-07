package dev.nexus.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class Helper {
    public static final Minecraft mc = Minecraft.getInstance();

    private Helper() {}

    public static boolean ready() {
        return mc.player != null && mc.level != null && mc.gameMode != null;
    }

    // ---- Tek noktada konteyner tiklama (API degisirse sadece burayi duzelt) ----
    public static void clickSlot(int containerId, int slot, int button, ContainerInput type) {
        mc.gameMode.handleContainerInput(containerId, slot, button, type, mc.player);
    }

    // ---- Ekran (26.2+: Minecraft.screen -> mc.gui.screen()) ----
    public static Screen screen() {
        return mc.gui.screen();
    }

    public static void openScreen(Screen s) {
        mc.gui.setScreen(s);
    }

    // ---- Kol sallama (swing imzasi degisti, paket ile gonderiyoruz) ----
    public static void swing() {
        try {
            var m = mc.player.getClass().getMethod("swing", InteractionHand.class);
            m.invoke(mc.player, InteractionHand.MAIN_HAND);
        } catch (Throwable ignored) {
            // 26.3'te swing(hand) yok; saldiri swing olmadan da gecerli.
        }
    }

    public static void msg(String s) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("\u00a7d[Nexus] \u00a7f" + s));
    }

    // ---- Hareket ----
    public static boolean moving() {
        var o = mc.options;
        return o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown();
    }

    /** Basili yon tuslarindan dunya uzayinda birim yatay yon (tus yoksa ZERO). */
    public static Vec3 inputDir() {
        var o = mc.options;
        double f = 0, s = 0;
        if (o.keyUp.isDown()) f += 1;
        if (o.keyDown.isDown()) f -= 1;
        if (o.keyLeft.isDown()) s += 1;
        if (o.keyRight.isDown()) s -= 1;
        if (f == 0 && s == 0) return Vec3.ZERO;
        double len = Math.sqrt(f * f + s * s);
        f /= len;
        s /= len;
        double yaw = Math.toRadians(mc.player.getYRot());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        return new Vec3(-sin * f + cos * s, 0, cos * f + sin * s);
    }

    public static void setHSpeed(double speed) {
        LocalPlayer p = mc.player;
        var o = mc.options;
        double f = 0, s = 0;
        if (o.keyUp.isDown()) f += 1;
        if (o.keyDown.isDown()) f -= 1;
        if (o.keyLeft.isDown()) s += 1;
        if (o.keyRight.isDown()) s -= 1;
        Vec3 v = p.getDeltaMovement();
        if (f == 0 && s == 0) {
            p.setDeltaMovement(0, v.y, 0);
            return;
        }
        double len = Math.sqrt(f * f + s * s);
        f /= len;
        s /= len;
        double yaw = Math.toRadians(p.getYRot());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double x = (-sin * f + cos * s) * speed;
        double z = (cos * f + sin * s) * speed;
        p.setDeltaMovement(x, v.y, z);
    }

    public static void setVY(double y) {
        Vec3 v = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(v.x, y, v.z);
    }

    // ---- Donus ----
    public static float[] rotsTo(Vec3 target) {
        Vec3 eye = mc.player.getEyePosition();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return new float[]{yaw, pitch};
    }

    // ---- Varliklar ----
    public static List<Entity> nearby(double range) {
        return mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(range), e -> true);
    }

    public static List<LivingEntity> targets(double range, java.util.function.Predicate<Entity> filter) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : nearby(range)) {
            if (!(e instanceof LivingEntity le) || e == mc.player || e instanceof ArmorStand) continue;
            if (!le.isAlive() || le.getHealth() <= 0) continue;
            if (mc.player.distanceTo(e) > range) continue;
            if (filter.test(e)) out.add(le);
        }
        return out;
    }

    /** Bakis yonu ile hedef arasindaki aci (derece). */
    public static float angleTo(Entity e) {
        float[] r = rotsTo(e.getBoundingBox().getCenter());
        float dy = Mth.wrapDegrees(r[0] - mc.player.getYRot());
        float dp = r[1] - mc.player.getXRot();
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    // ---- Alet / silah ----
    public static int toolRank(ItemStack s) {
        if (s.isEmpty()) return -1;
        String p = itemPath(s);
        if (p.startsWith("netherite")) return 6;
        if (p.startsWith("diamond")) return 5;
        if (p.startsWith("iron")) return 4;
        if (p.startsWith("stone")) return 3;
        if (p.startsWith("golden")) return 2;
        if (p.startsWith("wooden")) return 1;
        return 0;
    }

    /** Hotbar'da verilen sonekli (ornegin "_sword") en iyi esyanin slotu, yoksa -1. */
    public static int findHotbar(String suffix) {
        int best = -1, bestRank = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.isEmpty() || !itemPath(s).endsWith(suffix)) continue;
            int r = toolRank(s);
            if (r > bestRank) {
                bestRank = r;
                best = i;
            }
        }
        return best;
    }

    public static boolean holdingWeapon() {
        String p = itemPath(mc.player.getMainHandItem());
        return p.endsWith("_sword") || p.endsWith("_axe") || p.equals("mace");
    }

    public static boolean holdingAxe() {
        return itemPath(mc.player.getMainHandItem()).endsWith("_axe");
    }

    public static void equipBestWeapon() {
        if (holdingWeapon()) return;
        int s = findHotbar("_sword");
        if (s < 0) s = findHotbar("_axe");
        if (s >= 0) selectSlot(s);
    }

    // ---- Envanter ----
    /** Oyuncu envanter indeksi (0-8 hotbar, 9-35 ana) -> InventoryMenu slot numarasi. */
    public static int menuSlot(int invIndex) {
        return invIndex < 9 ? invIndex + 36 : invIndex;
    }

    public static String itemPath(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
    }

    public static void selectSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
    }

    public static int selectedSlot() {
        return mc.player.getInventory().getSelectedSlot();
    }
}
