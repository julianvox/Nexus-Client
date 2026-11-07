package dev.nexus.module.impl;

import dev.nexus.module.Category;
import dev.nexus.module.Module;
import dev.nexus.setting.BoolSetting;
import dev.nexus.setting.NumSetting;
import dev.nexus.util.Helper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class WorldModules {
    private WorldModules() {}

    public static void register(List<Module> l) {
        l.add(new Nuker());
        l.add(new Scaffold());
        l.add(new AutoTool());
        l.add(new AutoMine());
    }

    public static class Nuker extends Module {
        final NumSetting radius = add(new NumSetting("Yarıçap", 1, 6, 1, 4));
        final NumSetting perTick = add(new NumSetting("TikBaşınaBlok", 1, 10, 1, 2));
        final BoolSetting keepFloor = add(new BoolSetting("ZeminiKoru", true));

        public Nuker() { super("BlokKırıcı", "Çevredeki blokları kırar", Category.WORLD); }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            BlockPos base = p.blockPosition();
            int r = radius.getInt();
            int count = 0;
            for (int dx = -r; dx <= r; dx++)
                for (int dy = r; dy >= -r; dy--)
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos pos = base.offset(dx, dy, dz);
                        if (keepFloor.get() && pos.getY() < base.getY()) continue;
                        BlockState s = mc.level.getBlockState(pos);
                        if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                        if (s.getDestroySpeed(mc.level, pos) < 0) continue;
                        if (Vec3.atCenterOf(pos).distanceTo(p.getEyePosition()) > r + 0.5) continue;
                        mc.gameMode.startDestroyBlock(pos, Direction.UP);
                        mc.gameMode.continueDestroyBlock(pos, Direction.UP);
                        Helper.swing();
                        if (++count >= perTick.getInt()) return;
                    }
        }
    }

    public static class Scaffold extends Module {
        final NumSetting delay = add(new NumSetting("Gecikme", 0, 10, 1, 1));
        final BoolSetting autoSelect = add(new BoolSetting("OtoSeç", true));
        int timer;

        public Scaffold() { super("OtoKöprü", "Ayağının altına blok koyar", Category.WORLD); }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            if (++timer < delay.getInt() + 1) return;
            BlockPos below = p.blockPosition().below();
            if (!mc.level.getBlockState(below).canBeReplaced()) return;

            if (autoSelect.get()) {
                for (int i = 0; i < 9; i++) {
                    ItemStack s = p.getInventory().getItem(i);
                    if (!s.isEmpty() && s.getItem() instanceof BlockItem) {
                        Helper.selectSlot(i);
                        break;
                    }
                }
            }
            if (!(p.getMainHandItem().getItem() instanceof BlockItem)) return;

            for (Direction d : Direction.values()) {
                BlockPos n = below.relative(d);
                if (mc.level.getBlockState(n).canBeReplaced()) continue;
                Direction face = d.getOpposite();
                Vec3 hitPos = Vec3.atCenterOf(n).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                BlockHitResult hit = new BlockHitResult(hitPos, face, n, false);
                mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);
                Helper.swing();
                timer = 0;
                return;
            }
        }
    }

    public static class AutoTool extends Module {
        public AutoTool() { super("OtoAlet", "Kazarken en iyi aleti seçer", Category.WORLD); }

        @Override public void onTick() {
            if (!mc.options.keyAttack.isDown()) return;
            if (!(mc.hitResult instanceof BlockHitResult bhr)) return;
            BlockState state = mc.level.getBlockState(bhr.getBlockPos());
            if (state.isAir()) return;
            int best = Helper.selectedSlot();
            float bestSpeed = mc.player.getInventory().getItem(best).getDestroySpeed(state);
            for (int i = 0; i < 9; i++) {
                float sp = mc.player.getInventory().getItem(i).getDestroySpeed(state);
                if (sp > bestSpeed) {
                    bestSpeed = sp;
                    best = i;
                }
            }
            if (best != Helper.selectedSlot()) Helper.selectSlot(best);
        }
    }

    public static class AutoMine extends Module {
        public AutoMine() { super("OtoKazı", "Sol tuşu sürekli basılı tutar", Category.WORLD); }

        @Override public void onTick() { mc.options.keyAttack.setDown(true); }
        @Override public void onDisable() { mc.options.keyAttack.setDown(false); }
    }
}
