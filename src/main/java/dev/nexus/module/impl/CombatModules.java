package dev.nexus.module.impl;

import dev.nexus.module.Category;
import dev.nexus.module.Module;
import dev.nexus.setting.BoolSetting;
import dev.nexus.setting.ListSetting;
import dev.nexus.setting.MobTable;
import dev.nexus.setting.ModeSetting;
import dev.nexus.setting.NumSetting;
import dev.nexus.util.FriendManager;
import dev.nexus.util.Helper;
import dev.nexus.util.TargetFilter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public final class CombatModules {
    private CombatModules() {}

    public static void register(List<Module> l) {
        l.add(new KillAura());
        l.add(new ShieldBreaker());
        l.add(new AimAssist());
        l.add(new Triggerbot());
        l.add(new Velocity());
        l.add(new Reach());
        l.add(new Criticals());
        l.add(new AutoGapple());
    }

    /** Zıplama/düşme ile kritik vurulabilir mi? */
    static boolean canCrit(LocalPlayer p) {
        return !p.isInWater() && !p.isInLava() && !p.onClimbable()
                && !p.getAbilities().flying && !p.isPassenger();
    }

    // ------------------------------------------------------------------ OtoSaldırı
    public static class KillAura extends Module {
        public static KillAura INSTANCE;
        /** Ekrandaki hedef bilgisi ve OtoKalkanKır için. */
        public static LivingEntity target;

        final NumSetting range = add(new NumSetting("Menzil", 1, 6, 0.1, 4.2));
        final BoolSetting cooldown = add(new BoolSetting("Bekleme", true));
        final NumSetting cps = add(new NumSetting("CPS", 1, 20, 1, 12));
        final ModeSetting crits = add(new ModeSetting("Kritik", "Zıpla", "Kapalı", "Zıpla"));
        final BoolSetting autoWeapon = add(new BoolSetting("OtoSilah", true));
        final ListSetting targets = add(MobTable.create("Hedefler", true, true, false, false));
        final BoolSetting protectPets = add(new BoolSetting("EvcilleriKoru", true));
        final BoolSetting protectNamed = add(new BoolSetting("İsimlileriKoru", true));
        final BoolSetting walls = add(new BoolSetting("DuvarArkası", false));
        final ModeSetting priority = add(new ModeSetting("Öncelik", "Mesafe", "Mesafe", "Can", "Açı"));
        final NumSetting fov = add(new NumSetting("GörüşAçısı", 30, 360, 10, 360));
        final ModeSetting rotate = add(new ModeSetting("Dönüş", "Yok", "Yok", "Yumuşak", "Ani"));
        final NumSetting rotSpeed = add(new NumSetting("DönüşHızı", 1, 100, 1, 35));
        int timer;

        public KillAura() {
            super("OtoSaldırı", "Yakındaki hedeflere otomatik vurur; kritik ve kalkan kırma ile uyumlu", Category.COMBAT);
            INSTANCE = this;
        }

        @Override public void onDisable() { target = null; }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            timer++;

            List<LivingEntity> list = Helper.targets(range.get(), e -> TargetFilter.allow(e, targets, true, protectPets.get(), protectNamed.get()));
            list.removeIf(e -> (!walls.get() && !p.hasLineOfSight(e)) || Helper.angleTo(e) > fov.get() / 2.0);
            if (list.isEmpty()) {
                target = null;
                return;
            }
            Comparator<LivingEntity> c;
            if (priority.is("Can")) c = Comparator.comparingDouble(LivingEntity::getHealth);
            else if (priority.is("Açı")) c = Comparator.comparingDouble(e -> Helper.angleTo(e));
            else c = Comparator.comparingDouble(e -> p.distanceTo(e));
            list.sort(c);
            LivingEntity t = list.get(0);
            target = t;

            // Kamera dönüşü (varsayılan kapalı: ekran sabit kalır)
            if (!rotate.is("Yok")) {
                float[] r = Helper.rotsTo(t.getBoundingBox().getCenter());
                if (rotate.is("Ani")) {
                    p.setYRot(r[0]);
                    p.setXRot(Mth.clamp(r[1], -90f, 90f));
                } else {
                    float sp = rotSpeed.getFloat() / 100f;
                    p.setYRot(p.getYRot() + Mth.wrapDegrees(r[0] - p.getYRot()) * sp);
                    p.setXRot(Mth.clamp(p.getXRot() + (r[1] - p.getXRot()) * sp, -90f, 90f));
                }
            }

            // Kalkan tutuyorsa: baltaya geç, kalkana vur, kılıca dön (OtoKalkanKır açıksa)
            if (ShieldBreaker.INSTANCE != null && ShieldBreaker.INSTANCE.handle(t)) {
                timer = 0;
                return;
            }

            if (autoWeapon.get()) Helper.equipBestWeapon();

            float str = p.getAttackStrengthScale(0f);
            int interval = Math.max(1, (int) (20.0 / cps.get()));
            boolean ready = cooldown.get() ? str >= 0.95f : timer >= interval;

            boolean critMode = crits.is("Zıpla") && canCrit(p);
            if (critMode) {
                // Koşarken kritik olmaz: koşuyu kapat, yerdeyken zıpla
                p.setSprinting(false);
                boolean jumpReady = cooldown.get() ? str >= 0.5f : timer >= interval - 8;
                if (p.onGround() && jumpReady) Helper.setVY(0.42);
            }
            if (!ready) return;
            if (critMode) {
                double fd = p.fallDistance;
                // Sadece havadayken ve DÜŞERKEN vur -> garanti kritik
                if (p.onGround() || p.getDeltaMovement().y >= 0 || fd <= 0) return;
            }
            timer = 0;
            mc.gameMode.attack(p, t);
            Helper.swing();
        }
    }

    // ------------------------------------------------------------------ OtoKalkanKır
    public static class ShieldBreaker extends Module {
        public static ShieldBreaker INSTANCE;

        final NumSetting range = add(new NumSetting("Menzil", 2, 6, 0.1, 4.2));
        final BoolSetting onlyPlayers = add(new BoolSetting("SadeceOyuncular", true));
        final BoolSetting backToSword = add(new BoolSetting("KılıcaDön", true));
        final BoolSetting crosshair = add(new BoolSetting("NişangahHedefi", true));
        final NumSetting wait = add(new NumSetting("Bekleme", 2, 20, 1, 6));
        int cd;

        public ShieldBreaker() {
            super("OtoKalkanKır", "Kalkan tutan hedefe baltayla vurup kılıca geri döner (OtoSaldırı ile uyumlu)", Category.COMBAT);
            INSTANCE = this;
        }

        /** Kalkan kırıldıysa true döner; çağıran bu tikte normal vuruş yapmaz. */
        public boolean handle(LivingEntity t) {
            LocalPlayer p = mc.player;
            if (!enabled || p == null || t == null || !t.isAlive() || cd > 0) return false;
            if (onlyPlayers.get() && !(t instanceof Player)) return false;
            if (FriendManager.isFriend(t) || !t.isBlocking()) return false;
            if (p.distanceTo(t) > range.get()) return false;

            int prev = Helper.selectedSlot();
            int axe = Helper.holdingAxe() ? prev : Helper.findHotbar("_axe");
            if (axe < 0) return false;

            if (axe != prev) Helper.selectSlot(axe);
            mc.gameMode.attack(p, t);
            Helper.swing();

            int back = prev;
            if (backToSword.get()) {
                int sw = Helper.findHotbar("_sword");
                if (sw >= 0) back = sw;
            }
            if (back != Helper.selectedSlot()) Helper.selectSlot(back);
            cd = wait.getInt();
            return true;
        }

        @Override public void onTick() {
            if (cd > 0) cd--;
            // OtoSaldırı hedef bulduysa vuruşu o yönetir
            if (KillAura.INSTANCE != null && KillAura.INSTANCE.enabled && KillAura.target != null) return;
            if (!crosshair.get()) return;
            if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) handle(le);
        }
    }

    // ------------------------------------------------------------------ NişanYardımı
    public static class AimAssist extends Module {
        final NumSetting speed = add(new NumSetting("Hız", 1, 100, 1, 18));
        final NumSetting range = add(new NumSetting("Menzil", 1, 8, 0.1, 5));
        final NumSetting fov = add(new NumSetting("GörüşAçısı", 10, 180, 5, 90));
        final BoolSetting onlyAttack = add(new BoolSetting("SadeceSaldırırken", true));
        final ListSetting targets = add(MobTable.create("Hedefler", true, true, false, false));
        final BoolSetting protectPets = add(new BoolSetting("EvcilleriKoru", true));
        final BoolSetting protectNamed = add(new BoolSetting("İsimlileriKoru", true));

        public AimAssist() { super("NişanYardımı", "Hedefe yumuşak nişan yardımı yapar", Category.COMBAT); }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            if (onlyAttack.get() && !mc.options.keyAttack.isDown()) return;
            if (Helper.screen() != null) return;
            List<LivingEntity> list = Helper.targets(range.get(), e -> TargetFilter.allow(e, targets, true, protectPets.get(), protectNamed.get()));
            list.removeIf(e -> !p.hasLineOfSight(e) || Helper.angleTo(e) > fov.get() / 2.0);
            if (list.isEmpty()) return;
            list.sort(Comparator.comparingDouble(e -> Helper.angleTo(e)));
            float[] r = Helper.rotsTo(list.get(0).getBoundingBox().getCenter());
            float sp = speed.getFloat() / 100f;
            p.setYRot(p.getYRot() + Mth.wrapDegrees(r[0] - p.getYRot()) * sp);
            p.setXRot(Mth.clamp(p.getXRot() + (r[1] - p.getXRot()) * sp, -90f, 90f));
        }
    }

    // ------------------------------------------------------------------ OtoTetik
    public static class Triggerbot extends Module {
        final ListSetting targets = add(MobTable.create("Hedefler", true, true, false, false));
        final BoolSetting protectPets = add(new BoolSetting("EvcilleriKoru", true));
        final BoolSetting protectNamed = add(new BoolSetting("İsimlileriKoru", true));
        final BoolSetting crits = add(new BoolSetting("Kritik", false));

        public Triggerbot() { super("OtoTetik", "Nişangahtaki varlığa otomatik vurur", Category.COMBAT); }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            if (!(mc.hitResult instanceof EntityHitResult ehr)) return;
            if (!(ehr.getEntity() instanceof LivingEntity le) || !le.isAlive()) return;
            if (!TargetFilter.allow(le, targets, true, protectPets.get(), protectNamed.get())) return;

            if (ShieldBreaker.INSTANCE != null && ShieldBreaker.INSTANCE.handle(le)) return;

            if (p.getAttackStrengthScale(0f) < 0.95f) return;
            if (crits.get() && canCrit(p)) {
                p.setSprinting(false);
                double fd = p.fallDistance;
                if (p.onGround() || p.getDeltaMovement().y >= 0 || fd <= 0) return;
            }
            mc.gameMode.attack(p, le);
            Helper.swing();
        }
    }

    // ------------------------------------------------------------------ GeriTepme
    public static class Velocity extends Module {
        final NumSetting horizontal = add(new NumSetting("Yatay%", 0, 100, 5, 0));
        final NumSetting vertical = add(new NumSetting("Dikey%", 0, 100, 5, 0));

        public Velocity() { super("GeriTepme", "Aldığın geri tepmeyi azaltır", Category.COMBAT); }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            if (p.hurtTime > 0 && p.hurtTime >= p.hurtDuration - 1) {
                Vec3 v = p.getDeltaMovement();
                double h = horizontal.get() / 100.0, vv = vertical.get() / 100.0;
                p.setDeltaMovement(v.x * h, v.y * vv, v.z * h);
            }
        }
    }

    // ------------------------------------------------------------------ Menzil
    public static class Reach extends Module {
        final NumSetting entity = add(new NumSetting("Varlık", 3, 6, 0.1, 4.5));
        final NumSetting block = add(new NumSetting("Blok", 4.5, 8, 0.1, 6));

        public Reach() { super("Menzil", "Vuruş ve blok menzilini uzatır", Category.COMBAT); }

        @Override public void onTick() { apply(entity.get(), block.get()); }
        @Override public void onDisable() { apply(3.0, 4.5); }

        private void apply(double e, double b) {
            LocalPlayer p = mc.player;
            if (p == null) return;
            AttributeInstance ae = p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
            AttributeInstance ab = p.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
            if (ae != null) ae.setBaseValue(e);
            if (ab != null) ab.setBaseValue(b);
        }
    }

    // ------------------------------------------------------------------ Kritik
    public static class Criticals extends Module {
        public Criticals() { super("Kritik", "Vururken zıplayıp kritik vuruş yapar", Category.COMBAT); }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            if (mc.options.keyAttack.isDown() && mc.hitResult instanceof EntityHitResult && p.onGround()) {
                p.setSprinting(false);
                Helper.setVY(0.42);
            }
        }
    }

    // ------------------------------------------------------------------ OtoElma
    public static class AutoGapple extends Module {
        final NumSetting health = add(new NumSetting("Can", 2, 19, 1, 10));
        boolean eating;
        int prevSlot = -1;

        public AutoGapple() { super("OtoElma", "Can düşünce altın elma yer", Category.COMBAT); }

        private static boolean isGapple(ItemStack s) {
            return !s.isEmpty() && s.has(DataComponents.FOOD) && Helper.itemPath(s).contains("golden_apple");
        }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            if (eating) {
                if (p.getHealth() >= p.getMaxHealth() || !isGapple(p.getMainHandItem())) stop();
                return;
            }
            if (p.getHealth() > health.get() || Helper.screen() != null) return;
            for (int i = 0; i < 9; i++) {
                if (isGapple(p.getInventory().getItem(i))) {
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
}
