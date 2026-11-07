package dev.nexus.util;

import dev.nexus.setting.ListSetting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Hedef seçimi: onay kutusu listesi + evcil hayvan / isimli canlı / dost koruması. */
public final class TargetFilter {
    private static final Map<Class<?>, Object> TAME = new HashMap<>();

    private TargetFilter() {}

    /** Evcilleştirilmiş mi? (kurt, kedi, papağan, at, eşek, lama...) */
    public static boolean isTame(Entity e) {
        Class<?> c = e.getClass();
        Object v = TAME.get(c);
        if (v == null) {
            Method found = null;
            for (String n : new String[]{"isTame", "isTamed"}) {
                try {
                    Method m = c.getMethod(n);
                    if (m.getReturnType() == boolean.class) {
                        found = m;
                        break;
                    }
                } catch (Throwable ignored) {}
            }
            v = found == null ? Boolean.FALSE : found;
            TAME.put(c, v);
        }
        if (v instanceof Method m) {
            try {
                return (Boolean) m.invoke(e);
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    /**
     * @param attack true ise saldırı korumaları (dost, evcil, isimli) da uygulanır
     */
    public static boolean allow(Entity e, ListSetting list, boolean attack, boolean protectPets, boolean protectNamed) {
        if (!(e instanceof LivingEntity) || e == Helper.mc.player) return false;
        boolean isPlayer = e instanceof Player;
        String id = isPlayer ? "player" : BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        boolean on = list.has(id) ? list.isOn(id) : (e instanceof Enemy ? list.isOn("_diger_dusman") : list.isOn("_diger_canli"));
        if (!on) return false;
        if (attack) {
            if (FriendManager.isFriend(e)) return false;
            if (!isPlayer && protectPets && isTame(e)) return false;
            if (!isPlayer && protectNamed && e.hasCustomName()) return false;
        }
        return true;
    }
}
