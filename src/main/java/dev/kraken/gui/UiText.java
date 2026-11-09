package dev.kraken.gui;

import dev.kraken.Kraken;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** The clean Nunito font of Kraken, shared by the wheel and the HUD. */
public final class UiText {
    public static final FontDescription CLEAN = new FontDescription.Resource(Identifier.fromNamespaceAndPath(Kraken.MOD_ID, "clean"));
    public static final FontDescription CLEAN_BOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath(Kraken.MOD_ID, "clean_bold"));

    private UiText() {}

    public static Component styled(String text, boolean bold) {
        return Component.literal(text).withStyle(s -> s.withFont(bold ? CLEAN_BOLD : CLEAN));
    }

    public static int width(Font font, String text, boolean bold) {
        return font.width(styled(text, bold));
    }

    /** Draws text with its left edge at x and vertically centred on y. */
    public static void left(GuiGraphicsExtractor ctx, Font font, String text, float x, float y, float scale, int color, boolean bold) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scale, scale);
        ctx.text(font, styled(text, bold), 0, -4, color, false);
        ctx.pose().popMatrix();
    }
}
