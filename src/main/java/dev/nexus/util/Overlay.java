package dev.nexus.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Dunya -> ekran projeksiyonu. Render API'sine dokunmadan, 2D HUD uzerine
 * kutu / cizgi cizmek icin (ESP, tracer, maden ESP).
 */
public final class Overlay {
    private static final Minecraft mc = Minecraft.getInstance();

    private static double ex, ey, ez, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalf, aspect;
    public static int sw, sh;

    private Overlay() {}

    public static void begin(float pt) {
        LocalPlayer p = mc.player;
        Vec3 eye = p.getEyePosition(pt);
        ex = eye.x; ey = eye.y; ez = eye.z;
        double yaw = Math.toRadians(p.getViewYRot(pt));
        double pitch = Math.toRadians(p.getViewXRot(pt));
        double cp = Math.cos(pitch);
        fx = -Math.sin(yaw) * cp;
        fy = -Math.sin(pitch);
        fz = Math.cos(yaw) * cp;
        rx = -Math.cos(yaw);
        ry = 0;
        rz = -Math.sin(yaw);
        ux = ry * fz - rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy - ry * fx;
        sw = mc.getWindow().getGuiScaledWidth();
        sh = mc.getWindow().getGuiScaledHeight();
        aspect = (double) sw / (double) sh;
        double fov = mc.options.fov().get() * fovModifier(p);
        tanHalf = Math.tan(Math.toRadians(fov) / 2.0);
    }

    /** Kosarken / hiz efektinde oyunun FOV'u genisletmesini taklit eder. */
    private static double fovModifier(LocalPlayer p) {
        try {
            double f = 1.0;
            if (p.getAbilities().flying) f *= 1.1;
            double base = p.getAbilities().getWalkingSpeed();
            if (base > 0) f *= (p.getAttributeValue(Attributes.MOVEMENT_SPEED) / base + 1.0) / 2.0;
            double scale = mc.options.fovEffectScale().get();
            return 1.0 + (f - 1.0) * scale;
        } catch (Throwable t) {
            return 1.0;
        }
    }

    /** out[0]=x, out[1]=y ekran koordinati. Kamera arkasindaysa false. */
    public static boolean project(double x, double y, double z, double[] out) {
        double dx = x - ex, dy = y - ey, dz = z - ez;
        double d = dx * fx + dy * fy + dz * fz;
        if (d < 0.05) return false;
        double px = dx * rx + dy * ry + dz * rz;
        double py = dx * ux + dy * uy + dz * uz;
        double nx = (px / d) / (tanHalf * aspect);
        double ny = (py / d) / tanHalf;
        out[0] = sw / 2.0 * (1.0 + nx);
        out[1] = sh / 2.0 * (1.0 - ny);
        return true;
    }

    private static int clampI(double v) {
        return (int) Math.max(-4000, Math.min(4000, v));
    }

    private static final double[] CLIP = new double[4];

    /** Liang-Barsky: cizgiyi ekrana kirpar (ekran disi kisimlar cizilmez -> FPS). */
    private static boolean clip(double x1, double y1, double x2, double y2) {
        double xmin = -2, ymin = -2, xmax = sw + 2, ymax = sh + 2;
        double dx = x2 - x1, dy = y2 - y1;
        double t0 = 0, t1 = 1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x1 - xmin, xmax - x1, y1 - ymin, ymax - y1};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (q[i] < 0) return false;
            } else {
                double r = q[i] / p[i];
                if (p[i] < 0) {
                    if (r > t1) return false;
                    if (r > t0) t0 = r;
                } else {
                    if (r < t0) return false;
                    if (r < t1) t1 = r;
                }
            }
        }
        CLIP[0] = x1 + t0 * dx;
        CLIP[1] = y1 + t0 * dy;
        CLIP[2] = x1 + t1 * dx;
        CLIP[3] = y1 + t1 * dy;
        return true;
    }

    /** Cizgi: sadece min(|dx|,|dy|)+1 adet dikdortgen cizer (piksel piksel degil). */
    public static void line(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, int color, int thick) {
        if (!clip(x1, y1, x2, y2)) return;
        int ax = (int) Math.round(CLIP[0]), ay = (int) Math.round(CLIP[1]);
        int bx = (int) Math.round(CLIP[2]), by = (int) Math.round(CLIP[3]);
        int adx = Math.abs(bx - ax), ady = Math.abs(by - ay);
        if (adx >= ady) {
            if (ady == 0) {
                g.fill(Math.min(ax, bx), ay, Math.max(ax, bx) + thick, ay + thick, color);
                return;
            }
            int sy = by > ay ? 1 : -1;
            int lox = Math.min(ax, bx), hix = Math.max(ax, bx);
            for (int k = 0; k <= ady; k++) {
                int y = ay + sy * k;
                double xa = ax + (double) (bx - ax) * (k - 0.5) / ady;
                double xb = ax + (double) (bx - ax) * (k + 0.5) / ady;
                int lo = Math.max(lox, (int) Math.round(Math.min(xa, xb)));
                int hi = Math.min(hix, (int) Math.round(Math.max(xa, xb)));
                if (hi < lo) hi = lo;
                g.fill(lo, y, hi + thick, y + thick, color);
            }
        } else {
            int sx = bx > ax ? 1 : -1;
            int loy = Math.min(ay, by), hiy = Math.max(ay, by);
            for (int k = 0; k <= adx; k++) {
                int x = ax + sx * k;
                double ya = ay + (double) (by - ay) * (k - 0.5) / adx;
                double yb = ay + (double) (by - ay) * (k + 0.5) / adx;
                int lo = Math.max(loy, (int) Math.round(Math.min(ya, yb)));
                int hi = Math.min(hiy, (int) Math.round(Math.max(ya, yb)));
                if (hi < lo) hi = lo;
                g.fill(x, lo, x + thick, hi + thick, color);
            }
        }
    }

    /** Ortalanmis yazi (arka planli). Ekran disindaysa cizmez. */
    public static void label(GuiGraphicsExtractor g, String text, double cx, double cy, int color) {
        int w = mc.font.width(text);
        int x = (int) cx - w / 2, y = (int) cy - 10;
        if (x + w < 0 || x > sw || y < -12 || y > sh) return;
        g.fill(x - 2, y - 1, x + w + 2, y + 9, 0x90000000);
        g.text(mc.font, text, x, y, color, true);
    }

    public static void rectOutline(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    private static final double[] TMP = new double[2];
    private static final double[] BX = new double[8], BY = new double[8];

    /** 3D tel kafes kutu (12 kenar). Kosegenlerden biri kamera arkasindaysa cizmez. */
    public static boolean wireBox(GuiGraphicsExtractor g, double x1, double y1, double z1,
                                  double x2, double y2, double z2, int color, int thick) {
        double[] sx = BX, sy = BY;
        boolean allLeft = true, allRight = true, allTop = true, allBottom = true;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? x1 : x2;
            double y = (i & 2) == 0 ? y1 : y2;
            double z = (i & 4) == 0 ? z1 : z2;
            if (!project(x, y, z, TMP)) return false;
            sx[i] = TMP[0];
            sy[i] = TMP[1];
            if (sx[i] >= 0) allLeft = false;
            if (sx[i] <= Overlay.sw) allRight = false;
            if (sy[i] >= 0) allTop = false;
            if (sy[i] <= Overlay.sh) allBottom = false;
        }
        if (allLeft || allRight || allTop || allBottom) return true; // ekran disinda, cizme
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((i & bit) == 0) line(g, sx[i], sy[i], sx[i | bit], sy[i | bit], color, thick);
            }
        }
        return true;
    }

    /** 2D ekran kutusu (8 kosenin sinir dikdortgeni). */
    public static boolean flatBox(GuiGraphicsExtractor g, double x1, double y1, double z1,
                                  double x2, double y2, double z2, int color) {
        double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? x1 : x2;
            double y = (i & 2) == 0 ? y1 : y2;
            double z = (i & 4) == 0 ? z1 : z2;
            if (!project(x, y, z, TMP)) return false;
            minX = Math.min(minX, TMP[0]);
            maxX = Math.max(maxX, TMP[0]);
            minY = Math.min(minY, TMP[1]);
            maxY = Math.max(maxY, TMP[1]);
        }
        rectOutline(g, clampI(minX), clampI(minY), clampI(maxX), clampI(maxY), color);
        return true;
    }
}
