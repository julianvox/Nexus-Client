package dev.nexus.util;

import dev.nexus.config.UiPrefs;

/** Mor - sarı - siyah - beyaz renk teması ve animasyonlu geçişler. */
public final class Theme {
    public static final int PURPLE = 0xFF8A5CFF;
    public static final int YELLOW = 0xFFFFD43B;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF0A0A10;

    private static final int[] CYCLE = {PURPLE, YELLOW, WHITE};

    private Theme() {}

    public static int lerp(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Mor -> sarı -> beyaz -> mor döngüsü (süs çizgiler, logo, parıltı). */
    public static int color(double phase) {
        if (!UiPrefs.fx) return PURPLE;
        double t = (System.currentTimeMillis() / 6000.0 + phase) % 1.0;
        if (t < 0) t += 1.0;
        double p = t * 3.0;
        int i = (int) p;
        double f = p - i;
        f = f * f * (3 - 2 * f);
        return lerp(CYCLE[i % 3], CYCLE[(i + 1) % 3], f);
    }

    /** Sadece mor <-> sarı (üstüne beyaz/siyah yazı konulan yüzeyler için). */
    public static int accent(double phase) {
        if (!UiPrefs.fx) return PURPLE;
        double s = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1200.0 + phase * 6.28318);
        return lerp(PURPLE, YELLOW, s);
    }
}
