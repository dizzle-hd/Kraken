package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.Level;

import java.util.List;

/** XYZ plus the matching Nether / Overworld coordinates. */
public class CoordsElement extends HudElement {
    public CoordsElement() {
        super("coords", "Koordinaten", true, START, END, 5, 5);
    }

    private String[] lines() {
        if (mc.player == null) return new String[]{"XYZ", "0  64  0", "Nether", "0  64  0"};
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        boolean nether = mc.level != null && mc.level.dimension() == Level.NETHER;
        double f = nether ? 8 : 1 / 8.0;
        return new String[]{"XYZ", String.format("%.0f  %.0f  %.0f", x, y, z),
                nether ? "Oberwelt" : "Nether", String.format("%.0f  %.0f  %.0f", x * f, y, z * f)};
    }

    @Override
    public float width(boolean editor) {
        String[] l = lines();
        return Math.max(labelValueWidth(l[0], l[1]), labelValueWidth(l[2], l[3]));
    }

    @Override
    public float height(boolean editor) { return HudStyle.PILL_H * 2 + 3f; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        String[] l = lines();
        float w0 = labelValueWidth(l[0], l[1]), w1 = labelValueWidth(l[2], l[3]);
        boolean right = rightAligned();
        float total = width(editor);
        labelValue(s, text, ctx, right ? x + total - w0 : x, y, l[0], l[1], HudStyle.TEXT);
        labelValue(s, text, ctx, right ? x + total - w1 : x, y + HudStyle.PILL_H + 3f, l[2], l[3], HudStyle.TEXT_DIM);
    }
}
