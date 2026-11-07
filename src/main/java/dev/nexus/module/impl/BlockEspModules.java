package dev.nexus.module.impl;

import dev.nexus.hud.Hud;
import dev.nexus.module.Category;
import dev.nexus.module.Module;
import dev.nexus.setting.BoolSetting;
import dev.nexus.setting.ModeSetting;
import dev.nexus.setting.NumSetting;
import dev.nexus.util.Overlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BlockEspModules {
    private BlockEspModules() {}

    public static void register(List<Module> l) {
        l.add(new OreESP());
        l.add(new StorageESP());
    }

    record Found(BlockPos pos, int type) {}

    /** Cevredeki bloklari parca parca tarar; bulunanlari RGB tel kafes, yazi ve cizgi ile cizer. */
    public abstract static class BlockEsp extends Module {
        final NumSetting range = add(new NumSetting("Menzil", 8, 64, 2, 24));
        final ModeSetting style = add(new ModeSetting("Stil", "Tel", "Tel", "Kutu"));
        final BoolSetting labels = add(new BoolSetting("Yazılar", true));
        final BoolSetting tracers = add(new BoolSetting("Çizgiler", true));
        final NumSetting tracerCount = add(new NumSetting("ÇizgiSayısı", 1, 40, 1, 8));
        final BoolSetting rainbow = add(new BoolSetting("RGB", true));
        final NumSetting thickness = add(new NumSetting("Kalınlık", 1, 3, 1, 2));
        final NumSetting maxDraw = add(new NumSetting("MaksBlok", 5, 150, 5, 40));

        private List<Found> found = new ArrayList<>();
        private final List<Found> building = new ArrayList<>();
        private final Map<Block, Integer> cache = new HashMap<>();
        private static final int IDLE = Integer.MIN_VALUE;
        private int scanX = IDLE, scanR, idle;
        private BlockPos origin = BlockPos.ZERO;

        protected BlockEsp(String name, String desc) { super(name, desc, Category.RENDER); }

        /** Blok yolu (ornegin "diamond_ore") icin tur numarasi, yoksa -1. */
        protected abstract int match(String path);
        protected abstract boolean isOn(int type);
        protected abstract int baseColor(int type);
        protected abstract String typeName(int type);

        @Override public void onEnable() { scanX = IDLE; idle = 0; found = new ArrayList<>(); }
        @Override public void onDisable() { found = new ArrayList<>(); building.clear(); scanX = IDLE; }

        @Override public void onTick() {
            LocalPlayer p = mc.player;
            if (scanX == IDLE) {
                if (--idle > 0) return;
                origin = p.blockPosition();
                scanR = range.getInt();
                scanX = -scanR;
                building.clear();
            }
            int r = scanR, h = Math.min(r, 32);
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            for (int n = 0; n < 3 && scanX <= r; n++, scanX++) {
                for (int dy = -h; dy <= h; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        mp.set(origin.getX() + scanX, origin.getY() + dy, origin.getZ() + dz);
                        BlockState s = mc.level.getBlockState(mp);
                        if (s.isAir()) continue;
                        Block b = s.getBlock();
                        Integer t = cache.get(b);
                        if (t == null) {
                            t = match(BuiltInRegistries.BLOCK.getKey(b).getPath());
                            cache.put(b, t);
                        }
                        if (t >= 0 && isOn(t)) building.add(new Found(mp.immutable(), t));
                    }
                }
            }
            if (scanX > r) {
                Vec3 c = p.position();
                List<Found> res = new ArrayList<>(building);
                res.sort(Comparator.comparingDouble(f -> f.pos().distToCenterSqr(c)));
                found = res;
                scanX = IDLE;
                idle = 8;
            }
        }

        @Override public void onRender(GuiGraphicsExtractor g, float pt) {
            List<Found> list = found;
            if (list.isEmpty()) return;
            Overlay.begin(pt);
            Vec3 pos = mc.player.position();
            double maxD = range.get() * range.get() * 1.2;
            int th = thickness.getInt();
            int limit = maxDraw.getInt(), tracerLimit = tracerCount.getInt();
            boolean wire = style.is("Tel");
            int count = 0, tr = 0, lab = 0;
            double[] a = new double[2];
            for (Found f : list) {
                if (count >= limit) break;
                if (!isOn(f.type())) continue;
                BlockPos bp = f.pos();
                double d2 = bp.distToCenterSqr(pos);
                if (d2 > maxD) break; // liste mesafeye gore sirali
                int col = rainbow.get() ? Hud.rainbow(count * 0.05, true) : baseColor(f.type());
                double x = bp.getX(), y = bp.getY(), z = bp.getZ();
                if (wire) Overlay.wireBox(g, x, y, z, x + 1, y + 1, z + 1, col, th);
                else Overlay.flatBox(g, x, y, z, x + 1, y + 1, z + 1, col);
                if (tracers.get() && tr < tracerLimit && Overlay.project(x + 0.5, y + 0.5, z + 0.5, a)) {
                    Overlay.line(g, Overlay.sw / 2.0, Overlay.sh / 2.0, a[0], a[1], col, th);
                    tr++;
                }
                if (labels.get() && lab < 30 && Overlay.project(x + 0.5, y + 1.15, z + 0.5, a)) {
                    Overlay.label(g, typeName(f.type()) + " " + (int) Math.sqrt(d2) + "m", a[0], a[1], col);
                    lab++;
                }
                count++;
            }
        }
    }

    public static class OreESP extends BlockEsp {
        private static final int[] COLORS = {
                0xFF55FFFF, 0xFFB5651D, 0xFF17DD62, 0xFFFFD700, 0xFFD8AF93,
                0xFFAAAAAA, 0xFFFF2020, 0xFF2244FF, 0xFFE07B39, 0xFFEEEEEE};
        private static final String[] NAMES = {
                "Elmas", "Antik Kalıntı", "Zümrüt", "Altın", "Demir",
                "Kömür", "Kızıltaş", "Lapis", "Bakır", "Kuvars"};
        private final BoolSetting[] ores;

        public OreESP() {
            super("MadenGöster", "Seçilen madenleri duvar arkasından gösterir");
            ores = new BoolSetting[]{
                    add(new BoolSetting("Elmas", true)),
                    add(new BoolSetting("AntikKalıntı", true)),
                    add(new BoolSetting("Zümrüt", true)),
                    add(new BoolSetting("Altın", false)),
                    add(new BoolSetting("Demir", false)),
                    add(new BoolSetting("Kömür", false)),
                    add(new BoolSetting("Kızıltaş", false)),
                    add(new BoolSetting("Lapis", false)),
                    add(new BoolSetting("Bakır", false)),
                    add(new BoolSetting("Kuvars", false))};
        }

        @Override protected int match(String p) {
            if (p.contains("diamond_ore")) return 0;
            if (p.equals("ancient_debris")) return 1;
            if (p.contains("emerald_ore")) return 2;
            if (p.contains("gold_ore")) return 3;
            if (p.contains("iron_ore")) return 4;
            if (p.contains("coal_ore")) return 5;
            if (p.contains("redstone_ore")) return 6;
            if (p.contains("lapis_ore")) return 7;
            if (p.contains("copper_ore")) return 8;
            if (p.equals("nether_quartz_ore")) return 9;
            return -1;
        }

        @Override protected boolean isOn(int t) { return ores[t].get(); }
        @Override protected int baseColor(int t) { return COLORS[t]; }
        @Override protected String typeName(int t) { return NAMES[t]; }
    }

    public static class StorageESP extends BlockEsp {
        private static final int[] COLORS = {0xFFFFA500, 0xFFAA00FF, 0xFFB07840, 0xFFFF77FF, 0xFFFF3030};
        private static final String[] NAMES = {"Sandık", "Ender Sandığı", "Fıçı", "Shulker", "Spawner"};
        private final BoolSetting[] types;

        public StorageESP() {
            super("DepoGöster", "Sandık, shulker ve spawner'ları gösterir");
            types = new BoolSetting[]{
                    add(new BoolSetting("Sandık", true)),
                    add(new BoolSetting("EnderSandığı", true)),
                    add(new BoolSetting("Fıçı", true)),
                    add(new BoolSetting("Shulker", true)),
                    add(new BoolSetting("Spawner", true))};
        }

        @Override protected int match(String p) {
            if (p.equals("ender_chest")) return 1;
            if (p.equals("chest") || p.equals("trapped_chest")) return 0;
            if (p.equals("barrel")) return 2;
            if (p.endsWith("shulker_box")) return 3;
            if (p.equals("spawner") || p.equals("trial_spawner")) return 4;
            return -1;
        }

        @Override protected boolean isOn(int t) { return types[t].get(); }
        @Override protected int baseColor(int t) { return COLORS[t]; }
        @Override protected String typeName(int t) { return NAMES[t]; }
    }
}
