package dev.nautilus.hud.elements;

import dev.nautilus.gui.NautilusLogo;
import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

public class WatermarkElement extends HudElement {
    private static final float H = 20f, LOGO = 15f;

    public WatermarkElement() {
        super("watermark", "Logo", true, START, START, 5, 5);
    }

    @Override public float width(boolean editor) { return 7f + LOGO + 5f + HudStyle.width("Nautilus", true, 1f) + 8f; }
    @Override public float height(boolean editor) { return H; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        HudStyle.pill(ctx, s, x, y, x + width(editor), y + H, 1f);
        NautilusLogo.draw(s, x + 7f + LOGO / 2f, y + H / 2f, LOGO, (System.currentTimeMillis() % 600000L) / 1000f,
                HudStyle.accent(), 0xFFFFFFFF, dev.nautilus.hud.HudStyle.shade(0xFF, 0.84f));
        text.add(() -> HudStyle.text(ctx, "Nautilus", x + 7f + LOGO + 5f, y + H / 2f, 1f, HudStyle.TEXT, true));
    }
}
