package dev.nexus.module.impl;

import dev.nexus.module.Category;
import dev.nexus.module.Module;
import dev.nexus.setting.BoolSetting;
import dev.nexus.setting.ModeSetting;
import dev.nexus.setting.NumSetting;
import dev.nexus.util.Helper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class MovementModules {
    private MovementModules() {}

    public static void register(List<Module> l) {
        l.add(new Fly());
        l.add(new Speed());
        l.add(new Sprint());
        l.add(new NoFall());
        l.add(new Step());
        l.add(new HighJump());
        l.add(new Glide());
        l.add(new Jesus());
        l.add(new Spider());
        l.add(new AirJump());
        l.add(new AutoWalk());
    }

    public static class Fly extends Module {
        final ModeSetting mode = add(new ModeSetting("Mod", "Hız", "Hız", "Yaratıcı"));
        final NumSetting speed = add(new NumSetting("Hız", 0.1, 5, 0.1, 1.0));
        final BoolSetting antiKick = add(new BoolSetting("AtılmaKoruma", true));
        int ticks;

        public Fly() { super("Uçuş", "Uçmanı sağlar", Category.MOVEMENT); }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            if (mode.is("Yaratıcı")) {
                p.getAbilities().mayfly = true;
                p.getAbilities().setFlyingSpeed((float) (speed.get() * 0.05));
                return;
            }
            p.getAbilities().flying = false;
            double s = speed.get();
            Helper.setHSpeed(Helper.moving() ? s : 0);
            double y = 0;
            if (mc.options.keyJump.isDown()) y = s;
            else if (mc.options.keyShift.isDown()) y = -s;
            else if (antiKick.get() && ++ticks % 40 == 0) y = -0.04;
            Helper.setVY(y);
            p.fallDistance = 0;
        }

        @Override public void onDisable() {
            LocalPlayer p = mc.player;
            if (p == null) return;
            if (!p.isCreative() && !p.isSpectator()) {
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
            }
            p.getAbilities().setFlyingSpeed(0.05f);
        }
    }

    public static class Speed extends Module {
        final NumSetting speed = add(new NumSetting("Hız", 0.1, 2, 0.05, 0.35));
        final BoolSetting hop = add(new BoolSetting("Sıçrama", true));

        public Speed() { super("Hız", "Yürüme hızını arttırır", Category.MOVEMENT); }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            if (!Helper.moving() || p.isInWater() || p.isInLava() || p.onClimbable()) return;
            if (hop.get() && p.onGround()) Helper.setVY(0.42);
            Vec3 v = p.getDeltaMovement();
            double y = v.y;
            Helper.setHSpeed(speed.get());
            Helper.setVY(y);
        }
    }

    public static class Sprint extends Module {
        public Sprint() { super("Koşu", "Sürekli koşar", Category.MOVEMENT); }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            if (mc.options.keyUp.isDown() && !p.horizontalCollision && p.getFoodData().getFoodLevel() > 6) {
                p.setSprinting(true);
            }
        }
    }

    public static class NoFall extends Module {
        public NoFall() { super("DüşmeKoruma", "Düşme hasarını engeller", Category.MOVEMENT); }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            double fd = p.fallDistance;
            if (fd > 2.5 && !p.onGround() && mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, p.horizontalCollision));
            }
        }
    }

    public static class Step extends Module {
        final NumSetting height = add(new NumSetting("Yükseklik", 1, 5, 0.5, 2));

        public Step() { super("Basamak", "Blokları basamak gibi çıkar", Category.MOVEMENT); }

        @Override public void onTick() {
            AttributeInstance a = mc.player.getAttribute(Attributes.STEP_HEIGHT);
            if (a != null) a.setBaseValue(height.get());
        }

        @Override public void onDisable() {
            if (mc.player == null) return;
            AttributeInstance a = mc.player.getAttribute(Attributes.STEP_HEIGHT);
            if (a != null) a.setBaseValue(0.6);
        }
    }

    public static class HighJump extends Module {
        final NumSetting power = add(new NumSetting("Güç", 0.42, 3, 0.05, 1.0));

        public HighJump() { super("YüksekZıplama", "Daha yükseğe zıplar", Category.MOVEMENT); }

        @Override public void onTick() {
            AttributeInstance a = mc.player.getAttribute(Attributes.JUMP_STRENGTH);
            if (a != null) a.setBaseValue(power.get());
        }

        @Override public void onDisable() {
            if (mc.player == null) return;
            AttributeInstance a = mc.player.getAttribute(Attributes.JUMP_STRENGTH);
            if (a != null) a.setBaseValue(0.42);
        }
    }

    public static class Glide extends Module {
        final NumSetting fall = add(new NumSetting("DüşüşHızı", 0.01, 0.5, 0.01, 0.05));

        public Glide() { super("Süzülme", "Havada yavaş düşer", Category.MOVEMENT); }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            if (!p.onGround() && !p.isInWater() && p.getDeltaMovement().y < -fall.get()) {
                Helper.setVY(-fall.get());
                p.fallDistance = 0;
            }
        }
    }

    public static class Jesus extends Module {
        public Jesus() { super("SudaYürü", "Suda ve lavda yüzer", Category.MOVEMENT); }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            if ((p.isInWater() || p.isInLava()) && !mc.options.keyShift.isDown()) {
                Helper.setVY(0.11);
            }
        }
    }

    public static class Spider extends Module {
        public Spider() { super("Örümcek", "Duvara tırmanır", Category.MOVEMENT); }

        @Override public void onTick() {
            if (mc.player.horizontalCollision && !mc.options.keyShift.isDown()) Helper.setVY(0.2);
        }
    }

    public static class AirJump extends Module {
        boolean was;

        public AirJump() { super("HavadaZıpla", "Havada tekrar zıplar", Category.MOVEMENT); }

        @Override public void onTick() {
            boolean down = mc.options.keyJump.isDown();
            if (down && !was && !mc.player.onGround()) Helper.setVY(0.42);
            was = down;
        }
    }

    public static class AutoWalk extends Module {
        public AutoWalk() { super("OtoYürü", "Otomatik ileri yürür", Category.MOVEMENT); }

        @Override public void onTick() { mc.options.keyUp.setDown(true); }
        @Override public void onDisable() { mc.options.keyUp.setDown(false); }
    }
}
