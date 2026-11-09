package dev.kraken.hud.elements;

import dev.kraken.gui.KrakenLogo;
import dev.kraken.gui.Shapes;
import dev.kraken.hud.HudElement;
import dev.kraken.hud.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

public class WatermarkElement extends HudElement {
    private static final float H = 20f, LOGO = 15f;

    public WatermarkElement() {
        super("watermark", "Logo", true, START, START, 5, 5);
    }

    @Override public float width(boolean editor) { return 7f + LOGO + 5f + HudStyle.width("Kraken", true, 1f) + 8f; }
    @Override public float height(boolean editor) { return H; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        HudStyle.pill(s, x, y, x + width(editor), y + H, 1f);
        KrakenLogo.draw(s, x + 7f + LOGO / 2f, y + H / 2f, LOGO, (System.currentTimeMillis() % 600000L) / 1000f,
                HudStyle.ACCENT, 0xFFFFFFFF, 0xFF2A0E08);
        text.add(() -> HudStyle.text(ctx, "Kraken", x + 7f + LOGO + 5f, y + H / 2f, 1f, HudStyle.TEXT, true));
    }
}
