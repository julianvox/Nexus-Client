package dev.nexus.module.impl;

import dev.nexus.module.Category;
import dev.nexus.module.Module;
import dev.nexus.setting.BoolSetting;
import dev.nexus.setting.NumSetting;
import dev.nexus.util.FriendManager;
import dev.nexus.util.Helper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class DodgeModule {
    private DodgeModule() {}

    public static void register(List<Module> l) {
        l.add(new AutoDodge());
    }

    public static class AutoDodge extends Module {
        final BoolSetting lava = add(new BoolSetting("LavdanKaç", true));
        final BoolSetting hazards = add(new BoolSetting("ZararlıBlok", true));
        final BoolSetting projectiles = add(new BoolSetting("Mermiler", true));
        final BoolSetting mobs = add(new BoolSetting("Canavarlar", true));
        final BoolSetting players = add(new BoolSetting("Oyuncular", true));
        final BoolSetting creeper = add(new BoolSetting("CreeperKaç", true));
        final NumSetting range = add(new NumSetting("Menzil", 2, 8, 0.1, 3.5));
        final NumSetting speed = add(new NumSetting("Hız", 0.1, 0.9, 0.05, 0.45));
        final NumSetting back = add(new NumSetting("GeriFaktör", 0, 1, 0.05, 0.4));
        final BoolSetting cliff = add(new BoolSetting("UçurumKoruma", true));
        final BoolSetting jump = add(new BoolSetting("Zıpla", false));

        private final Random rnd = new Random();
        private final Map<Block, Integer> kinds = new HashMap<>();
        private static final Map<Class<?>, Method> SWELL = new HashMap<>();
        private static final Map<Class<?>, Method> OWNER = new HashMap<>();
        private double side = 1;
        private int sideTicks;

        public AutoDodge() {
            super("OtoKaçınma", "Lavdan, oklardan, canavar ve oyuncu saldırılarından kaçar", Category.COMBAT);
        }

        // ------------------------------------------------------------ ana döngü
        @Override public void onTick() {
            LocalPlayer p = mc.player;

            if (lava.get() && p.isInLava()) {
                escapeLava(p);
                return;
            }

            Vec3 dodge = null;
            if (projectiles.get()) dodge = projectileDodge(p);
            if (dodge == null) dodge = meleeDodge(p);
            if (dodge != null) {
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(dodge.x, v.y, dodge.z);
                if (jump.get() && p.onGround()) Helper.setVY(0.42);
            }

            if (lava.get() || hazards.get()) brake(p);
        }

        // ------------------------------------------------------------ blok güvenliği
        /** 0 = zararsız, 1 = lav, 2 = diğer zararlı bloklar */
        private int kind(BlockState s) {
            if (s.isAir()) return 0;
            Block b = s.getBlock();
            Integer k = kinds.get(b);
            if (k == null) {
                String p = BuiltInRegistries.BLOCK.getKey(b).getPath();
                if (p.equals("lava") || p.equals("lava_cauldron")) k = 1;
                else if (p.equals("fire") || p.equals("soul_fire") || p.equals("magma_block") || p.equals("cactus")
                        || p.contains("campfire") || p.equals("sweet_berry_bush") || p.equals("wither_rose")) k = 2;
                else k = 0;
                kinds.put(b, k);
            }
            return k;
        }

        private boolean bad(BlockState s, boolean any) {
            int k = kind(s);
            if (k == 0) return false;
            return any || (k == 1 && lava.get()) || (k == 2 && hazards.get());
        }

        private boolean solid(BlockState s, BlockPos q) {
            return !s.getCollisionShape(mc.level, q).isEmpty();
        }

        /** Burada durursam/düşersem zarar görür müyüm? (ayak, kafa ve düşülecek zemin) */
        private boolean hazardAt(Vec3 pos, boolean any) {
            BlockPos b = BlockPos.containing(pos);
            if (bad(mc.level.getBlockState(b), any) || bad(mc.level.getBlockState(b.above()), any)) return true;
            for (int i = 0; i <= 4; i++) {
                BlockPos q = b.below(i);
                BlockState s = mc.level.getBlockState(q);
                if (bad(s, any)) return true;
                if (solid(s, q) || !s.getFluidState().isEmpty()) return false;
            }
            return false;
        }

        private boolean hasGround(Vec3 pos) {
            BlockPos b = BlockPos.containing(pos);
            for (int i = 1; i <= 4; i++) {
                BlockPos q = b.below(i);
                BlockState s = mc.level.getBlockState(q);
                if (solid(s, q) || !s.getFluidState().isEmpty()) return true;
            }
            return false;
        }

        /** Kaçış hedefi: duvar yok, zararlı blok yok, (istenirse) uçurum yok. */
        private boolean spotSafe(Vec3 pos) {
            BlockPos b = BlockPos.containing(pos);
            if (solid(mc.level.getBlockState(b), b)) return false;
            if (solid(mc.level.getBlockState(b.above()), b.above())) return false;
            if (hazardAt(pos, true)) return false;
            return !cliff.get() || hasGround(pos);
        }

        // ------------------------------------------------------------ lav / zararlı blok freni
        private void brake(LocalPlayer p) {
            if (hazardAt(p.position(), false)) return; // zaten üstündeysek çıkışı engelleme
            Vec3 input = Helper.inputDir();
            Vec3 v = p.getDeltaMovement();
            Vec3 hv = new Vec3(v.x, 0, v.z);
            Vec3 heading = input.lengthSqr() > 0 ? input : (hv.lengthSqr() > 0.0009 ? hv.normalize() : Vec3.ZERO);
            if (heading.lengthSqr() == 0) return;
            for (double d : new double[]{0.8, 1.3, 1.9}) {
                if (hazardAt(p.position().add(heading.scale(d)), false)) {
                    p.setDeltaMovement(0, v.y, 0);
                    return;
                }
            }
        }

        private void escapeLava(LocalPlayer p) {
            BlockPos base = p.blockPosition();
            Vec3 best = null;
            double bd = 1e9;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        BlockPos f = base.offset(dx, dy, dz);
                        BlockState fs = mc.level.getBlockState(f);
                        BlockState hs = mc.level.getBlockState(f.above());
                        BlockState gs = mc.level.getBlockState(f.below());
                        if (kind(fs) != 0 || kind(hs) != 0) continue;
                        if (solid(fs, f) || solid(hs, f.above())) continue;
                        if (!solid(gs, f.below()) || kind(gs) != 0) continue;
                        double d = dx * dx + dz * dz + dy * dy * 0.5;
                        if (d < bd) {
                            bd = d;
                            best = Vec3.atBottomCenterOf(f);
                        }
                    }
                }
            }
            Vec3 v = p.getDeltaMovement();
            if (best != null) {
                Vec3 dir = new Vec3(best.x - p.getX(), 0, best.z - p.getZ());
                if (dir.lengthSqr() > 0.01) dir = dir.normalize().scale(0.3);
                p.setDeltaMovement(dir.x, 0.35, dir.z);
            } else {
                p.setDeltaMovement(v.x, 0.3, v.z);
            }
        }

        // ------------------------------------------------------------ oklar ve mermiler
        private static String typePath(Entity e) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        }

        private static boolean isThreat(String p) {
            return p.contains("arrow") || p.equals("trident") || p.contains("fireball") || p.equals("wither_skull")
                    || p.equals("shulker_bullet") || p.equals("llama_spit") || p.contains("splash_potion")
                    || p.contains("lingering_potion");
        }

        private boolean ownerIsMe(Entity e) {
            try {
                Method m = OWNER.get(e.getClass());
                if (m == null) {
                    m = e.getClass().getMethod("getOwner");
                    OWNER.put(e.getClass(), m);
                }
                return m.invoke(e) == mc.player;
            } catch (Throwable t) {
                return false;
            }
        }

        private Vec3 projectileDodge(LocalPlayer p) {
            Vec3 center = p.position().add(0, p.getBbHeight() / 2.0, 0);
            Vec3 bestV = null, bestClosest = null;
            double bestT = 1e9;
            for (Entity e : Helper.nearby(26)) {
                if (!isThreat(typePath(e)) || ownerIsMe(e)) continue;
                Vec3 v = e.getDeltaMovement();
                double v2 = v.lengthSqr();
                if (v2 < 0.05) continue; // yere saplanmış / durmuş
                double t = center.subtract(e.position()).dot(v) / v2;
                if (t <= 0 || t > 16) continue;
                Vec3 closest = e.position().add(v.scale(t));
                if (closest.distanceTo(center) > 0.95) continue;
                if (t < bestT) {
                    bestT = t;
                    bestV = v;
                    bestClosest = closest;
                }
            }
            if (bestV == null) return null;

            Vec3 n = new Vec3(-bestV.z, 0, bestV.x);
            double nl = n.length();
            if (nl < 1e-4) n = new Vec3(rnd.nextDouble() - 0.5, 0, rnd.nextDouble() - 0.5).normalize();
            else n = n.scale(1.0 / nl);
            double s = Math.signum(center.subtract(bestClosest).dot(n));
            if (s == 0) s = rnd.nextBoolean() ? 1 : -1;
            for (int k = 0; k < 2; k++) {
                Vec3 dir = n.scale(s * speed.get());
                if (spotSafe(p.position().add(dir.scale(4)))) return dir;
                s = -s;
            }
            return null;
        }

        // ------------------------------------------------------------ yakın saldırılar
        private boolean fusingCreeper(Entity e) {
            if (!typePath(e).equals("creeper")) return false;
            try {
                Method m = SWELL.get(e.getClass());
                if (m == null) {
                    m = e.getClass().getMethod("getSwellDir");
                    SWELL.put(e.getClass(), m);
                }
                return m.invoke(e) instanceof Integer i && i > 0;
            } catch (Throwable t) {
                return false;
            }
        }

        private Vec3 meleeDodge(LocalPlayer p) {
            Entity best = null;
            double bestD = 1e9;
            boolean fuse = false;
            for (Entity e : Helper.nearby(8)) {
                if (e == p || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
                boolean pl = e instanceof Player, hostile = e instanceof Enemy;
                if (!pl && !hostile) continue;
                if (FriendManager.isFriend(e)) continue;
                boolean fusing = hostile && creeper.get() && fusingCreeper(e);
                if (pl && !players.get()) continue;
                if (hostile && !mobs.get() && !fusing) continue;
                if (Math.abs(p.getY() - e.getY()) > 2.5) continue;
                double dx = p.getX() - e.getX(), dz = p.getZ() - e.getZ();
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > (fusing ? 7.0 : range.get())) continue;
                if (d < bestD) {
                    bestD = d;
                    best = e;
                    fuse = fusing;
                }
            }
            if (best == null) return null;

            Vec3 away = new Vec3(p.getX() - best.getX(), 0, p.getZ() - best.getZ());
            away = away.lengthSqr() < 1e-4 ? new Vec3(rnd.nextDouble() - 0.5, 0, rnd.nextDouble() - 0.5).normalize() : away.normalize();
            Vec3 tan = new Vec3(-away.z, 0, away.x);
            if (--sideTicks <= 0) {
                side = rnd.nextBoolean() ? 1 : -1;
                sideTicks = 8 + rnd.nextInt(14);
            }
            double bk = fuse ? 1.0 : back.get(), tn = fuse ? 0.0 : 1.0;
            double sp = speed.get() * (fuse ? 1.2 : 1.0);
            double s = side;
            for (int k = 0; k < 3; k++) {
                Vec3 dir = k < 2 ? away.scale(bk).add(tan.scale(s * tn)) : away;
                if (dir.lengthSqr() < 1e-6) dir = away;
                dir = dir.normalize().scale(sp);
                if (spotSafe(p.position().add(dir.scale(4)))) return dir;
                s = -s;
            }
            return null;
        }
    }
}
