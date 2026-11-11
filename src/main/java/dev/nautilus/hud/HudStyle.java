package dev.nautilus.hud;

import dev.nautilus.gui.LiquidGlass;
import dev.nautilus.gui.Shapes;
import dev.nautilus.gui.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** Shared look of all HUD elements: transparent rounded glass pills like the wheel segments. */
public final class HudStyle {
    public static final int GLASS_TOP = 0x2EFFE6DC;
    public static final int GLASS_BOTTOM = 0x14FFE6DC;
    public static final int RIM = 0x4DFFFFFF;
    public static final int SHINE = 0x1EFFFFFF;
    public static final int TEXT = 0xFFF6ECE8;
    public static final int TEXT_DIM = 0xD8F0D8D0;
    public static final int GOOD = 0xFF5BE38A;
    public static final int BAD = 0xFFFF5A5A;

    /** Height of a standard one-line pill and the text scale used inside it. */
    public static final float PILL_H = 14f;
    public static final float TEXT_SCALE = 0.9f;
    public static final float PAD = 7f;

    /**
     * Vertical correction so that the Nunito glyphs look optically centred: measured from screenshots
     * (cap height centre vs. the line box used by {@link UiText}).
     */
    public static float textCenterFix = 0.0f;

    private HudStyle() {}

    // ---- colour theme (Client → HUD → "HUD color") ---------------------------------------------

    /** Names of the colour choices, in the order of the setting; the last one cycles through all hues. */
    public static final String[] COLOR_NAMES = {"Lavender", "Orange", "Red", "Pink", "Purple", "Blue", "Turquoise", "Green", "Gold", "White", "Rainbow"};
    private static final int[] COLORS = {0xFFC4A6FF, 0xFFFF5A2A, 0xFFFF3B3B, 0xFFFF4FA3, 0xFFA66BFF, 0xFF3D8BFF, 0xFF22D3C5, 0xFF3DDC6A, 0xFFFFC23D, 0xFFF2F2F2};
    public static final String RAINBOW = "Rainbow";

    /** Colour of a choice (for swatches); the rainbow swatch shows the current hue. */
    public static int colorOf(String name) {
        if (RAINBOW.equals(name)) return rainbow(0f);
        for (int i = 0; i < COLORS.length; i++) if (COLOR_NAMES[i].equals(name)) return COLORS[i];
        return COLORS[0];
    }

    private static String current() {
        var hud = dev.nautilus.module.ModuleManager.get().get(dev.nautilus.module.client.Hud.class);
        return hud == null ? COLOR_NAMES[0] : hud.color.get();
    }

    private static int rainbow(float offset) {
        float hue = ((System.currentTimeMillis() % 6000L) / 6000f + offset) % 1f;
        return 0xFF000000 | (net.minecraft.util.Mth.hsvToRgb(hue, 0.65f, 1f) & 0xFFFFFF); // no AWT in the game
    }

    /** The accent colour of the HUD (dots, logo, sliders, active buttons). */
    public static int accent() { return accent(0f); }

    /** Accent with a hue offset – only differs for "Rainbow" (e.g. one step per row of the module list). */
    public static int accent(float rainbowOffset) {
        String c = current();
        return RAINBOW.equals(c) ? rainbow(rainbowOffset) : colorOf(c);
    }

    /** {@code color} with its hue turned by {@code shift} (0-1, full circle) – e.g. a matching second colour for gradients. */
    public static int hueShift(int color, float shift) {
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        if (delta < 1e-4f) return color; // grey / white: no hue to turn
        float h = max == r ? ((g - b) / delta) % 6f : max == g ? (b - r) / delta + 2f : (r - g) / delta + 4f;
        h = ((h / 6f + shift) % 1f + 1f) % 1f;
        return (color & 0xFF000000) | (net.minecraft.util.Mth.hsvToRgb(h, delta / max, max) & 0xFFFFFF);
    }

    /** Second theme colour: the accent turned a bit around the colour wheel. */
    public static int accent2() { return hueShift(accent(), 0.12f); }

    /** Accent with the given alpha (0-255). */
    public static int accent(int alpha) { return (alpha << 24) | (accent() & 0xFFFFFF); }

    /** Accent for a hovered button: a bit lighter. */
    public static int accentHot(int alpha) { return (alpha << 24) | (Shapes.lerp(accent(), 0xFFFFFFFF, 0.14f) & 0xFFFFFF); }

    /** Text colour on an accent-filled button: dark on bright accents (white, gold), otherwise light. */
    public static int onAccent() {
        int c = accent();
        float lum = (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
        return lum > 0.68f ? 0xFF2A0E08 : TEXT;
    }

    /** Labels inside the pills ("FPS", "Ping", …): the accent mixed with white. */
    public static int label() { return Shapes.lerp(accent(), 0xFFFFFFFF, 0.55f); }

    /** Dark tint of the glass, in the hue of the accent (keeps the text readable). */
    private static int tint(int alpha) { return shade(alpha, 0.86f); }

    // ---- derived theme colours: everything that used to be hard-wired orange/brown -------------

    /** The accent darkened towards black by {@code dark} (0 = accent, 1 = black), with the given alpha (0-255). */
    public static int shade(int alpha, float dark) { return (alpha << 24) | (Shapes.lerp(accent(), 0xFF000000, dark) & 0xFFFFFF); }

    /** White tinted towards the accent by {@code amount} (0 = white, 1 = accent), with the given alpha (0-255). */
    public static int tintedWhite(int alpha, float amount) { return (alpha << 24) | (Shapes.lerp(0xFFFFFFFF, accent(), amount) & 0xFFFFFF); }

    /** Dark, semi-transparent panel background (settings panel, editor, onboarding) in the accent's hue. */
    public static int panel(int alpha) { return shade(alpha, 0.9f); }

    /** Values next to settings ("12.5", "Head", …): a light pastel of the accent. */
    public static int value() { return tintedWhite(0xFF, 0.37f); }

    /** Is the HUD drawn as dark panels (default) instead of round glass pills? (Client → HUD → Style) */
    public static boolean panels() {
        var hud = dev.nautilus.module.ModuleManager.get().get(dev.nautilus.module.client.Hud.class);
        return hud == null || hud.style.is("Panel");
    }

    /** Background of an HUD element: a dark panel or a glass pill, depending on the HUD style. */
    public static void pill(GuiGraphicsExtractor ctx, Shapes s, float x1, float y1, float x2, float y2, float alpha) {
        if (panels()) { panel(ctx, s, x1, y1, x2, y2, alpha); return; }
        glassPill(ctx, s, x1, y1, x2, y2, alpha);
    }

    /** Panel base colour: near-black with a hint of the accent. */
    public static int panelColor(int alpha) { return (alpha << 24) | (Shapes.lerp(0xFF141218, accent(), 0.07f) & 0xFFFFFF); }

    /**
     * Dark, slightly see-through panel with small rounded corners, a hairline rim and a faint top
     * highlight. With Liquid Glass on, the world behind it is blurred as well (frosted dark glass).
     */
    public static void panel(GuiGraphicsExtractor ctx, Shapes s, float x1, float y1, float x2, float y2, float alpha) {
        float r = Math.min((y2 - y1) / 2f, 4.5f);
        if (LiquidGlass.enabled()) {
            LiquidGlass.roundRect(ctx, x1, y1, x2, y2, r, alpha);
            s.roundRect(x1, y1, x2, y2, r, Shapes.alpha(panelColor(0xB8), alpha));
        } else {
            s.roundRect(x1, y1, x2, y2, r, Shapes.alpha(panelColor(0xD4), alpha));
        }
        s.roundRectGradient(x1 + 0.5f, y1 + 0.5f, x2 - 0.5f, y1 + Math.min(10f, (y2 - y1) * 0.5f), r, Shapes.alpha(0x0EFFFFFF, alpha), 0x00FFFFFF);
        s.roundRectRim(x1, y1, x2, y2, r, 0.6f, Shapes.alpha(0x26FFFFFF, alpha));
    }

    /** Small rounded square with a vector icon in the accent colour (the "icon tile" of panels). */
    public static void iconBox(Shapes s, Icons icon, float x, float y, float size, float alpha) {
        s.roundRect(x, y, x + size, y + size, Math.min(3.5f, size / 4f), Shapes.alpha(Shapes.lerp(0x18FFFFFF, accent(0x30), 0.5f), alpha));
        icon.draw(s, x + size / 2f, y + size / 2f, size * 0.6f, Shapes.alpha(Shapes.lerp(accent(), 0xFFFFFFFF, 0.25f), alpha));
    }

    /** Thin progress line (track + fill) – the bars under values in panels. */
    public static void line(Shapes s, float x1, float y, float x2, float frac, int color, float alpha) {
        float h = 1.3f;
        s.roundRect(x1, y, x2, y + h, h / 2f, Shapes.alpha(0x26FFFFFF, alpha));
        frac = Math.max(0f, Math.min(1f, frac));
        if (frac > 0.003f) s.roundRect(x1, y, x1 + (x2 - x1) * frac, y + h, h / 2f, Shapes.alpha(color, alpha));
    }

    /** Small rounded "chip" with a value (keys, timers) – right-aligned at x2, vertically centred on cy. */
    public static float chip(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, String value, float x2, float cy, float scale, int color) {
        float w = width(value, false, scale) + 7f, h = 9f * scale + 3f;
        s.roundRect(x2 - w, cy - h / 2f, x2, cy + h / 2f, 2.5f, 0x1CFFFFFF);
        text.add(() -> text(ctx, value, x2 - w + 3.5f, cy, scale, color, false));
        return w;
    }

    /** Glass pill (the original look): blur + refraction or a frosted gradient with a light rim. */
    public static void glassPill(GuiGraphicsExtractor ctx, Shapes s, float x1, float y1, float x2, float y2, float alpha) {
        float r = Math.min((y2 - y1) / 2f, 7f);
        if (LiquidGlass.enabled()) {
            // real liquid glass (blur + refraction), a faint warm tint keeps text readable
            LiquidGlass.roundRect(ctx, x1, y1, x2, y2, r, alpha);
            s.roundRect(x1, y1, x2, y2, r, Shapes.alpha(tint(0x1E), alpha));
            return;
        }
        s.roundRect(x1, y1, x2, y2, r, Shapes.alpha(tint(0x2E), alpha));
        s.roundRectGradient(x1, y1, x2, y2, r, Shapes.alpha(GLASS_TOP, alpha), Shapes.alpha(GLASS_BOTTOM, alpha));
        s.roundRectGradient(x1 + 1f, y1 + 1f, x2 - 1f, y1 + (y2 - y1) * 0.5f, r * 0.8f, Shapes.alpha(SHINE, alpha), 0x00FFFFFF);
        s.roundRectRim(x1, y1, x2, y2, r, 0.75f, Shapes.alpha(RIM, alpha));
    }

    public static float width(String text, boolean bold, float scale) {
        return UiText.width(Minecraft.getInstance().font, text, bold) * scale;
    }

    /** Text with its left edge at x, optically centred on centerY. */
    public static void text(GuiGraphicsExtractor ctx, String text, float x, float centerY, float scale, int color, boolean bold) {
        UiText.left(ctx, Minecraft.getInstance().font, text, x, centerY + textCenterFix * scale, scale, color, bold, true);
    }

    /** Text horizontally centred on centerX, optically centred on centerY. */
    public static void textCentered(GuiGraphicsExtractor ctx, String text, float centerX, float centerY, float scale, int color, boolean bold) {
        text(ctx, text, centerX - width(text, bold, scale) / 2f, centerY, scale, color, bold);
    }

    /** Flat item icon with its top-left corner at (x, y), {@code size} GUI pixels wide (vanilla = 16). */
    public static void item(GuiGraphicsExtractor ctx, net.minecraft.world.item.ItemStack stack, float x, float y, float size) {
        if (stack.isEmpty()) return;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(size / 16f, size / 16f);
        ctx.item(stack, 0, 0);
        ctx.pose().popMatrix();
    }

    /** Colour for a "how much is left" fraction (0-1): green, yellow, red. */
    public static int wear(float fraction) {
        return fraction > 0.5f ? GOOD : fraction > 0.2f ? 0xFFFFD27A : BAD;
    }
}
