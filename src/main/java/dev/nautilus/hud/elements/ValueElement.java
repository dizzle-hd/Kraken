package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** A one-line element "LABEL value" (FPS, Ping, TPS, Speed, …). */
public class ValueElement extends HudElement {
    private final String label;
    private final Supplier<String> value;
    private final IntSupplier color;

    public ValueElement(String id, String name, String label, boolean enabled, int ax, int ay, float ox, float oy,
                        Supplier<String> value, IntSupplier color) {
        super(id, name, enabled, ax, ay, ox, oy);
        this.label = label;
        this.value = value;
        this.color = color;
    }

    @Override public float width(boolean editor) { return labelValueWidth(label, value.get()); }
    @Override public float height(boolean editor) { return HudStyle.PILL_H; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        labelValue(s, text, ctx, x, y, label, value.get(), color.getAsInt());
    }
}
