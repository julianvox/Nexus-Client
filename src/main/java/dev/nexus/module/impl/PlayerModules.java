package dev.nexus.module.impl;

import dev.nexus.module.Category;
import dev.nexus.module.Module;
import dev.nexus.setting.BoolSetting;
import dev.nexus.setting.NumSetting;
import dev.nexus.util.Helper;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class PlayerModules {
    private PlayerModules() {}

    public static void register(List<Module> l) {
        l.add(new AutoEat());
        l.add(new AutoArmor());
        l.add(new AutoTotem());
        l.add(new ChestStealer());
        l.add(new AutoRespawn());
        l.add(new AntiAFK());
        l.add(new GodMode());
    }

    public static class AutoEat extends Module {
        final NumSetting hunger = add(new NumSetting("Açlık", 1, 19, 1, 14));
        boolean eating;
        int prevSlot = -1;

        public AutoEat() { super("OtoYemek", "Acıkınca otomatik yemek yer", Category.PLAYER); }

        private static boolean isFood(ItemStack s) {
            if (s.isEmpty() || !s.has(DataComponents.FOOD)) return false;
            String p = Helper.itemPath(s);
            return !(p.contains("rotten") || p.contains("spider_eye") || p.contains("pufferfish")
                    || p.contains("poisonous") || p.contains("chorus") || p.equals("enchanted_golden_apple"));
        }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            int food = p.getFoodData().getFoodLevel();
            if (eating) {
                if (food >= 20 || !isFood(p.getMainHandItem())) stop();
                return;
            }
            if (food > hunger.getInt() || Helper.screen() != null) return;
            for (int i = 0; i < 9; i++) {
                if (isFood(p.getInventory().getItem(i))) {
                    prevSlot = Helper.selectedSlot();
                    Helper.selectSlot(i);
                    mc.options.keyUse.setDown(true);
                    eating = true;
                    return;
                }
            }
        }

        private void stop() {
            eating = false;
            mc.options.keyUse.setDown(false);
            if (prevSlot >= 0) Helper.selectSlot(prevSlot);
            prevSlot = -1;
        }

        @Override public void onDisable() { if (eating && mc.player != null) stop(); }
    }

    public static class AutoArmor extends Module {
        final NumSetting delay = add(new NumSetting("Gecikme", 1, 20, 1, 4));
        int timer;

        public AutoArmor() { super("OtoZırh", "En iyi zırhı otomatik giyer", Category.PLAYER); }

        static int rank(ItemStack s) {
            if (s.isEmpty()) return -1;
            String p = Helper.itemPath(s);
            if (p.startsWith("netherite")) return 6;
            if (p.startsWith("diamond")) return 5;
            if (p.startsWith("iron")) return 4;
            if (p.startsWith("chainmail")) return 3;
            if (p.startsWith("golden")) return 2;
            if (p.startsWith("leather") || p.startsWith("turtle")) return 1;
            return 0;
        }

        @Override public void onTick() {
            if (++timer < delay.getInt()) return;
            LocalPlayer p = mc.player;
            String[] suffix = {"_helmet", "_chestplate", "_leggings", "_boots"};
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int a = 0; a < 4; a++) {
                int curRank = rank(p.getItemBySlot(slots[a]));
                int bestIdx = -1, bestRank = curRank;
                for (int i = 0; i < 36; i++) {
                    ItemStack s = p.getInventory().getItem(i);
                    if (s.isEmpty() || !Helper.itemPath(s).endsWith(suffix[a])) continue;
                    int r = rank(s);
                    if (r > bestRank) {
                        bestRank = r;
                        bestIdx = i;
                    }
                }
                if (bestIdx < 0) continue;
                timer = 0;
                if (curRank >= 0) {
                    Helper.clickSlot(p.inventoryMenu.containerId, 5 + a, 0, ContainerInput.QUICK_MOVE);
                } else {
                    Helper.clickSlot(p.inventoryMenu.containerId, Helper.menuSlot(bestIdx), 0, ContainerInput.QUICK_MOVE);
                }
                return;
            }
        }
    }

    public static class AutoTotem extends Module {
        final NumSetting delay = add(new NumSetting("Gecikme", 0, 10, 1, 2));
        int timer;

        public AutoTotem() { super("OtoTotem", "Totemi ikinci ele koyar", Category.PLAYER); }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            if (++timer < delay.getInt()) return;
            if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
            for (int i = 0; i < 36; i++) {
                if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                    Helper.clickSlot(p.inventoryMenu.containerId, Helper.menuSlot(i), 40, ContainerInput.SWAP);
                    timer = 0;
                    return;
                }
            }
        }
    }

    public static class ChestStealer extends Module {
        final NumSetting delay = add(new NumSetting("Gecikme", 0, 10, 1, 1));
        final BoolSetting autoClose = add(new BoolSetting("OtoKapat", true));
        int timer;

        public ChestStealer() { super("SandıkSoyucu", "Sandıktaki her şeyi alır", Category.PLAYER); }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            if (!(p.containerMenu instanceof ChestMenu menu) || Helper.screen() == null) return;
            if (++timer < delay.getInt() + 1) return;
            int size = menu.getRowCount() * 9;
            for (int i = 0; i < size; i++) {
                if (!menu.getSlot(i).getItem().isEmpty()) {
                    Helper.clickSlot(menu.containerId, i, 0, ContainerInput.QUICK_MOVE);
                    timer = 0;
                    return;
                }
            }
            if (autoClose.get()) p.closeContainer();
        }
    }

    public static class AutoRespawn extends Module {
        public AutoRespawn() { super("OtoDoğ", "Ölünce otomatik yeniden doğar", Category.PLAYER); }

        @Override public void onTick() {
            if (Helper.screen() instanceof DeathScreen) mc.player.respawn();
        }
    }

    public static class AntiAFK extends Module {
        final NumSetting interval = add(new NumSetting("Aralık", 20, 600, 20, 200));
        final Random rnd = new Random();
        int timer;

        public AntiAFK() { super("AFKKoruma", "AFK atılmayı engeller", Category.PLAYER); }

        @Override public void onTick() {
            if (++timer < interval.getInt()) return;
            timer = 0;
            LocalPlayer p = mc.player;
            p.setYRot(p.getYRot() + rnd.nextInt(60) - 30);
            if (p.onGround()) Helper.setVY(0.42);
        }
    }

    // ------------------------------------------------------------------ Ölümsüzlük
    /** Tek oyunculu (entegre sunucu) dünyada oyuncuyu sunucu tarafında dokunulmaz yapar. */
    public static class GodMode extends Module {
        final BoolSetting heal = add(new BoolSetting("CanYenile", true));
        final BoolSetting extinguish = add(new BoolSetting("AteşiSöndür", true));
        final BoolSetting voidGuard = add(new BoolSetting("BoşlukKoruma", true));
        double sx, sy, sz;
        boolean hasSafe;


        // 26.3'te Entity.setInvulnerable kaldırılmış/yeniden adlandırılmış: dokunulmazlık bayrağını
        // çalışma sırasında bul (metot, alan ve Abilities.invulnerable), sonra kullan.
        private static boolean discovered;
        private static Method setter;
        private static Field flag, abilFlag;

        private static void discover(ServerPlayer sp) {
            if (discovered) return;
            discovered = true;
            try {
                for (Class<?> k = ServerPlayer.class; k != null; k = k.getSuperclass()) {
                    if (setter == null) {
                        for (Method m : k.getDeclaredMethods()) {
                            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class
                                    && m.getName().toLowerCase(Locale.ROOT).contains("invulnerable")) {
                                m.setAccessible(true);
                                setter = m;
                                break;
                            }
                        }
                    }
                    if (flag == null) {
                        for (Field f : k.getDeclaredFields()) {
                            if (f.getType() == boolean.class && f.getName().toLowerCase(Locale.ROOT).contains("invulnerable")) {
                                f.setAccessible(true);
                                flag = f;
                                break;
                            }
                        }
                    }
                }
                Object ab = sp.getAbilities();
                for (Field f : ab.getClass().getDeclaredFields()) {
                    if (f.getType() == boolean.class && f.getName().toLowerCase(Locale.ROOT).contains("invulnerable")) {
                        f.setAccessible(true);
                        abilFlag = f;
                        break;
                    }
                }
            } catch (Throwable t) {
                dev.nexus.NexusClient.LOGGER.warn("Ölümsüzlük: bayrak aranırken hata", t);
            }
            dev.nexus.NexusClient.LOGGER.info("Ölümsüzlük: metot={}, alan={}, abilities={}",
                    setter == null ? "yok" : setter.getName(), flag == null ? "yok" : flag.getName(),
                    abilFlag == null ? "yok" : abilFlag.getName());
        }

        private static void setInv(ServerPlayer sp, boolean v) {
            discover(sp);
            try {
                if (setter != null) setter.invoke(sp, v);
                else if (flag != null) flag.setBoolean(sp, v);
            } catch (Throwable ignored) {}
            try {
                if (abilFlag != null && (v || (!sp.isCreative() && !sp.isSpectator()))) abilFlag.setBoolean(sp.getAbilities(), v);
            } catch (Throwable ignored) {}
        }

        public GodMode() {
            super("Ölümsüzlük", "Tek oyunculuda her hasarı engeller: lav, canavar, düşme, boğulma...", Category.PLAYER);
        }

        @Override public void onEnable() {
            if (mc.getSingleplayerServer() == null && mc.player != null) {
                Helper.msg("Ölümsüzlük yalnızca tek oyunculu dünyada (veya LAN sahibinde) çalışır. Bu sunucuda hasar alırsın.");
            }
        }

        @Override public void onTick() {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            LocalPlayer p = mc.player;
            if (p.onGround() && !p.isInLava() && !p.isInWater()) {
                sx = p.getX();
                sy = p.getY();
                sz = p.getZ();
                hasSafe = true;
            }
            // Boşluğa düşersen son güvenli yere geri ışınla (boşluk hasarı dokunulmazlığı deler)
            final boolean tp = voidGuard.get() && hasSafe && !p.onGround() && p.getY() < -60 && p.getY() < sy - 25;
            final double tx = sx, ty = sy, tz = sz;
            final boolean h = heal.get(), ext = extinguish.get();
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(p.getUUID());
                if (sp == null) return;
                setInv(sp, true);
                if (h && sp.getHealth() > 0 && sp.getHealth() < sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth());
                if (ext) sp.clearFire();
                if (tp) {
                    sp.teleportTo(tx, ty, tz);
                    sp.fallDistance = 0;
                }
            });
        }

        @Override public void onDisable() {
            var server = mc.getSingleplayerServer();
            if (server == null || mc.player == null) return;
            var id = mc.player.getUUID();
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                if (sp != null) setInv(sp, false);
            });
        }
    }
}
