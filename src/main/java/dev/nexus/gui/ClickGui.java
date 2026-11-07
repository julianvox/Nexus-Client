package dev.nexus.gui;

import dev.nexus.NexusClient;
import dev.nexus.config.ConfigManager;
import dev.nexus.config.UiPrefs;
import dev.nexus.hud.Hud;
import dev.nexus.module.Category;
import dev.nexus.module.Module;
import dev.nexus.module.ModuleManager;
import dev.nexus.setting.BoolSetting;
import dev.nexus.setting.ListSetting;
import dev.nexus.setting.ModeSetting;
import dev.nexus.setting.NumSetting;
import dev.nexus.setting.Setting;
import dev.nexus.util.FriendManager;
import dev.nexus.util.Helper;
import dev.nexus.util.Names;
import dev.nexus.util.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nexus menüsü: vanilla görünümünden bağımsız, kendi çizdiği arayüz.
 * Minecraft 26.3 SDL3 kullanır: fare tuşları Sol=1, Orta=2, Sağ=3, yan tuşlar 4-5;
 * klavye kodları SDL scancode'dur.
 */
public class ClickGui extends Screen {
    private static final int LEFT = 1, MIDDLE = 2, RIGHT = 3;
    private static final int TITLE_H = 26, FOOT_H = 14, SIDE_W = 84;
    private static final int CATS = Category.values().length;

    private static final int ACCENT = 0xFF8A5CFF, ACCENT2 = 0xFFFFD43B;
    private static final int BG = 0xFA0A0A10, PANEL = 0xFF10101A, SIDE = 0xFF07070C, HOVER = 0xFF1D1735;
    private static final int TXT = 0xFFF4F2FA, DIM = 0xFF9C98B4, GREEN = 0xFFFFD43B, RED = 0xFFFF6B6B;

    private enum Focus { NONE, SEARCH, NUM, PROFILE, FRIEND }

    @FunctionalInterface
    private interface Click { void run(int btn, double mx, double my); }

    private record Hit(int x, int y, int w, int h, int top, int bottom, Click action) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h && my >= top && my < bottom;
        }
    }

    // ---- kalıcı durum (menü kapanıp açılınca korunur)
    private static int catIndex = 0;
    private static Module selected;

    // ---- anlık durum
    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> menuHits = new ArrayList<>();

    // ---- ipuçları: yazının üstünde biraz bekleyince açıklama çıkar
    private record Tip(int x, int y, int w, int h, int top, int bottom, String title, String body) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h && my >= top && my < bottom;
        }
    }

    private final List<Tip> tips = new ArrayList<>();
    private String lastTipKey;
    private long tipSince;
    private static final long TIP_DELAY_MS = 350;
    private final Map<Module, Float> anim = new HashMap<>();
    private final long openTime = System.currentTimeMillis();
    private float fade = 1f;

    private int wx, wy, ww, wh, bodyTop, bodyBottom, listX, listW, setX, setW;
    private int listScroll, setScroll;

    private Focus focus = Focus.NONE;
    private final StringBuilder searchBuf = new StringBuilder();
    private final StringBuilder numBuf = new StringBuilder();
    private final StringBuilder profileBuf = new StringBuilder();
    private final StringBuilder friendBuf = new StringBuilder();
    private NumSetting editing;
    private NumSetting dragSlider;
    private int dragSX, dragSW;

    private Module ctxModule;
    private int ctxX, ctxY;
    private String tooltip;
    private String status = "";
    private long statusUntil;
    private long confirmUntil;

    public ClickGui() {
        super(Component.literal("Nexus"));
        if (selected == null) {
            List<Module> l = ModuleManager.byCategory(Category.values()[0]);
            if (!l.isEmpty()) selected = l.get(0);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private float ease = 1f;
    private final Map<Object, Float> hoverMap = new HashMap<>();

    private float hoverAnim(Object key, boolean hov) {
        float v = hoverMap.getOrDefault(key, 0f);
        float tg = hov ? 1f : 0f;
        v += (tg - v) * 0.25f;
        if (Math.abs(tg - v) < 0.01f) v = tg;
        hoverMap.put(key, v);
        return v;
    }

    private static int alpha(int argb, float a) {
        return ((int) (((argb >>> 24) & 255) * a) << 24) | (argb & 0xFFFFFF);
    }

    /** Mor <-> sarı akan renk geçişli çubuk. */
    private void gradBar(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
        int w = x2 - x1;
        if (w <= 0) return;
        for (int k = 0; k < w; k += 4) {
            f(g, x1 + k, y1, Math.min(x1 + k + 4, x2), y2, Theme.accent(k * 0.012));
        }
    }

    /** Arka planda süzülen mor / sarı parçacıklar. */
    private void renderParticles(GuiGraphicsExtractor g) {
        if (!UiPrefs.fx) return;
        double tm = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < 30; i++) {
            double sp = 6 + (i % 5) * 3.5;
            double py = (this.height + 10) - ((tm * sp + i * 53) % (this.height + 20));
            double px = ((i * 97) % Math.max(1, this.width)) + Math.sin(tm * 0.6 + i) * 6;
            int sz = i % 3 == 0 ? 2 : 1;
            int base = (i % 2 == 0) ? 0x8A5CFF : 0xFFD43B;
            int a = (int) (45 + 35 * Math.sin(tm + i));
            f(g, (int) px, (int) py, (int) px + sz, (int) py + sz, (a << 24) | base);
        }
    }

    /** Pencerenin etrafında dönen animasyonlu renk çerçevesi. */
    private void renderBorder(GuiGraphicsExtractor g) {
        int step = 4;
        for (int k = 0; k < ww + 2; k += step) {
            int e = Math.min(wx - 1 + k + step, wx + ww + 1);
            f(g, wx - 1 + k, wy - 1, e, wy, Theme.color(k / (double) ww));
            f(g, wx - 1 + k, wy + wh, e, wy + wh + 1, Theme.color(0.5 + k / (double) ww));
        }
        for (int k = 0; k < wh; k += step) {
            int e = Math.min(wy + k + step, wy + wh);
            f(g, wx - 1, wy + k, wx, e, Theme.color(0.25 + k / (double) wh));
            f(g, wx + ww, wy + k, wx + ww + 1, e, Theme.color(0.75 + k / (double) wh));
        }
    }

    // ---- ince yazı tipi (Roboto Light): assets/nexus/font/ui.json
    private static final Identifier UI_FONT = Identifier.fromNamespaceAndPath("nexus", "ui");
    private static Style slimStyle;
    private static boolean slimTried;

    private static Style slim() {
        if (!UiPrefs.slimFont) return null;
        if (!slimTried) {
            slimTried = true;
            try {
                slimStyle = buildSlim();
            } catch (Throwable t) {
                NexusClient.LOGGER.warn("İnce yazı tipi ayarlanamadı", t);
            }
            NexusClient.LOGGER.info("İnce yazı tipi: {}", slimStyle == null ? "kullanılamıyor" : "etkin");
        }
        return slimStyle;
    }

    /** Style.withFont'un parametre türü sürüme göre değişebilir (Identifier / FontDescription). */
    private static Style buildSlim() throws Exception {
        for (Method m : Style.class.getMethods()) {
            if (!m.getName().equals("withFont") || m.getParameterCount() != 1) continue;
            Class<?> pt = m.getParameterTypes()[0];
            Object arg = null;
            if (pt.isAssignableFrom(Identifier.class)) {
                arg = UI_FONT;
            } else {
                for (Class<?> inner : pt.getDeclaredClasses()) {
                    if (!pt.isAssignableFrom(inner)) continue;
                    try {
                        arg = inner.getConstructor(Identifier.class).newInstance(UI_FONT);
                        break;
                    } catch (Throwable ignored) {}
                }
            }
            if (arg != null) return (Style) m.invoke(Style.EMPTY, arg);
        }
        return null;
    }

    private int tw(String s) {
        Style st = slim();
        return st != null ? this.font.width(Component.literal(s).withStyle(st)) : this.font.width(s);
    }

    // =========================================================== çizim yardımcıları
    private int c(int argb) {
        int a = (int) (((argb >>> 24) & 255) * fade);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private void f(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int col) {
        g.fill(x1, y1, x2, y2, c(col));
    }

    /** Hafif yuvarlatılmış dikdörtgen. */
    private void rr(GuiGraphicsExtractor g, int x, int y, int w, int h, int col) {
        int cc = c(col);
        g.fill(x + 2, y, x + w - 2, y + h, cc);
        g.fill(x, y + 2, x + w, y + h - 2, cc);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, cc);
    }

    private void t(GuiGraphicsExtractor g, String s, int x, int y, int col) {
        Style st = slim();
        if (st != null) g.text(this.font, Component.literal(s).withStyle(st), x, y, c(col), true);
        else g.text(this.font, s, x, y, c(col), true);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int lerpColor(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private String truncate(String s, int maxW) {
        if (tw(s) <= maxW) return s;
        while (s.length() > 1 && tw(s + "...") > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private void drawSwitch(GuiGraphicsExtractor g, int x, int y, float t) {
        rr(g, x, y, 22, 10, lerpColor(0xFF3A3552, Theme.accent(x * 0.01 + y * 0.004), t));
        int kx = x + 2 + (int) (12 * t);
        f(g, kx, y + 2, kx + 8, y + 8, lerpColor(0xFFFFFFFF, 0xFF0A0A10, t));
    }

    private float anim(Module m) {
        float tg = m.enabled ? 1f : 0f;
        float v = anim.getOrDefault(m, tg);
        v += (tg - v) * 0.3f;
        if (Math.abs(tg - v) < 0.01f) v = tg;
        anim.put(m, v);
        return v;
    }

    private void tip(int x, int y, int w, int h, String title, String body) {
        tips.add(new Tip(x, y, w, h, Integer.MIN_VALUE, Integer.MAX_VALUE, title, body));
    }

    private void tipClip(int x, int y, int w, int h, int top, int bottom, String title, String body) {
        tips.add(new Tip(x, y, w, h, top, bottom, title, body));
    }

    private List<String> wrap(String text, int maxW) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) {
                out.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String test = line.length() == 0 ? word : line + " " + word;
                if (tw(test) > maxW && line.length() > 0) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(test);
                }
            }
            if (line.length() > 0) out.add(line.toString());
        }
        return out;
    }

    private void renderTooltip(GuiGraphicsExtractor g, int mx, int my) {
        Tip found = null;
        for (int i = tips.size() - 1; i >= 0; i--) {
            if (tips.get(i).in(mx, my)) {
                found = tips.get(i);
                break;
            }
        }
        if (found == null || dragSlider != null) {
            lastTipKey = null;
            return;
        }
        String key = found.title() + "|" + found.x() + "|" + found.y();
        long now = System.currentTimeMillis();
        if (!key.equals(lastTipKey)) {
            lastTipKey = key;
            tipSince = now;
            return;
        }
        if (now - tipSince < TIP_DELAY_MS) return;

        int maxW = Math.min(240, this.width / 2);
        List<String> lines = wrap(found.body(), maxW);
        int tw = tw(found.title());
        for (String l : lines) tw = Math.max(tw, tw(l));
        tw += 14;
        boolean hasTitle = !found.title().isEmpty();
        int th = 10 + (hasTitle ? 12 : 0) + lines.size() * 10;
        int x = mx + 12, y = my + 14;
        if (x + tw > this.width - 4) x = mx - tw - 8;
        if (y + th > this.height - 4) y = my - th - 8;
        x = Math.max(4, x);
        y = Math.max(4, y);

        f(g, x - 1, y - 1, x + tw + 1, y + th + 1, 0x99000000);
        rr(g, x, y, tw, th, 0xFA14141F);
        gradBar(g, x + 2, y, x + tw - 2, y + 2);
        int ty = y + 6;
        if (hasTitle) {
            t(g, found.title(), x + 7, ty, ACCENT2);
            ty += 12;
        }
        for (String l : lines) {
            t(g, l, x + 7, ty, TXT);
            ty += 10;
        }
    }

    private void hit(int x, int y, int w, int h, Click a) {
        hits.add(new Hit(x, y, w, h, Integer.MIN_VALUE, Integer.MAX_VALUE, a));
    }

    private void hitClip(int x, int y, int w, int h, int top, int bottom, Click a) {
        hits.add(new Hit(x, y, w, h, top, bottom, a));
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, double mx, double my, int col, Click a) {
        boolean hov = in(mx, my, x, y, w, h);
        rr(g, x, y, w, h, hov ? lerpColor(col, 0xFFFFFFFF, 0.15f) : col);
        t(g, text, x + (w - tw(text)) / 2, y + (h - 8) / 2, TXT);
        hit(x, y, w, h, a);
    }

    private void field(GuiGraphicsExtractor g, int x, int y, int w, StringBuilder buf, Focus fc, String placeholder) {
        boolean foc = focus == fc;
        rr(g, x, y, w, 14, foc ? 0xFF2A2250 : 0xFF151124);
        if (foc) gradBar(g, x + 2, y + 13, x + w - 2, y + 14);
        boolean empty = buf.length() == 0 && !foc;
        String s = empty ? placeholder : buf + (foc ? "_" : "");
        t(g, truncate(s, w - 8), x + 4, y + 3, empty ? DIM : TXT);
        hit(x, y, w, 14, (btn, cx, cy) -> { if (btn == LEFT) focus = fc; });
    }

    private void setStatus(String s) {
        status = s;
        statusUntil = System.currentTimeMillis() + 2500;
    }

    // =========================================================== yerleşim
    private void layout() {
        ww = Math.max(300, Math.min(this.width - 12, 480));
        wh = Math.max(200, Math.min(this.height - 12, 300));
        wx = (this.width - ww) / 2;
        wy = (this.height - wh) / 2 + (UiPrefs.fx ? (int) ((1f - ease) * 18) : 0);
        bodyTop = wy + TITLE_H;
        bodyBottom = wy + wh - FOOT_H;
        listX = wx + SIDE_W;
        listW = Math.min(150, (ww - SIDE_W) / 2);
        setX = listX + listW;
        setW = wx + ww - setX;
    }

    private List<Module> view() {
        String q = Names.norm(searchBuf.toString()).trim();
        List<Module> out = new ArrayList<>();
        if (q.isEmpty()) {
            if (catIndex < CATS) out.addAll(ModuleManager.byCategory(Category.values()[catIndex]));
        } else {
            for (Module m : ModuleManager.MODULES) {
                if (m.hidden) continue;
                if (Names.norm(m.name).contains(q) || Names.norm(m.description).contains(q)) out.add(m);
            }
            out.sort(Comparator.comparing(m -> m.name));
        }
        return out;
    }

    // =========================================================== ana çizim
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        long nowMs = System.currentTimeMillis();
        float ep = Math.min(1f, (nowMs - openTime) / 280f);
        ease = 1f - (1f - ep) * (1f - ep) * (1f - ep);
        fade = UiPrefs.fx ? Math.min(1f, (nowMs - openTime) / 200f) : 1f;
        layout();
        hits.clear();
        menuHits.clear();
        tooltip = null;
        tips.clear();

        boolean menuOpen = ctxModule != null;
        int hx = menuOpen ? -10000 : mx, hy = menuOpen ? -10000 : my;

        f(g, 0, 0, this.width, this.height, 0x88000000);
        renderParticles(g);
        f(g, wx - 2, wy - 2, wx + ww + 2, wy + wh + 2, 0x66000000);
        renderBorder(g);
        rr(g, wx, wy, ww, wh, BG);

        renderTitle(g, hx, hy);
        renderSidebar(g, hx, hy);
        if (catIndex < CATS) {
            renderList(g, hx, hy);
            renderSettings(g, hx, hy);
        } else {
            renderProfile(g, hx, hy);
        }
        renderFooter(g);
        if (menuOpen) {
            renderMenu(g, mx, my);
            lastTipKey = null;
        } else {
            renderTooltip(g, mx, my);
        }
    }

    private void renderTitle(GuiGraphicsExtractor g, int mx, int my) {
        f(g, wx, wy, wx + ww, wy + TITLE_H, 0xFF06060B);
        if (UiPrefs.fx) { // başlık çubuğunda gezinen ışık
            int span = ww + 120;
            int sx = wx + (int) ((System.currentTimeMillis() / 9) % span) - 60;
            for (int k = 0; k < 3; k++) {
                int x1 = Math.max(wx, sx + k * 14), x2 = Math.min(wx + ww, sx + k * 14 + 14);
                if (x2 > x1) f(g, x1, wy, x2, wy + TITLE_H - 1, ((k == 1 ? 0x22 : 0x10) << 24) | 0xFFFFFF);
            }
        }

        String logo = "NEXUS";
        int lx = wx + 10;
        for (int i = 0; i < logo.length(); i++) {
            String ch = String.valueOf(logo.charAt(i));
            t(g, ch, lx, wy + 9, Theme.color(i * 0.09));
            lx += tw(ch) + 1;
        }
        t(g, "26.3", lx + 4, wy + 9, DIM);
        tip(wx + 8, wy + 4, lx - wx, 18, "Nexus", "Minecraft 26.3 için menülü ve ayarlanabilir istemci. Menüyü açma tuşunu ESC > Seçenekler > Kontroller > Tuş Atamaları > Nexus - Genel altından değiştirebilirsin.");

        int sbw = Math.max(60, Math.min(150, ww - 178 - tw(ConfigManager.current)));
        int sbx = wx + 96, sby = wy + 6;
        boolean foc = focus == Focus.SEARCH;
        rr(g, sbx, sby, sbw, 14, foc ? 0xFF2A2250 : 0xFF151124);
        boolean empty = searchBuf.length() == 0 && !foc;
        String st = empty ? "Modül ara..." : searchBuf + (foc ? "_" : "");
        t(g, truncate(st, sbw - 8), sbx + 4, sby + 3, empty ? DIM : TXT);
        tip(sbx, sby, sbw, 14, "Modül ara", "Modülleri adına veya açıklamasına göre arar. Tıklayıp yazabilirsin; menüdeyken harf tuşlarına basınca da otomatik buraya yazar. Esc ile aramayı temizlersin.");
        hit(sbx, sby, sbw, 14, (btn, cx, cy) -> {
            if (btn == LEFT) {
                if (catIndex >= CATS) catIndex = 0;
                focus = Focus.SEARCH;
            }
        });

        String prof = ConfigManager.current;
        int pw = tw(prof);
        t(g, prof, wx + ww - 24 - pw, wy + 9, ACCENT2);
        int fxX = wx + ww - 24 - pw - 26, aaX = fxX - 24;
        boolean aaH = in(mx, my, aaX, wy + 5, 20, 16), fxH = in(mx, my, fxX, wy + 5, 20, 16);
        rr(g, aaX, wy + 5, 20, 16, UiPrefs.slimFont ? 0xFF3B2B86 : (aaH ? 0xFF2A2250 : 0xFF151124));
        t(g, "Aa", aaX + 10 - tw("Aa") / 2, wy + 9, UiPrefs.slimFont ? TXT : DIM);
        rr(g, fxX, wy + 5, 20, 16, UiPrefs.fx ? 0xFF3B2B86 : (fxH ? 0xFF2A2250 : 0xFF151124));
        t(g, "FX", fxX + 10 - tw("FX") / 2, wy + 9, UiPrefs.fx ? ACCENT2 : DIM);
        tip(aaX, wy + 5, 20, 16, "İnce yazı tipi: " + (UiPrefs.slimFont ? "AÇIK" : "kapalı"), "Menü yazılarını ince ve okunaklı Roboto Light ile gösterir. Kapatırsan oyunun kendi yazı tipine döner. Tıklayarak aç / kapat.");
        tip(fxX, wy + 5, 20, 16, "Animasyonlar: " + (UiPrefs.fx ? "AÇIK" : "kapalı"), "Renk geçişleri, süzülen parçacıklar, açılış efekti ve parıltılar. Bilgisayarın zorlanırsa kapatabilirsin. Tıklayarak aç / kapat.");
        hit(aaX, wy + 5, 20, 16, (btn, cx, cy) -> {
            if (btn == LEFT) {
                UiPrefs.slimFont = !UiPrefs.slimFont;
                ConfigManager.save();
            }
        });
        hit(fxX, wy + 5, 20, 16, (btn, cx, cy) -> {
            if (btn == LEFT) {
                UiPrefs.fx = !UiPrefs.fx;
                ConfigManager.save();
            }
        });
        tip(wx + ww - 28 - pw, wy + 4, pw + 6, 18, "Aktif profil: " + prof, "Ayarların bu profile kaydedilir. Başka profil seçmek veya yeni oluşturmak için soldaki Profil sekmesini kullan.");

        boolean ch = in(mx, my, wx + ww - 20, wy, 20, TITLE_H);
        t(g, "X", wx + ww - 14, wy + 9, ch ? RED : DIM);
        tip(wx + ww - 20, wy, 20, TITLE_H, "Kapat", "Menüyü kapatır ve ayarları kaydeder. Esc veya menü tuşu da aynı işi yapar.");
        hit(wx + ww - 20, wy, 20, TITLE_H, (btn, cx, cy) -> { if (btn == LEFT) this.onClose(); });

        for (int i = 0; i < ww; i += 3) {
            f(g, wx + i, wy + TITLE_H - 1, Math.min(wx + i + 3, wx + ww), wy + TITLE_H, Theme.color(i / (double) ww));
        }
    }

    private void renderSidebar(GuiGraphicsExtractor g, int mx, int my) {
        f(g, wx, bodyTop, wx + SIDE_W, bodyBottom, SIDE);
        Category[] cs = Category.values();
        int y = bodyTop + 8;
        for (int i = 0; i <= CATS; i++) {
            boolean prof = i == CATS;
            String label = prof ? "Profil" : cs[i].label;
            boolean active = i == catIndex;
            boolean hov = in(mx, my, wx, y, SIDE_W, 22);
            float tha = hoverAnim("tab" + i, hov);
            if (active) {
                f(g, wx, y, wx + SIDE_W, y + 22, 0xFF1A1432);
                f(g, wx, y, wx + 3, y + 22, Theme.color(i * 0.12));
            } else if (tha > 0.02f) {
                f(g, wx, y, wx + SIDE_W, y + 22, alpha(HOVER, tha));
            }
            t(g, label, wx + 12, y + 7, active ? TXT : DIM);
            if (!prof) {
                int n = 0;
                for (Module m : ModuleManager.byCategory(cs[i])) if (m.enabled) n++;
                if (n > 0) {
                    String ns = String.valueOf(n);
                    int cw = tw(ns) + 6;
                    rr(g, wx + SIDE_W - cw - 6, y + 5, cw, 12, 0xFF3A2A7A);
                    t(g, ns, wx + SIDE_W - cw - 3, y + 7, TXT);
                }
            }
            tip(wx, y, SIDE_W, 22, label, prof ? Help.profileTab() : Help.category(cs[i]));
            final int idx = i;
            hit(wx, y, SIDE_W, 22, (btn, cx, cy) -> {
                if (btn != LEFT) return;
                catIndex = idx;
                searchBuf.setLength(0);
                listScroll = 0;
                setScroll = 0;
                if (idx < CATS) {
                    List<Module> l = ModuleManager.byCategory(cs[idx]);
                    if (selected == null || selected.category != cs[idx]) selected = l.isEmpty() ? null : l.get(0);
                }
            });
            y += 24;
        }
        t(g, "ESC > Kontroller", wx + 8, bodyBottom - 20, 0xFF6E6A88);
        t(g, "> Tuş Atamaları", wx + 8, bodyBottom - 11, 0xFF6E6A88);
        tip(wx + 4, bodyBottom - 24, SIDE_W - 8, 22, "Tuş atama", "Modüllerin tuşlarını ESC > Seçenekler > Kontroller > Tuş Atamaları menüsünde 'Nexus' kategorilerinden atarsın. Menüdeki 'Tuşu Ayarla' düğmesi o ekranı açar.");
    }

    private void renderList(GuiGraphicsExtractor g, int mx, int my) {
        f(g, listX, bodyTop, listX + listW, bodyBottom, PANEL);
        List<Module> mods = view();
        boolean searching = searchBuf.length() > 0;
        String head = searching ? "Arama: " + mods.size() + " sonuç" : Category.values()[catIndex].label;
        t(g, head, listX + 8, bodyTop + 6, TXT);
        tip(listX + 4, bodyTop + 2, listW - 8, 16, head, searching ? "Arama sonuçları. Aramayı silmek için arama kutusuna tıkla ve Esc'e bas." : Help.category(Category.values()[catIndex]));

        int rowH = 22, viewTop = bodyTop + 20, viewBottom = bodyBottom;
        int content = mods.size() * rowH;
        int maxScroll = Math.max(0, content - (viewBottom - viewTop));
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));

        g.enableScissor(listX, viewTop, listX + listW, viewBottom);
        int y = viewTop - listScroll;
        for (Module m : mods) {
            if (y + rowH > viewTop && y < viewBottom) {
                boolean hov = in(mx, my, listX, y, listW, rowH) && my >= viewTop && my < viewBottom;
                boolean sel = m == selected;
                float rha = hoverAnim(m, hov);
                if (sel) rr(g, listX + 3, y + 1, listW - 6, rowH - 2, 0xFF2B2152);
                else if (rha > 0.02f) rr(g, listX + 3, y + 1, listW - 6, rowH - 2, alpha(HOVER, rha));
                if (hov) tooltip = m.description;
                if (m.enabled) f(g, listX + 3, y + 4, listX + 5, y + rowH - 4, Theme.color(y * 0.004));
                t(g, m.name, listX + 10, y + 7, m.enabled ? TXT : 0xFFBDB9D4);
                int swx = listX + listW - 32;
                String kt = truncate(m.keyText(), 44);
                if (!kt.isEmpty()) t(g, kt, swx - tw(kt) - 6, y + 7, DIM);
                drawSwitch(g, swx, y + 6, anim(m));
                tipClip(listX, y, listW - 34, rowH, viewTop, viewBottom, m.name, Help.module(m) + Help.keyLine(m));
                tipClip(swx - 4, y, 30, rowH, viewTop, viewBottom, m.name + ": aç / kapat", "Bu anahtara sol tıklayarak modülü açıp kapatırsın. Modülün adına tıklamak sadece ayarlarını gösterir. Şu an: " + (m.enabled ? "AÇIK" : "kapalı") + ".");
                // Satıra tıklamak sadece seçer (ayarları gösterir); aç/kapat için anahtara tıkla
                hitClip(listX, y, listW, rowH, viewTop, viewBottom, (btn, cx, cy) -> rowClick(m, btn, cx, cy));
                hitClip(swx - 4, y, 30, rowH, viewTop, viewBottom, (btn, cx, cy) -> {
                    if (btn == LEFT) m.toggle();
                    else rowClick(m, btn, cx, cy);
                });
            }
            y += rowH;
        }
        g.disableScissor();
        scrollbar(g, listX + listW - 3, viewTop, viewBottom, content, listScroll);
    }

    private void rowClick(Module m, int btn, double cx, double cy) {
        if (selected != m) {
            selected = m;
            setScroll = 0;
        }
        if (btn == RIGHT) {
            ctxModule = m;
            ctxX = (int) cx;
            ctxY = (int) cy;
        }
    }

    private void scrollbar(GuiGraphicsExtractor g, int x, int top, int bottom, int content, int scroll) {
        int view = bottom - top;
        if (content <= view) return;
        int bh = Math.max(12, view * view / content);
        int by = top + (int) ((view - bh) * ((double) scroll / (content - view)));
        f(g, x, by, x + 2, by + bh, Theme.accent(by * 0.01));
    }

    // =========================================================== ayar paneli
    private int modeLines(ModeSetting ms, int cw) {
        int lines = 1, x = 0;
        for (String o : ms.modes) {
            int w = tw(o) + 12;
            if (x > 0 && x + w > cw) {
                lines++;
                x = 0;
            }
            x += w + 4;
        }
        return lines;
    }

    private int listHeight(ListSetting ls) {
        if (!ls.expanded) return 20;
        int h = 24;
        for (String grp : ls.groups()) h += 16 + ((ls.inGroup(grp).size() + 1) / 2) * 16;
        return h + 2;
    }

    private int rowHeight(Setting s, int cw) {
        if (s instanceof ListSetting ls) return listHeight(ls);
        if (s instanceof NumSetting) return 34;
        if (s instanceof ModeSetting ms) return 18 + modeLines(ms, cw) * 15;
        return 20;
    }

    private void renderSettings(GuiGraphicsExtractor g, int mx, int my) {
        f(g, setX, bodyTop, setX + setW, bodyBottom, 0xFF0C0C14);
        Module m = selected;
        if (m == null) {
            String s = "Soldan bir modül seç";
            t(g, s, setX + (setW - tw(s)) / 2, bodyTop + 40, DIM);
            return;
        }
        int x = setX, w = setW, pad = 10;

        t(g, m.name, x + pad, bodyTop + 7, TXT);
        t(g, truncate(m.description, w - pad * 2 - 34), x + pad, bodyTop + 19, DIM);
        tip(x + pad, bodyTop + 4, w - pad * 2 - 34, 28, m.name, Help.module(m) + Help.keyLine(m));
        tip(x + w - pad - 26, bodyTop + 4, 30, 18, m.name + ": aç / kapat", "Modülü açıp kapatır. Şu an: " + (m.enabled ? "AÇIK" : "kapalı") + ".");
        drawSwitch(g, x + w - pad - 22, bodyTop + 8, anim(m));
        hit(x + w - pad - 26, bodyTop + 4, 30, 18, (btn, cx, cy) -> { if (btn == LEFT) m.toggle(); });

        int ky = bodyTop + 34;
        String kt = m.keyText();
        t(g, "Tuş: " + (kt.isEmpty() ? "atanmamış" : kt), x + pad, ky + 3, kt.isEmpty() ? DIM : TXT);
        tip(x + pad, ky, w - pad * 2 - 90, 14, "Tuş ataması", "Bu modülü açıp kapatan tuş. Atanmamışsa 'Tuşu Ayarla' düğmesiyle Kontroller ekranına gidip Nexus kategorisinden atayabilirsin. Fare yan tuşları da atanabilir.");
        String bt = "Tuşu Ayarla";
        int bw = tw(bt) + 14;
        tip(x + w - pad - bw, ky, bw, 14, "Tuşu Ayarla", "ESC > Seçenekler > Kontroller > Tuş Atamaları ekranını açar. Orada 'Nexus' kategorilerinden bu modülün tuşunu seçersin.");
        button(g, x + w - pad - bw, ky, bw, 14, bt, mx, my, 0xFF3B2B86, (btn, cx, cy) -> {
            if (btn == LEFT) Helper.openScreen(new KeyBindsScreen(this, Minecraft.getInstance().options));
        });
        f(g, x + pad, ky + 19, x + w - pad, ky + 20, 0xFF2A2250);

        int viewTop = ky + 24, viewBottom = bodyBottom - 2;
        int cw = w - pad * 2;
        int content = 0;
        for (Setting s : m.settings) content += rowHeight(s, cw);
        content += 24;
        int maxScroll = Math.max(0, content - (viewBottom - viewTop));
        setScroll = Math.max(0, Math.min(setScroll, maxScroll));

        g.enableScissor(x, viewTop, x + w, viewBottom);
        int y = viewTop - setScroll;
        for (Setting s : m.settings) {
            int rh = rowHeight(s, cw);
            boolean vis = y + rh > viewTop && y < viewBottom;
            if (vis) {
                boolean rowHov = in(mx, my, x, y, w, rh) && my >= viewTop && my < viewBottom;
                tipClip(x, y, w, rh, viewTop, viewBottom, s.name, Help.setting(m, s));
                if (rowHov && !(s instanceof ListSetting)) f(g, x + 4, y, x + w - 4, y + rh, 0x14FFFFFF);

                if (s instanceof BoolSetting bs) {
                    t(g, bs.name, x + pad, y + 6, TXT);
                    drawSwitch(g, x + w - pad - 22, y + 5, bs.get() ? 1f : 0f);
                    hitClip(x, y, w, rh, viewTop, viewBottom, (btn, cx, cy) -> {
                        if (btn == LEFT || btn == RIGHT) {
                            bs.toggle();
                            ConfigManager.save();
                        }
                    });

                } else if (s instanceof NumSetting ns) {
                    t(g, ns.name, x + pad, y + 3, TXT);
                    int boxW = 58, boxX = x + w - pad - boxW;
                    boolean ed = editing == ns;
                    rr(g, boxX, y + 1, boxW, 13, ed ? 0xFF3A2A6A : 0xFF171325);
                    String vt = ed ? numBuf + "_" : ns.display();
                    t(g, vt, boxX + boxW - tw(vt) - 4, y + 4, TXT);

                    int tx = x + pad, tw = w - pad * 2, ty = y + 23;
                    int fw = (int) (tw * ns.ratio());
                    rr(g, tx, ty, tw, 5, 0xFF1A1630);
                    if (fw > 0) gradBar(g, tx + 1, ty + 1, tx + Math.max(2, fw), ty + 4);
                    rr(g, tx + fw - 3, ty - 3, 7, 11, 0xFFFFFFFF);

                    final int fx = tx, fwid = tw;
                    tipClip(tx - 4, ty - 6, tw + 8, 17, viewTop, viewBottom, ns.name + " - kaydırıcı", "Fareyle basılı tutup sürükle. Sol uç en küçük değer (" + ns.min + "), sağ uç en büyük değerdir (" + ns.max + "). Şu an: " + ns.display() + ".");
                    hitClip(tx - 4, ty - 6, tw + 8, 17, viewTop, viewBottom, (btn, cx, cy) -> {
                        if (btn == LEFT) {
                            commitEdit();
                            dragSlider = ns;
                            dragSX = fx;
                            dragSW = fwid;
                            slide(ns, fx, fwid, cx);
                        }
                    });
                    tipClip(boxX, y + 1, boxW, 13, viewTop, viewBottom, ns.name + " - değer kutusu", "Tıkla, klavyeden bir sayı yaz ve Enter'a bas. Esc iptal eder. Aralık dışındaki değerler otomatik düzeltilir.");
                    hitClip(boxX, y + 1, boxW, 13, viewTop, viewBottom, (btn, cx, cy) -> {
                        if (btn == LEFT) {
                            editing = ns;
                            numBuf.setLength(0);
                            focus = Focus.NUM;
                        }
                    });

                } else if (s instanceof ListSetting ls) {
                    boolean hh = in(mx, my, x + pad, y + 2, cw, 16);
                    rr(g, x + pad, y + 2, cw, 16, hh ? 0xFF30275C : 0xFF171325);
                    t(g, ls.name + "  (" + ls.count() + "/" + ls.options.size() + " seçili)", x + pad + 6, y + 6, TXT);
                    t(g, ls.expanded ? "v" : ">", x + w - pad - 14, y + 6, DIM);
                    tipClip(x + pad, y + 2, cw, 16, viewTop, viewBottom, ls.name, Help.setting(m, s) + "\nTıklayarak listeyi aç / kapat.");
                    hitClip(x + pad, y + 2, cw, 16, viewTop, viewBottom, (btn, cx, cy) -> {
                        if (btn == LEFT) ls.expanded = !ls.expanded;
                    });
                    if (ls.expanded) {
                        int ly = y + 24;
                        int colW = cw / 2;
                        for (String grp : ls.groups()) {
                            t(g, grp, x + pad, ly + 3, ACCENT2);
                            int gbx = x + w - pad - 70;
                            boolean h1 = in(mx, my, gbx, ly, 34, 13), h2 = in(mx, my, gbx + 36, ly, 34, 13);
                            rr(g, gbx, ly, 34, 13, h1 ? 0xFF6B4FE0 : 0xFF3F2F8F);
                            t(g, "Tümü", gbx + 17 - tw("Tümü") / 2, ly + 3, TXT);
                            rr(g, gbx + 36, ly, 34, 13, h2 ? 0xFF5A2530 : 0xFF38222A);
                            t(g, "Hiç", gbx + 36 + 17 - tw("Hiç") / 2, ly + 3, TXT);
                            tipClip(x + pad, ly, gbx - x - pad - 4, 14, viewTop, viewBottom, grp + " grubu", "Bu gruptaki " + ls.inGroup(grp).size() + " canlıdan seçili olanları aşağıdaki kutularla belirlersin.");
                            tipClip(gbx, ly, 34, 13, viewTop, viewBottom, grp + ": Tümü", "Bu gruptaki bütün canlıları seçer.");
                            tipClip(gbx + 36, ly, 34, 13, viewTop, viewBottom, grp + ": Hiç", "Bu gruptaki hiçbir canlıyı seçmez (hepsinin işaretini kaldırır).");
                            hitClip(gbx, ly, 34, 13, viewTop, viewBottom, (btn, cx, cy) -> {
                                if (btn == LEFT) {
                                    ls.setGroup(grp, true);
                                    ConfigManager.save();
                                }
                            });
                            hitClip(gbx + 36, ly, 34, 13, viewTop, viewBottom, (btn, cx, cy) -> {
                                if (btn == LEFT) {
                                    ls.setGroup(grp, false);
                                    ConfigManager.save();
                                }
                            });
                            ly += 16;
                            List<ListSetting.Option> opts = ls.inGroup(grp);
                            for (int i = 0; i < opts.size(); i++) {
                                final ListSetting.Option op = opts.get(i);
                                int ox = x + pad + (i % 2) * colW, oy = ly + (i / 2) * 16;
                                boolean ov = in(mx, my, ox, oy, colW - 4, 15) && my >= viewTop && my < viewBottom;
                                if (ov) rr(g, ox, oy, colW - 4, 15, 0xFF211A3D);
                                rr(g, ox + 2, oy + 2, 11, 11, op.on ? ACCENT2 : 0xFF2E2848);
                                if (op.on) {
                                    f(g, ox + 4, oy + 7, ox + 6, oy + 11, 0xFF0A0A10);
                                    f(g, ox + 6, oy + 9, ox + 11, oy + 11, 0xFF0A0A10);
                                    f(g, ox + 9, oy + 4, ox + 11, oy + 9, 0xFF0A0A10);
                                }
                                t(g, truncate(op.label, colW - 22), ox + 17, oy + 4, op.on ? TXT : DIM);
                                tipClip(ox, oy, colW - 4, 15, viewTop, viewBottom, op.label, "Kimlik: " + op.id + "\nGrup: " + op.group + "\nDurum: " + (op.on ? "SEÇİLİ" : "seçili değil") + "\nTıklayarak seç / kaldır.");
                                hitClip(ox, oy, colW - 4, 15, viewTop, viewBottom, (btn, cx, cy) -> {
                                    if (btn == LEFT || btn == RIGHT) {
                                        op.on = !op.on;
                                        ConfigManager.save();
                                    }
                                });
                            }
                            ly += ((opts.size() + 1) / 2) * 16;
                        }
                    }
                } else if (s instanceof ModeSetting ms) {
                    t(g, ms.name, x + pad, y + 3, TXT);
                    int chx = x + pad, chy = y + 16;
                    for (String o : ms.modes) {
                        int cwid = tw(o) + 12;
                        if (chx > x + pad && chx + cwid > x + w - pad) {
                            chx = x + pad;
                            chy += 15;
                        }
                        boolean act = ms.is(o);
                        boolean hv = in(mx, my, chx, chy, cwid, 13);
                        rr(g, chx, chy, cwid, 13, act ? ACCENT : (hv ? 0xFF30275C : 0xFF171325));
                        t(g, o, chx + 6, chy + 3, act ? 0xFFFFFFFF : DIM);
                        final String opt = o;
                        tipClip(chx, chy, cwid, 13, viewTop, viewBottom, ms.name + ": " + o, Help.option(m, ms, o));
                        hitClip(chx, chy, cwid, 13, viewTop, viewBottom, (btn, cx, cy) -> {
                            if (btn == LEFT) {
                                ms.setFromString(opt);
                                ConfigManager.save();
                            }
                        });
                        chx += cwid + 4;
                    }
                }
            }
            y += rh;
        }

        // modülü sıfırla
        if (y + 22 > viewTop && y < viewBottom) {
            boolean hov = in(mx, my, x + pad, y + 4, cw, 15) && my >= viewTop && my < viewBottom;
            rr(g, x + pad, y + 4, cw, 15, hov ? 0xFF5A44B8 : 0xFF3B2B86);
            String rt = "Bu modülün ayarlarını sıfırla";
            t(g, rt, x + pad + (cw - tw(rt)) / 2, y + 8, TXT);
            tipClip(x + pad, y + 4, cw, 15, viewTop, viewBottom, "Ayarları sıfırla", "Bu modülün bütün ayarlarını varsayılan değerlerine döndürür (modülün açık/kapalı durumu değişmez).");
            hitClip(x + pad, y + 4, cw, 15, viewTop, viewBottom, (btn, cx, cy) -> {
                if (btn == LEFT) {
                    m.resetSettings();
                    ConfigManager.save();
                    setStatus(m.name + " ayarları sıfırlandı");
                }
            });
        }
        g.disableScissor();
        scrollbar(g, x + w - 3, viewTop, viewBottom, content, setScroll);
    }

    // =========================================================== profil sekmesi
    private void renderProfile(GuiGraphicsExtractor g, int mx, int my) {
        // ---- sol: profiller
        f(g, listX, bodyTop, listX + listW, bodyBottom, PANEL);
        t(g, "Profiller", listX + 8, bodyTop + 6, TXT);
        tip(listX + 4, bodyTop + 2, listW - 8, 16, "Profiller", "Her profil ayrı bir ayar dosyasıdır (modüllerin açık/kapalı durumu ve tüm ayarlar). Birine tıklayınca o profil yüklenir.");
        int viewTop = bodyTop + 20, viewBottom = bodyBottom - 62, rowH = 20;
        List<String> profs = ConfigManager.list();
        int content = profs.size() * rowH;
        int maxScroll = Math.max(0, content - (viewBottom - viewTop));
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));
        g.enableScissor(listX, viewTop, listX + listW, viewBottom);
        int y = viewTop - listScroll;
        for (String p : profs) {
            if (y + rowH > viewTop && y < viewBottom) {
                boolean act = p.equalsIgnoreCase(ConfigManager.current);
                boolean hov = in(mx, my, listX, y, listW, rowH) && my >= viewTop && my < viewBottom;
                if (act) rr(g, listX + 3, y + 1, listW - 6, rowH - 2, 0xFF2B2152);
                else if (hov) rr(g, listX + 3, y + 1, listW - 6, rowH - 2, HOVER);
                if (act) f(g, listX + 3, y + 4, listX + 5, y + rowH - 4, ACCENT);
                t(g, truncate(p, listW - 40), listX + 10, y + 6, act ? TXT : 0xFFBDB9D4);
                if (act) t(g, "aktif", listX + listW - 34, y + 6, GREEN);
                tipClip(listX, y, listW, rowH, viewTop, viewBottom, "Profil: " + p, "Tıklayınca bu profilin ayarları yüklenir." + (act ? " (Şu an aktif profil.)" : ""));
                hitClip(listX, y, listW, rowH, viewTop, viewBottom, (btn, cx, cy) -> {
                    if (btn == LEFT && ConfigManager.load(p)) setStatus("Profil yüklendi: " + p);
                });
            }
            y += rowH;
        }
        g.disableScissor();
        scrollbar(g, listX + listW - 3, viewTop, viewBottom, content, listScroll);

        int by = bodyBottom - 58;
        f(g, listX + 6, by - 3, listX + listW - 6, by - 2, 0xFF2A2250);
        tip(listX + 8, by, listW - 16, 14, "Yeni profil adı", "Tıkla ve bir ad yaz (harf, rakam ve tire). Enter veya Oluştur düğmesi mevcut ayarlarını bu adla yeni profil olarak kaydeder.");
        field(g, listX + 8, by, listW - 16, profileBuf, Focus.PROFILE, "yeni profil adı");
        int bw = (listW - 22) / 2;
        tip(listX + 8, by + 20, bw, 15, "Oluştur", "Yukarıya yazdığın adla, şu anki ayarlarından yeni bir profil oluşturur ve onu aktif yapar.");
        tip(listX + 14 + bw, by + 20, bw, 15, "Kaydet", "Şu anki ayarları aktif profile kaydeder. (Ayarlar zaten değiştikçe otomatik kaydedilir.)");
        button(g, listX + 8, by + 20, bw, 15, "Oluştur", mx, my, 0xFF6B4FE0, (btn, cx, cy) -> { if (btn == LEFT) createProfile(); });
        button(g, listX + 14 + bw, by + 20, bw, 15, "Kaydet", mx, my, 0xFF3B2B86, (btn, cx, cy) -> {
            if (btn == LEFT) {
                ConfigManager.save();
                setStatus("Kaydedildi: " + ConfigManager.current);
            }
        });

        // ---- sağ: dostlar
        f(g, setX, bodyTop, setX + setW, bodyBottom, 0xFF0C0C14);
        int pad = 10;
        t(g, "Dostlar", setX + pad, bodyTop + 6, TXT);
        tip(setX + pad, bodyTop + 2, setW - pad * 2, 26, "Dostlar", "Dost listesindeki oyunculara OtoSaldırı, OtoTetik, NişanYardımı ve diğer saldırı modülleri dokunmaz. VarlıkGöster onları yeşil gösterir.");
        t(g, "Saldırı modülleri dostlara dokunmaz", setX + pad, bodyTop + 17, DIM);
        int fTop = bodyTop + 32, fBottom = bodyBottom - 62;
        List<String> friends = new ArrayList<>(FriendManager.NAMES);
        int fContent = friends.size() * 18;
        int fMax = Math.max(0, fContent - (fBottom - fTop));
        setScroll = Math.max(0, Math.min(setScroll, fMax));
        g.enableScissor(setX, fTop, setX + setW, fBottom);
        int fy = fTop - setScroll;
        for (String n : friends) {
            if (fy + 18 > fTop && fy < fBottom) {
                boolean hov = in(mx, my, setX + 4, fy, setW - 8, 18) && my >= fTop && my < fBottom;
                if (hov) rr(g, setX + 4, fy, setW - 8, 18, HOVER);
                t(g, n, setX + pad, fy + 5, 0xFFFFE680);
                boolean xh = in(mx, my, setX + setW - 22, fy, 16, 18);
                t(g, "x", setX + setW - 18, fy + 5, xh ? RED : DIM);
                tipClip(setX + 4, fy, setW - 32, 18, fTop, fBottom, n, "Dost oyuncu. Sağdaki x ile listeden çıkarabilirsin.");
                tipClip(setX + setW - 24, fy, 20, 18, fTop, fBottom, "Dostu sil", n + " adlı oyuncuyu dost listesinden çıkarır.");
                hitClip(setX + setW - 24, fy, 20, 18, fTop, fBottom, (btn, cx, cy) -> {
                    if (btn == LEFT) {
                        FriendManager.NAMES.remove(n);
                        ConfigManager.save();
                    }
                });
            }
            fy += 18;
        }
        g.disableScissor();
        scrollbar(g, setX + setW - 3, fTop, fBottom, fContent, setScroll);

        int ay = bodyBottom - 58;
        f(g, setX + 6, ay - 3, setX + setW - 6, ay - 2, 0xFF2A2250);
        tip(setX + pad, ay, setW - pad * 2 - 58, 14, "Oyuncu adı", "Tıkla ve dost eklemek istediğin oyuncunun adını yaz (alt çizgi için - tuşunu kullan). Enter veya Ekle düğmesi listeye ekler.");
        tip(setX + setW - pad - 52, ay, 52, 14, "Ekle", "Yazdığın oyuncuyu dost listesine ekler.");
        field(g, setX + pad, ay, setW - pad * 2 - 58, friendBuf, Focus.FRIEND, "oyuncu adı");
        button(g, setX + setW - pad - 52, ay, 52, 14, "Ekle", mx, my, 0xFF6B4FE0, (btn, cx, cy) -> { if (btn == LEFT) addFriend(); });

        tip(setX + pad, ay + 22, setW - pad * 2, 15, "Tüm ayarları sıfırla", "Bütün modülleri kapatır ve bütün ayarlarını varsayılana döndürür. Yanlışlıkla basmamak için 3 saniye içinde iki kez tıklaman gerekir.");
        boolean confirming = System.currentTimeMillis() < confirmUntil;
        button(g, setX + pad, ay + 22, setW - pad * 2, 15, confirming ? "Emin misin? Tekrar tıkla" : "Tüm ayarları sıfırla",
                mx, my, confirming ? 0xFFB02020 : 0xFF5A2530, (btn, cx, cy) -> {
                    if (btn != LEFT) return;
                    if (System.currentTimeMillis() < confirmUntil) {
                        confirmUntil = 0;
                        ConfigManager.resetAll();
                        setStatus("Tüm ayarlar sıfırlandı");
                    } else {
                        confirmUntil = System.currentTimeMillis() + 3000;
                    }
                });
    }

    private void createProfile() {
        String n = profileBuf.toString().trim();
        if (n.isEmpty()) {
            setStatus("Önce bir profil adı yaz");
            return;
        }
        ConfigManager.save(n);
        profileBuf.setLength(0);
        focus = Focus.NONE;
        setStatus("Profil oluşturuldu: " + n);
    }

    private void addFriend() {
        String n = friendBuf.toString().trim().toLowerCase(java.util.Locale.ROOT);
        if (n.isEmpty()) {
            setStatus("Önce bir oyuncu adı yaz");
            return;
        }
        FriendManager.NAMES.add(n);
        ConfigManager.save();
        friendBuf.setLength(0);
        focus = Focus.NONE;
        setStatus("Dost eklendi: " + n);
    }

    // =========================================================== alt çubuk ve sağ tık menüsü
    private void renderFooter(GuiGraphicsExtractor g) {
        int fy = wy + wh - FOOT_H;
        f(g, wx, fy, wx + ww, wy + wh, 0xFF06060B);
        boolean st = System.currentTimeMillis() < statusUntil;
        String left = tooltip != null ? tooltip : "Sol tık: seç   Anahtara tıkla: aç/kapat   Sağ tık: menü";
        t(g, truncate(left, st ? ww / 2 : ww - 16), wx + 8, fy + 3, DIM);
        if (st) {
            int sw = tw(status);
            t(g, status, wx + ww - sw - 8, fy + 3, ACCENT2);
        }
    }

    private void renderMenu(GuiGraphicsExtractor g, int mx, int my) {
        Module m = ctxModule;
        String[] labels = {m.enabled ? "Kapat" : "Aç", "Ayarları göster", "Ayarlarını sıfırla", "Tuşu ayarla..."};
        int rh = 17, mw = 140, head = 18;
        int mh = head + labels.length * rh + 4;
        int x = Math.min(ctxX, this.width - mw - 4), y = Math.min(ctxY, this.height - mh - 4);

        f(g, x - 2, y - 2, x + mw + 2, y + mh + 2, 0x77000000);
        rr(g, x, y, mw, mh, 0xFF0E0C18);
        gradBar(g, x + 2, y, x + mw - 2, y + 2);
        t(g, m.name, x + 8, y + 6, ACCENT2);
        String kt = m.keyText();
        if (!kt.isEmpty()) t(g, kt, x + mw - tw(kt) - 8, y + 6, DIM);

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            int yy = y + head + i * rh;
            boolean hov = in(mx, my, x + 2, yy, mw - 4, rh);
            if (hov) rr(g, x + 2, yy, mw - 4, rh, 0xFF2A2060);
            int col = i == 0 ? (m.enabled ? RED : GREEN) : TXT;
            t(g, labels[i], x + 10, yy + 5, col);
            menuHits.add(new Hit(x, yy, mw, rh, Integer.MIN_VALUE, Integer.MAX_VALUE, (btn, cx, cy) -> {
                if (btn != LEFT && btn != RIGHT) return;
                ctxModule = null;
                switch (idx) {
                    case 0 -> m.toggle();
                    case 1 -> {
                        selected = m;
                        setScroll = 0;
                    }
                    case 2 -> {
                        m.resetSettings();
                        ConfigManager.save();
                        setStatus(m.name + " ayarları sıfırlandı");
                    }
                    case 3 -> Helper.openScreen(new KeyBindsScreen(this, Minecraft.getInstance().options));
                    default -> { }
                }
            }));
        }
    }

    // =========================================================== girdi
    private static void slide(NumSetting ns, int x, int w, double mx) {
        double r = Math.max(0, Math.min(1, (mx - x) / w));
        ns.set(ns.min + r * (ns.max - ns.min));
    }

    private void commitEdit() {
        if (editing != null) {
            if (numBuf.length() > 0) editing.setFromString(numBuf.toString());
            editing = null;
            numBuf.setLength(0);
            ConfigManager.save();
        }
        if (focus == Focus.NUM) focus = Focus.NONE;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean doubleClick) {
        double mx = ev.x(), my = ev.y();
        int btn = ev.button();

        if (focus == Focus.NUM) commitEdit();
        focus = Focus.NONE;

        if (ctxModule != null) {
            for (int i = menuHits.size() - 1; i >= 0; i--) {
                Hit h = menuHits.get(i);
                if (h.in(mx, my)) {
                    h.action().run(btn, mx, my);
                    return true;
                }
            }
            ctxModule = null;
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.in(mx, my)) {
                h.action().run(btn, mx, my);
                return true;
            }
        }
        if (in(mx, my, wx, wy, ww, wh)) return true;
        return super.mouseClicked(ev, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent ev, double dx, double dy) {
        if (dragSlider != null) {
            slide(dragSlider, dragSX, dragSW, ev.x());
            return true;
        }
        return super.mouseDragged(ev, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent ev) {
        if (dragSlider != null) {
            dragSlider = null;
            ConfigManager.save();
        }
        return super.mouseReleased(ev);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        int d = (int) (sy * 22);
        if (in(x, y, listX, bodyTop, listW, bodyBottom - bodyTop)) listScroll -= d;
        else if (in(x, y, setX, bodyTop, setW, bodyBottom - bodyTop)) setScroll -= d;
        return true;
    }

    // SDL scancode -> karakter
    private static char numChar(int k) {
        if (k >= 30 && k <= 38) return (char) ('1' + k - 30);
        if (k == 39) return '0';
        if (k >= 89 && k <= 97) return (char) ('1' + k - 89);
        if (k == 98) return '0';
        if (k == 54 || k == 55 || k == 56 || k == 99) return '.';
        if (k == 45 || k == 86) return '-';
        return 0;
    }

    private static char textChar(int k, Focus f) {
        if (k >= 4 && k <= 29) return (char) ('a' + k - 4);
        if (k >= 30 && k <= 38) return (char) ('1' + k - 30);
        if (k == 39) return '0';
        if (k == 44) return f == Focus.SEARCH ? ' ' : 0;
        if (k == 45) return f == Focus.FRIEND ? '_' : '-';
        return 0;
    }

    private StringBuilder bufOf(Focus f) {
        return switch (f) {
            case SEARCH -> searchBuf;
            case PROFILE -> profileBuf;
            case FRIEND -> friendBuf;
            default -> numBuf;
        };
    }

    @Override
    public boolean keyPressed(KeyEvent ev) {
        int k = ev.key();
        final int ESC = 41, ENTER = 40, KP_ENTER = 88, BACKSPACE = 42;

        if (focus == Focus.NUM && editing != null) {
            if (k == ESC) {
                editing = null;
                numBuf.setLength(0);
                focus = Focus.NONE;
            } else if (k == ENTER || k == KP_ENTER) {
                commitEdit();
            } else if (k == BACKSPACE) {
                if (numBuf.length() > 0) numBuf.setLength(numBuf.length() - 1);
            } else {
                char ch = numChar(k);
                if (ch != 0 && numBuf.length() < 9) numBuf.append(ch);
            }
            return true;
        }

        if (focus == Focus.SEARCH || focus == Focus.PROFILE || focus == Focus.FRIEND) {
            StringBuilder buf = bufOf(focus);
            if (k == ESC) {
                if (focus == Focus.SEARCH) buf.setLength(0);
                focus = Focus.NONE;
            } else if (k == ENTER || k == KP_ENTER) {
                if (focus == Focus.PROFILE) createProfile();
                else if (focus == Focus.FRIEND) addFriend();
                else focus = Focus.NONE;
            } else if (k == BACKSPACE) {
                if (buf.length() > 0) buf.setLength(buf.length() - 1);
                if (focus == Focus.SEARCH) listScroll = 0;
            } else {
                char ch = textChar(k, focus);
                if (ch != 0 && buf.length() < 24) {
                    buf.append(ch);
                    if (focus == Focus.SEARCH) listScroll = 0;
                }
            }
            return true; // yazarken menü kapanmasın
        }

        if (NexusClient.OPEN_KEY != null && NexusClient.OPEN_KEY.matches(ev)) {
            this.onClose();
            return true;
        }

        // Odak yokken harfe basınca otomatik aramaya yaz
        char ch = textChar(k, Focus.SEARCH);
        if (ch != 0 && ch >= 'a' && ch <= 'z') {
            if (catIndex >= CATS) catIndex = 0;
            focus = Focus.SEARCH;
            searchBuf.append(ch);
            listScroll = 0;
            return true;
        }
        return super.keyPressed(ev);
    }

    @Override
    public void onClose() {
        commitEdit();
        ConfigManager.save();
        super.onClose();
    }
}
