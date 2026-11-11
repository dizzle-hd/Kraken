package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.hud.Icons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Panel with a header (icon tile, title, count) and rows "icon · label · value" – the value either as
 * plain text or in a small chip (keys, timers). Base of Resources, Effects and Keybinds.
 */
public abstract class ListPanelElement extends HudElement {
    /** One row: an item icon or a coloured dot, label, value, value colour, value shown as a chip. */
    public record Row(ItemStack item, int dot, String label, String value, int valueColor, boolean chip) {}

    private static final float HEAD = 18f, ROW = 13f, PAD = 6f;
    private final Icons icon;
    private final String title;

    protected ListPanelElement(String id, String name, Icons icon, String title, boolean enabled, int ax, int ay, float ox, float oy) {
        super(id, name, enabled, ax, ay, ox, oy);
        this.icon = icon;
        this.title = title;
    }

    protected abstract List<Row> rows(boolean editor);

    /** Hide the whole panel when there are no rows (outside the editor). */
    protected boolean hideWhenEmpty() { return true; }

    private static float rowWidth(Row r) {
        float w = PAD + (r.item() != null || r.dot() != 0 ? 13f : 0f) + HudStyle.width(r.label(), false, 0.72f) + 10f;
        w += r.chip() ? HudStyle.width(r.value(), false, 0.62f) + 7f : HudStyle.width(r.value(), true, 0.72f);
        return w + PAD;
    }

    @Override
    public float width(boolean editor) {
        float w = Math.max(100f, PAD + 14f + 6f + HudStyle.width(title, true, 0.8f) + 30f);
        for (Row r : rows(editor)) w = Math.max(w, rowWidth(r));
        return w;
    }

    @Override public float height(boolean editor) { return HEAD + Math.max(1, rows(editor).size()) * ROW + 4f; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        List<Row> rows = rows(editor);
        if (rows.isEmpty() && hideWhenEmpty() && !editor) return;
        float w = width(editor), h = height(editor);
        HudStyle.pill(ctx, s, x, y, x + w, y + h, 1f);
        HudStyle.iconBox(s, icon, x + 4f, y + 3f, 12f, 1f);
        s.rect(x + PAD, y + HEAD - 1.5f, x + w - PAD, y + HEAD - 1f, 0x1AFFFFFF);
        String count = String.valueOf(rows.size());
        text.add(() -> {
            HudStyle.text(ctx, title, x + 4f + 12f + 5f, y + 9f, 0.8f, HudStyle.TEXT, true);
            HudStyle.text(ctx, count, x + w - PAD - HudStyle.width(count, false, 0.65f), y + 9f, 0.65f, HudStyle.TEXT_DIM, false);
        });
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float cy = y + HEAD + i * ROW + ROW / 2f + 1f;
            float lx = x + PAD;
            if (r.dot() != 0) s.circle(lx + 4f, cy, 2.4f, r.dot());
            if (r.item() != null || r.dot() != 0) lx += 13f;
            float labelX = lx;
            if (r.chip()) HudStyle.chip(ctx, s, text, r.value(), x + w - PAD, cy, 0.62f, r.valueColor());
            text.add(() -> {
                if (r.item() != null) HudStyle.item(ctx, r.item(), x + PAD - 1f, cy - 5.5f, 11f);
                HudStyle.text(ctx, r.label(), labelX, cy, 0.72f, HudStyle.TEXT_DIM, false);
                if (!r.chip()) HudStyle.text(ctx, r.value(), x + w - PAD - HudStyle.width(r.value(), true, 0.72f), cy, 0.72f, r.valueColor(), true);
            });
        }
    }
}
