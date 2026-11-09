package dev.kraken.hud;

import dev.kraken.gui.LiquidGlass;
import dev.kraken.gui.Shapes;
import dev.kraken.gui.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared look of all HUD elements: transparent rounded glass pills like the wheel segments. */
public final class HudStyle {
    public static final int TINT = 0x2E2A0E06;
    public static final int GLASS_TOP = 0x2EFFE6DC;
    public static final int GLASS_BOTTOM = 0x14FFE6DC;
    public static final int RIM = 0x4DFFFFFF;
    public static final int SHINE = 0x1EFFFFFF;
    public static final int ACCENT = 0xFFFF5A2A;
    public static final int TEXT = 0xFFF6ECE8;
    public static final int TEXT_DIM = 0xD8F0D8D0;
    public static final int LABEL = 0xFFFFB49C;
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

    public static void pill(GuiGraphicsExtractor ctx, Shapes s, float x1, float y1, float x2, float y2, float alpha) {
        float r = Math.min((y2 - y1) / 2f, 7f);
        if (LiquidGlass.enabled()) {
            // real liquid glass (blur + refraction), a faint warm tint keeps text readable
            LiquidGlass.roundRect(ctx, x1, y1, x2, y2, r, alpha);
            s.roundRect(x1, y1, x2, y2, r, Shapes.alpha(0x1E2A0E06, alpha));
            return;
        }
        s.roundRect(x1, y1, x2, y2, r, Shapes.alpha(TINT, alpha));
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
