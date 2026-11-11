package dev.nautilus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nautilus.config.ConfigManager;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudManager;
import dev.nautilus.hud.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD editor: drag elements around (they snap to screen edges, the centre lines and other elements),
 * right-click an element to hide it, and use the glass toolbar at the bottom to show/hide elements or
 * reset the layout.
 */
public class HudEditorScreen extends Screen {
    private static final float SNAP = 5f;
    private static final float MARGIN = 5f;

    private HudElement dragging;
    private float grabX, grabY;
    private final List<float[]> guides = new ArrayList<>(); // {x1, y1, x2, y2}
    private final List<Chip> chips = new ArrayList<>();
    private final long openedAt = System.currentTimeMillis();

    private record Chip(float x1, float y1, float x2, float y2, HudElement element, String action) {}

    public HudEditorScreen() {
        super(Component.literal("HUD editor"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =============================================================================================
    // Rendering
    // =============================================================================================

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // light veil + fine grid so the game stays visible behind the HUD
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 200f);
        Shapes s = new Shapes();
        s.rect(0, 0, width, height, Shapes.alpha(0x50120604, open));
        int grid = Shapes.alpha(0x10FFFFFF, open);
        for (float x = 0; x < width; x += 20) s.rect(x, 0, x + 0.5f, height, grid);
        for (float y = 0; y < height; y += 20) s.rect(0, y, width, y + 0.5f, grid);
        int centre = Shapes.alpha(0x22FFFFFF, open);
        s.rect(width / 2f - 0.25f, 0, width / 2f + 0.25f, height, centre);
        s.rect(0, height / 2f - 0.25f, width, height / 2f + 0.25f, centre);
        s.submit(ctx);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        HudManager hud = HudManager.get();

        // element frames (behind the elements), in HUD space (scaled like the HUD itself)
        float k = HudManager.scale();
        ctx.pose().pushMatrix();
        ctx.pose().scale(k, k);
        Shapes frames = new Shapes();
        List<Runnable> labels = new ArrayList<>();
        HudElement hovered = dragging != null ? dragging : elementAt(mouseX, mouseY);
        for (HudElement e : hud.elements()) {
            if (!e.enabled) continue;
            float x = e.x(sw(), true), y = e.y(sh(), true), w = e.width(true), h = e.height(true);
            boolean hot = e == hovered;
            frames.roundRect(x - 3, y - 3, x + w + 3, y + h + 3, 6, hot ? HudStyle.accent(0x24) : 0x10FFFFFF);
            frames.roundRectRim(x - 3, y - 3, x + w + 3, y + h + 3, 6, hot ? 1.2f : 0.8f, hot ? HudStyle.accent(0xCC) : 0x44FFFFFF);
            if (hot) {
                String name = e.name();
                float lx = x, ly = y - 3 - 9;
                if (ly < 2) ly = y + h + 4;
                float fx = lx, fy = ly;
                labels.add(() -> HudStyle.text(ctx, name, fx, fy + 4, 0.8f, HudStyle.label(), true));
            }
        }
        // snap guides
        for (float[] g : guides) {
            frames.rect(Math.min(g[0], g[2]) - 0.4f, Math.min(g[1], g[3]) - 0.4f, Math.max(g[0], g[2]) + 0.4f, Math.max(g[1], g[3]) + 0.4f, HudStyle.accent(0xCC));
        }
        frames.submit(ctx);
        for (Runnable r : labels) r.run();
        labels.clear();
        ctx.pose().popMatrix();

        // the elements themselves (preview content; HudManager applies the scale itself)
        hud.render(ctx, true, null);

        ctx.nextStratum();
        // toolbar with one chip per element + reset
        if (dragging == null) renderPanel(ctx, mouseX, mouseY); // the panel steps aside while dragging
        else chips.clear();
        for (Runnable r : labels) r.run();
    }

    /** Centre panel: title, help line and one chip per element (+ reset). */
    private void renderPanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        chips.clear();
        HudManager hud = HudManager.get();
        float scale = 0.8f, h = 14f, gap = 4f;
        List<String> names = new ArrayList<>();
        for (HudElement e : hud.elements()) names.add(e.name());
        names.add("Reset");
        float maxRow = Math.min(width - 60f, 330f);
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> row = new ArrayList<>();
        float rw = 0;
        for (int i = 0; i < names.size(); i++) {
            float cw = chipWidth(names.get(i), scale, i == names.size() - 1);
            if (!row.isEmpty() && rw + gap + cw > maxRow) {
                rows.add(row);
                row = new ArrayList<>();
                rw = 0;
            }
            rw += (row.isEmpty() ? 0 : gap) + cw;
            row.add(i);
        }
        rows.add(row);
        float contentW = 0;
        for (List<Integer> r : rows) contentW = Math.max(contentW, rowWidth(r, names, scale, gap));

        String title = "HUD editor";
        String help = "Drag: move  ·  Right-click: hide  ·  ESC: done";
        float tw = HudStyle.width(title, true, 1.1f), hw = HudStyle.width(help, false, 0.72f);
        float pw = Math.max(contentW, Math.max(tw, hw)) + 24f;
        float ph = 12f + 11f + 9f + 8f + rows.size() * (h + gap) - gap + 12f;
        float px = width / 2f - pw / 2f, py = height / 2f - ph / 2f;

        Shapes s = new Shapes();
        s.roundRect(px, py, px + pw, py + ph, 10f, dev.nautilus.hud.HudStyle.panel(0x8C)); // stronger tint: the editor panel must stay readable
        HudStyle.pill(ctx, s, px, py, px + pw, py + ph, 1f);
        List<Runnable> text = new ArrayList<>();
        text.add(() -> HudStyle.text(ctx, title, width / 2f - tw / 2f, py + 12f + 4f, 1.1f, HudStyle.TEXT, true));
        text.add(() -> HudStyle.text(ctx, help, width / 2f - hw / 2f, py + 12f + 11f + 4f, 0.72f, HudStyle.TEXT_DIM, false));
        s.rect(px + 12f, py + 12f + 11f + 9f + 2f, px + pw - 12f, py + 12f + 11f + 9f + 2.5f, 0x26FFFFFF);

        float y = py + 12f + 11f + 9f + 8f;
        for (List<Integer> r : rows) {
            float x = width / 2f - rowWidth(r, names, scale, gap) / 2f;
            for (int i : r) {
                String n = names.get(i);
                boolean reset = i == names.size() - 1;
                float cw = chipWidth(n, scale, reset);
                HudElement e = reset ? null : hud.elements().get(i);
                boolean on = e != null && e.enabled;
                boolean hot = mouseX >= x && mouseX <= x + cw && mouseY >= y && mouseY <= y + h;
                int fill = reset ? (hot ? 0x40FFFFFF : 0x22FFFFFF) : on ? (hot ? HudStyle.accentHot(0xCC) : HudStyle.accent(0xA6)) : (hot ? 0x40FFFFFF : 0x1EFFFFFF);
                s.roundRect(x, y, x + cw, y + h, h / 2f, fill);
                s.roundRectRim(x, y, x + cw, y + h, h / 2f, 0.7f, 0x40FFFFFF);
                if (!reset) s.circle(x + 7f, y + h / 2f, 1.8f, on ? 0xFFFFFFFF : 0x80FFFFFF);
                float tx = x + (reset ? 8f : 12f), ty = y + h / 2f;
                int col = on && !reset ? HudStyle.onAccent() : reset ? HudStyle.TEXT : HudStyle.TEXT_DIM;
                text.add(() -> HudStyle.text(ctx, n, tx, ty, scale, col, false));
                chips.add(new Chip(x, y, x + cw, y + h, e, reset ? "reset" : "toggle"));
                x += cw + gap;
            }
            y += h + gap;
        }
        s.submit(ctx);
        for (Runnable t : text) t.run();
    }

    private static float chipWidth(String name, float scale, boolean reset) {
        return HudStyle.width(name, false, scale) + (reset ? 16f : 19f);
    }

    private static float rowWidth(List<Integer> row, List<String> names, float scale, float gap) {
        float w = -gap;
        for (int i : row) w += chipWidth(names.get(i), scale, i == names.size() - 1) + gap;
        return w;
    }

    // =============================================================================================
    // Interaction
    // =============================================================================================

    /** Screen size in HUD space. */
    private float sw() { return width / HudManager.scale(); }
    private float sh() { return height / HudManager.scale(); }

    private HudElement elementAt(double screenX, double screenY) {
        double mx = screenX / HudManager.scale(), my = screenY / HudManager.scale();
        List<HudElement> list = HudManager.get().elements();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudElement e = list.get(i);
            if (!e.enabled) continue;
            float x = e.x(sw(), true), y = e.y(sh(), true);
            if (mx >= x - 3 && mx <= x + e.width(true) + 3 && my >= y - 3 && my <= y + e.height(true) + 3) return e;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        for (Chip c : chips) {
            if (mx >= c.x1() && mx <= c.x2() && my >= c.y1() && my <= c.y2()) {
                if (c.action().equals("reset")) HudManager.get().resetLayout();
                else c.element().enabled = !c.element().enabled;
                return true;
            }
        }
        HudElement e = elementAt(mx, my);
        if (e == null) return true;
        if (click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            e.enabled = false;
            return true;
        }
        dragging = e;
        grabX = (float) (mx / HudManager.scale()) - e.x(sw(), true);
        grabY = (float) (my / HudManager.scale()) - e.y(sh(), true);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (dragging == null) return super.mouseDragged(click, offsetX, offsetY);
        float w = dragging.width(true), h = dragging.height(true);
        float width = sw(), height = sh(); // HUD space
        float x = (float) (click.x() / HudManager.scale()) - grabX, y = (float) (click.y() / HudManager.scale()) - grabY;
        guides.clear();

        // candidate snap lines: screen edges (with margin), centre, other elements
        List<Float> xs = new ArrayList<>(List.of(MARGIN, width - MARGIN, width / 2f));
        List<Float> ys = new ArrayList<>(List.of(MARGIN, height - MARGIN, height / 2f));
        for (HudElement o : HudManager.get().elements()) {
            if (o == dragging || !o.enabled) continue;
            float ox = o.x(width, true), oy = o.y(height, true); // width/height = HUD space here
            xs.add(ox);
            xs.add(ox + o.width(true));
            ys.add(oy);
            ys.add(oy + o.height(true));
            ys.add(oy + o.height(true) + 3f); // stack below with a small gap
            ys.add(oy - 3f);
        }
        float bestDx = SNAP + 1, snapX = x;
        for (float line : xs) {
            float[] cand = {line, line - w, line - w / 2f};
            for (float c : cand) {
                float d = Math.abs(x - c);
                if (d < bestDx) {
                    bestDx = d;
                    snapX = c;
                }
            }
        }
        float bestDy = SNAP + 1, snapY = y;
        for (float line : ys) {
            float[] cand = {line, line - h, line - h / 2f};
            for (float c : cand) {
                float d = Math.abs(y - c);
                if (d < bestDy) {
                    bestDy = d;
                    snapY = c;
                }
            }
        }
        if (bestDx <= SNAP) {
            x = snapX;
            float gx = Math.abs(x - snapX) < 0.01f ? guideX(x, w, xs) : -1;
            if (gx >= 0) guides.add(new float[]{gx, 0, gx, height});
        }
        if (bestDy <= SNAP) {
            y = snapY;
            float gy = guideX(y, h, ys);
            if (gy >= 0) guides.add(new float[]{0, gy, width, gy});
        }
        x = Math.max(0, Math.min(width - w, x));
        y = Math.max(0, Math.min(height - h, y));
        dragging.setPosition(x, y, width, height, true);
        return true;
    }

    /** Which snap line the box [p, p+size] is aligned to (for drawing the guide). */
    private static float guideX(float p, float size, List<Float> lines) {
        for (float l : lines) {
            if (Math.abs(p - l) < 0.01f) return l;
            if (Math.abs(p + size - l) < 0.01f) return l;
            if (Math.abs(p + size / 2f - l) < 0.01f) return l;
        }
        return -1;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        dragging = null;
        guides.clear();
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        ConfigManager.save();
        super.removed();
    }
}
