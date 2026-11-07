package dev.nexus.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashSet;
import java.util.Set;

public final class FriendManager {
    public static final Set<String> NAMES = new LinkedHashSet<>();

    private FriendManager() {}

    public static boolean isFriend(Entity e) {
        return e instanceof Player && NAMES.contains(e.getName().getString().toLowerCase(java.util.Locale.ROOT));
    }
}
