package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.hud.Icons;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A one-value element (FPS, ping, TPS, speed, …). Panel style: an icon tile, the value large, the unit
 * small next to it and an optional thin bar below. Glass style: the classic pill "LABEL value".
 */
public class ValueElement extends HudElement {
    static final float TILE_H = 20f, BOX = 14f;

    private final String label;
    private final Supplier<String> value;
    private final IntSupplier color;
    private Icons icon = Icons.DOT;
    private String unit = "";
    private DoubleSupplier bar;
    private float shownBar = -1f; // eases towards the real value
    private String shownValue;
    private long valueAt;

    /** The value refreshes four times a second – numbers like the FPS don't flicker every frame. */
    private String value() {
        long now = System.currentTimeMillis();
        if (shownValue == null || now - valueAt > 250) { shownValue = value.get(); valueAt = now; }
        return shownValue;
    }

    public ValueElement(String id, String name, String label, boolean enabled, int ax, int ay, float ox, float oy,
                        Supplier<String> value, IntSupplier color) {
        super(id, name, enabled, ax, ay, ox, oy);
        this.label = label;
        this.value = value;
        this.color = color;
    }

    /** Panel style: icon, unit after the value and the fill (0-1) of the bar below (null = no bar). */
    public ValueElement tile(Icons icon, String unit, DoubleSupplier bar) {
        this.icon = icon;
        this.unit = unit;
        this.bar = bar;
        return this;
    }

    private String fullValue() { return unit.isEmpty() ? value() : value() + " " + unit; }

    private float tileWidth() {
        float w = 3f + BOX + 6f + HudStyle.width(value(), true, 0.95f);
        if (!unit.isEmpty()) w += 3f + HudStyle.width(unit, false, 0.65f);
        return Math.max(64f, w + 8f);
    }

    @Override public float width(boolean editor) { return HudStyle.panels() ? tileWidth() : labelValueWidth(label, fullValue()); }
    @Override public float height(boolean editor) { return HudStyle.panels() ? TILE_H : HudStyle.PILL_H; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        if (!HudStyle.panels()) {
            labelValue(s, text, ctx, x, y, label, fullValue(), color.getAsInt());
            return;
        }
        float w = tileWidth();
        HudStyle.pill(ctx, s, x, y, x + w, y + TILE_H, 1f);
        HudStyle.iconBox(s, icon, x + 3f, y + 3f, BOX, 1f);
        float tx = x + 3f + BOX + 6f, right = x + w - 6f;
        float cy = y + (bar == null ? TILE_H / 2f : 8.5f);
        if (bar != null) {
            float target = (float) Math.max(0.0, Math.min(1.0, bar.getAsDouble()));
            shownBar = shownBar < 0 ? target : dev.nautilus.hud.Anim.approach(shownBar, target, dt, 5f);
            HudStyle.line(s, tx, y + TILE_H - 4.5f, right, shownBar, HudStyle.accent(), 1f);
        }
        String v = value();
        int c = color.getAsInt();
        text.add(() -> {
            // value right-aligned like a gauge, unit after it
            float uw = unit.isEmpty() ? 0f : HudStyle.width(unit, false, 0.65f) + 3f;
            float vx = right - uw - HudStyle.width(v, true, 0.95f);
            HudStyle.text(ctx, v, Math.max(tx, vx), cy, 0.95f, c, true);
            if (!unit.isEmpty()) HudStyle.text(ctx, unit, right - uw + 3f, cy + 0.6f, 0.65f, HudStyle.TEXT_DIM, false);
        });
    }
}
