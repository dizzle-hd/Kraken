package dev.nautilus.hud;

import dev.nautilus.gui.LiquidGlass;
import dev.nautilus.gui.Shapes;
import dev.nautilus.gui.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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

    // ---- colour theme (Client → HUD → "HUD-Farbe") ---------------------------------------------

    /** Names of the colour choices, in the order of the setting; the last one cycles through all hues. */
    public static final String[] COLOR_NAMES = {"Orange", "Rot", "Pink", "Lila", "Blau", "Türkis", "Grün", "Gold", "Weiß", "Regenbogen"};
    private static final int[] COLORS = {0xFFFF5A2A, 0xFFFF3B3B, 0xFFFF4FA3, 0xFFA66BFF, 0xFF3D8BFF, 0xFF22D3C5, 0xFF3DDC6A, 0xFFFFC23D, 0xFFF2F2F2};
    public static final String RAINBOW = "Regenbogen";

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

    /** Accent with a hue offset – only differs for "Regenbogen" (e.g. one step per row of the module list). */
    public static int accent(float rainbowOffset) {
        String c = current();
        return RAINBOW.equals(c) ? rainbow(rainbowOffset) : colorOf(c);
    }

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
    private static int tint(int alpha) { return (alpha << 24) | (Shapes.lerp(accent(), 0xFF000000, 0.86f) & 0xFFFFFF); }

    public static void pill(GuiGraphicsExtractor ctx, Shapes s, float x1, float y1, float x2, float y2, float alpha) {
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
}
