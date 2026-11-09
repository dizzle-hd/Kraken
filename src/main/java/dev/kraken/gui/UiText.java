package dev.kraken.gui;

import dev.kraken.Kraken;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** The clean Nunito font of Kraken, shared by the wheel and the HUD. */
public final class UiText {
    /**
     * Fine vertical correction (font units) so the cap height of Nunito is centred on the given y.
     * Measured in screenshots: without it the capitals sat ~0.55 units too low.
     */
    public static final float BASELINE_FIX = -0.55f;
    public static final FontDescription CLEAN = new FontDescription.Resource(Identifier.fromNamespaceAndPath(Kraken.MOD_ID, "clean"));
    public static final FontDescription CLEAN_BOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath(Kraken.MOD_ID, "clean_bold"));

    private UiText() {}

    public static Component styled(String text, boolean bold) {
        return Component.literal(text).withStyle(s -> s.withFont(bold ? CLEAN_BOLD : CLEAN));
    }

    public static int width(Font font, String text, boolean bold) {
        return font.width(styled(text, bold));
    }

    /** Draws text horizontally centred on x and vertically centred on y. */
    public static void centered(GuiGraphicsExtractor ctx, Font font, String text, float x, float y, float scale, int color, boolean bold, boolean shadow) {
        left(ctx, font, text, x - width(font, text, bold) * scale / 2f, y, scale, color, bold, shadow);
    }

    /** Draws text with its left edge at x and vertically centred on y. */
    public static void left(GuiGraphicsExtractor ctx, Font font, String text, float x, float y, float scale, int color, boolean bold) {
        left(ctx, font, text, x, y, scale, color, bold, false);
    }

    public static void left(GuiGraphicsExtractor ctx, Font font, String text, float x, float y, float scale, int color, boolean bold, boolean shadow) {
        Component c = styled(text, bold);
        if (shadow) {
            // soft shadow: a few faint, slightly offset copies instead of the hard vanilla shadow
            int a = (color >>> 24) & 0xFF;
            int sc = ((int) (a * 0.22f) << 24);
            float[][] offs = {{0.35f, 0.5f}, {0.6f, 0.75f}, {0.1f, 0.35f}};
            for (float[] o : offs) {
                ctx.pose().pushMatrix();
                ctx.pose().translate(x + o[0], y + o[1]);
                ctx.pose().scale(scale, scale);
                ctx.pose().translate(0, BASELINE_FIX);
                ctx.text(font, c, 0, -4, sc, false);
                ctx.pose().popMatrix();
            }
        }
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scale, scale);
        ctx.pose().translate(0, BASELINE_FIX);
        ctx.text(font, c, 0, -4, color, false);
        ctx.pose().popMatrix();
    }
}
